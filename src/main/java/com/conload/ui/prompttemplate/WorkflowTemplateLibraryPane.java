package com.conload.ui.prompttemplate;

import com.conload.ui.DialogStyler;
import com.conload.ui.Icons;
import com.conload.ui.Theme;
import com.conload.ui.components.UiFactory;
import com.conload.ui.prompttemplate.dialog.WorkflowTemplateEditorDialog;
import com.conload.workflow.PromptTemplateLoader;
import com.conload.workflow.Workflow;
import com.conload.workflow.WorkflowRegistry;
import com.conload.workflow.WorkflowTemplateStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * "Workflow Templates" tab content for the Prompts library. Renders one card
 * per registered {@link Workflow}; each card offers an Edit action that opens
 * {@link WorkflowTemplateEditorDialog} (locked-variable chips + editable prose)
 * and persists the result as a global override via {@link WorkflowTemplateStore}.
 * <p>
 * The class is a thin composition root: it holds no business logic beyond
 * wiring the dialog, persistence, and refresh. The host passes a
 * {@code Runnable onUpdated} (usually a refresh of the shared prompt panel)
 * invoked after a successful save/reset.
 */
public class WorkflowTemplateLibraryPane extends VBox {

    private final Stage stage;
    private final Runnable onUpdated;

    public WorkflowTemplateLibraryPane(Stage stage, Runnable onUpdated) {
        this.stage = stage;
        this.onUpdated = onUpdated;
        setSpacing(10);
        setPadding(new Insets(20, 28, 16, 28));
        Theme.classes(this, Theme.CL_BG_APP);
        getChildren().add(buildHeader());
        getChildren().add(new Separator());
        getChildren().add(buildList());
    }

    private HBox buildHeader() {
        Label header = new Label("# Workflow Templates");
        header.getStyleClass().add("title");
        header.setPadding(new Insets(0, 0, 4, 0));
        Region sp = UiFactory.hSpacer();
        Label hint = new Label("Variables are always included and locked");
        hint.getStyleClass().addAll("secondary", "small");
        return new HBox(10, header, sp, hint);
    }

    private ScrollPane buildList() {
        VBox list = new VBox(10);
        for (Workflow w : WorkflowRegistry.all()) {
            list.getChildren().add(buildWorkflowCard(w));
        }
        ScrollPane scroll = UiFactory.scrollable(list);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private VBox buildWorkflowCard(Workflow w) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");

        Label nameLbl = new Label(Icons.SPARKLE + " " + w.displayName());
        nameLbl.getStyleClass().add("title");
        Region sp = UiFactory.hSpacer();

        Button btnEdit = UiFactory.actionButton(Icons.EDIT + " Edit");
        btnEdit.setOnAction(e -> showEditDialog(w));
        HBox hdr = new HBox(10, nameLbl, sp, btnEdit);
        hdr.setAlignment(Pos.CENTER_LEFT);

        Label desc = new Label(w.description());
        desc.getStyleClass().addAll("secondary", "small");
        desc.setWrapText(true);
        desc.setMaxWidth(Double.MAX_VALUE);

        Label status = new Label();
        status.getStyleClass().addAll("secondary", "small");
        updateStatus(status, w);

        card.getChildren().addAll(hdr, desc, status);
        return card;
    }

    private void updateStatus(Label status, Workflow w) {
        boolean edited = WorkflowTemplateStore.hasOverride(w.id());
        status.setText(edited
                ? Icons.EDIT + " Edited — customised text"
                : Icons.DOCUMENT + " Default (bundled)");
        status.setTooltip(new Tooltip(edited
                ? "A custom override is active. \"Reset to default\" inside the editor restores the bundled text."
                : "No override — uses the bundled template."));
    }

    private void showEditDialog(Workflow w) {
        double popW = Math.max(UiFactory.widePopupWidth(stage), 800);
        double popH = Math.max(UiFactory.widePopupHeight(stage), 650);
        String bundled = PromptTemplateLoader.load(w.templateResource());
        String effective = WorkflowTemplateStore.loadEffective(w.id(), w.templateResource());

        WorkflowTemplateEditorDialog dialog = new WorkflowTemplateEditorDialog(
                w.displayName(), w.description(), bundled, effective, popW, popH);
        dialog.setHeaderText("Edit Workflow Template — " + w.displayName()
                + "   (variables locked)");
        java.util.Optional<String> result = dialog.showAndWait(stage, Modality.WINDOW_MODAL);
        if (result.isEmpty()) return;
        String text = result.get();
        try {
            // If the result matches the bundled template, treat as a reset
            // (no override needed) so "default" status is restored.
            if (text.equals(bundled)) {
                WorkflowTemplateStore.reset(w.id());
            } else {
                WorkflowTemplateStore.save(w.id(), text);
            }
            if (onUpdated != null) onUpdated.run();
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Save failed: " + ex.getMessage());
            DialogStyler.style(alert);
            alert.showAndWait();
        }
    }
}
