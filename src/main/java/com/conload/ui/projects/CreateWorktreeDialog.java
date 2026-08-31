package com.conload.ui.projects;

import com.conload.service.GitWorktreeService;
import com.conload.ui.DialogStyler;
import com.conload.ui.Icons;
import com.conload.ui.components.UiFactory;
import com.conload.util.BackgroundTasks;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;

/**
 * Modal dialog for creating a new git worktree (the "Create new worktree"
 * popup). Mirrors the proposed layout:
 * <pre>
 *   [✓] Pull latest branches   (fetch all remote branches — on by default)
 *   Select branch            [ feature/pgvk-refactor ▾ ]
 *   Worktree name (optional) [ pgvk-refactor           ]
 *   Target path              [ ~/Dev/.../einheiten-service-pgvk-refactor ]
 *                              [ Cancel ]  [ Create ]
 * </pre>
 * <p>On open it asynchronously {@code git fetch --all}s (when the checkbox is
 * on) and fills the branch combo from {@code git branch -r}. {@code Create}
 * returns the selected branch + target path; the actual
 * {@code git worktree add} is executed by the host controller on a background
 * thread (this dialog never runs the mutating git command).
 */
public class CreateWorktreeDialog extends Dialog<CreateWorktreeDialog.Result> {

    /** The user's selections: the branch to check out + the target path. */
    public record Result(String branch, String targetPath) {}

    private final GitWorktreeService gitSvc;
    private final Path repoDir;

    private final CheckBox fetchBox = new CheckBox("Pull latest branches");
    private final BranchAutoCompleteField branchField = new BranchAutoCompleteField();
    private final TextField nameField = UiFactory.darkTextField("");
    private final TextField targetField = UiFactory.darkTextField("");
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Label statusLabel = new Label();
    private boolean targetEdited = false;
    private List<String> allBranches = List.of();

    public CreateWorktreeDialog(Stage owner, GitWorktreeService gitSvc, Path repoDir) {
        this.gitSvc = gitSvc;
        this.repoDir = repoDir;
        setTitle("Create new worktree");
        DialogStyler.style(this);
        initOwner(owner);

        fetchBox.setSelected(true);
        fetchBox.getStyleClass().add("small");
        Label fetchHint = new Label("Fetch all remote branches so the list is current");
        fetchHint.getStyleClass().addAll("hint", "small");

        // Branch picker: a reliable autocomplete (TextField + Popup +
        // ListView). The previous editable-ComboBox + mutate-items + show()
        // pattern silently failed to display the suggestion dropdown.
        TextField branchEditor = branchField.editor();
        branchEditor.setMaxWidth(Double.MAX_VALUE);
        branchEditor.setDisable(true);
        branchField.padLikeCombo();
        branchField.valueProperty().addListener((obs, o, n) -> onBranchChanged(n));

        nameField.setPromptText("auto-filled from the branch name");
        nameField.textProperty().addListener((obs, o, n) -> regenerateTarget());
        targetField.textProperty().addListener((obs, o, n) -> targetEdited = true);

        spinner.setPrefSize(14, 14);
        spinner.setMaxSize(14, 14);
        spinner.setVisible(false);
        HBox comboRow = new HBox(6, branchEditor, spinner);
        HBox.setHgrow(branchEditor, Priority.ALWAYS);
        comboRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel.getStyleClass().addAll("small", "error");
        statusLabel.setWrapText(true);
        statusLabel.setVisible(false);

        VBox content = new VBox(10,
                fetchBox, fetchHint,
                UiFactory.fieldLabel("SELECT BRANCH"), comboRow,
                UiFactory.fieldLabel("WORKTREE NAME (OPTIONAL)"), nameField,
                UiFactory.fieldLabel("TARGET PATH"), targetField,
                statusLabel);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("bg-panel");
        content.setPrefWidth(620);

        DialogPane pane = getDialogPane();
        pane.setContent(content);
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        // Rename OK → Create + disable until a branch is selected.
        Button createBtn = (Button) pane.lookupButton(ButtonType.OK);
        createBtn.setText(Icons.CHECK + " Create");
        createBtn.setDisable(true);
        branchField.valueProperty().addListener((obs, o, n) ->
                createBtn.setDisable(n == null || n.isBlank()));

        setResultConverter(bt -> bt == ButtonType.OK ? buildResult() : null);
        setOnShowing(e -> populateBranches());
    }

    /** Fetches (when enabled) + lists remote branches on a background thread,
     *  then fills the autocomplete field and selects the first entry. */
    private void populateBranches() {
        spinner.setVisible(true);
        statusLabel.setVisible(false);
        BackgroundTasks.<List<String>>runOnFxThread("wt-branch-fetch",
            () -> {
                if (fetchBox.isSelected()) {
                    try { gitSvc.fetchAll(repoDir); }
                    catch (Exception fe) { /* non-fatal: list anyway */ }
                }
                return gitSvc.listRemoteBranches(repoDir);
            },
            branches -> {
                allBranches = branches;
                branchField.setBranches(branches);
                if (!branches.isEmpty()) {
                    branchField.setDisable(false);
                    branchField.selectFirst();
                    branchField.requestFocus();
                } else {
                    statusLabel.setText("No remote branches found.");
                    statusLabel.setVisible(true);
                }
            },
            ex -> {
                statusLabel.setText("⚠ " + ex.getMessage());
                statusLabel.setVisible(true);
            },
            () -> spinner.setVisible(false));
    }

    /** When the branch changes, always sync the worktree name to the branch's
     *  last path segment (so picking {@code feature/pgvk-refactor} fills
     *  {@code pgvk-refactor}). The user can still type a custom name — but
     *  picking a different branch re-syncs it. */
    private void onBranchChanged(String branch) {
        if (branch == null || branch.isBlank()) return;
        String last = branch.contains("/")
                ? branch.substring(branch.lastIndexOf('/') + 1) : branch;
        nameField.setText(last);
        regenerateTarget();
    }

    /** Recompute the default target path = {@code <repo parent>/<repoName>-<name>}
     *  unless the user has manually edited it. */
    private void regenerateTarget() {
        if (targetEdited) return;
        String branch = branchField.getValue();
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String segment;
        if (!name.isBlank()) {
            segment = name;
        } else if (branch != null && !branch.isBlank()) {
            segment = branch.contains("/") ? branch.substring(branch.lastIndexOf('/') + 1) : branch;
        } else {
            segment = "worktree";
        }
        String repoName = (repoDir.getFileName() != null) ? repoDir.getFileName().toString() : "repo";
        Path parent = repoDir.getParent();
        Path target = (parent != null)
                ? parent.resolve(repoName + "-" + segment)
                : Path.of(repoName + "-" + segment);
        targetField.setText(target.toString());
        targetEdited = false;
    }

    private Result buildResult() {
        String branch = branchField.getValue();
        String target = targetField.getText();
        if (branch == null || branch.isBlank() || target == null || target.isBlank()) return null;
        return new Result(branch, target.trim());
    }
}
