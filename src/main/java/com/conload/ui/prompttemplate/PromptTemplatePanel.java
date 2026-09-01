package com.conload.ui.prompttemplate;

import com.conload.ui.ProjectColors;
import com.conload.ui.DialogStyler;
import com.conload.ui.Icons;
import com.conload.ui.Theme;
import com.conload.ui.components.SpeechMicButton;
import com.conload.ui.components.UiFactory;
import com.conload.ui.components.WorkflowsIcon;
import com.conload.ui.components.ZoomIcon;
import com.conload.ui.components.SparkleIcon;
import com.conload.ui.prompttemplate.dialog.PromptTemplateEditorDialog;
import com.conload.ui.prompttemplate.dialog.PromptFullscreenDialog;

import com.conload.model.QuickAction;
import com.conload.service.QuickActionService;
import com.conload.service.SpeechRecognitionService;
import com.conload.ui.terminal.CopilotTerminalPane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt template panel displayed between QuickActionsBar and the terminal.
 * Shows a template selector with CRUD, a minimal apply button for prompt
 * template variables, compiled prompt textarea, and action buttons.
 */
public class PromptTemplatePanel extends VBox {

    private static final Pattern VAR = Pattern.compile("\\$\\{(\\w+)}");

    // ── Fields ────────────────────────────────────────────────────────────────
    private final Consumer<String> terminalSender;
    private final Runnable onQuickActionsUpdated;
    private final QuickActionService quickActionService = new QuickActionService();

    /** Optional hooks for the prompt-header right cluster (mic / export / sessions / send).
     *  All nullable so the 2-arg legacy constructor still works (the controls
     *  simply aren't built when the hooks are absent). */
    private final SpeechRecognitionService speechService;
    private final Supplier<CopilotTerminalPane> activeTerminalSupplier;
    /** Inline workflow section (form + gather + log), injected by the host.
     *  Placed above the prompt textarea and below the header row.
     *  {@code null} until {@link #setWorkflowSection} is called. */
    private com.conload.ui.workflow.WorkflowInlineSection workflowSection;
    private HBox rightCluster;
    private SpeechMicButton micButton;
    private Button sendBtn;
    private Button expandPromptBtn;
    private Button copyPromptBtn;

    private HBox templateRow;
    private TextArea promptTextArea;
    private ComboBox<String> templateCombo;
    private final Map<String, QuickAction> templateMap = new HashMap<>();
    private QuickAction selectedTemplate = null;

    private File workDir;

    /** Collapsible prompt area (collapsed by default, opens on template select). */
    private VBox promptCollapsible;
    private Button collapseToggleBtn;
    private boolean promptExpanded = false;
    /** Fired when the prompt area expands/collapses — host uses it to grow/shrink the terminal. */
    private Consumer<Boolean> onPromptExpandChanged;

    /** Project identity color (hex, e.g. "#A7F3D0") used for a thin separator
     *  border around the template-selector row so the panel is visually
     *  distinguished from the terminal below. Applied lazily so it works whether
     *  set before or after {@link #buildUI()} constructs the row. */
    private String projectColor;

    // ── Context Picker Mode Fields ─────────────────────────────────────────
    private boolean isPickerModeActive = false;
    private int targetStartOffset = -1;
    private int targetEndOffset = -1;
    private final List<String> pickerSelectedPaths = new ArrayList<>();
    private HBox pickerStatusBar;
    private Button applyContextBtn;
    private Consumer<Boolean> onPickerModeChanged;

    public void setOnPickerModeChanged(Consumer<Boolean> callback) {
        this.onPickerModeChanged = callback;
    }

    /** Set callback fired when the prompt area expands or collapses. */
    public void setOnPromptExpandChanged(Consumer<Boolean> callback) {
        this.onPromptExpandChanged = callback;
    }

