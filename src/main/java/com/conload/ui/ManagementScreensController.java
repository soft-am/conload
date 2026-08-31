package com.conload.ui;


import com.conload.model.AppConfig;
import com.conload.model.Project;
import com.conload.util.BackgroundTasks;
import com.conload.service.ConfigService;
import com.conload.service.ProjectService;
import com.conload.ui.components.UiFactory;
import com.conload.ui.components.LocalContextFolderDialog;
import com.conload.ui.Icons;
import com.conload.ui.projects.ProjectFilesPane;
import com.conload.ui.prompttemplate.dialog.PromptTemplateEditorDialog;
import com.conload.ui.management.ProjectManagementScreen;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ManagementScreensController extends MainControllerSupport {
    private final ProjectManagementScreen projectManagementScreen;

    protected ManagementScreensController(Stage stage, ConfigService configService) {
        super(stage, configService);
        projectManagementScreen = new ProjectManagementScreen(stage, projectService,
                this::switchToProject, this::showAddContextChoiceDialog,
                this::showProjectDownloadContext, this::refreshProjectPane,
                this::showAlert);
    }

    protected abstract void refreshProjectTabsBar();

    protected abstract void switchToProject(Project project);

    /** Mount the download / search panel inline, targeting the given folder. */
    protected abstract void showProjectAddContextView(File folder);

    /** Show the Projects management view (list / create / edit) full-window. */

    public void switchToProjects() {
        cachedProjectsPanel = buildProjectsTab(); // rebuild to reflect latest data
        showFullWindow("PROJECTS", cachedProjectsPanel);
        refreshProjectTabsBar();
    }

    /** Show the Prompts management view (list / create / edit prompt templates) full-window. */

    public void switchToPrompts() {
        cachedPromptsPanel = buildPromptsTab();
        showFullWindow("PROMPTS", cachedPromptsPanel);
    }

    /** Show the in-app Help / developer user guide full-window. */

    public void switchToHelp() {
        cachedHelpPanel = com.conload.ui.components.HelpView.build();
        showFullWindow("HELP", cachedHelpPanel);
    }

    // =========================================================================
    // Prompts tab — full-window tabbed library:
    //   Tab 1 "Prompt Templates"    — user prompt-template CRUD (QuickAction)
    //   Tab 2 "Workflow Templates"  — editable workflow prompt templates with
    //                                locked ${variables} (WorkflowTemplateLibraryPane)
    // =========================================================================


    protected javafx.scene.Node buildPromptsTab() {
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("prompts-library-tabs");
        VBox.setVgrow(tabs, Priority.ALWAYS);

        Tab promptTab = new Tab("Prompt Templates");
        promptTab.getStyleClass().add("prompts-library-tabs");
        promptTab.setClosable(false);
        promptTab.setContent(buildPromptTemplatesContent());

        Tab workflowTab = new Tab("Workflow Templates");
        workflowTab.setClosable(false);
        Runnable onWorkflowUpdated = () -> {
            if (sharedPromptPanel != null) sharedPromptPanel.refreshTemplates();
        };
        workflowTab.setContent(new com.conload.ui.prompttemplate.WorkflowTemplateLibraryPane(stage, onWorkflowUpdated));

        tabs.getTabs().addAll(promptTab, workflowTab);
        tabs.getSelectionModel().select(0);

        VBox root = new VBox(tabs);
        root.setPadding(new Insets(8, 12, 8, 12));
        //Theme.classes(root, Theme.CL_BG_APP);
        return root;
    }

    /** Builds the "Prompt Templates" tab body: toolbar (create / refresh) + scrollable card list. */
    private javafx.scene.Node buildPromptTemplatesContent() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(12, 16, 12, 16));
        Theme.classes(root, Theme.CL_BG_APP);

        Label header = new Label("# Prompt Templates");
        header.getStyleClass().addAll("title");
        header.setPadding(new Insets(0, 0, 4, 0));

        Button btnCreate = UiFactory.actionButton("+ New Prompt Template");
        Button btnRefresh = UiFactory.actionButton(Icons.REFRESH + " Refresh");
        Region sp = UiFactory.hSpacer();
        HBox topRow = new HBox(10, header, sp, btnCreate, btnRefresh);
        topRow.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().add(topRow);

        Separator sep = new Separator();
        sep.getStyleClass().add("separator");
        root.getChildren().add(sep);

        VBox list = new VBox(10);
        ScrollPane scroll = UiFactory.scrollable(list);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);

        com.conload.service.QuickActionService qas = new com.conload.service.QuickActionService();

        Runnable refresh = () -> {
            list.getChildren().clear();
            List<com.conload.model.QuickAction> templates = qas.load().stream()
                .filter(com.conload.model.QuickAction::isTemplate)
                .toList();
            if (templates.isEmpty()) {
                Label empty = new Label("No prompt templates yet. Click \"+ New Prompt Template\" to create one.");
                empty.getStyleClass().add("placeholder");
                list.getChildren().add(empty);
            } else {
                for (com.conload.model.QuickAction qa : templates) list.getChildren().add(buildPromptCard(qa, qas));
            }
        };

        btnCreate.setOnAction(e -> {
            showPromptEditPopup(null, qas);
        });
        btnRefresh.setOnAction(e -> refresh.run());
        refresh.run();
        return root;
    }


    protected VBox buildPromptCard(com.conload.model.QuickAction qa, com.conload.service.QuickActionService qas) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");

        Label nameLbl = new Label(Icons.DOCUMENT + " " + qa.getName());
        nameLbl.getStyleClass().add("title");
        Region sp = UiFactory.hSpacer();

        Button btnEdit = UiFactory.actionButton(Icons.EDIT + " Edit");
        Button btnDel  = UiFactory.errorButton(Icons.CLOSE + " Delete");
        HBox hdr = new HBox(10, nameLbl, sp, btnEdit, btnDel);
        hdr.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().add(hdr);

        btnEdit.setOnAction(e -> showPromptEditPopup(qa, qas));
        btnDel.setOnAction(e -> {
            Alert cf = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete prompt template \"" + qa.getName() + "\"?", ButtonType.YES, ButtonType.NO);
            cf.setHeaderText(null);
            DialogStyler.style(cf);
            cf.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> {
                try {
                    qas.delete(qa.getId());
                    if (sharedPromptPanel != null) sharedPromptPanel.refreshTemplates();
                    switchToPrompts();
                } catch (IOException ex) {
                    showAlert("Error", "Delete failed: " + ex.getMessage());
                }
            });
        });

        return card;
    }

     protected void showPromptEditPopup(com.conload.model.QuickAction existing, com.conload.service.QuickActionService qas) {
         boolean isEdit = existing != null;
         double popW = Math.max(UiFactory.widePopupWidth(stage), 800);
         double popH = Math.max(UiFactory.widePopupHeight(stage), 650);
         PromptTemplateEditorDialog dialog = new PromptTemplateEditorDialog(existing,
                 isEdit ? "Edit Prompt Template" : "New Prompt Template", popW, popH);
         dialog.setHeaderText(isEdit ? "Edit Prompt Template" : "New Prompt Template");
         dialog.showAndWait(stage, Modality.WINDOW_MODAL).ifPresent(result -> {
             try {
                 if (isEdit) qas.update(result);
                 else {
                     List<com.conload.model.QuickAction> all = qas.load();
                     all.add(result);
                     qas.save(all);
                 }
                 if (sharedPromptPanel != null) sharedPromptPanel.refreshTemplates();
                 switchToPrompts();
             } catch (IOException ex) {
                 Alert alert = new Alert(Alert.AlertType.ERROR, "Save failed: " + ex.getMessage());
                 DialogStyler.style(alert);
                 alert.showAndWait();
             }
         });
     }

    // =========================================================================
    // TAB 3 — Projects
    // =========================================================================


    protected VBox buildProjectsTab() {
        return projectManagementScreen.build();
        /*
        VBox root = new VBox(8);
        root.setPadding(new Insets(12, 28, 8, 28));
        Theme.classes(root, Theme.CL_BG_APP);

        Button btnCreateFolder   = UiFactory.actionButton(" New Project (from Folder)");
        Button btnCreateExternal = UiFactory.actionButton(" New Project (from External Data)");
        Button btnRefresh        = UiFactory.actionButton(Icons.REFRESH );
        root.getChildren().add(buildProjectsToolbar(btnCreateFolder, btnCreateExternal, btnRefresh));
        root.getChildren().add(separator());

        VBox projectsList = new VBox(6);
        ScrollPane scroll = UiFactory.scrollable(projectsList);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);

        Runnable refreshList = () -> refreshProjects(projectsList, btnRefresh);
        btnRefresh.setOnAction(e -> refreshList.run());
        btnCreateFolder.setOnAction(e -> createProjectFromFolder(btnRefresh));
        btnCreateExternal.setOnAction(e -> createProjectFromExternal(btnRefresh));
        Platform.runLater(refreshList);
        return root;
        */
    }

    private void refreshProjectPane(Project project) {
        ProjectFilesPane pane = projectFilesPanes.get(project.getId());
        if (pane == null) return;
        Project updated = projectService.findById(project.getId());
        if (updated != null) pane.setContextFolderPaths(updated.getContextFolders());
        pane.refresh();
    }

    /* Project node construction and project CRUD live in ProjectManagementScreen. */
    /*
    private HBox buildProjectsToolbar(Button createFolder, Button createExternal, Button refresh) {
        HBox toolbar = new HBox(8, createFolder, createExternal, refresh);
        return toolbar;
    }

    private Separator separator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("separator");
        return separator;
    }

    private void refreshProjects(VBox projectsList, Button refreshButton) {
        projectsList.getChildren().clear();
        ProjectService ps = projectService;
        for (Project project : ps.loadProjects()) {
            projectsList.getChildren().add(buildProjectCard(project, ps, refreshButton));
        }
    }

    private VBox buildProjectCard(Project project, ProjectService ps, Button refreshButton) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(8, 10, 8, 10));

        Button open = UiFactory.actionButton(Icons.FORWARD + " Open");
        Button addContext = UiFactory.actionButton(Icons.ADD_CIRCLE + " Add Context");
        Button delete = UiFactory.errorButton(Icons.CLOSE + " Delete");
        open.setOnAction(e -> switchToProject(project));
        addContext.setOnAction(e -> showAddContextChoiceDialog(project, refreshButton));
        delete.setOnAction(e -> deleteProject(project, ps, refreshButton));

        card.getChildren().addAll(buildProjectHeader(project, open, addContext, delete),
                buildContextsDirectoryRow(project, refreshButton),
                buildContextList(project, ps, refreshButton));
        return card;
    }

    private HBox buildProjectHeader(Project project, Button open, Button addContext, Button delete) {
        Label name = new Label(Icons.ACTIVE + " " + project.getName());
        name.getStyleClass().add("title");
        Label path = new Label(project.getParentPath() != null ? " (" + project.getParentPath() + ")" : "");
        path.getStyleClass().add("secondary");
        HBox header = new HBox(10, name, path, UiFactory.hSpacer(), open, addContext, delete);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox buildContextList(Project project, ProjectService ps, Button refreshButton) {
        VBox list = new VBox(3);
        list.setPadding(new Insets(0, 0, 0, 16));
        List<String> folders = project.getContextFolders();
        for (int i = 0; i < folders.size(); i++) {
            list.getChildren().add(buildContextRow(project, ps, refreshButton, folders.get(i), i, folders.size()));
        }
        return list;
    }

    private HBox buildContextRow(Project project, ProjectService ps, Button refreshButton,
                                 String folderPath, int index, int total) {
        File folder = new File(folderPath);
        String displayName = folder.exists() ? folder.getName() : folderPath;
        Label label = new Label(Icons.BRANCH + " " + displayName + "  [" + folderPath + "]");
        label.getStyleClass().add("secondary");

        Button up = contextOrderButton(Icons.UP, index == 0, project, ps, folderPath, true, refreshButton);
        Button down = contextOrderButton(Icons.DOWN, index == total - 1, project, ps, folderPath, false, refreshButton);
        Button remove = UiFactory.errorButton(Icons.CLOSE);
        remove.getStyleClass().addAll("remove-button", "small", "icon");
        remove.setOnAction(e -> removeContext(project, ps, refreshButton, folderPath, displayName));

        HBox row = new HBox(10, label, UiFactory.hSpacer(), up, down, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Button contextOrderButton(String icon, boolean disabled, Project project, ProjectService ps,
                                      String folderPath, boolean moveUp, Button refreshButton) {
        Button button = new Button(icon);
        button.getStyleClass().addAll("app-button", "small", "icon");
        button.setDisable(disabled);
        button.setOnAction(e -> {
            try {
                ps.moveContextFolder(project.getId(), folderPath, moveUp);
                refreshButton.fire();
            } catch (Exception ex) {
                showAlert("Error", ex.getMessage());
            }
        });
        return button;
    }

    private HBox buildContextsDirectoryRow(Project project, Button refreshButton) {
        Label label = new Label(Icons.FOLDER + "  Contexts: " + projectService.resolveContextsDir(project));
        label.getStyleClass().add("secondary");
        Button edit = UiFactory.actionButton(Icons.EDIT);
        edit.setTooltip(new Tooltip("Change contexts directory"));
        edit.setOnAction(e -> changeContextsDirectory(project, refreshButton));
        HBox row = new HBox(6, label, edit);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 0, 0, 20));
        return row;
    }

    private void deleteProject(Project project, ProjectService ps, Button refreshButton) {
        try {
            ps.deleteProject(project.getId());
            refreshButton.fire();
        } catch (Exception ex) {
            showAlert("Error", ex.getMessage());
        }
    }

    private void changeContextsDirectory(Project project, Button refreshButton) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Contexts Directory for " + project.getName());
        File current = projectService.resolveContextsDir(project).toFile();
        if (current.isDirectory()) chooser.setInitialDirectory(current);
        File directory = chooser.showDialog(stage);
        if (directory == null) return;
        try {
            project.setContextsDir(directory.getAbsolutePath());
            projectService.updateProject(project);
            projectService.syncProjectFolder(project);
            refreshButton.fire();
        } catch (Exception ex) {
            showAlert("Error", ex.getMessage());
        }
    }

    private void removeContext(Project project, ProjectService ps, Button refreshButton,
                               String folderPath, String displayName) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + displayName + "\" and all its contents?\n\n" + folderPath + "\n\nThis cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Remove Context");
        confirm.setHeaderText("Remove context folder");
        DialogStyler.style(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        BackgroundTasks.runIOTask("remove-context-thread", () -> {
            try {
                ps.removeContextFolder(project.getId(), folderPath);
                Platform.runLater(() -> refreshProjectAfterContextRemoval(project, ps, refreshButton));
            } catch (Exception ex) {
                Platform.runLater(() -> showAlert("Error", "Failed to remove context: " + ex.getMessage()));
            }
        });
    }

    private void refreshProjectAfterContextRemoval(Project project, ProjectService ps, Button refreshButton) {
        ProjectFilesPane pane = projectFilesPanes.get(project.getId());
        if (pane != null) {
            Project updated = ps.findById(project.getId());
            if (updated != null) pane.setContextFolderPaths(updated.getContextFolders());
            pane.refresh();
        }
        refreshButton.fire();
    }

    private void createProjectFromFolder(Button refreshButton) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Existing Project Folder");
        File directory = chooser.showDialog(stage);
        if (directory == null) return;
        TextInputDialog dialog = new TextInputDialog(directory.getName());
        dialog.setTitle("Project Name");
        dialog.setHeaderText("Enter name for this project");
        DialogStyler.style(dialog);
        dialog.showAndWait().ifPresent(name -> {
            try {
                Project project = projectService.createProjectInFolder(name, new ArrayList<>(), directory.getAbsolutePath());
                refreshButton.fire();
                switchToProject(project);
            } catch (Exception ex) {
                showAlert("Error", ex.getMessage());
            }
        });
    }

    private void createProjectFromExternal(Button refreshButton) {
        TextInputDialog dialog = new TextInputDialog("New Project");
        dialog.setTitle("Project Name");
        dialog.setHeaderText("Enter a name for the new project.\nContent will be downloaded into this project.");
        DialogStyler.style(dialog);
        dialog.showAndWait().ifPresent(name -> {
            String trimmed = name.strip();
            if (trimmed.isBlank()) return;
            try {
                Path projectDir = newExternalProjectDirectory(trimmed);
                Project project = projectService.createProjectInFolder(trimmed, new ArrayList<>(), projectDir.toString());
                refreshButton.fire();
                showProjectDownloadContext(project);
            } catch (Exception ex) {
                showAlert("Error", "Failed to create project: " + ex.getMessage());
            }
        });
    }

    private Path newExternalProjectDirectory(String projectName) throws IOException {
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

    */

    /**
     * Shows a directory picker + name prompt, creates the project, and switches
     * to it. Used by the top-bar "Project" kebab "Open…" menu item.
     *
     * @return the newly created project, or {@code null} if the user cancelled
     *         or creation failed (error already shown).
     */
    protected Project openProjectFromFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Existing Project Folder");
        File directory = chooser.showDialog(stage);
        if (directory == null) return null;
        TextInputDialog dialog = new TextInputDialog(directory.getName());
        dialog.setTitle("Project Name");
        dialog.setHeaderText("Enter name for this project");
        DialogStyler.style(dialog);
        return dialog.showAndWait().map(name -> {
            try {
                Project project = projectService.createProjectInFolder(
                        name, new ArrayList<>(), directory.getAbsolutePath());
                switchToProject(project);
                return project;
            } catch (Exception ex) {
                showAlert("Error", ex.getMessage());
                return null;
            }
        }).orElse(null);
    }

    // =========================================================================
    // Add Context dialog — choose between local import / download
    // =========================================================================

    /**
     * Shows a small dialog letting the user choose how to add a context to a
     * project: import local files/folders, or download new content (Confluence
     * / Jira / GitHub).
     */
    protected void showAddContextChoiceDialog(Project p, Button refreshBtn) {
        Alert choice = new Alert(Alert.AlertType.NONE);
        choice.setTitle("Add Context");
        choice.setHeaderText("Add a context to \"" + p.getName() + "\"");
        DialogStyler.style(choice);

        ButtonType localBtn   = new ButtonType("Link Local Folder", ButtonBar.ButtonData.OTHER);
        ButtonType downloadBtn = new ButtonType("Download Context", ButtonBar.ButtonData.OTHER);
        ButtonType cancelBtn   = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        choice.getButtonTypes().setAll(localBtn, downloadBtn, cancelBtn);

        choice.showAndWait().ifPresent(b -> {
            if (b == localBtn) {
                showLocalImportDialog(p, refreshBtn);
            } else if (b == downloadBtn) {
                showProjectDownloadContext(p);
            }
        });
    }

    /**
     * Opens a folder picker and links the chosen folders to the project as
     * contexts <b>by reference</b> (no copy). Each picked folder's original
     * path is registered via {@code addContextFolder}. Removing such a context
     * later deletes the original folder from disk.
     */
    protected void showLocalImportDialog(Project p, Button refreshBtn) {
        new LocalContextFolderDialog(stage).showAndWait().ifPresent(selected -> {
            if (selected.isEmpty()) {
                showAlert("No Folders", "Please select at least one folder to link.");
                return;
            }
            try {
                Path projRoot = p.getParentPath() != null && !p.getParentPath().isBlank()
                        ? Path.of(p.getParentPath()) : null;
                for (Path src : selected) {
                    String absPath = src.toAbsolutePath().toString();
                    // Safety guard: refuse to link the project root itself (or a
                    // folder that contains it) — otherwise "Remove Context" could
                    // delete the entire project folder.
                    if (projRoot != null && (projRoot.equals(src) || projRoot.startsWith(src))) {
                        showAlert("Refused", "Cannot link a folder that is the project root or contains it:\n" + absPath);
                        continue;
                    }
                    projectService.addContextFolder(p.getId(), absPath);
                }
                ProjectFilesPane projectPane = projectFilesPanes.get(p.getId());
                if (projectPane != null) {
                    Project updated = projectService.findById(p.getId());
                    if (updated != null) {
                        projectPane.setContextFolderPaths(updated.getContextFolders());
                    }
                    projectPane.refresh();
                }
                refreshBtn.fire();
            } catch (Exception ex) {
                showAlert("Error", "Link failed: " + ex.getMessage());
            }
        });
    }

    /**
     * Opens the search / download panel for a given project so the user can
     * download Confluence / Jira / GitHub content into a new context folder.
     */
    protected void showProjectDownloadContext(Project p) {
        activeProjectId = p.getId();
        Path contextsDir = projectService.resolveContextsDir(p);
        try { Files.createDirectories(contextsDir); }
        catch (IOException ex) { showAlert("Error", "Could not create contexts dir: " + ex.getMessage()); }
        showProjectAddContextView(contextsDir.toFile());
    }
}
