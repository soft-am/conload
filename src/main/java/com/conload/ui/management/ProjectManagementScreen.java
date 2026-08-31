package com.conload.ui.management;

import com.conload.model.Project;
import com.conload.service.ProjectService;
import com.conload.ui.DialogStyler;
import com.conload.ui.Icons;
import com.conload.ui.Theme;
import com.conload.ui.components.UiFactory;
import com.conload.util.BackgroundTasks;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Builds and manages the Projects screen without owning application navigation. */
public final class ProjectManagementScreen {
    private final Stage stage;
    private final ProjectService projectService;
    private final Consumer<Project> openProject;
    private final BiConsumer<Project, Button> addContext;
    private final Consumer<Project> downloadContext;
    private final Consumer<Project> refreshProjectPane;
    private final BiConsumer<String, String> showAlert;

    public ProjectManagementScreen(Stage stage, ProjectService projectService,
            Consumer<Project> openProject, BiConsumer<Project, Button> addContext,
            Consumer<Project> downloadContext, Consumer<Project> refreshProjectPane,
            BiConsumer<String, String> showAlert) {
        this.stage = stage;
        this.projectService = projectService;
        this.openProject = openProject;
        this.addContext = addContext;
        this.downloadContext = downloadContext;
        this.refreshProjectPane = refreshProjectPane;
        this.showAlert = showAlert;
    }

