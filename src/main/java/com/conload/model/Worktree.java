package com.conload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.File;

/**
 * One entry from {@code git worktree list --porcelain}.
 * <p>
 * Each worktree is a linked working tree of a single git repository. The
 * first entry returned by git is always the <em>primary</em> (main) checkout —
 * the original repository directory, which cannot be removed via
 * {@code git worktree remove}. Additional entries are worktrees created via
 * {@code git worktree add}.
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code path} — absolute filesystem path of the worktree.</li>
 *   <li>{@code head} — the commit SHA currently checked out.</li>
 *   <li>{@code branch} — short branch name (e.g. {@code feature/foo}); empty
 *       when the worktree is in detached-HEAD state.</li>
 *   <li>{@code detached}, {@code locked}, {@code prunable}, {@code bare} —
 *       porcelain flags.</li>
 *   <li>{@code primary} — true for the main checkout (first block).</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Worktree {

    private String  path;
    private String  head;
    private String  branch;
    private boolean detached;
    private boolean locked;
    private boolean prunable;
    private boolean bare;
    private boolean primary;

    public Worktree() {}

    public Worktree(String path) {
        this.path = path != null ? path : "";
    }

    public String  getPath()       { return path; }
    public String  getHead()       { return head != null ? head : ""; }
    public String  getBranch()     { return branch != null ? branch : ""; }
    public boolean isDetached()    { return detached; }
    public boolean isLocked()      { return locked; }
    public boolean isPrunable()    { return prunable; }
    public boolean isBare()        { return bare; }
    public boolean isPrimary()    { return primary; }

    public void setPath(String path)         { this.path = path != null ? path : ""; }
    public void setHead(String head)         { this.head = head; }
    public void setBranch(String branch)     { this.branch = branch != null ? branch : ""; }
    public void setDetached(boolean v)        { this.detached = v; }
    public void setLocked(boolean v)          { this.locked = v; }
    public void setPrunable(boolean v)        { this.prunable = v; }
    public void setBare(boolean v)            { this.bare = v; }
    public void setPrimary(boolean v)         { this.primary = v; }

    /** The short branch name, or for a detached worktree the first 7 chars of
     *  the HEAD SHA prefixed with {@code "detached"}. Used for display. */
    public String displayName() {
        if (branch != null && !branch.isBlank()) return branch;
        if (detached && head != null && !head.isBlank()) {
            return "detached " + head.substring(0, Math.min(7, head.length()));
        }
        File f = new File(path);
        String n = f.getName();
        return n.isBlank() ? path : n;
    }

    /** Last path segment of the worktree directory (e.g.
     *  {@code ~/GIT/einheiten-service-pgvk} → {@code einheiten-service-pgvk}). */
    public String dirName() {
        if (path == null || path.isBlank()) return "";
        return new File(path).getName();
    }

    @Override
    public String toString() {
        return displayName() + " @ " + path;
    }
}
