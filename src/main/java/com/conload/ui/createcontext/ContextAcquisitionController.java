package com.conload.ui.createcontext;

import com.conload.ui.Icons;
import com.conload.ui.ManagementScreensController;
import com.conload.ui.Theme;
import com.conload.ui.components.SvgIcon;
import com.conload.ui.components.UiFactory;
import com.conload.ui.projects.ProjectFilesPane;

import com.conload.model.AppConfig;
import com.conload.model.Project;
import com.conload.service.ConfigService;
import com.conload.service.ProjectService;
import com.conload.service.RecursivePageProcessor;
import com.conload.service.SearchDownloadService;
import com.conload.service.search.SourceInputClassifier;
import com.conload.ui.createcontext.model.CriteriaType;
import com.conload.ui.workflow.WorkflowHost;
import com.conload.ui.createcontext.model.DownloadTarget;
import com.conload.ui.createcontext.model.PageSearchResult;
import com.conload.ui.createcontext.model.PageTreeItem;
import com.conload.ui.createcontext.model.SearchCriterion;
import com.conload.ui.createcontext.model.SearchResults;
import com.conload.ui.createcontext.presenter.SearchResultsPresenter;
import com.conload.ui.createcontext.presenter.SearchResultTables;
import com.conload.ui.createcontext.search.SearchExecutionController;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ContextAcquisitionController extends ManagementScreensController {

    private SearchResultsPresenter searchResultsPresenter;
    private SearchResultsPresenter.View searchResultsView;
    private SearchExecutionController searchExecutionController;

    protected ContextAcquisitionController(Stage stage, ConfigService configService) {
        super(stage, configService);
    }

    protected abstract void restorePromptWorkspace();

    /** Provides the workflow host (config, workspace, contexts dir, registerContext)
     *  used to run cross-context gathering from the download-context popup. */
    protected abstract WorkflowHost workflowHost();

    // ── Search-popup section references (transient; valid only while popup is open) ──
    private VBox popupContent;
    private ScrollPane popupInputsScroll;
    private HBox popupSearchBtnRow;
    /** Project id captured at search start so the sidebar badge stays on the
     *  originating project's pane even if the user switches projects mid-search. */
    private String searchProjectId;
    /** True when search has completed with results visible (badge shows
     *  "Results ready" and re-entry preserves results instead of clearing). */
    protected boolean searchResultsReady;
    /** Cached download-tab region — rebuilt only once, re-attached on re-entry. */
    protected Region cachedDownloadPane;
    // ── Main-tab holders (results + download-btn row move into the popup, then back) ──
    private ScrollPane mainResultsScroll;
    private VBox mainFooter;
    // ── Main-tab download-state views + search launcher ──
    // resultsContainer never returns to the main tab; during a download the center
    // shows downloadInProgressView, and after it finishes, downloadCompleteView.
    private Button searchContextBtn;
    private BorderPane mainShell;
    private VBox downloadInProgressView;
    private VBox downloadCompleteView;
    private VBox noResultsView;
    private SearchCriteriaPane searchCriteriaPane;
    private DownloadProgressPane downloadProgressPane;
    private ContextDownloadController downloadController;
    private CrossContextSection crossContextSection;

    protected Region buildDownloadTab() {
        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("bg-app");
        mainShell = shell;

        VBox root = new VBox(0);
        root.setPadding(Theme.PAD_SECTION);
        Theme.classes(root, Theme.CL_BG_APP);
        root.getChildren().add(buildSearchLauncher());

        VBox resultsBox = buildResultsContainer();
        VBox.setMargin(resultsBox, new Insets(8, 0, 0, 0));
        ScrollPane resultsScroll = UiFactory.scrollable(resultsBox);
        resultsScroll.setMinHeight(400);
        VBox.setVgrow(resultsScroll, Priority.ALWAYS);
        mainResultsScroll = resultsScroll;
        noResultsView = buildNoResultsView();

        downloadController = new ContextDownloadController(new DownloadHost());
        downloadProgressPane = downloadController.buildProgressPane();
        VBox footer = downloadProgressPane.buildFooter();
        downloadInProgressView = downloadProgressPane.inProgressView();
        startButton = downloadProgressPane.startButton();
        stopButton = downloadProgressPane.stopButton();
        downloadBtnRow = downloadProgressPane.buttonRow();
        progressBar = downloadProgressPane.progressBar();
        statusLabel = downloadProgressPane.statusLabel();
        logArea = downloadProgressPane.logArea();
        logPane = downloadProgressPane.logPane();
        downloadSpinner = downloadProgressPane.spinner();
        mainFooter = footer;
        uniStatusLabel = downloadProgressPane.searchStatusLabel();
        uniStopBtn = UiFactory.errorButton("Stop");
        uniStopBtn.setDisable(true);
        uniStopBtn.setOnAction(e -> stopUniversalSearch());
        downloadProgressPane.searchStatusRow().getChildren().add(uniStopBtn);

        shell.setTop(root);
        shell.setCenter(resultsScroll);
        shell.setBottom(footer);
        return shell;
    }

    private VBox buildNoResultsView() {
        Label label = new Label("No results found");
        label.getStyleClass().addAll("placeholder", "context-no-results");
        VBox view = new VBox(label);
        view.setAlignment(Pos.CENTER);
        view.setMinHeight(400);
        return view;
    }

    private VBox buildSearchLauncher() {
        SvgIcon jiraIcon = new SvgIcon("/images/jira-logo.svg", 20, "-app-panel");
        SvgIcon confluenceIcon = new SvgIcon("/images/confluence-logo.svg", 20, "-app-panel");
        SvgIcon githubIcon = new SvgIcon("/images/github-logo.svg", 20, "-app-panel");
        Label label = new Label("Search Context");
        label.getStyleClass().add("search-context-title");
        HBox content = new HBox(10, jiraIcon, confluenceIcon, githubIcon, label);
        content.setAlignment(Pos.CENTER);
        searchContextBtn = new Button();
        searchContextBtn.setGraphic(content);
        searchContextBtn.getStyleClass().add("search-context-btn");
        searchContextBtn.setOnAction(e -> openSearchDialog());
        VBox panel = new VBox(searchContextBtn);
        panel.setAlignment(Pos.CENTER);
        panel.getStyleClass().add("search-context-panel");
        VBox.setMargin(panel, new Insets(8, 0, 8, 0));
        return panel;
    }

    // ── Search popup — holds keyword + criteria + search/stop/reset/close ──────


    protected void openSearchDialog() {
        if (currentTask != null && currentTask.isRunning()) return;
        if (isSearchRunning()) return;
        if (searchDialogStage != null && searchDialogStage.isShowing()) {
            searchDialogStage.toFront();
            return;
        }

        clearSearchState();
        Stage popup = new Stage();
        popup.initOwner(stage);
        popup.initModality(Modality.NONE);
        popup.setTitle("Search Context");
        popup.setMinWidth(680);
        popup.setMinHeight(480);
        searchDialogStage = popup;
        popupContent = buildPopupContent();
        popupContent.getStyleClass().addAll("bg-app", "popup-window-root");

        popup.setMinWidth(800);
        popup.setMinHeight(410);
        double popW = Math.max(UiFactory.widePopupWidth(stage), 800);
        double popH = Math.max(stage.getHeight() * 0.55, 410);
        Scene popupScene = new Scene(popupContent, popW, popH);
        Theme.apply(popupContent);
        popup.setScene(popupScene);
        popup.setOnCloseRequest(e -> closeSearchPopup());
        popup.show();
    }

    private VBox buildPopupContent() {
        searchCriteriaPane = new SearchCriteriaPane(criteria -> {
            activeCriteria.clear();
            activeCriteria.addAll(criteria);
            updateSearchButtonState();
        }, () -> uniStopBtn != null && !uniStopBtn.isDisable());
        criteriaRowsBox = searchCriteriaPane.rows();
        Button addCriteriaBtn = UiFactory.accentButton("+");
        addCriteriaBtn.setOnAction(e -> searchCriteriaPane.addRow());
        uniSearchBtn = UiFactory.accentButton("Search");
        uniSearchBtn.setDisable(true);
        uniSearchBtn.setOnAction(e -> runSearch());
        searchExecutionController = new SearchExecutionController(searchDownloadService,
            new SearchExecutionController.SearchUi(() -> activeCriteria,
                () -> usernameField.getText(), () -> tokenField.getText(),
                () -> githubTokenField == null ? "" : githubTokenField.getText(),
                () -> githubApiUrlField == null ? "" : githubApiUrlField.getText(),
                this::resolveConfBaseUrl, this::resolveJiraBaseUrl, uniSearchBtn, uniStopBtn,
                uniStatusLabel, this::setSearching, this::finalizeResults,
                this::resetResultPanels, this::updateSearchButtonState, this::appendLog),
            new SearchExecutionController.Callbacks() {
                @Override public void succeeded(SearchResults results, SearchExecutionController.SearchPlan plan) {
                    populateSearchResults(results, plan.confBase());
                    logSearchResults(results);
                    displayResultsSummary(results);
                    if (!hasVisibleResults())
                        setUniStatus(Icons.WARNING + "  No results found — adjust criteria and retry", "warning");
                    applyResultsCenterView();
                    updateSaveButtonState();
                }
                @Override public void failed(Throwable error) { }
                @Override public void cancelled() { }
            });
        updateSearchButtonState();
        popupSearchBtnRow = new HBox(6, uniSearchBtn);
        popupSearchBtnRow.setAlignment(Pos.CENTER_LEFT);
        popupSearchBtnRow.setPadding(Theme.PAD_ROW);
        popupSearchBtnRow.getStyleClass().add("panel-border-top");
        VBox middle = new VBox(8, criteriaRowsBox, addCriteriaBtn);
        middle.setPadding(Theme.PAD_ROW);
        Theme.classes(middle, Theme.CL_BG_APP);
        popupInputsScroll = UiFactory.scrollable(middle);
        VBox.setVgrow(popupInputsScroll, Priority.ALWAYS);
        crossContextSection = new CrossContextSection(workflowHost(),
                (source, seed, fullMode) -> downloadController.startCrossContextGather(source, seed, fullMode));
        return new VBox(0, popupInputsScroll, popupSearchBtnRow, crossContextSection.view());
    }

    // ── Search lifecycle: popup closes on start, progress shows on main tab ────

    /** True when a search task is running on the background thread. */
    protected boolean isSearchRunning() {
        return searchExecutionController != null && searchExecutionController.isRunning();
    }

    /** Closes the search popup (if open), reveals the search-progress bar + log
     *  on the main tab, and shows a sidebar badge on the originating project's
     *  pane so the user knows a search is running even after switching tabs. */
    private void setSearching(boolean searching) {
        if (searching) {
            searchProjectId = activeProjectId;
            searchResultsReady = false;
            closeSearchPopup();
            if (searchContextBtn != null) UiFactory.hide(searchContextBtn);
            if (mainShell != null && downloadProgressPane != null)
                mainShell.setCenter(downloadProgressPane.searchInProgressView());
            downloadProgressPane.setSearching(true);
            showSearchCriteriaInfo();
            ProjectFilesPane badgePane = searchProjectId == null ? null
                    : projectFilesPanes.get(searchProjectId);
            if (badgePane != null) badgePane.showTaskBadge("search", "Searching…", true, null);
        } else {
            downloadProgressPane.setSearching(false);
            if (searchContextBtn != null) UiFactory.show(searchContextBtn);
            ProjectFilesPane badgePane = searchProjectId == null ? null
                    : projectFilesPanes.get(searchProjectId);
            if (badgePane != null) badgePane.hideTaskBadge("search");
        }
    }

    /** Shows the {@code searchCriteriaInfoLabel} with the project name + active
     *  criteria so the user can see what is being searched while the popup is
     *  closed and the progress bar is running on the main panel. */
    private void showSearchCriteriaInfo() {
        String projectName = "";
        if (searchProjectId != null) {
            Project p = projectService.findById(searchProjectId);
            if (p != null) projectName = p.getName();
        }
        String criteria = formatActiveSearchCriteria();
        String info = projectName.isBlank() ? criteria : projectName + "  ·  " + criteria;
        if (info != null && !info.isBlank()) {
            searchCriteriaInfoLabel.setText(Icons.INFO + "  " + info);
            UiFactory.show(searchCriteriaInfoLabel);
        }
    }

    /** Stops the search-progress presentation and ensures the results container
     *  is visible in the main panel. Keeps a "Results ready" badge (no spinner)
     *  on the sidebar so the user can navigate back to this screen. */
    private void finalizeResults() {
        setSearching(false);
        if (mainShell != null && mainResultsScroll != null)
            mainShell.setCenter(mainResultsScroll);
        if (downloadBtnRow != null) UiFactory.show(downloadBtnRow);
        searchResultsReady = true;
        ProjectFilesPane badgePane = searchProjectId == null ? null
                : projectFilesPanes.get(searchProjectId);
        if (badgePane != null) badgePane.showTaskBadge("search", Icons.CHECK + " Search results ready", false, null);
    }

    /** Mounts the results area or the empty-state view, based on populated panels.
     *  Must run after {@code populateSearchResults} so panel visibility is current. */
    private void applyResultsCenterView() {
        if (mainShell == null || mainResultsScroll == null) return;
        mainShell.setCenter(hasVisibleResults() ? mainResultsScroll : noResultsView);
    }

    /** Hides the search popup (if open). The popup is criteria-entry-only; it
     *  closes on search start and does not host results or searching state. */
    private void closeSearchPopup() {
        if (searchDialogStage != null) {
            searchDialogStage.hide();
        }
        searchDialogStage = null;
    }

    // ── Inline cascading criteria rows (source → subtype → input, one line) ──

    protected HBox createSearchCriteriaRow() {
        return searchCriteriaPane.createSearchCriteriaRow();
    }

    /**
     * Rebuilds activeCriteria from the inline criteria rows, wiring each
     * criterion's spinner/badge to its row controls so runSearch() shows the
     * same per-criterion progress it previously showed on the chips.
     */
    protected void syncCriteriaFromRows() {
        if (searchCriteriaPane != null) searchCriteriaPane.synchronize();
    }

    /** Enables the Search button only when at least one criteria row has a value. */
    protected void updateSearchButtonState() {
        if (searchCriteriaPane != null) searchCriteriaPane.updateSearchButtonState(uniSearchBtn, uniStopBtn);
    }

    protected void setSpinner(ProgressIndicator pi, boolean active) {
        if (searchExecutionController != null) searchExecutionController.setSpinner(pi, active);
        else UiFactory.setVisible(pi, active);
    }


    protected void setAllSpinners(boolean active) {
        if (searchExecutionController != null) searchExecutionController.setAllSpinners(active);
    }

    // ── Confluence inline section ─────────────────────────────────────────────

    // ── Creates tree toggle + fresh TreeView factories for multi-tree ────────


    protected void buildConfluenceTreeToggle() {
        recToggleBtn = UiFactory.actionButton("With Sub-pages: ON");
        recToggleBtn.setOnAction(e -> {
            globalRecursive = !globalRecursive;
            recToggleBtn.setText(globalRecursive
                ? "With Sub-pages: ON" : "With Sub-pages: OFF");
        });
    }

    /** Creates a fresh Confluence page-tree TreeView with shared cell factory + auto-expand. */
    protected TreeView<PageTreeItem> createConfTreeView() {
        TreeView<PageTreeItem> tv = new TreeView<>();
        tv.setShowRoot(true);
        tv.setCellFactory(view -> new PageTreeCell());
        return tv;
    }

    // ── Dynamic results container — panels shown/hidden per search ───────────

    @SuppressWarnings("unchecked")

    protected VBox buildResultsContainer() {
        searchResultsPresenter = new SearchResultsPresenter(
            SearchResultTables::buildConfSearchTable, SearchResultTables::buildJiraTable,
            SearchResultTables::buildCommitTable, SearchResultTables::buildActionTable, SearchResultTables::buildPrTable,
            this::createConfTreeView, () -> { buildConfluenceTreeToggle(); return recToggleBtn; },
            SearchResultTables::buildSelectAllBar, this::expandLevel1,
            commit -> SearchResultTables.showCommitDetail(githubDetailArea, commit),
            run -> SearchResultTables.showActionLogDetail(githubActionDetailArea, run),
            url -> currentBaseUrl = url, url -> jiraBaseUrl = url,
            confTreeEntries, jiraSearchEntries, jiraUrlEntries, githubCommitEntries, githubActionEntries, githubPrEntries);
        searchResultsView = searchResultsPresenter.build();
        searchSummaryBox = searchResultsView.summaryBox();
        searchSummaryLabel = searchResultsView.summaryLabel();
        searchCriteriaInfoLabel = searchResultsView.criteriaLabel();
        confSearchTable = searchResultsView.confSearchTable();
        confTreesContainer = searchResultsView.confTrees();
        jiraSearchContainer = searchResultsView.jiraSearch();
        jiraUrlContainer = searchResultsView.jiraUrl();
        githubContainer = searchResultsView.github();
        githubActionContainer = searchResultsView.githubActions();
        githubPrContainer = searchResultsView.githubPr();
        jiraSpinner = searchResultsView.jiraSpinner();
        jiraLoadingLabel = searchResultsView.jiraLoadingLabel();
        jiraTableContainer = searchResultsView.jiraTableContainer();
        jiraStatusLabel = searchResultsView.jiraStatusLabel();
        githubSpinner = searchResultsView.githubSpinner();
        githubStatusLabel = searchResultsView.githubStatusLabel();
        githubDetailArea = searchResultsView.commitDetail();
        githubActionDetailArea = searchResultsView.actionDetail();
        rpConfSearch = (ResultPanel<PageSearchResult>) searchResultsView.confSearchPanel();
        rpConfTrees = searchResultsView.confTreePanel(); rpJiraSearch = searchResultsView.jiraSearchPanel();
        rpJiraUrl = searchResultsView.jiraUrlPanel(); rpGitHub = searchResultsView.githubPanel();
        rpGitHubAction = searchResultsView.githubActionPanel(); rpGitHubPr = searchResultsView.githubPrPanel();
        resultsContainer = searchResultsView.container();
        return resultsContainer;
    }


    // ── Unified download (Confluence + Jira in one task)
    // =========================================================================

    // ── Download target selector (folder + context name) ───────────────────────

    protected VBox buildDownloadTargetSelector() {
        VBox selector = downloadController.buildDownloadTargetSelector();
        newContextNameField = downloadController.nameField();
        savePathField = downloadController.pathField();
        return selector;
    }


    protected DownloadTarget resolveDownloadTarget() { return downloadController.resolveDownloadTarget(); }


    /** Shows a popup dialog with the Download Target selector.
     *  Returns the chosen {@link DownloadTarget}, or {@code null} if the user cancelled. */

    protected DownloadTarget showDownloadTargetDialog() { return downloadController.showDownloadTargetDialog(); }


    protected void stopDownload() { downloadController.stop(); }


    /** Show the Contexts (list / create / search / download) view full-window. */

    protected void browseSavePath() { downloadController.browseSavePath(); }


    protected void setDownloadState(boolean running) { downloadControllerState(running); }
    private void downloadControllerState(boolean running) {
        if (downloadProgressPane != null) downloadProgressPane.setRunning(running);
        if (savePathField != null) savePathField.setDisable(running);
        if (searchContextBtn != null) searchContextBtn.setDisable(running);
        if (running && mainShell != null) mainShell.setCenter(downloadInProgressView);
        if (running && downloadBtnRow != null) UiFactory.show(downloadBtnRow);
    }

    /** Check whether any result panel is currently visible (i.e. search returned results). */
    protected boolean hasVisibleResults() {
        if (rpConfSearch != null && rpConfSearch.pane.isVisible()) return true;
        if (rpConfTrees  != null && rpConfTrees.pane.isVisible())  return true;
        if (rpJiraSearch != null && rpJiraSearch.pane.isVisible()) return true;
        if (rpJiraUrl    != null && rpJiraUrl.pane.isVisible())    return true;
        if (rpGitHub     != null && rpGitHub.pane.isVisible())     return true;
        if (rpGitHubAction != null && rpGitHubAction.pane.isVisible()) return true;
        if (rpGitHubPr   != null && rpGitHubPr.pane.isVisible())   return true;
        return false;
    }

    /** Enable/disable the Save button and show/hide the button row based on results. */
    protected void updateSaveButtonState() {
        if (downloadProgressPane != null) downloadProgressPane.updateSaveButtonState();
    }


    /** Updates {@link #statusLabel} text and toggles the success/warning/error CSS class.
     *  @param msg       the message to display.
     *  @param styleKey  one of {@code "success"}, {@code "warning"}, {@code "error"},
     *                   or any other value (including {@code null}) for the neutral state. */
    protected void setStatus(String msg, String styleKey) {
        statusLabel.textProperty().unbind();
        applyStatus(statusLabel, msg, styleKey);
    }

    protected void appendLog(String msg) {
        if (downloadProgressPane != null) downloadProgressPane.appendLog(msg);
    }

    // =========================================================================
    // Tree helpers
    // =========================================================================


    protected List<RecursivePageProcessor.SelectedPage> collectSelectedPages() {
        List<RecursivePageProcessor.SelectedPage> result = new ArrayList<>();
        for (ConfTreeEntry e : confTreeEntries) {
            if (e.treeView().getRoot() != null) collectFrom(e.treeView().getRoot(), result);
        }
        return result;
    }

    protected void collectFrom(TreeItem<PageTreeItem> node,
                              List<RecursivePageProcessor.SelectedPage> out) {
        PageTreeItem d = node.getValue();
        if (d != null && d.selected.get())
            out.add(new RecursivePageProcessor.SelectedPage(d.pageId, d.title.get(), globalRecursive));
        for (TreeItem<PageTreeItem> c : node.getChildren()) collectFrom(c, out);
    }


    /** Updates {@link #uniStatusLabel} text and toggles the success/warning/error CSS class.
     *  @param msg       the message to display.
     *  @param styleKey  one of {@code "success"}, {@code "warning"}, {@code "error"},
     *                   or any other value (including {@code null}) for the neutral state. */
    protected void setUniStatus(String msg, String styleKey) {
        applyStatus(uniStatusLabel, msg, styleKey);
    }

    /** Shared status-label updater — removes old status classes and applies the new one. */
    private static void applyStatus(javafx.scene.control.Label label, String msg, String styleKey) {
        if (label == null) return;
        label.setText(msg);
        label.getStyleClass().removeAll("success", "warning", "error");
        if (styleKey != null) {
            switch (styleKey) {
                case "success", "warning", "error" -> label.getStyleClass().add(styleKey);
            }
        }
    }

    // =========================================================================
    // Badge helpers
    // =========================================================================


    protected Label makeBadge() {
        Label l = new Label(Icons.DOT);
        l.setPadding(new Insets(0, 0, 0, 4));
        l.getStyleClass().addAll("badge", "icon");
        return l;
    }


    protected void badgeOk(Label badge, int count) {
        Platform.runLater(() -> {
            badge.setText(String.valueOf(count));
            badge.getStyleClass().removeAll("badge-err");
            badge.getStyleClass().add("badge-ok");
        });
    }


    protected void badgeErr(Label badge) {
        Platform.runLater(() -> {
            badge.setText("!");
            badge.getStyleClass().removeAll("badge-ok");
            badge.getStyleClass().add("badge-err");
        });
    }


    protected void badgeReset(Label badge) {
        Platform.runLater(() -> {
            badge.setText(Icons.DOT);
            badge.getStyleClass().removeAll("badge-ok", "badge-err");
        });
    }
    // =========================================================================
    // Config logic
    // =========================================================================


    // ── Universal search execution ────────────────────────────────────────────


    protected void runSearch() {
        if (searchExecutionController != null) uniTask = searchExecutionController.start();
    }

    private void resetResultPanels() {
        if (searchResultsPresenter != null && searchResultsView != null) {
            searchResultsPresenter.reset(searchResultsView);
        }
        activeCriteria.forEach(c -> { if (c.badge != null) badgeReset(c.badge); });
    }

    private void populateSearchResults(SearchResults r, String confBase) {
        searchResultsPresenter.populate(searchResultsView, r, confBase);
    }

    private void logSearchResults(SearchResults r) {
        appendLog("━━━ Search complete ━━━");
        if (r.confSearch != null) appendLog("[RESULT] Confluence search:  " + r.confSearch.size() + " page(s)");
        if (!r.confTrees.isEmpty()) appendLog("[RESULT] Confluence trees:    " + r.confTrees.size() + " tree(s), "
            + r.confTrees.stream().mapToInt(SearchResults.ConfTreeResult::count).sum() + " page(s) total");
        r.confTrees.forEach(t -> appendLog("           └ " + t.criterionValue() + " → " + t.count() + " page(s)"));
        if (!r.jiraKeywordResults.isEmpty()) {
            int total = r.jiraKeywordResults.stream().mapToInt(x -> x.items().size()).sum();
            appendLog("[RESULT] Jira search:        " + r.jiraKeywordResults.size() + " criterion(s), " + total + " issue(s) total");
            r.jiraKeywordResults.forEach(x -> appendLog("           └ \"" + x.criterionValue() + "\" → " + x.items().size() + " issue(s)"));
        }
        if (!r.jiraUrlResults.isEmpty()) {
            int total = r.jiraUrlResults.stream().mapToInt(x -> x.items().size()).sum();
            appendLog("[RESULT] Jira from URL:      " + r.jiraUrlResults.size() + " criterion(s), " + total + " issue(s) total");
        }
        if (!r.githubCommitResults.isEmpty()) {
            int total = r.githubCommitResults.stream().mapToInt(x -> x.commits().size()).sum();
            appendLog("[RESULT] GitHub commits:     " + r.githubCommitResults.size() + " criterion(s), " + total + " commit(s) total");
        }
        if (!r.githubActionResults.isEmpty()) appendLog("[RESULT] GitHub actions:     " + r.githubActionResults.size() + " criterion(s)");
        if (!r.githubPrResults.isEmpty()) appendLog("[RESULT] GitHub PR:          " + r.githubPrResults.size() + " criterion(s)");
        if (!r.errors.isEmpty()) appendLog("[RESULT] " + Icons.WARNING + " " + r.errors.size() + " error(s) — see below");
        r.errors.forEach((src, msg) -> appendLog("  [ERROR] " + src + ": " + msg));
    }

    protected void displayResultsSummary(SearchResults r) {
        // Info line: which search criteria were used (above the summary)
        String criteriaInfo = formatActiveSearchCriteria();
        if (criteriaInfo != null && !criteriaInfo.isBlank()) {
            searchCriteriaInfoLabel.setText(Icons.INFO + "  Used:  " + criteriaInfo);
            UiFactory.show(searchCriteriaInfoLabel);
        } else {
            searchCriteriaInfoLabel.setText("");
            UiFactory.hide(searchCriteriaInfoLabel);
        }

        // summary bar (totals are aggregated across all per-criterion result groups)
        StringBuilder sb = new StringBuilder(Icons.CHECK + "  Search complete");
        if (r.confSearch  != null) sb.append("  │  Confluence: ").append(r.confSearch.size());
        if (!r.confTrees.isEmpty()) {
            int total = r.confTrees.stream().mapToInt(SearchResults.ConfTreeResult::count).sum();
            sb.append("  │  Trees: ").append(r.confTrees.size()).append(" (").append(total).append(" pages)");
        }
        if (!r.jiraKeywordResults.isEmpty()) {
            int total = r.jiraKeywordResults.stream().mapToInt(cr -> cr.items().size()).sum();
            sb.append("  │  Jira search: ").append(total)
              .append(" (").append(r.jiraKeywordResults.size()).append(" criteria)");
        }
        if (!r.jiraUrlResults.isEmpty()) {
            int total = r.jiraUrlResults.stream().mapToInt(cr -> cr.items().size()).sum();
            sb.append("  │  Jira URL: ").append(total)
              .append(" (").append(r.jiraUrlResults.size()).append(" criteria)");
        }
        if (!r.githubCommitResults.isEmpty()) {
            int total = r.githubCommitResults.stream().mapToInt(cr -> cr.commits().size()).sum();
            sb.append("  │  GitHub: ").append(total)
              .append(" (").append(r.githubCommitResults.size()).append(" criteria)");
        }
        if (!r.githubActionResults.isEmpty())
            sb.append("  │  Actions: ").append(r.githubActionResults.size());
        if (!r.githubPrResults.isEmpty())
            sb.append("  │  GitHub PR: ").append(r.githubPrResults.size());
        if (!r.errors.isEmpty())   sb.append("  │  " + Icons.WARNING + " ").append(r.errors.size()).append(" error(s)");
        searchSummaryLabel.setText(sb.toString());
        searchSummaryLabel.getStyleClass().removeAll("warning", "error");
        searchSummaryLabel.getStyleClass().add("success");
        setUniStatus(Icons.CHECK + "  Search complete", "success");

        r.errors.forEach((src, msg) -> appendLog("[WARN] " + src + ": " + msg));
    }

    /** Builds a human-readable description of the search criteria used in the last run
     *  (each active criterion with its type label and value). */
    protected String formatActiveSearchCriteria() {
        StringBuilder sb = new StringBuilder();
        for (SearchCriterion c : activeCriteria) {
            if (c.value == null || c.value.isBlank()) continue;
            if (sb.length() > 0) sb.append("   │   ");
            sb.append(c.type.label.strip()).append(" → ");
            if (c.type == CriteriaType.GITHUB_COMMIT && c.extraValue != null && !c.extraValue.isBlank()) {
                sb.append(c.extraValue).append(" / \"").append(c.value).append("\"");
            } else {
                sb.append(c.value);
            }
        }
        return sb.toString();
    }


    protected void stopUniversalSearch() {
        if (searchExecutionController != null) searchExecutionController.stop();
    }

    /** Resets the universal search panel to a blank-slate "ready for new search" state.
     *  Called automatically after a successful download, and by the manual [ RESET ] button. */

    protected void clearSearchState() {
        if (isSearchRunning()) return;
        searchResultsReady = false;
        // 1. Clear criteria rows
        activeCriteria.clear();
        if (searchCriteriaPane != null) searchCriteriaPane.clear();

        // 2. Reset status labels
        setUniStatus("", null);
        if (searchSummaryLabel != null) searchSummaryLabel.setText("");
        if (searchCriteriaInfoLabel != null) {
            searchCriteriaInfoLabel.setText("");
            UiFactory.hide(searchCriteriaInfoLabel);
        }

        // 4. Hide all result panels
        // 4-5. Hide and clear every result view/model.
        if (searchResultsPresenter != null && searchResultsView != null) {
            searchResultsPresenter.reset(searchResultsView);
        }

        // 6. Reset progress / status bar
        if (downloadProgressPane != null) downloadProgressPane.reset();
        updateSaveButtonState();
    }


    protected void startUnifiedDownload() { downloadController.start(); }


    // ── URL resolution helpers ────────────────────────────────────────────────


    protected String resolveConfBaseUrl() {
        if (currentBaseUrl != null && !currentBaseUrl.isBlank()) return currentBaseUrl;
        // Try to extract base URL from any Confluence URL criteria (tree URLs carry a base)
        SourceInputClassifier classifier = new SourceInputClassifier();
        for (SearchCriterion c : activeCriteria) {
            if (c.type == CriteriaType.CONFLUENCE && !c.value.isBlank()) {
                String base = classifier.classifyConfluence(c.value).baseUrl();
                if (base != null && !base.isBlank()) return base;
            }
        }
        String cfg = baseUrlConfigField != null ? baseUrlConfigField.getText().strip() : "";
        if (!cfg.isBlank()) return cfg;
        return null;
    }


    protected String resolveJiraBaseUrl() {
        if (jiraBaseUrl != null && !jiraBaseUrl.isBlank()) return jiraBaseUrl;
        // Try to extract base URL from any Jira URL criteria (issue URLs carry a base)
        SourceInputClassifier classifier = new SourceInputClassifier();
        for (SearchCriterion c : activeCriteria) {
            if (c.type == CriteriaType.JIRA && !c.value.isBlank()) {
                String base = classifier.classifyJira(c.value).baseUrl();
                if (base != null && !base.isBlank()) return base;
            }
        }
        return resolveConfBaseUrl();
    }

    // =========================================================================
    // TAB — About
    // =========================================================================


    /** Sets the selected state on every node in the page tree. */

    protected void setAllTreeSelected(boolean selected) {
        for (ConfTreeEntry e : confTreeEntries) {
            if (e.treeView().getRoot() != null)
                setAllTreeSelectedRecursive(e.treeView().getRoot(), selected);
        }
    }


    protected void setAllTreeSelectedRecursive(TreeItem<PageTreeItem> node, boolean selected) {
        if (node.getValue() != null) node.getValue().selected.set(selected);
        for (TreeItem<PageTreeItem> child : node.getChildren())
            setAllTreeSelectedRecursive(child, selected);
    }

    /** Expands root and all direct children (level 1). */

    protected void expandLevel1(TreeItem<PageTreeItem> root) {
        if (root == null) return;
        root.setExpanded(true);
        for (TreeItem<PageTreeItem> child : root.getChildren()) child.setExpanded(true);
    }

    private final class DownloadHost implements ContextDownloadController.Host {
        @Override public Stage stage() { return stage; }
        @Override public String username() { return usernameField.getText(); }
        @Override public String token() { return tokenField.getText(); }
        @Override public String githubToken() { return githubTokenField == null ? "" : githubTokenField.getText(); }
        @Override public String confBaseUrl() { return resolveConfBaseUrl(); }
        @Override public String jiraBaseUrl() { return resolveJiraBaseUrl(); }
        @Override public ContextDownloadController.SelectionData selectionData() {
            return new ContextDownloadController.SelectionData(rpConfSearch, confSearchTable, rpConfTrees, confTreeEntries,
                    rpJiraSearch, jiraSearchEntries, rpJiraUrl, jiraUrlEntries, rpGitHub, githubCommitEntries,
                    rpGitHubAction, githubActionEntries, rpGitHubPr, githubPrEntries, globalRecursive);
        }
        @Override public boolean hasVisibleResults() { return ContextAcquisitionController.this.hasVisibleResults(); }
        @Override public File addContextTargetFolder() { return ContextAcquisitionController.this.addContextTargetFolder; }
        @Override public String addContextTargetProjectId() { return ContextAcquisitionController.this.addContextTargetProjectId; }
        @Override public String activeProjectId() { return ContextAcquisitionController.this.activeProjectId; }
        @Override public ContextDownloadController.ProjectFiles projectFiles(String id) {
            ProjectFilesPane pane = id == null ? null : projectFilesPanes.get(id);
            return pane == null ? null : new ContextDownloadController.ProjectFiles(pane);
        }
        @Override public AtomicBoolean cancelled() { return ContextAcquisitionController.this.cancelled; }
        @Override public SearchDownloadService searchDownloadService() { return ContextAcquisitionController.this.searchDownloadService; }
        @Override public ProjectService projectService() { return ContextAcquisitionController.this.projectService; }
        @Override public AppConfig config() { return workflowHost().config(); }
        @Override public String githubApiUrl() { return workflowHost().githubApiUrl(); }
        @Override public String workspacePath() { return workflowHost().workspacePath(); }
        @Override public Path contextsDir() { return workflowHost().contextsDir(); }
        @Override public String fullConfluenceFolder() { return workflowHost().fullConfluenceFolder(); }
        @Override public Task<?> currentTask() { return ContextAcquisitionController.this.currentTask; }
        @Override public void setCurrentTask(Task<String> task) { currentTask = task; }
        @Override public void setLastSessionPath(String path) { lastSessionPath = path; }
        @Override public void prepareDownload() {
            cancelled.set(false); logArea.clear(); logPane.setExpanded(false); setDownloadState(true);
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        }
        @Override public void bindDownload(Task<String> task) {
            statusLabel.textProperty().bind(task.messageProperty());
            progressBar.progressProperty().bind(task.progressProperty());
        }
        @Override public void closeSearchPopup() { if (searchDialogStage != null) ContextAcquisitionController.this.closeSearchPopup(); }
        @Override public void appendLog(String message) { ContextAcquisitionController.this.appendLog(message); }
        @Override public void setStatus(String message, String style) { ContextAcquisitionController.this.setStatus(message, style); }
        @Override public void setDownloadState(boolean running) { ContextAcquisitionController.this.setDownloadState(running); }
        @Override public void alert(String title, String message) { showAlert(title, message); }
        @Override public BorderPane mainShell() { return mainShell; }
        @Override public void restorePromptWorkspace() { ContextAcquisitionController.this.restorePromptWorkspace(); }
        @Override public void refreshProjects() { cachedProjectsPanel = buildProjectsTab(); }
    }
}
