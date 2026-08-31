package com.conload.ui.prompttemplate.dialog;

import com.conload.model.QuickAction;
import com.conload.ui.DialogStyler;
import com.conload.ui.Icons;
import com.conload.ui.Theme;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Editor for a single workflow prompt template. The "always-used"
 * {@code ${variable}} tokens are rendered as non-removable chips; only the
 * prose between them is editable. The chip structure (count and order) is
 * derived from the <em>bundled</em> template — the source of truth — so a
 * variable can never be deleted, only the surrounding text edited.
 * <p>
 * This dialog performs no persistence: it returns the reconstructed template
 * text (chips + edited prose) on OK, or {@code null} on cancel. The caller
 * decides whether to persist an override or reset to the bundled default.
 * <p>
 * "Reset to default" reloads the bundled template into the editor (in-memory
 * only); if the user then saves and the result equals the bundled template,
 * the caller clears the override.
 */
public class WorkflowTemplateEditorDialog extends Dialog<String> {

    private static final Pattern VAR = Pattern.compile("\\$\\{(\\w+)}");

    private final String bundledTemplate;
    private final String effectiveText;

    private VBox editorBox;
    private final List<TextArea> proseAreas = new ArrayList<>();
    private final List<String> chipTokens = new ArrayList<>();

    /**
     * @param workflowName  display name (shown in the title)
     * @param description   workflow description (shown under the title)
     * @param bundledTemplate  the bundled template text (source of truth for chips)
     * @param effectiveText     the current effective text (override if present, else bundled)
     * @param width / height    dialog size
     */
    public WorkflowTemplateEditorDialog(String workflowName, String description,
                                        String bundledTemplate, String effectiveText,
                                        double width, double height) {
        this.bundledTemplate = bundledTemplate;
        this.effectiveText = effectiveText;

        setTitle("Edit Workflow Template — " + workflowName);
        setHeaderText("Variables are locked (not removable). Edit the text around them.");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setPrefSize(width, height);

        buildContent(description);
        loadFromEffective();

        setResultConverter(button -> button == ButtonType.OK ? reconstruct() : null);
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, this::rejectInvalid);
    }

    /** Shows this editor with the requested owner and modality. */
    public java.util.Optional<String> showAndWait(Window owner, Modality modality) {
        if (owner != null) initOwner(owner);
        if (modality != null) initModality(modality);
        DialogStyler.style(this);
        return super.showAndWait();
    }

    // ── Content construction ────────────────────────────────────────────────

    private void buildContent(String description) {
        List<String> requiredVars = QuickAction.extractParameters(bundledTemplate);
        Label varsLabel = new Label("Locked variables: "
                + (requiredVars.isEmpty() ? "(none)"
                    : requiredVars.stream().map(v -> "${" + v + "}")
                            .collect(Collectors.joining("   "))));
        varsLabel.getStyleClass().addAll("secondary", "small");
        varsLabel.setWrapText(true);

        Button resetBtn = new Button(Icons.REFRESH + " Reset to default");
        resetBtn.getStyleClass().addAll("app-button", "secondary");
        resetBtn.setTooltip(new Tooltip("Reload the bundled template (variables stay locked)"));
        resetBtn.setOnAction(e -> loadFromBundled());

        Region spacer = hSpacer();
        HBox headerRow = new HBox(8, varsLabel, spacer, resetBtn);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        editorBox = new VBox(6);
        editorBox.setPadding(new Insets(2, 0, 2, 0));
        Theme.classes(editorBox, Theme.CL_BG_APP);

        ScrollPane scroll = new ScrollPane(editorBox);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("scroll-transparent");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        List<javafx.scene.Node> children = new ArrayList<>();
        if (description != null && !description.isBlank()) {
            Label desc = new Label(description);
            desc.getStyleClass().addAll("secondary", "small");
            desc.setWrapText(true);
            children.add(desc);
        }
        children.add(varsLabel);
        children.add(headerRow);
        children.add(scroll);

        VBox form = new VBox(8, children.toArray(new javafx.scene.Node[0]));
        form.setPadding(new Insets(12, 14, 8, 14));
        Theme.classes(form, Theme.CL_BG_APP);
        getDialogPane().setContent(form);
    }

    /** A growable horizontal spacer (avoids importing UiFactory here). */
    private static javafx.scene.layout.Region hSpacer() {
        javafx.scene.layout.Region r = new javafx.scene.layout.Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    // ── Segment splitting + editor rendering ───────────────────────────────

    /**
     * Split {@code text} into alternating editable prose segments and
     * non-removable {@code ${var}} chip tokens. {@code chipsOut} receives the
     * chip tokens in order; the returned list has size {@code chipsOut.size()+1}.
     */
    private static List<String> split(String text, List<String> chipsOut) {
        List<String> prose = new ArrayList<>();
        if (text == null) text = "";
        Matcher m = VAR.matcher(text);
        int last = 0;
        while (m.find()) {
            prose.add(text.substring(last, m.start()));
            chipsOut.add(m.group());
            last = m.end();
        }
        prose.add(text.substring(last));
        return prose;
    }

    /** (Re)build the alternating prose-area / chip-row editors from {@code prose}. */
    private void buildEditorAreas(List<String> prose) {
        proseAreas.clear();
        editorBox.getChildren().clear();
        int chips = chipTokens.size();
        for (int i = 0; i < prose.size(); i++) {
            TextArea ta = new TextArea(prose.get(i));
            ta.setWrapText(true);
            ta.setPrefRowCount(prefRows(prose.get(i)));
            ta.getStyleClass().addAll("input", "text-area");
            VBox.setVgrow(ta, Priority.ALWAYS);
            proseAreas.add(ta);
            editorBox.getChildren().add(ta);
            if (i < chips) {
                Label chip = new Label(chipTokens.get(i));
                chip.getStyleClass().addAll("workflow-var-chip", "bold");
                chip.setMaxWidth(Double.MAX_VALUE);
                HBox chipRow = new HBox(chip);
                chipRow.setAlignment(Pos.CENTER_LEFT);
                chipRow.setPadding(new Insets(1, 0, 1, 0));
                editorBox.getChildren().add(chipRow);
            }
        }
    }

    /** Reconstruct the full template text from the edited prose + fixed chips. */
    private String reconstruct() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < proseAreas.size(); i++) {
            sb.append(proseAreas.get(i).getText());
            if (i < chipTokens.size()) sb.append(chipTokens.get(i));
        }
        return sb.toString();
    }

    // ── Loaders ─────────────────────────────────────────────────────────────

    /** Pre-fill the editor from the effective text, aligned to the bundled chip structure. */
    private void loadFromEffective() {
        List<String> bundledChips = new ArrayList<>();
        List<String> bundledProse = split(bundledTemplate, bundledChips);
        List<String> effChips = new ArrayList<>();
        List<String> effProse = split(effectiveText, effChips);
        chipTokens.clear();
        chipTokens.addAll(bundledChips);
        // Use the override's prose only when its chip sequence matches the
        // bundled structure (i.e. the override was produced by this editor);
        // otherwise fall back to the bundled prose to guarantee a valid layout.
        buildEditorAreas(effChips.equals(bundledChips) ? effProse : bundledProse);
    }

    /** Reload the bundled template into the editor (in-memory "reset"). */
    private void loadFromBundled() {
        List<String> chips = new ArrayList<>();
        List<String> prose = split(bundledTemplate, chips);
        chipTokens.clear();
        chipTokens.addAll(chips);
        buildEditorAreas(prose);
    }

    // ── Validation ──────────────────────────────────────────────────────────

    private void rejectInvalid(ActionEvent event) {
        if (reconstruct().isBlank()) event.consume();
    }

    private static int prefRows(String s) {
        if (s == null || s.isEmpty()) return 2;
        long lines = s.chars().filter(ch -> ch == '\n').count();
        return (int) Math.min(40, Math.max(2, lines + 1));
    }
}
