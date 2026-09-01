package com.conload.ui.terminal.session;

/**
 * Immutable snapshot of one terminal tab's CLI session state — the typed
 * "map entry" for the ordered list {@code TerminalGroup.terminals()}.
 * <p>
 * {@code pending} is {@code true} when a session id is known (reopened from
 * disk) but not yet live in the PTY; the bar then shows a Restore button.
 * {@link #isEmpty()} is true when no session has been detected or staged.
 */
public record TerminalSessionInfo(String type, String title, String id, boolean pending) {

    public TerminalSessionInfo {
        type  = type  != null ? type  : "";
        title = title != null ? title : "";
        id    = id    != null ? id    : "";
    }

    public boolean isEmpty() {
        return id.isBlank();
    }
}
