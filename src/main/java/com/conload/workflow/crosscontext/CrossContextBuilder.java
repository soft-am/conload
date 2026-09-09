package com.conload.workflow.crosscontext;

import com.conload.confluence.ConfluenceClient;
import com.conload.confluence.ConfluenceUrlParser;
import com.conload.github.GitHubClient;
import com.conload.jira.JiraClient;
import com.conload.jira.JiraMarkdownExporter;
import com.conload.model.ConfluencePage;
import com.conload.model.JiraIssue;
import com.conload.service.AttachmentDownloaderService;
import com.conload.service.search.SourceInputClassifier;
import com.conload.util.FileUtil;
import com.conload.workflow.WorkflowCallbacks;
import com.conload.workflow.WorkflowEnvironment;
import com.conload.workflow.WorkflowStoppedException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified cross-context builder — the single entry point for gathering
 * cross-context from three sources:
 * <ol>
 *   <li><b>Jira</b> keys → recursive jira/confluence/commits expansion</li>
 *   <li><b>Confluence</b> URL → tree download → discover Jira keys → same as (1)</li>
 *   <li><b>GitHub PR</b> → diff + commit messages → discover Jira keys + Confluence URLs → same as (1)</li>
 * </ol>
 *
 * <pre>
 *                     +-----------------------------+
 *                     |   createCrossContext(...)   |
 *                     +--------------+--------------+
 *                                    |
 *            +-----------------------+-----------------------+
 *            v                       v                       v
 *    [1. Jira keys]          [2. Confluence URL]    [3. GitHub PR]
 *            |                       |                       |
 *            +-----------------------+-----------------------+
 *                                    | normalizeSource
 *                                    v
 *                       +---------------------------+
 *                       |  Context Traversal Engine|
 *                       +-------------+-------------+
 *                                    |
 *            +-----------------------+-----------------------+
 *            v                                               v
 *    +-----------------+      <— inter-links —>        +-----------------+
 *    | Jira Processor  |  (confluence↔jira loop)      | Confluence Proc |
 *    +--------+--------+                             +-----------------+
 *             |
 *             +-----------------------------> +-----------------+
 *                                           | Git Commit Proc |
 *                                           +--------+--------+
 *                                                    |
 *                                                    v
 *                                           +-----------------+
 *                                           | Keyword Analysis|
 *                                           +--------+--------+
 *                                                    |
 *                                                    v
 *                                           +-----------------+
 *                                           | Hierarchy Writer|
 *                                           +-----------------+
 * </pre>
 */
public final class CrossContextBuilder {

    private static final Pattern CONFLUENCE_URL =
            Pattern.compile("https?://[^\\s\"<>]+/wiki/(?:spaces/[^/]+/)?(?:pages/\\d+|[^\\s\"<>]*pageId=\\d+)");

    private final WorkflowEnvironment env;
    private final String ownerRepo;
    private final WorkflowCallbacks callbacks;
    private final String baseUrl;
    private final boolean fullMode;

    private JiraClient jiraClient;
    private ConfluenceClient confluenceClient;
    private ConfluenceUrlParser confluenceUrlParser;
    private AttachmentDownloaderService attachmentDownloader;
    private JiraMarkdownExporter jiraExporter;
    private JiraKeyDiscoverer keyDiscoverer;
    private ConfluenceTreeWriter confluenceWriter;
    private CommitsAggregator commitsAggregator;
    private WordFrequencyAnalyzer wordAnalyzer;
    private HierarchyDocWriter hierarchyWriter;

    private final Set<String> visitedJira = new LinkedHashSet<>();
    private final Set<String> visitedConf = new LinkedHashSet<>();
    private final Set<String> visitedCommits = new LinkedHashSet<>();
    private final List<String> epics = new ArrayList<>();
    private final TitleWords titleWords = new TitleWords();
    private final SourceInputClassifier classifier = new SourceInputClassifier();
    private final int[] counts = {0, 0, 0};

    /** Maps a leaf file/directory name → list of original source URLs for that
     *  entry. Populated by collaborators as they write files; consumed by
     *  {@link HierarchyDocWriter#render} to append URLs to each hierarchy line. */
    private final Map<String, List<String>> sourceUrls = new LinkedHashMap<>();

