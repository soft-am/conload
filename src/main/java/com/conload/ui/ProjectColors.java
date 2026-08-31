package com.conload.ui;

/**
 * Cyclic palette of vibrant colors assigned to each new project tab so that
 * a project's identity (title text, robot SVG fill, close icon, terminal
 * action buttons) is visually distinct from its neighbours.
 *
 * <p>The palette is defined as CSS variables in {@code theme.css}
 * ({@code -project-color-1} through {@code -project-color-10}) so it can be
 * themed. This class mirrors the same colors for use in Java inline styles
 * (e.g. when setting {@code -accent-color} on a container).</p>
 */
public final class ProjectColors {

    /** Default fallback color when no project accent has been assigned. */
    public static final String DEFAULT = "#FFFFFF";

    /** Mirror of the CSS palette in theme.css (—project-color-1..10).
     *  Delegates to {@link AppColors#PROJECT} — the single source of truth. */
    private static final String[] PALETTE = AppColors.PROJECT.clone();

    private static int cursor = 0;

    /** Next color in the palette (cycles, thread-safe). */
    public static synchronized String next() {
        String c = PALETTE[cursor % PALETTE.length];
        cursor++;
        return c;
    }

    /**
     * Return a defensive copy of the full palette. Single source of truth for
     * components that need direct access to the ordered project colors (e.g. the
     * animated focus-mode logo, which cycles the first corners through these).
     */
    public static String[] palette() {
        return PALETTE.clone();
    }

    private ProjectColors() {}
}
