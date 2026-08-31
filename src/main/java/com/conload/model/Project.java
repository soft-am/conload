package com.conload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A workspace project — analogous to a "chat" in ChatGPT.
 * Each project references zero or more context folders (plain filesystem paths
 * living under the project's {@code contexts/} directory) and owns an
 * independent terminal session.
 * Persisted to {@code ~/.conload/projects.json}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Project {

    private String       id;
    private String       name;
    private String       parentPath;
    /** Optional override for the per-project contexts root.
     *  When null/blank the default {@code ~/conload-contexts/<projectName>} is used. */
    private String       contextsDir;
    private List<String> contextFolders = new ArrayList<>();
    private List<String> sessionFolders = new ArrayList<>();
    private String       createdAt;
    private String       color;   // CSS hex, e.g. "#4ec9b0"
    /** Last-known CLI agent session for this project — durable across tab
     *  close/restart. {@code sessionType} is the discriminator ("opencode",
     *  "copilot", …); {@code sessionId} is the opaque id returned by that CLI.
     *  Used on restore to offer a one-click resume. See
     *  {@link com.conload.service.OpenTabsService.OpenTab} for the live (open-tab)
     *  counterpart; this pair is the durable fallback that survives tab close. */
    private String       lastSessionType = "";
    private String       lastSessionId = "";
    /** Per-worktree durable session fallback — keyed by the worktree's absolute
     *  path (the base workspace is NOT included here; it uses
     *  {@code lastSessionType/lastSessionId}). Values are encoded as
     *  {@code "<sessionType>:<sessionId>"} (type never contains a colon, so the
     *  id is everything after the <em>first</em> colon). Used to offer a
     *  one-click resume for a <em>closed</em> worktree's terminal session. */
    private Map<String,String> worktreeSessions = new LinkedHashMap<>();
    /** The worktree path the user had selected when this project's tab was last
     *  open (blank = base workspace). Used on restore to re-select the same
     *  workspace. */
    private String       lastWorktreePath = "";

    public Project() {}

    public Project(String id, String name, String color) {
        this.id        = id;
        this.name      = name;
        this.color     = color;
        this.createdAt = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

    public String       getId()            { return id; }
    public String       getName()          { return name; }
    public String       getParentPath()    { return parentPath; }
    public String       getContextsDir()   { return contextsDir; }
    public List<String> getContextFolders(){ return contextFolders; }
    public List<String> getSessionFolders(){ return sessionFolders; }
    public String       getCreatedAt()     { return createdAt; }
    public String       getColor()         { return color; }
    public String getLastSessionType(){ return lastSessionType != null ? lastSessionType : ""; }
    public String getLastSessionId()  { return lastSessionId   != null ? lastSessionId   : ""; }
    /** Per-worktree session map (worktreePath → {@code "type:id"}). Mutable so
     *  {@code ProjectService} can update it in place. */
    public Map<String,String> getWorktreeSessions() {
        return worktreeSessions != null ? worktreeSessions : (worktreeSessions = new LinkedHashMap<>());
    }
    public String getLastWorktreePath() { return lastWorktreePath != null ? lastWorktreePath : ""; }

    public void setId(String id)                          { this.id         = id; }
    public void setName(String name)                      { this.name       = name; }
    public void setParentPath(String parentPath)          { this.parentPath = parentPath; }
    public void setContextsDir(String contextsDir)        { this.contextsDir = contextsDir; }
    public void setContextFolders(List<String> folders)  { this.contextFolders = folders; }
    public void setSessionFolders(List<String> folders)  { this.sessionFolders = folders; }
    public void setCreatedAt(String createdAt)            { this.createdAt  = createdAt; }
    public void setColor(String color)                    { this.color      = color; }
    public void setLastSessionType(String t)              { this.lastSessionType = t != null ? t : ""; }
    public void setLastSessionId(String id)               { this.lastSessionId   = id != null ? id : ""; }
    public void setWorktreeSessions(Map<String,String> m){ this.worktreeSessions = m != null ? m : new LinkedHashMap<>(); }
    public void setLastWorktreePath(String path)         { this.lastWorktreePath = path != null ? path : ""; }

    @Override public String toString() { return name; }
}
