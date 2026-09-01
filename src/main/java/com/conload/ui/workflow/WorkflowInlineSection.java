package com.conload.ui.workflow;

import com.conload.ui.Icons;
import com.conload.ui.Theme;
import com.conload.ui.components.CrossContextIcon;
import com.conload.ui.components.UiFactory;
import com.conload.util.FileUtil;
import com.conload.util.Json;
import com.conload.workflow.Workflow;
import com.conload.workflow.WorkflowCallbacks;
import com.conload.workflow.WorkflowContext;
import com.conload.workflow.WorkflowEnvironment;
import com.conload.workflow.WorkflowInputs;
import com.conload.workflow.WorkflowRegistry;
import com.conload.workflow.GitRemoteResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Inline replacement for the former {@code WorkflowDialog} popup. Owns the
 * workflow selector combo (placed in the prompt header by the host), a dynamic
 * input form, gather/cancel buttons, and a progress + log area.
 * <p>
 * The section is hidden by default. When the user selects a workflow from the
 * combo, the form appears <b>above the prompt textarea</b>. After context is
 * gathered, the substituted prompt is pushed into the shared prompt panel and
 * this section collapses so the textarea is fully visible.
 */
public final class WorkflowInlineSection extends VBox {

    private final WorkflowHost host;

    private ComboBox<String> workflowCombo;
    private Label descriptionLabel;
    private VBox formContainer;
    private WorkflowFormBuilder.FormResult currentForm;
    private Workflow selectedWorkflow;

    private TextArea logArea;
    private Label progressLabel;
    private ProgressIndicator spinner;
    private VBox gatherSection;
    private Button gatherBtn;
    private Button cancelBtn;
    private volatile boolean gatheringCancelled = false;

    /** Recoverable per-item errors collected during the current gather pass
     *  (API failures, download errors). Reset on each {@link #startGathering}. */
    private final ConcurrentLinkedQueue<String> gatherErrors = new ConcurrentLinkedQueue<>();

    /** Creates the section. Call {@link #getSelector()} to obtain the combo for
     *  header placement, then add this section itself to the layout. */
    public WorkflowInlineSection(WorkflowHost host) {
        this.host = host;
        setSpacing(6);
        setPadding(new Insets(4, 10, 4, 10));
        getStyleClass().add("workflow-inline-section");
        buildContent();
        UiFactory.hide(this);
    }

    /** Returns the workflow selector combo — placed by the host in the prompt
     *  header row (after the mic button, before send). */
    public ComboBox<String> getSelector() {
        return workflowCombo;
    }

    // ── Build ──────────────────────────────────────────────────────────────

    private void buildContent() {
        workflowCombo = new ComboBox<>();
        for (Workflow w : WorkflowRegistry.all()) {
            workflowCombo.getItems().add(w.displayName());
        }
        workflowCombo.setPromptText("Workflow…");
        workflowCombo.setOnAction(e -> onWorkflowSelected());
        workflowCombo.getStyleClass().add("workflow-combo");

        formContainer = new VBox(8);
        formContainer.setPadding(new Insets(4, 0, 4, 0));

        gatherSection = buildGatherSection();
        UiFactory.hide(gatherSection);

        HBox actionRow = buildActionRow();

        getChildren().addAll(buildSectionHeader(), formContainer, gatherSection, actionRow);
    }

