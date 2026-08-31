package com.conload.workflow.crosscontext;

import com.conload.github.GitHubClient;
import com.conload.github.GitHubClient.GitCommit;
import com.conload.util.FileUtil;
import com.conload.util.Json;
import com.conload.workflow.WorkflowCallbacks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Searches GitHub commits for a Jira key and writes a single aggregated
 * {@code commits_<KEY>.json} file containing the commit metadata, author,
 * date, and diff for each commit.
 * <p>
 * This replaces the per-commit individual files that
 * {@link com.conload.workflow.collectors.GitHubCommitCollector} produces,
 * consolidating them into one file per jira key for the cross-context
 * folder layout.
 */
final class CommitsAggregator {

    private final String githubToken;
    private final String githubApiUrl;
    private final String ownerRepo;
    private final WorkflowCallbacks callbacks;
    private final Map<String, List<String>> sourceUrls;

    CommitsAggregator(String githubToken, String githubApiUrl, String ownerRepo,
                      WorkflowCallbacks callbacks, Map<String, List<String>> sourceUrls) {
        this.githubToken = githubToken;
        this.githubApiUrl = githubApiUrl;
        this.ownerRepo = ownerRepo;
        this.callbacks = callbacks;
        this.sourceUrls = sourceUrls;
    }

    /**
     * Search commits for a Jira key and write {@code commits_<key>.json}
     * into {@code dir}. Commits whose SHA is already in {@code visitedShas}
     * are skipped (global dedup across all Jira keys).
     *
     * @param visitedShas  global set of already-processed commit SHAs
     * @return number of new commits written
     */
    int writeCommits(Path dir, String jiraKey, Set<String> visitedShas) {
        if (ownerRepo == null || ownerRepo.isBlank() || !ownerRepo.contains("/")
                || githubToken == null || githubToken.isBlank()) {
            return 0;
        }
        String[] parts = ownerRepo.split("/", 2);
        String owner = parts[0].strip();
        String repo = parts[1].strip();
        GitHubClient client = new GitHubClient(githubToken, githubApiUrl);

        List<GitCommit> commits;
        try {
            callbacks.onProgress("GitHub", "Searching commits for " + jiraKey + "…");
            commits = client.searchCommits(owner, repo, jiraKey);
        } catch (Exception e) {
            callbacks.onError("GitHub", "Commits search failed for " + jiraKey + ": " + e.getMessage());
            return 0;
        }

        int saved = 0;
        var arr = Json.MAPPER.createArrayNode();
        List<String> commitUrls = new ArrayList<>();
        for (GitCommit commit : commits) {
            if (saved >= CrossContextLimits.MAX_COMMITS_PER_KEY || callbacks.isCancelled()) break;
            if (visitedShas.contains(commit.sha())) continue;
            visitedShas.add(commit.sha());
            String diff = "";
            try {
                diff = client.getCommitDiff(owner, repo, commit.sha());
            } catch (Exception e) {
                callbacks.onError("GitHub", "Diff fetch failed for " + commit.shortSha() + ": " + e.getMessage());
            }
            var obj = Json.MAPPER.createObjectNode();
            obj.put("sha", commit.sha());
            obj.put("shortSha", commit.shortSha());
            obj.put("author", commit.authorName() != null ? commit.authorName() : "");
            obj.put("date", commit.committerDate() != null ? commit.committerDate() : "");
            obj.put("message", commit.message() != null ? commit.message() : "");
            obj.put("htmlUrl", commit.htmlUrl() != null ? commit.htmlUrl() : "");
            obj.put("diff", diff);
            arr.add(obj);
            if (commit.htmlUrl() != null && !commit.htmlUrl().isBlank()) {
                commitUrls.add(commit.htmlUrl());
            }
            saved++;
        }

        if (saved > 0) {
            try {
                String filename = "commits_" + jiraKey + ".json";
                String json = Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(arr);
                FileUtil.writeText(dir.resolve(filename), json);
                callbacks.onLog("[GITHUB] ✓ " + saved + " commit(s) → " + filename);
                if (!commitUrls.isEmpty()) {
                    sourceUrls.computeIfAbsent(filename, k -> new ArrayList<>()).addAll(commitUrls);
                }
        } catch (Exception e) {
            callbacks.onError("GitHub", "Failed to write commits_" + jiraKey + ".json: " + e.getMessage());
        }
        }
        return saved;
    }
}
