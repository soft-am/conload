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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Left side-pane showing the local project files and registered context. */
public class ProjectFilesPane extends VBox {
    private File workDir;
    private final Consumer<File> onFileClick;
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
    private Button contextToggleBtn;
    private Button codeRefreshBtn;
    private HBox taskBadgeHeader;
    private VBox taskBadgeRows;
    private String projectName = "";
    private RobotIndicator.Character projectCharacter;
    private String accentColor = "";
    private Set<String> contextFolderPaths = new HashSet<>();
    private Set<String> sessionFolderPaths = new HashSet<>();
    private VBox codeSectionView;
    private VBox contextSectionView;
    private final Map<String, TaskBadgeRow> taskBadges = new LinkedHashMap<>();

    private final class TaskBadgeRow {
        private final HBox box = new HBox(6);
        private final ProgressIndicator spinner = new ProgressIndicator();
        private final Label label = new Label();
        private final AtomicBoolean countInFlight = new AtomicBoolean();
        private javafx.animation.Timeline countTimeline;
        private File outputDirectory;
        private long initialCount = -1;

        private TaskBadgeRow() {
            spinner.getStyleClass().add("download-spinner");
            label.getStyleClass().addAll("small", "muted");
            box.getChildren().addAll(spinner, label);
            box.getStyleClass().add("task-badge-box");
            box.setAlignment(Pos.CENTER_LEFT);
            box.setOnMouseClicked(e -> { if (onTaskBadgeClick != null) onTaskBadgeClick.run(); });
        }

        private void stopCount() {
            if (countTimeline != null) { countTimeline.stop(); countTimeline = null; }
        }
    }

