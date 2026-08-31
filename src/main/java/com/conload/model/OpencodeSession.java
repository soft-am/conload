package com.conload.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One opencode session entry, as returned by {@code opencode session list --format json}.
 * <p>
 * Matches the opencode 1.17.13 schema:
 * <pre>
 * { "id", "title", "created" (epoch ms), "updated" (epoch ms),
 *   "projectId" (opaque hash), "directory" (worktree path) }
 * </pre>
 * Field names are kept verbose for clarity and align 1:1 with the JSON keys so
 * Jackson serializes/deserializes them directly. {@code message} is a legacy
 * alias of {@code title} retained for back-compat with stored caches and older
 * callers; {@code time} is likewise retained but unused.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpencodeSession(
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
    public OpencodeSession {
        id        = id        != null ? id        : "";
        message   = message   != null ? message   : "";
        time      = time      != null ? time      : "";
        title     = title     != null ? title     : "";
        projectId = projectId != null ? projectId : "";
        directory = directory != null ? directory : "";
        cli       = cli       != null ? cli       : "";
    }

    // Canonical 8-arg ctor used by OpencodeSessionService.parseJson (no cli):
    public OpencodeSession(String id, String title, String message, String time,
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

    /** Functional mutator — returns a new record with the CLI hint set. Replaces
     *  the old {@code void setCli(String)} after the records migration. */
    public OpencodeSession withCli(String cliLabel) {
        return new OpencodeSession(id, message, time, title, created, updated, projectId, directory,
                                   cliLabel != null ? cliLabel : "");
    }

    @Override
    public String toString() {
        return id + (title.isBlank() ? (message.isBlank() ? "" : " — " + message) : " — " + title);
    }
}
