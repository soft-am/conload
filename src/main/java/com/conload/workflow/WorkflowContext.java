package com.conload.workflow;

import com.conload.model.QuickAction;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of a workflow context-gathering run.
 * Carries output paths plus structured summaries used to build the prompt
 * and to display a context summary in the dialog.
 *
     * @param contextRoot         the workflow-named folder where downloads live
 * @param jiraDir             {@code <contextRoot>/jira} (downloaded Jira MDs)
 * @param githubDir           {@code <contextRoot>/github} (commit JSONs)
 * @param confluenceDir       {@code <contextRoot>/confluence} (page MDs)
 * @param jiraKeys            the original comma-separated Jira keys
 * @param relatedJiras        summaries of related/linked issues
 * @param commitsByAuthor     commits grouped per author (who/when)
 * @param confluencePages     summaries of downloaded Confluence pages
 * @param outputDocPath       where the generated doc should be written
 * @param repoOwnerRepo       the resolved GitHub {@code owner/repo}
 * @param confluenceDataSource the configured Confluence data-source reference
 * @param workspacePath       the project working directory (terminal cwd)
 */
public record WorkflowContext(
        Path contextRoot,
        Path jiraDir,
        Path githubDir,
        Path confluenceDir,
        String jiraKeys,
        List<RelatedJiraSummary> relatedJiras,
        List<CommitByAuthor> commitsByAuthor,
        List<ConfluencePageSummary> confluencePages,
        Path outputDocPath,
        String repoOwnerRepo,
        String confluenceDataSource,
        String workspacePath
) {

    /** Summary of a related/linked Jira issue (one level deep). */
    public record RelatedJiraSummary(
            String key,
            String summary,
            String status,
            String issueType,
            String relation
    ) {}

    /** Commits touching a Jira key, grouped by author. */
    public record CommitByAuthor(
            String author,
            List<CommitEntry> commits
    ) {
        /** One commit in an author group. */
        public record CommitEntry(
                String shortSha,
                String date,
                String message,
                String htmlUrl
        ) {}
    }

    /** Summary of a downloaded Confluence page. */
    public record ConfluencePageSummary(
            String title,
            String creator,
            String createdDate,
            String webUrl,
            String localPath
    ) {}

    /**
     * Builds the variable map for {@link QuickAction#substitute} so the
     * template's {@code ${var}} placeholders are filled with concrete values.
     */
    public Map<String, String> toVariableMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("jiraContextDir", str(jiraDir));
        m.put("githubContextDir", str(githubDir));
        m.put("confluenceContextDir", str(confluenceDir));
        m.put("confluenceDataSource", confluenceDataSource != null ? confluenceDataSource : "");
        m.put("outputDocPath", str(outputDocPath));
        m.put("jiraKeys", jiraKeys != null ? jiraKeys : "");
        m.put("workspacePath", workspacePath != null ? workspacePath : "");
        m.put("repoOwnerRepo", repoOwnerRepo != null ? repoOwnerRepo : "");
        m.put("relatedJirasSummary", formatRelatedJiras());
        m.put("commitsByAuthorSummary", formatCommitsByAuthor());
        m.put("confluencePagesSummary", formatConfluencePages());
        m.put("contextRoot", str(contextRoot));
        return m;
    }

    private static String str(Path p) {
        return p != null ? p.toAbsolutePath().toString() : "";
    }

    private String formatRelatedJiras() {
        if (relatedJiras == null || relatedJiras.isEmpty()) return "(none found)";
        var sb = new StringBuilder();
        for (var r : relatedJiras) {
            sb.append("- ").append(r.key()).append(" [").append(r.relation())
              .append("] ").append(r.issueType()).append(" / ").append(r.status())
              .append(": ").append(r.summary()).append("\n");
        }
        return sb.toString();
    }

    private String formatCommitsByAuthor() {
        if (commitsByAuthor == null || commitsByAuthor.isEmpty()) return "(none found)";
        var sb = new StringBuilder();
        for (var group : commitsByAuthor) {
            sb.append("### ").append(group.author()).append("\n");
            for (var c : group.commits()) {
                sb.append("- ").append(c.shortSha()).append(" (").append(c.date())
                  .append("): ").append(c.message()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatConfluencePages() {
        if (confluencePages == null || confluencePages.isEmpty()) return "(none found)";
        var sb = new StringBuilder();
        for (var p : confluencePages) {
            sb.append("- ").append(p.title());
            if (p.creator() != null && !p.creator().isBlank())
                sb.append(" (by ").append(p.creator());
            if (p.createdDate() != null && !p.createdDate().isBlank())
                sb.append(", ").append(p.createdDate());
            if (p.creator() != null && !p.creator().isBlank())
                sb.append(")");
            sb.append(" — ").append(p.localPath()).append("\n");
        }
        return sb.toString();
    }
}