    public ProjectFilesPane(File workDir, Consumer<File> onFileClick) {
        this.workDir = workDir;
        this.onFileClick = onFileClick;
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
    public void showTaskBadge(String label) { showTaskBadge("main", label, true, null); }
    public void showTaskBadge(String label, boolean spin) { showTaskBadge("main", label, spin, null); }
    public void showTaskBadge(String label, boolean spin, File outputDirectory) {
        showTaskBadge("main", label, spin, outputDirectory);
    }
    public void showTaskBadge(String key, String label) {
        showTaskBadge(key, label, true, null);
    }

    public void showTaskBadge(String key, String label, boolean spin, File outputDirectory) {
        javafx.application.Platform.runLater(() -> {
            TaskBadgeRow row = taskBadges.computeIfAbsent(key, ignored -> {
                TaskBadgeRow created = new TaskBadgeRow();
                taskBadgeRows.getChildren().add(created.box);
                return created;
            });
            row.label.setText(label == null ? "" : label);
            UiFactory.setVisible(row.spinner, spin);
            row.outputDirectory = outputDirectory;
            row.stopCount();
            if (spin && outputDirectory != null) startTaskBadgeCount(row);
            taskBadgeHeader.setVisible(true);
            taskBadgeHeader.setManaged(true);
        });
    }

    public void hideTaskBadge() { hideTaskBadge("main"); }

    public void hideTaskBadge(String key) {
        javafx.application.Platform.runLater(() -> {
            TaskBadgeRow row = taskBadges.remove(key);
            if (row == null) return;
            row.stopCount();
            taskBadgeRows.getChildren().remove(row.box);
            boolean visible = !taskBadges.isEmpty();
            taskBadgeHeader.setVisible(visible);
            taskBadgeHeader.setManaged(visible);
        });
    }

    private void startTaskBadgeCount(TaskBadgeRow row) {
        Thread.startVirtualThread(() -> {
            if (row.initialCount < 0) row.initialCount = countFiles(row.outputDirectory);
            javafx.application.Platform.runLater(() -> {
                if (!taskBadges.containsValue(row)) return;
                row.countTimeline = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                        javafx.util.Duration.millis(500), event -> updateTaskBadgeCount(row)));
                row.countTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
                row.countTimeline.play();
            });
        });
    }

    private void updateTaskBadgeCount(TaskBadgeRow row) {
        if (!row.countInFlight.compareAndSet(false, true)) return;
        Thread.startVirtualThread(() -> {
            long addedFiles = Math.max(0, countFiles(row.outputDirectory) - row.initialCount);
            javafx.application.Platform.runLater(() -> {
                if (taskBadges.containsValue(row)) {
                    String base = row.label.getText().replaceFirst(" \\(\\d+ files\\)$", "");
                    row.label.setText(base + " (" + addedFiles + " files)");
                }
                row.countInFlight.set(false);
            });
        });
    }

    private long countFiles(File directory) {
        if (directory == null || !directory.isDirectory()) return 0;
        try (var paths = java.nio.file.Files.walk(directory.toPath())) {
            return paths.filter(java.nio.file.Files::isRegularFile).count();
        } catch (java.io.IOException ignored) {
            return 0;
        }
    }

    public void setOnTaskBadgeClick(Runnable callback) { onTaskBadgeClick = callback; }

    private void buildUI() {
        addContextBtn = new Button(Icons.ADD); addContextBtn.getStyleClass().add("sidebar-section-action"); addContextBtn.setTooltip(new Tooltip("Add context")); addContextBtn.setOnAction(e -> showContextActions());
        codeToggleBtn = buildCodeToggleBtn();
        contextToggleBtn = buildContextToggleBtn();
        codeRefreshBtn = buildCodeRefreshBtn();
        taskBadgeHeader = buildTaskBadgeHeader(); treeView = buildTreeView(buildCodeSection()); contextTreeView = buildTreeView(buildContextSection());
        //contextTreeView.setMaxHeight(100);
        worktreeSection = buildWorktreeSection(); addSectionViews();
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
    private Button buildContextToggleBtn() {
        Button btn = new Button(Icons.CHEVRON_DOWN); btn.getStyleClass().add("sidebar-section-action");
        btn.setTooltip(new Tooltip("Collapse context section"));
        btn.setOnAction(e -> toggleContextSection());
        return btn;
    }
    private void toggleContextSection() {
        boolean visible = !contextTreeView.isVisible();
        contextTreeView.setVisible(visible); contextTreeView.setManaged(visible);
        contextToggleBtn.setText(visible ? Icons.CHEVRON_DOWN : Icons.BULLET);
        contextToggleBtn.setTooltip(new Tooltip(visible ? "Collapse context section" : "Expand context section"));
    }
    private void showContextActions() {
        TreeItem<File> selected = allTreeViews().stream().filter(tv -> tv != null).map(tv -> tv.getSelectionModel().getSelectedItem()).filter(i -> i != null).findFirst().orElse(null);
        File target = selected != null && selected.getValue() != null && selected.getValue().isDirectory() ? selected.getValue() : workDir;
        MenuItem local = new MenuItem("Add Context (link local folder)"); local.setOnAction(e -> invokeDirectoryCallback(onAddLocalContext, target));
        MenuItem download = new MenuItem("Add Context (Jira/Confluence/GitHub)"); download.setOnAction(e -> invokeDirectoryCallback(onDownloadContext, target));
        new ContextMenu(local, download).show(addContextBtn, javafx.geometry.Side.BOTTOM, 0, 0);
    }
    private void invokeDirectoryCallback(Consumer<File> callback, File target) { if (callback != null && target != null && target.isDirectory()) callback.accept(target); }
    private HBox buildTaskBadgeHeader() {
        taskBadgeRows = new VBox(2);
        HBox header = new HBox(8, taskBadgeRows, UiFactory.hSpacer());
        header.setAlignment(Pos.CENTER_LEFT); header.setPadding(new Insets(4, 10, 4, 10));
        header.getStyleClass().add("panel-border-bottom"); header.setManaged(false); header.setVisible(false);
        return header;
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
        HBox contextActions = new HBox(4, addContextBtn, contextToggleBtn);
        codeSectionView = buildScrollableSection("CODE", codeActions, treeView);
        contextSectionView = buildScrollableSection("CONTEXT", contextActions, contextTreeView);
        VBox.setVgrow(codeSectionView, Priority.ALWAYS);
        VBox.setVgrow(contextSectionView, Priority.ALWAYS);
        codeSectionView.getStyleClass().add("panel-border-top");
        getChildren().addAll(worktreeSection.getView(), codeSectionView, taskBadgeHeader, contextSectionView);

        heightProperty().addListener((obs, old, newVal) -> updateSectionHeights());
        if (worktreeSection != null) {
            worktreeSection.getView().visibleProperty().addListener((obs, old, newVal) -> updateSectionHeights());
            worktreeSection.getView().managedProperty().addListener((obs, old, newVal) -> updateSectionHeights());
        }
    }

    private void updateSectionHeights() {
        double totalHeight = getHeight();
        if (totalHeight <= 0) return;

        VBox wtView = worktreeSection != null ? worktreeSection.getView() : null;
        boolean wtVisible = wtView != null && wtView.isVisible() && wtView.isManaged();
        if (wtVisible) {
            wtView.setPrefHeight(totalHeight * 0.20);
            if (codeSectionView != null) codeSectionView.setPrefHeight(totalHeight * 0.40);
            if (contextSectionView != null) contextSectionView.setPrefHeight(totalHeight * 0.40);
        } else {
            if (wtView != null) wtView.setPrefHeight(0);
            if (codeSectionView != null) codeSectionView.setPrefHeight(totalHeight * 0.50);
            if (contextSectionView != null) contextSectionView.setPrefHeight(totalHeight * 0.50);
        }
    }
    private VBox buildScrollableSection(String titleText, javafx.scene.Node action, TreeView<File> content) {
        HBox header = SidebarSectionHeader.create(titleText, action);
        content.setMinHeight(0);
        content.setPrefHeight(100);
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox section = new VBox(0, header, content);
        section.setMinHeight(0);
        section.setPrefHeight(100);
        return section;
    }
    private WorktreeSidebarSection buildWorktreeSection() { return new WorktreeSidebarSection(() -> { if (onCreateWorktree != null) onCreateWorktree.run(); }, () -> { if (onRefreshWorktrees != null) onRefreshWorktrees.run(); }, w -> { if (onSelectWorktree != null) onSelectWorktree.accept(w); }, w -> { if (onRemoveWorktree != null) onRemoveWorktree.accept(w); }, () -> currentWorktreePath, () -> worktreeSessionLabels, (wt, idx) -> { if (onActivateTerminalSession != null) onActivateTerminalSession.accept(wt, idx); }); }
    private void wireSectionTreeViewHandlers(TreeView<File> tv) {
        tv.setOnMouseClicked(event -> {
            if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            if (event.getClickCount() > 1) return;
            if (event.getPickResult() == null || event.getPickResult().getIntersectedNode() == null) return;
            javafx.scene.Node hit = event.getPickResult().getIntersectedNode();
            if (isDisclosureClick(hit, tv)) return;
            ProjectFileTreeCell cell = rowCellFor(hit, tv);
            if (cell == null || cell.isEmpty() || cell.getItem() == null) return;
            File file = cell.getItem();
            if (pickerModeActive) {
                if (onFileClick != null) onFileClick.accept(file);
            } else if (file.isFile() && file.getName().toLowerCase().endsWith(".md")) {
                if (onViewMarkdown != null) onViewMarkdown.accept(file);
            } else if (onFileClick != null) onFileClick.accept(file);
        });
        tv.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> { if (event.getDeltaY() == 0) return; for (var node : tv.lookupAll(".scroll-bar:vertical")) if (node instanceof ScrollBar sb) { double delta = -event.getDeltaY() * 0.5; sb.setValue(Math.max(sb.getMin(), Math.min(sb.getMax(), sb.getValue() + delta))); event.consume(); break; } });
    }

    private boolean isDisclosureClick(javafx.scene.Node node, TreeView<File> tv) {
        while (node != null && node != tv) {
            if (node.getStyleClass().contains("tree-disclosure-node")) return true;
            node = node.getParent();
        }
        return false;
    }

    private ProjectFileTreeCell rowCellFor(javafx.scene.Node node, TreeView<File> tv) {
        while (node != null && node != tv) {
            if (node instanceof ProjectFileTreeCell cell) return cell;
            node = node.getParent();
        }
        return null;
    }
}
