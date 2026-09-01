package com.conload.model;

import com.conload.util.Json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User-configurable definition of a CLI agent (opencode, GitHub Copilot,
 * aider, claude-code, …) so that the terminal can detect which tool the user
 * launched, label it in the Sessions dialog, and — for CLIs that expose a
 * JSON session list — list/resume/export sessions.
 * <p>
 * Detection is still the existing two-step mechanism (typed command token
 * match + visible-buffer substring match); these definitions simply replace
 * the previously hardcoded {@code "opencode"} / {@code "copilot"} literals
 * with a user-editable list.
 * <p>
 * All command fields ({@code listCommand}, {@code resumeCommand},
 * {@code exportCommand}) may contain the literal token {@code {id}}, which is
 * substituted with the session id at run time. A blank command means that
 * capability is unavailable for this CLI (e.g. the Sessions button stays
 * hidden, or the Export button is disabled).
 * <p>
 * Stored as indexed keys in {@code ~/.conload/config.txt} (see {@link
 * com.conload.service.ConfigService}); not a separate file.
 */
public record CliTypeDefinition(
        String label,
        String detectText,
        String listCommand,
        String resumeCommand,
        String exportCommand,
        boolean canCompact
) {
    public CliTypeDefinition {
        label         = label         != null ? label         : "";
        detectText    = detectText    != null ? detectText    : "";
        listCommand   = listCommand   != null ? listCommand   : "";
        resumeCommand = resumeCommand != null ? resumeCommand : "";
        exportCommand = exportCommand != null ? exportCommand : "";
    }

    /** No-arg ctor for reflective Jackson deserialization. */
    public CliTypeDefinition() {
        this("", "", "", "", "", false);
    }

    /** Back-compat 5-arg ctor (canCompact defaults to {@code false}). */
    public CliTypeDefinition(String label, String detectText, String listCommand,
                              String resumeCommand, String exportCommand) {
        this(label, detectText, listCommand, resumeCommand, exportCommand, false);
    }

    public String getLabel()         { return label; }
    public String getDetectText()    { return detectText; }
    public String getListCommand()   { return listCommand; }
    public String getResumeCommand() { return resumeCommand; }
    public String getExportCommand() { return exportCommand; }
    /** Whether this CLI supports LLM-powered session compaction (e.g. via {@code opencode serve}). */
    public boolean canCompact()      { return canCompact; }

    /** Whether this CLI exposes a JSON session listing (Sessions button shown). */
    public boolean hasSessions() { return !listCommand.isBlank(); }

    /** Whether resume is supported for this CLI. */
    public boolean canResume() { return !resumeCommand.isBlank(); }

    /** Whether export is supported for this CLI. */
    public boolean canExport() { return !exportCommand.isBlank(); }

    /** Substitutes {@code {id}} in a command template, returning the ready-to-run
     *  command string. Returns {@code ""} if {@code cmd} is blank. */
    public static String substituteId(String cmd, String id) {
        if (cmd == null || cmd.isBlank()) return "";
        String safeId = id == null ? "" : id;
        return cmd.replace("{id}", safeId);
    }

    /** Built-in defaults loaded from the committed classpath resource
     *  {@code /defaults/cli-types.default.json}: OpenCode fully wired
     *  (list / resume / export), GitHub Copilot detected and labelled but
     *  with blank commands, Codex CLI detected. */
    public static List<CliTypeDefinition> defaults() {
        try (var is = CliTypeDefinition.class.getResourceAsStream("/defaults/cli-types.default.json")) {
            if (is == null) return new ArrayList<>();
            return Json.MAPPER.readValue(is,
                new com.fasterxml.jackson.core.type.TypeReference<List<CliTypeDefinition>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    @Override
    public String toString() {
        return (label.isBlank() ? detectText : label) + " [" + detectText + "]";
    }
}
