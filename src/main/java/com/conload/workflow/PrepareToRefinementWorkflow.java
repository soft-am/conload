package com.conload.workflow;

import com.conload.jira.JiraClient;
import com.conload.service.search.SourceInputClassifier;
import com.conload.util.FileUtil;
import com.conload.workflow.crosscontext.CrossContextBuilder;
import com.conload.workflow.crosscontext.CrossContextResult;
import com.conload.workflow.crosscontext.CrossContextSource;
import com.conload.workflow.WorkflowStoppedException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The "Prepare to Refinement" workflow — the first concrete workflow.
 * <p>
 * Gathers cross-context for a set of Jira issue keys using the unified
 * {@link CrossContextBuilder}, which recursively expands:
 * <ol>
 *   <li><b>Jira</b> — each issue + related issues (issuelinks, subtasks, inter-linked)
 *       as Markdown, in per-key folders with {@code related_context/} nesting.</li>
 *   <li><b>Confluence</b> — pages linked from each Jira (remote links + ADF URLs),
 *       downloaded recursively with subpages and media.</li>
 *   <li><b>GitHub commits</b> — commits matching each Jira key, saved as aggregated
 *       {@code commits_<KEY>.json} with diff, author, and date.</li>
 *   <li><b>Epic</b> — the parent/epic issue is discovered and expanded the same way.</li>
 *   <li><b>Often-words</b> — top-3 words from the main/epic title that appear
 *       frequently across all context files, used to search Confluence for
 *       additional topically-related pages.</li>
 * </ol>
 * A {@code cross_context_hierarchy.md} tree summary is written at the context root.
 * The global full Confluence folder is injected into the prompt template as
 * {@code ${confluenceDataSource}}.
 */
public final class PrepareToRefinementWorkflow implements Workflow {

    private static final String ID = "prepare-to-refinement";
    private static final String TEMPLATE = "/defaults/workflows/prepare-to-refinement.md.tpl";

    @Override
    public String id()             { return ID; }

    @Override
    public String displayName()    { return "Prepare to Refinement"; }

    @Override
    public String description() {
        return "Gathers cross-context (Jira, Confluence, GitHub commits) for the "
               + "given Jira keys using the unified cross-context builder, then "
               + "generates a prompt for the CLI agent to produce a refinement-"
               + "preparation document.";
    }

