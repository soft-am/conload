package com.conload.service;

import com.conload.util.ProcessRunner;
import com.conload.util.ShellSplitter;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports an opencode session as a readable Markdown file.
 * Extracted from {@link OpencodeSessionService} to reduce its size.
 */
public final class OpencodeSessionExporter {

    /** Lenient mapper for parsing {@code opencode export} JSON, which can
     *  contain unescaped control characters/newlines inside tool-output
     *  strings. These features let Jackson tolerate that common malformation. */
    private static final ObjectMapper EXPORT_MAPPER = JsonMapper.builder()
        .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .build();

    private OpencodeSessionExporter() {}

    /**
     * Exports a session as a readable Markdown file. Runs
     * {@code opencode export <id>} (JSON to stdout), strips the leading status
     * line, parses JSON <b>fault-tolerantly</b>, and writes a Markdown summary
     * (metadata table + transcript) to {@code outFile}. Must be called on a
     * background thread — this method blocks.
     *
     * @param sessionId     the opencode session id
     * @param outFile       destination .md file
     * @param exportCommand command template (may contain {@code {id}})
     * @param workDir       working directory for the opencode process (may be null)
     * @return {@code null} on success, else an error message.
     */
    public static String exportSessionMarkdown(String sessionId, Path outFile,
                                                String exportCommand, File workDir) {
        if (sessionId == null || sessionId.isBlank()) return "no session id";
        try {
            String resolved = exportCommand.replace("{id}", sessionId);
            String[] cmd = ShellSplitter.split(resolved);
            if (cmd.length == 0) return "no export command configured";
            Path workingDirectory = workDir != null && workDir.isDirectory()
                    ? workDir.toPath() : null;
            ProcessRunner.Result result = ProcessRunner.run(
                    List.of(cmd), workingDirectory, false);
            String output = result.stdout();
            if (result.exitCode() != 0) {
                return "export exited " + result.exitCode() + ": " + result.stderr().strip();
            }

            String md = convertExportJsonToMarkdown(output, sessionId);
            if (md == null) return "failed to parse export output";
            Files.createDirectories(outFile.getParent());
            Files.writeString(outFile, md);
            return null;
        } catch (Exception e) {
            return "export failed: " + e.getMessage();
        }
    }

    private static String convertExportJsonToMarkdown(String output, String sessionId) {
        String json = output;
        int brace = json.indexOf('{');
        if (brace > 0) json = json.substring(brace);

        try {
            JsonNode root = EXPORT_MAPPER.readTree(json);
            return renderMarkdown(root, sessionId, output);
        } catch (Exception e) {
            System.err.println("[OPENCODE-EXPORT] lenient JSON parse failed: " + e.getMessage()
                + " — falling back to partial extraction");
            return renderMarkdownFromPartial(json, sessionId, output, e.getMessage());
        }
    }

    private static String renderMarkdown(JsonNode root, String sessionId, String output) {
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
        sb.append("> Exported from opencode via `opencode export`.\n\n");
        appendInfoTable(sb, sessionId, title, slug, agent, version, dir, projId,
                        cost, additions, deletions, files, providerID, modelID);

        JsonNode messages = root.path("messages");
        if (messages.isArray() && messages.size() > 0) {
            sb.append("---\n\n## Transcript\n\n");
            for (JsonNode msg : messages) {
                appendMessage(sb, msg);
            }
        }
        return sb.toString();
    }

    private static void appendInfoTable(StringBuilder sb, String sessionId, String title, String slug,
            String agent, String version, String dir, String projId, double cost,
            long additions, long deletions, long files, String providerID, String modelID) {
        sb.append("| Field | Value |\n|---|---|\n");
        sb.append("| Session ID | `").append(sessionId).append("` |\n");
        if (!slug.isBlank())     sb.append("| Slug | ").append(slug).append(" |\n");
        if (!agent.isBlank())   sb.append("| Agent | ").append(agent).append(" |\n");
        if (!modelID.isBlank()) sb.append("| Model | ").append(providerID).append("/").append(modelID).append(" |\n");
        if (!version.isBlank()) sb.append("| Version | ").append(version).append(" |\n");
        if (cost > 0)          sb.append("| Cost | $").append(String.format("%.4f", cost)).append(" |\n");
        sb.append("| Summary | +").append(additions).append(" / -").append(deletions)
          .append(" / ").append(files).append(" files |\n");
        if (!dir.isBlank())     sb.append("| Directory | `").append(dir).append("` |\n");
        if (!projId.isBlank())  sb.append("| Project ID | `").append(projId).append("` |\n");
        sb.append("\n");
    }

    private static void appendMessage(StringBuilder sb, JsonNode msg) {
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

    private static String renderMarkdownFromPartial(String json, String sessionId, String output, String errMsg) {
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
        if (!agent.isBlank()) sb.append("| Agent | ").append(agent).append(" |\n");
        if (!version.isBlank()) sb.append("| Version | ").append(version).append(" |\n");
        if (!dir.isBlank())    sb.append("| Directory | `").append(dir).append("` |\n");
        if (!projId.isBlank()) sb.append("| Project ID | `").append(projId).append("` |\n");
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

    private static String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null ? n.asText() : "";
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
}
