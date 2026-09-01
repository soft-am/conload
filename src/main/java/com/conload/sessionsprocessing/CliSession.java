package com.conload.sessionsprocessing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One CLI agent session entry, as returned by a session-list command
 * (e.g. {@code opencode session list --format json}).
 * <p>
 * Matches a generic CLI schema with fields: {@code id}, {@code title},
 * {@code created} (epoch ms), {@code updated} (epoch ms), {@code projectId}
 * (opaque hash), {@code directory} (worktree path). Field names are kept
 * verbose for clarity and align 1:1 with JSON keys so Jackson
 * serializes/deserializes them directly. {@code message} is a legacy alias
 * of {@code title} retained for back-compat with stored caches and older
 * callers; {@code time} is likewise retained but unused.
 *
 * @see com.conload.sessionsprocessing.SessionProcessor
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CliSession(
        String id,
        String message,
        String time,
        String title,
        long   created,     // epoch millis
        long   updated,     // epoch millis
        String projectId,
        String directory,
        @JsonIgnore String cli   // UI hint only; not persisted
) {
    public CliSession {
        id        = id        != null ? id        : "";
        message   = message   != null ? message   : "";
        time      = time      != null ? time      : "";
        title     = title     != null ? title     : "";
        projectId = projectId != null ? projectId : "";
        directory = directory != null ? directory : "";
        cli       = cli       != null ? cli       : "";
    }

    // Canonical 8-arg ctor used by SessionProcessor.parseJson (no cli):
    public CliSession(String id, String title, String message, String time,
                       long created, long updated, String projectId, String directory) {
        this(id, message, time, title, created, updated, projectId, directory, "");
    }

    // Bean-style accessors
    public String getId()        { return id; }
    public String getMessage()   { return message; }
    public String getTime()      { return time; }
    public String getTitle()     { return title; }
    public long   getCreated()   { return created; }
    public long   getUpdated()   { return updated; }
    public String getProjectId() { return projectId; }
    public String getDirectory() { return directory; }
    /** Friendly CLI/provider label for the sessions table. Blank until back-filled. Not persisted. */
    public String getCli()       { return cli; }

    /** Functional mutator — returns a new record with the CLI hint set. */
    public CliSession withCli(String cliLabel) {
        return new CliSession(id, message, time, title, created, updated, projectId, directory,
                               cliLabel != null ? cliLabel : "");
    }

    @Override
    public String toString() {
        return id + (title.isBlank() ? (message.isBlank() ? "" : " — " + message) : " — " + title);
    }
}
