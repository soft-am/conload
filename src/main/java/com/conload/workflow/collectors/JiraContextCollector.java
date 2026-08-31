package com.conload.workflow.collectors;

import com.conload.jira.JiraClient;
import com.conload.jira.JiraMarkdownExporter;
import com.conload.model.AppConfig;
import com.conload.model.JiraIssue;
import com.conload.workflow.WorkflowCallbacks;
import com.conload.workflow.WorkflowContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collector for the Jira context (step a1):
 * <ol>
 *   <li>Fetch each Jira issue and export it as Markdown (reusing
 *       {@link JiraMarkdownExporter}).</li>
 *   <li>Discover related/linked issues from {@code issuelinks}, subtasks,
 *       and parent — one level deep.</li>
 *   <li>Download related issues as Markdown too.</li>
 * </ol>
 * All MDs land in {@code <contextRoot>/jira/}.
 */
public final class JiraContextCollector {

    private final AppConfig config;
    private final String jiraBaseUrl;
    private final WorkflowCallbacks callbacks;

    public JiraContextCollector(AppConfig config, String jiraBaseUrl, WorkflowCallbacks callbacks) {
        this.config = config;
        this.jiraBaseUrl = jiraBaseUrl;
        this.callbacks = callbacks;
    }

    /**
     * @param keys          the primary Jira keys to fetch
     * @param jiraDir       output directory ({@code <contextRoot>/jira})
     * @return summaries of all related/linked issues discovered
     */
    public List<WorkflowContext.RelatedJiraSummary> collect(List<String> keys, Path jiraDir)
            throws Exception {
        Files.createDirectories(jiraDir);
        JiraMarkdownExporter exporter = new JiraMarkdownExporter(config, callbacks::onLog);
        JiraClient client = new JiraClient(config);

        Set<String> fetched = new LinkedHashSet<>();
        List<WorkflowContext.RelatedJiraSummary> related = new ArrayList<>();

        for (String key : keys) {
            if (callbacks.isCancelled()) break;
            key = key.strip();
            if (key.isEmpty()) continue;

            callbacks.onProgress("Jira", "Fetching " + key + "…");
            JiraIssue issue = exporter.fetchAndExport(jiraBaseUrl, key, jiraDir);
            fetched.add(key);

            collectRelated(client, issue, "primary", fetched, related, exporter, jiraDir);
        }
        return related;
    }

    /** Extract related issues from issuelinks + subtasks + parent (one level deep). */
    private void collectRelated(JiraClient client, JiraIssue issue, String relationPrefix,
                                 Set<String> fetched,
                                 List<WorkflowContext.RelatedJiraSummary> related,
                                 JiraMarkdownExporter exporter, Path jiraDir) throws Exception {
        var fields = issue.getFields();

        // Issuelinks — both inward and outward
        for (var link : fields.getIssuelinks()) {
            var linkedIssue = link.getInwardIssue() != null
                    ? link.getInwardIssue() : link.getOutwardIssue();
            if (linkedIssue == null || linkedIssue.getKey().isBlank()) continue;
            String rel = link.getInwardIssue() != null
                    ? link.getType().getInward() : link.getType().getOutward();
            fetchRelated(client, linkedIssue.getKey(), rel, linkedIssue.getFields().getSummary(),
                    fetched, related, exporter, jiraDir);
        }

        // Subtasks
        for (var st : fields.getSubtasks()) {
            fetchRelated(client, st.getKey(), "subtask", st.getFields().getSummary(),
                    fetched, related, exporter, jiraDir);
        }

        // Parent
        if (fields.getParent() != null && !fields.getParent().getName().isBlank()) {
            // Parent is a NamedObject with the key in the name field for Jira Cloud
            fetchRelated(client, fields.getParent().getName(), "parent", "",
                    fetched, related, exporter, jiraDir);
        }
    }

    private void fetchRelated(JiraClient client, String key, String relation, String summary,
                              Set<String> fetched,
                              List<WorkflowContext.RelatedJiraSummary> related,
                              JiraMarkdownExporter exporter, Path jiraDir) throws Exception {
        if (key == null || key.isBlank() || fetched.contains(key)) return;
        fetched.add(key);

        if (callbacks.isCancelled()) return;
        try {
            callbacks.onProgress("Jira", "Fetching related " + key + "…");
            JiraIssue relatedIssue = exporter.fetchAndExport(jiraBaseUrl, key, jiraDir);
            related.add(new WorkflowContext.RelatedJiraSummary(
                    relatedIssue.getKey(),
                    relatedIssue.getFields().getSummary(),
                    nameOf(relatedIssue.getFields().getStatus()),
                    nameOf(relatedIssue.getFields().getIssueType()),
                    relation
            ));
        } catch (Exception e) {
            // If we can't fetch the related issue, still record it from the link summary
            callbacks.onLog("[JIRA] Could not fetch related " + key + ": " + e.getMessage());
            related.add(new WorkflowContext.RelatedJiraSummary(
                    key, summary != null ? summary : "",
                    "", "", relation));
        }
    }

    private static String nameOf(JiraIssue.NamedObject obj) {
        return obj != null ? obj.getName() : "";
    }
}
