package com.conload.workflow;

/**
 * Callbacks the workflow engine uses to report progress and check cancellation
 * while accumulating context on a background thread.
 */
public interface WorkflowCallbacks {

    /** Report a named stage (e.g. "Jira") and a human-readable message. */
    void onProgress(String stage, String message);

    /** Append a log line to the dialog's log area. */
    void onLog(String line);

    /** Return {@code true} when the user has cancelled the operation.
     *  <p>Cancellation covers both the soft "Enough" path (in-flight API call
     *  completes, remaining phases skipped, partial results finalized) and
     *  the hard "Stop" path (in-flight HTTP interrupted, results discarded).
     *  Use {@link #isHardStopped()} to distinguish the two in catch blocks.
     *  @return {@code true} when the user requested cancellation (Enough or Stop) */
    boolean isCancelled();

    /** Return {@code true} when the user has requested an immediate hard stop
     *  (Stop button), as opposed to a soft "Enough" finalize.
     *  <p>When {@code true}, the in-flight API call is being interrupted and
     *  any broad {@code catch (Exception)} blocks along the pipeline should
     *  re-throw a {@link WorkflowStoppedException} so the entire stack unwinds
     *  instead of logging the interrupt as a recoverable error and continuing.
     *  <p>Default returns {@code false} for backwards compatibility.
     *  @return {@code true} only when a hard Stop was requested */
    default boolean isHardStopped() { return false; }

    /**
     * Report a recoverable per-item failure (API error, download failure, etc.).
     * Unlike {@link #onLog}, this signals that something went wrong rather than
     * being purely informational. The default implementation routes the message
     * to {@link #onLog} with an {@code [ERROR]} tag so existing callback
     * implementations keep working unchanged.
     *
     * @param stage   short source label (e.g. "Jira", "Confluence", "GitHub")
     * @param message human-readable description of the failure
     */
    default void onError(String stage, String message) {
        onLog("[ERROR] [" + stage + "] " + message);
    }

    /** Detect an interrupt anywhere in a throwable's cause chain.
     *  <p>{@link java.net.http.HttpClient#send} clears the thread's interrupt
     *  flag when it throws {@link InterruptedException} (or
     *  {@link java.io.InterruptedIOException}), so a plain
     *  {@code Thread.currentThread().isInterrupted()} check is unreliable
     *  inside the catch block. Use this to confirm the failure was an
     *  interrupt, then re-throw {@link WorkflowStoppedException} to unwind.
     *  @param t throwable caught in a broad {@code catch (Exception)} block
     *  @return {@code true} when an {@link InterruptedException} or
     *          {@link java.io.InterruptedIOException} is found in the chain */
    static boolean isInterruptCause(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof InterruptedException) return true;
            if (c instanceof java.io.InterruptedIOException) return true;
        }
        return false;
    }
}