    /**
     * Unified entry point. Creates the builder internally, runs all phases,
     * and returns the result.
     *
     * @param fullMode  true = full deep recursion (epic children, unlimited depth,
     *                  top-word Confluence discovery); false = limited depth,
     *                  no epic children recursion, no word-discovery phase
     */
    public static CrossContextResult createCrossContext(
            CrossContextSource source, WorkflowEnvironment env,
            String ownerRepo, Path contextRoot, WorkflowCallbacks callbacks,
            boolean fullMode) throws Exception {
        var builder = new CrossContextBuilder(env, ownerRepo, callbacks, fullMode);
        return builder.build(source, contextRoot);
    }

    private CrossContextBuilder(WorkflowEnvironment env, String ownerRepo,
                                 WorkflowCallbacks callbacks, boolean fullMode) {
        this.env = env;
        this.ownerRepo = ownerRepo;
        this.callbacks = callbacks;
        this.baseUrl = env.config().getBaseUrl();
        this.fullMode = fullMode;
    }

    // ── Main pipeline ────────────────────────────────────────────────────

    private CrossContextResult build(CrossContextSource source, Path contextRoot) throws Exception {
        initCollaborators();
        confluenceWriter = new ConfluenceTreeWriter(confluenceClient, confluenceUrlParser,
                attachmentDownloader, callbacks, sourceUrls);
        commitsAggregator = new CommitsAggregator(env.githubToken(), env.githubApiUrl(),
                ownerRepo, callbacks, sourceUrls);

        callbacks.onLog("[CROSS] Context root: " + contextRoot
                + "  (mode: " + (fullMode ? "FULL" : "NOT-FULL") + ")");

        // Phase A: normalize source into jira keys + extra confluence page IDs
        NormalizeResult seeds = normalizeSource(source, contextRoot);
        registerRootSeedUrls(source, contextRoot, seeds);
        callbacks.onLog("[CROSS] Seeds: " + seeds.jiraKeys.size() + " Jira key(s), "
                + seeds.extraConfluencePageIds.size() + " extra Confluence page(s)");

        // Phase B: recursive jira expansion for each seed key
        for (String key : seeds.jiraKeys) {
            if (callbacks.isCancelled()) break;
            expandJira(key, 0, contextRoot);
        }

        // Phase C: extra confluence trees (from PR scan)
        for (String pid : seeds.extraConfluencePageIds) {
            if (callbacks.isCancelled()) break;
            confluenceWriter.download(baseUrl, pid, contextRoot, "", visitedConf, counts);
        }

        // Phase D: expand discovered epics
        for (String epicKey : new ArrayList<>(epics)) {
            if (callbacks.isCancelled()) break;
            expandJira(epicKey, 0, contextRoot);
        }

        // Phase E: top-word Confluence discovery (full mode only)
        if (fullMode) runOftenWordsPhase(contextRoot);

        // Phase F: hierarchy document
        hierarchyWriter.render(contextRoot, counts[0], counts[1], counts[2], sourceUrls);

        callbacks.onProgress("Done", "Cross-context complete: "
                + counts[0] + " Jira, " + counts[1] + " Confluence, " + counts[2] + " commits.");

        return new CrossContextResult(contextRoot, counts[0], counts[1], counts[2],
                contextRoot.resolve("cross_context_hierarchy.md"));
    }

    private void initCollaborators() {
        jiraClient = new JiraClient(env.config());
        confluenceClient = new ConfluenceClient(env.config());
        confluenceUrlParser = new ConfluenceUrlParser();
        attachmentDownloader = new AttachmentDownloaderService(confluenceClient, callbacks::onLog);
        jiraExporter = new JiraMarkdownExporter(env.config(), callbacks::onLog);
        keyDiscoverer = new JiraKeyDiscoverer(callbacks);
        wordAnalyzer = new WordFrequencyAnalyzer(callbacks);
        hierarchyWriter = new HierarchyDocWriter(callbacks);
    }

    // ── Phase A: normalize source ───────────────────────────────────────

    private record NormalizeResult(List<String> jiraKeys, List<String> extraConfluencePageIds) {}

    private NormalizeResult normalizeSource(CrossContextSource source, Path contextRoot) throws Exception {
        return switch (source) {
            case CrossContextSource.Jira j -> new NormalizeResult(
                    j.keys(),
                    resolveExtraConfluence(j.extraConfluenceUrl()));
            case CrossContextSource.Confluence c -> normalizeConfluence(c, contextRoot);
            case CrossContextSource.GitHubPr pr -> normalizeGitHubPr(pr, contextRoot);
            case CrossContextSource.GitHubPrs prs -> normalizeGitHubPrs(prs, contextRoot);
        };
    }

