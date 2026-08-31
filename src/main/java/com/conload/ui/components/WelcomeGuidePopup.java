package com.conload.ui.components;

import com.conload.ui.Icons;
import com.conload.ui.Theme;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

/**
 * A modal 5-step welcome / onboarding guide shown on app start when the user
 * has no projects or an incomplete config. Each step covers one topic; a row
 * of indicator dots reflects progress. Narrow callback dependencies (no
 * controller injection): only the open-settings / open-projects / open-prompts
 * runnables and an onFinish callback.
 *
 * <p>Styling reuses the shared {@code .popup-window-root} + {@code .bg-app} +
 * {@code .card} CSS classes so the popup matches the rest of the app's dark
 * theme. Step dots use the {@code .welcome-step-dot} /
 * {@code .welcome-step-dot-active} classes defined in {@code theme.css}.
 */
public class WelcomeGuidePopup {

    private static final double WIDTH = 640;
    private static final double HEIGHT = 560;

    private final Stage stage;
    private final List<StepDef> steps;
    private int current = 0;

    private Label stepIndicatorLabel;
    private HBox stepDots;
    private VBox stepBody;
    private Button backBtn;
    private Button nextBtn;
    private Button skipBtn;
    private Button actionBtn;

    /** One wizard step: title, subtitle, body bullets, optional action button. */
    private record StepDef(String title, String subtitle, List<String> bullets,
                           String actionLabel, Runnable action) {}

    /**
     * Build the popup.
     *
     * @param owner           owning window (for modality/centering), may be {@code null}
     * @param onOpenSettings  opens the Settings/Config panel (step 2 action)
     * @param onOpenProjects  opens the Projects management screen (step 1 action)
     * @param onOpenPrompts   opens the Prompts library (step 4 action)
     * @param onFinish        invoked when the user clicks Finish on the last step
     */
    public WelcomeGuidePopup(Window owner, Runnable onOpenSettings, Runnable onOpenProjects,
                             Runnable onOpenPrompts, Runnable onFinish) {
        this.steps = List.of(
                step("Welcome to conload", "Create your first project",
                        List.of(
                                "Click the top-left 'Project' button → 'Open…' to use an existing folder as a project.",
                                "Or → 'Download Confluence…' to create a project straight from a Confluence page URL "
                                        + "(the project is created and the page tree downloaded in one step).",
                                "Or open the Projects screen to create a project for a cloned GitHub repo folder.",
                                "Each project gets its own terminal, file tree, context folders, and browser-style tab."),
                        "Open Projects", onOpenProjects),
                step("Configure API Access & Folders", "Mandatory settings to enable search & workflows",
                        List.of(
                                "Click the ⚙ Settings icon (top bar). Fill these MANDATORY fields, then click Save:",
                                "▸ Atlassian root URL — e.g. https://yoursite.atlassian.net (shared by Confluence & Jira).",
                                "▸ Email / username — the Atlassian account email used to generate the API token.",
                                "▸ Atlassian API token — create one at https://id.atlassian.com → Account → API tokens.",
                                "▸ GitHub personal access token — fine-grained, read access to your repos & orgs.",
                                "▸ GitHub REST API URL — https://api.github.com (or your GitHub Enterprise host).",
                                "Mandatory folders:",
                                "▸ Full Confluence folder — a local folder holding your full Confluence data; injected "
                                        + "into workflow prompt templates as ${confluenceDataSource}.",
                                "▸ Default export folder — where session transcripts and exports are written "
                                        + "(suggested: ~/conload-exports).",
                                "Optional: Terminal shell (zsh/bash/powershell) — blank = prompt on first terminal open.",
                                "All config lives in ~/.conload/config.txt — outside the repo, never committed."),
                        "Open Settings", onOpenSettings),
                step("Add Context", "Load content for your agent",
                        List.of(
                                "Inside a project, click 'Add Context' → 'Link Local Folder' to reference existing "
                                        + "folders, or 'Download Context' to fetch new content.",
                                "Download: search Confluence (CQL), Jira (JQL), and GitHub (PRs/commits) in one dialog, "
                                        + "then download everything as clean Markdown into a context_<name>/ folder.",
                                "Your agent reads these files directly — they're just Markdown on disk."),
                        null, null),
                step("Prompt Library", "Reusable templates & quick actions",
                        List.of(
                                "Click 'Prompts library' (top bar) to create reusable prompt templates.",
                                "Use ${variable} placeholders — they auto-render as input fields when you pick the "
                                        + "template from the prompt-bar combo.",
                                "Context-picker mode: click files in the tree to insert their paths into ${var} "
                                        + "placeholders.",
                                "Quick actions bar: one-click parameterized commands like 'git checkout ${branch}'."),
                        "Open Prompts", onOpenPrompts),
                step("Workflows", "Gather cross-source context in one click",
                        List.of(
                                "Pick a workflow from the dropdown in the prompt header (top-right of the prompt area).",
                                "Built-in: Prepare-to-Refinement (Jira key), Code Review (GitHub PR URL), Confluence "
                                        + "Reverse Engineering (page URL or keyword).",
                                "Fill the dynamic form, click 'Gather' — cross-source context is collected into one "
                                        + "folder and a ready-to-use prompt is loaded into the textarea.",
                                "Full Mode = deeper recursion + top-word discovery; Fast Mode = depth 1. Right-click a "
                                        + "context folder → 'Run Workflow' to regenerate the prompt later."),
                        "Finish", onFinish));
        this.stage = new Stage();
        if (owner != null) stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Welcome to conload");
        stage.setResizable(false);
        buildUI();
        renderStep();
        stage.setOnCloseRequest(e -> close());
    }

