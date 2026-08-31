package com.conload.ui;

import com.conload.ui.Icons;
import com.conload.ui.components.UiFactory;
import com.conload.ui.components.AboutPanel;
import com.conload.ui.components.WelcomeGuidePopup;
import com.conload.ui.projects.ProjectFilesPane;
import com.conload.ui.projects.ProjectWorkspaceController;
import com.conload.ui.prompttemplate.PromptTemplatePanel;
import com.conload.ui.prompttemplate.QuickActionsBar;
import com.conload.ui.shell.FocusModeController;

import com.conload.model.AppConfig;
import com.conload.service.ConfigService;
import com.conload.service.OpenTabsService;
import com.conload.service.SpeechRecognitionService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.*;
import javafx.geometry.Rectangle2D;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class AppShellController extends ProjectWorkspaceController {

    private final FocusModeController focusModeController;
    /** Red banner shown at the top of the app while config is incomplete. */
    private HBox configBanner;

    // ── Speech-to-text (Vosk) ──────────────────────────────────────────────────
    // (speechService moved to MainControllerSupport — shared across terminal panes)

    public AppShellController(Stage stage, ConfigService configService) {
        super(stage, configService);
        focusModeController = new FocusModeController(stage);
    }

    /**
     * Show the 5-step welcome / onboarding guide when the user has no projects
     * or an incomplete config (missing any mandatory field / folder). Uses the
     * same {@link #isConfigFullySet()} check as the top banner so there is one
     * source of truth. Re-checked on every launch; auto-stops once setup is
     * done. Called from {@link com.conload.App#start} after the primary stage is shown.
     */
    public void maybeShowWelcomeGuide() {
        boolean noProjects = projectService.loadProjects().isEmpty();
        if (!(noProjects || !isConfigFullySet())) return;
        new WelcomeGuidePopup(stage, this::showSettingsPanel, this::switchToProjects,
                this::switchToPrompts, () -> {}).show();
    }

    /** Open the Settings/Config overlay panel (used by the welcome guide). */
    private void showSettingsPanel() {
        showPanel("CONFIG");
    }

    @Override
    protected com.conload.ui.workflow.WorkflowHost workflowHost() {
        return createWorkflowHost();
    }

    public Scene buildScene() {
        buildCachedPanels();
        buildSceneOverlay();
        buildLeftPane();
        buildSharedPromptWorkspace();
        VBox dashboard = buildDashboard();
        StackPane focusOverlay = focusModeController.buildOverlay();

        StackPane sceneRoot = new StackPane(dashboard, sceneOverlay, focusOverlay);
        return finishScene(sceneRoot);
    }

    private void buildCachedPanels() {
        cachedConfigPanel = buildConfigContent();
        cachedAboutPanel = buildAboutTab();
        cachedProjectsPanel = buildProjectsTab();
    }

    private void buildSceneOverlay() {
        overlayCard = new VBox(0);
        overlayCard.setMaxWidth(820);
        overlayCard.setMaxHeight(700);
        overlayCard.getStyleClass().add("overlay-card");
        StackPane.setAlignment(overlayCard, Pos.CENTER);

        sceneOverlay = new StackPane(overlayCard);
        sceneOverlay.getStyleClass().add("overlay-scene");
        UiFactory.hide(sceneOverlay);
        sceneOverlay.setOnMouseClicked(e -> {
            if (e.getTarget() == sceneOverlay) hideOverlay();
        });
    }

    private void buildLeftPane() {
        leftPaneHost = new StackPane();
        leftPaneHost.setMinWidth(410);
        leftPaneHost.setPrefWidth(410);
        leftPaneHost.getStyleClass().add("panel-border-left");
        showEmptyLeftPane();
    }

    private VBox buildDashboard() {
        dashboardCenter = contentArea;
        centerStack = new StackPane(dashboardCenter);

        HBox topBar = buildTopBar();
        configBanner = buildConfigBanner();
        projectTabsBar = new HBox(8);
        projectTabsBar.setAlignment(Pos.CENTER_LEFT);
        projectTabsBar.setPadding(new Insets(6, 12, 6, 12));
        projectTabsBar.getStyleClass().add("panel-border-bottom");
        refreshProjectTabsBar();

        VBox terminalSection = buildTerminalSection();
        VBox rightColumn = new VBox(centerStack, sharedPromptPanel.getSelectionsBar(),
                sharedQuickActionsBar, terminalSeparator, terminalSection);
        Theme.classes(rightColumn, Theme.CL_BG_APP);
        VBox.setVgrow(centerStack, Priority.ALWAYS);

        splitPane = new SplitPane(leftPaneHost, rightColumn);
        splitPane.setDividerPositions(0.24);
        SplitPane.setResizableWithParent(leftPaneHost, false);
        Theme.classes(splitPane, Theme.CL_BG_APP);

        VBox dashboard = new VBox(topBar, configBanner, projectTabsBar, splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        return dashboard;
    }

    /** Builds (but does not show) the red "config incomplete" banner.
     *  Visibility is driven by {@link #refreshConfigBanner()}. */
    private HBox buildConfigBanner() {
        Label icon = new Label(Icons.WARNING);
        icon.getStyleClass().add("config-banner-icon");
        Label text = new Label("Configuration incomplete — not all required settings (*) are set. "
                + "Search, download, and workflows are disabled until setup is finished.");
        text.getStyleClass().add("config-banner-text");
        text.setWrapText(true);
        HBox.setHgrow(text, Priority.ALWAYS);
        Button openBtn = UiFactory.actionButton("Open Settings");
        openBtn.setOnAction(e -> showPanel("CONFIG"));
        HBox banner = new HBox(10, icon, text, openBtn);
        banner.getStyleClass().add("config-banner");
        banner.setAlignment(Pos.CENTER_LEFT);
        UiFactory.hide(banner);
        return banner;
    }

    /** True when every mandatory config field is present: Atlassian email/token,
     *  root URL, GitHub token, default export folder, and full Confluence folder. */
    private boolean isConfigFullySet() {
        AppConfig cfg = configService.loadConfig();
        if (!cfg.isValid()) return false;
        if (cfg.getBaseUrl().isBlank()) return false;
        if (cfg.getGithubToken().isBlank()) return false;
        if (cfg.getDefaultExportFolder().isBlank()) return false;
        return !new com.conload.workflow.WorkflowSettingsService().getFullConfluenceFolder().isBlank();
    }

    /** Show or hide the red top banner based on the current config state.
     *  Called at startup (after autoLoadConfig) and after Save. */
    private void refreshConfigBanner() {
        if (configBanner == null) return;
        UiFactory.setVisible(configBanner, !isConfigFullySet());
    }

    private VBox buildTerminalSection() {
        terminalHost = new StackPane();
        terminalHost.setMinHeight(230);
        Theme.classes(terminalHost, Theme.CL_BG_APP);
        VBox.setVgrow(terminalHost, Priority.ALWAYS);
        Label terminalHint = new Label("Open a project tab to start terminal session");
        terminalHint.getStyleClass().addAll("hint");
        terminalHost.getChildren().setAll(terminalHint);

        VBox terminalSection = new VBox(terminalHost);
        terminalSection.getStyleClass().add("terminal-section");
        VBox.setVgrow(terminalSection, Priority.ALWAYS);
        this.terminalSection = terminalSection;

        terminalSeparator = new javafx.scene.control.Separator();
        terminalSeparator.getStyleClass().add("qa-terminal-separator");
        return terminalSection;
    }

    private Scene finishScene(StackPane sceneRoot) {
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double sceneH = screen.getHeight() * 0.99;
        double sceneW = Math.min(1400, screen.getWidth() * 0.98);
        Scene scene = new Scene(sceneRoot, sceneW, sceneH);
        Theme.apply(sceneRoot);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                if (focusModeController.isActive()) focusModeController.toggle();
                else hideOverlay();
            }
        });
        stage.setX(screen.getMinX() + (screen.getWidth() - sceneW) / 2);
        stage.setY(screen.getMinY() + screen.getHeight() * 0.015);
        autoLoadConfig();
        refreshConfigBanner();
        pidRegistry.killAllAliveAndClear();
        List<OpenTabsService.OpenTab> saved = openTabsService.load();
        if (!saved.isEmpty()) restoreOpenTabs();
        else selectInitialProject();
        return scene;
    }

    private void buildSharedPromptWorkspace() {
        speechService = new SpeechRecognitionService();
        if ("de".equalsIgnoreCase(configService.getVoskLang())) {
            speechService.setCurrentLang(com.conload.service.SpeechRecognitionService.ModelLang.DE);
        }
        speechService.preloadModel();

        sharedQuickActionsBar = new QuickActionsBar(this::sendToActiveTerminal);
        sharedPromptPanel = new PromptTemplatePanel(
                this::sendToActiveTerminal,
                () -> { if (sharedQuickActionsBar != null) sharedQuickActionsBar.refresh(); },
                speechService,
                () -> projectTerminals.get(activeProjectId));
        sharedPromptPanel.setOnPickerModeChanged(active ->
                projectFilesPanes.values().forEach(pane -> pane.setPickerMode(active)));
        sharedPromptPanel.setOnPromptExpandChanged(this::resizePromptWorkspace);

        // Inline workflow section — replaces the former modal WorkflowDialog.
        // Its selector combo goes in the prompt header; its body appears above
        // the textarea when a workflow is selected.
        com.conload.ui.workflow.WorkflowInlineSection workflowSection =
                new com.conload.ui.workflow.WorkflowInlineSection(createWorkflowHost());
        sharedPromptPanel.setWorkflowSection(workflowSection);

        contentArea = new StackPane(sharedPromptPanel);
        Theme.classes(contentArea, Theme.CL_BG_APP);
    }

    private void resizePromptWorkspace(boolean expanded) {
        if (centerStack != null) {
            VBox.setVgrow(centerStack, expanded ? Priority.ALWAYS : Priority.NEVER);
        }
        if (terminalSection != null) {
            VBox.setVgrow(terminalSection, expanded ? Priority.NEVER : Priority.ALWAYS);
        }
        if (terminalHost != null) {
            VBox.setVgrow(terminalHost, expanded ? Priority.NEVER : Priority.ALWAYS);
        }
    }


    protected HBox buildTopBar() {

        Button projects = UiFactory.actionButton("Projects");
        projects.setOnAction(e -> switchToProjects());

        Button prompts = UiFactory.actionButton("Prompts library");
        prompts.setOnAction(e -> switchToPrompts());

        Button help = UiFactory.actionButton("Help");
        help.setOnAction(e -> switchToHelp());

        // ── Focus mode toggle = brand logo (Hide/Show) ───────────────────────
        // Uses a native SVG graphic (corners + terminal symbol) instead of a
        // text glyph so it survives full-screen / focus transitions cleanly.
        // Rendered larger than the other icon glyphs so it reads as the brand
        // mark sitting on one line with the title.
        Button focusToggleBtn = focusModeController.buildToggleButton();

        Label title = new Label("CONLOAD");
        title.getStyleClass().addAll("title");

        Button settings = new Button(Icons.SETTINGS);
        settings.getStyleClass().addAll("icon-button", "secondary");
        settings.getStyleClass().add("icon");
        settings.setTooltip(new Tooltip("Settings"));
        settings.setOnAction(e -> showPanel("CONFIG"));

        Region spacer = UiFactory.hSpacer();

        // Logo first, directly left of the title, both vertically centered.
        HBox bar = new HBox(10, focusToggleBtn, title, spacer, settings, projects, prompts, help);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.getStyleClass().add("panel-border-bottom");
        return bar;
    }

    protected void showPanel(String which) {
        overlayCard.getChildren().clear();

        // Title bar
        HBox titleBar = new HBox(8);
        titleBar.setPadding(new Insets(10, 14, 10, 16));
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.getStyleClass().add("panel-border-bottom");
        Label panelTitle = new Label(which.equals("CONFIG") ? Icons.SETTINGS + "  SETTINGS / CONFIG" : Icons.INFO + "  ABOUT");
        Theme.classes(panelTitle, Theme.CL_TITLE_SMALL);
        Region sp = UiFactory.hSpacer();
        Button closeBtn = new Button(Icons.CLOSE);
        closeBtn.getStyleClass().addAll("overlay-close", "icon");
        closeBtn.setOnAction(e -> hideOverlay());
        titleBar.getChildren().addAll(panelTitle, sp, closeBtn);

        javafx.scene.Node content = which.equals("CONFIG") ? cachedConfigPanel : cachedAboutPanel;
        VBox.setVgrow(content, Priority.ALWAYS);
        overlayCard.getChildren().addAll(titleBar, content);

        UiFactory.show(sceneOverlay);
    }

    /**
     * Close the overlay panel.
     */

    protected void hideOverlay() {
        UiFactory.hide(sceneOverlay);
    }

    /**
     * Creates the {@link com.conload.ui.workflow.WorkflowHost} that bridges the
     * inline workflow section to the active project's config, workspace path,
     * contexts directory, and terminal.
     */
    private com.conload.ui.workflow.WorkflowHost createWorkflowHost() {
        return new com.conload.ui.workflow.WorkflowHost() {
            @Override public com.conload.model.AppConfig config() {
                return configService.loadConfig();
            }
            @Override public String githubToken() {
                return configService.loadConfig().getGithubToken();
            }
            @Override public String githubApiUrl() {
                return configService.loadConfig().githubApiBase();
            }
            @Override public String activeProjectId() {
                return activeProjectId;
            }
            @Override public String workspacePath() {
                if (activeProjectId == null) return System.getProperty("user.home");
                // Follow the active worktree (if any) so workflow prompts and
                // output-doc paths point into the worktree's tree, not the base
                // repo. Falls back to the base workspace when no worktree is
                // selected or the worktree path no longer exists on disk.
                return resolveActiveWorkspace(projectService.findById(activeProjectId));
            }
            @Override public java.nio.file.Path contextsDir() {
                if (activeProjectId == null)
                    return java.nio.file.Path.of(System.getProperty("user.home"));
                com.conload.model.Project p = projectService.findById(activeProjectId);
                if (p == null)
                    return java.nio.file.Path.of(System.getProperty("user.home"));
                java.nio.file.Path dir = projectService.resolveContextsDir(p);
                try { java.nio.file.Files.createDirectories(dir); }
                catch (java.io.IOException ignored) {}
                return dir;
            }
            @Override public void registerContext(String contextFolder) {
                if (activeProjectId == null) return;
                try {
                    projectService.addContextFolder(activeProjectId, contextFolder);
                    ProjectFilesPane pane = projectFilesPanes.get(activeProjectId);
                    if (pane != null) {
                        com.conload.model.Project found = projectService.findById(activeProjectId);
                        if (found != null) pane.setContextFolderPaths(found.getContextFolders());
                        pane.refresh();
                        pane.highlightAndExpandPath(java.nio.file.Path.of(contextFolder));
                    }
                } catch (java.io.IOException e) {
                    System.err.println("[WORKFLOW] registerContext failed: " + e.getMessage());
                }
            }
            @Override public void setPromptText(String text) {
                if (sharedPromptPanel != null) sharedPromptPanel.setPromptText(text);
            }
            @Override public void sendToTerminal(String command) {
                sendToActiveTerminal(command);
            }
            @Override public String fullConfluenceFolder() {
                return new com.conload.workflow.WorkflowSettingsService().getFullConfluenceFolder();
            }
            @Override public javafx.stage.Window ownerWindow() {
                return stage;
            }
        };
    }


    // =========================================================================
    // TAB 1 — Config
    // =========================================================================


    protected VBox buildConfigContent() {
        VBox cred = new VBox(16);
        cred.setPadding(new Insets(20, 24, 20, 24));
        cred.getStyleClass().add("panel");
        Label reqHint = new Label("Fields marked with * are required — search and workflows are disabled until they are filled.");
        reqHint.getStyleClass().addAll("hint", "small");
        reqHint.setWrapText(true);
        cred.getChildren().addAll(reqHint, buildCredentialFields(), buildUrlSection(),
                buildGithubSection(), buildConfluenceFolderSection(),
                buildExportFolderSection(), buildShellSection(),
                buildCliTypesSection());

        Button saveBtn = UiFactory.actionButton("Save");
        Button loadBtn = UiFactory.actionButton("Load");
        saveBtn.setPrefWidth(120);
        loadBtn.setPrefWidth(120);
        cred.getChildren().add(new HBox(12, saveBtn, loadBtn));

        configStatusLabel = new Label("");
        configStatusLabel.getStyleClass().addAll("status", "success");
        cred.getChildren().add(configStatusLabel);
        saveBtn.setOnAction(e -> saveConfig());
        loadBtn.setOnAction(e -> loadConfig());
        return cred;
    }

    private HBox buildCredentialFields() {
        HBox fieldsRow = new HBox(24);
        fieldsRow.setAlignment(Pos.TOP_LEFT);

        VBox userBox = new VBox(6);
        userBox.getChildren().add(UiFactory.fieldLabel("EMAIL / USERNAME (Atlassian & Github)", true));
        usernameField = UiFactory.darkTextField("user@example.com");
        userBox.getChildren().add(usernameField);
        HBox.setHgrow(userBox, Priority.ALWAYS);

        VBox tokenBox = new VBox(6);
        tokenBox.getChildren().add(UiFactory.fieldLabel("ATLASSIAN API TOKEN", true));
        tokenField = new PasswordField();
        UiFactory.styleInput(tokenField, "Paste Atlassian API token here");
        tokenBox.getChildren().add(tokenField);
        HBox.setHgrow(tokenBox, Priority.ALWAYS);

        fieldsRow.getChildren().addAll(userBox, tokenBox);
        return fieldsRow;
    }

    private VBox buildUrlSection() {
        VBox urlBox = new VBox(6);
        urlBox.getChildren().add(UiFactory.fieldLabel("ATLASSIAN ROOT URL (CONFLUENCE & JIRA)", true));
        baseUrlConfigField = UiFactory.darkTextField("https://yoursite.atlassian.net");
        urlBox.getChildren().add(baseUrlConfigField);
        return urlBox;
    }

    private VBox buildGithubSection() {
        VBox ghBox = new VBox(6);
        ghBox.getChildren().add(UiFactory.fieldLabel("GITHUB ROOT URL (REST API BASE)", false));
        githubApiUrlField = UiFactory.darkTextField("https://api.github.com");
        githubApiUrlField.setPromptText("https://api.github.com  (or GitHub Enterprise host)");
        ghBox.getChildren().add(githubApiUrlField);
        Label ghUrlHint = new Label("REST API root used by all GitHub calls + web-URL parsing. Blank = https://api.github.com");
        ghUrlHint.getStyleClass().addAll("hint", "small");
        ghUrlHint.setWrapText(true);
        ghBox.getChildren().add(ghUrlHint);
        ghBox.getChildren().add(UiFactory.fieldLabel("GITHUB PERSONAL ACCESS TOKEN", true));
        githubTokenField = new PasswordField();
        UiFactory.styleInput(githubTokenField, "ghp_xxxxxxxxxxxxxxxxxxxx");
        ghBox.getChildren().add(githubTokenField);
        return ghBox;
    }

    private VBox buildConfluenceFolderSection() {
        VBox box = new VBox(6);
        box.getChildren().add(UiFactory.fieldLabel("FULL CONFLUENCE FOLDER (GLOBAL DEFAULT)", true));
        fullConfluenceFolderField = UiFactory.darkTextField("Local folder with full Confluence data");
        HBox.setHgrow(fullConfluenceFolderField, Priority.ALWAYS);
        Button browseBtn = UiFactory.actionButton("Browse");
        browseBtn.setPrefWidth(90);
        browseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Confluence folder");
            String cur = fullConfluenceFolderField.getText().strip();
            if (!cur.isBlank()) {
                File f = new File(cur);
                if (f.exists() && f.isDirectory()) chooser.setInitialDirectory(f);
            }
            File dir = chooser.showDialog(stage);
            if (dir != null) fullConfluenceFolderField.setText(dir.getAbsolutePath());
        });
        HBox row = new HBox(4, fullConfluenceFolderField, browseBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(row);
        Label hint = new Label("Same for all workflows. Local folder only — injected into prompt templates as ${confluenceDataSource}.");
        hint.getStyleClass().addAll("hint", "small");
        hint.setWrapText(true);
        box.getChildren().add(hint);
        return box;
    }

    private VBox buildExportFolderSection() {
        VBox exportBox = new VBox(6);
        exportBox.getChildren().add(UiFactory.fieldLabel("DEFAULT EXPORT FOLDER", true));
        defaultExportFolderField = UiFactory.darkTextField(System.getProperty("user.home") + "/conload-exports");
        Button exportBrowseBtn = UiFactory.actionButton("Browse");
        exportBrowseBtn.setPrefWidth(90);
        exportBrowseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select default export folder");
            String cur = defaultExportFolderField.getText().strip();
            if (!cur.isBlank()) {
                File f = new File(cur);
                if (f.exists() && f.isDirectory()) chooser.setInitialDirectory(f);
            }
            File dir = chooser.showDialog(stage);
            if (dir != null) defaultExportFolderField.setText(dir.getAbsolutePath());
        });
        HBox exportRow = new HBox(4, defaultExportFolderField, exportBrowseBtn);
        HBox.setHgrow(defaultExportFolderField, Priority.ALWAYS);
        exportBox.getChildren().add(exportRow);
        return exportBox;
    }

    private VBox buildShellSection() {
        VBox shellBox = new VBox(6);
        shellBox.getChildren().add(UiFactory.fieldLabel("TERMINAL SHELL", false));
        shellField = UiFactory.darkTextField("zsh / bash / powershell / pwsh");
        shellBox.getChildren().add(shellField);
        Label shellHint = new Label("The shell spawned by the embedded terminal. Blank = prompt for a detected one on first open.");
        shellHint.getStyleClass().addAll("hint", "small");
        shellHint.setWrapText(true);
        shellBox.getChildren().add(shellHint);
        return shellBox;
    }

    private VBox buildCliTypesSection() {
        VBox cliBox = new VBox(6);
        Label cliHeader = new Label("CLI TYPES (DETECTION + SESSION COMMANDS)");
        cliHeader.getStyleClass().addAll("field-label", "small");
        cliBox.getChildren().add(cliHeader);
        cliTypesContainer = new VBox(4);
        cliBox.getChildren().add(cliTypesContainer);
        Button addCliBtn = UiFactory.actionButton("+ Add CLI type");
        addCliBtn.setPrefWidth(140);
        addCliBtn.setOnAction(e -> {
            cliTypesContainer.getChildren().add(new CliTypeRow().node());
        });
        cliBox.getChildren().add(addCliBtn);
        Label cliHint = new Label("Blank list/resume/export = capability disabled for that CLI. Use {id} in commands for the session id.");
        cliHint.getStyleClass().addAll("hint", "small");
        cliHint.setWrapText(true);
        cliBox.getChildren().add(cliHint);
        return cliBox;
    }

    // =========================================================================
    // TAB — Download  (all functionality in one tab)
    // =========================================================================


    protected void saveConfig() {
        String base = baseUrlConfigField.getText().strip().replaceAll("/$", "");
        String ghApi = githubApiUrlField.getText().strip().replaceAll("/$", "");
        AppConfig config = new AppConfig(
                usernameField.getText().strip(), tokenField.getText().strip(),
                base, githubTokenField.getText().strip(),
                ghApi,
                defaultExportFolderField.getText().strip(),
                shellField.getText().strip(), collectCliTypes());
        try {
            configService.saveConfig(config);
            new com.conload.workflow.WorkflowSettingsService()
                    .setFullConfluenceFolder(fullConfluenceFolderField.getText().strip());
            setConfigStatus(Icons.CHECK + "  Saved to " + configService.getConfigPath(), "success");
        } catch (IOException e) {
            setConfigStatus("✗  Failed: " + e.getMessage(), "error");
        }
        refreshConfigBanner();
    }


    protected void loadConfig() {
        AppConfig config = configService.loadConfig();
        usernameField.setText(config.getUsername());
        tokenField.setText(config.getToken());
        if (!config.getBaseUrl().isBlank()) baseUrlConfigField.setText(config.getBaseUrl());
        if (!config.getGithubToken().isBlank()) githubTokenField.setText(config.getGithubToken());
        if (!config.getGithubApiUrl().isBlank()) githubApiUrlField.setText(config.getGithubApiUrl());
        if (!config.getDefaultExportFolder().isBlank()) defaultExportFolderField.setText(config.getDefaultExportFolder());
        shellField.setText(config.getShell());
        populateCliTypes(config.getCliTypes());
        String confFolder = new com.conload.workflow.WorkflowSettingsService().getFullConfluenceFolder();
        if (!confFolder.isBlank()) fullConfluenceFolderField.setText(confFolder);
        setConfigStatus(config.isValid()
                        ? Icons.CHECK + "  Loaded from " + configService.getConfigPath()
                        : Icons.WARNING + "  Config not found — fill credentials and save",
                config.isValid() ? "success" : "warning");
    }


    protected void autoLoadConfig() {
        AppConfig config = configService.loadConfig();
        if (config.isValid()) {
            usernameField.setText(config.getUsername());
            tokenField.setText(config.getToken());
        }
        if (!config.getBaseUrl().isBlank()) baseUrlConfigField.setText(config.getBaseUrl());
        if (!config.getGithubToken().isBlank()) githubTokenField.setText(config.getGithubToken());
        if (!config.getGithubApiUrl().isBlank()) githubApiUrlField.setText(config.getGithubApiUrl());
        if (!config.getDefaultExportFolder().isBlank()) defaultExportFolderField.setText(config.getDefaultExportFolder());
        shellField.setText(config.getShell());
        populateCliTypes(config.getCliTypes());
        String confFolder = new com.conload.workflow.WorkflowSettingsService().getFullConfluenceFolder();
        if (!confFolder.isBlank()) fullConfluenceFolderField.setText(confFolder);
    }

    // ── CLI types section helpers ─────────────────────────────────────────────

    /** Collects the current Config-tab CLI rows into a list of definitions.
     *  Fully-blank rows are skipped (the service also drops them on save, but
     *  skipping here keeps the in-memory list tidy). */
    protected java.util.List<com.conload.model.CliTypeDefinition> collectCliTypes() {
        java.util.List<com.conload.model.CliTypeDefinition> list = new java.util.ArrayList<>();
        if (cliTypesContainer == null) return list;
        for (javafx.scene.Node node : cliTypesContainer.getChildren()) {
            if (!(node instanceof HBox hb)) continue;
            // Each row HBox holds five labeled field boxes + a remove button.
            // Read the text fields by position (child[N].getChildren().get(1)).
            String[] vals = new String[5];
            int i = 0;
            for (javafx.scene.Node child : hb.getChildren()) {
                if (i >= 5) break; // 6th child is the Remove button
                if (child instanceof javafx.scene.layout.VBox box && box.getChildren().size() >= 2
                        && box.getChildren().get(1) instanceof TextField tf) {
                    vals[i] = tf.getText().strip();
                } else {
                    vals[i] = "";
                }
                i++;
            }
            com.conload.model.CliTypeDefinition def = new com.conload.model.CliTypeDefinition(
                i > 0 ? vals[0] : "", i > 1 ? vals[1] : "", i > 2 ? vals[2] : "",
                i > 3 ? vals[3] : "", i > 4 ? vals[4] : "");
            if (def.getLabel().isBlank() && def.getDetectText().isBlank()
                && def.getListCommand().isBlank() && def.getResumeCommand().isBlank()
                && def.getExportCommand().isBlank()) continue;
            list.add(def);
        }
        return list;
    }

    /** Populates the Config-tab CLI rows from a loaded list. Seeds the
     *  built-in defaults when the loaded list is empty (first run). */
    protected void populateCliTypes(java.util.List<com.conload.model.CliTypeDefinition> cliTypes) {
        if (cliTypesContainer == null) return;
        cliTypesContainer.getChildren().clear();
        java.util.List<com.conload.model.CliTypeDefinition> list =
            (cliTypes == null || cliTypes.isEmpty())
                ? com.conload.model.CliTypeDefinition.defaults()
                : cliTypes;
        for (com.conload.model.CliTypeDefinition def : list) {
            cliTypesContainer.getChildren().add(new CliTypeRow(def).node());
        }
    }


    protected void setConfigStatus(String msg, String color) {
        configStatusLabel.setText(msg);
        configStatusLabel.getStyleClass().removeAll("success", "warning", "error");
        if ("success".equals(color)) {
            configStatusLabel.getStyleClass().add("success");
        } else if ("warning".equals(color)) {
            configStatusLabel.getStyleClass().add("warning");
        } else {
            configStatusLabel.getStyleClass().add("error");
        }
    }


    protected ScrollPane buildAboutTab() {
        return AboutPanel.build();
    }

    // =========================================================================
    // Contexts manager block — shown on top of the Contexts (Download) view
    // =========================================================================

}
