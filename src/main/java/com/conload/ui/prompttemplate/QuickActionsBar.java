package com.conload.ui.prompttemplate;

import com.conload.ui.DialogStyler;
import com.conload.ui.Icons;
import com.conload.ui.Theme;
import com.conload.ui.components.UiFactory;

import com.conload.model.QuickAction;
import com.conload.service.QuickActionService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Bar shown above the terminal.
 * Simple actions are sent directly; actions whose command contains ${varName}
 * placeholders ("quick action with input") open an input dialog first, then the
 * provided values are substituted into the command before sending.
 */
public class QuickActionsBar extends VBox {

    // ── Fields ────────────────────────────────────────────────────────────────
    private final QuickActionService svc    = new QuickActionService();
    private final Consumer<String>   sender;

    private       FlowPane actionsFlow;
    private       Button manageBtn;

    /** Edit-popup state — rebuilt lazily when null. */
    private Stage editPopupStage;
    private VBox  editRowsContainer;

    public QuickActionsBar(Consumer<String> sender) {
        this.sender = sender;
        setSpacing(0);
        getStyleClass().add("panel-border-bottom");

        actionsFlow = new FlowPane();
        actionsFlow.setHgap(6);
        actionsFlow.setVgap(4);
        actionsFlow.setAlignment(Pos.CENTER_LEFT);
        actionsFlow.setPadding(new Insets(3, 8, 3, 8));
        actionsFlow.getStyleClass().add("panel-border-bottom");

        manageBtn = UiFactory.iconButton(Icons.ADD_CIRCLE, null);
        manageBtn.getStyleClass().addAll("secondary", "quick-action-manage-btn");
        manageBtn.setTooltip(new Tooltip("Manage quick actions"));
        manageBtn.setOnAction(e -> toggleEditPopup());

        getChildren().add(actionsFlow);
        refresh();
    }

    // ── Actions row ───────────────────────────────────────────────────────────

    public void refresh() {
        actionsFlow.getChildren().clear();

        // With-input actions go to the front of the row (before plain actions),
        // so they're always reachable at the top-left of the bar.
        java.util.List<QuickAction> withInput = new ArrayList<>();
        java.util.List<QuickAction> plain    = new ArrayList<>();
        for (QuickAction qa : svc.load()) {
            if (qa.isTemplate()) continue;
            (qa.hasInput() ? withInput : plain).add(qa);
        }

        for (QuickAction qa : withInput) {
            Button btn = UiFactory.actionButton(qa.getName());
            btn.getStyleClass().add("quick-action-input");
            btn.setTooltip(new Tooltip(qa.getName() + " — provides input fields:\n"
                    + QuickAction.extractParameters(qa.getCommand()).stream()
                            .map(v -> "${" + v + "}").collect(Collectors.joining("  "))));
            btn.setOnAction(e -> runWithInput(qa));
            actionsFlow.getChildren().add(btn);
        }
        for (QuickAction qa : plain) {
            Button btn = UiFactory.actionButton(qa.getName());
            btn.setOnAction(e -> sender.accept(qa.getCommand()));
            actionsFlow.getChildren().add(btn);
        }
        // Manage button at the end
        actionsFlow.getChildren().add(manageBtn);
    }

    // ── Quick Action with Input ───────────────────────────────────────────────

    /**
     * Opens a small dialog with one input field per ${varName} placeholder in the
     * action's command. On confirm, the values are substituted into the command
     * and the result is sent to the terminal.
     */
    private void runWithInput(QuickAction qa) {
        List<String> params = QuickAction.extractParameters(qa.getCommand());
        if (params.isEmpty()) {
            sender.accept(qa.getCommand());
            return;
        }

        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle(qa.getName());
        dialog.setHeaderText(Icons.EDIT + "  " + qa.getName() + " — provide input values");

        DialogPane pane = dialog.getDialogPane();
        pane.setPrefWidth(460);
        ButtonType okBtn = new ButtonType("Run", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(okBtn, ButtonType.CANCEL);
        DialogStyler.style(dialog);

        List<TextField> fields = new ArrayList<>();
        VBox box = new VBox(6);
        box.setPadding(new Insets(12));
        for (String p : params) {
            Label lbl = new Label("${" + p + "}");
            Theme.classes(lbl, Theme.CL_TITLE_SMALL);
            TextField tf = UiFactory.darkTextField(p);
            HBox.setHgrow(tf, Priority.ALWAYS);
            HBox row = new HBox(6, lbl, tf);
            row.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(row);
            fields.add(tf);
        }
        pane.setContent(box);

        // Focus the first field and bind OK to non-blank first field.
        Platform.runLater(fields.get(0)::requestFocus);
        Button okNode = (Button) pane.lookupButton(okBtn);
        if (okNode != null) okNode.setDisable(true);
        fields.get(0).textProperty().addListener((obs, o, n) -> {
            if (okNode != null) okNode.setDisable(n.strip().isEmpty());
        });

        dialog.setResultConverter(btn -> {
            if (btn != okBtn) return null;
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < params.size(); i++) {
                values.put(params.get(i), fields.get(i).getText());
            }
            return values;
        });

        dialog.showAndWait().ifPresent(values -> {
            String cmd = QuickAction.substitute(qa.getCommand(), values);
            if (cmd != null && !cmd.isBlank()) sender.accept(cmd);
        });
    }

