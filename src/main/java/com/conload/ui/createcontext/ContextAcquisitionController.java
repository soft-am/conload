package com.conload.ui.createcontext;

import com.conload.ui.DialogStyler;
import com.conload.ui.Icons;
import com.conload.ui.ManagementScreensController;
import com.conload.util.FileUtil;
import com.conload.ui.Theme;
import com.conload.ui.components.SvgIcon;
import com.conload.ui.components.UiFactory;
import com.conload.ui.projects.ProjectFilesPane;

import com.conload.github.GitHubClient;
import com.conload.model.AppConfig;
import com.conload.model.Project;
import com.conload.service.ConfigService;
import com.conload.service.ProjectService;
import com.conload.service.RecursivePageProcessor;
import com.conload.service.SearchDownloadService;
import com.conload.service.search.SourceInputClassifier;
import com.conload.ui.createcontext.model.CriteriaType;
import com.conload.ui.workflow.CrossContextRunner;
import com.conload.ui.workflow.WorkflowHost;
import com.conload.ui.createcontext.model.DownloadTarget;
import com.conload.ui.createcontext.model.JiraTableItem;
import com.conload.ui.createcontext.model.PageSearchResult;
import com.conload.ui.createcontext.model.PageTreeItem;
import com.conload.ui.createcontext.model.SearchCriterion;
import com.conload.ui.createcontext.model.SearchResults;
import com.conload.ui.createcontext.presenter.SearchResultsPresenter;
import com.conload.ui.createcontext.search.SearchExecutionController;
import com.conload.ui.createcontext.download.ContextDownloadController;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import javafx.util.Duration;

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
    private VBox popupSearchingBox;
    private ScrollPane popupResultsScroll;
    private HBox popupResultsFooter;
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
    private SearchCriteriaPane searchCriteriaPane;
    private DownloadProgressPane downloadProgressPane;
    private ContextDownloadController downloadController;
    private CrossContextRunner crossContextRunner;
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
        VBox.setVgrow(resultsScroll, Priority.ALWAYS);
        mainResultsScroll = resultsScroll;

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

        shell.setTop(root);
        shell.setCenter(resultsScroll);
        shell.setBottom(footer);
        return shell;
    }

    private VBox buildSearchLauncher() {
        SvgIcon jiraIcon = new SvgIcon("/images/jira-logo.svg", 20, "-app-panel");
        SvgIcon confluenceIcon = new SvgIcon("/images/confluence-logo.svg", 20, "-app-panel");
        SvgIcon githubIcon = new SvgIcon("/images/github-logo.svg", 20, "-app-panel");
        Label label = new Label("Download Context");
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
        if (currentTask != null && currentTask.isRunning()) {
            return;
        }
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
        uniStatusLabel = new Label("");
        uniStatusLabel.getStyleClass().add("status");
        searchCriteriaPane = new SearchCriteriaPane(criteria -> {
            activeCriteria.clear();
            activeCriteria.addAll(criteria);
            updateSearchButtonState();
        }, () -> uniStopBtn != null && !uniStopBtn.isDisable());
        criteriaRowsBox = searchCriteriaPane.rows();
        Button addCriteriaBtn = UiFactory.accentButton("+");
        addCriteriaBtn.setOnAction(e -> {
            searchCriteriaPane.addRow();
        });
        uniSearchBtn = UiFactory.accentButton("Search");
        uniSearchBtn.setDisable(true);
        uniSearchBtn.setOnAction(e -> runSearch());
        uniStopBtn = UiFactory.errorButton("Stop");
        uniStopBtn.setDisable(true);
        uniStopBtn.setOnAction(e -> stopUniversalSearch());
        searchExecutionController = new SearchExecutionController(searchDownloadService,
            new SearchExecutionController.SearchUi(() -> activeCriteria,
                () -> usernameField.getText(), () -> tokenField.getText(),
                () -> githubTokenField == null ? "" : githubTokenField.getText(),
                () -> githubApiUrlField == null ? "" : githubApiUrlField.getText(),
                this::resolveConfBaseUrl, this::resolveJiraBaseUrl, uniSearchBtn, uniStopBtn,
                uniStatusLabel, this::setPopupSearching, this::swapPopupToResultsMode,
                this::resetResultPanels, this::updateSearchButtonState, this::appendLog),
            new SearchExecutionController.Callbacks() {
                @Override public void succeeded(SearchResults results, SearchExecutionController.SearchPlan plan) {
                    populateSearchResults(results, plan.confBase());
                    logSearchResults(results);
                    displayResultsSummary(results);
                    updateSaveButtonState();
                }
                @Override public void failed(Throwable error) { }
                @Override public void cancelled() { }
            });
        updateSearchButtonState();
        popupSearchBtnRow = new HBox(6, uniSearchBtn, uniStopBtn);
        popupSearchBtnRow.setAlignment(Pos.CENTER_LEFT);
        popupSearchBtnRow.setPadding(Theme.PAD_ROW);
        popupSearchBtnRow.getStyleClass().add("panel-border-top");
        VBox middle = new VBox(8, uniStatusLabel, criteriaRowsBox, addCriteriaBtn);
        middle.setPadding(Theme.PAD_ROW);
        Theme.classes(middle, Theme.CL_BG_APP);
        popupInputsScroll = UiFactory.scrollable(middle);
        VBox.setVgrow(popupInputsScroll, Priority.ALWAYS);
        popupSearchingBox = buildSearchingBox();
        popupResultsScroll = UiFactory.scrollable(null);
        UiFactory.hide(popupResultsScroll);
        popupResultsFooter = UiFactory.footerRow();
        popupResultsFooter.setSpacing(6);
        UiFactory.hide(popupResultsFooter);
        crossContextRunner = new CrossContextRunner(workflowHost());
        crossContextSection = new CrossContextSection(crossContextRunner, workflowHost(),
                this::appendLog, this::closeSearchPopup);
        return new VBox(0, popupInputsScroll, popupSearchingBox,
            popupSearchBtnRow, crossContextSection.view(), popupResultsScroll, popupResultsFooter);
    }

    private VBox buildSearchingBox() {
        ProgressIndicator spinner = new ProgressIndicator(-1);
        spinner.setPrefSize(40, 40);
        Theme.classes(spinner, Theme.CL_PROGRESS_ACCENT);
        Label label = new Label(Icons.LOADING + "  Searching — please wait…");
        Theme.classes(label, Theme.CL_TITLE_SMALL);
        VBox box = new VBox(12, spinner, label);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40, 14, 40, 14));
        Theme.classes(box, Theme.CL_BG_APP);
        UiFactory.hide(box);
        return box;
    }

    // ── Popup mode switching: input ⇄ searching ⇄ results ──────────────────────

    /** Toggles the popup between input mode and "searching…" mode.
     *  In searching mode the criteria inputs are hidden but the Search/Stop
     *  button row stays visible so Stop remains clickable. */
    private void setPopupSearching(boolean searching) {
        if (popupContent == null) return;
        UiFactory.setVisible(popupInputsScroll, !searching);
        if (popupSearchingBox != null) {
            UiFactory.setVisible(popupSearchingBox, searching);
        }
        if (crossContextSection != null) UiFactory.setVisible(crossContextSection.view(), !searching);
        // popupSearchBtnRow stays visible in both modes (Search/Stop controls).
    }

    /** Moves the results container + Download button row from the main tab into
     *  the popup and grows the popup to a much larger results-oriented size. */
    private void swapPopupToResultsMode() {
        if (searchDialogStage == null || !searchDialogStage.isShowing()) return;
        if (popupContent == null) return;

        // Hide inputs + searching box + search buttons (results mode footer replaces them).
        UiFactory.hide(popupInputsScroll);
        if (popupSearchingBox != null) UiFactory.hide(popupSearchingBox);
        UiFactory.hide(popupSearchBtnRow);
        if (crossContextSection != null) UiFactory.hide(crossContextSection.view());

        // Move the results container from the main tab into the popup scroll.
        if (resultsContainer != null && mainResultsScroll != null) {
            mainResultsScroll.setContent(null);
            popupResultsScroll.setContent(resultsContainer);
            UiFactory.show(popupResultsScroll);
            VBox.setVgrow(popupResultsScroll, Priority.ALWAYS);
        }

        // Move the Download/Stop button row into the popup footer.
        if (downloadBtnRow != null) {
            popupResultsFooter.getChildren().setAll(downloadBtnRow);
            UiFactory.show(popupResultsFooter);
            // Download row always visible while reviewing results; disable state
            // is refined by updateSaveButtonState() below.
            UiFactory.show(downloadBtnRow);
        }

        updateSaveButtonState();
        growPopupForResults();
    }

    /** Detaches the results container from the popup (search results must NEVER
     *  appear on the main panel — they are shown only inside the search popup)
     *  and restores the Download/Stop button row to the main-tab footer so
     *  download progress / Stop remain reachable after the popup closes.
     *  The main-panel center is left as whatever the caller set (idle results
     *  scroll, in-progress view, or completion view). */
    private void restoreResultsToMainTab() {
        // Detach the results container from the popup but do NOT move it back
        // into the main tab — search results live only in the search popup.
        if (resultsContainer != null && resultsContainer.getParent() != null) {
            if (popupResultsScroll != null
                    && popupResultsScroll.getContent() == resultsContainer) {
                popupResultsScroll.setContent(null);
            } else {
                ((javafx.scene.layout.Pane) resultsContainer.getParent()).getChildren().remove(resultsContainer);
            }
        }
        if (mainResultsScroll != null) {
            mainResultsScroll.setContent(null);
        }
        // Move the Download/Stop button row back into the main tab footer (top slot).
        if (downloadBtnRow != null && mainFooter != null) {
            if (popupResultsFooter != null) {
                popupResultsFooter.getChildren().clear();
            }
            if (!mainFooter.getChildren().contains(downloadBtnRow)) {
                mainFooter.getChildren().add(0, downloadBtnRow);
            }
        }
        // Reset popup section visibility so the next open starts in input mode.
        UiFactory.show(popupInputsScroll);
        UiFactory.show(popupSearchBtnRow);
        if (crossContextSection != null) UiFactory.show(crossContextSection.view());
        UiFactory.hide(popupSearchingBox);
        UiFactory.hide(popupResultsScroll);
        UiFactory.hide(popupResultsFooter);
    }

    /** Enlarges the popup to a results-friendly size (capped to 90% of the screen). */
    private void growPopupForResults() {
        if (searchDialogStage == null) return;
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double w = Math.min(1100, screen.getWidth() * 0.9);
        double h = Math.min(820, screen.getHeight() * 0.9);
        searchDialogStage.setWidth(w);
        searchDialogStage.setHeight(h);
        searchDialogStage.centerOnScreen();
    }

    /** Restores results/download controls to the main tab and hides the popup. */
    private void closeSearchPopup() {
        restoreResultsToMainTab();
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

    // ── Jira inline section ───────────────────────────────────────────────────

    // (removed: buildJiraInline, now handled in buildResultsTabPane)

    @SuppressWarnings("unchecked")

    protected TableView<JiraTableItem> buildJiraTable() {
        TableView<JiraTableItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<JiraTableItem, Boolean> selCol = checkboxColumn(JiraTableItem::selectedProperty);
        TableColumn<JiraTableItem, String> keyCol  = textColumn("KEY",     "key",     110);
        TableColumn<JiraTableItem, String> typeCol = textColumn("TYPE",    "type",     90);
        TableColumn<JiraTableItem, String> statCol = textColumn("STATUS",  "status",  120);
        TableColumn<JiraTableItem, String> sumCol  = textColumn("SUMMARY", "summary",  -1);
        table.getColumns().addAll(selCol, keyCol, typeCol, statCol, sumCol);
        return table;
    }


    /** Generic checkbox column: 44px, click-to-toggle label. Fixes the missing
     *  bounds guard in the former buildJiraTable.selCol copy (latent AIOOBE).
     *  Replaces 4 per-row-type duplicates. */
    protected <S> TableColumn<S, Boolean> checkboxColumn(java.util.function.Function<S, javafx.beans.property.BooleanProperty> selectedProp) {
        TableColumn<S, Boolean> col = new TableColumn<>("");
        col.setMaxWidth(44); col.setMinWidth(44);
        col.setCellValueFactory(cd -> selectedProp.apply(cd.getValue()));
        col.setCellFactory(tc -> new TableCell<>() {
            private final Label lbl = new Label(Icons.UNCHECKED);
            {
                lbl.getStyleClass().addAll("bold", "icon");
                lbl.setOnMouseClicked(e -> {
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) return;
                    S item = getTableView().getItems().get(getIndex());
                    selectedProp.apply(item).set(!selectedProp.apply(item).get());
                });
            }
            @Override protected void updateItem(Boolean val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setGraphic(null); } else {
                    lbl.setText(val ? Icons.CHECKED : Icons.UNCHECKED);
                    S item = getTableView().getItems().get(getIndex());
                    selectedProp.apply(item).addListener((o, ov, nv) -> lbl.setText(nv ? Icons.CHECKED : Icons.UNCHECKED));
                    setGraphic(lbl);
                }
            }
        });
        return col;
    }

    /** Generic table column factory: PropertyValueFactory-backed, Label-headered, transparent-empty-cell.
     *  Replaces the 5 per-row-type duplicates (styledCol/ghActStrCol/styledPrCol/uniStrCol/ghStrCol). */
    protected <S, T> TableColumn<S, T> textColumn(String header, String prop, double width) {
        TableColumn<S, T> col = new TableColumn<>();
        col.setCellValueFactory(new PropertyValueFactory<>(prop));
        col.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); getStyleClass().add("bg-transparent"); } else {
                    setText(item.toString());
                }
            }
        });
        Label h = new Label(header);
        h.getStyleClass().addAll("bold", "small");
        col.setGraphic(h); col.setText("");
        if (width > 0) { col.setMinWidth(width); col.setMaxWidth(width); }
        return col;
    }

    // ── Dynamic results container — panels shown/hidden per search ───────────

    @SuppressWarnings("unchecked")

    protected VBox buildResultsContainer() {
        searchResultsPresenter = new SearchResultsPresenter(this::buildUniConfTable, this::buildJiraTable,
            this::buildGitHubTable, this::buildGitHubActionTable, this::buildGitHubPrTable,
            this::createConfTreeView, () -> { buildConfluenceTreeToggle(); return recToggleBtn; },
            this::buildSelectAllBar, this::expandLevel1, this::showCommitDetail, this::showActionLogDetail,
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


    protected TableView<GitHubActionResult> buildGitHubActionTable() {
        TableView<GitHubActionResult> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<GitHubActionResult, Boolean> selCol = checkboxColumn(GitHubActionResult::selectedProperty);

        TableColumn<GitHubActionResult, String> runCol   = textColumn("RUN",        "runName",      200);
        TableColumn<GitHubActionResult, String> jobCol   = textColumn("JOB",        "jobName",      130);
        TableColumn<GitHubActionResult, String> statCol  = textColumn("STATUS",     "status",        90);
        TableColumn<GitHubActionResult, String> concCol = textColumn("CONCLUSION", "conclusion",    90);
        table.getColumns().addAll(selCol, runCol, jobCol, statCol, concCol);
        return table;
        }




    /** Populates the githubActionDetailArea with steps + logs of the selected run. */
    protected void showActionLogDetail(GitHubActionResult run) {
        if (githubActionDetailArea == null || run == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("Run:       ").append(run.getRunName()).append("\n");
        sb.append("Workflow:  ").append(run.getWorkflowName()).append("\n");
        sb.append("Status:    ").append(run.getStatus())
          .append("  /  ").append(run.getConclusion()).append("\n");
        sb.append("URL:       ").append(run.getHtmlUrl()).append("\n\n");

        // ── Steps list (mirror's GitHub UI's step breakdown) ──
        sb.append("── STEPS ──\n");
        if (run.getStepsJson() != null && !run.getStepsJson().isBlank()
                && !run.getStepsJson().equals("[]")) {
            try {
                com.fasterxml.jackson.databind.JsonNode steps =
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(run.getStepsJson());
                for (com.fasterxml.jackson.databind.JsonNode s : steps) {
                    int num = s.path("number").asInt(0);
                    String name = s.path("name").asText("(unnamed)");
                    String status = s.path("status").asText("");
                    String conc = s.path("conclusion").asText("");
                    sb.append(String.format("  [%d] %-40s  %s/%s%n",
                            num, name, status, conc));
                }
            } catch (Exception e) {
                sb.append("  (failed to parse steps)\n");
            }
        } else {
            sb.append("  (no steps available)\n");
        }
        sb.append("\n");

        // ── Full raw log text ──
        sb.append("── RAW LOGS ──\n");
        String logs = run.getJobLogs();
        sb.append(logs.isBlank() ? "(no logs available for this job)" : logs);
        githubActionDetailArea.setText(sb.toString());
    }


    protected TableView<GitHubPrResult> buildGitHubPrTable() {
        TableView<GitHubPrResult> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<GitHubPrResult, Boolean> selectCol = checkboxColumn(GitHubPrResult::selectedProperty);

        TableColumn<GitHubPrResult, Integer> numCol    = textColumn("#",      "number",  50);
        TableColumn<GitHubPrResult, String>  titleCol  = textColumn("TITLE",  "title",   -1);
        TableColumn<GitHubPrResult, String>  authorCol = textColumn("AUTHOR", "author", 140);
        TableColumn<GitHubPrResult, String>  stateCol  = textColumn("STATE",  "state",   90);

        table.getColumns().addAll(selectCol, numCol, titleCol, authorCol, stateCol);
        return table;
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


    protected TableView<PageSearchResult> buildUniConfTable() {
        TableView<PageSearchResult> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add("table-border");

        TableColumn<PageSearchResult, Boolean> selCol = checkboxColumn(PageSearchResult::selectedProperty);

        TableColumn<PageSearchResult, String> titleCol = textColumn("TITLE", "title", -1);
        TableColumn<PageSearchResult, String> keyCol   = textColumn("SPACE KEY",  "spaceKey",   100);
        TableColumn<PageSearchResult, String> nameCol  = textColumn("SPACE NAME", "spaceTitle", 160);
        table.getColumns().addAll(selCol, titleCol, keyCol, nameCol);
        return table;
    }




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


    protected HBox buildSelectAllBar(Runnable selectAll, Runnable deselectAll) {
        Button selAllBtn   = UiFactory.actionButton(Icons.CHECKED + "  ALL");
        Button deselAllBtn = UiFactory.actionButton(Icons.UNCHECKED + "  NONE");
        selAllBtn.setOnAction(e  -> selectAll.run());
        deselAllBtn.setOnAction(e -> deselectAll.run());
        HBox bar = new HBox(6, selAllBtn, deselAllBtn);
        bar.setPadding(Theme.PAD_ROW_FLAT);
        return bar;
    }

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

    /** Builds the GitHub commits TableView. */

    protected TableView<GitHubClient.GitCommit> buildGitHubTable() {
        TableView<GitHubClient.GitCommit> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<GitHubClient.GitCommit, String> shaCol  = textColumn("SHA",     "shortSha",      70);
        TableColumn<GitHubClient.GitCommit, String> msgCol  = textColumn("MESSAGE", "shortMessage",  -1);
        TableColumn<GitHubClient.GitCommit, String> authCol = textColumn("AUTHOR",  "authorName",   140);
        TableColumn<GitHubClient.GitCommit, String> dateCol = textColumn("DATE",    "committerDate", 130);
        table.getColumns().addAll(shaCol, msgCol, authCol, dateCol);
        return table;
    }

    /** Populates the githubDetailArea with details of the selected commit,
     *  rendering the diff as a pretty JSON object {"sha","url","diff"}. */

    protected void showCommitDetail(GitHubClient.GitCommit commit) {
        if (githubDetailArea == null || commit == null) return;
        githubDetailArea.setText(commit.toDiffJson());
    }

    /** Expands root and all direct children (level 1). */

    protected void expandLevel1(TreeItem<PageTreeItem> root) {
        if (root == null) return;
        root.setExpanded(true);
        for (TreeItem<PageTreeItem> child : root.getChildren()) child.setExpanded(true);
    }

    /** Reset a per-criterion panel container to its initial placeholder state. */
    protected void clearCriterionContainer(VBox container, String placeholderText) {
        if (container == null) return;
        container.getChildren().clear();
        Label ph = new Label(placeholderText);
        ph.getStyleClass().add("placeholder");
        container.getChildren().add(ph);
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
