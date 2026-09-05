package com.conload.ui.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import javafx.application.Platform;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** Owns the lifetime of the shell process and its reader thread. */
final class TerminalPtyController {
    interface Listener {
        void onInput(String data);
        void onOutput(String base64);
        void onStarted(long pid);
        void onStopped(long pid);
        void onEnded();
    }

    private final Listener listener;
    private volatile PtyProcess process;
    private final Object outputLock = new Object();
    private final ByteArrayOutputStream pendingOutput = new ByteArrayOutputStream();
    private final Object inputLock = new Object();
    private boolean outputFlushScheduled;

    TerminalPtyController(Listener listener) {
        this.listener = listener;
    }

    boolean isAlive() {
        PtyProcess p = process;
        return p != null && p.isAlive();
    }

    long pid() {
        PtyProcess p = process;
        return p != null && p.isAlive() ? p.pid() : -1;
    }

    void start(String shell, File directory, Map<String, String> environment) throws IOException {
        PtyProcess p = new PtyProcessBuilder().setCommand(new String[]{shell})
            .setDirectory(directory.getAbsolutePath()).setEnvironment(environment)
            .setInitialColumns(220).setInitialRows(50).start();
        process = p;
        listener.onStarted(p.pid());
        Thread reader = new Thread(() -> readOutput(p), "pty-reader");
        reader.setDaemon(true);
        reader.start();
    }

    void sendInput(String data) {
        sendInput(data, null);
    }

    void sendInput(String data, Runnable onComplete) {
        PtyProcess p = process;
        if (p == null || !p.isAlive()) {
            if (onComplete != null) Platform.runLater(onComplete);
            return;
        }
        listener.onInput(data);
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        Thread.startVirtualThread(() -> {
            writeInput(p, bytes);
            if (onComplete != null) Platform.runLater(onComplete);
        });
    }

    private void writeInput(PtyProcess p, byte[] bytes) {
        synchronized (inputLock) {
            if (!p.isAlive()) return;
            try {
                p.getOutputStream().write(bytes);
                p.getOutputStream().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void resize(int cols, int rows) {
        PtyProcess p = process;
        if (p != null && p.isAlive()) {
            try {
                p.setWinSize(new WinSize(cols, rows));
            } catch (Exception e) {
                System.err.println("[TERMINAL] setWinSize failed: " + e.getMessage());
            }
        }
    }

    void destroy() {
        PtyProcess p = process;
        if (p != null && p.isAlive()) {
            long stoppedPid = p.pid();
            p.destroy();
            process = null;
            listener.onStopped(stoppedPid);
        }
    }

    /** Shutdown-hook variant: kills descendants and does not touch UI state. */
    void destroyNoUi() {
        PtyProcess p = process;
        if (p == null) return;
        long stoppedPid = p.pid();
        try {
            if (p.isAlive()) p.destroyForcibly();
        } catch (Exception e) {
            System.err.println("[TERMINAL] destroyForcibly on " + stoppedPid + " failed: " + e.getMessage());
        }
        try {
            ProcessHandle.of(stoppedPid).ifPresent(h -> h.descendants().forEach(d -> {
                try { d.destroyForcibly(); } catch (Exception ignored) { }
            }));
        } catch (Exception ignored) { }
        process = null;
        listener.onStopped(stoppedPid);
    }

    private void readOutput(PtyProcess p) {
        System.out.println("[TERMINAL] PTY reader thread started");
        byte[] buffer = new byte[4096];
        try (InputStream in = p.getInputStream()) {
            int count;
            while ((count = in.read(buffer)) != -1) {
                queueOutput(buffer, count);
            }
            flushOutput();
            System.out.println("[TERMINAL] PTY reader — stream ended (EOF)");
        } catch (IOException e) {
            System.err.println("[TERMINAL] PTY reader IOException: " + e.getMessage());
        }
        Platform.runLater(listener::onEnded);
    }

    /** Coalesce rapid PTY reads so the WebView is not updated once per read. */
    private void queueOutput(byte[] bytes, int length) {
        synchronized (outputLock) {
            pendingOutput.write(bytes, 0, length);
            if (outputFlushScheduled) return;
            outputFlushScheduled = true;
        }
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            flushOutput();
        });
    }

    private void flushOutput() {
        byte[] bytes;
        synchronized (outputLock) {
            if (pendingOutput.size() == 0) {
                outputFlushScheduled = false;
                return;
            }
            bytes = pendingOutput.toByteArray();
            pendingOutput.reset();
            outputFlushScheduled = false;
        }
        String output = Base64.getEncoder().encodeToString(bytes);
        Platform.runLater(() -> listener.onOutput(output));
    }
}
