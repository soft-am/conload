package com.conload.service;

import com.conload.util.AppPaths;
import com.conload.util.JsonStore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Persists the set of open project tabs and their last-known terminal PIDs
 * to {@code ~/.conload/open_tabs.json} (see {@link AppPaths#openTabsJson()})
 * so they can be restored on app restart.
 */
public class OpenTabsService {

    private static final Logger log = Logger.getLogger(OpenTabsService.class.getName());

    private final JsonStore<List<OpenTab>> store =
        JsonStore.list(AppPaths.openTabsJson().toString(), OpenTab.class);

    /** One entry per open tab. {@code worktreePath} is blank for the project's
     *  base (primary) workspace; non-blank for a git-worktree workspace whose
     *  terminal lives independently with its own session id. */
    public record OpenTab(
            @JsonProperty("projectId") String projectId,
            @JsonProperty("worktreePath") String worktreePath,
            @JsonProperty("pid") long pid,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("sessionType") String sessionType
    ) {
        // Compact constructor: normalise nulls so old open_tabs.json files
        // (which lack the worktreePath field) load as the base workspace.
        public OpenTab {
            worktreePath = worktreePath != null ? worktreePath : "";
            sessionId    = sessionId    != null ? sessionId    : "";
            sessionType  = sessionType  != null ? sessionType  : "";
        }
    }

    /** Convenience constructor for callers using the base workspace
     *  (worktreePath = ""). */
    public static OpenTab of(String projectId, long pid, String sessionId, String sessionType) {
        return of(projectId, "", pid, sessionId, sessionType);
    }

    /** Convenience constructor specifying a worktree path (blank = base). */
    public static OpenTab of(String projectId, String worktreePath, long pid,
                             String sessionId, String sessionType) {
        return new OpenTab(projectId,
                worktreePath != null ? worktreePath : "",
                pid,
                sessionId != null ? sessionId : "",
                sessionType != null ? sessionType : "");
    }

    /** Save the list of open tabs to disk. */
    public void save(List<OpenTab> tabs) {
        try {
            store.save(tabs);
            log.info("Open tabs saved (" + tabs.size() + " tabs)");
        } catch (IOException e) {
            log.warning("Failed to save open tabs: " + e.getMessage());
        }
    }

    /** Load the list of open tabs from disk. Returns empty list if file missing. */
    public List<OpenTab> load() {
        return store.load();
    }

    /** Clear the persisted open tabs file. */
    public void clear() {
        try {
            store.clear();
        } catch (IOException e) {
            log.warning("Failed to clear open tabs: " + e.getMessage());
        }
    }
}