    @Override
    public List<WorkflowFieldDefinition> inputFields() {
        return List.of(
                new WorkflowFieldDefinition(
                        "jiraKeys",
                        "Jira Issue Keys / URLs / Keywords",
                        "PROJ-123, https://site.atlassian.net/browse/PROJ-123, or keyword (comma/space separated)",
                        true, true),
                new WorkflowFieldDefinition(
                        "githubOwnerRepo",
                        "GitHub Owner / Repo (auto-detected, editable)",
                        "owner/repo or https://<host>/owner/repo",
                        false, false),
                new WorkflowFieldDefinition(
                        "additionalConfluenceUrl",
                        "Additional Confluence (page URL or keyword — optional)",
                        "https://yoursite.atlassian.net/wiki/.../pages/123/Title  or  a search keyword",
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

        List<String> keys = resolveJiraKeys(inputs.get("jiraKeys"), env, callbacks);
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("No Jira keys provided.");
        }

        String folderName = String.join("_", keys).replaceAll("[^A-Za-z0-9_-]", "_");
        Path contextRoot = env.contextsDir().resolve(FileUtil.normalizeContextFolderName(folderName));
        Files.createDirectories(contextRoot);
        callbacks.onLog("Context root: " + contextRoot);

        String ownerRepo = inputs.get("githubOwnerRepo").strip();
        if (ownerRepo.isEmpty()) {
            ownerRepo = GitRemoteResolver.resolveOwnerRepo(env.workspacePath());
            callbacks.onLog("[GITHUB] Auto-detected owner/repo: " + ownerRepo);
        } else {
            ownerRepo = normalizeOwnerRepo(ownerRepo);
        }

        String confluenceDataSource = env.fullConfluenceFolder() != null
                ? env.fullConfluenceFolder() : "";
        String additionalConfluenceUrl = inputs.get("additionalConfluenceUrl").strip();

        // ── Cross-context builder (unified) ───────────────────────────────
        boolean fullMode = !"false".equalsIgnoreCase(inputs.get("fullMode"));
        CrossContextResult ccResult = CrossContextBuilder.createCrossContext(
                new CrossContextSource.Jira(keys, additionalConfluenceUrl),
                env, ownerRepo, contextRoot, callbacks, fullMode);

        callbacks.onLog("Cross-context: " + ccResult.jiraCount() + " Jira, "
                + ccResult.confluenceCount() + " Confluence, " + ccResult.commitCount() + " commits.");
        callbacks.onLog("Hierarchy: " + ccResult.hierarchyFile());

        // ── Output doc path ────────────────────────────────────────────────
        // The generated document lives in the shared project contexts dir
        // under doc/cross_context/ (shared across all worktrees of a project,
        // not pinned to a git checkout).
        String safeKeys = String.join("-", keys).replaceAll("[^A-Za-z0-9_-]", "");
        Path outputDocPath = Workflow.resolveOutputDoc(env, "prepare-to-refinement-" + safeKeys + ".md");

        return new WorkflowContext(
                contextRoot,
                contextRoot,
                contextRoot,
                contextRoot,
                String.join(", ", keys),
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Resolve the {@code jiraKeys} input via the shared
     * {@link SourceInputClassifier} — the same dispatcher the download-context
     * search uses. Issue URLs and bare keys are taken directly; keywords trigger
     * a JQL text search whose discovered keys are added. Deduplicates preserving
     * insertion order.
     */
    static List<String> resolveJiraKeys(String input, WorkflowEnvironment env, WorkflowCallbacks callbacks) {
        SourceInputClassifier classifier = new SourceInputClassifier();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String token : parseKeys(input)) {
            SourceInputClassifier.JiraInput ji = classifier.classifyJira(token);
            if (ji.isDirectFetch()) {
                if (ji.key() != null && !ji.key().isBlank()) keys.add(ji.key());
            } else if (!ji.value().isBlank()) {
                try {
                    String jql = "text~\"" + ji.value().replace("\"", "\\\"") + "\" ORDER BY updated DESC";
                    var issues = new JiraClient(env.config())
                            .searchIssues(env.config().getBaseUrl(), jql, 50);
                    for (var issue : issues) keys.add(issue.getKey());
                    callbacks.onLog("[JIRA] Keyword \"" + ji.value() + "\" → " + issues.size() + " issue(s)");
                } catch (Exception e) {
                    if (callbacks.isHardStopped() || WorkflowCallbacks.isInterruptCause(e)) {
                        throw new WorkflowStoppedException(e);
                    }
                    callbacks.onError("Jira", "Keyword search failed for \"" + ji.value() + "\": " + e.getMessage());
                }
            }
        }
        return new ArrayList<>(keys);
    }

    /** Parse comma or whitespace separated tokens into a list (non-blank). */
    static List<String> parseKeys(String input) {
        if (input == null || input.isBlank()) return List.of();
        List<String> keys = new ArrayList<>();
        for (String p : input.split("[,\\s]+")) {
            String k = p.strip();
            if (!k.isEmpty()) keys.add(k);
        }
        return keys;
    }

    /** Normalize user input into {@code owner/repo} form (host-agnostic). */
    static String normalizeOwnerRepo(String input) {
        if (input == null || input.isBlank()) return "";
        String s = input.strip()
                .replaceFirst("https?://[^/]+/", "")
                .replaceFirst("^git@[^:]+:", "");
        s = s.replaceAll("\\.git$", "");
        return s;
    }
}
