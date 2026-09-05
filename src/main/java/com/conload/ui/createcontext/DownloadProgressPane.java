package com.conload.ui.createcontext;

import com.conload.ui.Icons;
import com.conload.ui.Theme;
import com.conload.ui.components.UiFactory;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Owns download controls and progress presentation, independent of its host controller. */
public final class DownloadProgressPane {
    private static final String LOG_TITLE = "  Progress logs";
    private final Button startButton;
    private final Button stopButton;
    private final HBox buttonRow;
    private final ProgressIndicator spinner;
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label("");
    private final TextArea logArea = new TextArea();
    private final TitledPane logPane;
    private final VBox inProgressView;
    private final VBox searchInProgressView;
    private final BooleanSupplier hasResults;
    private final ProgressBar searchProgressBar = new ProgressBar();
    private final Label searchStatusLabel = new Label("");
    private final HBox searchStatusRow;
    private Timeline ellipsisTimeline;
    private int ellipsisIndex;

    public DownloadProgressPane(Runnable start, Runnable stop, BooleanSupplier hasResults) {
        this.hasResults = hasResults;
        spinner = new ProgressIndicator();
        spinner.setPrefSize(14, 14);
        spinner.getStyleClass().add("download-spinner");
        UiFactory.hide(spinner);
        Label download = new Label("Save context");
        download.getStyleClass().addAll("app-button", "accent");
        HBox content = new HBox(6, download, spinner);
        content.setAlignment(Pos.CENTER);
        startButton = new Button();
        startButton.setGraphic(content);
        startButton.getStyleClass().addAll("app-button", "accent");
        startButton.setDisable(true);
        startButton.setOnAction(e -> start.run());
        stopButton = UiFactory.errorButton("Stop");
        stopButton.setDisable(true);
        stopButton.setOnAction(e -> stop.run());
        buttonRow = new HBox(6, startButton, stopButton);
        buttonRow.setAlignment(Pos.CENTER);
        UiFactory.hide(buttonRow);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(4);
        VBox.setMargin(progressBar, new Insets(6, 0, 0, 0));
        statusLabel.getStyleClass().add("status");
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setPrefHeight(180);
        logArea.getStyleClass().add("log-area");
        logPane = new TitledPane(LOG_TITLE, logArea);
        logPane.setExpanded(false);
        logPane.setAnimated(true);
        logPane.getStyleClass().add("progress-log-pane");
        VBox.setVgrow(logPane, Priority.NEVER);
        inProgressView = buildInProgressView();
        searchInProgressView = buildSearchInProgressView();
        searchProgressBar.setPrefWidth(Double.MAX_VALUE);
        searchProgressBar.setPrefHeight(4);
        searchProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        searchProgressBar.getStyleClass().add("search-progress-bar");
        UiFactory.hide(searchProgressBar);
        searchStatusLabel.getStyleClass().add("status");
        searchStatusRow = new HBox(6, searchStatusLabel, UiFactory.hSpacer());
        searchStatusRow.setAlignment(Pos.CENTER_LEFT);
        UiFactory.hide(searchStatusRow);
    }

    public VBox buildFooter() {
        VBox footer = new VBox(0, buttonRow, progressBar, statusLabel,
                searchProgressBar, searchStatusRow, logPane);
        footer.setPadding(new Insets(0, 22, 14, 22));
        Theme.classes(footer, Theme.CL_BG_APP);
        return footer;
    }

    public VBox inProgressView() { return inProgressView; }
    public VBox searchInProgressView() { return searchInProgressView; }
    public Button startButton() { return startButton; }
    public Button stopButton() { return stopButton; }
    public HBox buttonRow() { return buttonRow; }
    public ProgressIndicator spinner() { return spinner; }
    public ProgressBar progressBar() { return progressBar; }
    public Label statusLabel() { return statusLabel; }
    public TextArea logArea() { return logArea; }
    public TitledPane logPane() { return logPane; }
    public ProgressBar searchProgressBar() { return searchProgressBar; }
    public Label searchStatusLabel() { return searchStatusLabel; }
    public HBox searchStatusRow() { return searchStatusRow; }