    // ── Edit popup ────────────────────────────────────────────────────────────

    /** Opens the manage-popup (or closes it if already showing). */
    private void toggleEditPopup() {
        if (editPopupStage != null && editPopupStage.isShowing()) {
            editPopupStage.close();
            return;
        }
        openEditPopup();
    }

    /** Builds and shows the big edit popup with a vertically scrolling list of actions. */
    private void openEditPopup() {
        Stage popup = new Stage();
        if (getScene() != null && getScene().getWindow() != null) {
            popup.initOwner(getScene().getWindow());
        }
        popup.initModality(Modality.NONE);
        popup.setTitle("Quick Actions");
        popup.setOnCloseRequest(e -> onPopupClosed());
        editPopupStage = popup;
        manageBtn.getStyleClass().removeAll("accent", "secondary");
        manageBtn.getStyleClass().add("accent");

        // ── Add-new row (sticky at top of scroll content) ─────────────────────
        VBox addBox = buildAddNewBox();
        addBox.setPadding(new Insets(10, 12, 6, 12));

        // ── Scrollable rows container ────────────────────────────────────────
        editRowsContainer = new VBox(6);
        editRowsContainer.setPadding(new Insets(2, 12, 10, 12));
        Theme.classes(editRowsContainer, Theme.CL_BG_APP);
        rebuildEditRows();

        VBox scrollContent = new VBox(addBox, editRowsContainer);
        Theme.classes(scrollContent, Theme.CL_BG_APP);

        ScrollPane scroll = UiFactory.scrollable(scrollContent);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // ── Footer hint ──────────────────────────────────────────────────────
        Label helpHint = new Label(Icons.INFO + "  Tip: use ${varName} in a command to make it a \"with input\" action.");
        helpHint.getStyleClass().addAll("secondary", "small");
        helpHint.setWrapText(true);
        helpHint.setMaxWidth(Double.MAX_VALUE);
        HBox footer = UiFactory.footerRow(helpHint);

        VBox root = new VBox(0, scroll, footer);
        root.getStyleClass().addAll("bg-app", "popup-window-root");

        Window owner = popup.getOwner();
        popup.setMinWidth(800);
        popup.setMinHeight(650);
        double popW = Math.max(UiFactory.widePopupWidth(owner), 800);
        double popH = Math.max(UiFactory.widePopupHeight(owner), 650);
        Scene scene = new Scene(root, popW, popH);
        Theme.apply(root);
        popup.setScene(scene);
        popup.setOnShown(e -> Platform.runLater(() -> {
            popup.setMinWidth(800);
            popup.setMinHeight(650);
        }));
        popup.show();
    }

    private void onPopupClosed() {
        manageBtn.getStyleClass().removeAll("accent", "secondary");
        manageBtn.getStyleClass().add("secondary");
        editRowsContainer = null;
        editPopupStage = null;
    }

    /** Rebuilds the list of existing quick-action editor cards inside the popup. */
    private void rebuildEditRows() {
        if (editRowsContainer == null) return;
        editRowsContainer.getChildren().clear();

        for (QuickAction qa : svc.load()) {
            if (qa.isTemplate()) continue;
            editRowsContainer.getChildren().add(buildEditRowCard(qa));
        }

        if (editRowsContainer.getChildren().isEmpty()) {
            Label empty = new Label("No quick actions yet. Use the row above to add one.");
            empty.getStyleClass().addAll("placeholder", "small");
            editRowsContainer.getChildren().add(empty);
        }
    }

