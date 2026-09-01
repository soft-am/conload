package com.conload.ui;

import com.conload.model.CliTypeDefinition;
import com.conload.ui.components.UiFactory;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * One editable row in the Config tab's "CLI types" section. Holds five
 * TextFields (label / detect / list / resume / export) plus a Compact
 * CheckBox and a Remove button, and converts to/from a
 * {@link CliTypeDefinition}.
 * <p>
 * Layout is a single-line {@link HBox}; the list/resume/export command fields
 * grow to fill width. No validation is enforced here — Save serializes whatever
 * is typed; fully-blank rows are dropped by {@code ConfigService.saveCliTypes}.
 */
public class CliTypeRow {

    final TextField labelField   = UiFactory.darkTextField("");
    final TextField detectField  = UiFactory.darkTextField("");
    final TextField listField    = UiFactory.darkTextField("");
    final TextField resumeField  = UiFactory.darkTextField("");
    final TextField exportField  = UiFactory.darkTextField("");
    final CheckBox compactField  = new CheckBox();
    final Button removeBtn      = UiFactory.errorButton("×");
    final HBox row;

    public CliTypeRow() {
        this(null);
    }

    /** Creates a row populated from a definition (fields blank when {@code def}
     *  is {@code null}). */
    public CliTypeRow(CliTypeDefinition def) {
        compactField.setSelected(def != null && def.canCompact());
        removeBtn.setTooltip(new javafx.scene.control.Tooltip("Remove this CLI type"));
        removeBtn.setPrefWidth(36);
        if (def != null) {
            labelField.setText(def.getLabel());
            detectField.setText(def.getDetectText());
            listField.setText(def.getListCommand());
            resumeField.setText(def.getResumeCommand());
            exportField.setText(def.getExportCommand());
            compactField.setSelected(def.canCompact());
        }
        // Vertical-align the narrow label/detect/remove fields.
        VBox labelBox = labeled("LABEL", labelField, 110);
        VBox detectBox = labeled("DETECT", detectField, 90);
        VBox listBox = labeled("LIST CMD", listField, 0);
        VBox resumeBox = labeled("RESUME CMD", resumeField, 0);
        VBox exportBox = labeled("EXPORT CMD", exportField, 0);
        VBox compactBox = labeled("COMPACT", compactField, 60);
        HBox.setHgrow(listField, Priority.ALWAYS);
        HBox.setHgrow(resumeField, Priority.ALWAYS);
        HBox.setHgrow(exportField, Priority.ALWAYS);
        row = new HBox(6, labelBox, detectBox, listBox, resumeBox, exportBox, compactBox, removeBtn);
        row.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
        // Self-wiring: Remove drops this row from whatever container holds it.
        // (Assigned after `row` exists so the lambda can capture it.)
        removeBtn.setOnAction(e -> {
            javafx.scene.Parent parent = row.getParent();
            if (parent instanceof javafx.scene.layout.Pane pane) {
                pane.getChildren().remove(row);
            }
        });
    }

    /** Small column with a tiny uppercase muted header above the field. */
    private static VBox labeled(String header, javafx.scene.control.Control field, double fixedWidth) {
        Label h = new Label(header);
        h.getStyleClass().addAll("field-label", "small");
        h.setStyle("-fx-font-size: 9px; -fx-text-fill: -app-muted; -fx-padding: 0 0 1 0;");
        if (fixedWidth > 0) {
            field.setPrefWidth(fixedWidth);
            field.setMinWidth(fixedWidth);
            field.setMaxWidth(fixedWidth);
        }
        return new VBox(2, h, field);
    }

    /** Returns the HBox node to add to the container. */
    public HBox node() { return row; }

}