    /** Register the original source URLs for the context-root line in the
     *  hierarchy doc, keyed by the root folder name so {@link HierarchyDocWriter}
     *  can append them to the top-level entry. */
    private void registerRootSeedUrls(CrossContextSource source, Path contextRoot, NormalizeResult seeds) {
        String rootName = contextRoot.getFileName().toString();
        switch (source) {
            case CrossContextSource.Jira j -> {
                List<String> urls = new ArrayList<>();
                for (String k : seeds.jiraKeys) urls.add(jiraBrowseUrl(k));
                if (!urls.isEmpty()) sourceUrls.put(rootName, urls);
            }
            case CrossContextSource.Confluence c -> {
                if (!c.pageUrls().isEmpty()) sourceUrls.put(rootName, new ArrayList<>(c.pageUrls()));
            }
            case CrossContextSource.GitHubPr pr -> sourceUrls.put(rootName,
                    List.of(githubPrUrl(pr.ownerRepo(), pr.prNumber())));
            case CrossContextSource.GitHubPrs prs -> {
                List<String> urls = new ArrayList<>();
                for (var p : prs.prs()) urls.add(githubPrUrl(p.ownerRepo(), p.prNumber()));
                if (!urls.isEmpty()) sourceUrls.put(rootName, urls);
            }
        }
    }

    private String jiraBrowseUrl(String key) {
        return baseUrl + "/browse/" + key;
    }

    private static String githubPrUrl(String ownerRepo, int prNumber) {
        return "https://github.com/" + ownerRepo + "/pull/" + prNumber;
    }

    /** Associate a single URL with a leaf file/directory name, preserving the
     *  first registration if the same key appears again (deduplication). */
    private void addSourceUrl(String leafName, String url) {
        if (leafName == null || leafName.isBlank() || url == null || url.isBlank()) return;
        sourceUrls.computeIfAbsent(leafName, k -> new ArrayList<>()).add(url);
    }

    /**
     * Resolve the "additional Confluence" input that accompanies a Jira-keyed
     * source. A URL (parseable page ID) seeds the extra-Confluence page trees
     * directly; a keyword runs a CQL text search and seeds the matched pages —
     * "if URL, code makes [a] search tree; if word, word".
     */
    private List<String> resolveExtraConfluence(String extraConfluenceUrl) {
        if (extraConfluenceUrl == null || extraConfluenceUrl.isBlank()) return List.of();
        SourceInputClassifier.ConfluenceInput ci = classifier.classifyConfluence(extraConfluenceUrl);
        if (ci.isTreeUrl()) return ci.pageId() != null ? List.of(ci.pageId()) : List.of();
        if (ci.value().isBlank()) return List.of();
        List<String> pageIds = new ArrayList<>();
        try {
            callbacks.onProgress("Confluence", "Searching pages for \"" + ci.value() + "\"…");
            for (ConfluencePage p : confluenceClient.searchPages(baseUrl, ci.value(),
                    CrossContextLimits.MAX_CONFLUENCE_SEARCH_RESULTS)) {
                if (p.getId() != null && !p.getId().isBlank()) pageIds.add(p.getId());
            }
            callbacks.onLog("[CROSS] Keyword \"" + ci.value() + "\" → " + pageIds.size() + " Confluence page(s)");
        } catch (Exception e) {
            if (callbacks.isHardStopped() || WorkflowCallbacks.isInterruptCause(e)) {
                throw new WorkflowStoppedException(e);
            }
            callbacks.onError("Confluence", "Keyword search failed: " + e.getMessage());
        }
        return pageIds;
    }

    private NormalizeResult normalizeConfluence(CrossContextSource.Confluence c, Path contextRoot) {
        for (String pageUrl : c.pageUrls()) {
            String pageId = confluenceUrlParser.extractPageId(pageUrl);
            if (pageId == null) {
                callbacks.onLog("[CROSS] Could not extract Confluence page ID from: " + pageUrl);
                continue;
            }
            confluenceWriter.download(baseUrl, pageId, contextRoot, "", visitedConf, counts);
        }
        List<String> jiraKeys = keyDiscoverer.fromDirectory(contextRoot);
        callbacks.onLog("[CROSS] Discovered " + jiraKeys.size() + " Jira key(s) from Confluence pages");
        return new NormalizeResult(jiraKeys, List.of());
    }

