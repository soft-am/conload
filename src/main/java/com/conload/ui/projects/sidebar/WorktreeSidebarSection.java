package com.conload.ui.projects.sidebar;

import com.conload.model.Worktree;
import com.conload.ui.Icons;
import com.conload.ui.components.UiFactory;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The WORKTREES sidebar section and its list-cell rendering. */
public final class WorktreeSidebarSection {
    private final Runnable onCreate;
    private final Runnable onRefresh;
    private final Consumer<Worktree> onSelect;
    private final Consumer<Worktree> onRemove;
    private final Supplier<String> currentPath;
    private final Supplier<Map<String, List<WorktreeSessionBadge.SessionInfo>>> sessionInfos;
    private final BiConsumer<String, Integer> onActivateTerminalSession;
    private final ListView<Worktree> list = new ListView<>();
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Label errorLabel = new Label();
    private final VBox view;

    public WorktreeSidebarSection(Runnable onCreate, Runnable onRefresh,
                                  Consumer<Worktree> onSelect, Consumer<Worktree> onRemove,
                                  Supplier<String> currentPath,
                                  Supplier<Map<String, List<WorktreeSessionBadge.SessionInfo>>> sessionInfos,
                                  BiConsumer<String, Integer> onActivateTerminalSession) {
        this.onCreate = onCreate;
        this.onRefresh = onRefresh;
        this.onSelect = onSelect;
        this.onRemove = onRemove;
        this.currentPath = currentPath;
        this.sessionInfos = sessionInfos;
        this.onActivateTerminalSession = onActivateTerminalSession;
        view = buildSection();
    }

    public VBox getView() {
        return view;
    }

    public void setVisible(boolean visible) {
        Platform.runLater(() -> {
            view.setManaged(visible);
            view.setVisible(visible);
        });
    }

    public boolean isVisible() {
        return view.isVisible();
    }

    public void setWorktrees(List<Worktree> worktrees) {
        List<Worktree> items = worktrees != null ? worktrees : List.of();
        List<Worktree> reordered = pinPrimaryFirst(items);
        Platform.runLater(() -> list.getItems().setAll(reordered));
    }

