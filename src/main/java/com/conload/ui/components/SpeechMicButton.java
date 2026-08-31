package com.conload.ui.components;

import com.conload.ui.ProjectColors;
import com.conload.service.SpeechRecognitionService;
import com.conload.ui.Icons;
import com.conload.ui.Theme;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.animation.AnimationTimer;
import javafx.scene.Scene;

import java.util.function.Consumer;

/**
 * Icon-only mic button that owns the full speech-to-text recording flow:
 * idle/ready state, breathing pulse while recording, the dictation popup
 * (with EN/DE language toggle), and "Stop &amp; Send" / "Cancel" handlers.
 *
 * <p>Extracted from {@code CopilotTerminalPane} so the prompt-header row can
 * host the mic button; the terminal bar no longer carries it. Recognized
 * text is delivered to the supplied {@code terminalTextSink} (typically the
 * same funnel the prompt's "send" button uses, i.e.
 * {@code sendToActiveTerminal}).</p>
 *
 * <p>The button is disabled until a {@link SpeechRecognitionService} is set
 * via {@link #setSpeechService(SpeechRecognitionService)} and that service's
 * model is available.</p>
 */
public final class SpeechMicButton extends Button {

    private SpeechRecognitionService speechService;
    private String projectColor = ProjectColors.DEFAULT;
    private Window ownerWindow;
    private final Consumer<String> terminalTextSink;

    // Recording-state UI bits (rebuilt per recording session)
    private Stage recordingPopup;
    private Label recordingTextLabel;
    private Button langToggleBtn;
    private final StringBuilder finalText = new StringBuilder();
    private String currentPartial = "";

    // Breathing-pulse timer
    private AnimationTimer micPulse;
    private long micPulseStartNanos;

    public SpeechMicButton(Window ownerWindow, Consumer<String> terminalTextSink) {
        this.ownerWindow = ownerWindow;
        this.terminalTextSink = terminalTextSink;
        setGraphic(buildMicIcon());
        getStyleClass().addAll("icon-button", "mic-button");
        setTooltip(new Tooltip("Record speech — text is sent to terminal"));
        setDisable(true);
        setOnAction(e -> toggleMicRecording());
    }

    /**
     * Builds the per-project-tinted microphone icon as a native JavaFX vector
     * {@link Group} (a rounded-rect capsule + three stroked SVG paths), scaled
     * from the source 24×24 viewBox to 16px.
     *
     * <p>This deliberately avoids {@link SvgIcon}/{@link javafx.scene.web.WebView}
     * — a WebView always paints an opaque page background and would capture
     * mouse events, so a button graphic built on it can't be transparent and
     * wouldn't be clickable. Native JavaFX shapes paint only along their
     * geometry (fully transparent elsewhere) and bubble mouse events up to the
     * parent {@link Button}, so the mic is genuinely see-through and its
     * {@code setOnAction} fires when clicked. The shapes fill/stroke in
     * {@link #projectColor} so the icon tints per-project, matching the SVG.
     *
     * <p>Geometry mirrors {@code /images/mic-white.svg} (a 24×24 viewBox):
     * <ul>
     *   <li>rect x=9 y=2 w=6 h11 rx=3 (capsule, filled)</li>
     *   <li>path "M5 11V12a7 7 0 0 0 14 0V11" (cradle, stroked)</li>
     *   <li>path "M12 19V22" (stem, stroked)</li>
     *   <li>path "M8 22h8" (base, stroked)</li>
     * </ul>
     */
    private Node buildMicIcon() {
        Color fill = Color.web(
            (projectColor == null || projectColor.isBlank()) ? ProjectColors.DEFAULT : projectColor);

        // Capsule: rounded rect (arcWidth/arcHeight are the full arc dims, so
        // rx=3 → arc width/height = 6).
        Rectangle capsule = new Rectangle(9, 2, 6, 11);
        capsule.setArcWidth(6);
        capsule.setArcHeight(6);
        capsule.setFill(fill);
        capsule.setStroke(null);

        // Stroked paths: cradle + stem + base. Stroke = 2, round caps (matches
        // the SVG's stroke-width="2" stroke-linecap="round").
        SVGPath cradle = strokedPath("M5 11 V12 a7 7 0 0 0 14 0 V11", fill);
        SVGPath stem   = strokedPath("M12 19 V22", fill);
        SVGPath base   = strokedPath("M8 22 h8", fill);

        Group mic = new Group(capsule, cradle, stem, base);
        // Source geometry is in a 24×24 viewBox; render at 16px. Scale around
        // origin (0,0) — the paths span ~x[5,22] y[2,22], so after scaling they
        // fit within the 16×16 button graphic.
        double s = 16.0 / 24.0;
        mic.setScaleX(s);
        mic.setScaleY(s);
        return mic;
    }

