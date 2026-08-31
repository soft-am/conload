package com.conload.ui.components;

import javafx.scene.Group;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

/**
 * Static focus-mode toggle icon: corner brackets + a terminal {@code >} symbol.
 * Rendered from the provided SVG with native {@link SVGPath} nodes (no WebView),
 * so it survives window focus / OS full-screen transitions without stalling or
 * blanking the way a SMIL-in-WebView graphic would.
 *
 * <p>Stroke colour follows the owning button's themed {@code -fx-text-fill}
 * via the {@code .seg} CSS rule in {@code theme.css}, so the icon is muted by
 * default and brightens on hover — matching the other text-based icon buttons.</p>
 */
public final class FocusModeIcon extends Group {

    /** Source SVG viewBox is 0 0 35 35. */
    private static final double VIEWBOX = 35.0;

    /** Default render size — large enough to sit on one line with the brand title. */
    public FocusModeIcon() {
        this(28.0);
    }

    /**
     * @param size target edge length in pixels the 35×35 icon is scaled to fit.
     */
    public FocusModeIcon(double size) {
        getStyleClass().add("focus-toggle-graphic");

        // Corner brackets (stroke-width 2.8 in the source SVG)
        addPath("M7 12 V7 H12", 2.8, "corner");
        addPath("M23 7 H28 V12", 2.8, "corner");
        addPath("M7 23 V28 H12", 2.8, "corner");
        addPath("M23 28 H28 V23", 2.8, "corner");

        // Terminal symbol: a ">" chevron + an underscore cursor (stroke-width 2.3)
        addPath("M12.5 14.2 L16.2 17.5 L12.5 20.8", 2.3, "term");
        addPath("M18.4 20.8 H23", 2.3, "term");

        // Scale geometry + stroke widths together (preserves SVG proportions).
        double scale = size / VIEWBOX;
        getTransforms().add(new Scale(scale, scale));
    }

    private void addPath(String data, double strokeWidth, String cls) {
        SVGPath p = new SVGPath();
        p.setContent(data);
        p.setFill(null);
        p.setStrokeWidth(strokeWidth);
        p.setStrokeLineCap(StrokeLineCap.ROUND);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        p.getStyleClass().addAll("seg", cls);
        getChildren().add(p);
    }
}
