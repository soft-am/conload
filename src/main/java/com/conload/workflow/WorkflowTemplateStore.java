package com.conload.workflow;

import com.conload.util.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists global per-workflow prompt-template overrides as plain Markdown
 * files under {@code ~/.conload/workflow-templates/<id>.md} (see
 * {@link AppPaths#workflowTemplatesDir()}) — paralleling the other
 * runtime-state files under {@code ~/.conload/}.
 * <p>
 * The bundled template (from {@link PromptTemplateLoader}) is the source of
 * truth for the "always-used" {@code ${variable}} tokens. An override only
 * customises the prose around those locked tokens; it is produced by
 * {@link com.conload.ui.prompttemplate.dialog.WorkflowTemplateEditorDialog},
 * which interleaves the fixed chips with edited prose, so an override always
 * carries the same variable tokens as the bundled template.
 * <p>
 * {@link Workflow#buildPrompt} loads the effective template (override if
 * present, else bundled) before substituting variables.
 */
public final class WorkflowTemplateStore {

    private static final Path DIR = AppPaths.workflowTemplatesDir();

    private WorkflowTemplateStore() {}

    private static Path overridePath(String workflowId) {
        return DIR.resolve(workflowId + ".md");
    }

    /** True when a user override exists for the given workflow. */
    public static boolean hasOverride(String workflowId) {
        Path p = overridePath(workflowId);
        return Files.exists(p) && Files.isRegularFile(p);
    }

    /**
     * Load the effective template text: the user override if present, otherwise
     * the bundled template loaded from {@code bundledResource}.
     */
    public static String loadEffective(String workflowId, String bundledResource) {
        Path p = overridePath(workflowId);
        if (Files.exists(p) && Files.isRegularFile(p)) {
            try {
                return Files.readString(p, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // fall through to the bundled resource
            }
        }
        return PromptTemplateLoader.load(bundledResource);
    }

    /** Persist {@code text} as the override for the given workflow. */
    public static void save(String workflowId, String text) throws IOException {
        Files.createDirectories(DIR);
        Files.writeString(overridePath(workflowId), text, StandardCharsets.UTF_8);
    }

    /** Delete the override for the given workflow, restoring the bundled template. */
    public static void reset(String workflowId) throws IOException {
        Files.deleteIfExists(overridePath(workflowId));
    }
}
