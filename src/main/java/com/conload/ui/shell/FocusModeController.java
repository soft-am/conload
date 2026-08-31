package com.conload.ui.shell;

import com.conload.ui.components.FocusModeIcon;
import com.conload.ui.components.UiFactory;
import com.conload.ui.terminal.RobotIndicator;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Owns the focus overlay, including its window and WebView state. */
public final class FocusModeController {
    private final Stage stage;
    private StackPane overlay;
    private Label workingLabel;
    private Button toggleButton;
    private Timeline dotsTimeline;
    private WebView robotView;
    private boolean active;
    private double savedX, savedY, savedW, savedH;
    private boolean savedMaximized;
    private static String svgTemplate;

    public FocusModeController(Stage stage) { this.stage = stage; }

    public boolean isActive() { return active; }

    public Button buildToggleButton() {
        toggleButton = new Button();
        toggleButton.getStyleClass().addAll("icon-button", "secondary", "icon", "brand-logo-btn");
        toggleButton.setGraphic(new FocusModeIcon());
        toggleButton.setTooltip(new Tooltip("Hide screen — focus mode"));
        toggleButton.setOnAction(e -> toggle());
        return toggleButton;
    }

    public StackPane buildOverlay() {
        String html = robotHtml(composeSvg());
        robotView = new WebView();
        robotView.setPrefSize(200, 200);
        robotView.setMinSize(200, 200);
        robotView.setMaxSize(200, 200);
        robotView.setContextMenuEnabled(false);
        robotView.getEngine().loadContent(html);
        StackPane robotPane = new StackPane(robotView);
        robotPane.setMinSize(200, 200);
        robotPane.setMaxSize(200, 200);

        workingLabel = new Label("agent working");
        workingLabel.getStyleClass().add("focus-working-text");
        String[] messages = {"agent working", "agent working .", "agent working . .", "agent working . . .",
                "Bildschirm gesperrt. Agent arbeitet.", "Sicher gesperrt. KI arbeitet weiter.",
                "Locked for humans. Open for agents."};
        dotsTimeline = new Timeline();
        for (int i = 0; i < messages.length; i++) {
            String message = messages[i];
            dotsTimeline.getKeyFrames().add(new KeyFrame(Duration.millis(i * 1000L), e -> workingLabel.setText(message)));
        }
        dotsTimeline.setCycleCount(Animation.INDEFINITE);

        Button showButton = new Button(" ");
        showButton.getStyleClass().add("focus-show-btn");
        showButton.setOnAction(e -> toggle());
        VBox content = new VBox(24, robotPane, workingLabel, showButton);
        content.setAlignment(javafx.geometry.Pos.CENTER);
        overlay = new StackPane(content);
        overlay.getStyleClass().add("focus-overlay");
        UiFactory.hide(overlay);
        return overlay;
    }

    public void toggle() {
        if (active) {
            active = false;
            dotsTimeline.stop();
            UiFactory.hide(overlay);
            stage.setFullScreen(false);
            if (savedMaximized) stage.setMaximized(true);
            else {
                stage.setX(savedX); stage.setY(savedY);
                stage.setWidth(savedW); stage.setHeight(savedH);
            }
            toggleButton.setTooltip(new Tooltip("Hide screen — focus mode"));
            return;
        }
        savedMaximized = stage.isMaximized();
        savedX = stage.getX(); savedY = stage.getY();
        savedW = stage.getWidth(); savedH = stage.getHeight();
        stage.setFullScreen(true);
        UiFactory.show(overlay);
        overlay.toFront();
        robotView.getEngine().loadContent(robotHtml(composeSvg()));
        toggleButton.setTooltip(new Tooltip("Show screen — exit focus mode"));
        active = true;
        dotsTimeline.play();
    }

    private static String robotHtml(String svg) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
                + "html,body{margin:0;padding:0;background:#050D12;overflow:hidden;width:200px;height:200px;}"
                + "svg{display:block;width:200px;height:200px;}"
                + "</style></head><body>" + svg + "</body></html>";
    }

    private static String composeSvg() {
        RobotIndicator.Character character = RobotIndicator.Character.random();
        String body = RobotIndicator.bodyXml(character);
        String inner = body == null || body.isBlank() ? RobotIndicator.bodyXml(RobotIndicator.Character.ROBOT) : body;
        return focusTemplate().replace("{{inner}}", inner);
    }

    private static synchronized String focusTemplate() {
        if (svgTemplate != null) return svgTemplate;
        try (InputStream input = FocusModeController.class.getResourceAsStream("/terminal/focus-robot.svg")) {
            svgTemplate = input == null ? fallbackSvg() : new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { svgTemplate = fallbackSvg(); }
        return svgTemplate;
    }

    private static String fallbackSvg() {
        return "<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40' viewBox='0 0 40 40'><rect width='40' height='40' fill='none'/></svg>";
    }
}
