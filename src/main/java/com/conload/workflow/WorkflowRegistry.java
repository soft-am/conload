package com.conload.workflow;

import java.util.List;

/**
 * Registry of all available workflows.
 * The first entry is the default selection in the workflow dialog.
 */
public final class WorkflowRegistry {

    private static final List<Workflow> WORKFLOWS = List.of(
            new PrepareToRefinementWorkflow(),
            new CodeReviewWorkflow(),
            new ConfluenceReverseEngineeringWorkflow()
    );

    private WorkflowRegistry() {}

    /** All registered workflows, in selection order. */
    public static List<Workflow> all() {
        return WORKFLOWS;
    }

    /** Find a workflow by id, or null. */
    public static Workflow findById(String id) {
        for (Workflow w : WORKFLOWS) {
            if (w.id().equals(id)) return w;
        }
        return null;
    }
}
