package com.conload.service.search;

import com.conload.confluence.ConfluenceClient;
import com.conload.confluence.ConfluenceUrlParser;
import com.conload.github.GitHubClient;
import com.conload.jira.JiraClient;
import com.conload.model.AppConfig;
import com.conload.ui.createcontext.model.CriteriaType;
import com.conload.ui.createcontext.model.JiraTableItem;
import com.conload.ui.createcontext.model.PageSearchResult;
import com.conload.ui.createcontext.model.PageTreeItem;
import com.conload.ui.createcontext.model.SearchCriterion;
import com.conload.ui.createcontext.model.SearchResults;
import com.conload.ui.Icons;
import javafx.concurrent.Task;
import javafx.scene.control.TreeItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ContentSearchService {
    public Task<SearchResults> createSearchTask(String username, String token, String ghToken, String ghApiUrl,
                                                  String confBase, String jiraBase,
                                                  List<SearchCriterion> activeCriteria,
                                                  java.util.function.Consumer<String> log,
                                                  AtomicBoolean cancelled) {
        return new Task<>() {
            @Override protected SearchResults call() throws Exception {
                return runSearch(username, token, ghToken, ghApiUrl, confBase, jiraBase, activeCriteria, log);
            }
        };
    }

    private SearchResults runSearch(String username, String token, String ghToken, String ghApiUrl, String confBase,
                                    String jiraBase, List<SearchCriterion> criteria,
                                    java.util.function.Consumer<String> log) throws Exception {
        SearchResults results = new SearchResults();
        Map<String, String> errors = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        SearchContext context = new SearchContext(username, token, ghToken, ghApiUrl, confBase, jiraBase,
                criteria, results, errors, log, futures, pool);
        submitConfluenceSearches(context);
        submitJiraSearches(context);
        submitGitHubSearches(context);
        waitForSearches(futures);
        pool.shutdown();
        results.errors.putAll(errors);
        return results;
    }

    // ── Confluence: unified URL-or-keyword dispatch ────────────────────────

    private void submitConfluenceSearches(SearchContext c) {
        CriteriaLists x = c.criteriaLists();
        // Everywhere keywords → run as Confluence keyword search when base is configured
        if (x.hasKeyword() && c.credentials() && valid(c.confBase)) submitKeywords(c, x.everywheres(), false);
        if (!x.confluence().isEmpty() && c.credentials()) {
            SourceInputClassifier classifier = new SourceInputClassifier();
            for (SearchCriterion criterion : x.confluence()) {
                if (criterion.value.isBlank()) continue;
                SourceInputClassifier.ConfluenceInput ci = classifier.classifyConfluence(criterion.value);
                if (ci.isTreeUrl()) {
                    if (ci.baseUrl() == null || ci.pageId() == null) {
                        c.log.accept("[CONFLUENCE][WARN] Could not parse URL: " + criterion.value);
                    } else {
                        c.futures.add(c.pool.submit(() -> loadConfTree(c, criterion, ci.baseUrl(), ci.pageId())));
                    }
                } else if (valid(c.confBase)) {
                    c.futures.add(c.pool.submit(() -> searchConfluenceKeyword(c, criterion, false)));
                }
            }
        }
    }

    private void submitKeywords(SearchContext c, List<SearchCriterion> criteria, boolean criterionSearch) {
        for (SearchCriterion criterion : criteria)
            c.futures.add(c.pool.submit(() -> searchConfluenceKeyword(c, criterion, criterionSearch)));
    }

    private void loadConfTree(SearchContext c, SearchCriterion criterion, String base, String pageId) {
        try {
            c.log.accept("[CONFLUENCE] Loading page tree for: " + criterion.value);
            ConfluenceClient client = new ConfluenceClient(new AppConfig(c.username, c.token));
            AtomicInteger count = new AtomicInteger();
            com.conload.model.ConfluencePage root = client.getPage(base, pageId);
            c.log.accept("[CONFLUENCE] Root page: \"" + root.getTitle() + "\"  id=" + pageId);
            PageTreeItem data = new PageTreeItem(root.getId(), root.getTitle());
            data.selected.set(true);
            TreeItem<PageTreeItem> item = new TreeItem<>(data);
            count.incrementAndGet();
            loadChildrenRecursive(client, base, root.getId(), item, count);
            synchronized (c.results.confTrees) { c.results.confTrees.add(new SearchResults.ConfTreeResult(criterion.value, item, base, count.get())); }
            c.log.accept("[CONFLUENCE] " + Icons.CHECK + " Tree loaded for \"" + criterion.value + "\": " + count.get() + " page(s)");
        } catch (Exception ex) {
            c.errors.put("Conf tree " + criterion.value, ex.getMessage());
            c.log.accept("[CONFLUENCE][ERROR] Tree load failed for " + criterion.value + ": " + ex.getMessage());
        }
    }

    // ── Jira: unified URL/key/keyword dispatch ────────────────────────────

    private void submitJiraSearches(SearchContext c) {
        CriteriaLists x = c.criteriaLists();
        if (x.hasKeyword() && c.credentials() && valid(c.jiraBase)) submitJiraKeywords(c, x.everywheres(), false);
        if (!x.jira().isEmpty() && c.credentials()) {
            SourceInputClassifier classifier = new SourceInputClassifier();
            for (SearchCriterion criterion : x.jira()) {
                if (criterion.value.isBlank()) continue;
                SourceInputClassifier.JiraInput ji = classifier.classifyJira(criterion.value);
                if (ji.isDirectFetch()) {
                    String base = ji.baseUrl() != null ? ji.baseUrl() : c.jiraBase;
                    c.futures.add(c.pool.submit(() -> loadJiraEntry(c, criterion, base, ji.key())));
                } else if (valid(c.jiraBase)) {
                    c.futures.add(c.pool.submit(() -> searchJiraKeyword(c, criterion, false)));
                }
            }
        }
    }

    private void submitJiraKeywords(SearchContext c, List<SearchCriterion> criteria, boolean criterionSearch) {
        for (SearchCriterion criterion : criteria)
            c.futures.add(c.pool.submit(() -> searchJiraKeyword(c, criterion, criterionSearch)));
    }

    /** Fetch a Jira issue (and its linked issues) directly by key. Used for both
     *  ISSUE_URL inputs (base extracted from the URL) and bare KEY inputs
     *  (base = configured Jira base). Results land in the {@code jiraUrlResults}
     *  bucket (direct fetch, not a keyword search). */
    private void loadJiraEntry(SearchContext c, SearchCriterion criterion, String base, String key) {
        try {
            c.log.accept("[JIRA] Loading issue: " + key + (base != null ? " @ " + base : ""));
            JiraClient client = new JiraClient(new AppConfig(c.username, c.token, base));
            com.conload.model.JiraIssue issue = client.getIssue(base, key);
            List<JiraTableItem> items = jiraItems(issue);
            List<String> related = relatedKeys(issue);
            if (!related.isEmpty()) {
                c.log.accept("[JIRA] Loading " + related.size() + " linked issue(s): " + related);
                for (com.conload.model.JiraIssue linked : client.getIssuesBatch(base, related)) items.addAll(jiraItems(linked));
            }
            synchronized (c.results.jiraUrlResults) { c.results.jiraUrlResults.add(new SearchResults.JiraCriterionResult(criterion.value, items, base)); }
            c.log.accept("[JIRA] " + Icons.CHECK + " Loaded " + items.size() + " issue(s) for " + key);
        } catch (Exception ex) {
            c.errors.put("Jira " + criterion.value, ex.getMessage());
            c.log.accept("[JIRA][ERROR] Load failed for " + criterion.value + ": " + ex.getMessage());
        }
    }

    private static List<JiraTableItem> jiraItems(com.conload.model.JiraIssue issue) {
        return new ArrayList<>(List.of(new JiraTableItem(issue.getKey(), name(issue.getFields().getIssueType()), name(issue.getFields().getStatus()), issue.getFields().getSummary())));
    }
    private static List<String> relatedKeys(com.conload.model.JiraIssue issue) {
        List<String> keys = new ArrayList<>();
        for (var subtask : issue.getFields().getSubtasks()) keys.add(subtask.getKey());
        for (var link : issue.getFields().getIssuelinks()) { var linked = link.getInwardIssue() != null ? link.getInwardIssue() : link.getOutwardIssue(); if (linked != null && !linked.getKey().isBlank()) keys.add(linked.getKey()); }
        return keys;
    }

    // ── GitHub: configurable API base + web-host-aware URL parsing ─────────

    private void submitGitHubSearches(SearchContext c) {
        CriteriaLists x = c.criteriaLists();
        if (c.ghToken.isBlank()) return;
        String webHost = GitHubClient.webHostFromApiUrl(c.ghApiUrl);
        x.ghCommits().forEach(v -> c.futures.add(c.pool.submit(() -> searchGitHubCommits(c, v))));
        x.ghCommitUrls().forEach(v -> c.futures.add(c.pool.submit(() -> loadGitHubCommit(c, v, webHost))));
        x.ghActionUrls().forEach(v -> c.futures.add(c.pool.submit(() -> loadGitHubAction(c, v, webHost))));
        x.ghPrs().forEach(v -> c.futures.add(c.pool.submit(() -> loadGitHubPr(c, v, webHost))));
    }
    private void searchGitHubCommits(SearchContext c, SearchCriterion criterion) {
        try { String[] repo = parseGitHubRepo(criterion.extraValue, GitHubClient.webHostFromApiUrl(c.ghApiUrl)); if (repo == null) { c.log.accept("[GITHUB][WARN] Could not parse repo: " + criterion.extraValue); return; }
            c.log.accept("[GITHUB] Searching commits in " + repo[0] + "/" + repo[1] + " for \"" + criterion.value + "\"");
            List<GitHubClient.GitCommit> commits = new GitHubClient(c.ghToken, c.ghApiUrl).searchCommits(repo[0], repo[1], criterion.value);
            synchronized (c.results.githubCommitResults) { c.results.githubCommitResults.add(new SearchResults.GitHubCriterionResult(criterion.value, commits, repo[0], repo[1])); }
            c.log.accept("[GITHUB] " + Icons.CHECK + " Found " + commits.size() + " commit(s)");
        } catch (Exception ex) { c.errors.put("GitHub " + criterion.value, ex.getMessage()); c.log.accept("[GITHUB][ERROR] Commit search failed: " + ex.getMessage()); }
    }
    private void loadGitHubCommit(SearchContext c, SearchCriterion criterion, String webHost) {
        try { c.log.accept("[GITHUB] Fetching commit: " + criterion.value); String[] info = parseGitHubCommitUrl(criterion.value, webHost); if (info == null) throw new Exception("Invalid GitHub commit URL format.");
            GitHubClient.GitCommit commit = new GitHubClient(c.ghToken, c.ghApiUrl).getCommit(info[0], info[1], info[2]); synchronized (c.results.githubCommitResults) { c.results.githubCommitResults.add(new SearchResults.GitHubCriterionResult(criterion.value, new ArrayList<>(List.of(commit)), info[0], info[1])); }
            c.log.accept("[GITHUB] " + Icons.CHECK + " Commit " + commit.shortSha() + " — \"" + commit.shortMessage() + "\"");
        } catch (Exception ex) { c.errors.put("GitHub commit " + criterion.value, ex.getMessage()); c.log.accept("[GITHUB][ERROR] Commit fetch failed: " + ex.getMessage()); }
    }
    private void loadGitHubAction(SearchContext c, SearchCriterion criterion, String webHost) {
        try { c.log.accept("[GITHUB] Fetching action run: " + criterion.value); String[] info = parseGitHubActionUrl(criterion.value, webHost); if (info == null) throw new Exception("Invalid GitHub Action URL format."); long runId = Long.parseLong(info[2]); Long jobId = info[3] == null ? null : Long.parseLong(info[3]);
            var run = new GitHubClient(c.ghToken, c.ghApiUrl).getActionRun(info[0], info[1], runId, jobId); synchronized (c.results.githubActionResults) { c.results.githubActionResults.add(new SearchResults.GitHubActionCriterionResult(criterion.value, run)); }
            c.log.accept("[GITHUB] " + Icons.CHECK + " Action run #" + runId + (jobId != null ? "  job " + jobId : "") + "  state=" + run.status() + "/" + run.conclusion());
        } catch (Exception ex) { c.errors.put("GitHub action " + criterion.value, ex.getMessage()); c.log.accept("[GITHUB][ERROR] Action run fetch failed: " + ex.getMessage()); }
    }
    private void loadGitHubPr(SearchContext c, SearchCriterion criterion, String webHost) {
        try { c.log.accept("[GITHUB] Fetching PR: " + criterion.value); String[] info = parseGitHubPrUrl(criterion.value, webHost); if (info == null) throw new Exception("Invalid GitHub PR URL format."); var pr = new GitHubClient(c.ghToken, c.ghApiUrl).getPullRequest(info[0], info[1], Integer.parseInt(info[2])); if (pr == null) { c.log.accept("[GITHUB][WARN] PR not found: " + criterion.value); return; }
            synchronized (c.results.githubPrResults) { c.results.githubPrResults.add(new SearchResults.GitHubPrCriterionResult(criterion.value, pr)); } c.log.accept("[GITHUB] " + Icons.CHECK + " PR #" + pr.number() + " \"" + pr.title() + "\"  state=" + pr.state());
        } catch (Exception ex) { c.errors.put("GitHub PR " + criterion.value, ex.getMessage()); c.log.accept("[GITHUB][ERROR] PR fetch failed: " + ex.getMessage()); }
    }

    private void searchConfluenceKeyword(SearchContext c, SearchCriterion criterion, boolean criterionSearch) {
        try { c.log.accept("[CONFLUENCE] " + (criterionSearch ? "Searching for criterion \"" : "Searching for \"") + criterion.value + "\" at " + c.confBase); var pages = new ConfluenceClient(new AppConfig(c.username, c.token, c.confBase)).searchPages(c.confBase, criterion.value, 50); List<PageSearchResult> res = new ArrayList<>(); for (var p : pages) res.add(new PageSearchResult(p.getId(), p.getTitle(), p.getSpace() != null ? p.getSpace().getKey() : "", p.getSpace() != null ? p.getSpace().getName() : "")); synchronized (c.results) { if (c.results.confSearch == null) c.results.confSearch = new ArrayList<>(); c.results.confSearch.addAll(res); c.results.confSearchBase = c.confBase; } c.log.accept("[CONFLUENCE] " + Icons.CHECK + (criterionSearch ? " Criterion \"" + criterion.value + "\" " + Icons.ARROW_RIGHT : " Keyword \"" + criterion.value + "\" →") + " " + res.size() + " page(s)");
        } catch (Exception ex) { String key = criterionSearch ? "Conf keyword " : "Confluence search"; c.errors.put(key + (criterionSearch ? criterion.value : ""), ex.getMessage()); c.log.accept("[CONFLUENCE][ERROR] " + (criterionSearch ? "Criterion \"" + criterion.value + "\": " : "Keyword search failed: ") + ex.getMessage()); }
    }
    private void searchJiraKeyword(SearchContext c, SearchCriterion criterion, boolean criterionSearch) {
        try { String jql = "text~\"" + criterion.value.replace("\"", "\\\"") + "\" ORDER BY updated DESC"; c.log.accept("[JIRA] " + (criterionSearch ? "Criterion search: " : "Searching: ") + jql); var issues = new JiraClient(new AppConfig(c.username, c.token, c.jiraBase)).searchIssues(c.jiraBase, jql, 50); List<JiraTableItem> items = new ArrayList<>(); for (var i : issues) items.add(new JiraTableItem(i.getKey(), name(i.getFields().getIssueType()), name(i.getFields().getStatus()), i.getFields().getSummary())); synchronized (c.results.jiraKeywordResults) { c.results.jiraKeywordResults.add(new SearchResults.JiraCriterionResult(criterion.value, items, c.jiraBase)); } c.log.accept("[JIRA] " + Icons.CHECK + (criterionSearch ? " Criterion \"" + criterion.value + "\" " + Icons.ARROW_RIGHT : " Keyword \"" + criterion.value + "\" →") + " " + items.size() + " issue(s)");
        } catch (Exception ex) { String key = criterionSearch ? "Jira keyword " : "Jira search"; c.errors.put(key + (criterionSearch ? criterion.value : ""), ex.getMessage()); c.log.accept("[JIRA][ERROR] " + (criterionSearch ? "Criterion \"" + criterion.value + "\": " : "Keyword search failed: ") + ex.getMessage()); }
    }
    private void loadChildrenRecursive(ConfluenceClient cl, String base, String parentId, TreeItem<PageTreeItem> parent, AtomicInteger cnt) { try { for (var child : cl.getChildPages(base, parentId)) { var data = new PageTreeItem(child.getId(), child.getTitle()); data.selected.set(true); var item = new TreeItem<>(data); parent.getChildren().add(item); cnt.incrementAndGet(); loadChildrenRecursive(cl, base, child.getId(), item, cnt); } } catch (Exception ignored) { } }
    private static void waitForSearches(List<Future<?>> futures) { for (Future<?> future : futures) try { future.get(); } catch (Exception ignored) { } }
    private static boolean valid(String value) { return value != null && !value.isBlank(); }
    private static String name(com.conload.model.JiraIssue.NamedObject obj) { return obj != null ? obj.getName() : ""; }

    /**
     * Parse an {@code owner/repo} (or {@code https://<host>/owner/repo}) into
     * {@code [owner, repo]}. The host defaults to {@code github.com} but honors
     * a configured GitHub Enterprise host.
     */
    public static String[] parseGitHubRepo(String ghRepo) {
        return parseGitHubRepo(ghRepo, "github.com");
    }
    public static String[] parseGitHubRepo(String ghRepo, String webHost) {
        if (ghRepo == null || ghRepo.isBlank()) return null;
        String s = ghRepo.strip();
        String host = (webHost == null || webHost.isBlank()) ? "github.com" : webHost;
        String prefix = "https://" + host + "/";
        if (s.startsWith(prefix)) s = s.substring(prefix.length());
        if (s.endsWith(".git")) s = s.substring(0, s.length() - 4);
        s = s.replaceAll("/$", "");
        String[] parts = s.split("/", 2);
        return parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank() ? parts : null;
    }

    public static String[] parseGitHubPrUrl(String url) { return parseGitHubPrUrl(url, "github.com"); }
    public static String[] parseGitHubPrUrl(String url, String webHost) {
        String host = (webHost == null || webHost.isBlank()) ? "github.com" : java.util.regex.Pattern.quote(webHost);
        var m = java.util.regex.Pattern.compile(host + "/([^/]+)/([^/]+)/pull/(\\d+)").matcher(url);
        return m.find() ? new String[]{m.group(1), m.group(2), m.group(3)} : null;
    }

    public static String[] parseGitHubCommitUrl(String url) { return parseGitHubCommitUrl(url, "github.com"); }
    public static String[] parseGitHubCommitUrl(String url, String webHost) {
        if (url == null || url.isBlank()) return null;
        String host = (webHost == null || webHost.isBlank()) ? "github.com" : java.util.regex.Pattern.quote(webHost);
        var m = java.util.regex.Pattern.compile(host + "/([^/]+)/([^/]+)/commit/([0-9a-fA-F]{7,40})").matcher(url);
        return m.find() ? new String[]{m.group(1), m.group(2), m.group(3)} : null;
    }

    public static String[] parseGitHubActionUrl(String url) { return parseGitHubActionUrl(url, "github.com"); }
    public static String[] parseGitHubActionUrl(String url, String webHost) {
        if (url == null || url.isBlank()) return null;
        String host = (webHost == null || webHost.isBlank()) ? "github.com" : java.util.regex.Pattern.quote(webHost);
        var m = java.util.regex.Pattern.compile(host + "/([^/]+)/([^/]+)/actions/runs/(\\d+)(?:/job/(\\d+))?").matcher(url);
        if (!m.find()) return null;
        return new String[]{m.group(1), m.group(2), m.group(3), m.group(4)};
    }

    private record SearchContext(String username, String token, String ghToken, String ghApiUrl, String confBase, String jiraBase, List<SearchCriterion> criteria, SearchResults results, Map<String, String> errors, java.util.function.Consumer<String> log, List<Future<?>> futures, ExecutorService pool) {
        boolean credentials() { return !username.isBlank() && !token.isBlank(); }
        CriteriaLists criteriaLists() {
            return new CriteriaLists(
                    criteria.stream().filter(c -> c.type == CriteriaType.EVERYWHERE && !c.value.isBlank()).toList(),
                    criteria.stream().filter(c -> c.type == CriteriaType.CONFLUENCE && !c.value.isBlank()).toList(),
                    criteria.stream().filter(c -> c.type == CriteriaType.JIRA && !c.value.isBlank()).toList(),
                    criteria.stream().filter(c -> c.type == CriteriaType.GITHUB_COMMIT && !c.value.isBlank() && !c.extraValue.isBlank()).toList(),
                    criteria.stream().filter(c -> c.type == CriteriaType.GITHUB_COMMIT_URL && !c.value.isBlank()).toList(),
                    criteria.stream().filter(c -> c.type == CriteriaType.GITHUB_ACTION_URL && !c.value.isBlank()).toList(),
                    criteria.stream().filter(c -> c.type == CriteriaType.GITHUB_PR && !c.value.isBlank()).toList());
        }
    }
    private record CriteriaLists(List<SearchCriterion> everywheres, List<SearchCriterion> confluence,
                                 List<SearchCriterion> jira, List<SearchCriterion> ghCommits,
                                 List<SearchCriterion> ghCommitUrls, List<SearchCriterion> ghActionUrls,
                                 List<SearchCriterion> ghPrs) {
        boolean hasKeyword() { return !everywheres.isEmpty(); }
    }
}
