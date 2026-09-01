package com.conload.ui.createcontext;

import com.conload.ui.Icons;
import com.conload.ui.Theme;
import com.conload.ui.components.CrossContextIcon;
import com.conload.ui.components.UiFactory;
import com.conload.ui.workflow.WorkflowHost;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The "Cross Context" section rendered at the bottom of the search popup. Lets
 * the user pick one of three recursive multi-source seeds (Jira / Confluence /
 * GitHub PR), enter a single value, enable Full Mode, and gather straight to
 * disk via the unified {@code CrossContextBuilder} (reused through the
 * registered workflows — no flat-search result rows are involved).
 *
 * <p>On completion the new context folder is registered with the active project
 * (which refreshes the file tree) and the search popup is closed. No prompt is
 * pushed — this path is context-only, mirroring the download-context flow.</p>
 */
public final class CrossContextSection {

    private static final String INFO_TEXT = """
            Cross Context recursively gathers from Jira, Confluence and GitHub
            in one pass, following links between sources (Jira ↔ Confluence
            ↔ commits) with global de-duplication. It writes the gathered
            files plus a cross_context_hierarchy.md map into the project's
            contexts folder — no manual selection needed.""";

    private final WorkflowHost host;
    private final GatherCallback onGather;

    private final ComboBox<CrossSource> sourceBox = new ComboBox<>();
    private final TextField valueField = UiFactory.darkTextField("");
    private final CheckBox fullModeBox = new CheckBox("Full Mode");
    private final Button gatherBtn = UiFactory.accentButton("Gather cross -context");
    private final Label statusLabel = new Label();
    private final VBox view;

    @FunctionalInterface
    public interface GatherCallback {
        void gather(CrossSource source, String seed, boolean fullMode);
    }

    public CrossContextSection(WorkflowHost host, GatherCallback onGather) {
        this.host = host;
        this.onGather = onGather;
        this.view = build();
    }

    public VBox view() { return view; }

    private VBox build() {
        Label title = new Label(Icons.SPARKLE + "  Cross Context (recursive)");
        Theme.classes(title, Theme.CL_TITLE_SMALL);

        Button info = new Button(Icons.INFO);
        info.getStyleClass().addAll("icon-button", "secondary");
        info.setTooltip(new Tooltip(INFO_TEXT));

        HBox header = new HBox(6, title, info);
        header.setAlignment(Pos.CENTER_LEFT);

        sourceBox.getItems().addAll(CrossSource.values());
        sourceBox.setValue(CrossSource.JIRA);
        sourceBox.setPrefWidth(165);
        sourceBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(CrossSource s) {
                if (s == null) return "";
                return switch (s) {
                    case JIRA -> "From Jira";
                    case CONFLUENCE -> "From Confluence";
                    case GITHUB -> "From GitHub";
                };
            }
            @Override public CrossSource fromString(String v) { return null; }
        });
        sourceBox.setOnAction(e -> valueField.setPromptText(sourceBox.getValue().prompt()));

        valueField.setPromptText(CrossSource.JIRA.prompt());
        HBox.setHgrow(valueField, Priority.ALWAYS);

        fullModeBox.setSelected(true);
        fullModeBox.setTooltip(new Tooltip("Deep recursion + epic children + word-frequency discovery"));

        gatherBtn.setGraphic(new CrossContextIcon(16));
        gatherBtn.setContentDisplay(ContentDisplay.LEFT);
        gatherBtn.setOnAction(e -> startGather());

        statusLabel.getStyleClass().add("small");

        HBox controls = new HBox(8, sourceBox, valueField, fullModeBox, gatherBtn);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(Theme.PAD_ROW_TIGHT);

        VBox box = new VBox(4, header, controls, statusLabel);
        box.getStyleClass().add("cross-context-section");
        box.setPadding(new Insets(10, 14, 10, 14));
        return box;
    }

    private void startGather() {
        if (host.activeProjectId() == null) {
            new Alert(Alert.AlertType.WARNING,
                    "Open a project first — cross-context needs an active project workspace.")
                    .showAndWait();
            return;
        }
        CrossSource source = sourceBox.getValue();
        String seed = valueField.getText().strip();
        if (seed.isBlank()) {
            statusLabel.setText(Icons.WARNING + "  Enter a value for " + titleOf(source));
            return;
        }
        onGather.gather(source, seed, fullModeBox.isSelected());
    }

    private static String titleOf(CrossSource s) {
        return switch (s) {
            case JIRA -> "Jira";
            case CONFLUENCE -> "Confluence";
            case GITHUB -> "GitHub";
        };
    }
}
