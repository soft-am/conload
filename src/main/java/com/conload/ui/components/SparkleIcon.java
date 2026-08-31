package com.conload.ui.components;

import javafx.scene.Group;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/**
 * Static sparkle icon rendered with a native {@link SVGPath} (no WebView),
 * mirroring {@link WorkflowsIcon}. Fill colour follows the owning container's
 * themed {@code -fx-text-fill} via the {@code .sparkle-seg} CSS rule in
 * {@code theme.css}.
 */
public final class SparkleIcon extends Group {

    /** Source SVG viewBox is 0 0 24 24. */
    private static final double VIEWBOX = 24.0;

    /** Default render size — matches {@link WorkflowsIcon}. */
    public SparkleIcon() {
        this(24.0);
    }

    /**
     * @param size target edge length in pixels the 24×24 icon is scaled to fit.
     */
    public SparkleIcon(double size) {
        SVGPath p = new SVGPath();
        p.setContent("M8.5 4.5C8.5 7.7 10.8 10 14 10C10.8 10 8.5 12.3 8.5 15.5C8.5 12.3 6.2 10 3 10C6.2 10 8.5 7.7 8.5 4.5Z");
        p.getStyleClass().add("sparkle-seg");
        getChildren().add(p);
        getTransforms().add(new Scale(size / VIEWBOX, size / VIEWBOX));
    }
}