    /** Guarantees the primary (main) checkout is the first row, regardless of
     *  the order returned by {@code git worktree list}. No-op when the list is
     *  empty, has a single entry, has no primary marker, or the primary is
     *  already at position 0 (the normal case). */
    private static List<Worktree> pinPrimaryFirst(List<Worktree> items) {
        if (items.size() <= 1) return items;
        int primaryIdx = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isPrimary()) { primaryIdx = i; break; }
        }
        if (primaryIdx <= 0) return items;
        List<Worktree> reordered = new ArrayList<>(items.size());
        reordered.add(items.get(primaryIdx));
        for (int i = 0; i < items.size(); i++) {
            if (i != primaryIdx) reordered.add(items.get(i));
        }
        return reordered;
    }

    public void refreshRows() {
        if (list != null) list.refresh();
    }

    public void setBusy(boolean busy) {
        Platform.runLater(() -> {
            spinner.setVisible(busy);
            spinner.setManaged(busy);
            errorLabel.setVisible(false);
        });
    }

    public void setError(String message) {
        Platform.runLater(() -> {
            errorLabel.setText(message != null ? "⚠ " + message : "");
            errorLabel.setVisible(message != null && !message.isBlank());
        });
    }

    private VBox buildSection() {
        spinner.getStyleClass().add("download-spinner");
        spinner.setPrefSize(13, 13);
        spinner.setMaxSize(13, 13);
        spinner.setVisible(false);
        spinner.setManaged(false);

        Button headerKebab = new Button(Icons.ADD);
        headerKebab.getStyleClass().add("sidebar-section-action");
        headerKebab.setTooltip(new Tooltip("Worktree actions"));
        MenuItem createItem = new MenuItem(Icons.ADD_CIRCLE + " Create new worktree…");
        createItem.setOnAction(e -> { if (onCreate != null) onCreate.run(); });
        MenuItem refreshItem = new MenuItem(Icons.REFRESH + " Refresh worktrees");
        refreshItem.setOnAction(e -> { if (onRefresh != null) onRefresh.run(); });
        headerKebab.setOnAction(e -> new ContextMenu(createItem, refreshItem)
                .show(headerKebab, javafx.geometry.Side.BOTTOM, 0, 0));

        HBox trailing = new HBox(4, spinner, headerKebab);
        HBox headerRow = SidebarSectionHeader.create("WORKTREES", trailing);

        list.getStyleClass().add("worktree-list");
        list.setCellFactory(lv -> new WorktreeCell());
        list.setMinHeight(0);
        list.setPrefHeight(100);
        VBox.setVgrow(list, Priority.ALWAYS);

        errorLabel.getStyleClass().addAll("small", "error", "worktree-error");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);

        VBox box = new VBox(0, headerRow, list, errorLabel);
        box.getStyleClass().add("worktree-section");
        box.setManaged(false);
        box.setVisible(false);
        return box;
    }

    private String shortenPath(String abs) {
        if (abs == null || abs.isBlank()) return "";
        String home = System.getProperty("user.home");
        String s = abs;
        if (home != null && !home.isBlank() && s.startsWith(home)) s = "~" + s.substring(home.length());
        return s;
    }

    private class WorktreeCell extends ListCell<Worktree> {
        private MenuButton rowKebab;

        @Override
        protected void updateItem(Worktree w, boolean empty) {
            super.updateItem(w, empty);
            getStyleClass().removeAll("worktree-row", "worktree-current", "worktree-primary");
            setContextMenu(null);
            setOnMouseClicked(null);
            rowKebab = null;
            if (empty || w == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            boolean isCurrent = w.getPath() != null && w.getPath().equals(currentPath.get());
            setGraphic(buildWorktreeCard(w, isCurrent));
            setText(null);
            getStyleClass().add(isCurrent ? "worktree-current" : "worktree-row");
            setOnMouseClicked(ev -> {
                if (ev.getTarget() != rowKebab && onSelect != null) onSelect.accept(w);
            });
            setContextMenu(buildRowMenu(w));
        }

        private VBox buildWorktreeCard(Worktree w, boolean isCurrent) {
            Label branch = new Label(Icons.BRANCH_ALT);
            branch.getStyleClass().add("worktree-branch-glyph");
            Label name = new Label(w.displayName());
            name.getStyleClass().add("worktree-name");
            if (isCurrent) name.getStyleClass().add("worktree-name-current");
            HBox left = new HBox(6, branch, name);
            if (isCurrent) {
                Label cur = new Label(Icons.CHECK);
                cur.getStyleClass().add("worktree-current-label");
                left.getChildren().add(cur);
            }
            if (w.isPrimary()) {
                Label badge = new Label("Original");
                badge.getStyleClass().add("worktree-badge");
                left.getChildren().add(badge);
            }
            if (w.isLocked()) {
                Label lock = new Label(Icons.WARNING);
                lock.getStyleClass().add("worktree-lock");
                lock.setTooltip(new Tooltip("worktree is locked"));
                left.getChildren().add(lock);
            }

            rowKebab = new MenuButton(Icons.KEBAB);
            rowKebab.getStyleClass().addAll("icon-button", "icon", "small", "worktree-kebab");
            rowKebab.setPopupSide(javafx.geometry.Side.RIGHT);
            rowKebab.setMinHeight(0);
            rowKebab.setPrefHeight(16);
            rowKebab.setMaxHeight(16);
            rowKebab.getItems().addAll(buildKebabItems(w));
            Region rowSpacer = UiFactory.hSpacer();
            HBox.setHgrow(rowSpacer, Priority.ALWAYS);
            HBox topRow = new HBox(6, left, rowSpacer, rowKebab);
            topRow.setAlignment(Pos.CENTER_LEFT);
            topRow.getStyleClass().add("worktree-top-row");
            topRow.setMinHeight(0);
            topRow.setPrefHeight(16);
            topRow.setMaxHeight(16);

            Label path = new Label(shortenPath(w.getPath()));
            path.getStyleClass().add("worktree-path");
            path.setMinHeight(0);
            path.setPrefHeight(14);
            path.setMaxHeight(14);
            VBox card = new VBox(0, topRow, path);
            card.getStyleClass().add("worktree-card");
            return card;
        }

        private ContextMenu buildRowMenu(Worktree w) {
            ContextMenu menu = new ContextMenu();
            menu.getItems().addAll(worktreeMenuItems(w));
            return menu;
        }

        private List<MenuItem> buildKebabItems(Worktree w) {
            List<MenuItem> items = new ArrayList<>();
            List<WorktreeSessionBadge.SessionInfo> infos = sessionInfos.get()
                    .getOrDefault(w.getPath(), List.of());
            if (!infos.isEmpty()) {
                Menu sessionsMenu = new Menu(Icons.SESSION + " Sessions (" + infos.size() + ")");
                for (WorktreeSessionBadge.SessionInfo s : infos) {
                    int idx = s.subIndex();
                    String label;
                    if (s.title() != null && !s.title().isBlank()) {
                        label = s.title();
                    } else if (s.sessionId() != null && !s.sessionId().isBlank()) {
                        label = s.sessionType() + " #" + s.sessionId();
                    } else {
                        label = "Terminal " + (idx + 1);
                    }
                    MenuItem mi = new MenuItem("[" + (idx + 1) + "] " + label);
                    mi.getStyleClass().add("worktree-session-menu-item");
                    mi.setOnAction(e -> onActivateTerminalSession.accept(w.getPath(), idx));
                    sessionsMenu.getItems().add(mi);
                }
                items.add(sessionsMenu);
                items.add(new SeparatorMenuItem());
            }
            items.addAll(worktreeMenuItems(w));
            return items;
        }

        private List<MenuItem> worktreeMenuItems(Worktree w) {
            MenuItem refresh = new MenuItem(Icons.REFRESH + " Refresh worktrees");
            refresh.setOnAction(e -> { if (onRefresh != null) onRefresh.run(); });
            List<MenuItem> items = new ArrayList<>(List.of(refresh));
            if (!w.isPrimary()) {
                items.add(new SeparatorMenuItem());
                MenuItem remove = new MenuItem(Icons.CLOSE + " Remove worktree…");
                remove.setOnAction(e -> { if (onRemove != null) onRemove.accept(w); });
                items.add(remove);
            }
            return items;
        }
    }
}
