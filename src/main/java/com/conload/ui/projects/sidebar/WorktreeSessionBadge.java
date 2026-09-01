package com.conload.ui.projects.sidebar;

/** Holder for the {@link SessionInfo} record used by the worktree kebab
 *  "Sessions" submenu to list active CLI sessions across all sub-terminals
 *  of a worktree. */
public final class WorktreeSessionBadge {
    private WorktreeSessionBadge() {}

    public record SessionInfo(int subIndex, String sessionId, String sessionType, String title) {}
}
