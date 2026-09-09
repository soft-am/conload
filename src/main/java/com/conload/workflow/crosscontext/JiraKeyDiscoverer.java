package com.conload.workflow.crosscontext;

import com.conload.confluence.ConfluenceUrlParser;
import com.conload.jira.JiraClient;
import com.conload.model.JiraIssue;
import com.conload.model.JiraIssue.IssueLink;
import com.conload.model.JiraIssue.IssueLink.LinkedIssue;
import com.conload.workflow.WorkflowCallbacks;
import com.conload.workflow.WorkflowStoppedException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Discovers Jira keys, epic links, related issues, and Confluence page IDs
 * from various text sources and Jira issue objects. Used by the recursive
 * {@code expandJira} core to find the full graph of related context.
 * <p>
 * All public methods are designed as small, composable, side-effect-free
 * utilities — except {@link #epicLinkKey} and {@link #confluencePageIds}
 * which make HTTP calls via {@link JiraClient}.
 */
final class JiraKeyDiscoverer {

    private static final Pattern BARE_KEY =
            Pattern.compile("\\b([A-Z][A-Z0-9_]+-\\d+)\\b");

    private static final Pattern CONFLUENCE_URL =
            Pattern.compile("https?://[^\\s\"<>]+/wiki/(?:spaces/[^/]+/)?(?:pages/\\d+|[^\\s\"<>]*pageId=\\d+)");

    private final WorkflowCallbacks callbacks;

    JiraKeyDiscoverer(WorkflowCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    // ── Jira keys from text ──────────────────────────────────────────────

    /** Extract all bare Jira keys (e.g. PROJ-123) from arbitrary text. */
    static List<String> fromText(String text) {
        if (text == null || text.isBlank()) return List.of();
        Set<String> keys = new LinkedHashSet<>();
        Matcher m = BARE_KEY.matcher(text);
        while (m.find()) keys.add(m.group(1));
        return new ArrayList<>(keys);
    }

    /** Scan all {@code *.md} files under {@code dir} for Jira keys. */
    List<String> fromDirectory(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return List.of();
        Set<String> keys = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".md"))
                .forEach(p -> {
                    try {
                        keys.addAll(fromText(Files.readString(p)));
                    } catch (IOException ignored) { /* best-effort */ }
                });
        } catch (IOException e) {
            callbacks.onError("Discover", "Directory scan failed: " + e.getMessage());
        }
        return new ArrayList<>(keys);
    }

    // ── Epic link ───────────────────────────────────────────────────────

    /**
     * Fetch the parent/epic key for an issue.
     * Tries {@code customfield_10014} (Jira Cloud default "Epic Link"),
     * then falls back to the {@code parent.key} field.
     *
     * @return epic key, or empty string if none found
     */
    String epicLinkKey(JiraClient client, String baseUrl, String issueKey) {
        try {
            String epic = client.getEpicLinkKey(baseUrl, issueKey);
            if (epic != null && !epic.isBlank()) return epic;
        } catch (Exception e) {
            if (callbacks.isHardStopped() || WorkflowCallbacks.isInterruptCause(e)) {
                throw new WorkflowStoppedException(e);
            }
            callbacks.onError("Discover", "Epic link fetch failed for " + issueKey + ": " + e.getMessage());
        }
        return "";
    }

    // ── Related keys ────────────────────────────────────────────────────

    /** Extract related Jira keys from issuelinks + subtasks (no API call). */
    static List<String> relatedKeys(JiraIssue issue) {
        if (issue == null || issue.getFields() == null) return List.of();
        Set<String> keys = new LinkedHashSet<>();
        var fields = issue.getFields();

        for (IssueLink link : fields.getIssuelinks()) {
            LinkedIssue li = link.getInwardIssue() != null
                    ? link.getInwardIssue() : link.getOutwardIssue();
            if (li != null && !li.getKey().isBlank()) keys.add(li.getKey());
        }

        for (var st : fields.getSubtasks()) {
            if (!st.getKey().isBlank()) keys.add(st.getKey());
        }

        return new ArrayList<>(keys);
    }

    // ── Confluence page IDs from a Jira issue ───────────────────────────

    /**
     * Discover Confluence page IDs linked from a Jira issue:
     * remote links + ADF description + comment bodies.
     *
     * @return deduplicated list of Confluence page IDs
     */
    List<String> confluencePageIds(JiraClient client, JiraIssue issue, String baseUrl,
                                     ConfluenceUrlParser parser) {
        if (issue == null) return List.of();
        Set<String> pageIds = new LinkedHashSet<>();

        String key = issue.getKey();
        try {
            for (JiraClient.RemoteLink link : client.getRemoteLinks(baseUrl, key)) {
                String pid = parser.extractPageId(link.getUrl());
                if (pid != null) pageIds.add(pid);
            }
        } catch (Exception e) {
            if (callbacks.isHardStopped() || WorkflowCallbacks.isInterruptCause(e)) {
                throw new WorkflowStoppedException(e);
            }
            callbacks.onError("Discover", "Remote links failed for " + key + ": " + e.getMessage());
        }

        scanAdfForUrls(issue.getFields().getDescription(), parser, pageIds);

        if (issue.getFields().getComment() != null) {
            for (var c : issue.getFields().getComment().getComments()) {
                scanAdfForUrls(c.getBody(), parser, pageIds);
            }
        }

        return new ArrayList<>(pageIds);
    }

    private void scanAdfForUrls(JsonNode adf, ConfluenceUrlParser parser, Set<String> pageIds) {
        if (adf == null || adf.isNull()) return;
        scanNode(adf, parser, pageIds);
    }

    private void scanNode(JsonNode node, ConfluenceUrlParser parser, Set<String> pageIds) {
        if (node == null || node.isNull()) return;
        if (node.isTextual()) {
            Matcher m = CONFLUENCE_URL.matcher(node.asText());
            while (m.find()) {
                String pid = parser.extractPageId(m.group());
                if (pid != null) pageIds.add(pid);
            }
        }
        if (node.isObject() || node.isArray()) {
            for (JsonNode child : node) scanNode(child, parser, pageIds);
        }
    }
}