    private NormalizeResult normalizeGitHubPr(CrossContextSource.GitHubPr pr, Path contextRoot) {
        String[] parts = pr.ownerRepo().split("/", 2);
        if (parts.length < 2) return new NormalizeResult(List.of(), List.of());
        String owner = parts[0].strip();
        String repo = parts[1].strip();

        GitHubClient ghClient = new GitHubClient(env.githubToken(), env.githubApiUrl());
        GitHubClient.GitPullRequest pullRequest;
        try {
            pullRequest = ghClient.getPullRequest(owner, repo, pr.prNumber());
        } catch (Exception e) {
            if (callbacks.isHardStopped() || WorkflowCallbacks.isInterruptCause(e)) {
                throw new WorkflowStoppedException(e);
            }
            callbacks.onError("GitHub", "PR fetch failed: " + e.getMessage());
            return new NormalizeResult(List.of(), List.of());
        }

        Path githubDir = contextRoot.resolve("github");
        FileUtil.ensureDir(githubDir);
        savePrArtifacts(githubDir, pr.prNumber(), pullRequest);
        String prUrl = githubPrUrl(pr.ownerRepo(), pr.prNumber());
        addSourceUrl("pr_" + pr.prNumber() + ".json", prUrl);
        addSourceUrl("pr_" + pr.prNumber() + "-diff.json", prUrl);

        List<String> commitMessages = new ArrayList<>();
        try {
            for (GitHubClient.GitCommit c : ghClient.getPrCommits(owner, repo, pr.prNumber())) {
                commitMessages.add(c.message() != null ? c.message() : "");
            }
        } catch (Exception e) {
            if (callbacks.isHardStopped() || WorkflowCallbacks.isInterruptCause(e)) {
                throw new WorkflowStoppedException(e);
            }
            callbacks.onError("GitHub", "PR commits fetch failed: " + e.getMessage());
        }

        String allText = pullRequest.title() + "\n"
                + (pullRequest.body() != null ? pullRequest.body() : "")
                + "\n" + String.join("\n", commitMessages);
        List<String> jiraKeys = JiraKeyDiscoverer.fromText(allText);
        List<String> confPageIds = extractConfluencePageIds(allText);

        callbacks.onLog("[CROSS] PR #" + pr.prNumber() + ": " + jiraKeys.size()
                + " Jira key(s), " + confPageIds.size() + " Confluence page(s)");
        return new NormalizeResult(jiraKeys, confPageIds);
    }

