package com.conload.ui.prompttemplate.dialog;

import com.conload.ui.DialogStyler;
import com.conload.ui.Theme;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Large popup editor for the prompt textarea. Seeded with the current prompt
 *  text; both buttons return the edited text so edits always sync back to the
 *  default textarea — only "Send to terminal" additionally dispatches to the
 *  terminal. Mimics {@link PromptTemplateEditorDialog}. */
public class PromptFullscreenDialog extends Dialog<PromptFullscreenDialog.Result> {

    public record Result(String text, boolean send) {}

    private final TextArea contentArea;
    private final ButtonType sendType;
    private final ButtonType closeType;

    public PromptFullscreenDialog(String initialText, String accentColor) {
        setTitle("Prompt editor");
        setHeaderText("Edit the prompt");
        sendType = new ButtonType("Send to terminal", ButtonBar.ButtonData.OK_DONE);
        closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(sendType, closeType);
        getDialogPane().setPrefSize(1200, 900);

        Label titleLabel = new Label("Prompt content");
        Theme.classes(titleLabel, Theme.CL_TITLE_SMALL);

        contentArea = new TextArea(initialText == null ? "" : initialText);
        contentArea.setWrapText(true);
        contentArea.setPromptText("Prompt text will appear here…");
        contentArea.getStyleClass().addAll("input", "text-area");
        if (accentColor != null && !accentColor.isBlank()) {
            contentArea.setStyle("-accent-color: " + accentColor + ";");
        }

        VBox form = new VBox(8, titleLabel, contentArea);
        form.setPadding(new Insets(12, 14, 8, 14));
        Theme.classes(form, Theme.CL_BG_APP);
        VBox.setVgrow(contentArea, Priority.ALWAYS);
        getDialogPane().setContent(form);

        setResultConverter(btn -> {
            if (btn == sendType) return new Result(contentArea.getText(), true);
            if (btn == closeType) return new Result(contentArea.getText(), false);
            return null;
        });
    }

    public java.util.Optional<Result> showAndWait(Window owner, Modality modality) {
        if (owner != null) initOwner(owner);
        if (modality != null) initModality(modality);
        DialogStyler.style(this);
        return super.showAndWait();
    }
}