    public VBox build() {
        VBox root = new VBox(8);
        root.setPadding(new Insets(12, 28, 8, 28));
        Theme.classes(root, Theme.CL_BG_APP);
        Button createFolder = UiFactory.actionButton(" New Project (from Folder)");
        Button createExternal = UiFactory.actionButton(" New Project (from External Data)");
        Button refresh = UiFactory.actionButton(Icons.REFRESH);
        root.getChildren().add(new HBox(8, createFolder, createExternal, refresh));
        Separator separator = new Separator();
        separator.getStyleClass().add("separator");
        root.getChildren().add(separator);
        VBox list = new VBox(6);
        ScrollPane scroll = UiFactory.scrollable(list);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);
        Runnable refreshList = () -> refresh(list, refresh);
        refresh.setOnAction(e -> refreshList.run());
        createFolder.setOnAction(e -> createFromFolder(refresh));
        createExternal.setOnAction(e -> createFromExternal(refresh));
        Platform.runLater(refreshList);
        return root;
    }

    private void refresh(VBox list, Button refresh) {
        list.getChildren().clear();
        for (Project project : projectService.loadProjects()) list.getChildren().add(card(project, refresh));
    }

    private VBox card(Project project, Button refresh) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(8, 10, 8, 10));
        Button open = UiFactory.actionButton(Icons.FORWARD + " Open");
        Button add = UiFactory.actionButton(Icons.ADD_CIRCLE + " Add Context");
        Button delete = UiFactory.errorButton(Icons.CLOSE + " Delete");
        open.setOnAction(e -> openProject.accept(project));
        add.setOnAction(e -> addContext.accept(project, refresh));
        delete.setOnAction(e -> delete(project, refresh));
        card.getChildren().addAll(header(project, open, add, delete), contextsDirectory(project, refresh), contextList(project, refresh));
        return card;
    }

    private HBox header(Project project, Button open, Button add, Button delete) {
        Label name = new Label(Icons.ACTIVE + " " + project.getName());
        name.getStyleClass().add("title");
        Label path = new Label(project.getParentPath() != null ? " (" + project.getParentPath() + ")" : "");
        path.getStyleClass().add("secondary");
        HBox header = new HBox(10, name, path, UiFactory.hSpacer(), open, add, delete);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox contextList(Project project, Button refresh) {
        VBox list = new VBox(3);
        list.setPadding(new Insets(0, 0, 0, 16));
        List<String> folders = project.getContextFolders();
        for (int i = 0; i < folders.size(); i++) list.getChildren().add(contextRow(project, refresh, folders.get(i), i, folders.size()));
        return list;
    }

    private HBox contextRow(Project project, Button refresh, String folderPath, int index, int total) {
        File folder = new File(folderPath);
        String displayName = folder.exists() ? folder.getName() : folderPath;
        Label label = new Label(Icons.BRANCH + " " + displayName + "  [" + folderPath + "]");
        label.getStyleClass().add("secondary");
        Button up = orderButton(project, refresh, folderPath, index == 0, true);
        Button down = orderButton(project, refresh, folderPath, index == total - 1, false);
        Button remove = UiFactory.errorButton(Icons.CLOSE);
        remove.getStyleClass().addAll("remove-button", "small", "icon");
        remove.setOnAction(e -> removeContext(project, refresh, folderPath, displayName));
        HBox row = new HBox(10, label, UiFactory.hSpacer(), up, down, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Button orderButton(Project project, Button refresh, String path, boolean disabled, boolean up) {
        Button button = new Button(up ? Icons.UP : Icons.DOWN);
        button.getStyleClass().addAll("app-button", "small", "icon");
        button.setDisable(disabled);
        button.setOnAction(e -> { try { projectService.moveContextFolder(project.getId(), path, up); refresh.fire(); }
            catch (Exception ex) { showAlert.accept("Error", ex.getMessage()); } });
        return button;
    }

    private HBox contextsDirectory(Project project, Button refresh) {
        Label label = new Label(Icons.FOLDER + "  Contexts: " + projectService.resolveContextsDir(project));
        label.getStyleClass().add("secondary");
        Button edit = UiFactory.actionButton(Icons.EDIT);
        edit.setTooltip(new Tooltip("Change contexts directory"));
        edit.setOnAction(e -> changeContextsDirectory(project, refresh));
        HBox row = new HBox(6, label, edit);
        row.setAlignment(Pos.CENTER_LEFT); row.setPadding(new Insets(0, 0, 0, 20));
        return row;
    }

    private void delete(Project project, Button refresh) {
        try { projectService.deleteProject(project.getId()); refresh.fire(); }
        catch (Exception ex) { showAlert.accept("Error", ex.getMessage()); }
    }

    private void changeContextsDirectory(Project project, Button refresh) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Contexts Directory for " + project.getName());
        File current = projectService.resolveContextsDir(project).toFile();
        if (current.isDirectory()) chooser.setInitialDirectory(current);
        File directory = chooser.showDialog(stage);
        if (directory == null) return;
        try { project.setContextsDir(directory.getAbsolutePath()); projectService.updateProject(project);
            projectService.syncProjectFolder(project); refresh.fire(); }
        catch (Exception ex) { showAlert.accept("Error", ex.getMessage()); }
    }

    private void removeContext(Project project, Button refresh, String path, String name) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete \"" + name + "\" and all its contents?\n\n" + path + "\n\nThis cannot be undone.", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Remove Context"); confirm.setHeaderText("Remove context folder"); DialogStyler.style(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        BackgroundTasks.runIOTask("remove-context-thread", () -> { try { projectService.removeContextFolder(project.getId(), path);
            Platform.runLater(() -> { refreshProjectPane.accept(project); refresh.fire(); }); }
            catch (Exception ex) { Platform.runLater(() -> showAlert.accept("Error", "Failed to remove context: " + ex.getMessage())); } });
    }

    private void createFromFolder(Button refresh) {
        DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle("Select Existing Project Folder");
        File directory = chooser.showDialog(stage); if (directory == null) return;
        TextInputDialog dialog = new TextInputDialog(directory.getName()); dialog.setTitle("Project Name");
        dialog.setHeaderText("Enter name for this project"); DialogStyler.style(dialog);
        dialog.showAndWait().ifPresent(name -> { try { Project project = projectService.createProjectInFolder(name, new ArrayList<>(), directory.getAbsolutePath());
            refresh.fire(); openProject.accept(project); } catch (Exception ex) { showAlert.accept("Error", ex.getMessage()); } });
    }

    private void createFromExternal(Button refresh) {
        TextInputDialog dialog = new TextInputDialog("New Project"); dialog.setTitle("Project Name");
        dialog.setHeaderText("Enter a name for the new project.\nContent will be downloaded into this project."); DialogStyler.style(dialog);
        dialog.showAndWait().ifPresent(name -> { String trimmed = name.strip(); if (trimmed.isBlank()) return;
            try { Path dir = newExternalDirectory(trimmed); Project project = projectService.createProjectInFolder(trimmed, new ArrayList<>(), dir.toString());
                refresh.fire(); downloadContext.accept(project); } catch (Exception ex) { showAlert.accept("Error", "Failed to create project: " + ex.getMessage()); } });
    }

    private Path newExternalDirectory(String name) throws IOException {
        Path base = Path.of(System.getProperty("user.home"), "copilot-projects"); Files.createDirectories(base);
        String safe = name.replaceAll("[^A-Za-z0-9_-]", "_"); Path dir = base.resolve(safe); int suffix = 1;
        while (Files.exists(dir)) dir = base.resolve(safe + "_" + suffix++); return dir;
    }
}
