package com.conload.ui.projects;

import com.conload.ui.Icons;
import com.conload.ui.components.UiFactory;
import com.conload.ui.projects.files.ProjectFileTreeCell;
import com.conload.ui.projects.sidebar.SidebarSectionHeader;
import com.conload.ui.projects.sidebar.WorktreeSidebarSection;
import com.conload.ui.terminal.RobotIndicator;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Left side-pane showing the local project files and registered context. */
public class ProjectFilesPane extends VBox {
    private File workDir;
    private final Consumer<File> onFileSelected;
    private TreeView<File> treeView;
    private TreeView<File> contextTreeView;
    private WorktreeSidebarSection worktreeSection;
    private Map<String, List<com.conload.ui.projects.sidebar.WorktreeSessionBadge.SessionInfo>> worktreeSessionLabels = Map.of();
    private String currentWorktreePath = "";
    private Runnable onCreateWorktree;
    private Runnable onRefreshWorktrees;
    private Consumer<com.conload.model.Worktree> onSelectWorktree;
    private Consumer<com.conload.model.Worktree> onRemoveWorktree;
    private Consumer<File> onAddLocalContext;
    private Consumer<File> onDownloadContext;
    private Consumer<File> onRemoveContext;
    private Consumer<File> onRemoveSession;
    private Consumer<String> onPutToTerminal;
    private Consumer<File> onViewMarkdown;
    private Consumer<File> onOpenSession;
    private BiConsumer<File, String> onRenameContextFolder;
    private BiConsumer<File, String> onRenameSessionFolder;
    private Consumer<File> onRunWorkflow;
    private BiConsumer<String, Integer> onActivateTerminalSession;
    private Runnable onTaskBadgeClick;
    private boolean pickerModeActive;
    private Button addContextBtn;
    private Button codeToggleBtn;
    private Button codeRefreshBtn;
    private HBox taskBadgeBox;
    private HBox taskBadgeHeader;
    private ProgressIndicator taskBadgeSpinner;
    private Label taskBadgeLabel;
    private String projectName = "";
    private RobotIndicator.Character projectCharacter;
    private String accentColor = "";
    private Set<String> contextFolderPaths = new HashSet<>();
    private Set<String> sessionFolderPaths = new HashSet<>();

