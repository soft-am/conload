package com.conload.ui.components;

import javafx.scene.Group;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/**
 * Static workflows icon: two filled sparkle/star shapes.
 * Rendered from the provided SVG with native {@link SVGPath} nodes (no WebView),
 * so it is fully transparent outside the geometry and mouse events bubble up
 * to the parent button.
 *
 * <p>Fill colour follows the owning button's themed {@code -fx-text-fill}
 * via the {@code .workflow-seg} CSS rule in {@code theme.css}, so the icon is
 * muted by default and brightens on hover.</p>
 */
public final class WorkflowsIcon extends Group {

    /** Source SVG viewBox is 0 0 24 24. */
    private static final double VIEWBOX = 24.0;

    /** Default render size — fits on one line with the prompt-header buttons. */
    public WorkflowsIcon() {
        this(24.0);
    }

    /**
     * @param size target edge length in pixels the 24×24 icon is scaled to fit.
     */
    public WorkflowsIcon(double size) {
        // Large sparkle
        addPath("M8.5 4.5C8.5 7.7 10.8 10 14 10C10.8 10 8.5 12.3 8.5 15.5C8.5 12.3 6.2 10 3 10C6.2 10 8.5 7.7 8.5 4.5Z");
        // Small sparkle
        addPath("M17 3C17 5.1 18.5 6.5 20.5 6.5C18.5 6.5 17 8 17 10C17 8 15.5 6.5 13.5 6.5C15.5 6.5 17 5.1 17 3Z");

        double scale = size / VIEWBOX;
        getTransforms().add(new Scale(scale, scale));
    }

    private void addPath(String data) {
        SVGPath p = new SVGPath();
        p.setContent(data);
        p.getStyleClass().add("workflow-seg");
        getChildren().add(p);
    }
}
