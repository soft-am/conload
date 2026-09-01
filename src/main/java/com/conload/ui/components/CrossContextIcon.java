package com.conload.ui.components;

import javafx.scene.Group;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.FillRule;
import javafx.scene.transform.Scale;

/**
 * Static cross-context icon: a ring (circle outline) with a 4-point sparkle
 * inside. Rendered with native {@link SVGPath} nodes (no WebView), so it is
 * fully transparent outside the geometry and mouse events bubble up to the
 * parent button.
 *
 * <p>Fill colour follows the owning button's themed {@code -fx-text-fill}
 * via the {@code .cross-context-seg} CSS rule in {@code theme.css}, so the
 * icon is muted by default, brightens on hover, and uses the accent colour
 * on accent buttons.</p>
 */
public final class CrossContextIcon extends Group {

    /** Source SVG viewBox is 0 0 24 24. */
    private static final double VIEWBOX = 24.0;

    /** Default render size — fits on one line with prompt-header buttons. */
    public CrossContextIcon() {
        this(24.0);
    }

    /**
     * @param size target edge length in pixels the 24×24 icon is scaled to fit.
     */
    public CrossContextIcon(double size) {
        getChildren().addAll(ring(), sparkle());
        getTransforms().add(new Scale(size / VIEWBOX, size / VIEWBOX));
    }

    private SVGPath ring() {
        SVGPath ring = new SVGPath();
        ring.setContent("M12,2 A10,10 0 1,0 12,22 A10,10 0 1,0 12,2 Z "
                + "M12,4 A8,8 0 1,0 12,20 A8,8 0 1,0 12,4 Z");
        ring.setFillRule(FillRule.EVEN_ODD);
        ring.getStyleClass().add("cross-context-seg");
        return ring;
    }

    private SVGPath sparkle() {
        SVGPath star = new SVGPath();
        star.setContent("M12,6.5C12,9.7 14.3,12 17.5,12 "
                + "C14.3,12 12,14.3 12,17.5 "
                + "C12,14.3 9.7,12 6.5,12 "
                + "C9.7,12 12,9.7 12,6.5Z");
        star.getStyleClass().add("cross-context-seg");
        return star;
    }
}