    private HBox buildSectionHeader() {
        descriptionLabel = new Label("");
        descriptionLabel.getStyleClass().addAll("hint", "small");
        descriptionLabel.setWrapText(true);
        HBox.setHgrow(descriptionLabel, Priority.ALWAYS);
        descriptionLabel.setMaxWidth(Double.MAX_VALUE);

        Button closeBtn = new Button(Icons.CLOSE);
        Theme.classes(closeBtn, Theme.CL_CLOSE_BTN);
        closeBtn.setMinWidth(Region.USE_COMPUTED_SIZE);
        closeBtn.setMaxWidth(Region.USE_COMPUTED_SIZE);
        closeBtn.setTooltip(new Tooltip("Close workflow section"));
        closeBtn.setOnAction(e -> collapse());

        HBox header = new HBox(6, descriptionLabel, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox buildGatherSection() {
        VBox box = new VBox(6);

        HBox progressRow = new HBox(8);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        spinner = new ProgressIndicator();
        spinner.setPrefSize(16, 16);
        spinner.getStyleClass().add("progress-accent");
        UiFactory.hide(spinner);
        progressLabel = new Label("");
        progressLabel.getStyleClass().add("secondary");
        progressRow.getChildren().addAll(spinner, progressLabel);

        logArea = new TextArea();
        logArea.setWrapText(true);
        logArea.setPrefRowCount(5);
        logArea.setEditable(false);
        logArea.getStyleClass().addAll("input", "text-area");
        logArea.setPromptText("Gather cross -context log will appear here…");

        box.getChildren().addAll(progressRow, logArea);
        return box;
    }

    private HBox buildActionRow() {
        gatherBtn = UiFactory.accentButton(" Gather cross-context");
        gatherBtn.setGraphic(new CrossContextIcon(16));
        gatherBtn.setContentDisplay(ContentDisplay.LEFT);
        gatherBtn.setDisable(true);
        gatherBtn.setOnAction(e -> startGathering());

        cancelBtn = UiFactory.errorButton("Cancel");
        cancelBtn.setDisable(true);
        UiFactory.hide(cancelBtn);
        cancelBtn.setOnAction(e -> {
            gatheringCancelled = true;
            cancelBtn.setDisable(true);
        });

        HBox row = new HBox(8, gatherBtn, cancelBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ── Selection ──────────────────────────────────────────────────────────

    private void onWorkflowSelected() {
        int idx = workflowCombo.getSelectionModel().getSelectedIndex();
        List<Workflow> all = WorkflowRegistry.all();
        selectedWorkflow = (idx >= 0 && idx < all.size()) ? all.get(idx) : null;

        formContainer.getChildren().clear();
        currentForm = null;
        UiFactory.hide(gatherSection);

        if (selectedWorkflow == null) {
            UiFactory.hide(this);
            return;
        }

        Map<String, String> prefill = new LinkedHashMap<>();
        String autoRepo = GitRemoteResolver.resolveOwnerRepo(host.workspacePath());
        prefill.put("githubOwnerRepo", autoRepo);

        currentForm = WorkflowFormBuilder.build(selectedWorkflow.inputFields(), prefill);
        formContainer.getChildren().add(currentForm.container());

        descriptionLabel.setText(selectedWorkflow.description());

        gatherBtn.setDisable(false);
        UiFactory.show(this);
    }

    // ── Gathering ───────────────────────────────────────────────────────────

    private void startGathering() {
        if (selectedWorkflow == null || currentForm == null) return;
        if (host.activeProjectId() == null) {
            new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.WARNING,
                    "Open a project first — the workflow needs an active project workspace.")
                    .showAndWait();
            return;
        }

        logArea.clear();
        UiFactory.show(gatherSection);
        gatherBtn.setDisable(true);
        UiFactory.show(cancelBtn);
        cancelBtn.setDisable(false);
        UiFactory.show(spinner);
        gatheringCancelled = false;
        gatherErrors.clear();
        progressLabel.getStyleClass().remove("workflow-progress-warning");
        host.showTaskBadge("Gathering…", true);

        WorkflowEnvironment env = new WorkflowEnvironment(
                host.config(), host.githubToken(), host.githubApiUrl(), host.workspacePath(),
                host.contextsDir(), host.activeProjectId(),
                host.fullConfluenceFolder());

        WorkflowInputs inputs = new WorkflowInputs(currentForm.valuesSupplier().get());

        WorkflowCallbacks callbacks = new WorkflowCallbacks() {
            @Override public void onProgress(String stage, String message) {
                Platform.runLater(() -> progressLabel.setText("[" + stage + "] " + message));
            }
            @Override public void onLog(String line) {
                Platform.runLater(() -> logArea.appendText(line + "\n"));
            }
            @Override public void onError(String stage, String message) {
                String line = "[ERROR] [" + stage + "] " + message;
                gatherErrors.add("[" + stage + "] " + message);
                Platform.runLater(() -> logArea.appendText(line + "\n"));
            }
            @Override public boolean isCancelled() {
                return gatheringCancelled;
            }
        };

        Thread thread = Thread.ofVirtual().name("workflow-gather", 0).unstarted(() -> {
            try {
                WorkflowContext ctx = selectedWorkflow.accumulate(env, inputs, callbacks);
                Platform.runLater(() -> onGatherComplete(ctx));
            } catch (Throwable t) {
                Platform.runLater(() -> onGatherFailed(t));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void onGatherComplete(WorkflowContext ctx) {
        UiFactory.hide(spinner);
        cancelBtn.setDisable(true);
        UiFactory.hide(cancelBtn);
        gatherBtn.setDisable(false);

        String summary = countContextSummary(ctx.contextRoot().toFile());
        int errorCount = gatherErrors.size();
        int totalFiles = ctx.contextRoot().toFile().exists() ? countRawFiles(ctx.contextRoot().toFile()) : 0;

        writeWorkflowMetadata(ctx);

        String prompt = selectedWorkflow.buildPrompt(ctx);
        host.setPromptText(prompt);

        try {
            host.registerContext(ctx.contextRoot().toAbsolutePath().toString());
        } catch (Exception ignored) { /* best-effort */ }

        if (errorCount > 0) {
            progressLabel.getStyleClass().add("workflow-progress-warning");
            progressLabel.setText(Icons.WARNING + " Gathered: " + summary
                    + " — " + errorCount + " error(s) occurred; see log below.");
            host.hideTaskBadge();
            // Keep the section + log visible so the user can read the errors.
        } else if (totalFiles == 0) {
            progressLabel.getStyleClass().add("workflow-progress-warning");
            progressLabel.setText(Icons.WARNING + " Gathered 0 files — nothing was downloaded; "
                    + "check inputs/credentials and the log below.");
            host.hideTaskBadge();
            // Nothing useful was gathered; keep the section open.
        } else {
            progressLabel.setText(Icons.CHECK + " Gathered: " + summary + " — prompt loaded below.");
            host.showTaskBadge(Icons.CHECK + " Cross-context ready", false);
            UiFactory.hide(this);
        }
    }

    /** Write {@code workflow-info.json} into the context root so the folder
     *  can be re-run from the file-tree context menu later. */
    private void writeWorkflowMetadata(WorkflowContext ctx) {
        try {
            ObjectNode node = Json.MAPPER.createObjectNode();
            node.put("workflowId", selectedWorkflow.id());
            node.put("workflowName", selectedWorkflow.displayName());
            node.put("jiraKeys", ctx.jiraKeys() != null ? ctx.jiraKeys() : "");
            node.put("repoOwnerRepo", ctx.repoOwnerRepo() != null ? ctx.repoOwnerRepo() : "");
            node.put("confluenceDataSource", ctx.confluenceDataSource() != null ? ctx.confluenceDataSource() : "");
            node.put("workspacePath", ctx.workspacePath() != null ? ctx.workspacePath() : "");
            // The output doc lives in the shared contexts dir under
            // doc/cross_context (outside the context folder), so store the
            // absolute path (the re-run path resolves it directly, falling
            // back to a relative path for legacy gathered folders whose doc
            // lived under contextRoot).
            String outputDoc = ctx.outputDocPath() != null
                    ? ctx.outputDocPath().toAbsolutePath().toString() : "";
            node.put("outputDocPath", outputDoc);
            node.put("gatheredAt", java.time.Instant.now().toString());
            node.put("type", "workflow");
            String json = Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            FileUtil.writeText(ctx.contextRoot().resolve("workflow-info.json"), json);
        } catch (Exception e) {
            logArea.appendText("[WARN] Failed to write workflow-info.json: " + e.getMessage() + "\n");
        }
    }

    /** Recursively count files in the context tree, categorizing by type. */
    private String countContextSummary(File root) {
        int[] counts = new int[4]; // total, jira, confluence, commits
        walkContext(root, counts);
        return counts[0] + " files, " + counts[1] + " Jira, "
                + counts[2] + " Confluence, " + counts[3] + " commits";
    }

    /** Total raw file count under {@code root} (any type). Used to detect a
     *  clean-but-empty gather (e.g. bad credentials returning no results). */
    private int countRawFiles(File root) {
        int[] counts = new int[4];
        walkContext(root, counts);
        return counts[0];
    }

    private void walkContext(File file, int[] counts) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return;
            for (File child : children) walkContext(child, counts);
        } else {
            counts[0]++;
            String name = file.getName();
            if (name.startsWith("jira_") && name.endsWith(".md")) counts[1]++;
            else if (name.startsWith("confluence_") || name.startsWith("conf_")) counts[2]++;
            else if (name.startsWith("commits_") && name.endsWith(".json")) counts[3]++;
        }
    }

    private void onGatherFailed(Throwable t) {
        UiFactory.hide(spinner);
        cancelBtn.setDisable(true);
        UiFactory.hide(cancelBtn);
        gatherBtn.setDisable(false);
        host.hideTaskBadge();
        progressLabel.setText("✗ Failed: " + t.getMessage());
        logArea.appendText("\nERROR: " + t.getMessage() + "\n");
        for (StackTraceElement e : t.getStackTrace()) {
            logArea.appendText("  at " + e + "\n");
        }
    }

    /** Collapse the section (hide it). */
    public void collapse() {
        UiFactory.hide(this);
    }

    /** Show the section (un-hide it). */
    public void show() {
        UiFactory.show(this);
    }

    /** Full reset: clear combo selection, clear form, hide section.
     *  Used by the host when the user selects a prompt template so the
     *  prompt and cross-context fields are never shown together. */
    public void reset() {
        workflowCombo.getSelectionModel().clearSelection();
        selectedWorkflow = null;
        formContainer.getChildren().clear();
        currentForm = null;
        descriptionLabel.setText("");
        UiFactory.hide(gatherSection);
        gatherBtn.setDisable(true);
        UiFactory.hide(this);
    }
}