    /**
     * Normalize multiple GitHub PRs (possibly from different repos). Each PR is
     * fetched and its artifacts saved; discovered Jira keys and Confluence page
     * IDs are accumulated into shared deduplicated lists so the recursive core
     * runs once over all combined seeds.
     */
    private NormalizeResult normalizeGitHubPrs(CrossContextSource.GitHubPrs prs, Path contextRoot) {
        GitHubClient ghClient = new GitHubClient(env.githubToken(), env.githubApiUrl());
        Path githubDir = contextRoot.resolve("github");
        FileUtil.ensureDir(githubDir);

        Set<String> allJiraKeys = new LinkedHashSet<>();
        List<String> allConfPageIds = new ArrayList<>();

        for (CrossContextSource.GitHubPr pr : prs.prs()) {
            if (callbacks.isCancelled()) break;
            String[] parts = pr.ownerRepo().split("/", 2);
            if (parts.length < 2) {
                callbacks.onLog("[CROSS] Skipping invalid owner/repo: " + pr.ownerRepo());
                continue;
            }
            String owner = parts[0].strip();
            String repo = parts[1].strip();

            GitHubClient.GitPullRequest pullRequest;
            try {
                callbacks.onProgress("GitHub", "Fetching PR #" + pr.prNumber() + " from " + pr.ownerRepo() + "…");
                pullRequest = ghClient.getPullRequest(owner, repo, pr.prNumber());
            } catch (Exception e) {
                if (callbacks.isHardStopped() || WorkflowCallbacks.isInterruptCause(e)) {
                    throw new WorkflowStoppedException(e);
                }
                callbacks.onError("GitHub", "PR #" + pr.prNumber() + " fetch failed: " + e.getMessage());
                continue;
            }

            savePrArtifacts(githubDir, pr.prNumber(), pullRequest);
            String prUrl = githubPrUrl(pr.ownerRepo(), pr.prNumber());
            addSourceUrl("pr_" + pr.prNumber() + ".json", prUrl);
            addSourceUrl("pr_" + pr.prNumber() + "-diff.json", prUrl);

            List<String> commitMessages = new ArrayList<>();
            try {
                for (GitHubClient.GitCommit c : ghClient.getPrCommits(owner, repo, pr.prNumber())) {
                    commitMessages.add(c.message() != null ? c.message() : "");
                }
            } catch (Exception e) {
                if (callbacks.isHardStopped() || WorkflowCallbacks.isInterruptCause(e)) {
                    throw new WorkflowStoppedException(e);
                }
                callbacks.onError("GitHub", "PR #" + pr.prNumber() + " commits fetch failed: " + e.getMessage());
            }

            String allText = pullRequest.title() + "\n"
                    + (pullRequest.body() != null ? pullRequest.body() : "")
                    + "\n" + String.join("\n", commitMessages);
            allJiraKeys.addAll(JiraKeyDiscoverer.fromText(allText));
            allConfPageIds.addAll(extractConfluencePageIds(allText));

            callbacks.onLog("[CROSS] PR #" + pr.prNumber() + " (" + pr.ownerRepo() + "): "
                    + allJiraKeys.size() + " cumulative Jira key(s), "
                    + allConfPageIds.size() + " cumulative Confluence page(s)");
        }

        return new NormalizeResult(new ArrayList<>(allJiraKeys), allConfPageIds);
    }

    private void savePrArtifacts(Path githubDir, int prNumber, GitHubClient.GitPullRequest pr) {
        try {
            String prJson = pr.rawJson() != null ? pr.rawJson() : "{}";
            FileUtil.writeText(githubDir.resolve("pr_" + prNumber + ".json"), prJson);
            if (pr.diffContent() != null && !pr.diffContent().isBlank()) {
                FileUtil.writeText(githubDir.resolve("pr_" + prNumber + "-diff.json"), pr.diffContent());
            }
            callbacks.onLog("[CROSS] ✓ PR #" + prNumber + " saved");
        } catch (Exception e) {
            callbacks.onError("GitHub", "PR save failed: " + e.getMessage());
        }
    }

    private List<String> extractConfluencePageIds(String text) {
        Set<String> pageIds = new LinkedHashSet<>();
        Matcher m = CONFLUENCE_URL.matcher(text);
        while (m.find()) {
            String pid = confluenceUrlParser.extractPageId(m.group());
            if (pid != null) pageIds.add(pid);
        }
        return new ArrayList<>(pageIds);
    }

    // ── Phase B/D: recursive jira expansion ────────────────────────────

    private void expandJira(String key, int depth, Path parentDir) {
        if (key == null || key.isBlank() || visitedJira.contains(key) || callbacks.isCancelled()) return;
        visitedJira.add(key);

        boolean isEpic = depth == 0 && epics.contains(key);
        String folderName = isEpic ? key + " (Epic)" : key;
        Path jiraDir = parentDir.resolve(folderName);
        FileUtil.ensureDir(jiraDir);

        JiraIssue issue = exportJiraMd(key, jiraDir);
        if (issue == null) return;

        String browseUrl = jiraBrowseUrl(key);
        addSourceUrl(folderName, browseUrl);
        addSourceUrl("jira_" + key + ".md", browseUrl);

        if (depth == 0) titleWords.setMain(issue.getFields().getSummary());

        String epicKey = keyDiscoverer.epicLinkKey(jiraClient, baseUrl, key);
        if (!epicKey.isBlank() && !epics.contains(epicKey)) {
            epics.add(epicKey);
            callbacks.onLog("[CROSS] Epic for " + key + ": " + epicKey);
        }
        if (isEpic) titleWords.setEpic(issue.getFields().getSummary());

        downloadLinkedConfluence(issue, jiraDir);
        counts[2] += commitsAggregator.writeCommits(jiraDir, key, visitedCommits);

        int maxDepth = fullMode ? CrossContextLimits.MAX_DEPTH_FULL : CrossContextLimits.MAX_DEPTH_NON_FULL;
        if (depth >= maxDepth) return;

        expandRelatedKeys(key, issue, jiraDir, depth);
    }

