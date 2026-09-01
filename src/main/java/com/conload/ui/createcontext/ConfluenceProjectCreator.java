package com.conload.ui.createcontext;

import com.conload.confluence.ConfluenceClient;
import com.conload.model.AppConfig;
import com.conload.model.Project;
import com.conload.service.ProjectService;
import com.conload.service.RecursivePageProcessor;
import com.conload.service.download.ContextDownloadService;
import com.conload.service.search.SourceInputClassifier;
import com.conload.ui.DialogStyler;
import com.conload.ui.Icons;
import com.conload.ui.Theme;
import com.conload.ui.components.UiFactory;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Creates a new project from a Confluence page URL via a simplified popup:
 * the user enters a Confluence page URL and (optionally) a project name, the
 * page tree is downloaded as Markdown into the project's contexts directory,
 * and the project is activated.
 * <p>
 * Reuses {@link ContextDownloadService} (same download path as normal "Download
 * Context") and {@link SourceInputClassifier} for URL parsing, so the
 * Confluence-to-Markdown pipeline is identical to the search/download flow.
 */
public final class ConfluenceProjectCreator {

    private final Stage owner;
    private final Supplier<AppConfig> configSupplier;
    private final ProjectService projectService;
    private final Consumer<String> logger;
    private final Consumer<Project> onProjectReady;

    private Stage dialog;
    private TextField urlField;
    private TextField nameField;
    private Label statusLabel;
    private Button downloadBtn;
    private ProgressIndicator spinner;

    public ConfluenceProjectCreator(Stage owner, Supplier<AppConfig> configSupplier,
                                    ProjectService projectService,
                                    Consumer<String> logger,
                                    Consumer<Project> onProjectReady) {
        this.owner = owner;
        this.configSupplier = configSupplier;
        this.projectService = projectService;
        this.logger = logger;
        this.onProjectReady = onProjectReady;
    }

    public void show() {
        AppConfig config = configSupplier.get();
        if (config.getUsername().isBlank() || config.getToken().isBlank()) {
            alert("Missing Credentials",
                    "Please fill in your Atlassian email and API token on the 'Config' tab.");
            return;
        }
        if (dialog == null) buildDialog();
        urlField.clear();
        nameField.clear();
        statusLabel.setText("");
        statusLabel.setStyle(null);
        downloadBtn.setDisable(false);
        UiFactory.hide(spinner);
        dialog.show();
        urlField.requestFocus();
    }

    private void buildDialog() {
        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("New Project from Confluence");
        dialog.setMinWidth(580);

        Label urlLabel = UiFactory.fieldLabel("Confluence Page URL:");
        urlField = UiFactory.darkTextField("https://site.atlassian.net/wiki/spaces/.../pages/123/Title");
        urlField.setPrefWidth(520);
        urlField.focusedProperty().addListener((obs, was, now) -> {
            if (!now && nameField.getText().isBlank()) lookupTitle();
        });

        Label nameLabel = UiFactory.fieldLabel("Project Name:");
        nameField = UiFactory.darkTextField("auto-filled from page title");
        nameField.setPrefWidth(520);

        downloadBtn = UiFactory.accentButton(Icons.DOWNLOAD + "  Download & Create");
        downloadBtn.setOnAction(e -> startDownload());

        Button cancelBtn = UiFactory.actionButton("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());

        spinner = new ProgressIndicator();
        spinner.getStyleClass().add("download-spinner");
        spinner.setPrefSize(14, 14);
        spinner.setMaxSize(14, 14);
        UiFactory.hide(spinner);

        statusLabel = new Label("");
        statusLabel.getStyleClass().addAll("small", "muted");
        statusLabel.setWrapText(true);
        statusLabel.setMinHeight(20);

        HBox buttonRow = new HBox(8, downloadBtn, cancelBtn, spinner);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(8, urlLabel, urlField, nameLabel, nameField, buttonRow, statusLabel);
        content.setPadding(new Insets(20));
        content.getStyleClass().addAll("bg-app", "popup-window-root");

        Scene scene = new Scene(content);
        Theme.apply(content);
        dialog.setScene(scene);
    }

    /**
     * Background-fetches the Confluence page title for the current URL and
     * fills the name field (only when empty). Runs on a virtual thread.
     */
    private void lookupTitle() {
        String url = urlField.getText().strip();
        if (url.isBlank()) return;
        SourceInputClassifier.ConfluenceInput ci =
                new SourceInputClassifier().classifyConfluence(url);
        if (!ci.isTreeUrl() || ci.pageId() == null) return;
        AppConfig config = configSupplier.get();
        String base = ci.baseUrl() != null ? ci.baseUrl() : config.getBaseUrl();
        if (base == null || base.isBlank()) return;
        Thread.startVirtualThread(() -> {
            try {
                var page = new ConfluenceClient(new AppConfig(
                        config.getUsername(), config.getToken(), base))
                        .getPage(base, ci.pageId());
                Platform.runLater(() -> {
                    if (nameField.getText().isBlank()) nameField.setText(page.getTitle());
                });
            } catch (Exception ignored) { /* name stays blank; resolved on Download */ }
        });
    }

    private void startDownload() {
        String url = urlField.getText().strip();
        if (url.isBlank()) {
            setStatus("Please enter a Confluence page URL.", "error");
            return;
        }
        AppConfig config = configSupplier.get();
        if (config.getUsername().isBlank() || config.getToken().isBlank()) {
            setStatus("Missing Atlassian credentials. Configure them in the Config tab.", "error");
            return;
        }
        SourceInputClassifier.ConfluenceInput ci =
                new SourceInputClassifier().classifyConfluence(url);
        if (!ci.isTreeUrl() || ci.pageId() == null) {
            setStatus("Could not extract a Confluence page ID from the URL.", "error");
            return;
        }
        String confBase = ci.baseUrl() != null ? ci.baseUrl() : config.getBaseUrl();
        if (confBase == null || confBase.isBlank()) {
            setStatus("No Confluence base URL. Enter a full URL or configure it in the Config tab.", "error");
            return;
        }

        downloadBtn.setDisable(true);
        UiFactory.show(spinner);
        setStatus("Fetching page info…", null);
        String projectName = nameField.getText().strip();

        Thread.startVirtualThread(() -> {
            try {
                var page = new ConfluenceClient(new AppConfig(
                        config.getUsername(), config.getToken(), confBase))
                        .getPage(confBase, ci.pageId());
                String resolvedName = projectName.isBlank() ? page.getTitle() : projectName;
                Platform.runLater(() -> nameField.setText(resolvedName));

                Path projectDir = newExternalProjectDirectory(resolvedName);
                Project project = projectService.createProjectInFolder(
                        resolvedName, List.of(), projectDir.toString());
                Path contextsDir = projectService.resolveContextsDir(project);
                Files.createDirectories(contextsDir);

                Map<String, List<?>> selected = Map.of("CONF_TREE",
                        List.of(new RecursivePageProcessor.SelectedPage(
                                ci.pageId(), page.getTitle(), true)));

                AtomicBoolean cancelled = new AtomicBoolean(false);
                Task<String> task = new ContextDownloadService().createDownloadTask(
                        config.getUsername(), config.getToken(), "",
                        confBase, confBase, selected,
                        contextsDir.toString(), "confluence",
                        msg -> Platform.runLater(() -> setStatus(msg, null)),
                        cancelled);

                task.messageProperty().addListener((obs, old, msg) ->
                        Platform.runLater(() -> setStatus(msg, null)));

                task.setOnSucceeded(e -> Platform.runLater(() -> {
                    String folder = task.getValue();
                    try {
                        if (folder != null)
                            projectService.addContextFolder(project.getId(), folder);
                    } catch (IOException ex) {
                        logger.accept("[CONFLUENCE-PROJECT] Failed to register context: "
                                + ex.getMessage());
                    }
                    logger.accept("[CONFLUENCE-PROJECT] \u2713 Project \"" + resolvedName
                            + "\" created with Confluence context.");
                    dialog.close();
                    onProjectReady.accept(project);
                }));

                task.setOnFailed(e -> Platform.runLater(() -> {
                    downloadBtn.setDisable(false);
                    UiFactory.hide(spinner);
                    Throwable err = task.getException();
                    setStatus("Download failed: "
                            + (err != null && err.getMessage() != null ? err.getMessage() : "unknown error"),
                            "error");
                }));

                task.setOnCancelled(e -> Platform.runLater(() -> {
                    downloadBtn.setDisable(false);
                    UiFactory.hide(spinner);
                    setStatus("Download cancelled.", "warning");
                }));

                new Thread(task).start();
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    downloadBtn.setDisable(false);
                    UiFactory.hide(spinner);
                    setStatus("Error: " + ex.getMessage(), "error");
                });
            }
        });
    }

    private void setStatus(String text, String style) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("error", "warning", "success");
        if (style != null) statusLabel.getStyleClass().add(style);
    }

    private void alert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(owner);
        DialogStyler.style(alert);
        alert.showAndWait();
    }

    private static Path newExternalProjectDirectory(String projectName) throws IOException {
        String baseDir = System.getProperty("user.home") + File.separator + "copilot-projects";
        Files.createDirectories(Path.of(baseDir));
        String safeFolder = projectName.replaceAll("[^A-Za-z0-9_-]", "_");
        Path projectDir = Path.of(baseDir, safeFolder);
        int suffix = 1;
        while (Files.exists(projectDir)) {
            projectDir = Path.of(baseDir, safeFolder + "_" + suffix++);
        }
        return projectDir;
    }
}
