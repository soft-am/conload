package com.conload.workflow;

import com.conload.github.GitHubClient;
import com.conload.service.search.ContentSearchService;
import com.conload.util.FileUtil;
import com.conload.workflow.crosscontext.CrossContextBuilder;
import com.conload.workflow.crosscontext.CrossContextResult;
import com.conload.workflow.crosscontext.CrossContextSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The "Code Review" workflow — starts from one or more GitHub PR URLs
 * (cross-context source type 3 / 4). Each PR's diff, title, body, and commit
 * messages are scanned for Jira keys and Confluence page URLs, which then seed
 * the same recursive cross-context core as the other workflows.
 * <p>
 * When multiple PR URLs are provided (possibly from different repos), all PRs
 * are fetched and their discovered Jira keys + Confluence page IDs are accumulated
 * before the recursive expansion runs once over all combined seeds.
 * <p>
 * The gathered context (PR artifacts, related Jira issues, Confluence pages,
 * GitHub commits) is substituted into a prompt template that instructs the
 * CLI agent to perform a structured code review.
 */
public final class CodeReviewWorkflow implements Workflow {

    private static final String ID = "code-review";
    private static final String TEMPLATE = "/defaults/workflows/code-review.md.tpl";

    @Override
    public String id() { return ID; }

    @Override
    public String displayName() { return "Code Review"; }

    @Override
    public String description() {
        return "Fetches one or more GitHub pull requests (diff, commits, descriptions), "
               + "discovers related Jira issues and Confluence pages, and generates a "
               + "prompt for the CLI agent to perform a structured code review.";
    }

    @Override
    public List<WorkflowFieldDefinition> inputFields() {
        return List.of(
                new WorkflowFieldDefinition(
                        "prUrls",
                        "GitHub PR URL(s)",
                        "https://github.com/owner/repo/pull/123  (one or more, space/comma/newline separated)",
                        true, true),
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

        String rawInput = inputs.get("prUrls").strip();
        if (rawInput.isBlank()) {
            throw new IllegalArgumentException("At least one GitHub PR URL is required.");
        }

        String webHost = GitHubClient.webHostFromApiUrl(env.githubApiUrl());
        List<String> tokens = PrepareToRefinementWorkflow.parseKeys(rawInput);
        List<CrossContextSource.GitHubPr> prs = new ArrayList<>();

        for (String token : tokens) {
            String[] prInfo = ContentSearchService.parseGitHubPrUrl(token, webHost);
            if (prInfo == null) {
                throw new IllegalArgumentException(
                        "Invalid GitHub PR URL: " + token
                        + "\nExpected: https://" + (webHost.isBlank() ? "github.com" : webHost)
                        + "/owner/repo/pull/123");
            }
            String ownerRepo = prInfo[0] + "/" + prInfo[1];
            int prNumber = Integer.parseInt(prInfo[2]);
            prs.add(new CrossContextSource.GitHubPr(ownerRepo, prNumber));
            callbacks.onLog("[CODE-REVIEW] Parsed PR: " + ownerRepo + " #" + prNumber);
        }

        if (prs.isEmpty()) {
            throw new IllegalArgumentException("No valid GitHub PR URLs found in input.");
        }

        String ownerRepo = prs.getFirst().ownerRepo();
        String folderName;
        CrossContextSource source;

        if (prs.size() == 1) {
            CrossContextSource.GitHubPr pr = prs.getFirst();
            ownerRepo = pr.ownerRepo();
            folderName = "pr_" + ownerRepo.replace("/", "_") + "_" + pr.prNumber();
            source = pr;
        } else {
            folderName = "pr_multi_" + System.currentTimeMillis();
            source = new CrossContextSource.GitHubPrs(prs);
        }

        Path contextRoot = env.contextsDir().resolve(FileUtil.normalizeContextFolderName(folderName));
        Files.createDirectories(contextRoot);
        callbacks.onLog("Context root: " + contextRoot);

        String confluenceDataSource = env.fullConfluenceFolder() != null
                ? env.fullConfluenceFolder() : "";

        boolean fullMode = !"false".equalsIgnoreCase(inputs.get("fullMode"));
        CrossContextResult ccResult = CrossContextBuilder.createCrossContext(
                source, env, ownerRepo, contextRoot, callbacks, fullMode);

        callbacks.onLog("Cross-context: " + ccResult.jiraCount() + " Jira, "
                + ccResult.confluenceCount() + " Confluence, " + ccResult.commitCount() + " commits.");

        // ── Output doc path ────────────────────────────────────────────────
        // The generated document lives in the shared project contexts dir
        // under doc/cross_context/ (shared across all worktrees of a project,
        // not pinned to a git checkout).
        Path outputDocPath = Workflow.resolveOutputDoc(env, "code-review.md");

        String prRef = prs.size() == 1
                ? "PR #" + prs.getFirst().prNumber()
                : prs.size() + " PRs";
        return new WorkflowContext(
                contextRoot,
                contextRoot,
                contextRoot,
                contextRoot,
                prRef,
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
}
