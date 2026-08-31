package com.conload.ui.createcontext;

import com.conload.ui.Icons;
import com.conload.ui.createcontext.model.PageTreeItem;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.layout.HBox;

/**
 * Custom TreeCell for the Confluence page tree.
 * Shows a large checkbox label and the page title; clicking the checkbox
 * toggles the selection state on the underlying {@link PageTreeItem}.
 */
public class PageTreeCell extends TreeCell<PageTreeItem> {

    private final Label  chkLabel = new Label(Icons.UNCHECKED);
    private final Label  titleLbl = new Label();
    private final HBox   layout;
    private PageTreeItem bound;

    public PageTreeCell() {
        chkLabel.getStyleClass().add("bold");
        chkLabel.getStyleClass().add("icon");
        titleLbl.getStyleClass().add("title");
        layout = new HBox(10, chkLabel, titleLbl);
        layout.setAlignment(Pos.CENTER_LEFT);
        layout.setPadding(new Insets(4, 8, 4, 2));
        getStyleClass().add("bg-transparent");

        chkLabel.setOnMouseClicked(ev -> {
            if (bound != null) {
                bound.selected.set(!bound.selected.get());
                ev.consume();
            }
        });
    }

    @Override
    protected void updateItem(PageTreeItem item, boolean empty) {
        super.updateItem(item, empty);

        if (bound != null) {
            bound.selected.removeListener(this::onSelectedChanged);
            titleLbl.textProperty().unbind();
        }
        bound = null;

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
        } else {
            bound = item;
            titleLbl.textProperty().bind(item.title);
            chkLabel.setText(item.selected.get() ? Icons.CHECKED : Icons.UNCHECKED);
            item.selected.addListener(this::onSelectedChanged);
            setGraphic(layout);
            setText(null);
        }
    }

    private void onSelectedChanged(javafx.beans.value.ObservableValue<? extends Boolean> obs,
                                    Boolean old, Boolean now) {
        chkLabel.setText(now ? Icons.CHECKED : Icons.UNCHECKED);
    }
}
