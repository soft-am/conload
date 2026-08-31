package com.conload.ui.workflow;

import com.conload.workflow.Workflow;
import com.conload.workflow.WorkflowCallbacks;
import com.conload.workflow.WorkflowContext;
import com.conload.workflow.WorkflowEnvironment;
import com.conload.workflow.WorkflowInputs;
import javafx.application.Platform;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Runs one cross-context gathering pass for a single seed value, reusing the
 * registered {@link Workflow} that owns the {@code CrossContextBuilder}
 * invocation. Runs {@link Workflow#accumulate} on a daemon virtual thread and
 * delivers the {@link WorkflowContext} (or failure) back to the JavaFX thread.
 *
 * <p>Constructs the {@link WorkflowEnvironment} from the narrow
 * {@link WorkflowHost} — the same host the inline-workflow section uses — so no
 * controller or glue code is duplicated. Cancellation is surfaced through
 * {@link WorkflowCallbacks#isCancelled()}.</p>
 */
public final class CrossContextRunner {

    private final WorkflowHost host;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Thread worker;

    public CrossContextRunner(WorkflowHost host) {
        this.host = host;
    }

    /**
     * Start gathering. {@code onLog} / {@code onStatus} are called on the FX
     * thread; {@code onComplete} / {@code onFailed} likewise.
     *
     * @param source      which cross-context workflow to run
     * @param seed        the single user-entered seed value (key/URL/keyword)
     * @param fullMode    whether deep recursion + word discovery is enabled
     */
    public void run(CrossSource source, String seed, boolean fullMode,
                    Consumer<String> onLog, Consumer<String> onStatus,
                    Consumer<WorkflowContext> onComplete,
                    Consumer<Throwable> onFailed) {
        if (isRunning()) return;
        cancelled.set(false);

        Workflow workflow = source.workflow();
        WorkflowEnvironment env = new WorkflowEnvironment(
                host.config(), host.githubToken(), host.githubApiUrl(),
                host.workspacePath(), host.contextsDir(), host.activeProjectId(),
                host.fullConfluenceFolder());
        WorkflowInputs inputs = new WorkflowInputs(Map.of(
                source.inputKey(), seed == null ? "" : seed,
                "fullMode", Boolean.toString(fullMode)));

        WorkflowCallbacks callbacks = new WorkflowCallbacks() {
            @Override public void onProgress(String stage, String message) {
                Platform.runLater(() -> onStatus.accept("[" + stage + "] " + message));
            }
            @Override public void onLog(String line) {
                Platform.runLater(() -> onLog.accept(line));
            }
            @Override public boolean isCancelled() {
                return cancelled.get();
            }
        };

        worker = Thread.ofVirtual().name("cross-context", 0).unstarted(() -> {
            try {
                WorkflowContext ctx = workflow.accumulate(env, inputs, callbacks);
                Platform.runLater(() -> onComplete.accept(ctx));
            } catch (Throwable t) {
                Platform.runLater(() -> onFailed.accept(t));
            } finally {
                worker = null;
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    /** Request cancellation. The running workflow polls {@code isCancelled()}. */
    public void stop() {
        cancelled.set(true);
    }

    public boolean isRunning() {
        return worker != null && worker.isAlive();
    }
}
