package com.conload.ui.components;

import com.conload.ui.Theme;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Renders an SVG from resources via a small WebView.
 * JavaFX ImageView cannot load SVG files, so this component wraps the SVG
 * in a minimal HTML page and displays it in a sized WebView.
 *
 * <p>Pass a CSS variable name like {@code "-app-panel"} as the background
 * to match the current theme — resolved at construction time via
 * {@link Theme#resolve(String)}.
 */
public final class SvgIcon extends StackPane {

    /**
     * @param resourcePath  classpath path to the SVG file (e.g. "/images/jira-logo.svg")
     * @param size          pixel width/height of the icon
     * @param bgCssVar      CSS variable name (e.g. "-app-panel") or a hex color (e.g. "#202024")
     */
    public SvgIcon(String resourcePath, int size, String bgCssVar) {
        this(resourcePath, size, bgCssVar, null);
    }

    /**
     * Variant that re-tints the SVG's {@code #FFFFFF} fills with the supplied
     * project accent color before loading (mirrors the {@code RobotIndicator}
     * string-replace approach). Use for per-project-themed brand / robot icons.
     *
     * @param tintColorHex  CSS hex (e.g. {@code "#A7F3D0"}); {@code null}/blank keeps the SVG white
     */
    public SvgIcon(String resourcePath, int size, String bgCssVar, String tintColorHex) {
        String svg = readResource(resourcePath);
        if (tintColorHex != null && !tintColorHex.isBlank()) {
            svg = svg.replace("#FFFFFF", tintColorHex);
        }
        String bg = bgCssVar.startsWith("-") ? Theme.resolve(bgCssVar) : bgCssVar;
        WebView webView = new WebView();
        webView.setPrefSize(size, size);
        webView.setMinSize(size, size);
        webView.setMaxSize(size, size);
        webView.setContextMenuEnabled(false);
        String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
                + "html,body{margin:0;padding:0;background:" + bg + ";overflow:hidden;width:"
                + size + "px;height:" + size + "px;}"
                + "svg{display:block;width:" + size + "px;height:" + size + "px;}"
                + "</style></head><body>" + svg + "</body></html>";
        webView.getEngine().loadContent(html);
        getChildren().add(webView);
    }

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(SvgIcon.class.getName());

    private static String readResource(String path) {
        try (InputStream is = SvgIcon.class.getResourceAsStream(path)) {
            if (is == null) {
                log.warning("Missing SVG resource: " + path);
                return "<svg xmlns='http://www.w3.org/2000/svg' width='32' height='32'><rect width='32' height='32' fill='none'/></svg>";
            }
            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warning("Error reading " + path + ": " + e.getMessage());
            return "<svg xmlns='http://www.w3.org/2000/svg' width='32' height='32'><rect width='32' height='32' fill='none'/></svg>";
        }
    }
}
