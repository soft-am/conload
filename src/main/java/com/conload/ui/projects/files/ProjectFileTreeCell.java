package com.conload.ui.projects.files;

import com.conload.ui.DialogStyler;
import com.conload.ui.Icons;
import com.conload.ui.ProjectColors;
import com.conload.ui.components.SvgIcon;
import com.conload.ui.components.UiFactory;
import com.conload.ui.projects.ProjectTreeItems;
import com.conload.ui.projects.SessionFolderFiles;
import com.conload.util.BackgroundTasks;
import com.conload.util.FileTreeOps;
import com.conload.util.PlatformFileOpener;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Renders project-file rows and owns their file/context actions. */
public final class ProjectFileTreeCell extends TreeCell<File> {
    public record Callbacks(Consumer<File> removeContext, Consumer<File> removeSession,
                            Consumer<String> putToTerminal, Consumer<File> viewMarkdown,
                            Consumer<File> openSession, BiConsumer<File, String> renameContext,
                            BiConsumer<File, String> renameSession,
                            Consumer<File> runWorkflow) {}

    private final Supplier<File> workDir;
    private final Supplier<String> accentColor;
    private final Supplier<Set<String>> contextPaths;
    private final Supplier<Set<String>> sessionPaths;
    private final Runnable refresh;
    private final Supplier<Callbacks> callbacks;
    private final MenuItem removeContextItem = new MenuItem(Icons.CLOSE + " Remove Context");
    private final MenuItem removeSessionItem = new MenuItem(Icons.CLOSE + " Remove Session");
    private final MenuItem copyPathItem = new MenuItem("Copy Relative Path");
    private final MenuItem putToTerminalItem = new MenuItem("Put into Terminal");
    private final MenuItem openInDefaultItem = new MenuItem("Open");
    private final MenuItem openWithChooserItem = new MenuItem("Open in…");
    private final MenuItem copyToItem = new MenuItem(Icons.FOLDER + " Copy to…");
    private final MenuItem viewMarkdownItem = new MenuItem(Icons.DOCUMENT + " View Markdown");

    public ProjectFileTreeCell(Supplier<File> workDir, Supplier<String> accentColor,
                               Supplier<Set<String>> contextPaths, Supplier<Set<String>> sessionPaths,
                               Runnable refresh, Supplier<Callbacks> callbacks) {
        this.workDir = workDir;
        this.accentColor = accentColor;
        this.contextPaths = contextPaths;
        this.sessionPaths = sessionPaths;
        this.refresh = refresh;
        this.callbacks = callbacks;
        removeContextItem.setOnAction(e -> removeRegisteredFolder(callbacks().removeContext()));
        removeSessionItem.setOnAction(e -> removeRegisteredFolder(callbacks().removeSession()));
        copyPathItem.setOnAction(e -> copyRelativePath());
        putToTerminalItem.setOnAction(e -> putSelectedPathToTerminal());
        openInDefaultItem.setOnAction(e -> openWith(null));
        openWithChooserItem.setOnAction(e -> showOpenWithPopup());
        copyToItem.setOnAction(e -> copyToBrowse());
        viewMarkdownItem.setOnAction(e -> viewSelectedMarkdown());
    }

    private void removeRegisteredFolder(Consumer<File> callback) {
        File target = getItem();
        if (target != null && target.isDirectory() && callback != null) callback.accept(target);
    }

    /** Check if the folder contains {@code workflow-info.json}. */
    private boolean hasWorkflowMetadata(File folder) {
        return folder != null && folder.isDirectory()
                && new File(folder, "workflow-info.json").exists();
    }

    private void copyRelativePath() {
        File target = getItem();
        if (target != null) javafx.scene.input.Clipboard.getSystemClipboard().setContent(
                java.util.Map.of(javafx.scene.input.DataFormat.PLAIN_TEXT, relativize(target)));
    }

    private void putSelectedPathToTerminal() {
        File target = getItem();
        String relative = target == null ? "" : relativize(target);
        if (!relative.isBlank() && callbacks().putToTerminal() != null) callbacks().putToTerminal().accept(relative);
    }

