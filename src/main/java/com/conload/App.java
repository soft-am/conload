package com.conload;

import com.conload.service.ConfigService;
import com.conload.ui.AppShellController;
import com.conload.ui.Theme;
import com.conload.util.AppPaths;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX Application entry point.
 * Window: Copilot Context Loader
 * Run: mvn javafx:run
 */
public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        ConfigService configService = new ConfigService();
        AppShellController controller = new AppShellController(primaryStage, configService);

        Scene scene = controller.buildScene();
        Theme.apply(scene.getRoot());
        primaryStage.setTitle("CONLOAD");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(500);
        primaryStage.show();
        controller.maybeShowWelcomeGuide();
        Runnable shutdown = controller::shutdownAll;
        primaryStage.setOnCloseRequest(e -> shutdown.run());
        Runtime.getRuntime().addShutdownHook(new Thread(shutdown, "shutdown-kill"));
    }

    public static void main(String[] args) {
        // Resolve writable state dir (~/.conload/) before any service reads or
        // writes state — works for both dev runs and installed apps (whose CWD
        // is not the repo root). Migrates legacy src/<file> state once.
        AppPaths.bootstrap();
        launch(args);
    }
}

