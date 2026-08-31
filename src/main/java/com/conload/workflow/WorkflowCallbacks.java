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

    /** Return {@code true} when the user has cancelled the operation. */
    boolean isCancelled();

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
}
