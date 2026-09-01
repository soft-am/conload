package com.conload.ui.projects.workspace;

import com.conload.ui.Icons;
import com.conload.ui.components.UiFactory;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.function.Consumer;
import java.util.function.BiConsumer;

/** Compact subtab strip shown above the terminal area. Renders one pill per
 *  terminal in the active {@link TerminalGroup} plus a + button to create new.
 *  Hidden (unmanaged) when the group has ≤ 1 terminal. */
public final class TerminalSubtabStrip extends HBox {
    private TerminalGroup group;
    private final Consumer<Integer> onActivate;
    private final Runnable onNew;
    private final BiConsumer<TerminalGroup, Integer> onClose;

    public TerminalSubtabStrip(Consumer<Integer> onActivate, Runnable onNew,
                               BiConsumer<TerminalGroup, Integer> onClose) {
        this.onActivate = onActivate;
        this.onNew = onNew;
        this.onClose = onClose;
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(2, 8, 2, 8));
        getStyleClass().add("terminal-subtab-strip");
        UiFactory.hide(this);
    }

    /** Binds this strip to a (possibly different) group, rebuilding pills. */
    public void bind(TerminalGroup group) {
        this.group = group;
        rebuild();
    }

    /** Refreshes the pill highlight to match the group's active index. */
    public void refreshActive() {
        if (group == null) return;
        Platform.runLater(this::rebuild);
    }

    private void rebuild() {
        getChildren().clear();
        if (group == null || group.size() == 0) {
            UiFactory.hide(this);
            return;
        }
        UiFactory.show(this);
        int active = group.activeIndex();
        for (int i = 0; i < group.size(); i++) {
            int idx = i;
            boolean isActive = (i == active);
            boolean busy = group.terminals().get(i).busyProperty().get();
            String label;
            if (isActive) {
                String sl = group.terminals().get(i).sessionLabel();
                label = (sl != null && !sl.isBlank()) ? sl : String.valueOf(i + 1);
            } else {
                label = String.valueOf(i + 1);
            }
            Button pill = new Button(label);
            pill.getStyleClass().addAll("app-button", "terminal-subtab");
            if (isActive) pill.getStyleClass().add("active");
            if (busy) pill.getStyleClass().add("busy");
            pill.setOnAction(e -> onActivate.accept(idx));
            pill.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && onClose != null) onClose.accept(group, idx);
            });
            // Close button (appears on hover via CSS)
            Button closeBtn = new Button(Icons.CLOSE);
            closeBtn.getStyleClass().addAll("subtab-close", "icon");
            closeBtn.setOnAction(e -> { if (onClose != null) onClose.accept(group, idx); });
            HBox pillBox = new HBox(4, pill);
            if (i == group.size() - 1 || isActive) pillBox.getChildren().add(closeBtn);
            getChildren().add(pillBox);
        }
        // + button
        Button addBtn = new Button(Icons.ADD);
        addBtn.getStyleClass().addAll("app-button", "terminal-subtab-add", "icon");
        addBtn.setOnAction(e -> onNew.run());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(spacer, addBtn);
    }
}
