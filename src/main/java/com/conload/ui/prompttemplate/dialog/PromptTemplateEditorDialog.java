package com.conload.ui.prompttemplate.dialog;

import com.conload.model.QuickAction;
import com.conload.ui.DialogStyler;
import com.conload.ui.Theme;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.List;
import java.util.stream.Collectors;

/** Shared editor for named prompt templates. Persistence belongs to the caller. */
public class PromptTemplateEditorDialog extends Dialog<QuickAction> {

    private final QuickAction existing;
    private final TextField nameField;
    private final TextArea contentArea;

    public PromptTemplateEditorDialog(QuickAction existing, String title, double width, double height) {
        this.existing = existing;
        boolean isEdit = existing != null;
        setTitle(title);
        setHeaderText("Use ${varName} for replaceable variables");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setPrefSize(width, height);

        nameField = new TextField(isEdit ? existing.getName() : "");
        nameField.setPromptText("Template name");
        contentArea = new TextArea(isEdit ? existing.getCommand() : "");
        contentArea.setPromptText("Enter your prompt template here…\nUse ${variable} for replaceable parts.");
        contentArea.setPrefRowCount(20);
        contentArea.setWrapText(true);

        Label hint = new Label();
        hint.getStyleClass().addAll("secondary", "small");
        Runnable updateHint = () -> {
            List<String> vars = QuickAction.extractParameters(contentArea.getText());
            hint.setText(vars.isEmpty() ? "Variables: (none)" :
                    "Variables: " + vars.stream().map(v -> "${" + v + "}")
                            .collect(Collectors.joining("  ")));
        };
        contentArea.textProperty().addListener((obs, oldValue, newValue) -> updateHint.run());
        updateHint.run();

        Label nameLabel = new Label("Name");
        Label contentLabel = new Label("Prompt content");
        Theme.classes(nameLabel, Theme.CL_TITLE_SMALL);
        Theme.classes(contentLabel, Theme.CL_TITLE_SMALL);
        nameField.getStyleClass().add("input");
        contentArea.getStyleClass().addAll("input", "text-area");

        VBox form = new VBox(8, nameLabel, nameField, contentLabel, contentArea, hint);
        form.setPadding(new Insets(12, 14, 8, 14));
        Theme.classes(form, Theme.CL_BG_APP);
        VBox.setVgrow(contentArea, Priority.ALWAYS);
        getDialogPane().setContent(form);
        setResultConverter(button -> button == ButtonType.OK && valid() ? buildResult() : null);
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, this::rejectInvalid);
    }

    /** Shows this editor with the requested owner and modality. */
    public java.util.Optional<QuickAction> showAndWait(Window owner, Modality modality) {
        if (owner != null) initOwner(owner);
        if (modality != null) initModality(modality);
        DialogStyler.style(this);
        return super.showAndWait();
    }

    private boolean valid() {
        return !nameField.getText().isBlank() && !contentArea.getText().isBlank();
    }

    private void rejectInvalid(ActionEvent event) {
        if (!valid()) event.consume();
    }

    private QuickAction buildResult() {
        String name = nameField.getText().strip();
        String content = contentArea.getText().strip();
        QuickAction result = new QuickAction(existing == null ? java.util.UUID.randomUUID().toString() : existing.getId(),
                name, content, QuickAction.extractParameters(content));
        result.setTemplate(true);
        return result;
    }
}