    /** Builds the "add new" sticky box (name + command + add button). */
    private VBox buildAddNewBox() {
        TextField newName = UiFactory.textField("", 200);
        newName.setPromptText("Name");
        HBox.setHgrow(newName, Priority.ALWAYS);
        TextArea newCmd = UiFactory.commandArea("");
        newCmd.setPromptText("Command text  (use ${varName} for input)");

        Label varsHint = new Label();
        varsHint.getStyleClass().addAll("secondary", "small");
        Runnable updateHint = () -> {
            List<String> vars = QuickAction.extractParameters(newCmd.getText());
            varsHint.setText(vars.isEmpty() ? "Variables: (none — plain command)"
                    : "Variables: " + vars.stream().map(v -> "${" + v + "}").collect(Collectors.joining("  ")));
        };
        newCmd.textProperty().addListener((obs, o, n) -> updateHint.run());
        updateHint.run();

        Button addBtn = UiFactory.accentButton("Save");
        addBtn.setTooltip(new Tooltip("Add quick action"));
        addBtn.setOnAction(e -> safely(() -> {
            if (newName.getText().isBlank() || newCmd.getText().isBlank()) return;
            List<String> vars = QuickAction.extractParameters(newCmd.getText().strip());
            svc.add(newName.getText().strip(), newCmd.getText().strip(), vars);
            newName.clear();
            newCmd.clear();
            updateHint.run();
            refresh();
            rebuildEditRows();
        }));

        HBox row = UiFactory.row(newName, newCmd, addBtn);
        HBox.setHgrow(newCmd, Priority.ALWAYS);
        VBox box = new VBox(2, row, varsHint);
        box.getStyleClass().add("card-bordered");
        box.setPadding(new Insets(8));
        return box;
    }

    /** Builds one editable card for an existing quick action. */
    private VBox buildEditRowCard(QuickAction qa) {
        TextField nameF = UiFactory.textField(qa.getName(), 200);
        TextArea  cmdF  = UiFactory.commandArea(qa.getCommand());

        // Live variables hint — updates as the command is edited.
        Label varsHint = new Label();
        varsHint.getStyleClass().addAll("secondary", "small");
        Runnable updateHint = () -> {
            List<String> vars = QuickAction.extractParameters(cmdF.getText());
            varsHint.setText(vars.isEmpty() ? "Variables: (none — plain command)"
                    : "Variables: " + vars.stream().map(v -> "${" + v + "}").collect(Collectors.joining("  ")));
        };
        cmdF.textProperty().addListener((obs, o, n) -> updateHint.run());
        updateHint.run();

        Button save = UiFactory.iconButton(Icons.CHECK, null);
        save.getStyleClass().add("muted");
        save.setTooltip(new Tooltip("Save changes"));
        Button del  = UiFactory.iconButton(Icons.CLOSE, null);
        del.getStyleClass().add("error");
        del.setTooltip(new Tooltip("Delete quick action"));

        save.setOnAction(e -> safely(() -> {
            List<String> vars = QuickAction.extractParameters(cmdF.getText().strip());
            QuickAction updated = new QuickAction(
                    qa.getId(),
                    nameF.getText().strip(),
                    cmdF.getText().strip(),
                    vars
            );
            updated.setTemplate(false);
            svc.update(updated);
            refresh();
            rebuildEditRows();
        }));
        del.setOnAction(e -> safely(() -> {
            svc.delete(qa.getId());
            refresh();
            rebuildEditRows();
        }));

        HBox row = UiFactory.row(nameF, cmdF, save, del);
        HBox.setHgrow(cmdF, Priority.ALWAYS);

        VBox qaBlock = new VBox(2, row, varsHint);
        qaBlock.getStyleClass().add("card-bordered");
        qaBlock.setPadding(new Insets(8));
        return qaBlock;
    }

    private void safely(IOAction a) {
        try {
            a.run();
        } catch (Throwable ex) {
            ex.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error: " + ex.toString());
            alert.setHeaderText("An Error Occurred");
            DialogStyler.style(alert);
            alert.showAndWait();
        }
    }

    @FunctionalInterface
    private interface IOAction { void run() throws Throwable; }
}