    private void expandRelatedKeys(String key, JiraIssue issue, Path jiraDir, int depth) {
        Set<String> relatedKeys = new LinkedHashSet<>(JiraKeyDiscoverer.relatedKeys(issue));
        for (String rk : keyDiscoverer.fromDirectory(jiraDir)) {
            if (!rk.equals(key)) relatedKeys.add(rk);
        }
        for (String ck : keyDiscoverer.childKeys(jiraClient, baseUrl, key,
                CrossContextLimits.MAX_CHILD_ISSUES)) {
            if (!ck.equals(key)) relatedKeys.add(ck);
        }
        int maxRelated = fullMode ? CrossContextLimits.MAX_RELATED_FULL
                                  : CrossContextLimits.MAX_RELATED_NON_FULL;
        if (relatedKeys.size() > maxRelated) {
            relatedKeys = new LinkedHashSet<>(
                    new ArrayList<>(relatedKeys).subList(0, maxRelated));
        }

        Path relatedDir = jiraDir.resolve("related_context");
        FileUtil.ensureDir(relatedDir);
        for (String rk : relatedKeys) {
            if (callbacks.isCancelled()) break;
            expandJira(rk, depth + 1, relatedDir);
        }
    }

    private JiraIssue exportJiraMd(String key, Path jiraDir) {
        try {
            callbacks.onProgress("Jira", "Fetching " + key + "…");
            JiraIssue issue = jiraExporter.fetchAndExport(baseUrl, key, jiraDir);
            counts[0]++;
            renameJiraMd(jiraDir, key);
            return issue;
        } catch (Exception e) {
            if (callbacks.isHardStopped() || WorkflowCallbacks.isInterruptCause(e)) {
                throw new WorkflowStoppedException(e);
            }
            callbacks.onError("Jira", "Failed to fetch " + key + ": " + e.getMessage());
            return null;
        }
    }

    private void downloadLinkedConfluence(JiraIssue issue, Path jiraDir) {
        List<String> pageIds = keyDiscoverer.confluencePageIds(
                jiraClient, issue, baseUrl, confluenceUrlParser);
        for (String pid : pageIds) {
            if (callbacks.isCancelled()) break;
            confluenceWriter.download(baseUrl, pid, jiraDir, "", visitedConf, counts);
        }
    }

    private void renameJiraMd(Path dir, String key) {
        File[] files = dir.toFile().listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith(key + " - ") && f.getName().endsWith(".md")) {
                Path target = dir.resolve("jira_" + key + ".md");
                try {
                    Files.move(f.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) { /* best-effort */ }
                return;
            }
        }
    }

    // ── Phase E: top-word Confluence discovery ─────────────────────────

    private void runOftenWordsPhase(Path contextRoot) {
        callbacks.onProgress("Analysis", "Analyzing word frequency across context…");
        List<String> topWords = wordAnalyzer.topStrict(contextRoot, titleWords.allTitleTokens());

        if (topWords.isEmpty()) {
            callbacks.onLog("[WORDS] No matching words found — skipping Confluence search.");
            return;
        }

        Path oftenDir = contextRoot.resolve("often_words_confluence_pages");
        FileUtil.ensureDir(oftenDir);

        for (String word : topWords) {
            if (callbacks.isCancelled()) break;
            searchConfluenceForWord(word, oftenDir);
        }
    }

    private void searchConfluenceForWord(String word, Path oftenDir) {
        try {
            callbacks.onProgress("Confluence", "Searching pages for \"" + word + "\"…");
            List<ConfluencePage> pages = confluenceClient.searchPages(baseUrl, word,
                    CrossContextLimits.MAX_CONFLUENCE_SEARCH_RESULTS);
            for (ConfluencePage page : pages) {
                if (callbacks.isCancelled()) break;
                confluenceWriter.download(baseUrl, page.getId(), oftenDir, "", visitedConf, counts);
            }
        } catch (Exception e) {
            if (callbacks.isHardStopped() || WorkflowCallbacks.isInterruptCause(e)) {
                throw new WorkflowStoppedException(e);
            }
            callbacks.onError("Confluence", "Search failed for \"" + word + "\": " + e.getMessage());
        }
    }
}
