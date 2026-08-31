package com.conload.workflow.collectors;

import com.conload.github.GitHubClient;
import com.conload.util.FileUtil;
import com.conload.workflow.WorkflowCallbacks;
import com.conload.workflow.WorkflowContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collector for the GitHub commit context (step a2):
 * <ol>
 *   <li>For each Jira key, search GitHub commits mentioning the key.</li>
 *   <li>Save each commit as JSON (raw + diff), mirroring the existing
 *       {@code ContextDownloadService} file naming.</li>
 *   <li>Group commits by author — so the summary shows who/when.</li>
 * </ol>
 * All files land in {@code <contextRoot>/github/}.
 */
public final class GitHubCommitCollector {

    private static final int MAX_COMMITS_PER_KEY = 50;

    private final String githubToken;
    private final String githubApiUrl;
    private final WorkflowCallbacks callbacks;

    public GitHubCommitCollector(String githubToken, String githubApiUrl, WorkflowCallbacks callbacks) {
        this.githubToken = githubToken;
        this.githubApiUrl = githubApiUrl;
        this.callbacks = callbacks;
    }

    /**
     * @param jiraKeys     the Jira keys to search commit messages for
     * @param ownerRepo    the GitHub {@code owner/repo}
     * @param githubDir    output directory ({@code <contextRoot>/github})
     * @return commits grouped per author
     */
    public List<WorkflowContext.CommitByAuthor> collect(List<String> jiraKeys, String ownerRepo,
                                              Path githubDir) throws Exception {
        if (ownerRepo == null || ownerRepo.isBlank() || !ownerRepo.contains("/")) {
            callbacks.onLog("[GITHUB] No owner/repo configured — skipping commit search.");
            return List.of();
        }
        Files.createDirectories(githubDir);
        String[] parts = ownerRepo.split("/", 2);
        String owner = parts[0].strip();
        String repo = parts[1].strip();

        GitHubClient client = new GitHubClient(githubToken, githubApiUrl);

        // author → list of commits (preserves insertion order)
        Map<String, List<WorkflowContext.CommitByAuthor.CommitEntry>> byAuthor = new LinkedHashMap<>();

        for (String key : jiraKeys) {
            if (callbacks.isCancelled()) break;
            key = key.strip();
            if (key.isEmpty()) continue;

            callbacks.onProgress("GitHub", "Searching commits for " + key + "…");
            List<GitHubClient.GitCommit> commits;
            try {
                commits = client.searchCommits(owner, repo, key);
            } catch (Exception e) {
                callbacks.onLog("[GITHUB] Commits search failed for " + key + ": " + e.getMessage());
                continue;
            }

            int saved = 0;
            for (GitHubClient.GitCommit commit : commits) {
                if (saved >= MAX_COMMITS_PER_KEY) break;
                saveCommit(commit, githubDir, callbacks);
                addEntry(byAuthor, commit);
                saved++;
            }
            callbacks.onLog("[GITHUB] " + saved + " commit(s) found for " + key);
        }

        List<WorkflowContext.CommitByAuthor> result = new ArrayList<>();
        for (var entry : byAuthor.entrySet()) {
            result.add(new WorkflowContext.CommitByAuthor(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private void saveCommit(GitHubClient.GitCommit commit, Path dir, WorkflowCallbacks cb) {
        String shortSha = commit.shortSha();
        // Raw commit JSON
        String json = commit.rawJson() != null ? commit.rawJson() : "{}";
        try {
            FileUtil.writeText(dir.resolve("github-commit-" + shortSha + ".json"), json);
        } catch (Exception e) {
            cb.onLog("[GITHUB] Failed to save commit " + shortSha + ": " + e.getMessage());
        }
        // Diff JSON (when available)
        if (commit.diffContent() != null && !commit.diffContent().isBlank()) {
            try {
                FileUtil.writeText(dir.resolve("github-commit-" + shortSha + "-diff.json"),
                        commit.toDiffJson());
            } catch (Exception ignored) { /* best-effort */ }
        }
    }

    private void addEntry(Map<String, List<WorkflowContext.CommitByAuthor.CommitEntry>> byAuthor,
                          GitHubClient.GitCommit commit) {
        String author = commit.authorName();
        if (author == null || author.isBlank()) author = "unknown";
        byAuthor.computeIfAbsent(author, k -> new ArrayList<>()).add(
                new WorkflowContext.CommitByAuthor.CommitEntry(
                        commit.shortSha(),
                        commit.committerDate(),
                        commit.shortMessage(),
                        commit.htmlUrl()
                ));
    }
}
