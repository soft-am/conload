package com.conload.ui;

/**
 * Centralised icon and symbol constants used throughout the UI and log messages.
 *
 * <p>Icons are intentionally monochrome. Visual states such as active,
 * warning, success, or error should be represented through CSS colors.</p>
 */
public final class Icons {

    // ── Navigation ────────────────────────────────────────────────────────
    public static final String BACK     = "←";
    public static final String FORWARD  = "→";
    public static final String UP       = "↑";
    public static final String DOWN     = "↓";
    /** Expand indicator (open down-chevron, matches the tree disclosure shape). */
    public static final String CHEVRON_DOWN = "⌄";

    // ── Actions ───────────────────────────────────────────────────────────
    public static final String CLOSE      = "×";
    public static final String EDIT       = "✎";
    public static final String CHECK      = "✓";
    public static final String APPLY      = "✓";
    public static final String REFRESH    = "↻";
    public static final String SETTINGS   = "⚙";
    public static final String ADD        = "+";
    public static final String ADD_CIRCLE = "⊕";
    public static final String ATTACH     = "⌁";

    // ── File / Content ────────────────────────────────────────────────────
    public static final String FOLDER      = "";
    public static final String DOCUMENT    = "";

    // ── Status / Indicators ───────────────────────────────────────────────
    public static final String DOT        = "●";
    public static final String CLOUD      = "☁";
    public static final String BRANCH_ALT = "⑂";
    public static final String ACTIVE     = "●";
    public static final String LOADING    = "↻";
    public static final String WARNING    = "△";
    public static final String STOP       = "■";
    public static final String INFO       = "ⓘ";
    public static final String TARGET     = "";

    // ── Checkbox ──────────────────────────────────────────────────────────
    public static final String UNCHECKED = "□";
    public static final String CHECKED   = "▣";

    // ── Decorative / Tree ─────────────────────────────────────────────────
    public static final String HEXAGON = "";
    public static final String SESSION = "";
    public static final String BULLET   = "›";
    public static final String BRANCH   = "";

    // ── Log / Report arrows ───────────────────────────────────────────────
    public static final String ARROW_RIGHT = "→";
    public static final String ARROW_DOWN  = "↓";

    // ── Microphone (speech-to-text toggle) ─────────────────────────────────
    public static final String MIC  = "◉";   // idle: fisheye
    public static final String STOP_REC = "■"; // recording: stop sign

    // ── Prompt / terminal-bar accents ────────────────────────────────────────
    /** Sparkle shown before the "Prompt" label in the prompt header row. */
    public static final String SPARKLE  = "✦";
    /** Send/play glyph for the accent ▶ button at the right of the prompt header. */
    public static final String SEND     = "▶";

    // ── Download ────────────────────────────────────────────────────────────
    public static final String DOWNLOAD = "↓";

    /** Vertical three-dot "kebab" menu glyph. */
    public static final String KEBAB = "⋮";

    private Icons() {
        // Utility class
    }
}