package com.conload.ui.createcontext;

import com.conload.model.Project;
import com.conload.service.ProjectService;
import com.conload.service.RecursivePageProcessor;
import com.conload.service.SearchDownloadService;
import com.conload.ui.DialogStyler;
import com.conload.ui.Icons;
import com.conload.ui.Theme;
import com.conload.ui.components.UiFactory;
import com.conload.ui.createcontext.model.DownloadTarget;
import com.conload.ui.createcontext.model.JiraTableItem;
import com.conload.ui.createcontext.model.PageSearchResult;
import com.conload.ui.createcontext.model.PageTreeItem;
import com.conload.ui.MainControllerSupport;
import com.conload.github.GitHubClient;
import com.conload.workflow.WorkflowCallbacks;
import com.conload.workflow.WorkflowContext;
import com.conload.workflow.WorkflowEnvironment;
import com.conload.workflow.WorkflowInputs;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns selection, target selection, execution, and presentation of downloads. */
public final class ContextDownloadController {
    private final Host host;
    private TextField nameField;
    private TextField pathField;
    private DownloadProgressPane progressPane;
    private VBox inProgressView;

    public ContextDownloadController(Host host) { this.host = host; }

    public DownloadProgressPane buildProgressPane() {
        progressPane = new DownloadProgressPane(this::start, this::stop, host::hasVisibleResults);
        inProgressView = progressPane.inProgressView();
        return progressPane;
    }

    public VBox inProgressView() { return inProgressView; }

    public VBox buildDownloadTargetSelector() {
        Label title = UiFactory.fieldLabel("SAVE NEW CONTEXT DIRECTORY");
        nameField = UiFactory.darkTextField("");
        nameField.setText(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        nameField.setPrefWidth(280);
        pathField = UiFactory.darkTextField(System.getProperty("user.home") + File.separator + "copilot-export");
        HBox.setHgrow(pathField, Priority.ALWAYS);
        Button browse = UiFactory.actionButton("Browse");
        browse.setOnAction(e -> browseSavePath());
        File fixedFolder = host.addContextTargetFolder();
        HBox row;
        if (fixedFolder != null) {
            pathField.setText(fixedFolder.getAbsolutePath());
            Label savedTo = new Label(Icons.FOLDER + "  " + fixedFolder.getAbsolutePath());
            savedTo.getStyleClass().addAll("hint", "small");
            savedTo.setWrapText(true);
            HBox.setHgrow(savedTo, Priority.ALWAYS);
            row = new HBox(6, nameField, savedTo);
        } else row = new HBox(6, nameField, pathField, browse);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(6, title, row);
        box.setPadding(new Insets(14, 18, 14, 18));
        box.getStyleClass().add("card-bordered");
        VBox.setMargin(box, new Insets(8, 0, 0, 0));
        return box;
    }

    public DownloadTarget resolveDownloadTarget() {
        return new DownloadTarget.NewContext(pathField == null ? "" : pathField.getText().strip(),
                nameField == null ? "" : nameField.getText().strip());
    }
    public TextField nameField() { return nameField; }
    public TextField pathField() { return pathField; }

    public DownloadTarget showDownloadTargetDialog() {
        Dialog<DownloadTarget> dialog = new Dialog<>();
        dialog.setTitle("Save new Context directory");
        DialogPane pane = dialog.getDialogPane();
        pane.setContent(buildDownloadTargetSelector());
        pane.setPrefWidth(700);
        pane.setPrefHeight(200);
        DialogStyler.style(dialog);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().add(save);
        ((Button) pane.lookupButton(save)).getStyleClass().addAll("app-button", "accent");
        dialog.setResultConverter(button -> button == save ? resolveDownloadTarget() : null);
        return dialog.showAndWait().orElse(null);
    }

    public void browseSavePath() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Output Folder");
        if (pathField != null) {
            File current = new File(pathField.getText().strip());
            if (current.isDirectory()) chooser.setInitialDirectory(current);
        }
        File directory = chooser.showDialog(host.stage());
        if (directory != null) pathField.setText(directory.getAbsolutePath());
    }

