package com.conload.ui.terminal;

import com.conload.ui.ProjectColors;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Animated character indicator shown on project tabs while a terminal session is
 * busy (a command is running). Renders the SMIL-animated SVG via a WebView,
 * since JavaFX {@link javafx.scene.shape.SVGPath} only supports static path
 * data and cannot play SMIL animations.
 *
 * <p>Each project tab picks one of four characters (a typing robot, cat, alien,
 * or yoda-style creature) — all sharing the same animated body/laptop/typing
 * animation, differing only in the head/face region. The character is chosen at
 * random in {@link com.conload.ui.projects.ProjectWorkspaceController#doSwitch}
 * and passed in here alongside the project's identity color.</p>
 *
 * <p>The character's primary fill ({@code #FFFFFF} in the source SVG) is replaced
 * at runtime with the owning project's color, so each tab's indicator matches
 * the project's identity color. Dark accents ({@code #111111}) are kept so
 * the face and outline remain readable.</p>
 *
 * <p>One instance should be cached per {@link CopilotTerminalPane}
 * (see {@link CopilotTerminalPane#getRobotIndicator()}) so the WebView's
 * animation state survives {@code refreshProjectTabsBar()} rebuilds.</p>
 */
public final class RobotIndicator extends StackPane {

    /** Edge length (px) of the robot WebView. Exposed so the tab bar can size
     *  the indicator when showing it after a period hidden: the WebView must
     *  have a non-zero layout size to paint, so callers keep it laid out and
     *  collapse reserved space via prefWidth/prefHeight (0 when idle). */
    public static final double SIZE = 34.0;

    /** The four animated characters that can appear on a project tab. All share
     *  the same typing body; only the head/face differs. Use {@link #next()}
     *  to cycle through them in order per project (one after the other). */
    public enum Character {
        /** Original typing robot. */
        ROBOT,
        /** Cat face: triangular ears, slit-pupil eyes, whiskers, split mouth. */
        CAT,
        /** Alien face: large almond eyes, tiny slit mouth, paired antennae nubs. */
        ALIEN,
        /** Yoda-style face: large pointed ears, wide eyes, serene smile. */
        YODA;

        private static final Character[] VALUES = values();
        private static int cursor = 0;

        /** Cycle through characters in order (ROBOT → CAT → ALIEN → YODA → …),
         *  thread-safe. Used by the project controller when a new project tab
         *  is opened so consecutive tabs get consecutive characters. */
        public static synchronized Character next() {
            Character c = VALUES[cursor % VALUES.length];
            cursor++;
            return c;
        }

        /** Pick a uniformly-random character (thread-safe). Used by the focus-mode
         *  overlay so each focus entry can show a different character. */
        public static Character random() {
            return VALUES[ThreadLocalRandom.current().nextInt(VALUES.length)];
        }
    }

    /** File path (relative to the classpath root) for each character's SVG. */
    private static final Map<Character, String> SVG_PATHS = Map.of(
            Character.ROBOT,  "/terminal/robot-indicator.svg",
            Character.CAT,    "/terminal/cat-indicator.svg",
            Character.ALIEN,  "/terminal/alien-indicator.svg",
            Character.YODA,   "/terminal/yoda-indicator.svg"
    );

    /** Loaded-once source SVG per character (before color injection). */
    private static final Map<Character, String> SVG_BY_CHARACTER = loadSvgs();

    /** Inner body markup (everything between {@code </defs>} and {@code </svg>})
     *  per character. Used by the focus-mode overlay to compose a 40×40 SVG
     *  that places a randomly-chosen character inside the animated corner
     *  brackets. Computed once at load time so no string surgery at runtime. */
    private static final Map<Character, String> BODY_XML_BY_CHARACTER = loadBodyXmls();

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(RobotIndicator.class.getName());

    private static Map<Character, String> loadSvgs() {
        Map<Character, String> out = new EnumMap<>(Character.class);
        for (Map.Entry<Character, String> e : SVG_PATHS.entrySet()) {
            String content;
            try (InputStream is = RobotIndicator.class.getResourceAsStream(e.getValue())) {
                if (is == null) {
                    log.warning("Missing " + e.getValue());
                    content = "<svg xmlns='http://www.w3.org/2000/svg' width='32' height='32'><rect width='32' height='32' fill='none'/></svg>";
                } else {
                    byte[] bytes = is.readAllBytes();
                    content = new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (IOException ex) {
                log.warning("Error reading " + e.getValue() + ": " + ex.getMessage());
                content = "<svg xmlns='http://www.w3.org/2000/svg' width='32' height='32'><rect width='32' height='32' fill='none'/></svg>";
            }
            out.put(e.getKey(), content);
        }
        return java.util.Map.copyOf(out);
    }

    /** Extract the inner markup of each character SVG (the part between the
     *  closing {@code </defs>} tag and the closing {@code </svg>} tag). This is
     *  the head + body + laptop + hands + motion-lines block, which the focus
     *  overlay transplants into its 40×40 viewBox under a {@code translate(4,4)}.
     *  Falls back to the full robot body if the tags can't be located. */
    private static Map<Character, String> loadBodyXmls() {
        Map<Character, String> out = new EnumMap<>(Character.class);
        for (Map.Entry<Character, String> e : SVG_BY_CHARACTER.entrySet()) {
            String svg = e.getValue();
            int defsEnd = svg.indexOf("</defs>");
            int svgClose = svg.lastIndexOf("</svg>");
            String body;
            if (defsEnd >= 0 && svgClose > defsEnd) {
                // +7 to skip past "</defs>"; the substring includes any comments
                // and whitespace between </defs> and </svg>, which is exactly
                // head + body + laptop + hands + motion-lines.
                body = svg.substring(defsEnd + 7, svgClose).trim();
            } else {
                // Fallback: use the robot's body if parsing fails.
                body = SVG_BY_CHARACTER.get(Character.ROBOT);
            }
            out.put(e.getKey(), body);
        }
        return java.util.Map.copyOf(out);
    }

    /** Return the inner body markup (head+body+laptop+hands+motion-lines, no
     *  {@code <defs>} or {@code <svg>} wrapper) for the given character. Used by
     *  the focus-mode overlay to compose a 40×40 SVG with animated brackets.
     *  @param chr which character's body to return; {@code null} → ROBOT. */
    public static String bodyXml(Character chr) {
        Character c = (chr == null) ? Character.ROBOT : chr;
        return BODY_XML_BY_CHARACTER.get(c);
    }

    private final WebView webView;

    /** The color-injected SVG markup (project color baked in). */
    private final String coloredSvg;

    /**
     * @param colorHex  CSS hex color (e.g. {@code "#A7F3D0"}) used for the
     *                  character's primary fill. Falls back to white if null/blank.
     * @param character which animated character to render; {@code null} defaults
     *                  to {@link Character#ROBOT}.
     */
    public RobotIndicator(String colorHex, Character character) {
        String color = (colorHex == null || colorHex.isBlank()) ? ProjectColors.DEFAULT : colorHex;
        Character chr = (character == null) ? Character.ROBOT : character;
        // Inject the project color into the SVG by replacing all white fills.
        // The dark accents (#111111) are preserved so face/outline remain visible.
        this.coloredSvg = SVG_BY_CHARACTER.get(chr).replace(ProjectColors.DEFAULT, color);

        webView = new WebView();
        webView.setPrefSize(SIZE, SIZE);
        webView.setMinSize(SIZE, SIZE);
        webView.setMaxSize(SIZE, SIZE);
        webView.setContextMenuEnabled(false);
        // Make the WebView's native page fill transparent so the parent tab's
        // background (active = -app-bg, inactive = -app-panel, hover, etc.)
        // shows through naturally. The SVGs have no opaque background rect,
        // so this is all that's needed — no manual color swapping required.
        webView.setPageFill(javafx.scene.paint.Color.TRANSPARENT);
        webView.getEngine().loadContent(buildHtml());
        getChildren().add(webView);
        getStyleClass().add("robot-indicator");
    }

    /** Builds the WebView HTML with a transparent background. */
    private String buildHtml() {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
                + "html,body{margin:0;padding:0;background:transparent !important;overflow:hidden;width:" + SIZE + "px;height:" + SIZE + "px;}"
                + "svg{display:block;width:" + SIZE + "px;height:" + SIZE + "px;}"
                + "</style></head><body>" + coloredSvg + "</body></html>";
    }

    /** Convenience overload — white robot, the historical default. */
    public RobotIndicator(String colorHex) {
        this(colorHex, Character.ROBOT);
    }

    /** Convenience overload — white robot, no character specified. */
    public RobotIndicator() {
        this(ProjectColors.DEFAULT, Character.ROBOT);
    }
}