    private static StepDef step(String title, String subtitle, List<String> bullets,
                               String actionLabel, Runnable action) {
        return new StepDef(title, subtitle, bullets, actionLabel, action);
    }

    public void show() {
        stage.show();
    }

    /** Close the popup (also used by Skip / Finish / window close). */
    private void close() {
        stage.close();
    }

    // ── UI construction ──────────────────────────────────────────────────────

    private void buildUI() {
        stepIndicatorLabel = new Label();
        stepIndicatorLabel.getStyleClass().addAll("secondary", "small");

        stepDots = new HBox(6);
        stepDots.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < steps.size(); i++) {
            Label dot = new Label(Icons.UNCHECKED);
            dot.getStyleClass().addAll("welcome-step-dot", "icon");
            stepDots.getChildren().add(dot);
        }

        stepBody = new VBox(0);

        backBtn = UiFactory.actionButton("Back");
        backBtn.setOnAction(e -> nav(-1));

        nextBtn = UiFactory.accentButton("Next");
        nextBtn.setOnAction(e -> nav(1));

        skipBtn = UiFactory.errorButton("Skip");
        skipBtn.setOnAction(e -> close());

        actionBtn = UiFactory.accentButton("");
        actionBtn.setVisible(false);
        actionBtn.setManaged(false);

        HBox footer = UiFactory.footerRow(skipBtn, UiFactory.hSpacer(), backBtn, nextBtn, actionBtn);

        VBox root = new VBox(0, buildHeader(), new Separator(), stepBody, new Separator(), footer);
        root.getStyleClass().addAll("bg-app", "popup-window-root", "welcome-guide-root");
        root.setMinSize(WIDTH, HEIGHT);
        root.setPrefSize(WIDTH, HEIGHT);

        Scene scene = new Scene(root);
        Theme.apply(root);
        stage.setScene(scene);
    }

    private HBox buildHeader() {
        Label hero = new Label(Icons.SPARKLE + "  conload — Quick Start");
        hero.getStyleClass().addAll("title");
        HBox header = new HBox(12, hero, UiFactory.hSpacer(), stepIndicatorLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 22, 10, 22));
        header.getStyleClass().add("panel-border-bottom");
        return header;
    }

    private void renderStep() {
        StepDef s = steps.get(current);
        stepIndicatorLabel.setText("Step " + (current + 1) + " / " + steps.size());

        for (int i = 0; i < stepDots.getChildren().size(); i++) {
            Label dot = (Label) stepDots.getChildren().get(i);
            dot.getStyleClass().removeAll("welcome-step-dot-active");
            dot.setText(i <= current ? Icons.CHECKED : Icons.UNCHECKED);
            if (i == current) dot.getStyleClass().add("welcome-step-dot-active");
        }

        stepBody.getChildren().setAll(buildStepContent(s));

        backBtn.setDisable(current == 0);

        boolean last = current == steps.size() - 1;
        nextBtn.setText(last ? "Finish" : "Next");

        boolean hasAction = s.actionLabel() != null && !s.actionLabel().isBlank();
        if (hasAction) {
            actionBtn.setText(s.actionLabel());
            actionBtn.setVisible(true);
            actionBtn.setManaged(true);
            actionBtn.setOnAction(e -> {
                close();
                if (s.action() != null) s.action().run();
            });
            // The contextual action is the primary CTA; Next demotes to secondary look.
            nextBtn.getStyleClass().removeAll("accent");
        } else {
            actionBtn.setVisible(false);
            actionBtn.setManaged(false);
            if (!nextBtn.getStyleClass().contains("accent")) nextBtn.getStyleClass().add("accent");
        }
    }

    private ScrollPane buildStepContent(StepDef s) {
        VBox content = new VBox(8);
        content.setPadding(new Insets(16, 24, 16, 24));

        Label title = new Label(s.title());
        title.getStyleClass().addAll("title");
        title.setWrapText(true);

        Label subtitle = new Label(s.subtitle());
        subtitle.getStyleClass().addAll("secondary", "small");
        subtitle.setWrapText(true);
        subtitle.setPadding(new Insets(0, 0, 4, 0));

        Label dotsLine = new Label();
        dotsLine.setGraphic(stepDots);
        dotsLine.setPadding(new Insets(2, 0, 4, 0));

        content.getChildren().addAll(title, subtitle, dotsLine);
        for (String bullet : s.bullets()) content.getChildren().add(bulletRow(bullet));

        ScrollPane scroll = UiFactory.scrollable(content);
        scroll.setFitToHeight(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private HBox bulletRow(String text) {
        HBox row = new HBox(8);
        Label dot = new Label(Icons.BULLET);
        dot.getStyleClass().addAll("small", "icon");
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.getStyleClass().addAll("secondary");
        HBox.setHgrow(lbl, Priority.ALWAYS);
        row.getChildren().addAll(dot, lbl);
        row.setPadding(new Insets(3, 0, 3, 0));
        return row;
    }

    private void nav(int delta) {
        int next = current + delta;
        if (next < 0) return;
        if (next >= steps.size()) {
            StepDef last = steps.get(current);
            if (last.action() != null) last.action().run();
            close();
            return;
        }
        current = next;
        renderStep();
    }
}