    public void setRunning(boolean running) {
        startButton.setDisable(running || !hasResults.getAsBoolean());
        stopButton.setDisable(!running);
        if (!running) progressBar.progressProperty().unbind();
        spinner.setVisible(running);
        if (running) {
            startEllipsisAnimation();
            UiFactory.show(buttonRow);
            logPane.setExpanded(true);
        } else stopEllipsisAnimation();
    }

    public void updateSaveButtonState() {
        boolean available = hasResults.getAsBoolean();
        startButton.setDisable(!available);
        UiFactory.setVisible(buttonRow, available);
    }

    /** Toggles search-progress presentation: indeterminate bar + status label
     *  above the (auto-expanded) progress log. */
    public void setSearching(boolean searching) {
        UiFactory.setVisible(searchProgressBar, searching);
        UiFactory.setVisible(searchStatusRow, searching);
        if (searching) {
            searchProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            logPane.setExpanded(true);
            startEllipsisAnimation();
        } else {
            stopEllipsisAnimation();
        }
    }

    /** Updates the search status label text and style class. */
    public void setSearchStatus(String msg, String styleKey) {
        searchStatusLabel.setText(msg);
        searchStatusLabel.getStyleClass().removeAll("success", "warning", "error");
        if (styleKey != null) searchStatusLabel.getStyleClass().add(styleKey);
    }

    public void appendLog(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    public void reset() {
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);
        statusLabel.textProperty().unbind();
        statusLabel.setText("");
        searchStatusLabel.setText("");
        searchStatusLabel.getStyleClass().removeAll("success", "warning", "error");
        setSearching(false);
    }

    private VBox buildInProgressView() {
        ProgressIndicator progress = new ProgressIndicator(-1);
        progress.setPrefSize(48, 48);
        Theme.classes(progress, Theme.CL_PROGRESS_ACCENT);
        Label label = new Label(Icons.LOADING + "  Downloading in progress…");
        label.getStyleClass().addAll("title", "small");
        VBox view = new VBox(14, progress, label);
        view.setAlignment(Pos.CENTER);
        view.setPadding(new Insets(40, 14, 40, 14));
        Theme.classes(view, Theme.CL_BG_APP);
        UiFactory.hide(view);
        return view;
    }

    private VBox buildSearchInProgressView() {
        ProgressIndicator progress = new ProgressIndicator(-1);
        progress.setPrefSize(48, 48);
        Theme.classes(progress, Theme.CL_PROGRESS_ACCENT);
        Label label = new Label(Icons.LOADING + "  Searching — please wait…");
        label.getStyleClass().addAll("title", "small");
        VBox view = new VBox(14, progress, label);
        view.setAlignment(Pos.CENTER);
        view.setPadding(new Insets(40, 14, 40, 14));
        Theme.classes(view, Theme.CL_BG_APP);
        UiFactory.hide(view);
        return view;
    }

    private void startEllipsisAnimation() {
        if (ellipsisTimeline != null) ellipsisTimeline.stop();
        String[] frames = {LOG_TITLE + "   ", LOG_TITLE + ".  ", LOG_TITLE + ".. ", LOG_TITLE + "..."};
        ellipsisIndex = 0;
        logPane.setText(frames[0]);
        ellipsisTimeline = new Timeline(new KeyFrame(Duration.millis(450), e -> {
            ellipsisIndex = (ellipsisIndex + 1) % frames.length;
            logPane.setText(frames[ellipsisIndex]);
        }));
        ellipsisTimeline.setCycleCount(Timeline.INDEFINITE);
        ellipsisTimeline.play();
    }

    private void stopEllipsisAnimation() {
        if (ellipsisTimeline != null) {
            ellipsisTimeline.stop();
            ellipsisTimeline = null;
        }
        logPane.setText(LOG_TITLE);
    }
}