    public void start() {
        if (host.username().strip().isBlank() || host.token().strip().isBlank()) {
            host.alert("Missing Credentials", "Please fill in your Atlassian email and API token on the 'Config' tab.");
            return;
        }
        Map<String, List<?>> selected = collectSelectedItems(host.selectionData());
        if (selected.isEmpty()) {
            host.alert("Nothing Selected", "Please select at least one item to download from any of the result panels.");
            return;
        }
        DownloadTarget target = showDownloadTargetDialog();
        if (!(target instanceof DownloadTarget.NewContext context)) return;
        String folder = context.folderPath().isBlank() ? defaultFolder() : context.folderPath();
        String name = context.contextName().isBlank() ? String.valueOf(System.currentTimeMillis()) : context.contextName();
        String projectId = host.addContextTargetProjectId() != null ? host.addContextTargetProjectId() : host.activeProjectId();
        ProjectFiles project = host.projectFiles(projectId);
        boolean restorePrompt = host.addContextTargetFolder() != null;
        host.cancelled().set(false);
        host.prepareDownload();
        Task<String> task = host.searchDownloadService().createDownloadTask(host.username().strip(), host.token().strip(),
                host.githubToken().strip(), host.confBaseUrl(), host.jiraBaseUrl(), selected, folder, name,
                host::appendLog, host.cancelled());
        host.setCurrentTask(task);
        host.bindDownload(task);
        task.setOnSucceeded(e -> success(task, projectId, project, restorePrompt, "Download complete!"));
        task.setOnFailed(e -> failure(task, project));
        task.setOnCancelled(e -> cancelled(project));
        host.closeSearchPopup();
        if (project != null) project.pane().showTaskBadge("download", "Adding context… > ", true,
                new File(folder, com.conload.util.FileUtil.normalizeContextFolderName(name)));
        new Thread(task).start();
    }