    public ProjectFilesPane(File workDir, Consumer<File> onFileSelected) {
        this.workDir = workDir;
        this.onFileSelected = onFileSelected;
        setSpacing(0); setPadding(Insets.EMPTY); getStyleClass().add("panel-border-left");
        setMinWidth(410); setPrefWidth(410); setMaxWidth(Double.MAX_VALUE); buildUI();
    }
    public void setOnAddLocalContext(Consumer<File> callback) { onAddLocalContext = callback; }
    public void setOnDownloadContext(Consumer<File> callback) { onDownloadContext = callback; }
    public void setOnRemoveContext(Consumer<File> callback) { onRemoveContext = callback; }
    public void setOnRemoveSession(Consumer<File> callback) { onRemoveSession = callback; }
    public void setOnPutToTerminal(Consumer<String> callback) { onPutToTerminal = callback; }
    public void setOnViewMarkdown(Consumer<File> callback) { onViewMarkdown = callback; }
    public void setOnOpenSession(Consumer<File> callback) { onOpenSession = callback; }
    public void setProjectName(String name) { projectName = name == null ? "" : name; }
    public void setProjectCharacter(RobotIndicator.Character character) {
        projectCharacter = character; allTreeViews().forEach(tv -> { if (tv != null) tv.refresh(); });
    }
    public void setOnRenameContextFolder(BiConsumer<File, String> callback) { onRenameContextFolder = callback; }
    public void setOnRenameSessionFolder(BiConsumer<File, String> callback) { onRenameSessionFolder = callback; }
    public void setOnRunWorkflow(Consumer<File> callback) { onRunWorkflow = callback; }
    public void setOnActivateTerminalSession(BiConsumer<String, Integer> callback) { onActivateTerminalSession = callback; }
    public void setContextFolderPaths(java.util.Collection<String> paths) {
        contextFolderPaths = paths == null ? new HashSet<>() : new HashSet<>(paths);
        if (contextTreeView != null) javafx.application.Platform.runLater(this::rebuildContextSection);
    }
    public void setSessionFolderPaths(java.util.Collection<String> paths) {
        sessionFolderPaths = paths == null ? new HashSet<>() : new HashSet<>(paths);
        if (contextTreeView != null) javafx.application.Platform.runLater(this::rebuildContextSection);
    }
    public void setAccentColor(String colorHex) {
        if (colorHex == null || colorHex.isBlank()) return;
        accentColor = colorHex; setStyle("-accent-color: " + colorHex + ";");
        allTreeViews().forEach(tv -> { if (tv != null) tv.refresh(); });
    }
    private List<TreeView<File>> allTreeViews() { return List.of(treeView, contextTreeView); }
    public void setPickerMode(boolean active) {
        pickerModeActive = active;
        allTreeViews().forEach(tv -> { if (tv != null) { tv.setCursor(active ? Cursor.HAND : Cursor.DEFAULT); if (active) tv.getStyleClass().add("picker-mode"); else tv.getStyleClass().remove("picker-mode"); } });
    }
    public boolean isPickerModeActive() { return pickerModeActive; }
    public void refresh() { javafx.application.Platform.runLater(() -> { rebuildCodeSection(); rebuildContextSection(); }); }
    public void setCodeRoot(File dir) { if (dir != null) { workDir = dir; if (treeView != null) javafx.application.Platform.runLater(this::rebuildCodeSection); } }
    public void setWorktreeSectionVisible(boolean visible) { if (worktreeSection != null) worktreeSection.setVisible(visible); }
    public boolean isWorktreeSectionVisible() { return worktreeSection != null && worktreeSection.isVisible(); }
    public void setWorktrees(List<com.conload.model.Worktree> worktrees, Map<String, List<com.conload.ui.projects.sidebar.WorktreeSessionBadge.SessionInfo>> labels) {
        worktreeSessionLabels = labels == null ? Map.of() : new HashMap<>(labels);
        if (worktreeSection != null) worktreeSection.setWorktrees(worktrees == null ? List.of() : worktrees);
    }
    public void updateWorktreeSessions(Map<String, List<com.conload.ui.projects.sidebar.WorktreeSessionBadge.SessionInfo>> labels) { worktreeSessionLabels = labels == null ? Map.of() : new HashMap<>(labels); if (worktreeSection != null) worktreeSection.refreshRows(); }
    public void setCurrentWorktreePath(String path) { currentWorktreePath = path == null ? "" : path; if (worktreeSection != null) worktreeSection.refreshRows(); }
    public void setWorktreeBusy(boolean busy) { if (worktreeSection != null) worktreeSection.setBusy(busy); }
    public void setWorktreeError(String message) { if (worktreeSection != null) worktreeSection.setError(message); }
    public void setOnCreateWorktree(Runnable cb) { onCreateWorktree = cb; }
    public void setOnRefreshWorktrees(Runnable cb) { onRefreshWorktrees = cb; }
    public void setOnSelectWorktree(Consumer<com.conload.model.Worktree> cb) { onSelectWorktree = cb; }
    public void setOnRemoveWorktree(Consumer<com.conload.model.Worktree> cb) { onRemoveWorktree = cb; }

