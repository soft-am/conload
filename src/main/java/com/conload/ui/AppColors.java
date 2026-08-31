package com.conload.ui;

/**
 * Single source of truth for every hex color value in the application.
 * <p>
 * The values defined here are mirrored in {@code theme.css} (JavaFX CSS cannot
 * read Java constants — both copies must be kept in sync). All Java code that
 * needs a hex value — {@link Theme#resolve}, {@link ProjectColors}, and
 * {@link com.conload.ui.terminal.TerminalHtmlBuilder#darkPalette} — delegates
 * here rather than defining its own literals.
 *
 * <h2>Color groups</h2>
 * <ul>
 *   <li><b>Semantic</b> ({@code *_DARK} / {@code *_LIGHT}): text, muted, success,
 *       warning, error, bg, panel, input, border — one hex pair per CSS variable.</li>
 *   <li><b>Project palette</b> ({@link #PROJECT}): 10 accent colors assigned
 *       cyclically to project tabs. Indices 0–2 mirror success/warning/error so
 *       that {@code -project-color-1/2/3} in CSS can reference the same values.</li>
 * </ul>
 */
public final class AppColors {

    private AppColors() {}

    // ── Semantic colors — DARK ───────────────────────────────────────────
    public static final String BG_DARK      = "#090D16";
    public static final String PANEL_DARK   = "#111827";
    public static final String INPUT_DARK   = "#070A13";
    public static final String LOG_DARK     = "#070A13";
    public static final String BORDER_DARK = "#1E293B";
    public static final String TEXT_DARK    = "#b7c2c9";
    public static final String MUTED_DARK   = "#84939D";
    public static final String SUCCESS_DARK = "#A7F3D0";
    public static final String WARNING_DARK = "#FBBF24";
    public static final String ERROR_DARK   = "#F87171";

    // ── Semantic colors — LIGHT ──────────────────────────────────────────
    public static final String BG_LIGHT      = "#F5F5F7";
    public static final String PANEL_LIGHT   = "#FFFFFF";
    public static final String INPUT_LIGHT   = "#EEEEF2";
    public static final String LOG_LIGHT     = "#F0F0F3";
    public static final String BORDER_LIGHT  = "#D1D5DB";
    public static final String TEXT_LIGHT     = "#18181B";
    public static final String MUTED_LIGHT   = "#6B7280";
    public static final String SUCCESS_LIGHT = "#15803D";
    public static final String WARNING_LIGHT = "#B45309";
    public static final String ERROR_LIGHT   = "#DC2626";

    // ── Project accent palette (mode-independent) ────────────────────────
    // Indices 0-2 mirror success/warning/error so -project-color-1/2/3 in CSS
    // resolve to the same hex. 10 colors for cyclic project-tab assignment.
    public static final String[] PROJECT = {
            SUCCESS_DARK,  // 1  mint      == -app-success
            WARNING_DARK,  // 2  amber     == -app-warning
            ERROR_DARK,    // 3  coral     == -app-error
            "#60A5FA",     // 4  sky       (ansiBlue)
            "#C084FC",     // 5  lilac     (ansiMagenta)
            "#34D399",     // 6  emerald   (ansiCyan)
            "#FCD34D",     // 7  gold
            "#F472B6",     // 8  pink
            "#22D3EE",     // 9  cyan
            "#FB923C",     // 10 orange
    };

    /**
     * Resolve a CSS theme variable name to its hex value for the given mode.
     * Used by {@link Theme#resolve(String)} so Java inline-style code (e.g.
     * {@link com.conload.ui.components.SvgIcon} SVG backgrounds) reads the same
     * values as the CSS variables.
     */
    public static String resolve(String varName, Theme.Mode mode) {
        boolean dark = mode == Theme.Mode.DARK;
        return switch (varName) {
            case "-app-bg"      -> dark ? BG_DARK      : BG_LIGHT;
            case "-app-panel"   -> dark ? PANEL_DARK   : PANEL_LIGHT;
            case "-app-input"   -> dark ? INPUT_DARK   : INPUT_LIGHT;
            case "-app-log"     -> dark ? LOG_DARK     : LOG_LIGHT;
            case "-app-border"  -> dark ? BORDER_DARK  : BORDER_LIGHT;
            case "-app-text"    -> dark ? TEXT_DARK    : TEXT_LIGHT;
            case "-app-muted"   -> dark ? MUTED_DARK   : MUTED_LIGHT;
            case "-app-success" -> dark ? SUCCESS_DARK : SUCCESS_LIGHT;
            case "-app-warning" -> dark ? WARNING_DARK : WARNING_LIGHT;
            case "-app-error"   -> dark ? ERROR_DARK   : ERROR_LIGHT;
            default             -> "#202024";
        };
    }
}