    /** Helper: builds a stroke-only {@link SVGPath} (no fill) with the given
     *  content, 2px stroke, and round line caps — matching the mic SVG's
     *  stroked-subpath styling. */
    private static SVGPath strokedPath(String content, Color stroke) {
        SVGPath p = new SVGPath();
        p.setContent(content);
        p.setFill(null);
        p.setStroke(stroke);
        p.setStrokeWidth(2);
        p.setStrokeLineCap(StrokeLineCap.ROUND);
        return p;
    }

    /** Inject the shared speech service; refreshes enabled state + lang toggle. */
    public void setSpeechService(SpeechRecognitionService svc) {
        this.speechService = svc;
        updateMicButtonState();
    }

    /** Apply the project accent color (drives the breathing-pulse fill and
     *  re-tints the mic SVG icon to match the project's identity color). */
    public void setProjectColor(String hex) {
        this.projectColor = (hex == null || hex.isBlank()) ? ProjectColors.DEFAULT : hex;
        // Rebuild the mic icon so it tints in the new project color.
        setGraphic(buildMicIcon());
    }

    /** Set the owner window used to anchor the recording popup (centered over
     *  the app window). Call once the panel is added to a scene. */
    public void setOwnerWindow(Window w) {
        this.ownerWindow = w;
    }

    /** Enable/disable + tooltip based on model availability. */
    public void refreshState() {
        updateMicButtonState();
    }

    // ── Recording flow ───────────────────────────────────────────────────────

    private void toggleMicRecording() {
        if (speechService == null) return;
        if (speechService.isRecording()) {
            // Stop recording
            getStyleClass().remove("recording");
            setTooltip(new Tooltip("Record speech — text is sent to terminal"));
            stopMicPulse();
            speechService.stopRecording();
        } else {
            // Start recording — open the popup first for instant feedback.
            showRecordingPopup();
            getStyleClass().add("recording");
            setTooltip(new Tooltip("Stop recording"));
            startMicPulse();
            finalText.setLength(0);
            currentPartial = "";
            speechService.startRecording(
                text -> Platform.runLater(() -> {
                    finalText.append(text).append(" ");
                    currentPartial = "";
                    if (recordingTextLabel != null) {
                        recordingTextLabel.setText(finalText.toString());
                    }
                }),
                status -> Platform.runLater(() -> {
                    if (recordingTextLabel == null) return;
                    if (status.startsWith("Listening") || status.startsWith("Loading")
                            || status.startsWith("Error")) return;
                    currentPartial = status;
                    String display = finalText.toString() + currentPartial;
                    recordingTextLabel.setText(display);
                })
            );
        }
    }

    // ── Breathing pulse ──────────────────────────────────────────────────────

    private void startMicPulse() {
        stopMicPulse();
        final Color base;
        try {
            base = Color.web(projectColor);
        } catch (IllegalArgumentException e) {
            return;
        }
        micPulseStartNanos = System.nanoTime();
        micPulse = new AnimationTimer() {
            @Override public void handle(long now) {
                double t = (now - micPulseStartNanos) / 1_000_000_000.0;
                double alpha = 0.35 + 0.23 * Math.sin(t * (2 * Math.PI / 1.5));
                Color tinted = base.deriveColor(0, 1.0, 1.0, alpha);
                setBackground(new Background(new BackgroundFill(tinted,
                    new CornerRadii(6), Insets.EMPTY)));
            }
        };
        micPulse.start();
    }

    private void stopMicPulse() {
        if (micPulse != null) {
            micPulse.stop();
            micPulse = null;
        }
        setBackground(Background.EMPTY);
    }

    // ── Dictation popup ──────────────────────────────────────────────────────

    private void showRecordingPopup() {
        recordingPopup = new Stage();
        if (ownerWindow != null) recordingPopup.initOwner(ownerWindow);
        recordingPopup.initModality(Modality.NONE);
        recordingPopup.setTitle(Icons.MIC + " Dictate your prompt");

        Label headerLabel = new Label(Icons.MIC + "  Dictate your prompt");
        Theme.classes(headerLabel, Theme.CL_TITLE_SMALL);

        langToggleBtn = new Button("EN");
        langToggleBtn.getStyleClass().addAll("app-button", "popup-lang-toggle");
        langToggleBtn.setTooltip(new Tooltip("Switch speech language (EN / DE)"));
        langToggleBtn.setOnAction(e -> toggleSpeechLanguage());
        updateLangToggle();

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(8, headerLabel, headerSpacer, langToggleBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 12, 8, 12));

