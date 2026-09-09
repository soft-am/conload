package com.conload.workflow;

/**
 * Signals that the workflow was hard-stopped by the user (Stop button) and the
 * pipeline should unwind immediately rather than treat the interrupt as a
 * recoverable error. Thrown from HTTP-wrapping catch blocks that detect
 * {@code callbacks.isHardStopped()} or an {@link InterruptedException} in the
 * cause chain, so it propagates through {@code accumulate()} to the UI.
 *
 * <p>This is a {@link RuntimeException} because {@code accumulate()} is
 * declared {@code throws Exception} but the inner collaborators (e.g.
 * {@code ConfluenceTreeWriter.download}) are not declared to throw checked
 * exceptions. Making it unchecked lets it unwind through those layers without
 * forcing every intermediate method to declare it.</p>
 */
public class WorkflowStoppedException extends RuntimeException {

    public WorkflowStoppedException() {
        super("Workflow stopped by user");
    }

    public WorkflowStoppedException(String message) {
        super(message);
    }

    public WorkflowStoppedException(Throwable cause) {
        super("Workflow stopped by user", cause);
    }
}
