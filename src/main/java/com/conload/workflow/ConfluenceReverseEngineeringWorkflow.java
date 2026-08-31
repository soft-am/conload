package com.conload.workflow;

import com.conload.confluence.ConfluenceClient;
import com.conload.model.ConfluencePage;
import com.conload.service.search.SourceInputClassifier;
import com.conload.util.FileUtil;
import com.conload.workflow.crosscontext.CrossContextBuilder;
import com.conload.workflow.crosscontext.CrossContextLimits;
import com.conload.workflow.crosscontext.CrossContextResult;
import com.conload.workflow.crosscontext.CrossContextSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The "Confluence Reverse Engineering" workflow — starts from Confluence
 * pages (cross-context source type 2). The user provides a Confluence page
 * URL or a search keyword:
 * <ul>
 *   <li><b>URL</b> — the page tree below that page is downloaded, then Jira
 *       keys are discovered from the page content and the recursive
 *       cross-context core takes over.</li>
 *   <li><b>Keyword</b> — a CQL search runs; the top 3 result pages are used
 *       as starting points (each tree is downloaded into the same context
 *       root before Jira-key discovery).</li>
 * </ul>
 * The gathered context is substituted into a prompt template that instructs
 * the CLI agent to reverse-engineer the architecture/system from the
 * Confluence documentation.
 */
public final class ConfluenceReverseEngineeringWorkflow implements Workflow {

    private static final String ID = "confluence-reverse-engineering";
    private static final String TEMPLATE = "/defaults/workflows/confluence-reverse-engineering.md.tpl";

    @Override
    public String id() { return ID; }

    @Override
    public String displayName() { return "Confluence Reverse Engineering"; }

    @Override
    public String description() {
        return "Downloads Confluence page trees (from a URL or keyword search), discovers "
               + "related Jira issues and GitHub commits, and generates a prompt for the "
               + "CLI agent to reverse-engineer the documented system architecture.";
    }

    @Override
    public List<WorkflowFieldDefinition> inputFields() {
        return List.of(
                new WorkflowFieldDefinition(
                        "confluenceInput",
                        "Confluence Page URL or Search Keyword",
                        "https://yoursite.atlassian.net/wiki/.../pages/123/Title  or  a search keyword",
                        true, false),
                new WorkflowFieldDefinition(
                        "githubOwnerRepo",
                        "GitHub Owner / Repo (auto-detected, editable)",
                        "owner/repo or https://<host>/owner/repo",
                        false, false),
                new WorkflowFieldDefinition(
                        "fullMode",
                        "Full Mode (deep recursion + epic children + word discovery)",
                        "",
                        false, false, true)
        );
    }

    @Override
    public WorkflowContext accumulate(WorkflowEnvironment env, WorkflowInputs inputs,
                                       WorkflowCallbacks callbacks) throws Exception {

        String confluenceInput = inputs.get("confluenceInput").strip();
        if (confluenceInput.isBlank()) {
            throw new IllegalArgumentException("Confluence page URL or keyword is required.");
        }

        String baseUrl = env.config().getBaseUrl();
        SourceInputClassifier classifier = new SourceInputClassifier();
        SourceInputClassifier.ConfluenceInput ci = classifier.classifyConfluence(confluenceInput);

        List<String> pageUrls = resolvePageUrls(ci, baseUrl, env, callbacks);
        if (pageUrls.isEmpty()) {
            throw new IllegalArgumentException(
                    "Could not resolve any Confluence pages from: " + confluenceInput);
        }

        callbacks.onLog("[CONF-REVERSE] Resolved " + pageUrls.size() + " Confluence page URL(s)");

        String folderName = "conf_reverse_" + System.currentTimeMillis();
        Path contextRoot = env.contextsDir().resolve(FileUtil.normalizeContextFolderName(folderName));
        Files.createDirectories(contextRoot);
        callbacks.onLog("Context root: " + contextRoot);

        String ownerRepo = inputs.get("githubOwnerRepo").strip();
        if (ownerRepo.isEmpty()) {
            ownerRepo = GitRemoteResolver.resolveOwnerRepo(env.workspacePath());
            callbacks.onLog("[GITHUB] Auto-detected owner/repo: " + ownerRepo);
        } else {
            ownerRepo = PrepareToRefinementWorkflow.normalizeOwnerRepo(ownerRepo);
        }

        String confluenceDataSource = env.fullConfluenceFolder() != null
                ? env.fullConfluenceFolder() : "";

        boolean fullMode = !"false".equalsIgnoreCase(inputs.get("fullMode"));
        CrossContextResult ccResult = CrossContextBuilder.createCrossContext(
                new CrossContextSource.Confluence(pageUrls),
                env, ownerRepo, contextRoot, callbacks, fullMode);

        callbacks.onLog("Cross-context: " + ccResult.jiraCount() + " Jira, "
                + ccResult.confluenceCount() + " Confluence, " + ccResult.commitCount() + " commits.");

        // ── Output doc path ────────────────────────────────────────────────
        // The generated document lives in the shared project contexts dir
        // under doc/cross_context/ (shared across all worktrees of a project,
        // not pinned to a git checkout).
        Path outputDocPath = Workflow.resolveOutputDoc(env, "confluence-reverse-engineering.md");

        return new WorkflowContext(
                contextRoot,
                contextRoot,
                contextRoot,
                contextRoot,
                ci.value(),
                List.of(),
                List.of(),
                List.of(),
                outputDocPath,
                ownerRepo,
                confluenceDataSource,
                env.workspacePath()
        );
    }

    @Override
    public String templateResource() { return TEMPLATE; }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Resolve the user input into one or more Confluence page URLs. A URL
     * input is used directly; a keyword triggers a CQL search whose top
     * top {@value com.conload.workflow.crosscontext.CrossContextLimits#KEYWORD_TOP_RESULTS} results become the starting page URLs
     * (each as {@code <baseUrl>?pageId=<id>}, which the URL parser handles).
     */
    private List<String> resolvePageUrls(
            SourceInputClassifier.ConfluenceInput ci, String baseUrl,
            WorkflowEnvironment env, WorkflowCallbacks callbacks) {

        if (ci.isTreeUrl()) {
            return List.of(ci.value());
        }

        if (ci.value().isBlank()) {
            callbacks.onLog("[CONF-REVERSE] Empty keyword input.");
            return List.of();
        }

        List<String> urls = new ArrayList<>();
        try {
            callbacks.onProgress("Confluence", "Searching pages for \"" + ci.value() + "\"…");
            ConfluenceClient client = new ConfluenceClient(env.config());
            List<ConfluencePage> pages = client.searchPages(baseUrl, ci.value(),
                    CrossContextLimits.KEYWORD_TOP_RESULTS);
            for (ConfluencePage page : pages) {
                if (page.getId() != null && !page.getId().isBlank()) {
                    urls.add(baseUrl + "/wiki/pages/viewpage.action?pageId=" + page.getId());
                }
            }
            callbacks.onLog("[CONF-REVERSE] Keyword \"" + ci.value() + "\" → " + urls.size() + " page(s)");
        } catch (Exception e) {
            callbacks.onError("Confluence", "Keyword search failed: " + e.getMessage());
        }
        return urls;
    }
}
