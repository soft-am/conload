package com.conload.workflow;

import com.conload.model.QuickAction;
import com.conload.util.FileUtil;

import java.nio.file.Path;
import java.util.List;

/**
 * A pluggable context-gathering + prompt-generation workflow.
 * <p>
 * Each implementation declares its input fields, orchestrates context
 * accumulation from external APIs (Jira, GitHub, Confluence), and produces
 * a prompt (from a template resource) that the user can review and send to
 * the terminal CLI agent.
 * <p>
 * The bundled template lives in {@code src/main/resources/defaults/workflows/<id>.md.tpl}.
 * Users may edit the prose around the "always-used" {@code ${variable}} tokens
 * via the Prompts library "Workflow Templates" tab; overrides are persisted
 * globally under {@code ~/.conload/workflow-templates/<id>.md} (see
 * {@link WorkflowTemplateStore}). The variables themselves are locked and
 * always substituted by {@link WorkflowContext#toVariableMap()}.
 */
public sealed interface Workflow
        permits PrepareToRefinementWorkflow, CodeReviewWorkflow,
                ConfluenceReverseEngineeringWorkflow {

    /** Unique identifier (also the template resource name and settings key). */
    String id();

    /** Human-readable name shown in the workflow selector. */
    String displayName();

    /** Short description shown below the selector. */
    String description();

    /** Input fields rendered as a dynamic form. */
    List<WorkflowFieldDefinition> inputFields();

    /**
     * Gather context from external APIs and the filesystem, returning paths
     * and structured summaries.
     *
     * @param env       host-provided runtime environment (config, tokens, dirs)
     * @param inputs    user-supplied form values
     * @param callbacks progress / log / cancellation hooks
     * @return the gathered context (paths + summaries)
     */
    WorkflowContext accumulate(WorkflowEnvironment env, WorkflowInputs inputs,
                                WorkflowCallbacks callbacks) throws Exception;

    /** Classpath resource path of the prompt template ({@code /defaults/workflows/<id>.md.tpl}). */
    String templateResource();

    /**
     * Load the effective template (user override if present, otherwise the
     * bundled resource), substitute variables from the context, and return
     * the final prompt text. Subclasses normally do not override this.
     * <p>
     * Overrides are global, persisted under {@code ~/.conload/workflow-templates/}
     * via {@link WorkflowTemplateStore}; they customise only the prose around
     * the locked {@code ${variable}} tokens, so the substitution contract is
     * unchanged.
     */
    default String buildPrompt(WorkflowContext ctx) {
        String template = WorkflowTemplateStore.loadEffective(id(), templateResource());
        return QuickAction.substitute(template, ctx.toVariableMap());
    }

    // ── Static helpers shared by all workflow implementations ──────────────

    /**
     * Resolve the output-document path for a workflow run, always inside the
     * shared project contexts directory under
     * {@code <contextsDir>/doc/cross_context/}. The folder is created if it
     * does not exist. The result feeds the {@code ${outputDocPath}} template
     * variable so the CLI agent writes the generated Markdown into the shared
     * contexts dir, not into any git checkout (so the doc is shared across all
     * worktrees of a project rather than pinned to a single branch).
     *
     * @param env      the workflow environment (provides {@code contextsDir})
     * @param fileName the desired file name (sanitised internally)
     * @return the absolute path to the output document
     */
    static Path resolveOutputDoc(WorkflowEnvironment env, String fileName) {
        Path dir = env.contextsDir().resolve("doc/cross_context");
        FileUtil.ensureDir(dir);
        return dir.resolve(FileUtil.sanitizeFilename(fileName));
    }
}