        recordingTextLabel = new Label("");
        recordingTextLabel.setWrapText(true);
        recordingTextLabel.getStyleClass().addAll("input", "recording-text-label");
        recordingTextLabel.setMaxWidth(Double.MAX_VALUE);
        recordingTextLabel.setMinHeight(200);

        ScrollPane scroll = UiFactory.scrollable(recordingTextLabel);
        scroll.setPrefSize(580, 300);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button stopBtn = new Button(Icons.STOP_REC + " Stop & Send to Terminal");
        stopBtn.getStyleClass().addAll("app-button", "recording");
        stopBtn.setOnAction(e -> stopRecordingAndSend());

        Button cancelBtn = UiFactory.errorButton("Cancel");
        cancelBtn.setOnAction(e -> cancelRecording());

        HBox footer = new HBox(8, stopBtn, cancelBtn);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(8));
        footer.getStyleClass().add("panel-border-top");

        VBox root = new VBox(0, header, scroll, footer);
        root.getStyleClass().addAll("bg-app", "popup-window-root");

        recordingPopup.setMinWidth(800);
        recordingPopup.setMinHeight(600);
        double popW = 800;
        double popH = 600;
        Scene scene = new Scene(root, popW, popH);
        Theme.apply(root);
        recordingPopup.setScene(scene);
        recordingPopup.setOnCloseRequest(e -> { e.consume(); cancelRecording(); });
        if (ownerWindow != null) {
            double cx = ownerWindow.getX() + (ownerWindow.getWidth() - popW) / 2;
            double cy = ownerWindow.getY() + (ownerWindow.getHeight() - popH) / 2;
            recordingPopup.setX(Math.max(0, cx));
            recordingPopup.setY(Math.max(0, cy));
        }
        recordingPopup.setAlwaysOnTop(true);
        recordingPopup.show();
        Platform.runLater(() -> {
            recordingPopup.toFront();
            recordingPopup.requestFocus();
        });
    }

    private void stopRecordingAndSend() {
        String result = (finalText.toString() + " " + currentPartial).trim();
        getStyleClass().remove("recording");
        setTooltip(new Tooltip("Record speech — text is sent to terminal"));
        stopMicPulse();
        if (speechService != null) speechService.stopRecording();
        closeRecordingPopup();
        finalText.setLength(0);
        currentPartial = "";
        if (!result.isBlank() && terminalTextSink != null) {
            terminalTextSink.accept(result + "\n");
        }
    }

    private void cancelRecording() {
        closeRecordingPopup();
        getStyleClass().remove("recording");
        setTooltip(new Tooltip("Record speech — text is sent to terminal"));
        stopMicPulse();
        if (speechService != null) speechService.stopRecording();
        finalText.setLength(0);
        currentPartial = "";
    }

    private void closeRecordingPopup() {
        if (recordingPopup != null) {
            recordingPopup.close();
            recordingPopup = null;
            recordingTextLabel = null;
            langToggleBtn = null;
        }
    }

    // ── Speech service helpers ────────────────────────────────────────────────

    private void updateMicButtonState() {
        if (speechService == null) {
            setDisable(true);
            return;
        }
        boolean ready = speechService.isModelAvailable();
        setDisable(!ready);
        setTooltip(new Tooltip(ready
            ? "Record speech — text is sent to terminal"
            : speechService.getModelStatus()));
    }

    private void toggleSpeechLanguage() {
        if (speechService == null) return;
        var current = speechService.getCurrentLang();
        var next = current.toggle();
        if (!speechService.isModelAvailable(next)) return;
        speechService.setCurrentLang(next);
        try {
            new com.conload.service.ConfigService().setVoskLang(next.code);
        } catch (Exception ignored) {}
        updateLangToggle();
    }

    private void updateLangToggle() {
        if (langToggleBtn == null || speechService == null) return;
        var lang = speechService.getCurrentLang();
        langToggleBtn.setText(lang.code.toUpperCase());
        langToggleBtn.setTooltip(new Tooltip("Speech language: " + lang.displayName));
    }
}