    private void viewSelectedMarkdown() {
        File target = getItem();
        if (target != null && target.isFile() && target.getName().endsWith(".md")
                && callbacks().viewMarkdown() != null) callbacks().viewMarkdown().accept(target);
    }

    private String relativize(File file) {
        try { return workDir.get().toPath().relativize(file.toPath()).toString(); }
        catch (IllegalArgumentException | NullPointerException e) { return file.getAbsolutePath(); }
    }

    private void openWith(String appName) {
        File target = getItem();
        if (target == null) return;
        try {
            PlatformFileOpener.openWithApp(target, appName, workDir.get());
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to open:\n" + ex.getMessage());
            alert.setHeaderText("Open in…"); DialogStyler.style(alert); alert.showAndWait();
        }
    }

    private void showOpenWithPopup() {
        File target = getItem();
        if (target == null) return;
        ContextMenu popup = new ContextMenu(); popup.setAutoHide(true); popup.setAutoFix(true);
        List<String> apps = queryInstalledApps(target);
        for (String app : apps) { MenuItem item = new MenuItem(app); item.setOnAction(e -> openWith(app)); popup.getItems().add(item); }
        if (!apps.isEmpty()) popup.getItems().add(new SeparatorMenuItem());
        MenuItem other = new MenuItem("Other…");
        other.setOnAction(e -> openWithSystemChooser(target)); popup.getItems().add(other);
        popup.show(this, getScene().getWindow().getX() + getLayoutX(),
                getScene().getWindow().getY() + getLayoutY() + getHeight());
    }

    private List<String> queryInstalledApps(File target) {
        String[] defaults = {"TextEdit", "Xcode", "VS Code", "Sublime Text", "IntelliJ IDEA", "WebStorm", "Cursor", "Zed"};
        List<String> apps = new ArrayList<>(Arrays.asList(defaults));
        try {
            Path dir = Path.of("/Applications");
            List<String> verified = new ArrayList<>();
            for (String app : defaults) {
                String prefix = app.contains(" ") ? app.substring(0, app.indexOf(' ')) : app;
                boolean found = java.util.stream.Stream.of(dir.resolve(app + ".app"), dir.resolve(prefix + ".app"),
                        dir.resolve("Visual Studio Code.app")).anyMatch(p -> Files.exists(p) || existsIgnoreCase(dir, app));
                if (found) verified.add(app);
            }
            if (!verified.isEmpty()) apps = verified;
        } catch (Exception ignored) { }
        return apps;
    }

