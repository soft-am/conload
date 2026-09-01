package com.conload.ui.projects;

import com.conload.ui.createcontext.ContextAcquisitionController;
import com.conload.ui.createcontext.ConfluenceProjectCreator;
import com.conload.ui.DialogStyler;
import com.conload.ui.Icons;
import com.conload.util.BackgroundTasks;
import com.conload.ui.ProjectColors;
import com.conload.ui.Theme;
import com.conload.ui.components.MarkdownViewerPopup;
import com.conload.ui.components.LocalContextFolderDialog;
import com.conload.ui.components.UiFactory;
import com.conload.ui.terminal.CopilotTerminalPane;
import com.conload.ui.terminal.RobotIndicator;
import com.conload.ui.projects.workspace.ProjectTerminalFactory;
import com.conload.ui.projects.workspace.TerminalGroup;
import com.conload.model.Project;
import com.conload.model.Worktree;
import com.conload.model.QuickAction;
import com.conload.service.ConfigService;
import com.conload.service.OpenTabsService;
import com.conload.service.ProjectService;
import com.conload.util.Json;
import com.conload.workflow.Workflow;
import com.conload.workflow.WorkflowContext;
import com.conload.workflow.WorkflowRegistry;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public abstract class ProjectWorkspaceController extends ContextAcquisitionController {

    protected ProjectWorkspaceController(Stage stage, ConfigService configService) {
        super(stage, configService);
    }

    private final Map<String, HBox> projectTabBoxes = new LinkedHashMap<>();
    private Button tabStripTitle;
    private Button tabStripOpenBtn;
    private ConfluenceProjectCreator confluenceProjectCreator;
    /** Projects for which the WORKTREES section's git-repo detection + first
     *  listing has already run (so we don't shell out to git on every
     *  workspace switch within the same project). */
    private final Set<String> worktreesSectionInited = new HashSet<>();
    private final ProjectTerminalFactory terminalFactory;

    {
        terminalFactory = new ProjectTerminalFactory(stage, configService, projectService, pidRegistry,
                new HashMap<>(),
                projectTerminals, projectColors, projectCharacters, projectFilesPanes,
                color -> {
                    if (terminalSeparator != null) terminalSeparator.setStyle("-accent-color: " + color + ";");
                }, this::updateProjectTabStates, this::saveOpenTabs);
    }

    @Override
    public void switchToProjects() {
        // Collapse the prompt area so full-window views have maximum space
        if (sharedPromptPanel != null) sharedPromptPanel.collapsePrompt();
        super.switchToProjects();
    }

    @Override
    public void switchToPrompts() {
        if (sharedPromptPanel != null) sharedPromptPanel.collapsePrompt();
        super.switchToPrompts();
    }

    @Override
    public void switchToHelp() {
        if (sharedPromptPanel != null) sharedPromptPanel.collapsePrompt();
        super.switchToHelp();
    }

    protected void refreshProjectTabsBar() {
        if (projectTabsBar == null) return;

        if (tabStripTitle == null) {
            tabStripTitle = UiFactory.actionButton("Project");
            tabStripTitle.getStyleClass().addAll("link-button", "bold");
            tabStripTitle.setOnAction(e -> showProjectKebabMenu(tabStripTitle));
        }
        if (tabStripOpenBtn == null) {
            tabStripOpenBtn = UiFactory.actionButton(Icons.ADD);
            tabStripOpenBtn.getStyleClass().add("icon");
            tabStripOpenBtn.setOnAction(e -> showOpenProjectMenu(tabStripOpenBtn));
        }

        java.util.List<javafx.scene.Node> desired = new java.util.ArrayList<>();
        desired.add(tabStripTitle);

        List<Project> projects = projectService.loadProjects();
        java.util.Set<String> stillOpen = new java.util.HashSet<>();
        for (Project p : projects) {
            if (!openProjectIds.contains(p.getId())) continue; // only show tabs currently open
            stillOpen.add(p.getId());
            boolean active = p.getId().equals(activeProjectId);
            CopilotTerminalPane t = activeTerminal(activeWorkspaceKey(p.getId()));
            boolean busy = (t != null && t.busyProperty().get());
            String color = projectColors.getOrDefault(p.getId(), ProjectColors.DEFAULT);

            HBox tabBox = projectTabBoxes.computeIfAbsent(p.getId(),
                    id -> buildProjectTabBox(p, t));
            updateTabBoxState(tabBox, p, t, active, busy, color);
            desired.add(tabBox);
        }
        projectTabBoxes.keySet().removeIf(id -> !stillOpen.contains(id));
        desired.add(tabStripOpenBtn);

        // Reconcile with minimal mutations so persistent tab boxes (and their
        // cached RobotIndicator WebViews) stay attached to the scene graph.
        // macOS blanks a WebView and stalls its SMIL animation whenever it is
        // detached+reattached; the remove(int)/add(int) list ops shift siblings
        // without detaching them, so only genuinely closed tabs are detached.
        javafx.collections.ObservableList<javafx.scene.Node> kids = projectTabsBar.getChildren();
        for (javafx.scene.Node n : new java.util.ArrayList<>(kids)) {
            if (!desired.contains(n)) kids.remove(n);
        }
        for (int i = 0; i < desired.size(); i++) {
            javafx.scene.Node want = desired.get(i);
            int cur = kids.indexOf(want);
            if (cur == i) continue;
            if (cur >= 0) kids.remove(cur);
            kids.add(i, want);
        }
    }

    private HBox buildProjectTabBox(Project p, CopilotTerminalPane t) {
        Button tab = new Button(p.getName());
        tab.getStyleClass().add("bg-transparent");
        tab.setOnAction(e -> switchToProject(p));

        Button close = new Button(Icons.CLOSE);
        close.getStyleClass().addAll("tab-close", "icon");
        close.setTooltip(new Tooltip("Close tab"));
        close.setOnAction(e -> closeProjectTab(p));

        Node statusNode = (t != null) ? t.getRobotIndicator() : new Label("");
        HBox box = new HBox(6, statusNode, tab, close);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("project-tab");
        box.setUserData(p.getId());
        return box;
    }

    private void updateTabBoxState(HBox box, Project p, CopilotTerminalPane t,
                                   boolean active, boolean busy, String color) {
        Button tab = (Button) box.getChildren().get(1);
        tab.setText(p.getName());
        // -accent-color (set on the box below) drives all tab text/border colors
        // via CSS (.project-tab > .button { -fx-text-fill: -accent-color }).
        box.setStyle("-accent-color: " + color + ";");

        if (active) {
            if (!box.getStyleClass().contains("active")) box.getStyleClass().add("active");
        } else {
            box.getStyleClass().remove("active");
        }

        Node status = box.getChildren().get(0);
        if (t != null) {
            RobotIndicator ri = t.getRobotIndicator();
            // The robot WebView is natively transparent (setPageFill), so it
            // automatically blends into whatever tab background is behind it
            // (active or inactive, hover, etc.) — no manual color swap needed.
            if (status != ri) {
                box.getChildren().set(0, ri);
                status = ri;
            }
            // Keep the WebView both visible and managed at all times. Toggling
            // managed/visible off while a command runs means the RobotIndicator
            // WebView is at 0 layout size when its page finishes loading, so it
            // never paints; when busy flips on, macOS shows a blank square
            // instead of the animated robot. Keeping it laid out lets the page
            // render and stay warm; we collapse the reserved space via
            // prefWidth/prefHeight so an idle tab doesn't show a gap, and use
            // opacity to hide the now-zero-size node cleanly.
            UiFactory.show(ri);
            // Show the robot when: a command is running, OR a CLI session id
            // is active (resumed/detected), OR the user has typed at least
            // once into this terminal. Latched on first input via
            // hasInputProperty; sessionProperty tracks any opencode/copilot id.
            String sid = t.sessionProperty().get();
            boolean sessionActive = sid != null && !sid.isBlank();
            boolean showRobot = busy || sessionActive || t.hasInputProperty().get();
            if (showRobot) {
                ri.setPrefWidth(RobotIndicator.SIZE);
                ri.setPrefHeight(RobotIndicator.SIZE);
                ri.setOpacity(1.0);
            } else {
                ri.setPrefWidth(0);
                ri.setPrefHeight(0);
                ri.setOpacity(0.0);
            }
        } else if (status.isVisible()) {
            UiFactory.hide(status);
        }
    }

    private void updateProjectTabStates() {
        if (terminalSubtabStrip != null) terminalSubtabStrip.refreshActive();
        if (projectTabsBar == null) return;
        java.util.Map<String, Project> byId = new java.util.HashMap<>();
        for (Project p : projectService.loadProjects()) byId.put(p.getId(), p);
        for (Map.Entry<String, HBox> e : projectTabBoxes.entrySet()) {
            String id = e.getKey();
            HBox box = e.getValue();
            Project p = byId.get(id);
            if (p == null) continue;
            CopilotTerminalPane t = activeTerminal(activeWorkspaceKey(id));
            boolean active = id.equals(activeProjectId);
            boolean busy = (t != null && t.busyProperty().get());
            String color = projectColors.getOrDefault(id, ProjectColors.DEFAULT);
            updateTabBoxState(box, p, t, active, busy, color);
        }
        // Session pills in the worktree list may have changed (a session was
        // just detected/resumed) — refresh them for the active project without
        // re-running git (labels only).
        if (activeProjectId != null) refreshWorktreeSessionLabels(activeProjectId);
    }

    /** Popup that lists projects not currently open so the user can add a tab in one click. */

    protected void showOpenProjectMenu(Button anchor) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        List<Project> projects = projectService.loadProjects();

        boolean anyClosed = false;
        for (Project p : projects) {
            if (openProjectIds.contains(p.getId())) continue;
            anyClosed = true;
            javafx.scene.control.MenuItem item =
                new javafx.scene.control.MenuItem(Icons.FOLDER + "  " + p.getName());
            item.setOnAction(ev -> {
                openProjectIds.add(p.getId());
                switchToProject(p);
            });
            menu.getItems().add(item);
        }

        if (projects.isEmpty()) {
            javafx.scene.control.MenuItem empty =
                new javafx.scene.control.MenuItem("(no projects yet)");
            empty.setDisable(true);
            menu.getItems().add(empty);
        } else if (!anyClosed) {
            javafx.scene.control.MenuItem allOpen =
                new javafx.scene.control.MenuItem("(all projects already open)");
            allOpen.setDisable(true);
            menu.getItems().add(allOpen);
        }

        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        javafx.scene.control.MenuItem manage =
            new javafx.scene.control.MenuItem(Icons.ADD_CIRCLE + "  Create / Manage projects…");
        manage.setOnAction(ev -> switchToProjects());
        menu.getItems().add(manage);

        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    /**
     * Kebab menu for the "Project" button in the top-left: lets the user open
     * an existing folder as a project or create a new project from a Confluence
     * page URL.
     */
    private void showProjectKebabMenu(Button anchor) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem open =
                new javafx.scene.control.MenuItem(Icons.FOLDER + "  Open…");
        open.setOnAction(e -> openProjectFromFolder());
        javafx.scene.control.MenuItem download =
                new javafx.scene.control.MenuItem(Icons.DOWNLOAD + "  Download Confluence…");
        download.setOnAction(e -> getConfluenceProjectCreator().show());
        menu.getItems().addAll(open, download);
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private ConfluenceProjectCreator getConfluenceProjectCreator() {
        if (confluenceProjectCreator == null) {
            confluenceProjectCreator = new ConfluenceProjectCreator(
                    stage, configService::loadConfig, projectService,
                    this::appendLog, this::switchToProject);
        }
        return confluenceProjectCreator;
    }

    /** Remove a project from the top-tab strip; destroys its terminal to release resources. */

    protected void closeProjectTab(Project project) {
        String id = project.getId();
        // Close & discard EVERY terminal belonging to this project — the base
        // workspace terminal plus any open worktree terminals (keys whose
        // projectId half equals this id).
        java.util.Iterator<Map.Entry<String, com.conload.ui.projects.workspace.TerminalGroup>> it =
                projectTerminals.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, com.conload.ui.projects.workspace.TerminalGroup> e = it.next();
            if (id.equals(keyProjectId(e.getKey()))) {
                for (CopilotTerminalPane t : e.getValue().terminals()) t.closeTerminal();
                it.remove();
            }
        }
        projectFilesPanes.remove(id);
        selectedWorktreeByProject.remove(id);
        openProjectIds.remove(id);
        saveOpenTabs();

        if (id.equals(activeProjectId)) {
            activeProjectId = null;
            String next = openProjectIds.stream().findFirst().orElse(null);
            if (next != null) {
                projectService.loadProjects().stream()
                    .filter(p -> p.getId().equals(next))
                    .findFirst()
                    .ifPresent(this::switchToProject);
            } else {
                showEmptyLeftPane();
                if (terminalHost != null) {
                    Label hint = new Label("Open a project tab to start terminal session");
                    hint.getStyleClass().add("hint");
                    terminalHost.getChildren().setAll(hint);
                }
                refreshProjectTabsBar();
            }
        } else {
            refreshProjectTabsBar();
        }
    }
    protected void selectInitialProject() {
        List<Project> projects = projectService.loadProjects();
        if (!projects.isEmpty() && activeProjectId == null) {
            switchToProject(projects.get(0));
        } else {
            if (activeProjectId == null) showEmptyLeftPane();
        }
    }


    protected void sendToActiveTerminal(String command) {
        CopilotTerminalPane terminal = (activeProjectId != null)
                ? activeTerminal(activeWorkspaceKey(activeProjectId)) : null;
        if (terminal != null && command != null && !command.isBlank()) {
            terminal.sendInput(command);
        }
    }

    /**
     * Restore open tabs from disk. For each saved tab, opens the project
     * (which creates a new terminal). The old PID is logged but the process
     * is typically dead — a fresh terminal is started instead.
     * <p>If the saved tab carried an opencode (or, in future, copilot)
     * session id, that id is injected into the new terminal as a
     * <em>pending resume</em>: the terminal bar shows
     * {@code "opencode: <id>"} next to the PID label as a clickable link.
     * One click → the existing {@code resumeSession(id)} sends the
     * shell commands to resume; the label then becomes read-only. This
     * durable id is also mirrored onto the {@link Project} model
     * (lastSessionType/lastSessionId) by {@code saveOpenTabs} so it survives
     * a tab close (not just an app restart).
     */
    protected void restoreOpenTabs() {
        List<OpenTabsService.OpenTab> allSaved = openTabsService.load();
        if (allSaved.isEmpty()) return;
        List<OpenTabsService.OpenTab> saved = allSaved.stream()
                .filter(t -> t.sessionId() != null && !t.sessionId().isBlank())
                .toList();
        if (saved.isEmpty()) return;
        List<Project> projects = projectService.loadProjects();
        // Group entries by project preserving save order (base first, then any
        // worktrees) — a project may now have several saved OpenTabs, one per
        // workspace (base + each open worktree), each carrying its own session.
        // Sub-terminals (terminalSubId > 0) are created after the first.
        java.util.Map<String, java.util.List<OpenTabsService.OpenTab>> byProject =
                new java.util.LinkedHashMap<>();
        for (OpenTabsService.OpenTab tab : saved)
            byProject.computeIfAbsent(tab.projectId(), k -> new java.util.ArrayList<>()).add(tab);

        for (java.util.Map.Entry<String, java.util.List<OpenTabsService.OpenTab>> grp : byProject.entrySet()) {
            String pid = grp.getKey();
            Project p = projects.stream().filter(x -> x.getId().equals(pid)).findFirst().orElse(null);
            if (p == null) continue;
            openProjectIds.add(pid);
            // Group by workspace key, then create terminals in subId order.
            java.util.Map<String, java.util.List<OpenTabsService.OpenTab>> byWsKey = new java.util.LinkedHashMap<>();
            for (OpenTabsService.OpenTab tab : grp.getValue()) {
                String wt = tab.worktreePath() == null ? "" : tab.worktreePath();
                String wsKey = workspaceKey(pid, wt);
                byWsKey.computeIfAbsent(wsKey, k -> new java.util.ArrayList<>()).add(tab);
            }
            for (java.util.Map.Entry<String, java.util.List<OpenTabsService.OpenTab>> wsGrp : byWsKey.entrySet()) {
                String wsKey = wsGrp.getKey();
                java.util.List<OpenTabsService.OpenTab> wsTabs = wsGrp.getValue();
                wsTabs.sort(java.util.Comparator.comparingInt(OpenTabsService.OpenTab::terminalSubId));
                // Renumber subIds to 0,1,2,… so indices match the created
                // terminals (filtering may have removed some, leaving gaps).
                java.util.List<OpenTabsService.OpenTab> renumbered = new java.util.ArrayList<>();
                for (int i = 0; i < wsTabs.size(); i++) {
                    OpenTabsService.OpenTab t = wsTabs.get(i);
                    renumbered.add(OpenTabsService.of(t.projectId(), t.worktreePath(),
                            t.pid(), t.sessionId(), t.sessionType(), i));
                }
                wsTabs = renumbered;
                OpenTabsService.OpenTab first = wsTabs.get(0);
                String wt = first.worktreePath() == null ? "" : first.worktreePath();
                if (wt.isBlank()) doSwitch(p);
                else switchToWorkspace(p, wt);
                for (int si = 1; si < wsTabs.size(); si++) {
                    terminalFactory.createNew(p, wsKey, wt.isBlank() ? resolveWorkspaceSafe(p) : wt);
                }
                for (OpenTabsService.OpenTab tab : wsTabs) {
                    if (tab.pid() > 0 || (tab.sessionId() != null && !tab.sessionId().isBlank())) {
                        System.out.println("[RESTORE] " + p.getName()
                            + (wt.isBlank() ? "" : " [" + new File(wt).getName() + "]")
                            + " sub-" + tab.terminalSubId()
                            + " — old PID " + tab.pid()
                            + (tab.sessionId() != null && !tab.sessionId().isBlank()
                                ? ", " + tab.sessionType() + " session: " + tab.sessionId()
                                : ""));
                    }
                    String stype = tab.sessionType();
                    String sid   = tab.sessionId();
                    if (sid != null && !sid.isBlank()
                            && com.conload.ui.terminal.session.CliSessionController.isResumableType(stype)) {
                        com.conload.ui.projects.workspace.TerminalGroup g = projectTerminals.get(wsKey);
                        if (g != null && tab.terminalSubId() < g.size()) {
                            CopilotTerminalPane t = g.terminals().get(tab.terminalSubId());
                            if (t != null) t.setPendingResumeSession(stype, sid);
                        }
                    }
                }
            }
        }
        // Switch to the first restored project's last-saved workspace so
        // something visible matches the user's last session.
        if (!saved.isEmpty()) {
            String firstId = saved.get(0).projectId();
            String lastWt = "";
            for (OpenTabsService.OpenTab tab : saved)
                if (tab.projectId().equals(firstId)
                        && tab.worktreePath() != null && !tab.worktreePath().isBlank())
                    lastWt = tab.worktreePath();
            final String restoredWorktree = lastWt;
            projects.stream()
                .filter(p -> p.getId().equals(firstId))
                .findFirst()
                .ifPresent(p -> {
                    if (restoredWorktree.isBlank()) switchToProject(p);
                    else switchToWorkspace(p, restoredWorktree);
                });
        }
    }

    /** Mount the file tree for the given project into the left pane (lazy cache).
     *  Returns the (cached) pane so callers can re-root its CODE tree to a
     *  specific workspace directory via {@link ProjectFilesPane#setCodeRoot(File)}. */

    private String resolveWorkspaceSafe(Project p) {
        try { return projectService.resolveWorkspace(p).toString(); }
        catch (Exception ex) { return System.getProperty("user.home"); }
    }

    protected ProjectFilesPane mountLeftPaneForProject(String projectId, String workDir) {
        if (leftPaneHost == null) return null;
        // Resolve the project (for its display name) ahead of building the pane.
        Project proj = projectService.findById(projectId);
        ProjectFilesPane pane = projectFilesPanes.computeIfAbsent(projectId, id -> {
            File dir = new File(workDir);
            if (!dir.isDirectory()) dir = new File(System.getProperty("user.home"));
            ProjectFilesPane treePane = new ProjectFilesPane(dir, file -> {
                if (sharedPromptPanel != null) sharedPromptPanel.handleFileClick(file);
            });
            treePane.setOnAddLocalContext(this::showProjectLocalImport);
            treePane.setOnDownloadContext(this::showProjectAddContextView);
            treePane.setOnRemoveContext(this::showRemoveContextDialog);
            treePane.setOnRemoveSession(this::showRemoveSessionDialog);
            treePane.setOnPutToTerminal(this::sendToActiveTerminal);
            treePane.setOnViewMarkdown(this::showMarkdownViewer);
            treePane.setOnOpenSession(this::showSessionDetails);
            treePane.setOnRenameContextFolder(this::renameContextFolder);
            treePane.setOnRenameSessionFolder(this::renameSessionFolder);
            treePane.setOnRunWorkflow(this::runWorkflowFromContext);
            // Worktree lifecycle callbacks (create / list / switch / remove).
            // The section is only shown when the workspace is a git repo
            // (detected asynchronously by refreshWorktreesForActiveProject).
            treePane.setOnCreateWorktree(this::showCreateWorktreeDialog);
            treePane.setOnRefreshWorktrees(this::refreshWorktreesForActiveProject);
            treePane.setOnSelectWorktree(this::selectWorktree);
            treePane.setOnRemoveWorktree(this::removeWorktree);
            treePane.setOnTaskBadgeClick(() -> taskBadgeClickBackToResults(projectId));
            treePane.setOnActivateTerminalSession((wtPath, subIdx) -> {
                if (activeProjectId == null) return;
                Project p = projectService.findById(activeProjectId);
                if (p == null) return;
                switchToWorkspace(p, wtPath);
                activateTerminal(subIdx);
            });
            // Sidebar header: project name + the project's animated character
            // (matches the project tab indicator robot/cat/alien/yoda).
            if (proj != null) treePane.setProjectName(proj.getName());
            RobotIndicator.Character chr = projectCharacters.get(projectId);
            if (chr != null) treePane.setProjectCharacter(chr);
            // Apply this project's identity color to the file-tree header.
            String color = projectColors.get(projectId);
            if (color != null) treePane.setAccentColor(color);
            return treePane;
        });
        // Re-apply name + character + color on every mount (covers the cached-pane case).
        if (proj != null) pane.setProjectName(proj.getName());
        RobotIndicator.Character chr = projectCharacters.get(projectId);
        if (chr != null) pane.setProjectCharacter(chr);
        String color = projectColors.get(projectId);
        if (color != null) pane.setAccentColor(color);
        // Update context + session folder paths so the tree shows their icons.
        projectService.loadProjects().stream()
            .filter(p -> p.getId().equals(projectId))
            .findFirst()
            .ifPresent(p -> {
                pane.setContextFolderPaths(p.getContextFolders());
                pane.setSessionFolderPaths(p.getSessionFolders());
            });
        leftPaneHost.getChildren().setAll(pane);
        return pane;
    }


    protected void showProjectAddContextView(File folder) {
        if (folder == null || !folder.isDirectory() || contentArea == null) return;
        // Add-in-context always targets the project's Contexts directory (never the
        // clicked/selected subfolder). Falls back to the passed folder only when no
        // project is active (standalone case).
        File target = folder;
        if (activeProjectId != null) {
            Project current = projectService.findById(activeProjectId);
            if (current != null) {
                java.nio.file.Path contextsDir = projectService.resolveContextsDir(current);
                try { java.nio.file.Files.createDirectories(contextsDir); }
                catch (java.io.IOException ignore) {}
                target = contextsDir.toFile();
            }
        }
        addContextTargetFolder = target;
        addContextTargetProjectId = activeProjectId;
        // Collapse the prompt area so the download panel has maximum space.
        if (sharedPromptPanel != null) sharedPromptPanel.collapsePrompt();
        // Ensure contentArea is mounted in centerStack — the file tree is always
        // accessible now, so this might be triggered from a full-window view.
        showDashboard();
        // Give the download panel the larger share of vertical space by pinning
        // the terminal section to its minimum height. showDashboard() otherwise
        // grows the terminal (because the prompt is collapsed above). This is
        // restored automatically when restorePromptWorkspace() -> showDashboard()
        // re-applies the normal collapsed-prompt layout.
        if (centerStack != null) VBox.setVgrow(centerStack, Priority.ALWAYS);
        if (terminalSection != null) VBox.setVgrow(terminalSection, Priority.NEVER);
        if (terminalHost != null) VBox.setVgrow(terminalHost, Priority.NEVER);

        if (cachedDownloadPane == null) cachedDownloadPane = buildDownloadTab();
        Region downloadPane = cachedDownloadPane;

        // Compact top panel: back navigation + "Context Management" title.
        // Very small height (one line, tight vertical padding, bordered bottom).
        Button backBtn = new Button(Icons.BACK + " Back to Prompt");
        backBtn.getStyleClass().add("link-button");
        String backColor = projectColors.getOrDefault(activeProjectId, ProjectColors.DEFAULT);
        backBtn.setStyle("-fx-text-fill: " + backColor + ";");
        backBtn.setOnAction(e -> restorePromptWorkspace());

        Label contextTitle = new Label("Context Management");
        contextTitle.getStyleClass().addAll("title", "small");

        HBox headerPanel = new HBox(10, backBtn, contextTitle);
        headerPanel.setAlignment(Pos.CENTER_LEFT);
        headerPanel.setPadding(new Insets(4, 14, 4, 14));
        headerPanel.getStyleClass().add("panel-border-bottom");

        VBox wrapper = new VBox(0, headerPanel, downloadPane);
        Theme.classes(wrapper, Theme.CL_BG_APP);
        VBox.setVgrow(downloadPane, Priority.ALWAYS);
        contentArea.getChildren().setAll(wrapper);
        if (!isSearchRunning() && !searchResultsReady) Platform.runLater(this::openSearchDialog);
    }

    /** Re-mounts the shared context screen for any search or download badge state. */
    private void taskBadgeClickBackToResults(String projectId) {
        if (projectId == null || !projectId.equals(activeProjectId)) {
            Project p = projectService.findById(projectId);
            if (p != null) switchToProject(p);
        }
        if (sharedPromptPanel != null) sharedPromptPanel.collapsePrompt();
        showDashboard();
        if (centerStack != null) VBox.setVgrow(centerStack, Priority.ALWAYS);
        if (terminalSection != null) VBox.setVgrow(terminalSection, Priority.NEVER);
        if (terminalHost != null) VBox.setVgrow(terminalHost, Priority.NEVER);
        if (contentArea != null && cachedDownloadPane != null) {
            Button backBtn = new Button(Icons.BACK + " Back to Prompt");
            backBtn.getStyleClass().add("link-button");
            String backColor = projectColors.getOrDefault(projectId, ProjectColors.DEFAULT);
            backBtn.setStyle("-fx-text-fill: " + backColor + ";");
            backBtn.setOnAction(e -> restorePromptWorkspace());
            Label contextTitle = new Label("Context Management");
            contextTitle.getStyleClass().addAll("title", "small");
            HBox headerPanel = new HBox(10, backBtn, contextTitle);
            headerPanel.setAlignment(Pos.CENTER_LEFT);
            headerPanel.setPadding(new Insets(4, 14, 4, 14));
            headerPanel.getStyleClass().add("panel-border-bottom");
            VBox wrapper = new VBox(0, headerPanel, cachedDownloadPane);
            Theme.classes(wrapper, Theme.CL_BG_APP);
            VBox.setVgrow(cachedDownloadPane, Priority.ALWAYS);
            contentArea.getChildren().setAll(wrapper);
        }
    }


    /**
     * Removes a context folder from the active project: shows a destructive
     * confirmation, then (on a background thread) deletes the physical folder
     * and unregisters it from {@code projects.json}. Refreshes the file tree
     * and the management list on completion.
     */
    protected void showRemoveContextDialog(File folder) {
        showRemoveFolderDialog(folder, "Remove Context", "Remove context folder",
            "Failed to remove context: ",
            true, "remove-context-thread");
    }

    /**
     * Removes a session folder from the active project: shows a destructive
     * confirmation, then (on a background thread) deletes the physical folder
     * and unregisters it from {@code projects.json}. Refreshes the file tree
     * on completion.
     */
    protected void showRemoveSessionDialog(File folder) {
        showRemoveFolderDialog(folder, "Remove Session", "Remove session folder",
            "Failed to remove session: ",
            false, "remove-session-thread");
    }

    /** Shared destructive-confirm + background-delete + refresh for context/session folder removal. */
    private void showRemoveFolderDialog(File folder, String title, String header,
                                        String errorPrefix, boolean isContext, String threadName) {
        if (folder == null || !folder.isDirectory()) return;
        if (activeProjectId == null) return;

        String name = folder.getName();
        String path = folder.getAbsolutePath();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + name + "\" and all its contents?\n\n" + path + "\n\nThis cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle(title);
        confirm.setHeaderText(header);
        DialogStyler.style(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        BackgroundTasks.runIOTask(threadName, () -> {
            try {
                Project proj = projectService.findById(activeProjectId);
                java.util.List<String> registered = proj != null
                    ? (isContext ? proj.getContextFolders() : proj.getSessionFolders())
                    : java.util.List.of();
                if (registered.contains(path)) {
                    if (isContext) projectService.removeContextFolder(activeProjectId, path);
                    else projectService.removeSessionFolder(activeProjectId, path);
                } else {
                    projectService.deleteFolder(java.nio.file.Path.of(path));
                }
                javafx.application.Platform.runLater(() -> {
                    ProjectFilesPane pane = projectFilesPanes.get(activeProjectId);
                    if (pane != null) {
                        Project found = projectService.findById(activeProjectId);
                        if (found != null) {
                            if (isContext) pane.setContextFolderPaths(found.getContextFolders());
                            else pane.setSessionFolderPaths(found.getSessionFolders());
                        }
                        pane.refresh();
                    }
                    if (isContext) cachedProjectsPanel = buildProjectsTab();
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> showAlert("Error", errorPrefix + ex.getMessage()));
            }
        });
    }

    /**
     * Opens the Markdown viewer popup for a {@code .md} file. The popup is
     * editable when the file is {@code session_info.md} inside a session
     * folder; otherwise read-only.
     */
    protected void showMarkdownViewer(File mdFile) {
        if (mdFile == null || !mdFile.isFile()) return;
        Window owner = stage;
        // Editable for session_info.md files; read-only for all other .md.
        boolean editable = "session_info.md".equals(mdFile.getName());
        MarkdownViewerPopup popup = new MarkdownViewerPopup(mdFile, owner, editable);
        popup.show();
    }

    /**
     * Opens the session details modal for a registered session folder. Picks
     * the transcript {@code .md} file (the longest .md that isn't
     * {@code session_info.md}) next to {@code session_info.md}; opens it in
     * the read-only {@link MarkdownViewerPopup}. Falls back to
     * {@code session_info.md} when no transcript file is present.
     */
    protected void showSessionDetails(File sessionFolder) {
        if (sessionFolder == null || !sessionFolder.isDirectory()) return;
        File target = SessionFolderFiles.resolveMarkdown(sessionFolder);
        if (target == null || !target.isFile()) return;
        MarkdownViewerPopup popup = new MarkdownViewerPopup(target, stage, false);
        popup.show();
    }

    /**
     * Renames a context folder: physically moves the directory and updates the
     * path reference in {@code projects.json}. Runs on a background thread;
     * refreshes the file tree on completion.
     */
    protected void renameContextFolder(File folder, String newName) {
        if (folder == null || activeProjectId == null) return;
        performRename(folder, newName, true);
    }

    /**
     * Renames a session folder: physically moves the directory and updates the
     * path reference in {@code projects.json}. Runs on a background thread;
     * refreshes the file tree on completion.
     */
    protected void renameSessionFolder(File folder, String newName) {
        if (folder == null || activeProjectId == null) return;
        performRename(folder, newName, false);
    }

    /** Shared rename worker: calls {@link com.conload.service.ProjectService#renameContextFolder}
     *  or {@link com.conload.service.ProjectService#renameSessionFolder}, then
     *  refreshes the tree + management list. Shows an error alert on failure. */
    private void performRename(File folder, String newName, boolean isContext) {
        final String oldPath = folder.getAbsolutePath();
        BackgroundTasks.runIOTask("rename-folder-thread", () -> {
            try {
                String newPath = isContext
                    ? projectService.renameContextFolder(activeProjectId, oldPath, newName)
                    : projectService.renameSessionFolder(activeProjectId, oldPath, newName);
                if (newPath == null) {
                    javafx.application.Platform.runLater(() ->
                        showAlert("Rename failed", "Could not rename folder. It may not be registered, "
                            + "the target name may be invalid or already exist."));
                    return;
                }
                javafx.application.Platform.runLater(() -> {
                    ProjectFilesPane pane = projectFilesPanes.get(activeProjectId);
                    if (pane != null) {
                        Project found = projectService.findById(activeProjectId);
                        if (found != null) {
                            pane.setContextFolderPaths(found.getContextFolders());
                            pane.setSessionFolderPaths(found.getSessionFolders());
                        }
                        pane.refresh();
                    }
                    cachedProjectsPanel = buildProjectsTab();
                });
            } catch (IOException ex) {
                javafx.application.Platform.runLater(() ->
                    showAlert("Error", "Failed to rename: " + ex.getMessage()));
            }
        });
    }

    /** Re-run a workflow's prompt from an existing context folder.
     *  Reads {@code workflow-info.json}, reconstructs the {@link WorkflowContext},
     *  loads the template, substitutes variables, and pushes the prompt to the
     *  shared prompt panel — no API calls. */
    protected void runWorkflowFromContext(File contextFolder) {
        if (contextFolder == null || !contextFolder.isDirectory()) return;
        File infoFile = new File(contextFolder, "workflow-info.json");
        if (!infoFile.exists()) {
            showAlert("No Workflow", "This folder does not contain workflow metadata.");
            return;
        }
        try {
            var node = Json.MAPPER.readTree(infoFile);
            String workflowId = node.path("workflowId").asText("");
            Workflow workflow = WorkflowRegistry.findById(workflowId);
            if (workflow == null) {
                showAlert("Unknown Workflow", "Workflow \"" + workflowId + "\" is not registered.");
                return;
            }
            Path contextRoot = contextFolder.toPath();
            // The output doc lives in the shared contexts dir under
            // doc/cross_context (outside the context folder), so it is stored
            // as an absolute path. Legacy gathered folders store a path
            // relative to contextRoot — resolve those against contextRoot for
            // backward compatibility.
            String storedDocPath = node.path("outputDocPath").asText("");
            Path outputDoc = storedDocPath.isBlank() ? null
                    : (Path.of(storedDocPath).isAbsolute()
                        ? Path.of(storedDocPath)
                        : contextRoot.resolve(storedDocPath));
            // Use the live workspace path (active worktree or base) rather than
            // the value captured at gather time: the CLI agent runs in the
            // workspace that is active NOW, so the prompt must reference the
            // current tree, not a possibly-stale worktree that was open when
            // the context was first gathered.
            Project live = projectService.findById(activeProjectId);
            String liveWorkspace = resolveActiveWorkspace(live);
            WorkflowContext ctx = new WorkflowContext(
                    contextRoot, contextRoot, contextRoot, contextRoot,
                    node.path("jiraKeys").asText(""),
                    List.of(), List.of(), List.of(),
                    outputDoc,
                    node.path("repoOwnerRepo").asText(""),
                    node.path("confluenceDataSource").asText(""),
                    liveWorkspace);
            String prompt = workflow.buildPrompt(ctx);
            if (sharedPromptPanel != null) sharedPromptPanel.setPromptText(prompt);
            restorePromptWorkspace();
        } catch (Exception e) {
            showAlert("Workflow Error", "Failed to load workflow: " + e.getMessage());
        }
    }

    /** Link local folders to the project as contexts by reference (no copy).
     *  Each picked folder is registered via {@code addContextFolder}; removing
     *  a context later deletes the registered (original) folder from disk. */
    protected void showProjectLocalImport(File folder) {
        if (folder == null || !folder.isDirectory() || contentArea == null) return;

        new LocalContextFolderDialog(stage).showAndWait().ifPresent(selected -> {
            if (selected.isEmpty()) {
                showAlert("No Folders", "Please select at least one folder to link.");
                restorePromptWorkspace();
                return;
            }
            try {
                if (activeProjectId != null) {
                    Project current = projectService.findById(activeProjectId);
                    if (current != null) {
                        java.nio.file.Path projRoot = current.getParentPath() != null && !current.getParentPath().isBlank()
                                ? java.nio.file.Path.of(current.getParentPath()) : null;
                        int linked = 0;
                        for (java.nio.file.Path src : selected) {
                            String absPath = src.toAbsolutePath().toString();
                            // Safety guard: refuse to register the project root itself (or a
                            // parent of it) as a context — otherwise "Remove Context" could
                            // delete the entire project folder.
                            if (projRoot != null && (projRoot.equals(src) || projRoot.startsWith(src))) {
                                showAlert("Refused", "Cannot link a folder that is the project root or contains it:\n" + absPath);
                                continue;
                            }
                            projectService.addContextFolder(activeProjectId, absPath);
                            linked++;
                        }
                        ProjectFilesPane pane = projectFilesPanes.get(activeProjectId);
                        if (pane != null) {
                            Project found = projectService.findById(activeProjectId); if (found != null) pane.setContextFolderPaths(found.getContextFolders());
                            pane.refresh();
                            if (linked > 0 && !selected.isEmpty()) pane.highlightAndExpandPath(selected.get(0));
                        }
                        cachedProjectsPanel = buildProjectsTab();
                    }
                }
            } catch (Exception ex) {
                showAlert("Error", "Link failed: " + ex.getMessage());
            }
            restorePromptWorkspace();
        });
    }



    protected void restorePromptWorkspace() {
        addContextTargetFolder = null;
        addContextTargetProjectId = null;
        if (searchDialogStage != null) { searchDialogStage.hide(); searchDialogStage = null; }
        // Restore dashboard layout so contentArea is visible in centerStack.
        showDashboard();
        if (contentArea != null && sharedPromptPanel != null) {
            contentArea.getChildren().setAll(sharedPromptPanel);
        }
    }

    /** Empty-state hint shown before any project is selected. */

    protected void showEmptyLeftPane() {
        if (leftPaneHost == null) return;
        Label hint = new Label("No project selected.\nOpen or create a project from the top bar.");
        hint.getStyleClass().add("hint-padded");
        hint.setWrapText(true);
        StackPane.setAlignment(hint, Pos.TOP_LEFT);
        leftPaneHost.getChildren().setAll(hint);
    }

    /** Switch to the given project tab, restoring its last-selected workspace
     *  (base if none). The previous terminal is kept alive (running in
     *  background) so the user can come back to it. The process is only killed
     *  when the tab's close button is clicked. */
    protected void switchToProject(Project project) {
        switchToWorkspace(project, activeWorktreePath(project.getId()));
    }

    /** Legacy entry point — equivalent to selecting the base workspace. */
    protected void doSwitch(Project project) {
        switchToWorkspace(project, "");
    }

    // =========================================================================
    // Workspace switching (base + git worktrees)
    // =========================================================================

    /**
     * Switches the active project to the given <em>workspace</em> — either the
     * base (primary) repository checkout ({@code worktreePath} blank) or a
     * linked git worktree ({@code worktreePath} = the worktree's absolute path).
     * <p>Each workspace owns its own independent {@link CopilotTerminalPane}
     * (so an agent can run in several branches in parallel); switching just
     * swaps which terminal is mounted in {@link #terminalHost}. The left
     * panel's CODE tree is re-rooted to the workspace directory via
     * {@link ProjectFilesPane#setCodeRoot(File)}.
     */
    protected void switchToWorkspace(Project project, String worktreePath) {
        activeProjectId = project.getId();
        openProjectIds.add(project.getId()); // ensure registered as an open tab

        // Determine the effective workspace + working directory. A worktree
        // path that no longer exists on disk falls back to the base workspace
        // (so a removed/out-of-sync worktree can't break the terminal).
        String wt = (worktreePath == null) ? "" : worktreePath;
        String workDir;
        File wtDir = wt.isBlank() ? null : new File(wt);
        if (wt.isBlank() || !wtDir.isDirectory()) {
            if (!wt.isBlank()) {
                System.err.println("[WORKTREE] path not found, falling back to base: " + wt);
            }
            wt = "";
            try { workDir = projectService.resolveWorkspace(project).toString(); }
            catch (java.io.IOException ex) { workDir = System.getProperty("user.home"); }
        } else {
            workDir = wt;
        }

        selectedWorktreeByProject.put(project.getId(), wt);

        final String projectId  = project.getId();
        final String wsKey      = workspaceKey(projectId, wt);
        final String fworkDir   = workDir;

        CopilotTerminalPane terminal = terminalFactory.getOrCreate(project, wsKey, fworkDir);

        // Bind the subtab strip to this workspace's terminal group.
        com.conload.ui.projects.workspace.TerminalGroup group = projectTerminals.get(wsKey);
        if (terminalSubtabStrip != null) terminalSubtabStrip.bind(group);

        // If this workspace has a durable (saved) session but no live one yet,
        // inject it as a one-click resume pill in the terminal bar (next to the
        // PID). No-op when a session is already active/pending. Base uses the
        // project's lastSession*; a worktree uses the per-worktree session map.
        injectDurableSessionIfNeeded(terminal, project, wt);

        if (sharedPromptPanel != null) {
            sharedPromptPanel.setWorkDir(new File(workDir));
            // Apply the active project's color to the shared prompt panel so
            // the textarea selection highlight, template-row border, etc. use
            // the project's accent color (not the default white).
            String activeColor = projectColors.get(projectId);
            if (activeColor != null) sharedPromptPanel.setProjectColor(activeColor);
        }
        // One file-tree pane per project; re-root its CODE section to the
        // workspace dir (base or worktree). CONTEXT/WORKTREES sections stay
        // project-scoped regardless of the selected worktree.
        ProjectFilesPane pane = mountLeftPaneForProject(project.getId(), workDir);
        if (pane != null) {
            pane.setCodeRoot(new File(workDir));
            // Highlight the now-active worktree row immediately (green dot).
            pane.setCurrentWorktreePath(workDir);
            // On first activation of a project, asynchronously detect git +
            // populate the WORKTREES section. Subsequent workspace switches
            // within the same project only update the highlight (above).
            if (worktreesSectionInited.add(project.getId())) {
                refreshWorktreesForProject(project.getId(), false);
            }
        }
        showDashboard();
        if (terminalHost != null) {
            terminalHost.getChildren().setAll(terminal);
        }
        // Refresh the prompt-header right-cluster (mic/export/sessions) so it
        // reflects the now-active workspace's terminal.
        if (sharedPromptPanel != null) sharedPromptPanel.refreshActiveTerminal();
        // Check if opencode sessions exist → show Sessions button if so
        terminal.checkForCliSessions();
        // sessions availability may have changed — re-sync the prompt bar.
        if (sharedPromptPanel != null) sharedPromptPanel.refreshActiveTerminal();
        refreshProjectTabsBar();
        saveOpenTabs();
        // Persist the selected worktree so re-opening the project restores it.
        projectService.setLastWorktreePath(projectId, wt);
    }

    /** Switches the active sub-terminal for the current workspace. */
    protected void activateTerminal(int index) {
        if (activeProjectId == null) return;
        String wsKey = activeWorkspaceKey(activeProjectId);
        TerminalGroup group = projectTerminals.get(wsKey);
        if (group == null) return;
        group.setActive(index);
        CopilotTerminalPane terminal = group.active();
        if (terminal != null && terminalHost != null) {
            terminalHost.getChildren().setAll(terminal);
            if (sharedPromptPanel != null) sharedPromptPanel.refreshActiveTerminal();
            terminal.checkForCliSessions();
        }
        if (terminalSubtabStrip != null) terminalSubtabStrip.refreshActive();
        updateProjectTabStates();
        saveOpenTabs();
    }

    /** Creates a new sub-terminal in the current workspace. */
    protected void createNewTerminal() {
        if (activeProjectId == null) return;
        Project p = projectService.findById(activeProjectId);
        if (p == null) return;
        String wt = activeWorktreePath(activeProjectId);
        String wsKey = workspaceKey(activeProjectId, wt);
        String workDir = wt.isBlank() ? resolveWorkspaceSafe(p) : wt;
        CopilotTerminalPane terminal = terminalFactory.createNew(p, wsKey, workDir);
        TerminalGroup group = projectTerminals.get(wsKey);
        if (terminalHost != null) terminalHost.getChildren().setAll(terminal);
        if (terminalSubtabStrip != null) terminalSubtabStrip.bind(group);
        if (sharedPromptPanel != null) sharedPromptPanel.refreshActiveTerminal();
        saveOpenTabs();
    }

    /** Closes a sub-terminal within a group. */
    protected void closeTerminal(TerminalGroup group, int index) {
        if (group == null || index < 0 || index >= group.size()) return;
        CopilotTerminalPane t = group.terminals().get(index);
        if (t != null) t.closeTerminal();
        group.remove(index);
        CopilotTerminalPane active = group.active();
        if (active != null && terminalHost != null) {
            terminalHost.getChildren().setAll(active);
            if (sharedPromptPanel != null) sharedPromptPanel.refreshActiveTerminal();
        }
        if (terminalSubtabStrip != null) terminalSubtabStrip.bind(group);
        updateProjectTabStates();
        saveOpenTabs();
    }

    /**
     * Injects the durable (saved) session for this workspace into the terminal
     * so the bar shows a one-click resume pill next to the PID — but only when
     * the terminal has no live session yet and no pending resume already set.
     * <ul>
     *   <li>Base workspace ({@code wt} blank): reads {@code Project.lastSession*}.</li>
     *   <li>Worktree ({@code wt} non-blank): reads the per-worktree session map
     *       via {@code projectService.getWorktreeSession(...)}.</li>
     * </ul>
     * Only opencode/copilot sessions are wired for resume; other types are ignored.
     */
    private void injectDurableSessionIfNeeded(CopilotTerminalPane terminal, Project project, String wt) {
        if (terminal == null) return;
        String encoded;
        if (wt == null || wt.isBlank()) {
            // Base workspace → project-level lastSession.
            String lt = project.getLastSessionType();
            String li = project.getLastSessionId();
            if (li == null || li.isBlank()) return;
            encoded = lt + ":" + li;
        } else {
            // Worktree → per-worktree durable session ("type:id" or null).
            encoded = projectService.getWorktreeSession(project.getId(), wt);
        }
        if (encoded == null || encoded.isBlank()) return;
        String[] parts = ProjectService.decodeWorktreeSession(encoded);
        String stype = parts[0];
        String sid   = parts[1];
        if (sid.isBlank()) return;
        // Resume is gated by the CLI type's configured resumeCommand.
        if (!com.conload.ui.terminal.session.CliSessionController.isResumableType(stype)) return;
        terminal.injectDurableSession(stype, sid);
    }

    // =========================================================================
    // Git worktree lifecycle (create / list / switch / remove)
    // =========================================================================

    /** Resolves the base (primary) workspace path for a project — also where
     *  the git repo lives. Returns the user home on error. */
    private Path baseRepoDir(Project project) {
        try { return projectService.resolveWorkspace(project); }
        catch (IOException ex) { return Path.of(System.getProperty("user.home")); }
    }

    /** Effective working directory for a project's currently-selected
     *  workspace: the active worktree's path when one is selected and still
     *  exists on disk, otherwise the base (primary) workspace. Falls back to
     *  the user home on resolution error. Mirrors the fallback discipline in
     *  {@link #switchToWorkspace}. Used by workflow hosts (so workflow prompts
     *  and output-doc paths follow the worktree) and by workflow re-runs. */
    protected String resolveActiveWorkspace(Project project) {
        if (project == null) return System.getProperty("user.home");
        String wt = activeWorktreePath(project.getId());
        if (wt != null && !wt.isBlank()
                && new File(wt).isDirectory()) return wt;
        try { return projectService.resolveWorkspace(project).toString(); }
        catch (IOException ex) { return System.getProperty("user.home"); }
    }

    /** Refresh the WORKTREES section of the currently-active project (⟳ button). */
    protected void refreshWorktreesForActiveProject() {
        if (activeProjectId != null) refreshWorktreesForProject(activeProjectId, true);
    }

    /**
     * Background: detects whether the project's workspace is a git repo (show/hide
     * the WORKTREES section), optionally fetches all remotes, and repopulates
     * the worktree list. {@code fetch=true} mirrors the mock's "pull latest
     * branches" (used by ⟳ and Create); the auto-list on project open uses
     * {@code fetch=false} to avoid a network call on every activate.
     */
    protected void refreshWorktreesForProject(String projectId, boolean fetch) {
        Project project = projectService.findById(projectId);
        if (project == null) return;
        ProjectFilesPane pane = projectFilesPanes.get(projectId);
        if (pane == null) return;
        final Path repoDir = baseRepoDir(project);
        final String baseRepoPath = repoDir.toString();
        // `git worktree list` / `git fetch` are read-only (or network) and safe
        // to run concurrently, so we intentionally do NOT single-flight here:
        // doing so would silently skip listing for projects that activate
        // back-to-back during restore (B's refresh would see A's flag busy and
        // bail, leaving its section empty).
        pane.setWorktreeBusy(true);
        BackgroundTasks.runIOTask("worktree-list", () -> {
            try {
                boolean isRepo = gitWorktreeService.isGitRepo(repoDir);
                if (!isRepo) {
                    javafx.application.Platform.runLater(() ->
                            pane.setWorktreeSectionVisible(false));
                    return;
                }
                if (fetch) {
                    try { gitWorktreeService.fetchAll(repoDir); }
                    catch (Exception fe) { System.err.println("[WORKTREE] fetch failed: " + fe.getMessage()); }
                }
                java.util.List<Worktree> wts = gitWorktreeService.listWorktrees(repoDir);
                String cur = activeWorktreePath(projectId);
                if (cur == null || cur.isBlank()) cur = baseRepoPath;
                final String currentPath = cur;
                java.util.Map<String, java.util.List<com.conload.ui.projects.sidebar.WorktreeSessionBadge.SessionInfo>> labels = resolveWorktreeSessionLabels(projectId, wts, baseRepoPath);
                javafx.application.Platform.runLater(() -> {
                    pane.setWorktrees(wts, labels);
                    pane.setCurrentWorktreePath(currentPath);
                    pane.setWorktreeSectionVisible(true);
                });
            } catch (Exception e) {
                System.err.println("[WORKTREE] list failed: " + e.getMessage());
                javafx.application.Platform.runLater(() ->
                        pane.setWorktreeError(e.getMessage()));
            } finally {
                javafx.application.Platform.runLater(() -> pane.setWorktreeBusy(false));
            }
        });
    }

    /**
     * Builds a {@code worktree path → session display label} map for every
     * worktree of a project. The label is {@code "<Type>: <title>"} and is
     * resolved from (in priority):
     * <ol>
     *   <li>the live terminal's detected session (for the active worktree);</li>
     *   <li>the durable per-worktree session fallback stored on the project
     *       (worktreeSessions / lastSession), with the title resolved from the
     *       opencode sessions cache by id.</li>
     * </ol>
     * A blank/missing label means "no session" (the UI hides the pill).
     *
     * @param projectId   the project id
     * @param worktrees   the worktree list from {@code git worktree list}
     * @param baseRepoPath the primary checkout's absolute path (its session
     *                    is stored under the project's {@code lastSession*})
     */
    private java.util.Map<String, java.util.List<com.conload.ui.projects.sidebar.WorktreeSessionBadge.SessionInfo>> resolveWorktreeSessionLabels(
            String projectId, java.util.List<Worktree> worktrees, String baseRepoPath) {
        java.util.Map<String, java.util.List<com.conload.ui.projects.sidebar.WorktreeSessionBadge.SessionInfo>> labels = new java.util.HashMap<>();
        if (worktrees == null) return labels;
        java.util.Map<String,String> idToTitle = com.conload.sessionsprocessing.SessionProcessor.loadAllIdToTitles();
        for (Worktree w : worktrees) {
            String wtPath = w.getPath();
            if (wtPath == null || wtPath.isBlank()) continue;
            boolean isBase = w.isPrimary() || wtPath.equals(baseRepoPath);
            String wsKey = isBase ? baseKey(projectId) : workspaceKey(projectId, wtPath);
            java.util.List<com.conload.ui.projects.sidebar.WorktreeSessionBadge.SessionInfo> infos = new java.util.ArrayList<>();
            TerminalGroup group = projectTerminals.get(wsKey);
            if (group != null) {
                for (int i = 0; i < group.size(); i++) {
                    CopilotTerminalPane t = group.terminals().get(i);
                    String sid = t.getSessionId();
                    if (sid != null && !sid.isBlank()) {
                        String title = t.sessionTitleProperty().get();
                        if (title == null || title.isBlank()) title = idToTitle.get(sid);
                        infos.add(new com.conload.ui.projects.sidebar.WorktreeSessionBadge.SessionInfo(
                                i, sid, t.getSessionType(), title));
                    } else {
                        infos.add(new com.conload.ui.projects.sidebar.WorktreeSessionBadge.SessionInfo(
                                i, "", "", ""));
                    }
                }
            }
            if (infos.isEmpty()) {
                // Durable fallback (sub-terminal 0 only).
                String encoded;
                if (isBase) {
                    Project proj = projectService.findById(projectId);
                    if (proj == null) continue;
                    String li = proj.getLastSessionId();
                    if (li == null || li.isBlank()) continue;
                    encoded = proj.getLastSessionType() + ":" + li;
                } else {
                    encoded = projectService.getWorktreeSession(projectId, wtPath);
                }
                if (encoded == null || encoded.isBlank()) continue;
                String[] parts = ProjectService.decodeWorktreeSession(encoded);
                String stype = parts[0]; String sid = parts[1];
                if (sid.isBlank()) continue;
                infos.add(new com.conload.ui.projects.sidebar.WorktreeSessionBadge.SessionInfo(
                        0, sid, stype, idToTitle.get(sid)));
            }
            labels.put(wtPath, infos);
        }
        return labels;
    }

    private void refreshWorktreeSessionLabels(String projectId) {
        if (projectId == null) return;
        ProjectFilesPane pane = projectFilesPanes.get(projectId);
        if (pane == null || !pane.isWorktreeSectionVisible()) return;
        Project project = projectService.findById(projectId);
        if (project == null) return;
        final Path repoDir = baseRepoDir(project);
        BackgroundTasks.runIOTask("wt-session-refresh", () -> {
            try {
                java.util.List<Worktree> wts = gitWorktreeService.listWorktrees(repoDir);
                java.util.List<com.conload.ui.projects.sidebar.WorktreeSessionBadge.SessionInfo> unused = new java.util.ArrayList<>();
                java.util.Map<String, java.util.List<com.conload.ui.projects.sidebar.WorktreeSessionBadge.SessionInfo>> labels =
                        resolveWorktreeSessionLabels(projectId, wts, repoDir.toString());
                javafx.application.Platform.runLater(() -> pane.updateWorktreeSessions(labels));
            } catch (Exception e) { }
        });
    }
    /** One-click switch to a worktree row (the primary row switches to base). */
    protected void selectWorktree(Worktree w) {
        if (w == null || activeProjectId == null) return;
        Project project = projectService.findById(activeProjectId);
        if (project == null) return;
        // The primary (main) checkout row = the base workspace. Any other row
        // is a linked worktree identified by its path.
        String wtPath = w.isPrimary() ? "" : w.getPath();
        switchToWorkspace(project, wtPath);
        // Update the green-dot highlight immediately (refresh sets it too, but async).
        ProjectFilesPane pane = projectFilesPanes.get(activeProjectId);
        if (pane != null) pane.setCurrentWorktreePath(w.getPath());
    }

    /** Opens the "Create new worktree" dialog for the active project. On
     *  confirm, runs {@code git worktree add} on a background thread, refreshes
     *  the list, and auto-switches to the new worktree ("all in one click"). */
    protected void showCreateWorktreeDialog() {
        if (activeProjectId == null) return;
        Project project = projectService.findById(activeProjectId);
        if (project == null) return;
        final Path repoDir = baseRepoDir(project);
        CreateWorktreeDialog dlg = new CreateWorktreeDialog(stage, gitWorktreeService, repoDir);
        java.util.Optional<CreateWorktreeDialog.Result> res = dlg.showAndWait();
        res.ifPresent(result -> {
            BackgroundTasks.runIOTask("worktree-create", () -> {
                try {
                    gitWorktreeService.addWorktree(repoDir,
                            Path.of(result.targetPath()), result.branch());
                    javafx.application.Platform.runLater(() -> {
                        refreshWorktreesForProject(project.getId(), false);
                        switchToWorkspace(project, result.targetPath());
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() ->
                            showAlert("Create worktree failed", ex.getMessage()));
                }
            });
        });
    }

    /** Removes a linked worktree (the primary/base row is not removable).
     *  Confirms destructively, runs {@code git worktree remove} on a bg thread,
     *  discards that workspace's terminal, and refreshes. If the removed
     *  worktree was the active one, falls back to the base workspace. */
    protected void removeWorktree(Worktree w) {
        if (w == null || w.isPrimary() || activeProjectId == null) return;
        Project project = projectService.findById(activeProjectId);
        if (project == null) return;
        final Path repoDir = baseRepoDir(project);
        final Path target = Path.of(w.getPath());
        final String projectId = project.getId();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove worktree \"" + w.displayName() + "\"?\n\n" + target
                + "\n\nThis runs `git worktree remove` (the branch is NOT deleted). "
                + "This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Remove worktree");
        confirm.setHeaderText("Remove linked worktree");
        DialogStyler.style(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        BackgroundTasks.runIOTask("worktree-remove", () -> {
            try {
                gitWorktreeService.removeWorktree(repoDir, target, false);
            } catch (Exception ex) {
                System.err.println("[WORKTREE] remove failed, retrying with --force: " + ex.getMessage());
                try { gitWorktreeService.removeWorktree(repoDir, target, true); }
                catch (Exception ex2) {
                    javafx.application.Platform.runLater(() ->
                            showAlert("Remove worktree failed", ex2.getMessage()));
                    return;
                }
            }
            javafx.application.Platform.runLater(() -> {
                String wsKey = workspaceKey(projectId, w.getPath());
                com.conload.ui.projects.workspace.TerminalGroup grp = projectTerminals.remove(wsKey);
                if (grp != null) for (CopilotTerminalPane tp : grp.terminals()) tp.closeTerminal();
                if (w.getPath().equals(activeWorktreePath(projectId))) {
                    switchToWorkspace(project, "");
                }
                refreshWorktreesForProject(projectId, false);
                saveOpenTabs();
            });
        });
    }

    /** Show Config or About content as a centred overlay card. */
}