    /** Inject the inline workflow section. A "Workflows" label with icon and the
     *  workflow selector combo are placed in the prompt header row (after the
     *  prompt template combo, before the right-cluster buttons), mirroring how
     *  the "Prompt" label sits before its combo. The section body is inserted
     *  between the header row and the prompt collapsible so the workflow form
     *  appears above the textarea. */
    public void setWorkflowSection(com.conload.ui.workflow.WorkflowInlineSection section) {
        this.workflowSection = section;
        if (section != null && templateRow != null) {
            WorkflowsIcon workflowIcon = new WorkflowsIcon(24);
            Label workflowLabel = new Label("Workflows");
            workflowLabel.getStyleClass().addAll("prompt-header-text", "bold");
            javafx.scene.control.ComboBox<String> combo = section.getSelector();
            combo.getStyleClass().add("prompt-template-combo");
            combo.setMaxWidth(300);
            combo.setMinWidth(Region.USE_PREF_SIZE);
            HBox.setHgrow(combo, Priority.ALWAYS);

            Button workflowToggleBtn = new Button(Icons.CHEVRON_DOWN);
            Theme.classes(workflowToggleBtn, Theme.CL_CLOSE_BTN);
            workflowToggleBtn.setMinWidth(Region.USE_COMPUTED_SIZE);
            workflowToggleBtn.setMaxWidth(Region.USE_COMPUTED_SIZE);
            workflowToggleBtn.setTooltip(new Tooltip("Toggle workflow section"));
            var hasSelection = combo.getSelectionModel().selectedItemProperty().isNotNull();
            workflowToggleBtn.visibleProperty().bind(hasSelection);
            workflowToggleBtn.managedProperty().bind(hasSelection);
            workflowToggleBtn.setOnAction(e -> {
                if (section.isVisible()) section.collapse();
                else section.show();
            });

            int insertIdx = templateRow.getChildren().indexOf(rightCluster);
            if (insertIdx < 0) insertIdx = templateRow.getChildren().size();
            templateRow.getChildren().add(insertIdx, workflowToggleBtn);
            templateRow.getChildren().add(insertIdx, combo);
            templateRow.getChildren().add(insertIdx, workflowLabel);
            templateRow.getChildren().add(insertIdx, workflowIcon);
        }
        if (section != null) {
            getChildren().add(1, section);
            section.visibleProperty().addListener((obs, wasVisible, isNowVisible) -> {
                if (isNowVisible) setPromptExpanded(false);
            });
        }
    }

    /**
     * Set this pane's project identity color — applied as a thin border around
     * the template-selector row so the panel is visually separated in the
     * project's accent color. Safe to call before or after construction.
     * Sets -accent-color on the row; the .prompt-template-row CSS class
     * provides the actual border/radius styling.
     */
    public void setProjectColor(String hex) {
        this.projectColor = (hex == null || hex.isBlank()) ? ProjectColors.DEFAULT : hex;
        applyProjectColor();
    }

    /** Applies the stored project color as -accent-color on the template row.
     *  The .prompt-template-row CSS class reads this for the border styling.
     *  Also sets -accent-color on the prompt textarea so the picker-mode path
     *  highlight resolves to the project color via the .text-area.input CSS
     *  rule (transparent fill + accent text-fill). */
    private void applyProjectColor() {
        if (projectColor == null) return;
        if (templateRow != null) {
            templateRow.setStyle("-accent-color: " + projectColor + ";");
        }
        if (promptTextArea != null) {
            promptTextArea.setStyle("-accent-color: " + projectColor + ";");
        }
        // Tint the mic button's breathing-pulse fill so it follows the project.
        if (micButton != null) micButton.setProjectColor(projectColor);
    }

    /** Legacy 2-arg constructor — no header right-cluster (mic/export/sessions/send).
     *  Used by {@link CopilotTerminalPane}'s standalone (terminalOnlyMode=false)
     *  path; in the workspace the 5-arg constructor is used instead. */
    public PromptTemplatePanel(Consumer<String> terminalSender, Runnable onQuickActionsUpdated) {
        this(terminalSender, onQuickActionsUpdated, null, null);
    }

    /** Full constructor — builds the new prompt-header row with the right-side
     *  cluster (mic / export / sessions / ▶ send). Used by {@link com.conload.ui.AppShellController}.
     *  @param speechService           shared speech-to-text service (may be null → no mic)
     *  @param activeTerminalSupplier  supplies the currently-active terminal pane
     *                                 for delegating Export/Sessions actions (may be null) */
    public PromptTemplatePanel(Consumer<String> terminalSender,
                               Runnable onQuickActionsUpdated,
                               SpeechRecognitionService speechService,
                               Supplier<CopilotTerminalPane> activeTerminalSupplier) {
        this.terminalSender = terminalSender;
        this.onQuickActionsUpdated = onQuickActionsUpdated;
        this.speechService = speechService;
        this.activeTerminalSupplier = activeTerminalSupplier;

        setSpacing(0);
        setPadding(Insets.EMPTY);

        buildUI();
    }

