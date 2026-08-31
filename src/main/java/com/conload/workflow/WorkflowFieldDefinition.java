package com.conload.workflow;

/**
 * Declares one input field for a workflow form.
 * The {@link com.conload.ui.workflow.WorkflowFormBuilder} builds a UI control
 * from each definition.
 *
 * @param key       variable name used in the form values map (e.g. {@code "jiraKeys"})
 * @param label     human-readable label shown above the field
 * @param prompt    placeholder text for the input control
 * @param required  true when the field must be non-blank to proceed
 * @param multiline true for a TextArea, false for a single-line TextField
 * @param toggle    true renders a CheckBox (value is "true"/"false"),
 *                  false renders a text input
 */
public record WorkflowFieldDefinition(
        String key,
        String label,
        String prompt,
        boolean required,
        boolean multiline,
        boolean toggle
) {
    /** Convenience constructor without toggle (defaults to false). */
    public WorkflowFieldDefinition(String key, String label, String prompt,
                                    boolean required, boolean multiline) {
        this(key, label, prompt, required, multiline, false);
    }
}