    /** Runs a recursive gather through the same task, progress, and badge flow as a normal download. */
    public void startCrossContextGather(CrossSource source, String seed, boolean fullMode) {
        String projectId = host.addContextTargetProjectId() != null
                ? host.addContextTargetProjectId() : host.activeProjectId();
        ProjectFiles project = host.projectFiles(projectId);
        boolean restorePrompt = host.addContextTargetFolder() != null;
        host.cancelled().set(false);
        host.prepareDownload();
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                WorkflowEnvironment environment = new WorkflowEnvironment(host.config(), host.githubToken(),
                        host.githubApiUrl(), host.workspacePath(), host.contextsDir(), projectId,
                        host.fullConfluenceFolder());
                WorkflowInputs inputs = new WorkflowInputs(Map.of(source.inputKey(), seed,
                        "fullMode", Boolean.toString(fullMode)));
                WorkflowCallbacks callbacks = new WorkflowCallbacks() {
                    @Override public void onProgress(String stage, String message) {
                        updateMessage("[" + stage + "] " + message);
                    }

                    @Override public void onLog(String line) {
                        host.appendLog(line);
                    }

                    @Override public boolean isCancelled() {
                        return host.cancelled().get() || Thread.currentThread().isInterrupted();
                    }
                };
                WorkflowContext context = source.workflow().accumulate(environment, inputs, callbacks);
                return context.contextRoot().toAbsolutePath().toString();
            }
        };
        host.setCurrentTask(task);
        host.bindDownload(task);
        task.setOnSucceeded(e -> success(task, projectId, project, restorePrompt, "Cross-context ready >"));
        task.setOnFailed(e -> failure(task, project));
        task.setOnCancelled(e -> cancelled(project));
        host.closeSearchPopup();
        if (project != null) project.pane().showTaskBadge("download", "Gathering cross-context… >", true,
                host.contextsDir().toFile());
        Thread.ofVirtual().name("cross-context", 0).start(task);
    }

    public void stop() {
        host.cancelled().set(true);
        Task<?> task = host.currentTask();
        if (task != null) task.cancel(true);
        host.appendLog("[USER] Stop requested...");
        host.setStatus(Icons.STOP + "  STOPPING...", "warning");
    }

    private Map<String, List<?>> collectSelectedItems(SelectionData d) {
        Map<String, List<?>> selected = new LinkedHashMap<>();
        add(selected, "CONF_SEARCH", d.confSearchPanel().pane.isVisible() && d.confSearchTable() != null,
                d.confSearchTable() == null ? List.of() : d.confSearchTable().getItems().stream().filter(PageSearchResult::isSelected).toList());
        add(selected, "CONF_TREE", !d.confTreeEntries().isEmpty(), collectSelectedPages(d.confTreeEntries(), d.globalRecursive()));
        add(selected, "JIRA_SEARCH", d.jiraSearchPanel().pane.isVisible(), selectedJira(d.jiraSearchEntries()));
        add(selected, "JIRA_URL", d.jiraUrlPanel().pane.isVisible(), selectedJiraUrls(d.jiraUrlEntries()));
        add(selected, "GITHUB_COMMIT", d.githubPanel().pane.isVisible(), d.githubCommitEntries().stream()
                .flatMap(e -> e.table().getSelectionModel().getSelectedItems().stream()).filter(java.util.Objects::nonNull).toList());
        add(selected, "GITHUB_ACTION", d.githubActionPanel().pane.isVisible(), d.githubActionEntries().stream()
                .flatMap(e -> e.table().getItems().stream().filter(GitHubActionResult::isSelected)).toList());
        add(selected, "GITHUB_PR", d.githubPrPanel().pane.isVisible(), d.githubPrEntries().stream()
                .flatMap(e -> e.table().getItems().stream().filter(GitHubPrResult::isSelected)).toList());
        return selected;
    }

    private static List<JiraTableItem> selectedJira(List<MainControllerSupport.JiraSearchEntry> entries) {
        return entries.stream().flatMap(e -> e.table().getItems().stream().filter(JiraTableItem::isSelected)).toList();
    }
    private static List<JiraTableItem> selectedJiraUrls(List<MainControllerSupport.JiraUrlEntry> entries) {
        return entries.stream().flatMap(e -> e.table().getItems().stream().filter(JiraTableItem::isSelected)).toList();
    }
    private static void add(Map<String, List<?>> target, String key, boolean available, List<?> items) {
        if (available && !items.isEmpty()) target.put(key, items);
    }
    private static List<RecursivePageProcessor.SelectedPage> collectSelectedPages(List<MainControllerSupport.ConfTreeEntry> entries, boolean recursive) {
        List<RecursivePageProcessor.SelectedPage> result = new ArrayList<>();
        for (var entry : entries) collect(entry.treeView().getRoot(), result, recursive);
        return result;
    }
    private static void collect(TreeItem<PageTreeItem> node, List<RecursivePageProcessor.SelectedPage> out, boolean recursive) {
        if (node == null) return;
        PageTreeItem page = node.getValue();
        if (page != null && page.selected.get()) out.add(new RecursivePageProcessor.SelectedPage(page.pageId, page.title.get(), recursive));
        node.getChildren().forEach(child -> collect(child, out, recursive));
    }

    private void success(Task<String> task, String projectId, ProjectFiles project, boolean restorePrompt, String completionText) {
        if (project != null) project.pane().showTaskBadge("download", Icons.CHECK + " " + completionText, false, null);
        host.setLastSessionPath(task.getValue());
        host.setDownloadState(false);
        host.setStatus(Icons.CHECK + " " + completionText, "success");
        Path folder = task.getValue() == null ? null : Path.of(task.getValue());
        String path = folder == null ? null : folder.toAbsolutePath().toString();
        try {
            if (projectId != null && path != null) host.projectService().addContextFolder(projectId, path);
            if (path != null) host.appendLog("[CONTEXT] New context folder created at: " + path);
        } catch (IOException e) { host.alert("Context Error", "Failed to register context: " + e.getMessage()); }
        if (project != null && folder != null) {
            host.projectService().loadProjects().stream().filter(p -> p.getId().equals(projectId)).findFirst()
                    .ifPresent(p -> project.pane().setContextFolderPaths(p.getContextFolders()));
            project.pane().refresh();
            project.pane().highlightAndExpandPath(folder);
            host.refreshProjects();
        }
        if (restorePrompt) host.restorePromptWorkspace();
        else showCompletion(Icons.CHECK, completionText, path == null ? null : "New context folder: " + path, "success");
    }
    private void failure(Task<String> task, ProjectFiles project) {
        if (project != null) project.pane().showTaskBadge("download", "✗ Download failed", false, null);
        Throwable error = task.getException();
        String detail = error != null && error.getMessage() != null ? error.getMessage() : "Unknown error";
        host.setDownloadState(false); host.setStatus("✗ Download failed: " + detail, "error"); host.appendLog("[ERROR] " + detail);
        if (error != null) error.printStackTrace();
        showCompletion("✗", "Download failed", detail, "error");
    }
    private void cancelled(ProjectFiles project) {
        if (project != null) project.pane().showTaskBadge("download", Icons.STOP + " Download cancelled", false, null);
        host.setDownloadState(false); host.setStatus(Icons.STOP + " Download cancelled.", "warning");
        showCompletion(Icons.STOP, "Download cancelled", "The download was stopped by the user.", "warning");
    }
    private void showCompletion(String icon, String title, String detail, String style) {
        Label heading = new Label(icon + "  " + title);
        heading.getStyleClass().addAll("title", "small");
        if (style != null) heading.getStyleClass().add(style);
        VBox content = new VBox(8, heading);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40, 14, 40, 14));
        Theme.classes(content, Theme.CL_BG_APP);
        if (detail != null && !detail.isBlank()) {
            Label details = new Label(detail);
            details.getStyleClass().add("status");
            details.setWrapText(true);
            content.getChildren().add(details);
        }
        if (host.addContextTargetFolder() != null) {
            Button back = new Button(Icons.BACK + " Back to Prompt");
            back.getStyleClass().addAll("app-button", "accent");
            back.setOnAction(e -> host.restorePromptWorkspace());
            VBox.setMargin(back, new Insets(8, 0, 0, 0));
            content.getChildren().add(back);
        }
        host.mainShell().setCenter(content);
        UiFactory.hide(progressPane.buttonRow());
    }
    private String defaultFolder() { return System.getProperty("user.home") + File.separator + "copilot-export"; }

    public record ProjectFiles(com.conload.ui.projects.ProjectFilesPane pane) {}
    public record SelectionData(ResultPanel<PageSearchResult> confSearchPanel, TableView<PageSearchResult> confSearchTable,
            ResultPanel<?> confTreePanel, List<MainControllerSupport.ConfTreeEntry> confTreeEntries,
            ResultPanel<?> jiraSearchPanel, List<MainControllerSupport.JiraSearchEntry> jiraSearchEntries,
            ResultPanel<?> jiraUrlPanel, List<MainControllerSupport.JiraUrlEntry> jiraUrlEntries,
            ResultPanel<?> githubPanel, List<MainControllerSupport.GitHubCommitEntry> githubCommitEntries,
            ResultPanel<?> githubActionPanel, List<MainControllerSupport.GitHubActionEntry> githubActionEntries,
            ResultPanel<?> githubPrPanel, List<MainControllerSupport.GitHubPrEntry> githubPrEntries, boolean globalRecursive) {}

    public interface Host {
        Stage stage(); String username(); String token(); String githubToken(); String confBaseUrl(); String jiraBaseUrl();
        SelectionData selectionData(); boolean hasVisibleResults(); File addContextTargetFolder(); String addContextTargetProjectId();
        String activeProjectId(); ProjectFiles projectFiles(String projectId); AtomicBoolean cancelled();
        SearchDownloadService searchDownloadService(); ProjectService projectService(); Task<?> currentTask();
        com.conload.model.AppConfig config(); String githubApiUrl(); String workspacePath(); Path contextsDir(); String fullConfluenceFolder();
        void setCurrentTask(Task<String> task); void setLastSessionPath(String path); void prepareDownload(); void bindDownload(Task<String> task);
        void closeSearchPopup(); void appendLog(String message); void setStatus(String message, String style); void setDownloadState(boolean running);
        void alert(String title, String message); BorderPane mainShell();
        void restorePromptWorkspace(); void refreshProjects();
    }
}