    /** Notify the panel that the active project tab / terminal changed so the
     *  right-cluster buttons (mic) can refresh their visibility and enabled
     *  state. Called by {@link com.conload.ui.AppShellController} after a
     *  project tab switch. */
    public void refreshActiveTerminal() {
        CopilotTerminalPane t = activeTerminalSupplier == null ? null : activeTerminalSupplier.get();
        boolean hasTerminal = t != null;
        if (micButton != null) {
            micButton.setDisable(!hasTerminal || speechService == null);
            micButton.refreshState();
        }
    }

    /** Set the prompt text area content and auto-expand it so the text is
     *  visible. Used by the workflow dialog to push a substituted prompt into
     *  the shared prompt panel — the user then reviews and applies via ▶ Send.
     *  Clears any selected template so the text is not overwritten by template
     *  re-selection logic. */
    public void setPromptText(String text) {
        selectedTemplate = null;
        templateCombo.setValue(null);
        promptTextArea.setText(text != null ? text : "");
        setPromptExpanded(true);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setWorkDir(File dir) {
        this.workDir = dir;
    }

    /** Handles sidebar clicks only while context-picker mode is active. */
    public void handleFileClick(File file) {
        if (isPickerModeActive) addSelectedContext(file);
    }

    public void refreshTemplates() {
        loadTemplates();
    }

    // ── UI Build ──────────────────────────────────────────────────────────────

    private void buildUI() {
        buildTemplateHeader();
        buildPromptEditor();
        buildPickerStatusBar();
        buildPromptEditorOverlay();

        getChildren().addAll(templateRow, promptCollapsible);
        loadTemplates();
    }

    private void buildTemplateHeader() {
        // ── Header row: [✦][Prompt][templateCombo...] [collapse][mic][export][sessions][▶ send] ──
        SparkleIcon sparkle = new SparkleIcon(24);

        Label templateLabel = new Label("Prompt");
        templateLabel.getStyleClass().addAll("prompt-header-text", "bold");

        templateCombo = new ComboBox<>();
        templateCombo.setPromptText("Select a prompt template...");
        // Cap the combo so it doesn't stretch to claim every spare pixel on wide
        // windows; it remains the only flex element (HGrow=ALWAYS) but caps at
        // 300px, leaving a gap before the trailing buttons on wide windows.
        // Min = pref so the combo never shrinks below its prompt-text width,
        // keeping it visible on narrow windows before the trailing buttons clip.
        templateCombo.setMaxWidth(300);
        templateCombo.setMinWidth(Region.USE_PREF_SIZE);
        templateCombo.setOnAction(e -> onTemplateSelected());
        templateCombo.getStyleClass().add("prompt-template-combo");
        HBox.setHgrow(templateCombo, Priority.ALWAYS);

        // Collapse/close toggle button — always shows ×; lives in the text-area overlay.
        collapseToggleBtn = new Button(Icons.CLOSE);
        Theme.classes(collapseToggleBtn, Theme.CL_CLOSE_BTN);
        // Pin the close button to its preferred width so it never shrinks/disappears
        // when the window narrows — only the template combo is responsive.
        collapseToggleBtn.setMinWidth(Region.USE_COMPUTED_SIZE);
        collapseToggleBtn.setMaxWidth(Region.USE_COMPUTED_SIZE);
        HBox.setHgrow(collapseToggleBtn, Priority.NEVER);
        collapseToggleBtn.setTooltip(new Tooltip("Close prompt area"));
        collapseToggleBtn.setOnAction(e -> togglePromptExpanded());

        // Right cluster — only present when the full 5-arg constructor was used.
        rightCluster = new HBox(4);
        rightCluster.setAlignment(Pos.CENTER_RIGHT);
        // Pin the cluster to its preferred width and disable horizontal growth
        // so the trailing buttons (mic/workflow-combo/send) never shrink or
        // clip when the window narrows — only the template combo is responsive.
        rightCluster.setMinWidth(Region.USE_COMPUTED_SIZE);
        rightCluster.setMaxWidth(Region.USE_COMPUTED_SIZE);
        HBox.setHgrow(rightCluster, Priority.NEVER);

        if (speechService != null) {
            micButton = new SpeechMicButton(null, terminalSender);
            micButton.setSpeechService(speechService);
            // Once this panel is in a scene, propagate the owner window so the
            // recording popup centers over it.
            sceneProperty().addListener((obs, oldS, newS) ->
                micButton.setOwnerWindow(newS == null ? null : newS.getWindow()));
            pinButtonWidth(micButton);
            rightCluster.getChildren().add(micButton);
        }
        // ▶ Send — always visible; same handler as "Apply in terminal" before.
        sendBtn = new Button( "Send to terminal");
        sendBtn.getStyleClass().addAll("app-button", "accent", "prompt-action-btn");
        sendBtn.setTooltip(new Tooltip("Send the prompt to terminal stdin"));
        sendBtn.setDisable(true);
        sendBtn.setOnAction(e -> sendToTerminal());
        pinButtonWidth(sendBtn);

        HBox templateRow = new HBox(6, sparkle, templateLabel, templateCombo,
                rightCluster);
        templateRow.setAlignment(Pos.CENTER_LEFT);
        templateRow.setPadding(new Insets(14, 10, 14, 10));
        // Stylesheet hook only — .prompt-template-row no longer paints a border.
        templateRow.getStyleClass().add("prompt-template-row");
        VBox.setMargin(templateRow, new Insets(32, 14, 12, 14));
        this.templateRow = templateRow;
        applyProjectColor();

    }

    private void buildPromptEditor() {
        // ── Prompt TextArea ──
        promptTextArea = new TextArea();
        promptTextArea.setWrapText(true);
        promptTextArea.setPrefRowCount(5);
        promptTextArea.setMinHeight(100);
        promptTextArea.setPrefHeight(300);
        promptTextArea.setPromptText("Prompt text will appear here...");
        promptTextArea.getStyleClass().addAll("input", "text-area");
        // Send + Copy buttons enabled only when there is text.
        promptTextArea.textProperty().addListener((obs, o, n) -> {
            boolean empty = n == null || n.trim().isEmpty();
            sendBtn.setDisable(empty);
            if (copyPromptBtn != null) copyPromptBtn.setDisable(empty);
        });

        // ── Right-click context menu: Add context ──
        ContextMenu contextMenu = new ContextMenu();
        MenuItem addContextItem = new MenuItem(Icons.ATTACH + " Add context");
        addContextItem.setOnAction(e -> enterPickerMode());
        contextMenu.getItems().add(addContextItem);
        promptTextArea.setContextMenu(contextMenu);

    }

    private void buildPickerStatusBar() {
        // ── Picker Status Bar (hidden by default) ──
        Label pickerMsg = new Label(Icons.TARGET + " PICKER MODE  ·  Click files / packages in the Explorer tree to insert at cursor");
        pickerMsg.getStyleClass().addAll("warning", "bold");

        Region pickerSpacer = UiFactory.hSpacer();

        applyContextBtn = new Button(Icons.APPLY + " APPLY");
        applyContextBtn.getStyleClass().addAll("app-button", "success");
        applyContextBtn.setTooltip(new Tooltip("Finalize selection and exit context picker mode."));
        applyContextBtn.setOnAction(e -> exitPickerMode());

        pickerStatusBar = new HBox(8, pickerMsg, pickerSpacer, applyContextBtn);
        pickerStatusBar.setAlignment(Pos.CENTER_LEFT);
        pickerStatusBar.setPadding(new Insets(6, 10, 6, 10));
        pickerStatusBar.getStyleClass().addAll("panel");
        UiFactory.hide(pickerStatusBar);

    }

    private void buildPromptEditorOverlay() {
        expandPromptBtn = buildExpandPromptBtn();
        // ── Copy button ──
        copyPromptBtn = UiFactory.actionButton("Copy");
        copyPromptBtn.getStyleClass().addAll("prompt-action-btn");
        copyPromptBtn.setTooltip(new Tooltip("Copy prompt to clipboard"));
        copyPromptBtn.setDisable(true);
        copyPromptBtn.setOnAction(e -> copyPromptToClipboard());
        // ── Clear button ──
        Button clearBtn = UiFactory.actionButton("Clear");
        clearBtn.getStyleClass().addAll("prompt-action-btn", "danger");
        clearBtn.setTooltip(new Tooltip("Clear prompt"));
        clearBtn.setOnAction(e -> {
            selectedTemplate = null;
            templateCombo.setValue(null);
            promptTextArea.clear();
        });

        // ── Action overlay: Expand + Send + Copy + Clear + Close, floating in the top-right
        //    corner of the prompt text area. ──
        HBox textActionOverlay = new HBox(4, expandPromptBtn, sendBtn, copyPromptBtn, clearBtn,  collapseToggleBtn);
        textActionOverlay.setAlignment(Pos.TOP_RIGHT);
        textActionOverlay.setPickOnBounds(false);
        textActionOverlay.getStyleClass().add("prompt-action-overlay");
        StackPane.setMargin(textActionOverlay, new Insets(4, 4, 0, 0));

        StackPane promptStack = new StackPane(promptTextArea, textActionOverlay);
        promptStack.getStyleClass().add("prompt-stack");

        // ── Collapsible area: picker bar + (text area + action overlay) ──
        promptCollapsible = new VBox(4, pickerStatusBar, promptStack);
        promptCollapsible.setPadding(new Insets(4, 8, 4, 8));
        promptCollapsible.getStyleClass().add("prompt-collapsible");
        // Collapsed by default
        UiFactory.hide(promptCollapsible);
    }

    /** Toggle the prompt area expanded / collapsed. */
    private void togglePromptExpanded() {
        setPromptExpanded(!promptExpanded);
    }

    /** Expand or collapse the prompt area. */
    private void setPromptExpanded(boolean expanded) {
        promptExpanded = expanded;
        UiFactory.setVisible(promptCollapsible, expanded);
        // Close button (×) stays static regardless of state.
        collapseToggleBtn.setTooltip(new Tooltip("Close prompt area"));
        // Notify host so it can grow/shrink the terminal area accordingly
        if (onPromptExpandChanged != null) onPromptExpandChanged.accept(expanded);
    }

    /** Programmatically collapse the prompt area (used when switching to full-window views). */
    public void collapsePrompt() {
        if (!promptExpanded) return;
        setPromptExpanded(false);
        if (workflowSection != null) workflowSection.collapse();
    }

    /** Returns whether the prompt area is currently expanded. */
    public boolean isPromptExpanded() {
        return promptExpanded;
    }

    // ── Context Picker Mode ───────────────────────────────────────────────────

    private void enterPickerMode() {
        String text = promptTextArea.getText();
        if (text == null) text = "";
        int caret = promptTextArea.getCaretPosition();

        int start = caret;
        int end   = caret;
        boolean matched = false;

        Matcher m = VAR.matcher(text);
        while (m.find()) {
            if (caret >= m.start() && caret <= m.end()) {
                start   = m.start();
                end     = m.end();
                matched = true;
                break;
            }
        }

        if (!matched && promptTextArea.getSelection().getLength() > 0) {
            start   = promptTextArea.getSelection().getStart();
            end     = promptTextArea.getSelection().getEnd();
            matched = true;
        }

        if (!matched) {
            promptTextArea.insertText(caret, " ");
            start = caret;
            end   = caret + 1;
        }

        targetStartOffset = start;
        targetEndOffset   = end;
        pickerSelectedPaths.clear();
        isPickerModeActive = true;

        if (onPickerModeChanged != null) onPickerModeChanged.accept(true);

        promptTextArea.selectRange(targetStartOffset, targetEndOffset);

        UiFactory.show(pickerStatusBar);

        if (promptTextArea.getScene() != null) {
            promptTextArea.getScene().setCursor(Cursor.CROSSHAIR);
        }
    }

    private void addSelectedContext(File file) {
        if (targetStartOffset < 0 || targetEndOffset < 0) return;

        String relPath = getRelativePath(file);
        if (file.isDirectory() && !relPath.endsWith("/")) {
            relPath = relPath + "/";
        }

        if (!pickerSelectedPaths.contains(relPath)) {
            pickerSelectedPaths.add(relPath);
        }

        String replacement = String.join(", ", pickerSelectedPaths);
        promptTextArea.replaceText(targetStartOffset, targetEndOffset, replacement);
        targetEndOffset = targetStartOffset + replacement.length();
        promptTextArea.selectRange(targetStartOffset, targetEndOffset);
    }

    private void exitPickerMode() {
        isPickerModeActive = false;

        if (onPickerModeChanged != null) onPickerModeChanged.accept(false);

        UiFactory.hide(pickerStatusBar);

        if (promptTextArea.getScene() != null) {
            promptTextArea.getScene().setCursor(Cursor.DEFAULT);
        }

        if (targetStartOffset >= 0 && targetEndOffset >= 0
                && targetEndOffset <= promptTextArea.getLength()) {
            promptTextArea.selectRange(targetStartOffset, targetEndOffset);
        }

        targetStartOffset = -1;
        targetEndOffset   = -1;
        pickerSelectedPaths.clear();
    }

    // ── Template Management ───────────────────────────────────────────────────

    private void loadTemplates() {
        String prev = templateCombo.getValue();
        templateMap.clear();
        templateCombo.getItems().clear();

        List<QuickAction> templates = quickActionService.load().stream()
                .filter(QuickAction::isTemplate)
                .toList();

        for (QuickAction t : templates) {
            templateMap.put(t.getName(), t);
            templateCombo.getItems().add(t.getName());
        }
        if (prev != null && templateCombo.getItems().contains(prev)) {
            templateCombo.setValue(prev);
        }
    }

    private void onTemplateSelected() {
        String selected = templateCombo.getValue();
        if (selected == null || selected.isEmpty()) {
            selectedTemplate = null;
            promptTextArea.clear();
            return;
        }
        selectedTemplate = templateMap.get(selected);
        if (selectedTemplate == null) return;
        promptTextArea.setText(selectedTemplate.getCommand());
        setPromptExpanded(true);
        if (workflowSection != null) workflowSection.reset();
    }

    // ── Template CRUD Dialog ──────────────────────────────────────────────────

    public void showTemplateDialog(QuickAction existing, Runnable onComplete) {
         boolean isEdit = existing != null;
         PromptTemplateEditorDialog dialog = new PromptTemplateEditorDialog(existing,
                 isEdit ? "Edit Prompt Template" : "Create Prompt Template", 560, 420);
         dialog.showAndWait(null, null).ifPresent(result -> {
                     try {
                         if (isEdit) {
                             quickActionService.update(result);
                         } else {
                             List<QuickAction> all = quickActionService.load();
                             all.add(result);
                             quickActionService.save(all);
                         }
                         loadTemplates();
                         templateCombo.setValue(result.getName());
                        if (onQuickActionsUpdated != null) onQuickActionsUpdated.run();
                        if (onComplete != null) onComplete.run();
                    } catch (IOException ex) {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Save failed: " + ex.getMessage());
                        DialogStyler.style(alert);
                        alert.showAndWait();
                    }
                });
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private Button buildExpandPromptBtn() {
        Button btn = new Button();
        btn.setGraphic(new ZoomIcon(20));
        btn.getStyleClass().addAll("app-button", "prompt-action-btn");
        btn.setTooltip(new Tooltip("Open prompt in large editor"));
        btn.setOnAction(e -> openFullscreenEditor());
        return btn;
    }

    private void openFullscreenEditor() {
        PromptFullscreenDialog dialog =
                new PromptFullscreenDialog(promptTextArea.getText(), projectColor);
        dialog.showAndWait(getScene().getWindow(), Modality.NONE).ifPresent(result -> {
            promptTextArea.setText(result.text());
            if (result.send()) sendToTerminal();
        });
    }

    private void sendToTerminal() {
        String text = promptTextArea.getText().trim();
        if (text.isEmpty()) return;
        String escaped = text.replace("'", "'\\''");
        terminalSender.accept("'" + escaped + "'\n");
    }

    private void copyPromptToClipboard() {
        String text = promptTextArea.getText();
        if (text == null || text.isEmpty()) return;
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(
                java.util.Map.of(javafx.scene.input.DataFormat.PLAIN_TEXT, text));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getRelativePath(File file) {
        if (workDir == null) return file.getName();
        try {
            String bPath = workDir.getAbsoluteFile().toURI().getPath();
            String fPath = file.getAbsoluteFile().toURI().getPath();
            if (fPath.startsWith(bPath)) {
                String sub = fPath.substring(bPath.length());
                return sub.endsWith("/") ? sub.substring(0, sub.length() - 1) : sub;
            }
        } catch (Exception ignored) {}
        return file.getName();
    }

    /** Pin a button to its preferred width and disable horizontal growth so it
     *  never shrinks or clips when the containing HBox runs out of space —
     *  used for the prompt-header trailing buttons (mic/export/sessions/send)
     *  so only the template combo is responsive to width changes. */
    private static void pinButtonWidth(Button b) {
        b.setMinWidth(Region.USE_COMPUTED_SIZE);
        b.setMaxWidth(Region.USE_COMPUTED_SIZE);
        HBox.setHgrow(b, Priority.NEVER);
    }
}
