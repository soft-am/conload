package com.conload.sessionsprocessing;

import com.conload.model.CliTypeDefinition;
import com.conload.util.AppPaths;
import com.conload.util.BackgroundTasks;
import com.conload.util.Json;
import com.conload.util.JsonStore;
import com.conload.util.ProcessRunner;
import com.conload.util.ShellSplitter;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import javafx.application.Platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Single consolidated session-processing class for all configured CLI agent
 * types. Binds to one {@link CliTypeDefinition} at construction — the def's
 * {@code listCommand}, {@code resumeCommand}, {@code exportCommand}, and
 * {@code canCompact} flag drive every operation. No hardcoded "opencode"
 * literal: each CLI type's behaviour is read from config.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li><b>Fetch</b> — runs the def's {@code listCommand}, parses JSON into
 *       {@link CliSession} objects, caches them to a per-CLI cache file.</li>
 *   <li><b>Cache</b> — {@code ~/.conload/sessions-<cliType>.json} (one file per
 *       CLI type, so concurrent multi-CLI fetches never clobber each other).</li>
 *   <li><b>Resume</b> — resolves the ready-to-run resume command via
 *       {@link #resumeCommandFor(String)} (substitutes {@code {id}}).</li>
 *   <li><b>Compact</b> — LLM-powered summarization via {@code opencode serve}
 *       (only invoked when {@link CliTypeDefinition#canCompact()} is true).</li>
 *   <li><b>Export</b> — runs the def's {@code exportCommand} and renders the
 *       resulting JSON as a readable Markdown file.</li>
 *   <li><b>Auto-refresh</b> — 30-second recurring timer that re-fetches.</li>
 *   <li><b>Static cache helpers</b> — {@link #loadAllIdToTitles()} /
 *       {@link #loadCachedFor(String)} for the merged worktree-pill lookup.</li>
 * </ul>
 *
 * <h2>Threading</h2>
 * Fetch, compact, and export block on I/O and MUST be called on a background
 * thread ({@link BackgroundTasks#runIOTask}). Auto-refresh runs fetch on a
 * background thread internally; callbacks fire on the JavaFX Application
 * Thread.
 */
public class SessionProcessor {

    private static final Logger log = Logger.getLogger(SessionProcessor.class.getName());

    /** Lenient mapper for parsing export JSON, which can contain unescaped
     *  control characters/newlines inside tool-output strings. */
    private static final ObjectMapper EXPORT_MAPPER = JsonMapper.builder()
        .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .build();

    private final CliTypeDefinition def;
    private final String cliType;
    private final JsonStore<List<CliSession>> store;
    private File workDir;
    private javafx.animation.PauseTransition autoRefreshTimer;
    private Consumer<List<CliSession>> autoRefreshCallback;

    /** Creates a processor bound to one CLI type definition. The def's
     *  command fields are read at construction and never mutated. */
    public SessionProcessor(CliTypeDefinition def, File workDir) {
        this.def = def;
        this.cliType = def.getDetectText().isBlank() ? "default" : def.getDetectText();
        this.store = JsonStore.list(AppPaths.sessionsJson(cliType).toString(), CliSession.class);
        this.workDir = workDir;
    }

    /** The bound CLI type key (e.g. {@code "opencode"}, {@code "copilot"}). */
    public String cliType() { return cliType; }
    /** The bound definition (immutable). */
    public CliTypeDefinition definition() { return def; }
    /** Working directory for the CLI process; updatable when the active
     *  project/worktree changes. */
    public void setWorkDir(File dir) { this.workDir = dir; }

    /** Substitutes {@code {id}} in the def's resume command. Blank when no
     *  resume command is configured for this CLI type. */
    public String resumeCommandFor(String sessionId) {
        return CliTypeDefinition.substituteId(def.getResumeCommand(), sessionId);
    }

    // ── Fetch sessions (async) ───────────────────────────────────────────────

    /** Runs the def's {@code listCommand} on a background thread, parses the
     *  JSON output into {@link CliSession}s, caches them, and calls back on
     *  the FX thread. On error, calls back with cached sessions (or empty). */
    public void fetchSessions(Consumer<List<CliSession>> callback, Consumer<String> errorCallback) {
        BackgroundTasks.runIOTask(cliType + "-sessions-fetch", () -> {
            try {
                String[] cmd = ShellSplitter.split(def.getListCommand());
                if (cmd.length == 0) {
                    Platform.runLater(() -> {
                        errorCallback.accept("No session-list command configured for " + cliType);
                        callback.accept(new ArrayList<>());
                    });
                    return;
                }
                List<String> argv = new ArrayList<>(cmd.length);
                for (String c : cmd) argv.add(c);
                Path workingDirectory = workDir != null && workDir.isDirectory()
                        ? workDir.toPath() : null;
                ProcessRunner.Result result = ProcessRunner.run(argv, workingDirectory, false);
                if (result.exitCode() != 0) {
                    final String err = "session list exited with code " + result.exitCode()
                            + ": " + result.stderr().strip();
                    Platform.runLater(() -> {
                        errorCallback.accept(err);
                        callback.accept(loadCachedSessions());
                    });
                    return;
                }
                List<CliSession> sessions = parseJson(result.stdout());
                saveCachedSessions(sessions);
                Platform.runLater(() -> callback.accept(sessions));
            } catch (Exception e) {
                final String msg = e.getMessage();
                Platform.runLater(() -> {
                    errorCallback.accept("Failed to fetch sessions: " + msg);
                    callback.accept(loadCachedSessions());
                });
            }
        });
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    /** Parses the JSON output of the def's {@code listCommand}. Tolerates both
     *  bare arrays and objects wrapping an {@code "sessions"} array; reads
     *  multiple id/title field aliases for cross-CLI compatibility. */
    private List<CliSession> parseJson(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            JsonNode root = Json.MAPPER.readTree(json);
            JsonNode arrayNode = root.isArray() ? root : root.path("sessions");
            if (!arrayNode.isArray()) return new ArrayList<>();
            List<CliSession> result = new ArrayList<>();
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
                String message = title;
                String time = updated > 0 ? Long.toString(updated)
                        : (created > 0 ? Long.toString(created) : "");
                result.add(new CliSession(id, title, message, time, created, updated, projectId, directory));
            }
            return result;
        } catch (Exception e) {
            log.warning("[" + cliType + "-SESSIONS] JSON parse failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null ? n.asText() : "";
    }

    // ── Cache ─────────────────────────────────────────────────────────────────

    /** Reads cached sessions from {@code ~/.conload/sessions-<cliType>.json}. */
    public List<CliSession> loadCachedSessions() { return store.load(); }

    private void saveCachedSessions(List<CliSession> sessions) {
        try {
            store.save(sessions);
        } catch (IOException e) {
            log.warning("[" + cliType + "-SESSIONS] Failed to save cache: " + e.getMessage());
        }
    }

    // ── Session compaction (LLM-powered; opencode-only via canCompact) ────────

    /** Best-effort context compaction for a session via the opencode serve
     *  HTTP API. Only runs when {@link CliTypeDefinition#canCompact()} is true;
     *  otherwise returns {@code "compaction not supported for <cliType>"}.
     *  Launches a short-lived {@code opencode serve --port <p>} sidecar, waits
     *  for {@code GET /global/health} to return 200, then calls
     *  {@code POST /session/:id/summarize}. Must be called on a background
     *  thread — this method blocks.
     *  @return {@code null} on success (or skipped), else an error message. */
    public String compactSession(String sessionId, String providerID, String modelID) {
        if (sessionId == null || sessionId.isBlank()) return "no session id";
        if (!def.canCompact()) return "compaction not supported for " + cliType;
        Process serveProc = null;
        int port = -1;
        try {
            for (int p = 4096; p <= 4100; p++) {
                if (isPortFree(p)) { port = p; break; }
            }
            if (port < 0) return "no free port for opencode serve";
            ProcessBuilder pb = new ProcessBuilder(
                "opencode", "serve", "--port", Integer.toString(port), "--hostname", "127.0.0.1");
            if (workDir != null && workDir.isDirectory()) pb.directory(workDir);
            pb.redirectErrorStream(true);
            serveProc = pb.start();
            final Process drainProc = serveProc;
            new Thread(() -> { try { drainProc.getInputStream().readAllBytes(); } catch (Exception ignored) {} },
                      cliType + "-serve-drain").start();
            if (!waitForServerHealth(port, 15000)) return "opencode serve did not become healthy";
            String[] discovered = discoverConfiguredModel(port);
            String pid = (discovered != null) ? discovered[0]
                : (providerID == null || providerID.isBlank() ? "anthropic" : providerID);
            String mid = (discovered != null) ? discovered[1]
                : (modelID == null || modelID.isBlank() ? "claude-sonnet-4-5" : modelID);
            String body = "{\"providerID\":\"" + pid + "\",\"modelID\":\"" + mid + "\"}";
            int code = httpPostJson("http://127.0.0.1:" + port + "/session/" + sessionId + "/summarize", body);
            if (code != 200) return "summarize returned HTTP " + code;
            return null;
        } catch (Exception e) {
            return "compact failed: " + e.getMessage();
        } finally {
            if (serveProc != null && serveProc.isAlive()) serveProc.destroyForcibly();
        }
    }

    private boolean isPortFree(int port) {
        try (java.net.ServerSocket s = new java.net.ServerSocket(port)) { return true; }
        catch (IOException e) { return false; }
    }

    private boolean waitForServerHealth(int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String url = "http://127.0.0.1:" + port + "/global/health";
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(800); c.setReadTimeout(800); c.setRequestMethod("GET");
                if (c.getResponseCode() == 200) return true;
            } catch (Exception ignored) { }
            try { Thread.sleep(300); } catch (InterruptedException ie) { return false; }
        }
        return false;
    }

    private int httpPostJson(String urlStr, String jsonBody) throws IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true); c.setConnectTimeout(20000); c.setReadTimeout(120000);
        try (var os = c.getOutputStream()) {
            os.write(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        try (var is = c.getInputStream()) { is.readAllBytes(); }
        catch (Exception ignored) { }
        return c.getResponseCode();
    }

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
            log.warning("[" + cliType + "-COMPACT] /config discovery failed: " + e.getMessage());
            return null;
        }
    }

    private String httpGetBody(String urlStr, int timeoutMs) throws IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(Math.min(timeoutMs, 5000));
        c.setReadTimeout(timeoutMs);
        try (var is = c.getInputStream()) {
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    // ── Session export → Markdown ─────────────────────────────────────────────

    /** Exports a session as a readable Markdown file. Runs the def's
     *  {@code exportCommand} (with {@code {id}} substituted), parses JSON
     *  fault-tolerantly, and writes a Markdown summary (metadata table +
     *  transcript) to {@code outFile}. Must be called on a background thread.
     *  @return {@code null} on success, else an error message. */
    public String exportSessionMarkdown(String sessionId, Path outFile) {
        if (sessionId == null || sessionId.isBlank()) return "no session id";
        try {
            String resolved = def.getExportCommand().replace("{id}", sessionId);
            String[] cmd = ShellSplitter.split(resolved);
            if (cmd.length == 0) return "no export command configured for " + cliType;
            Path workingDirectory = workDir != null && workDir.isDirectory() ? workDir.toPath() : null;
            List<String> argv = new ArrayList<>(cmd.length);
            for (String c : cmd) argv.add(c);
            ProcessRunner.Result result = ProcessRunner.run(argv, workingDirectory, false);
            if (result.exitCode() != 0) {
                return "export exited " + result.exitCode() + ": " + result.stderr().strip();
            }
            String md = convertExportJsonToMarkdown(result.stdout(), sessionId);
            if (md == null) return "failed to parse export output";
            Files.createDirectories(outFile.getParent());
            Files.writeString(outFile, md);
            return null;
        } catch (Exception e) {
            return "export failed: " + e.getMessage();
        }
    }

    private String convertExportJsonToMarkdown(String output, String sessionId) {
        String json = output;
        int brace = json.indexOf('{');
        if (brace > 0) json = json.substring(brace);
        try {
            JsonNode root = EXPORT_MAPPER.readTree(json);
            return renderMarkdown(root, sessionId, output);
        } catch (Exception e) {
            log.warning("[" + cliType + "-EXPORT] lenient JSON parse failed: " + e.getMessage()
                + " — falling back to partial extraction");
            return renderMarkdownFromPartial(json, sessionId, output, e.getMessage());
        }
    }

    private String renderMarkdown(JsonNode root, String sessionId, String output) {
        JsonNode info = root.path("info");
        String title = text(info, "title");
        String slug = text(info, "slug");
        String agent = text(info, "agent");
        String version = text(info, "version");
        String dir = text(info, "directory");
        String projId = text(info, "projectID");
        double cost = info.path("cost").asDouble(0.0);
        JsonNode model = info.path("model");
        String providerID = text(model, "providerID");
        String modelID = text(model, "id");
        JsonNode summary = info.path("summary");
        long additions = summary.path("additions").asLong(0);
        long deletions = summary.path("deletions").asLong(0);
        long files = summary.path("files").asLong(0);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title.isBlank() ? sessionId : title).append("\n\n");
        sb.append("> Exported from ").append(cliType).append(" via export command.\n\n");
        appendInfoTable(sb, sessionId, slug, agent, version, dir, projId,
                        cost, additions, deletions, files, providerID, modelID);
        JsonNode messages = root.path("messages");
        if (messages.isArray() && messages.size() > 0) {
            sb.append("---\n\n## Transcript\n\n");
            for (JsonNode msg : messages) appendMessage(sb, msg);
        }
        return sb.toString();
    }

    private void appendInfoTable(StringBuilder sb, String sessionId, String slug,
            String agent, String version, String dir, String projId, double cost,
            long additions, long deletions, long files, String providerID, String modelID) {
        sb.append("| Field | Value |\n|---|---|\n");
        sb.append("| Session ID | `").append(sessionId).append("` |\n");
        if (!slug.isBlank())     sb.append("| Slug | ").append(slug).append(" |\n");
        if (!agent.isBlank())    sb.append("| Agent | ").append(agent).append(" |\n");
        if (!modelID.isBlank()) sb.append("| Model | ").append(providerID).append("/").append(modelID).append(" |\n");
        if (!version.isBlank())  sb.append("| Version | ").append(version).append(" |\n");
        if (cost > 0)           sb.append("| Cost | $").append(String.format("%.4f", cost)).append(" |\n");
        sb.append("| Summary | +").append(additions).append(" / -").append(deletions)
          .append(" / ").append(files).append(" files |\n");
        if (!dir.isBlank())     sb.append("| Directory | `").append(dir).append("` |\n");
        if (!projId.isBlank())  sb.append("| Project ID | `").append(projId).append("` |\n");
        sb.append("\n");
    }

    private void appendMessage(StringBuilder sb, JsonNode msg) {
        String role = text(msg, "role");
        if (role.isBlank()) role = text(msg, "type");
        String header = switch (role) {
            case "user" -> "## 👤 user";
            case "assistant" -> "## 🤖 assistant";
            default -> "## " + (role.isBlank() ? "message" : role);
        };
        sb.append(header).append("\n\n");
        JsonNode parts = msg.path("parts");
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                String type = text(part, "type");
                if (type.equals("text")) {
                    String t = text(part, "text");
                    if (!t.isBlank()) sb.append(t).append("\n\n");
                }
            }
        } else {
            String t = text(msg, "text");
            if (t.isBlank()) t = text(msg, "content");
            if (!t.isBlank()) sb.append(t).append("\n\n");
        }
    }

    private String renderMarkdownFromPartial(String json, String sessionId, String output, String errMsg) {
        StringBuilder sb = new StringBuilder();
        String title = extractQuoted(json, "title");
        if (title.isBlank()) title = sessionId;
        sb.append("# ").append(title).append("\n\n");
        sb.append("> ⚠ Full JSON parsing failed: ").append(safe(errMsg)).append("\n");
        sb.append("> Showing a best-effort partial extraction.\n\n");
        sb.append("| Field | Value |\n|---|---|\n");
        sb.append("| Session ID | `").append(sessionId).append("` |\n");
        String slug = extractQuoted(json, "slug");
        String agent = extractQuoted(json, "agent");
        String version = extractQuoted(json, "version");
        String dir = extractQuoted(json, "directory");
        String projId = extractQuoted(json, "projectID");
        if (!slug.isBlank())    sb.append("| Slug | ").append(slug).append(" |\n");
        if (!agent.isBlank())   sb.append("| Agent | ").append(agent).append(" |\n");
        if (!version.isBlank()) sb.append("| Version | ").append(version).append(" |\n");
        if (!dir.isBlank())     sb.append("| Directory | `").append(dir).append("` |\n");
        if (!projId.isBlank())  sb.append("| Project ID | `").append(projId).append("` |\n");
        sb.append("\n");
        sb.append("---\n\n## Transcript (partial)\n\n");
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "\"role\"\\s*:\\s*\"(user|assistant)\"|\"type\"\\s*:\\s*\"text\"\\s*,\\s*\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        java.util.regex.Matcher m = p.matcher(json);
        String currentRole = null;
        int count = 0;
        while (m.find()) {
            if (m.group(1) != null) {
                currentRole = m.group(1);
                sb.append("## ").append(currentRole.equals("user") ? "👤 user" : "🤖 assistant").append("\n\n");
            } else if (m.group(2) != null) {
                String t = unescapeJson(m.group(2));
                if (!t.isBlank()) { sb.append(t).append("\n\n"); count++; }
            }
        }
        if (count == 0) {
            sb.append("_(No text parts could be extracted; the raw export is below.)_\n\n```\n")
              .append(output.length() > 20000 ? output.substring(0, 20000) + "\n…[truncated]" : output)
              .append("\n```\n");
        }
        return sb.toString();
    }

    private static String extractQuoted(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "\"" + key + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        return m.find() ? unescapeJson(m.group(1)) : "";
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    default -> out.append(n);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    // ── Auto-refresh timer ───────────────────────────────────────────────────

    /** Starts a 30-second recurring timer that re-fetches sessions and calls
     *  back on the FX thread. Call {@link #stopAutoRefresh()} to stop. */
    public void startAutoRefresh(Consumer<List<CliSession>> callback) {
        stopAutoRefresh();
        this.autoRefreshCallback = callback;
        autoRefreshTimer = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(30));
        autoRefreshTimer.setOnFinished(e -> {
            fetchSessions(
                sessions -> {
                    if (autoRefreshCallback != null) autoRefreshCallback.accept(sessions);
                    if (autoRefreshTimer != null) autoRefreshTimer.playFromStart();
                },
                err -> { if (autoRefreshTimer != null) autoRefreshTimer.playFromStart(); }
            );
        });
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

    // ── Static cache helpers (merged, cross-CLI) ─────────────────────────────

    /** Reads one CLI type's cache ({@code ~/.conload/sessions-<cliType>.json}). */
    public static List<CliSession> loadCachedFor(String cliType) {
        return JsonStore.list(AppPaths.sessionsJson(cliType).toString(), CliSession.class).load();
    }

    /** Merges every {@code sessions-*.json} cache file in {@code ~/.conload/}
     *  into a single {@code id → title} map. Used by the worktree-session-pill
     *  lookup so titles resolve across all CLI types, not just the last
     *  fetched. Handles missing/corrupt files gracefully. */
    public static Map<String, String> loadAllIdToTitles() {
        Map<String, String> idToTitle = new HashMap<>();
        Path dir = AppPaths.dataDir();
        if (!Files.isDirectory(dir)) return idToTitle;
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().startsWith("sessions-"))
                  .filter(p -> p.getFileName().toString().endsWith(".json"))
                  .forEach(p -> mergeCacheFile(p, idToTitle));
        } catch (IOException e) {
            log.warning("Failed to list session cache dir " + dir + ": " + e.getMessage());
        }
        return idToTitle;
    }

    private static void mergeCacheFile(Path file, Map<String, String> idToTitle) {
        try {
            List<CliSession> sessions = JsonStore.list(file.toString(), CliSession.class).load();
            for (CliSession s : sessions) {
                if (s.getId() != null && !s.getId().isBlank() && s.getTitle() != null && !s.getTitle().isBlank()) {
                    idToTitle.putIfAbsent(s.getId(), s.getTitle());
                }
            }
        } catch (Exception ignored) { /* corrupt cache file — skip */ }
    }
}
