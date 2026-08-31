package com.conload.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;

import java.net.URL;
import java.util.Objects;

/**
 * Theme activation and theme mode management.
 * <p>
 * Styling is done via the external CSS resource {@code /styles/theme.css}.
 * Call {@link #apply(Parent, Mode)} on the scene root before showing the stage.
 * Theme switching works by toggling the {@code theme-dark}/{@code theme-light}
 * CSS class on the root node — no Java string rebuilding required.
 *
 * <p>This class also hosts the <em>named style constants</em> (standard
 * {@link Insets} paddings and style-class bundles) that are reused across the
 * UI builders in {@link com.conload.ui.components.UiFactory} and the various
 * controllers, so that repeated layout boilerplate is eliminated.</p>
 */
public final class Theme {

    // ── Standard paddings (replaces repeated `new Insets(...)` literals) ────────
    /** Most common content-row padding: 8 / 14 / 8 / 14. */
    public static final Insets PAD_ROW         = new Insets(8, 14, 8, 14);
    /** Tight inner padding for collapsible content boxes: 4 / 0 / 4 / 0. */
    public static final Insets PAD_ROW_TIGHT   = new Insets(4, 0, 4, 0);
    /** Modal header row padding: 8 / 12 / 8 / 14. */
    public static final Insets PAD_HEADER      = new Insets(8, 12, 8, 14);
    /** Modal footer row padding: 10 / 14 / 10 / 14. */
    public static final Insets PAD_FOOTER      = new Insets(10, 14, 10, 14);
    /** Download-tab root section padding: 14 / 22 / 8 / 22. */
    public static final Insets PAD_SECTION     = new Insets(14, 22, 8, 22);
    /** Tight 2 / 0 / 2 / 0 padding used by {@link com.conload.ui.components.UiFactory#row}. */
    public static final Insets PAD_ROW_FLAT    = new Insets(2, 0, 2, 0);

    // ── Style-class bundles (replaces repeated getStyleClass().addAll(...) chains) ──
    /** Header row border (bottom). */
    public static final String[] CL_HEADER_ROW      = {"panel-border-bottom"};
    /** Footer row border (top). */
    public static final String[] CL_FOOTER_ROW      = {"panel-border-top"};
    /** Themed app-background fill. */
    public static final String[] CL_BG_APP          = {"bg-app"};
    /** Themed transparent scroll pane classes. */
    public static final String[] CL_SCROLL          = {"scroll-pane", "scroll-transparent"};
    /** Bold title label + small font (popup headings). */
    public static final String[] CL_TITLE_SMALL     = {"title", "small"};
    /** Icon-style close/secondary button classes. */
    public static final String[] CL_CLOSE_BTN       = {"icon-button", "secondary", "icon"};
    /** Accent progress indicator class. */
    public static final String[] CL_PROGRESS_ACCENT = {"progress-accent"};

    /** Apply one or more style classes to a node (varargs convenience).
     *  Accepts individual class names, a {@code String[]} bundle
     *  (e.g. {@link #CL_SCROLL}), or a mix. */
    public static void classes(Node node, String... cls) {
        if (node == null || cls == null) return;
        node.getStyleClass().addAll(cls);
    }

    public enum Mode {
        DARK("theme-dark"),
        LIGHT("theme-light");

        private final String styleClass;

        Mode(String styleClass) {
            this.styleClass = styleClass;
        }

        public String styleClass() {
            return styleClass;
        }
    }

    private static final String STYLESHEET = resourceUrl("/styles/theme.css");

    private static Mode currentMode = Mode.DARK;

    /**
     * Apply the theme stylesheet and set the dark/light mode class on the root.
     */
    public static void apply(Parent root, Mode mode) {
        Objects.requireNonNull(root, "root");
        currentMode = Objects.requireNonNull(mode, "mode");

        if (!root.getStylesheets().contains(STYLESHEET)) {
            root.getStylesheets().add(STYLESHEET);
        }

        root.getStyleClass().removeAll(Mode.DARK.styleClass(), Mode.LIGHT.styleClass());
        root.getStyleClass().add(mode.styleClass());
    }

    /** Convenience: apply with default DARK mode. */
    public static void apply(Parent root) {
        apply(root, Mode.DARK);
    }

    public static Mode currentMode() {
        return currentMode;
    }

    /** Resolve a CSS theme variable to its hex value for the current mode.
     *  Delegates to {@link AppColors} — the single source of truth for hex. */
    public static String resolve(String varName) {
        return AppColors.resolve(varName, currentMode);
    }

    /** URL of the theme stylesheet (for dialog panes that need it explicitly). */
    static String stylesheetUrl() {
        return STYLESHEET;
    }

    private static String resourceUrl(String path) {
        URL resource = Theme.class.getResource(path);
        if (resource == null) {
            throw new IllegalStateException("Missing stylesheet: " + path);
        }
        return resource.toExternalForm();
    }

    private Theme() {}
}
