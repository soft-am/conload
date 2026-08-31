package com.conload.ui.components;

import com.conload.ui.Theme;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * A brief splash screen shown on app start — a borderless centered window
 * showing the conload logo (PNG, not WebView-based) and the app name on a
 * dark background. Auto-hidden by {@link #hide()} once the main window is
 * ready.
 *
 * <p>Uses {@link ImageView} with a bundled PNG (not {@link SvgIcon}) to avoid
 * creating a WebView — a WebView loading spinner would block the JavaFX
 * Application Thread during {@code buildScene()} and appear as a stuck loader.
 */
public class SplashScreen {

    private final Stage stage;

    public SplashScreen() {
        this.stage = new Stage(StageStyle.UNDECORATED);
        buildUI();
    }

    private void buildUI() {
        ImageView logo = new ImageView(new Image("/images/logo-128.png"));
        logo.setFitWidth(96);
        logo.setFitHeight(96);

        Label title = new Label("CONLOAD");
        title.getStyleClass().addAll("title", "splash-title");

        Label tagline = new Label("Context layer for CLI coding agents");
        tagline.getStyleClass().addAll("secondary", "small", "splash-tagline");

        VBox content = new VBox(12, logo, title, tagline);
        content.setAlignment(Pos.CENTER);

        StackPane root = new StackPane(content);
        root.getStyleClass().addAll("bg-app", "splash-root");
        root.setPrefSize(440, 300);

        Scene scene = new Scene(root);
        scene.setFill(null);
        Theme.apply(root);
        stage.setScene(scene);
        stage.centerOnScreen();
    }

    public void show() {
        stage.show();
    }

    public void hide() {
        stage.hide();
    }
}
