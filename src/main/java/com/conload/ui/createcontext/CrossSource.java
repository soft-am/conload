package com.conload.ui.createcontext;

import com.conload.workflow.Workflow;
import com.conload.workflow.WorkflowRegistry;

/**
 * The three cross-context seed sources selectable from the download-context
 * popup. Each maps to one of the registered cross-context workflows, which the
 * workflow runs with a single seed value.
 */
public enum CrossSource {
    JIRA("prepare-to-refinement", "jiraKeys",
            "Jira issue key, URL, or keyword (e.g. PROJ-123)"),
    CONFLUENCE("confluence-reverse-engineering", "confluenceInput",
            "Confluence page URL or search keyword"),
    GITHUB("code-review", "prUrls",
            "GitHub PR URL (one or more, separated by space / comma / newline)");

    private final String workflowId;
    private final String inputKey;
    private final String prompt;

    CrossSource(String workflowId, String inputKey, String prompt) {
        this.workflowId = workflowId;
        this.inputKey = inputKey;
        this.prompt = prompt;
    }

    public Workflow workflow() {
        Workflow w = WorkflowRegistry.findById(workflowId);
        if (w == null) {
            throw new IllegalStateException("Cross-context workflow not registered: " + workflowId);
        }
        return w;
    }

    public String inputKey() { return inputKey; }
    public String prompt() { return prompt; }
}
