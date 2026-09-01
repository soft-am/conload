package com.conload.ui.components;

import javafx.application.Platform;

import java.util.function.Consumer;

/** Shared route for non-modal application errors shown in the shell banner. */
public final class AppErrorNotifier {
    private static Consumer<String> reporter = message -> { };

    private AppErrorNotifier() { }

    public static void setReporter(Consumer<String> value) {
        reporter = value != null ? value : message -> { };
    }

    public static void report(String message) {
        if (message == null || message.isBlank()) return;
        if (Platform.isFxApplicationThread()) reporter.accept(message);
        else Platform.runLater(() -> reporter.accept(message));
    }
}
