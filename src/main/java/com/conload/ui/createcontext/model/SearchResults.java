package com.conload.ui.createcontext.model;

import com.conload.github.GitHubClient;
import javafx.scene.control.TreeItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Carrier for all results returned by one unified search run.
 *
 *  <p>Each criterion that produces results contributes its own entry to the
 *  matching per-criterion list (e.g. {@link #jiraUrlResults}). The UI renders
 *  one {@code ResultPanel} per entry so the user sees a separate result
 *  tree/table for every search criterion (mirrors how Confluence page trees
 *  have always worked via {@link #confTrees}). */
public class SearchResults {
    public List<PageSearchResult>         confSearch;
    public String                         confSearchBase;
    public final List<ConfTreeResult>     confTrees = new ArrayList<>();

    /** One Jira keyword-search criterion's results (EVERYWHERE or a unified Jira keyword). */
    public final List<JiraCriterionResult>    jiraKeywordResults = new ArrayList<>();
    /** One Jira criterion's results loaded directly from a URL or bare key. */
    public final List<JiraCriterionResult>    jiraUrlResults     = new ArrayList<>();

    /** One GitHub commit-search or commit-from-URL criterion's results
     *  ({@code GITHUB_COMMIT} / {@code GITHUB_COMMIT_URL}). */
    public final List<GitHubCriterionResult>      githubCommitResults = new ArrayList<>();
    /** One GitHub Action run criterion's results ({@code GITHUB_ACTION_URL}). */
    public final List<GitHubActionCriterionResult> githubActionResults = new ArrayList<>();
    /** One GitHub PR criterion's results ({@code GITHUB_PR}). */
    public final List<GitHubPrCriterionResult>     githubPrResults     = new ArrayList<>();

    public final Map<String, String>      errors = new LinkedHashMap<>();

    /** One Confluence page tree resulting from a single Confluence URL criterion. */
    public record ConfTreeResult(String criterionValue, TreeItem<PageTreeItem> tree, String baseUrl, int count) {}

    /** One Jira criterion's results (keyword search or URL-loaded issue + its
     *  linked issues). {@code baseUrl} is the Atlassian base used for the
     *  request so the download step can rebuild a client against the same host. */
    public record JiraCriterionResult(String criterionValue, List<JiraTableItem> items, String baseUrl) {}

    /** One GitHub commit criterion's results. {@code owner}/{@code repo}
     *  identify the repository the commits belong to. */
    public record GitHubCriterionResult(String criterionValue, List<GitHubClient.GitCommit> commits,
                                        String owner, String repo) {}

    /** One GitHub Action run criterion's results. */
    public record GitHubActionCriterionResult(String criterionValue, GitHubClient.GitActionRun run) {}

    /** One GitHub PR criterion's results. */
    public record GitHubPrCriterionResult(String criterionValue, GitHubClient.GitPullRequest pr) {}
}
