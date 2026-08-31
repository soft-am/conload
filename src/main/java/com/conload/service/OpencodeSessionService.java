package com.conload.service;

import com.conload.model.OpencodeSession;
import com.conload.util.AppPaths;
import com.conload.util.BackgroundTasks;
import com.conload.util.Json;
import com.conload.util.JsonStore;
import com.conload.util.ShellSplitter;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runs {@code opencode session list --format json} on a background thread,
 * parses the result into {@link OpencodeSession} objects (id, title, created,
 * updated, projectId, directory), caches them to
 * {@code ~/.conload/opencode-sessions.json} (see {@link AppPaths#opencodeSessionsJson()}),
 * and supports a 30-second auto-refresh.
 */
public class OpencodeSessionService {

    private static final String CACHE_PATH = AppPaths.opencodeSessionsJson().toString();

    private final JsonStore<List<OpencodeSession>> store =
        JsonStore.list(CACHE_PATH, OpencodeSession.class);

    private javafx.animation.PauseTransition autoRefreshTimer;
    private Consumer<List<OpencodeSession>> autoRefreshCallback;

    /** Working directory for the {@code opencode} process — set from the
     *  terminal pane's project folder so project-specific sessions are found. */
    private File workDir;

    /** Command (shell-split) that lists sessions as JSON. Configurable per CLI
     *  type; defaults to opencode's {@code "opencode session list --format json"}
     *  so the behaviour is unchanged when no custom CLI definition is set. */
    private String listCommand = "opencode session list --format json";

    /** Command template that exports a session as JSON; may contain {@code {id}}
     *  (substituted at run time). Defaults to opencode's
     *  {@code "opencode export {id}"}. */
    private String exportCommand = "opencode export {id}";

    /** Sets the working directory for the opencode process. */
    public void setWorkDir(File dir) { this.workDir = dir; }

    /** Sets the JSON session-list command (e.g. from a configured
     *  {@link com.conload.model.CliTypeDefinition}). Falls back to the opencode
     *  default when blank. */
    public void setListCommand(String cmd) {
        this.listCommand = (cmd == null || cmd.isBlank()) ? "opencode session list --format json" : cmd.strip();
    }

    /** Sets the export command template (may contain {@code {id}}). Falls back
     *  to the opencode default when blank. */
    public void setExportCommand(String cmd) {
        this.exportCommand = (cmd == null || cmd.isBlank()) ? "opencode export {id}" : cmd.strip();
    }

    // ── Fetch sessions (async) ───────────────────────────────────────────────

    /**
     * Runs {@code opencode session list --format json} on a background thread.
     * Parses the output and calls back on the FX thread with the session list.
     * On error, calls back with cached sessions (or empty list) + an error flag
     * embedded in the callback (the list will be empty if nothing is cached).
     */
    public void fetchSessions(Consumer<List<OpencodeSession>> callback, Consumer<String> errorCallback) {
        BackgroundTasks.runIOTask("opencode-sessions-fetch", () -> {
            try {
                String[] cmd = ShellSplitter.split(listCommand);
                if (cmd.length == 0) {
                    Platform.runLater(() -> {
                        errorCallback.accept("No session-list command configured");
                        callback.accept(new ArrayList<>());
                    });
                    return;
                }
                ProcessBuilder pb = new ProcessBuilder(cmd);
                if (workDir != null && workDir.isDirectory()) {
                    pb.directory(workDir);
                }
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                String output = new String(proc.getInputStream().readAllBytes());
                int exitCode = proc.waitFor();

                if (exitCode != 0) {
                    Platform.runLater(() -> {
                        errorCallback.accept("session list exited with code " + exitCode + ": " + output.strip());
                        callback.accept(loadCachedSessions());
                    });
                    return;
                }

                List<OpencodeSession> sessions = parseJson(output);
                saveCachedSessions(sessions);

                Platform.runLater(() -> callback.accept(sessions));
            } catch (Exception e) {
                String msg = e.getMessage();
                Platform.runLater(() -> {
                    errorCallback.accept("Failed to fetch sessions: " + msg);
                    callback.accept(loadCachedSessions());
                });
            }
        });
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    /**
     * Parses the JSON output of {@code opencode session list --format json}.
     * <p>
     * opencode 1.17.13 emits objects with fields: {@code id}, {@code title},
     * {@code created} (epoch ms), {@code updated} (epoch ms), {@code projectId}
     * (opaque hash), {@code directory} (worktree path). {@code message} is
     * back-filled from {@code title} for back-compat with older callers;
     * {@code time} mirrors {@code updated} (as epoch-ms string) for the same
     * reason. Unknown fields are ignored; missing fields default to
     * empty/zero.
     */
    private List<OpencodeSession> parseJson(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            JsonNode root = Json.MAPPER.readTree(json);
            // Could be an array directly, or an object wrapping an array
            JsonNode arrayNode = root.isArray() ? root : root.path("sessions");
            if (!arrayNode.isArray()) return new ArrayList<>();

            List<OpencodeSession> result = new ArrayList<>();
            for (JsonNode node : arrayNode) {
                String id = text(node, "id");
                if (id.isBlank()) id = text(node, "session_id");
                if (id.isBlank()) id = text(node, "name");
                String title = text(node, "title");
                if (title.isBlank()) title = text(node, "message");
                if (title.isBlank()) title = text(node, "summary");
                long created = node.path("created").asLong(0L);
                long updated = node.path("updated").asLong(0L);
                String projectId = text(node, "projectId");
                String directory = text(node, "directory");
                // Legacy back-fill so older callers/consumers keep working.
                String message = title;
                String time    = updated > 0 ? Long.toString(updated) : (created > 0 ? Long.toString(created) : "");
                result.add(new OpencodeSession(id, title, message, time, created, updated, projectId, directory));
            }
            return result;
        } catch (Exception e) {
            System.err.println("[OPENCODE-SESSIONS] JSON parse failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null ? n.asText() : "";
    }

    // ── Cache ─────────────────────────────────────────────────────────────────

    /** Reads cached sessions from {@code src/opencode-sessions.json}. */
    public List<OpencodeSession> loadCachedSessions() {
        return store.load();
    }

    /** Writes sessions to {@code src/opencode-sessions.json}. */
    private void saveCachedSessions(List<OpencodeSession> sessions) {
        try {
            store.save(sessions);
        } catch (IOException e) {
            System.err.println("[OPENCODE-SESSIONS] Failed to save cache: " + e.getMessage());
        }
    }

    // ── Session compaction & export ──────────────────────────────────────────

    /**
     * Best-effort context compaction for a session via the opencode serve HTTP
     * API. Launches a short-lived {@code opencode serve --port <p>} sidecar
     * (it reads the shared SQLite DB, so it can summarize any session), waits
     * for {@code GET /global/health} to return 200, then calls
     * {@code POST /session/:id/summarize} with the given {@code providerID} /
     * {@code modelID}. The sidecar is always destroyed when done.
     * <p>
     * This is LLM-powered and consumes tokens; it can take 10–60s. Must be
     * called on a background thread — this method blocks.
     *
     * @return {@code null} on success (or when successfully skipped), else an
     *         error message describing why compaction failed.
     */
    public String compactSession(String sessionId, String providerID, String modelID) {
        if (sessionId == null || sessionId.isBlank()) return "no session id";
        Process serveProc = null;
        int port = -1;
        try {
            // Find a free port in 4096..4100.
            for (int p = 4096; p <= 4100; p++) {
                if (isPortFree(p)) { port = p; break; }
            }
            if (port < 0) return "no free port for opencode serve";
            ProcessBuilder pb = new ProcessBuilder(
                "opencode", "serve", "--port", Integer.toString(port), "--hostname", "127.0.0.1");
            if (workDir != null && workDir.isDirectory()) pb.directory(workDir);
            pb.redirectErrorStream(true);
            serveProc = pb.start();
            // Drain stdout so the process doesn't block on a full pipe.
            final Process drainProc = serveProc;
            new Thread(() -> { try { drainProc.getInputStream().readAllBytes(); } catch (Exception ignored) {} },
                      "opencode-serve-drain").start();

            // Wait for the server to become healthy (up to ~15s).
            if (!waitForServerHealth(port, 15000)) return "opencode serve did not become healthy";

            // Discover the configured model from the running server's /config
            // (e.g. "neuralwatt/glm-5.2") so summarize uses a provider/model that
            // is actually authenticated — avoids HTTP 500 from a wrong default.
            String[] discovered = discoverConfiguredModel(port);
            String pid = (discovered != null) ? discovered[0]
                : (providerID == null || providerID.isBlank() ? "anthropic" : providerID);
            String mid = (discovered != null) ? discovered[1]
                : (modelID == null || modelID.isBlank() ? "claude-sonnet-4-5" : modelID);
            String body = "{\"providerID\":\"" + pid + "\",\"modelID\":\"" + mid + "\"}";
            int code = httpPostJson("http://127.0.0.1:" + port + "/session/" + sessionId + "/summarize", body);
            if (code != 200) return "summarize returned HTTP " + code;
            return null; // success
        } catch (Exception e) {
            return "compact failed: " + e.getMessage();
        } finally {
            if (serveProc != null && serveProc.isAlive()) {
                serveProc.destroyForcibly();
            }
        }
    }

    /** Returns true if the given TCP port appears free (no server listening). */
    private boolean isPortFree(int port) {
        try (java.net.ServerSocket s = new java.net.ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Polls {@code GET /global/health} until it returns 200 or {@code timeoutMs} elapses. */
    private boolean waitForServerHealth(int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String url = "http://127.0.0.1:" + port + "/global/health";
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(800);
                c.setReadTimeout(800);
                c.setRequestMethod("GET");
                if (c.getResponseCode() == 200) return true;
            } catch (Exception ignored) { /* not ready yet */ }
            try { Thread.sleep(300); } catch (InterruptedException ie) { return false; }
        }
        return false;
    }

    /** Performs a {@code POST} with a JSON body, returning the HTTP status code. */
    private int httpPostJson(String urlStr, String jsonBody) throws IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        try (var os = c.getOutputStream()) {
            os.write(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        // Drain the response so the connection can be reused/closed cleanly.
        try (var is = c.getInputStream()) { is.readAllBytes(); }
        catch (Exception ignored) { /* error body — we still care about the code */ }
        return c.getResponseCode();
    }

    /** Performs a {@code GET} and returns the response body as a string (UTF-8). */
    private String httpGetBody(String urlStr, int timeoutMs) throws IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(Math.min(timeoutMs, 5000));
        c.setReadTimeout(timeoutMs);
        try (var is = c.getInputStream()) {
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Asks the running opencode server for its configured {@code model}
     * (e.g. {@code "neuralwatt/glm-5.2"}) via {@code GET /config}, and splits
     * it on {@code /} into {@code [providerID, modelID]}. Returns {@code null}
     * on any failure — callers fall back to a default model.
     */
    private String[] discoverConfiguredModel(int port) {
        try {
            String body = httpGetBody("http://127.0.0.1:" + port + "/config", 4000);
            int i = body.indexOf("\"model\"");
            if (i < 0) return null;
            int colon = body.indexOf(':', i);
            int q1 = body.indexOf('"', colon + 1);
            int q2 = body.indexOf('"', q1 + 1);
            if (q1 < 0 || q2 < 0) return null;
            String model = body.substring(q1 + 1, q2);
            if (model.isBlank() || !model.contains("/")) return null;
            int slash = model.indexOf('/');
            return new String[]{ model.substring(0, slash), model.substring(slash + 1) };
        } catch (Exception e) {
            System.err.println("[OPENCODE-COMPACT] /config discovery failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Exports a session as a readable Markdown file. Runs
     * {@code opencode export <id>} (JSON to stdout), strips the leading status
     * line, parses JSON <b>fault-tolerantly</b>, and writes a Markdown summary
     * (metadata table + transcript) to {@code outFile}. Must be called on a
     * background thread — this method blocks.
     *
     * @return {@code null} on success, else an error message.
     */
    public String exportSessionMarkdown(String sessionId, java.nio.file.Path outFile) {
        return OpencodeSessionExporter.exportSessionMarkdown(sessionId, outFile, exportCommand, workDir);
    }

    // ── Auto-refresh timer ───────────────────────────────────────────────────

    /**
     * Starts a 30-second recurring timer that re-fetches sessions and calls
     * back on the FX thread. Call {@link #stopAutoRefresh()} to stop.
     */
    public void startAutoRefresh(Consumer<List<OpencodeSession>> callback) {
        stopAutoRefresh();
        this.autoRefreshCallback = callback;
        autoRefreshTimer = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(30));
        autoRefreshTimer.setOnFinished(e -> {
            fetchSessions(
                sessions -> {
                    if (autoRefreshCallback != null) autoRefreshCallback.accept(sessions);
                    if (autoRefreshTimer != null) autoRefreshTimer.playFromStart();
                },
                err -> {
                    if (autoRefreshTimer != null) autoRefreshTimer.playFromStart();
                }
            );
        });
        // Immediate first fetch
        fetchSessions(callback, err -> {});
        autoRefreshTimer.play();
    }

    /** Stops the auto-refresh timer. */
    public void stopAutoRefresh() {
        autoRefreshCallback = null;
        if (autoRefreshTimer != null) {
            autoRefreshTimer.stop();
            autoRefreshTimer = null;
        }
    }

}
