package com.conload.service;

import com.conload.util.AppPaths;
import com.conload.util.JsonStore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Live registry of every terminal PTY the app has started, persisted to
 * {@code ~/.conload/terminal_pids.json} (see {@link AppPaths#terminalPidsJson()}).
 * The file is refreshed on every PTY
 * start/stop so it stays accurate even if the JVM is force-killed — on the
 * next launch {@link #killAllAliveAndClear()} reaps any leftover shells
 * (and their child processes) before fresh terminals are opened.
 */
public class PidRegistryService {

    private static final Logger log = Logger.getLogger(PidRegistryService.class.getName());

    private final JsonStore<List<PidEntry>> store =
        JsonStore.list(AppPaths.terminalPidsJson().toString(), PidEntry.class);
    private final Object lock = new Object();

    /** One entry per live PTY. {@code worktreePath} is blank for a project's
     *  base (primary) workspace; non-blank for an independently-rooted worktree. */
    public record PidEntry(
            @JsonProperty("pid") long pid,
            @JsonProperty("projectId") String projectId,
            @JsonProperty("worktreePath") String worktreePath,
            @JsonProperty("startedAt") String startedAt
    ) {
        public PidEntry {
            projectId   = projectId   != null ? projectId : "";
            worktreePath = worktreePath != null ? worktreePath : "";
            startedAt   = startedAt   != null ? startedAt : "";
        }
    }

    /** Register a freshly-started PTY. No-op for non-positive pids. */
    public void add(long pid, String projectId, String worktreePath) {
        if (pid <= 0) return;
        synchronized (lock) {
            List<PidEntry> entries = new ArrayList<>(store.load());
            // Replace any stale entry for the same pid (a fresh start after a
            // destroy normally clears via remove(), but guard anyway).
            entries.removeIf(e -> e.pid() == pid);
            entries.add(new PidEntry(
                    pid,
                    projectId != null ? projectId : "",
                    worktreePath != null ? worktreePath : "",
                    java.time.LocalDateTime.now().toString()));
            saveLocked(entries);
        }
    }

    /** Remove a PTY from the registry when it is destroyed. */
    public void remove(long pid) {
        if (pid <= 0) return;
        synchronized (lock) {
            List<PidEntry> entries = new ArrayList<>(store.load());
            boolean removed = entries.removeIf(e -> e.pid() == pid);
            if (removed) saveLocked(entries);
        }
    }

    /** Load the persisted registry (empty list if the file is missing/corrupt). */
    public List<PidEntry> load() {
        synchronized (lock) {
            return store.load();
        }
    }

    /**
     * Reap any still-alive PTYs (and their descendant processes) recorded in
     * the registry file, then clear the file. Used at app startup to clean up
     * shells orphaned by a previous force-quit / {@code kill -9} that could not
     * run a shutdown hook. Safe to call repeatedly: dead pids are simply dropped.
     */
    public void killAllAliveAndClear() {
        List<PidEntry> entries;
        synchronized (lock) {
            entries = new ArrayList<>(store.load());
        }
        if (entries.isEmpty()) return;

        int killed = 0;
        for (PidEntry e : entries) {
            if (e.pid() <= 0) continue;
            Optional<ProcessHandle> ph = ProcessHandle.of(e.pid());
            if (ph.isEmpty() || !ph.get().isAlive()) continue;

            // Kill descendants first (opencode/copilot CLIs spawned by the shell),
            // then the shell itself. destroyForcibly() == SIGKILL on Unix.
            List<ProcessHandle> descendants = new ArrayList<>();
            ph.get().descendants().forEach(descendants::add);
            for (ProcessHandle d : descendants) {
                try { d.destroyForcibly(); }
                catch (Exception ex) { log.warning("Could not kill descendant " + d.pid() + ": " + ex.getMessage()); }
            }
            try {
                ph.get().destroyForcibly();
                killed++;
                log.info("[PID-REGISTRY] Reaped leftover PTY pid=" + e.pid()
                        + " (" + e.projectId() + ")");
            } catch (Exception ex) {
                log.warning("Could not kill pid " + e.pid() + ": " + ex.getMessage());
            }
        }
        if (killed > 0) {
            log.info("[PID-REGISTRY] Reaped " + killed + " leftover terminal process(es) from previous run.");
        }
        clear();
    }

    /** Delete the registry file. */
    public void clear() {
        synchronized (lock) {
            try {
                store.clear();
            } catch (IOException e) {
                log.warning("Failed to clear pid registry: " + e.getMessage());
            }
        }
    }

    // ── internals (must be called holding `lock`) ─────────────────────────────

    private void saveLocked(List<PidEntry> entries) {
        try {
            store.save(entries);
        } catch (IOException e) {
            log.warning("Failed to save pid registry: " + e.getMessage());
        }
    }
}
