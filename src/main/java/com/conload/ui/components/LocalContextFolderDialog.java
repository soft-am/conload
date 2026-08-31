package com.conload.ui.components;

import com.conload.ui.DialogStyler;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Dialog for selecting one or more local folders to link as context. */
public final class LocalContextFolderDialog extends Dialog<List<Path>> {

    private final List<Path> sources = new ArrayList<>();

    public LocalContextFolderDialog(Window owner) {
        setTitle("Link Local Folders");
        if (owner != null) initOwner(owner);
        DialogStyler.style(this);

        VBox pickers = new VBox(6);
        ScrollPane pickersScroll = UiFactory.scrollable(pickers);
        pickersScroll.setMaxHeight(120);

        VBox content = new VBox(8, new Label("Link a folder as context:"), pickersScroll);
        content.setPadding(new javafx.geometry.Insets(12));
        content.getStyleClass().add("bg-panel");

        var addFolderButton = UiFactory.actionButton("Add Folder…");
        addFolderButton.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Folder");
            java.io.File directory = chooser.showDialog(getOwner());
            if (directory != null) {
                Path source = directory.toPath();
                if (!sources.contains(source)) sources.add(source);
                UiFactory.fillFolderList(pickers, sources);
            }
        });
        content.getChildren().add(new HBox(8, addFolderButton));

        DialogPane pane = getDialogPane();
        pane.setContent(content);
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        pane.setPrefWidth(760);
        setResultConverter(button -> button == ButtonType.OK ? new ArrayList<>(sources) : null);
    }
}