    private boolean existsIgnoreCase(Path dir, String hint) {
        if (!Files.isDirectory(dir)) return false;
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(path -> path.getFileName().toString().toLowerCase().endsWith(".app")
                    && path.getFileName().toString().toLowerCase().contains(hint.toLowerCase().split(" ")[0]));
        } catch (Exception e) { return false; }
    }

    private void openWithSystemChooser(File target) {
        try {
            PlatformFileOpener.openWithChooser(target, workDir.get());
        } catch (Exception ignored) { openWith(null); }
    }

    private void copyToBrowse() {
        File target = getItem();
        if (target == null || !target.exists()) return;
        DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle("Select destination folder");
        File destination = chooser.showDialog(getScene().getWindow());
        if (destination == null) return;
        File copyTarget = new File(destination, target.getName());
        if (copyTarget.exists()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "A folder named '" + target.getName() + "' already exists here. Overwrite?", ButtonType.YES, ButtonType.NO);
            DialogStyler.style(confirm);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        }
        BackgroundTasks.runIOTask("copy-to-thread", () -> {
            try {
                if (target.isDirectory()) FileTreeOps.copyRecursively(target.toPath(), copyTarget.toPath());
                else Files.copy(target.toPath(), copyTarget.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                javafx.application.Platform.runLater(() -> {
                    Alert ok = new Alert(Alert.AlertType.INFORMATION, "Copied to:\n" + copyTarget.getAbsolutePath());
                    ok.setHeaderText("Copy complete"); DialogStyler.style(ok); ok.showAndWait();
                });
            } catch (Exception ex) { javafx.application.Platform.runLater(() -> {
                Alert error = new Alert(Alert.AlertType.ERROR, "Copy failed:\n" + ex.getMessage());
                DialogStyler.style(error); error.showAndWait();
            }); }
        });
    }

    private ContextMenu buildFolderMenu() {
        ContextMenu menu = new ContextMenu(); configurePopup(menu);
        List<MenuItem> items = new ArrayList<>(); addOpenItems(items, false);
        File current = getItem();
        boolean isContext = current != null && current.isDirectory() && contextPaths.get().contains(current.getAbsolutePath());
        boolean isSession = current != null && current.isDirectory() && sessionPaths.get().contains(current.getAbsolutePath());
        if (isContext || isSession) {
            items.add(new SeparatorMenuItem());
            MenuItem rename = new MenuItem(Icons.EDIT + " Rename…");
            rename.setOnAction(e -> showRenameDialog(current, isContext)); items.add(rename);
            if (isContext && hasWorkflowMetadata(current)) {
                MenuItem runWorkflow = new MenuItem(Icons.SPARKLE + " Run Workflow");
                runWorkflow.setOnAction(e -> {
                    if (callbacks().runWorkflow() != null) callbacks().runWorkflow().accept(current);
                });
                items.add(runWorkflow);
            }
            if (isContext) items.add(removeContextItem);
            if (isSession) { if (!isContext) items.add(new SeparatorMenuItem()); items.add(removeSessionItem); }
        }
        if (isSession) {
            items.add(new SeparatorMenuItem());
            MenuItem view = new MenuItem(Icons.DOCUMENT + " View Markdown");
            view.setOnAction(e -> { File markdown = SessionFolderFiles.resolveMarkdown(current);
                if (markdown != null && callbacks().viewMarkdown() != null) callbacks().viewMarkdown().accept(markdown); });
            items.add(view);
            MenuItem open = new MenuItem("Open Session Details");
            open.setOnAction(e -> { if (callbacks().openSession() != null) callbacks().openSession().accept(current); }); items.add(open);
        }
        addFileActions(items); menu.getItems().setAll(items); return menu;
    }

    private void configurePopup(ContextMenu menu) { menu.setAutoFix(true); menu.setAutoHide(true); }
    private void addOpenItems(List<MenuItem> items, boolean markdown) {
        if (markdown) { items.add(viewMarkdownItem); items.add(new SeparatorMenuItem()); }
        items.add(openInDefaultItem);
        if (PlatformFileOpener.isMac()) items.add(openWithChooserItem);
    }
    private void addFileActions(List<MenuItem> items) {
        items.add(new SeparatorMenuItem()); items.add(copyPathItem); items.add(putToTerminalItem);
        items.add(new SeparatorMenuItem()); items.add(copyToItem);
    }
    private void showRenameDialog(File folder, boolean context) {
        TextInputDialog dialog = new TextInputDialog(folder.getName());
        dialog.setTitle("Rename " + (context ? "Context" : "Session")); dialog.setHeaderText("Enter a new folder name:"); DialogStyler.style(dialog);
        dialog.showAndWait().ifPresent(value -> {
            String name = value.trim(); if (name.isBlank() || name.equals(folder.getName())) return;
            BiConsumer<File, String> callback = context ? callbacks().renameContext() : callbacks().renameSession();
            if (callback != null) callback.accept(folder, name);
        });
    }

    private Callbacks callbacks() {
        return callbacks.get();
    }

    private ContextMenu buildFileMenu() {
        ContextMenu menu = new ContextMenu(); configurePopup(menu); List<MenuItem> items = new ArrayList<>();
        File current = getItem(); addOpenItems(items, current != null && current.isFile() && current.getName().toLowerCase().endsWith(".md"));
        addFileActions(items); menu.getItems().setAll(items); return menu;
    }

    @Override protected void updateItem(File item, boolean empty) {
        super.updateItem(item, empty);
        getStyleClass().removeAll("bg-transparent", "bold", "secondary", "context-folder", "session-folder", "sidebar-section-cell");
        if (empty || item == null) {
            TreeItem<File> treeItem = getTreeItem();
            if (!empty && treeItem instanceof ProjectTreeItems.SourceGroupTreeItem group) {
                setGraphic(buildSourceGroupRow(group)); setText(null); getStyleClass().add("bg-transparent"); setOnContextMenuRequested(null);
            } else { setText(null); setGraphic(null); getStyleClass().add("bg-transparent"); setContextMenu(null); setOnContextMenuRequested(null); }
            return;
        }
        String displayName = item.getName().isBlank() ? item.getAbsolutePath() : item.getName();
        boolean context = item.isDirectory() && contextPaths.get().contains(item.getAbsolutePath());
        boolean session = item.isDirectory() && sessionPaths.get().contains(item.getAbsolutePath());
        if (context && getTreeItem() instanceof ProjectTreeItems.ContextFolderTreeItem) {
            getStyleClass().addAll("context-folder", "bold"); setGraphic(buildContextRow(item)); setText(null);
        } else if (session && getTreeItem() instanceof ProjectTreeItems.SessionLeafItem) {
            getStyleClass().add("session-folder"); setGraphic(buildSessionCardRow(item)); setText(null);
        } else if (item.isDirectory() && (contextPaths.get().stream().anyMatch(p -> p.startsWith(item.getAbsolutePath() + File.separator))
                || sessionPaths.get().stream().anyMatch(p -> p.startsWith(item.getAbsolutePath() + File.separator)))) {
            setText(displayName); setGraphic(null);
        } else { setText(displayName); setGraphic(null); getStyleClass().add(item.isDirectory() ? "bold" : "secondary"); }
        setOnContextMenuRequested(e -> { ContextMenu menu = item.isDirectory() ? buildFolderMenu() : buildFileMenu(); menu.show(this, e.getScreenX(), e.getScreenY()); e.consume(); });
    }

    private HBox buildProjectRootRow(ProjectTreeItems.ProjectRootTreeItem root) {
        Label robot = new Label(); robot.setGraphic(new SvgIcon("/images/conload-robot-c-white.svg", 18, "-app-bg", color()));
        Label name = new Label(root.getProjectName()); name.getStyleClass().add("sidebar-project-root-name");
        Button refreshButton = new Button(Icons.REFRESH); refreshButton.getStyleClass().addAll("icon-button", "icon", "file-tree-refresh-btn");
        refreshButton.setTooltip(new Tooltip("Refresh file tree from filesystem")); refreshButton.setOnAction(e -> refresh.run());
        HBox row = new HBox(6, robot, name, refreshButton); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("sidebar-project-root-row"); return row;
    }
    private HBox buildSourceGroupRow(ProjectTreeItems.SourceGroupTreeItem group) {
        HBox row = new HBox(); row.getStyleClass().add("sidebar-source-row"); row.setMinWidth(0);
        if (group.getIconResource() != null) row.getChildren().add(buildTintedSvg(group.getIconResource(), 14));
        Label name = new Label(group.getLabel()); name.getStyleClass().add("sidebar-source-name"); name.setMinWidth(0); row.getChildren().add(name);
        Region spacer = UiFactory.hSpacer(); HBox.setHgrow(spacer, Priority.ALWAYS); row.getChildren().add(spacer);
        Label count = new Label(String.valueOf(group.getCount())); count.getStyleClass().add("sidebar-source-badge"); row.getChildren().add(count); return row;
    }
    private HBox buildContextRow(File folder) {
        HBox row = new HBox(6); row.getStyleClass().add("sidebar-context-row"); row.setMinWidth(0);
        for (String icon : detectContextSourceIcons(folder)) row.getChildren().add(buildTintedSvg(icon, 14));
        Label name = new Label(folder.getName()); name.getStyleClass().add("sidebar-context-name"); name.setMinWidth(0); row.getChildren().add(name);
        Region spacer = UiFactory.hSpacer(); HBox.setHgrow(spacer, Priority.ALWAYS); row.getChildren().add(spacer);
        int total = countContextChildren(folder); if (total > 0) { Label count = new Label(String.valueOf(total)); count.getStyleClass().add("sidebar-source-badge"); row.getChildren().add(count); }
        return row;
    }
    private HBox buildSessionCardRow(File folder) {
        SessionFolderFiles.Info info = SessionFolderFiles.Info.parse(folder);
        String title = info.title() != null && !info.title().isBlank() ? info.title() : SessionFolderFiles.friendlySessionName(folder.getName());
        HBox row = new HBox(8); row.getStyleClass().add("sidebar-session-card"); row.setMinWidth(0); row.getChildren().add(buildTintedSvg("/images/session-icon.svg", 14));
        VBox text = new VBox(1); text.setMinWidth(0); Label titleLabel = new Label(title); titleLabel.getStyleClass().add("sidebar-session-title"); titleLabel.setMinWidth(0); text.getChildren().add(titleLabel);
        HBox meta = new HBox(6); meta.setAlignment(Pos.CENTER_LEFT); meta.setMinWidth(0);
        if (info.summary() != null && !info.summary().isBlank()) { String snippet = info.summary().length() > 80 ? info.summary().substring(0, 77) + "…" : info.summary(); Label sub = new Label(snippet); sub.getStyleClass().add("sidebar-session-subtitle"); sub.setMinWidth(0); meta.getChildren().add(sub); }
        if (info.agent() != null && !info.agent().isBlank()) { Label pill = new Label(info.agent()); pill.getStyleClass().add("sidebar-session-agent-pill"); pill.setMinWidth(0); meta.getChildren().add(pill); }
        text.getChildren().add(meta); row.getChildren().add(text); Region spacer = UiFactory.hSpacer(); HBox.setHgrow(spacer, Priority.ALWAYS); row.getChildren().add(spacer);
        if (info.date() != null && !info.date().isBlank()) { Label date = new Label(info.date()); date.getStyleClass().add("sidebar-session-date"); date.setMinWidth(0); row.getChildren().add(date); }
        row.setPickOnBounds(true); return row;
    }
    private String color() { String color = accentColor.get(); return color == null || color.isBlank() ? ProjectColors.DEFAULT : color; }
    private SvgIcon buildTintedSvg(String resource, int size) { return new SvgIcon(resource, size, "-app-bg", color()); }
    private List<String> detectContextSourceIcons(File folder) {
        List<String> result = new ArrayList<>(); File[] files = folder.listFiles(); if (files == null) return result;
        boolean workflow = false, confluence = false, jira = false, github = false;
        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName().toLowerCase();
                if (name.equals("jira")) jira = true;
                else if (name.equals("github")) github = true;
                else if (name.equals("workflow-result")) workflow = true;
            } else if (file.isFile() && file.getName().toLowerCase().endsWith(".md")) {
                confluence = true;
            }
        }
        // Workflow folders show only the workflow icon — suppress source icons.
        if (workflow) { result.add("/images/cross-context.svg"); return result; }
        if (confluence) result.add("/images/confluence-logo.svg");
        if (jira) result.add("/images/jira-logo.svg");
        if (github) result.add("/images/github-logo.svg");
        return result;
    }
    private int countContextChildren(File folder) {
        File[] files = folder.listFiles(); if (files == null) return 0; int total = 0;
        for (File file : files) { if (file.isDirectory() && (file.getName().equalsIgnoreCase("media") || file.getName().equalsIgnoreCase("jira") || file.getName().equalsIgnoreCase("github") || file.getName().equalsIgnoreCase("confluence") || file.getName().equalsIgnoreCase("workflow-result"))) { File[] children = file.listFiles(); if (children != null) total += children.length; } else if (file.isFile() && file.getName().toLowerCase().endsWith(".md")) total++; }
        return total;
    }
}
