package com.conload.jira;

import com.conload.model.AppConfig;
import com.conload.model.JiraIssue;
import com.conload.model.JiraIssue.*;
import com.conload.ui.Icons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Orchestrates: fetch a Jira issue → render as Markdown → save files + attachments.
 */
public class JiraMarkdownExporter {

    private final JiraClient    client;
    private final AdfConverter  adf   = new AdfConverter();
    private final Consumer<String> log;

    public JiraMarkdownExporter(AppConfig config, Consumer<String> log) {
        this.client = new JiraClient(config);
        this.log    = log;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Download a single Jira issue to Markdown.
     *
     * @param baseUrl  e.g. https://company.atlassian.net
     * @param issueKey e.g. PROJ-123
     * @param sessionRoot session folder (already created)
     */
    public JiraIssue fetchAndExport(String baseUrl, String issueKey, Path sessionRoot)
            throws IOException, InterruptedException {

        log.accept("[JIRA] Fetching " + issueKey + "…");
        JiraIssue issue = client.getIssue(baseUrl, issueKey);

        // Build attachment map: media-id → filename (for ADF media references)
        Map<String, String> mediaIdToFile = issue.getFields().getAttachment().stream()
            .collect(Collectors.toMap(
                Attachment::getId,
                Attachment::getFilename,
                (a, b) -> a));

        String markdown = renderMarkdown(issue, mediaIdToFile);
        String safeTitle = issue.getKey() + " - " + sanitize(issue.getFields().getSummary());
        Path mdFile = sessionRoot.resolve(safeTitle + ".md");
        Files.writeString(mdFile, markdown);
        log.accept("[JIRA] " + Icons.CHECK + " Written: " + mdFile.getFileName());

        // Download attachments
        if (!issue.getFields().getAttachment().isEmpty()) {
            Path mediaDir = sessionRoot.resolve("media");
            Files.createDirectories(mediaDir);
            for (Attachment att : issue.getFields().getAttachment()) {
                try {
                    log.accept("[JIRA]   " + Icons.ARROW_DOWN + " Attachment: " + att.getFilename());
                    byte[] bytes = client.downloadAttachment(att.getContent());
                    Files.write(mediaDir.resolve(att.getFilename()), bytes);
                } catch (Exception e) {
                    log.accept("[JIRA][WARN] Could not download " + att.getFilename() + ": " + e.getMessage());
                }
            }
        }

        return issue;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Markdown rendering
    // ─────────────────────────────────────────────────────────────────────────

    private String renderMarkdown(JiraIssue issue, Map<String, String> mediaIdToFile) {
        Fields f = issue.getFields();
        StringBuilder md = new StringBuilder();

        // Title
        md.append("# ").append(issue.getKey()).append(" · ").append(f.getSummary()).append("\n\n");

        // Metadata table
        md.append("| Field | Value |\n|---|---|\n");
        appendMeta(md, "Type",       name(f.getIssueType()));
        appendMeta(md, "Status",     name(f.getStatus()));
        appendMeta(md, "Priority",   name(f.getPriority()));
        appendMeta(md, "Assignee",   user(f.getAssignee()));
        appendMeta(md, "Reporter",   user(f.getReporter()));
        if (!f.getLabels().isEmpty())
            appendMeta(md, "Labels", f.getLabels().stream()
                .map(l -> "`" + l + "`").collect(Collectors.joining(", ")));
        if (!f.getComponents().isEmpty())
            appendMeta(md, "Components", f.getComponents().stream()
                .map(NamedObject::getName).collect(Collectors.joining(", ")));
        if (!f.getFixVersions().isEmpty())
            appendMeta(md, "Fix Version", f.getFixVersions().stream()
                .map(NamedObject::getName).collect(Collectors.joining(", ")));
        if (!f.getCreated().isBlank()) appendMeta(md, "Created", f.getCreated());
        if (!f.getUpdated().isBlank()) appendMeta(md, "Updated", f.getUpdated());
        if (!f.getDuedate().isBlank()) appendMeta(md, "Due",     f.getDuedate());
        md.append("\n");

        // Description
        if (f.getDescription() != null && !f.getDescription().isNull()) {
            md.append("## Description\n\n");
            String desc = adf.convert(f.getDescription());
            // Replace media-id placeholders with real filenames
            for (Map.Entry<String, String> e : mediaIdToFile.entrySet()) {
                desc = desc.replace(e.getKey(), e.getValue());
            }
            md.append(desc).append("\n\n");
        }

        // Subtasks
        if (!f.getSubtasks().isEmpty()) {
            md.append("## Subtasks\n\n");
            md.append("| Key | Summary | Status |\n|---|---|---|\n");
            for (SubTask st : f.getSubtasks()) {
                md.append("| ").append(st.getKey()).append(" | ")
                  .append(st.getFields().getSummary()).append(" | ")
                  .append(name(st.getFields().getStatus())).append(" |\n");
            }
            md.append("\n");
        }

        // Linked issues
        if (!f.getIssuelinks().isEmpty()) {
            md.append("## Linked Issues\n\n");
            md.append("| Relation | Key | Type | Summary | Status |\n|---|---|---|---|---|\n");
            for (IssueLink link : f.getIssuelinks()) {
                IssueLink.LinkedIssue li = link.getInwardIssue() != null
                    ? link.getInwardIssue() : link.getOutwardIssue();
                if (li == null) continue;
                String rel = link.getInwardIssue() != null
                    ? link.getType().getInward()
                    : link.getType().getOutward();
                md.append("| ").append(rel)
                  .append(" | ").append(li.getKey())
                  .append(" | ").append(name(li.getFields().getIssueType()))
                  .append(" | ").append(li.getFields().getSummary())
                  .append(" | ").append(name(li.getFields().getStatus()))
                  .append(" |\n");
            }
            md.append("\n");
        }

        // Attachments
        if (!f.getAttachment().isEmpty()) {
            md.append("## Attachments\n\n");
            for (Attachment att : f.getAttachment()) {
                md.append("- [").append(att.getFilename())
                  .append("](./media/").append(att.getFilename()).append(")\n");
            }
            md.append("\n");
        }

        // Comments
        if (f.getComment() != null && !f.getComment().getComments().isEmpty()) {
            md.append("## Comments\n\n");
            for (JiraIssue.CommentContainer.Comment c : f.getComment().getComments()) {
                String author = c.getAuthor() != null ? c.getAuthor().getDisplayName() : "Unknown";
                md.append("### ").append(author).append(" — ").append(c.getCreated()).append("\n\n");
                if (c.getBody() != null && !c.getBody().isNull()) {
                    md.append(adf.convert(c.getBody())).append("\n\n");
                }
            }
        }

        return md.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void appendMeta(StringBuilder md, String field, String value) {
        if (value == null || value.isBlank()) return;
        md.append("| **").append(field).append("** | ").append(value).append(" |\n");
    }

    private String name(NamedObject obj) {
        return obj != null ? obj.getName() : "";
    }

    private String user(JiraIssue.UserObject u) {
        return u != null ? u.getDisplayName() : "";
    }

    private String sanitize(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
    }
}
