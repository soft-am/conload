package com.conload.util;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Helpers for running blocking I/O off the JavaFX Application Thread and
 * bouncing results back onto it. Uses Java 21 virtual threads.
 *
 * <p>Replaces ~12 occurrences of:
 * <pre>{@code
 * Thread t = new Thread(() -> {
 *     try {
 *         T result = someBlockingCall();
 *         Platform.runLater(() -> onSuccess(result));
 *     } catch (Exception e) {
 *         Platform.runLater(() -> onError(e));
 *     } finally {
 *         Platform.runLater(onFinally);
 *     }
 * });
 * t.setDaemon(true);
 * t.setName("...");
 * t.start();
 * }</pre>
 * with a one-liner using virtual threads.
 *
 * <p>For callers using {@link javafx.concurrent.Task} (which already provide
 * cancellation + value propagation), prefer that class directly; this helper
 * is for the fire-and-forget daemon-thread pattern.
 */
public final class BackgroundTasks {

    private static final Logger log = Logger.getLogger(BackgroundTasks.class.getName());

    private BackgroundTasks() {}

    /**
     * Run {@code io} on a virtual thread named {@code threadName}; on success
     * invoke {@code onOk} on the FX thread, on failure {@code onErr} on the FX
     * thread; {@code onFinally} (if non-null) runs on the FX thread in either case.
     *
     * @param threadName daemon thread name (for diagnostics)
     * @param io         the blocking operation; must not touch JavaFX UI
     * @param onOk       receives the result on the FX thread (may be {@code null})
     * @param onErr      receives the throwable on the FX thread (may be {@code null})
     * @param onFinally  runs on the FX thread after onOk/onErr (may be {@code null})
     */
    public static <T> void runOnFxThread(String threadName,
                                          Callable<T> io,
                                          Consumer<T> onOk,
                                          Consumer<Throwable> onErr,
                                          Runnable onFinally) {
        Thread thread = Thread.ofVirtual()
            .name(threadName, 0)
            .unstarted(() -> {
                T value = null;
                Throwable error = null;
                try {
                    value = io.call();
                } catch (Throwable t) {
                    error = t;
                }
                final T v = value;
                final Throwable e = error;
                final Runnable fxStep = (e == null)
                    ? () -> { if (onOk != null) onOk.accept(v); }
                    : () -> {
                        log.warning("Background task '" + threadName + "' failed: " + e.getMessage());
                        if (onErr != null) onErr.accept(e);
                    };
                Platform.runLater(() -> {
                    try { fxStep.run(); }
                    finally { if (onFinally != null) onFinally.run(); }
                });
            });
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Convenience: no finally hook.
     * @see #runOnFxThread(String, Callable, Consumer, Consumer, Runnable)
     */
    public static <T> void runOnFxThread(String threadName,
                                          Callable<T> io,
                                          Consumer<T> onOk,
                                          Consumer<Throwable> onErr) {
        runOnFxThread(threadName, io, onOk, onErr, null);
    }

    /**
     * Fire-and-forget I/O on a virtual thread. Useful when only the side-effect
     * matters or when success is observed through some other channel (e.g.
     * a service callback).
     */
    public static void runIOTask(String threadName, Runnable io) {
        Thread thread = Thread.ofVirtual()
            .name(threadName, 0)
            .unstarted(() -> {
                try { io.run(); }
                catch (Throwable t) {
                    log.warning("Background IO task '" + threadName + "' failed: " + t.getMessage());
                }
            });
        thread.setDaemon(true);
        thread.start();
    }
}
