package com.conload.github;

import com.conload.http.AbstractRestClient;
import com.conload.github.actions.GitHubActionsClient;
import com.conload.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GitHubClient extends AbstractRestClient {

    private static final String DEFAULT_API_BASE = "https://api.github.com";
    private static final Map<String, String> GH_HEADERS =
        headers("X-GitHub-Api-Version", "2022-11-28");
    private final String apiBase;
    private final GitHubActionsClient actionsClient;

    /** Defaults the API base to {@code https://api.github.com}. */
    public GitHubClient(String githubToken) {
        this(githubToken, "");
    }

    /**
     * @param githubApiUrl GitHub REST API root (e.g.
     *                    {@code https://api.github.com} for github.com, or a
     *                    GitHub Enterprise host). Blank → default.
     */
    public GitHubClient(String githubToken, String githubApiUrl) {
        super("Bearer " + githubToken,
              Duration.ofSeconds(15),
              Duration.ofSeconds(30),
              3,
              500L);
        this.apiBase = resolveApiBase(githubApiUrl);
        actionsClient = new GitHubActionsClient(this.apiBase, new GitHubActionsClient.Transport() {
            @Override
            public String getJson(String url) throws IOException, InterruptedException {
                return GitHubClient.this.getRaw(url, "application/vnd.github+json", GH_HEADERS).body();
            }

            @Override
            public String fetchJobLogs(String url) throws IOException, InterruptedException {
                return GitHubClient.this.fetchRedirectedBody(url, "application/vnd.github+json", GH_HEADERS);
            }
        });
    }

    private static String resolveApiBase(String githubApiUrl) {
        if (githubApiUrl == null || githubApiUrl.isBlank()) return DEFAULT_API_BASE;
        return githubApiUrl.strip().replaceAll("/+$", "");
    }

    /**
     * Derives the GitHub <em>web</em> host (the host that appears in browser
     * URLs like commit/PR/action links) from a configured API base.
     * <ul>
     *   <li>{@code https://api.github.com} (or blank) → {@code github.com}</li>
     *   <li>{@code https://ghe.company.com/api/v3} → {@code ghe.company.com}</li>
     *   <li>{@code https://ghe.company.com} → {@code ghe.company.com}</li>
     * </ul>
     */
    public static String webHostFromApiUrl(String githubApiUrl) {
        String base = resolveApiBase(githubApiUrl);
        String host;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("https?://([^/]+)/?").matcher(base + "/");
        if (!m.find()) return "github.com";
        host = m.group(1);
        if (host.startsWith("api.")) host = host.substring(4);
        return host;
    }

    public record GitCommit(
        String sha,
        String shortSha,
        String message,
        String shortMessage,
        String authorName,
        String committerDate,
        String htmlUrl,
        String rawJson,
        String diffContent
    ) {
        // JavaBean getters — JavaFX PropertyValueFactory only resolves get<Name>()/
        // <name>Property() and cannot read record accessors, so without these the
        // commits table rows render as blank cells.
        public String getShortSha()      { return shortSha; }
        public String getShortMessage()  { return shortMessage; }
        public String getAuthorName()    { return authorName; }
        public String getCommitterDate() { return committerDate; }

        /** Serialises the commit diff as a pretty JSON object {"sha","url","diff"}.
         *  Used by the COMMIT DETAILS pane and the download step (file:
         *  github-commit-<sha>-diff.json). Mirrors the PR-diff JSON shape. */
        public String toDiffJson() {
            var node = Json.MAPPER.createObjectNode()
                    .put("sha", sha)
                    .put("url", htmlUrl)
                    .put("diff", diffContent != null ? diffContent : "");
            try {
                return Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            } catch (Exception e) {
                return node.toString();
            }
        }
    }

    public record GitUser(String login) {}

    public record GitPullRequest(
        int number,
        String title,
        String author,
        GitUser user,
        String state,
        String body,
        String htmlUrl,
        String createdAt,
        String updatedAt,
        String mergedAt,
        String diffUrl,
        String rawJson,
        String diffContent
    ) {}

    public record GitActionStep(
        int number,
        String name,
        String status,
        String conclusion
    ) {}

    public record GitActionRun(
        long runId,
        long jobId,
        String workflowName,
        String runName,
        String jobName,
        String status,        // run status: queued / in_progress / completed
        String conclusion,   // run conclusion: success / failure / cancelled / ...
        String htmlUrl,
        String rawJson,       // run metadata JSON
        String jobLogs,       // concatenated job log text (may be empty)
        List<GitActionStep> steps
    ) {}

    public GitPullRequest getPullRequest(String owner, String repo, int prNumber)
            throws IOException, InterruptedException {
        // Step 1: fetch PR metadata
        String url = apiBase + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber;
        String rawJson = getRaw(url, "application/vnd.github+json").body();
        JsonNode root = mapper.readTree(rawJson);
        String diffUrl = root.path("diff_url").asText("");

        // Step 2: fetch the PR diff
        String diffContent = fetchDiffOrEmpty(diffUrl);
        return parsePullRequest(root, diffUrl, rawJson, diffContent);
    }

    private GitPullRequest parsePullRequest(JsonNode item, String diffUrl,
                                            String rawJson, String diffContent) {
        return new GitPullRequest(
            item.path("number").asInt(),
            item.path("title").asText(""),
            item.path("user").path("login").asText(""),
            new GitUser(item.path("user").path("login").asText("")),
            item.path("state").asText(""),
            item.path("body").asText(""),
            item.path("html_url").asText(""),
            item.path("created_at").asText(""),
            item.path("updated_at").asText(""),
            item.path("merged_at").asText(""),
            diffUrl,
            rawJson,
            diffContent
        );
    }

    public List<GitCommit> searchCommits(String owner, String repo, String query)
            throws IOException, InterruptedException {
        String q = URLEncoder.encode(query + " repo:" + owner + "/" + repo,
                                      StandardCharsets.UTF_8);
        String url = apiBase + "/search/commits?q=" + q
            + "&sort=committer-date&order=desc&per_page=50";
        JsonNode items = getJsonNode(url).path("items");
        List<GitCommit> result = new ArrayList<>();
        if (!items.isArray()) return result;
        for (JsonNode item : items) {
            result.add(parseCommit(item, ""));
        }
        return result;
    }

    /**
     * List all commits in a Pull Request (for scanning commit messages
     * for Jira keys and Confluence URLs).
     */
    public List<GitCommit> getPrCommits(String owner, String repo, int prNumber)
            throws IOException, InterruptedException {
        String url = apiBase + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/commits";
        JsonNode items = getJsonNode(url);
        List<GitCommit> result = new ArrayList<>();
        if (!items.isArray()) return result;
        for (JsonNode item : items) {
            result.add(parseCommit(item, ""));
        }
        return result;
    }

    /** Unified commit parser (collapses the former {@code parseSearchCommit} + {@code parseListCommit}). */
    private GitCommit parseCommit(JsonNode item, String diffContent) {
        String sha     = item.path("sha").asText("");
        String htmlUrl = item.path("html_url").asText("");
        JsonNode commit = item.path("commit");
        String message = commit.path("message").asText("");
        String date    = commit.path("committer").path("date").asText(
                         commit.path("author").path("date").asText(""));
        String author  = commit.path("author").path("name").asText(
                         item.path("author").path("login").asText(""));
        return build(sha, message, author, date, htmlUrl, item.toString(), diffContent);
    }

    private GitCommit build(String sha, String message, String author,
                            String date, String htmlUrl, String rawJson, String diffContent) {
        String shortSha = sha.length() >= 7 ? sha.substring(0, 7) : sha;
        String shortDate = date.length() >= 16
            ? date.substring(0, 10) + " " + date.substring(11, 16) : date;
        String shortMsg = message.lines().findFirst().orElse("").strip();
        if (shortMsg.length() > 100) shortMsg = shortMsg.substring(0, 97) + "...";
        return new GitCommit(sha, shortSha, message, shortMsg, author,
                             shortDate, htmlUrl, rawJson,
                             diffContent != null ? diffContent : "");
    }

    /**
     * Fetches a single commit by owner/repo/sha, including its diff
     * (the commit "changes"). Used by the GitHub Commit URL criterion.
     */
    public GitCommit getCommit(String owner, String repo, String sha)
            throws IOException, InterruptedException {
        String url = commitUrl(owner, repo, sha);
        String rawJson = getRaw(url, "application/vnd.github+json").body();
        JsonNode root = mapper.readTree(rawJson);
        String diff = getCommitDiff(owner, repo, sha);
        return parseCommit(root, diff);
    }

    /** Fetches the unified diff ("changes") for a single commit. */
    public String getCommitDiff(String owner, String repo, String sha)
            throws IOException, InterruptedException {
        return fetchDiffOrEmpty(commitUrl(owner, repo, sha));
    }

    private String commitUrl(String owner, String repo, String sha) {
        return apiBase + "/repos/" + owner + "/" + repo + "/commits/" + sha;
    }

    /** Fetches a single GitHub Actions workflow run (and the referenced job's
     *  logs when {@code jobId} is non-null). Used by the GitHub Action URL criterion.
     *
     * @param jobId if present, the specific job whose logs are downloaded;
     *              if {@code null}, logs for all jobs of the run are concatenated.
     */
    public GitActionRun getActionRun(String owner, String repo, long runId, Long jobId)
            throws IOException, InterruptedException {
        return actionsClient.getActionRun(owner, repo, runId, jobId);
    }

    private JsonNode getJsonNode(String url) throws IOException, InterruptedException {
        return mapper.readTree(getRaw(url, "application/vnd.github+json").body());
    }

    private HttpResponse<String> getRaw(String url, String accept)
            throws IOException, InterruptedException {
        return getRaw(url, accept, GH_HEADERS);
    }

    /** Fetches a diff URL, returning "" on failure (best-effort, logged). */
    private String fetchDiffOrEmpty(String diffUrl) {
        if (diffUrl == null || diffUrl.isBlank()) return "";
        try {
            return getRaw(diffUrl, "application/vnd.github.v3.diff", GH_HEADERS).body();
        } catch (IOException | InterruptedException e) {
            log.warning("Failed to fetch diff from " + diffUrl + ": " + e.getMessage());
            return "";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vendor error mapping
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void ensureSuccess(int status, String url, String body) throws IOException {
        if (status >= 200 && status < 300) return;
        if (status == 401)
            throw new IOException("GitHub authentication failed (401). Check GitHub token in Config tab.");
        if (status == 403)
            throw new IOException("GitHub rate limit or access denied (403). " +
                "Commit search requires an authenticated token.");
        if (status == 404)
            throw new IOException("GitHub repository not found (404): " + url);
        if (status == 422)
            throw new IOException("GitHub search error (422) - check repo name format (owner/repo).");
        throw new IOException("GitHub API error: HTTP " + status + " for " + url);
    }
}
