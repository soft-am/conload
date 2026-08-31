package com.conload.ui.components;

import javafx.scene.Group;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.FillRule;
import javafx.scene.transform.Scale;

/**
 * Static magnifier / zoom-glass icon rendered with native {@link SVGPath}
 * nodes (no WebView), mirroring {@link WorkflowsIcon}. Fill colour follows
 * the owning button's themed {@code -fx-text-fill} via the {@code .zoom-seg}
 * CSS rule in {@code theme.css}, so the icon is muted by default and
 * brightens on hover.
 *
 * <p>Geometry: large lens ring (outer radius 7, inner 5, centred at 9,9),
 * a short handle stub to the south-east, and a plus cross inside the glass.
 */
public final class ZoomIcon extends Group {

    /** Source SVG viewBox is 0 0 24 24. */
    private static final double VIEWBOX = 24.0;

    /** Default render size — slightly larger than the sibling button text. */
    public ZoomIcon() {
        this(20.0);
    }

    /**
     * @param size target edge length in pixels the 24×24 icon is scaled to fit.
     */
    public ZoomIcon(double size) {
        getChildren().addAll(lensRing(), handle(), plus());
        getTransforms().add(new Scale(size / VIEWBOX, size / VIEWBOX));
    }

    private SVGPath lensRing() {
        SVGPath ring = new SVGPath();
        ring.setContent("M9,2 A7,7 0 1,0 9,16 A7,7 0 1,0 9,2 Z "
                + "M9,4 A5,5 0 1,0 9,14 A5,5 0 1,0 9,4 Z");
        ring.setFillRule(FillRule.EVEN_ODD);
        ring.getStyleClass().add("zoom-seg");
        return ring;
    }

    private SVGPath handle() {
        SVGPath rod = new SVGPath();
        rod.setContent("M13.29,14.71 L14.71,13.29 L17.21,15.79 L15.79,17.21 Z");
        rod.getStyleClass().add("zoom-seg");
        return rod;
    }

    private SVGPath plus() {
        SVGPath cross = new SVGPath();
        cross.setContent("M5.5,8.25 H12.5 V9.75 H5.5 Z "
                + "M8.25,5.5 H9.75 V12.5 H8.25 Z");
        cross.getStyleClass().add("zoom-seg");
        return cross;
    }
}