    private ProjectTreeItems.SectionHeaderTreeItem buildCodeSection() {
        ProjectTreeItems.SectionHeaderTreeItem section = new ProjectTreeItems.SectionHeaderTreeItem("CODE", ProjectTreeItems.codeChildren(workDir));
        pinSectionExpanded(section); return section;
    }
    private ProjectTreeItems.SectionHeaderTreeItem buildContextSection() {
        List<TreeItem<File>> children = new ArrayList<>();
        contextFolderPaths.stream().map(File::new).filter(File::exists).sorted(java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)).map(ProjectTreeItems.ContextFolderTreeItem::new).forEach(children::add);
        sessionFolderPaths.stream().map(File::new).filter(File::exists).sorted(java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)).map(ProjectTreeItems.SessionLeafItem::new).forEach(children::add);
        ProjectTreeItems.SectionHeaderTreeItem section = new ProjectTreeItems.SectionHeaderTreeItem("CONTEXT", children); pinSectionExpanded(section); return section;
    }
    private void rebuildCodeSection() { ProjectTreeItems.SectionHeaderTreeItem root = buildCodeSection(); treeView.setRoot(root); }
    private void rebuildContextSection() { ProjectTreeItems.SectionHeaderTreeItem root = buildContextSection(); contextTreeView.setRoot(root); }
    private void pinSectionExpanded(TreeItem<File> section) { section.setExpanded(true); section.expandedProperty().addListener((obs, old, expanded) -> { if (!expanded) javafx.application.Platform.runLater(() -> section.setExpanded(true)); }); }

    public void highlightAndExpandPath(Path targetPath) {
        javafx.application.Platform.runLater(() -> { File target = targetPath.toFile(); for (TreeView<File> tv : allTreeViews()) { if (tv == null || tv.getRoot() == null) continue; TreeItem<File> found = findAndExpand(tv.getRoot(), target); if (found != null) { tv.scrollTo(tv.getRow(found)); tv.getSelectionModel().select(found); tv.getStyleClass().add("highlighted-download"); javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(8)); delay.setOnFinished(e -> tv.getStyleClass().remove("highlighted-download")); delay.play(); return; } } });
    }
    private TreeItem<File> findAndExpand(TreeItem<File> node, File target) {
        if (node.getValue() != null && node.getValue().getAbsolutePath().equals(target.getAbsolutePath())) return node;
        for (TreeItem<File> child : node.getChildren()) { TreeItem<File> found = findAndExpand(child, target); if (found != null) { node.setExpanded(true); return found; } }
        return null;
    }
    public void showTaskBadge(String label) { showTaskBadge(label, true); }
    public void showTaskBadge(String label, boolean spin) { javafx.application.Platform.runLater(() -> { if (label != null && !label.isBlank()) taskBadgeLabel.setText(label); UiFactory.setVisible(taskBadgeSpinner, spin); taskBadgeBox.setVisible(true); taskBadgeBox.setManaged(true); taskBadgeHeader.setVisible(true); taskBadgeHeader.setManaged(true); }); }
    public void hideTaskBadge() { javafx.application.Platform.runLater(() -> { taskBadgeBox.setVisible(false); taskBadgeBox.setManaged(false); taskBadgeHeader.setVisible(false); taskBadgeHeader.setManaged(false); }); }
    public void setOnTaskBadgeClick(Runnable callback) { onTaskBadgeClick = callback; }

    private void buildUI() {
        addContextBtn = new Button(Icons.ADD); addContextBtn.getStyleClass().add("sidebar-section-action"); addContextBtn.setTooltip(new Tooltip("Add context")); addContextBtn.setOnAction(e -> showContextActions());
        codeToggleBtn = buildCodeToggleBtn();
        codeRefreshBtn = buildCodeRefreshBtn();
        taskBadgeHeader = buildTaskBadgeHeader(); treeView = buildTreeView(buildCodeSection()); contextTreeView = buildTreeView(buildContextSection()); worktreeSection = buildWorktreeSection(); addSectionViews();
    }
    private Button buildCodeToggleBtn() {
        Button btn = new Button(Icons.CHEVRON_DOWN); btn.getStyleClass().add("sidebar-section-action");
        btn.setTooltip(new Tooltip("Collapse code section"));
        btn.setOnAction(e -> toggleCodeSection());
        return btn;
    }
    private Button buildCodeRefreshBtn() {
        Button btn = new Button(Icons.REFRESH); btn.getStyleClass().add("sidebar-section-action");
        btn.setTooltip(new Tooltip("Refresh project files"));
        btn.setOnAction(e -> refresh());
        return btn;
    }
    private void toggleCodeSection() {
        boolean visible = !treeView.isVisible();
        treeView.setVisible(visible); treeView.setManaged(visible);
        codeToggleBtn.setText(visible ? Icons.CHEVRON_DOWN : Icons.BULLET);
        codeToggleBtn.setTooltip(new Tooltip(visible ? "Collapse code section" : "Expand code section"));
    }
    private void showContextActions() {
        TreeItem<File> selected = allTreeViews().stream().filter(tv -> tv != null).map(tv -> tv.getSelectionModel().getSelectedItem()).filter(i -> i != null).findFirst().orElse(null);
        File target = selected != null && selected.getValue() != null && selected.getValue().isDirectory() ? selected.getValue() : workDir;
        MenuItem local = new MenuItem("Add Context (link local folder)"); local.setOnAction(e -> invokeDirectoryCallback(onAddLocalContext, target));
        MenuItem download = new MenuItem("Download Context (Jira/Confluence/GitHub)"); download.setOnAction(e -> invokeDirectoryCallback(onDownloadContext, target));
        new ContextMenu(local, download).show(addContextBtn, javafx.geometry.Side.BOTTOM, 0, 0);
    }
    private void invokeDirectoryCallback(Consumer<File> callback, File target) { if (callback != null && target != null && target.isDirectory()) callback.accept(target); }
    private HBox buildTaskBadgeHeader() {
        taskBadgeSpinner = new ProgressIndicator(); taskBadgeSpinner.getStyleClass().add("download-spinner"); taskBadgeLabel = new Label("Exporting…"); taskBadgeLabel.getStyleClass().addAll("small", "muted"); taskBadgeBox = new HBox(6, taskBadgeSpinner, taskBadgeLabel); taskBadgeBox.getStyleClass().add("task-badge-box"); taskBadgeBox.setAlignment(Pos.CENTER_LEFT); taskBadgeBox.setVisible(false); taskBadgeBox.setManaged(false); taskBadgeBox.setOnMouseClicked(e -> { if (onTaskBadgeClick != null) onTaskBadgeClick.run(); });
        HBox header = new HBox(8, taskBadgeBox, UiFactory.hSpacer()); header.setAlignment(Pos.CENTER_LEFT); header.setPadding(new Insets(4, 10, 4, 10)); header.getStyleClass().add("panel-border-bottom"); header.setManaged(false); header.setVisible(false); return header;
    }
    private TreeView<File> buildTreeView(TreeItem<File> root) {
        TreeView<File> view = new TreeView<>(root); view.setShowRoot(false); view.getStyleClass().add("project-files-tree");
        view.setCellFactory(tree -> new ProjectFileTreeCell(() -> workDir, () -> accentColor, () -> contextFolderPaths, () -> sessionFolderPaths, this::refresh,
                this::fileTreeCallbacks));
        wireSectionTreeViewHandlers(view); return view;
    }
    private ProjectFileTreeCell.Callbacks fileTreeCallbacks() {
        return new ProjectFileTreeCell.Callbacks(onRemoveContext, onRemoveSession, onPutToTerminal,
                onViewMarkdown, onOpenSession, onRenameContextFolder, onRenameSessionFolder,
                onRunWorkflow);
    }
    private void addSectionViews() {
        HBox codeActions = new HBox(4, codeRefreshBtn, codeToggleBtn);
        VBox code = buildScrollableSection("CODE", codeActions, treeView); VBox context = buildScrollableSection("CONTEXT", addContextBtn, contextTreeView); VBox.setVgrow(code, Priority.ALWAYS); VBox.setVgrow(context, Priority.ALWAYS);
        code.getStyleClass().add("panel-border-top");
        getChildren().addAll(worktreeSection.getView(), taskBadgeHeader, code, context);
    }
    private VBox buildScrollableSection(String titleText, javafx.scene.Node action, TreeView<File> content) {
        HBox header = SidebarSectionHeader.create(titleText, action);
        content.setMinHeight(0); VBox.setVgrow(content, Priority.ALWAYS); VBox section = new VBox(0, header, content); section.setMinHeight(0); return section;
    }
    private WorktreeSidebarSection buildWorktreeSection() { return new WorktreeSidebarSection(() -> { if (onCreateWorktree != null) onCreateWorktree.run(); }, () -> { if (onRefreshWorktrees != null) onRefreshWorktrees.run(); }, w -> { if (onSelectWorktree != null) onSelectWorktree.accept(w); }, w -> { if (onRemoveWorktree != null) onRemoveWorktree.accept(w); }, () -> currentWorktreePath, () -> worktreeSessionLabels, (wt, idx) -> { if (onActivateTerminalSession != null) onActivateTerminalSession.accept(wt, idx); }); }
    private void wireSectionTreeViewHandlers(TreeView<File> tv) {
        tv.setOnMouseClicked(event -> { TreeItem<File> item = tv.getSelectionModel().getSelectedItem(); if (item != null && item.getValue() != null && onFileSelected != null) onFileSelected.accept(item.getValue()); });
        tv.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> { if (event.getDeltaY() == 0) return; for (var node : tv.lookupAll(".scroll-bar:vertical")) if (node instanceof ScrollBar sb) { double delta = -event.getDeltaY() * 0.5; sb.setValue(Math.max(sb.getMin(), Math.min(sb.getMax(), sb.getValue() + delta))); event.consume(); break; } });
    }
}
