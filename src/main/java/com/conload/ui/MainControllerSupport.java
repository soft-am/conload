package com.conload.ui;

import com.conload.ui.Icons;
import com.conload.ui.createcontext.GitHubActionResult;
import com.conload.ui.createcontext.GitHubPrResult;
import com.conload.ui.createcontext.ResultPanel;
import com.conload.ui.prompttemplate.PromptTemplatePanel;
import com.conload.ui.prompttemplate.QuickActionsBar;
import com.conload.ui.terminal.CopilotTerminalPane;
import com.conload.ui.terminal.RobotIndicator;
import com.conload.ui.projects.ProjectFilesPane;

import com.conload.confluence.ConfluenceUrlParser;
import com.conload.github.GitHubClient;
import com.conload.jira.JiraUrlParser;
import com.conload.model.AppConfig;
import com.conload.model.Project;
import com.conload.service.ConfigService;
import com.conload.service.GitWorktreeService;
import com.conload.service.OpenTabsService;
import com.conload.service.PidRegistryService;
import com.conload.service.ProjectService;
import com.conload.service.RecursivePageProcessor;
import com.conload.service.SearchDownloadService;
import com.conload.service.SpeechRecognitionService;
import com.conload.ui.components.UiFactory;
import com.conload.ui.createcontext.model.CriteriaType;
import com.conload.ui.createcontext.model.DownloadTarget;
import com.conload.ui.createcontext.model.JiraTableItem;
import com.conload.ui.createcontext.model.PageSearchResult;
import com.conload.ui.createcontext.model.PageTreeItem;
import com.conload.ui.createcontext.model.SearchCriterion;
import com.conload.ui.createcontext.model.SearchResults;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.*;
import javafx.geometry.Rectangle2D;
import javafx.scene.shape.SVGPath;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MainControllerSupport {

    // =========================================================================
    // Fields
    // =========================================================================

    protected final Stage stage;
    protected final ConfigService configService;

    // ── Overlay panels (Config / About shown via bottom icon bar) ────────────
    protected StackPane sceneOverlay;
    protected VBox overlayCard;
    protected javafx.scene.Node cachedConfigPanel;
    protected javafx.scene.Node cachedAboutPanel;

    // ── Config tab ───────────────────────────────────────────────────────────
    protected TextField usernameField;
    protected PasswordField tokenField;
    protected TextField baseUrlConfigField;
    protected Label configStatusLabel;
    protected PasswordField githubTokenField;
    protected TextField githubApiUrlField;
    protected TextField defaultExportFolderField;
    /** Global Confluence folder (workflow setting), edited on the Config tab. */
    protected TextField fullConfluenceFolderField;
    /** Terminal shell field on the Config tab (e.g. "zsh", "powershell").
     *  Blank means unset — the terminal refuses to open and proposes a
     *  detected shell. */
    protected TextField shellField;
    /** Container for the user-editable CLI type definition rows in the Config
     *  tab. Each row is a {@link com.conload.ui.CliTypeRow} holding five
     *  TextFields (label / detect / list / resume / export) + a Remove button. */
    protected javafx.scene.layout.VBox cliTypesContainer;

    // ── Confluence section ───────────────────────────────────────────────────
    protected String currentBaseUrl;
    protected Button recToggleBtn;
    protected boolean globalRecursive = true;

    // ── Jira section ─────────────────────────────────────────────────────────
    protected Label jiraStatusLabel;
    protected ProgressIndicator jiraSpinner;
    protected Label jiraLoadingLabel;
    protected StackPane jiraTableContainer;
    protected String jiraBaseUrl;

    // ── Shared download bottom ───────────────────────────────────────────────
    protected TextField savePathField;
    protected Button startButton;
    protected Button stopButton;
    /** Row containing Save/Stop buttons — visible only when search results exist. */
    protected HBox downloadBtnRow;
    protected TextArea logArea;
    protected ProgressBar progressBar;
    protected Label statusLabel;
    protected TitledPane logPane;
    protected ProgressIndicator downloadSpinner;
    protected Timeline ellipsisTimeline;
    protected Task<String> currentTask;
    protected final AtomicBoolean cancelled = new AtomicBoolean(false);
    protected String lastSessionPath = null;

    // ── GitHub tab ────────────────────────────────────────────────────────────
    protected Label githubStatusLabel;
    protected ProgressIndicator githubSpinner;
    /** Shared commit-detail viewer reused across all per-criterion GitHub commit panels. */
    protected TextArea githubDetailArea;
    /** Shared action-log viewer reused across all per-criterion GitHub Action panels. */
    protected TextArea githubActionDetailArea;

    // ── F7: Universal search ───────────────────────────────────────────────────
    protected Button uniSearchBtn;
    protected Button uniStopBtn;
    protected Label uniStatusLabel;
    protected Task<?> uniTask;
    protected final AtomicBoolean uniCancelled = new AtomicBoolean(false);

    // ── Search results container ───────────────────────────────────────────────
    protected VBox resultsContainer;
    /** Bordered box wrapping the criteria-info + summary labels. */
    protected VBox searchSummaryBox;
    /** Lists the criteria (keyword + active criteria) used for the last search. */
    protected Label searchCriteriaInfoLabel;
    protected Label searchSummaryLabel;

    // ── Dynamic search criteria (populated via inline cascading rows) ──────────
    protected final List<SearchCriterion> activeCriteria = new ArrayList<>();
    protected VBox criteriaRowsBox;

    // ── Result panels (hidden until results arrive) ──────────────────────────
    // Each non-Confluence-tree result type renders one ResultPanel per
    // CRITERION (mirroring the confTrees pattern): an outer "type-section"
    // ResultPanel wraps a container VBox that holds one per-criterion
    // ResultPanel + TableView/tree each. Entry lists track the per-criterion
    // tables/trees so the download step can aggregate selected rows across
    // all criteria of a given type.
    protected ResultPanel<PageSearchResult> rpConfSearch;
    protected TableView<PageSearchResult> confSearchTable;
    protected VBox confTreesContainer;
    protected ResultPanel<?> rpConfTrees;
    protected final java.util.List<ConfTreeEntry> confTreeEntries = new java.util.ArrayList<>();

    protected ResultPanel<?> rpJiraSearch;
    protected VBox jiraSearchContainer;
    protected final java.util.List<JiraSearchEntry> jiraSearchEntries = new java.util.ArrayList<>();

    protected ResultPanel<?> rpJiraUrl;
    protected VBox jiraUrlContainer;
    protected final java.util.List<JiraUrlEntry> jiraUrlEntries = new java.util.ArrayList<>();

    protected ResultPanel<?> rpGitHub;
    protected VBox githubContainer;
    protected final java.util.List<GitHubCommitEntry> githubCommitEntries = new java.util.ArrayList<>();

    protected ResultPanel<?> rpGitHubAction;
    protected VBox githubActionContainer;
    protected final java.util.List<GitHubActionEntry> githubActionEntries = new java.util.ArrayList<>();

    protected ResultPanel<?> rpGitHubPr;
    protected VBox githubPrContainer;
    protected final java.util.List<GitHubPrEntry> githubPrEntries = new java.util.ArrayList<>();

    /** Tracks one Confluence page tree (TreeView + panel) per Confluence URL criterion. */
    public record ConfTreeEntry(TreeView<PageTreeItem> treeView, ResultPanel<?> panel) {}
    /** Tracks one Jira keyword-search result table per criterion. */
    public record JiraSearchEntry(TableView<JiraTableItem> table, ResultPanel<?> panel) {}
    /** Tracks one Jira issue-from-URL result table per criterion. */
    public record JiraUrlEntry(TableView<JiraTableItem> table, ResultPanel<?> panel) {}
    /** Tracks one GitHub commit result table per criterion. */
    public record GitHubCommitEntry(TableView<GitHubClient.GitCommit> table, ResultPanel<?> panel) {}
    /** Tracks one GitHub Action result table per criterion. */
    public record GitHubActionEntry(TableView<GitHubActionResult> table, ResultPanel<?> panel) {}
    /** Tracks one GitHub PR result table per criterion. */
    public record GitHubPrEntry(TableView<GitHubPrResult> table, ResultPanel<?> panel) {}

    // ── Download target selection ──────────────────────────────────────────────
    protected TextField newContextNameField;

    // ── Speech-to-text (shared Vosk service) ───────────────────────────────────
    protected SpeechRecognitionService speechService;

    // ── Project session management ────────────────────────────────────────────
    protected final ProjectService projectService;
    /** Persists open project tabs + terminal PIDs across restarts. */
    protected final OpenTabsService openTabsService = new OpenTabsService();
    /** Live registry of every running terminal PTY, persisted to
     *  {@code src/terminal_pids.json} so leftover shells can be reaped after a
     *  force-quit on the next launch. */
    protected final PidRegistryService pidRegistry = new PidRegistryService();
    /** Orchestrates search and download operations off the JavaFX thread. */
    protected final SearchDownloadService searchDownloadService = new SearchDownloadService();
    /** One terminal pane per workspace key (projectId for the base workspace,
     *  or {@code projectId + "\u0001" + worktreePath} for a git worktree) —
     *  created lazily, kept alive until the project tab is closed. */
    protected final Map<String, CopilotTerminalPane> projectTerminals = new HashMap<>();
    /** Per-project identity color (hex string) — assigned when a project is first opened. */
    protected final Map<String, String> projectColors = new HashMap<>();
    /** Per-project animated character (robot/cat/alien/yoda) — picked at random
     *  independently of the color when a project is first opened. */
    protected final Map<String, RobotIndicator.Character> projectCharacters = new HashMap<>();
    /** The worktree path currently selected for each open project (blank =
     *  base/primary workspace). Drives workspace-key derivation on switch. */
    protected final Map<String, String> selectedWorktreeByProject = new HashMap<>();
    /** Runs {@code git worktree}/{@code git fetch} commands off the FX thread. */
    protected final GitWorktreeService gitWorktreeService = new GitWorktreeService();
    /** The content area (right side of SplitPane) — children are swapped when switching views. */
    protected StackPane contentArea;
    /** Currently active project id (null = no project open). */
    protected String activeProjectId = null;
    /** Cached Projects management panel. */
    protected javafx.scene.Node cachedProjectsPanel;

    protected SplitPane splitPane;
    protected HBox projectTabsBar;
    protected PromptTemplatePanel sharedPromptPanel;
    protected QuickActionsBar sharedQuickActionsBar;
    protected StackPane terminalHost;
    protected VBox terminalSection;
    /** Horizontal separator between the quick actions bar and the terminal.
     *  Tinted in the active project's accent color (see .qa-terminal-separator). */
    protected javafx.scene.control.Separator terminalSeparator;
    protected javafx.scene.Node cachedPromptsPanel;
    /** Cached Help / user-guide panel. */
    protected javafx.scene.Node cachedHelpPanel;
    /** Project IDs currently open as tabs in the top ribbon. */
    protected final java.util.Set<String> openProjectIds = new java.util.LinkedHashSet<>();
    /** Host for the current active project's file tree (left pane). */
    protected StackPane leftPaneHost;
    /** Lazy cache of file trees per project id. */
    protected final Map<String, ProjectFilesPane> projectFilesPanes = new HashMap<>();
    /** Center area — normally the split (files | prompt); swapped for full-window views. */
    protected StackPane centerStack;
    /** Cached dashboard node so it can be restored after full-window Projects/Contexts views. */
    protected javafx.scene.Node dashboardCenter;
    /** Target folder selected from project tree when running Add Context from project view. */
    protected File addContextTargetFolder;
    /** Project id the new context (downloaded or imported) should be registered with. */
    protected String addContextTargetProjectId;
    /** Non-modal search popup stage — rebuilt lazily when null. */
    protected Stage searchDialogStage;

    protected MainControllerSupport(Stage stage, ConfigService configService) {
        this.stage = stage;
        this.configService = configService;
        this.projectService = new ProjectService();
    }

    protected abstract void selectInitialProject();

    // =========================================================================
    // Workspace-key helpers
    // =========================================================================

    /** The base workspace's terminal-key = the project id (unchanged from
     *  pre-worktree behaviour, so projects with no worktrees work as before). */
    protected static String baseKey(String projectId) { return projectId; }

    /** Terminal-map key for a given (project, worktree). Blank {@code worktreePath}
     *  ⇒ the base workspace ⇒ the project id itself. */
    protected static String workspaceKey(String projectId, String worktreePath) {
        if (worktreePath == null || worktreePath.isBlank()) return projectId;
        return projectId + "\u0001" + worktreePath;
    }

    /** Splits a terminal-map key into {@code [projectId, worktreePath]}. */
    protected static String[] splitKey(String key) {
        if (key == null) return new String[]{"", ""};
        int i = key.indexOf('\u0001');
        return i < 0 ? new String[]{key, ""} : new String[]{key.substring(0, i), key.substring(i + 1)};
    }

    /** Project-id half of a terminal-map key. */
    protected static String keyProjectId(String key) { return splitKey(key)[0]; }

    /** Worktree-path half of a terminal-map key (blank = base workspace). */
    protected static String keyWorktreePath(String key) { return splitKey(key)[1]; }

    /** The worktree path currently selected for {@code projectId} (blank = base). */
    protected String activeWorktreePath(String projectId) {
        return selectedWorktreeByProject.getOrDefault(projectId, "");
    }

    /** Terminal-map key for the project's currently-selected workspace. */
    protected String activeWorkspaceKey(String projectId) {
        return workspaceKey(projectId, activeWorktreePath(projectId));
    }

    // =========================================================================
    // Open-tabs persistence
    // =========================================================================

    /** Save the currently open project tabs + terminal PIDs to disk. One entry
     *  per <em>workspace</em> (the base terminal + each open worktree terminal)
     *  so every worktree's session restores independently on restart. */
    public void saveOpenTabs() {
        List<OpenTabsService.OpenTab> tabs = new ArrayList<>();
        for (String id : openProjectIds) {
            for (Map.Entry<String, CopilotTerminalPane> e : projectTerminals.entrySet()) {
                if (!id.equals(keyProjectId(e.getKey()))) continue;
                CopilotTerminalPane t = e.getValue();
                String wt = keyWorktreePath(e.getKey());
                long pid = (t != null) ? t.getPid() : -1;
                String sid = (t != null) ? t.getSessionId() : "";
                String stype = (t != null) ? t.getSessionType() : "";
                tabs.add(OpenTabsService.of(id, wt, pid, sid, stype));
                // Mirror the live session into the durable Project record so
                // it survives a tab close/restart. Base → lastSession*; a
                // worktree → the per-worktree session map. Only persisted for
                // non-blank ids (blank leaves the field untouched so a
                // transient empty state doesn't clobber a known session).
                if (sid != null && !sid.isBlank()) {
                    if (wt.isBlank()) projectService.updateLastSession(id, stype, sid);
                    else projectService.updateWorktreeSession(id, wt, stype, sid);
                }
            }
        }
        openTabsService.save(tabs);
    }

    /**
     * Full graceful shutdown of all live terminal PTYs, called from the window
     * close handler and the JVM shutdown hook. Performs <strong>no UI
     * operations</strong> so it is safe to invoke from a non-FX thread.
     * <ol>
     *   <li>saves open tabs + session ids (so the next launch restores them),</li>
     *   <li>forcefully destroys every live PTY and its descendant processes
     *       (shells, opencode/copilot CLIs),</li>
     *   <li>clears the live pid registry file.</li>
     * </ol>
     * For the {@code kill -9} / Force-Quit case (no hook can run), the
     * leftover shells are reaped by {@link PidRegistryService#killAllAliveAndClear()}
     * on the next launch — see {@code AppShellController.buildScene()}.
     */
    public void shutdownAll() {
        try {
            saveOpenTabs();
        } catch (Exception e) {
            System.err.println("[SHUTDOWN] saveOpenTabs failed: " + e.getMessage());
        }
        // Destroy every live PTY (no UI ops → shutdown-hook safe). Copy the
        // values first to avoid ConcurrentModification if a listener re-enters.
        java.util.List<CopilotTerminalPane> panes;
        synchronized (projectTerminals) {
            panes = new java.util.ArrayList<>(projectTerminals.values());
        }
        for (CopilotTerminalPane t : panes) {
            try {
                if (t != null) t.destroyPtyNoUi();
            } catch (Exception e) {
                System.err.println("[SHUTDOWN] destroyPtyNoUi failed: " + e.getMessage());
            }
        }
        try {
            pidRegistry.killAllAliveAndClear();
        } catch (Exception e) {
            System.err.println("[SHUTDOWN] pidRegistry clear failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // Scene assembly
    // =========================================================================

    protected void showDashboard() {
        if (centerStack != null && dashboardCenter != null
            && (centerStack.getChildren().isEmpty() || centerStack.getChildren().get(0) != dashboardCenter)) {
            centerStack.getChildren().setAll(dashboardCenter);
        }
        setTerminalChromeVisible(true);
        // Restore VGrow based on prompt collapsed state: if prompt is collapsed,
        // centerStack should NOT grow (terminal fills the space).
        if (sharedPromptPanel != null && !sharedPromptPanel.isPromptExpanded()) {
            VBox.setVgrow(centerStack, Priority.NEVER);
            if (terminalSection != null) VBox.setVgrow(terminalSection, Priority.ALWAYS);
            if (terminalHost != null)   VBox.setVgrow(terminalHost, Priority.ALWAYS);
        }
    }

    /** Swap the center area for a full-window view (Projects / Contexts) with a back bar. */

    protected void showFullWindow(String title, javafx.scene.Node body) {
        if (centerStack == null) return;
        setTerminalChromeVisible(false);
        // Restore centerStack visibility — an expanded terminal hides it, but
        // full-window views (Projects / Prompts) need it visible to show content.
        UiFactory.show(centerStack);
        VBox.setVgrow(centerStack, Priority.ALWAYS);
        Button back = new Button(Icons.BACK + " Back");
        back.getStyleClass().add("link-button");
        back.setOnAction(e -> {
            if (activeProjectId != null) showDashboard();
            else selectInitialProject();
            if (activeProjectId == null) showDashboard();
        });
        Label lbl = new Label(title);
        lbl.getStyleClass().add("title");
        Region sp = UiFactory.hSpacer();
        HBox navBar = new HBox(10, back, lbl, sp);
        navBar.setAlignment(Pos.CENTER_LEFT);
        navBar.setPadding(new Insets(8, 14, 8, 14));
        navBar.getStyleClass().add("panel-border-bottom");

        VBox root = new VBox(navBar, body);
        VBox.setVgrow(body, Priority.ALWAYS);
        Theme.classes(root, Theme.CL_BG_APP);
        centerStack.getChildren().setAll(root);
    }

    /** Hide/show the terminal ribbon and the shared quick-actions bar around it. */

    protected void setTerminalChromeVisible(boolean visible) {
        if (terminalSection != null) {
            UiFactory.setVisible(terminalSection, visible);
        }
        if (sharedQuickActionsBar != null) {
            UiFactory.setVisible(sharedQuickActionsBar, visible);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Switch the content area to the given project's terminal.
     * If the current terminal is busy, asks the user what to do first.
     */

    protected void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(stage);
        DialogStyler.style(alert);
        alert.showAndWait();
    }
}
