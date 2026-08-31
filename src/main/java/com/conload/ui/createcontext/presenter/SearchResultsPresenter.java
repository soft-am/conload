package com.conload.ui.createcontext.presenter;

import com.conload.github.GitHubClient;
import com.conload.ui.Icons;
import com.conload.ui.MainControllerSupport;
import com.conload.ui.Theme;
import com.conload.ui.createcontext.GitHubActionResult;
import com.conload.ui.createcontext.GitHubPrResult;
import com.conload.ui.createcontext.ResultPanel;
import com.conload.ui.createcontext.model.JiraTableItem;
import com.conload.ui.createcontext.model.PageSearchResult;
import com.conload.ui.createcontext.model.PageTreeItem;
import com.conload.ui.createcontext.model.SearchResults;
import com.conload.ui.components.UiFactory;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Builds and populates the result portion of the Search Context dialog. */
public final class SearchResultsPresenter {
    private final Supplier<TableView<PageSearchResult>> confluenceTableFactory;
    private final Supplier<TableView<JiraTableItem>> jiraTableFactory;
    private final Supplier<TableView<GitHubClient.GitCommit>> commitTableFactory;
    private final Supplier<TableView<GitHubActionResult>> actionTableFactory;
    private final Supplier<TableView<GitHubPrResult>> prTableFactory;
    private final Supplier<TreeView<PageTreeItem>> treeFactory;
    private final Supplier<Button> recursiveToggleFactory;
    private final BiFunction<Runnable, Runnable, HBox> selectAllBarFactory;
    private final Consumer<TreeItem<PageTreeItem>> expandTree;
    private final Consumer<GitHubClient.GitCommit> showCommitDetail;
    private final Consumer<GitHubActionResult> showActionLogDetail;
    private final Consumer<String> setConfluenceBaseUrl;
    private final Consumer<String> setJiraBaseUrl;
    private final List<MainControllerSupport.ConfTreeEntry> confTreeEntries;
    private final List<MainControllerSupport.JiraSearchEntry> jiraSearchEntries;
    private final List<MainControllerSupport.JiraUrlEntry> jiraUrlEntries;
    private final List<MainControllerSupport.GitHubCommitEntry> githubCommitEntries;
    private final List<MainControllerSupport.GitHubActionEntry> githubActionEntries;
    private final List<MainControllerSupport.GitHubPrEntry> githubPrEntries;

    public SearchResultsPresenter(
            Supplier<TableView<PageSearchResult>> confluenceTableFactory,
            Supplier<TableView<JiraTableItem>> jiraTableFactory,
            Supplier<TableView<GitHubClient.GitCommit>> commitTableFactory,
            Supplier<TableView<GitHubActionResult>> actionTableFactory,
            Supplier<TableView<GitHubPrResult>> prTableFactory,
            Supplier<TreeView<PageTreeItem>> treeFactory,
            Supplier<Button> recursiveToggleFactory,
            BiFunction<Runnable, Runnable, HBox> selectAllBarFactory,
            Consumer<TreeItem<PageTreeItem>> expandTree,
            Consumer<GitHubClient.GitCommit> showCommitDetail,
            Consumer<GitHubActionResult> showActionLogDetail,
            Consumer<String> setConfluenceBaseUrl,
            Consumer<String> setJiraBaseUrl,
            List<MainControllerSupport.ConfTreeEntry> confTreeEntries,
            List<MainControllerSupport.JiraSearchEntry> jiraSearchEntries,
            List<MainControllerSupport.JiraUrlEntry> jiraUrlEntries,
            List<MainControllerSupport.GitHubCommitEntry> githubCommitEntries,
            List<MainControllerSupport.GitHubActionEntry> githubActionEntries,
            List<MainControllerSupport.GitHubPrEntry> githubPrEntries) {
        this.confluenceTableFactory = confluenceTableFactory;
        this.jiraTableFactory = jiraTableFactory;
        this.commitTableFactory = commitTableFactory;
        this.actionTableFactory = actionTableFactory;
        this.prTableFactory = prTableFactory;
        this.treeFactory = treeFactory;
        this.recursiveToggleFactory = recursiveToggleFactory;
        this.selectAllBarFactory = selectAllBarFactory;
        this.expandTree = expandTree;
        this.showCommitDetail = showCommitDetail;
        this.showActionLogDetail = showActionLogDetail;
        this.setConfluenceBaseUrl = setConfluenceBaseUrl;
        this.setJiraBaseUrl = setJiraBaseUrl;
        this.confTreeEntries = confTreeEntries;
        this.jiraSearchEntries = jiraSearchEntries;
        this.jiraUrlEntries = jiraUrlEntries;
        this.githubCommitEntries = githubCommitEntries;
        this.githubActionEntries = githubActionEntries;
        this.githubPrEntries = githubPrEntries;
    }

    public View build() {
        SummaryView summaryView = buildSummaryView();
        ConfluenceView confluenceView = buildConfluenceView();
        JiraView jiraView = buildJiraView();
        GitHubView gitHubView = buildGitHubView();

        VBox all = new VBox(5, summaryView.summaryBox(), confluenceView.searchPanel().pane,
                confluenceView.treePanel().pane, jiraView.searchPanel().pane, jiraView.urlPanel().pane,
                gitHubView.commitPanel().pane, gitHubView.actionPanel().pane, gitHubView.prPanel().pane);
        return new View(all, summaryView.summaryBox(), summaryView.summary(), summaryView.criteria(),
                confluenceView.table(), confluenceView.trees(), jiraView.search(), jiraView.url(),
                gitHubView.commits(), gitHubView.actions(), gitHubView.pr(), jiraView.spinner(),
                jiraView.loading(), jiraView.tableContainer(), jiraView.status(), gitHubView.spinner(),
                gitHubView.status(), gitHubView.commitDetail(), gitHubView.actionDetail(),
                confluenceView.searchPanel(), confluenceView.treePanel(), jiraView.searchPanel(),
                jiraView.urlPanel(), gitHubView.commitPanel(), gitHubView.actionPanel(), gitHubView.prPanel());
    }

    private SummaryView buildSummaryView() {
        Label summary = new Label("");
        summary.setWrapText(true);
        summary.getStyleClass().add("status");
        Label criteria = new Label("");
        criteria.setWrapText(true);
        criteria.getStyleClass().addAll("hint", "secondary");
        UiFactory.hide(criteria);
        VBox summaryBox = new VBox(4, criteria, summary);
        summaryBox.setPadding(new javafx.geometry.Insets(8, 10, 8, 10));
        summaryBox.getStyleClass().add("card-bordered");
        VBox.setMargin(summaryBox, new javafx.geometry.Insets(0, 0, 2, 0));
        return new SummaryView(summaryBox, summary, criteria);
    }

    private ConfluenceView buildConfluenceView() {
        TableView<PageSearchResult> confTable = confluenceTableFactory.get();
        confTable.setMinHeight(200);
        confTable.setPrefHeight(240);
        VBox confSearch = new VBox(4, selectAllBarFactory.apply(
                () -> confTable.getItems().forEach(r -> r.selectedProperty().set(true)),
                () -> confTable.getItems().forEach(r -> r.selectedProperty().set(false))), confTable);
        confSearch.setPadding(Theme.PAD_ROW_TIGHT);
        VBox.setVgrow(confTable, Priority.ALWAYS);
        ResultPanel<?> confSearchPanel = new ResultPanel<>(Icons.CLOUD + "  CONFLUENCE SEARCH RESULTS", confSearch);

        Button recursiveToggle = recursiveToggleFactory.get();
        VBox confTrees = placeholderContainer("No page trees yet. Use Search Context to add Confluence page URLs.");
        VBox treeContent = new VBox(4, selectAllBarFactory.apply(
                () -> confTreeEntries.forEach(e -> setTreeSelected(e.treeView().getRoot(), true)),
                () -> confTreeEntries.forEach(e -> setTreeSelected(e.treeView().getRoot(), false))),
                confTrees, new HBox(6, UiFactory.hSpacer(), recursiveToggle));
        treeContent.setPadding(Theme.PAD_ROW_TIGHT);
        VBox.setVgrow(confTrees, Priority.ALWAYS);
        ResultPanel<?> confTreePanel = new ResultPanel<>(Icons.CLOUD + "  CONFLUENCE PAGE TREES", treeContent);
        return new ConfluenceView(confTable, confTrees, confSearchPanel, confTreePanel);
    }

    private JiraView buildJiraView() {
        VBox jiraSearch = placeholderContainer("No results yet.");
        VBox jiraSearchContent = new VBox(4, jiraSearch);
        jiraSearchContent.setPadding(Theme.PAD_ROW_TIGHT);
        VBox.setVgrow(jiraSearch, Priority.ALWAYS);
        ResultPanel<?> jiraSearchPanel = new ResultPanel<>(Icons.DOT + "  JIRA — keyword search", jiraSearchContent);

        ProgressIndicator jiraSpinner = new ProgressIndicator(-1);
        jiraSpinner.setPrefSize(44, 44);
        Theme.classes(jiraSpinner, Theme.CL_PROGRESS_ACCENT);
        Label jiraLoading = new Label(Icons.LOADING + "  Fetching issue…");
        Theme.classes(jiraLoading, Theme.CL_TITLE_SMALL);
        VBox jiraLoadingBox = new VBox(10, jiraSpinner, jiraLoading);
        jiraLoadingBox.setAlignment(javafx.geometry.Pos.CENTER);
        jiraLoadingBox.getStyleClass().add("overlay-loading-box");
        UiFactory.hide(jiraLoadingBox);
        StackPane jiraTableContainer = new StackPane(jiraLoadingBox);
        jiraTableContainer.setMinHeight(180);
        jiraTableContainer.setPrefHeight(220);
        Label jiraStatus = new Label(Icons.DOT + "  Results appear after search");
        jiraStatus.getStyleClass().add("hint");
        VBox jiraUrl = placeholderContainer("No results yet.");
        VBox jiraUrlContent = new VBox(4, jiraStatus, jiraTableContainer, jiraUrl);
        jiraUrlContent.setPadding(Theme.PAD_ROW_TIGHT);
        VBox.setVgrow(jiraUrl, Priority.ALWAYS);
        ResultPanel<?> jiraUrlPanel = new ResultPanel<>(Icons.DOT + "  JIRA — linked issues from URL", jiraUrlContent);
        return new JiraView(jiraSearch, jiraUrl, jiraSpinner, jiraLoading, jiraTableContainer, jiraStatus,
                jiraSearchPanel, jiraUrlPanel);
    }

    private GitHubView buildGitHubView() {
        ProgressIndicator githubSpinner = new ProgressIndicator(-1);
        githubSpinner.setPrefSize(44, 44);
        Theme.classes(githubSpinner, Theme.CL_PROGRESS_ACCENT);
        Label githubLoading = new Label(Icons.LOADING + "  Searching…");
        Theme.classes(githubLoading, Theme.CL_TITLE_SMALL);
        VBox githubLoadingBox = new VBox(10, githubSpinner, githubLoading);
        githubLoadingBox.setAlignment(javafx.geometry.Pos.CENTER);
        githubLoadingBox.getStyleClass().add("overlay-loading-box");
        UiFactory.hide(githubLoadingBox);
        StackPane githubTableStack = new StackPane(githubLoadingBox);
        githubTableStack.setMinHeight(160);
        githubTableStack.setPrefHeight(200);
        TextArea commitDetail = new TextArea();
        commitDetail.setEditable(false); commitDetail.setWrapText(true); commitDetail.setPrefHeight(220);
        commitDetail.getStyleClass().add("log-area");
        TitledPane commitDetailPane = new TitledPane("  COMMIT DETAILS", commitDetail);
        commitDetailPane.setExpanded(false); commitDetailPane.setAnimated(true);
        Label githubStatus = new Label(Icons.DOT + "  Results appear after search"); githubStatus.getStyleClass().add("hint");
        VBox github = placeholderContainer("No results yet.");
        VBox githubContent = new VBox(5, githubStatus, githubTableStack, github, commitDetailPane);
        githubContent.setPadding(Theme.PAD_ROW_TIGHT); VBox.setVgrow(github, Priority.ALWAYS);
        ResultPanel<?> githubPanel = new ResultPanel<>(Icons.BRANCH_ALT + "  GITHUB COMMITS", githubContent);
        VBox githubPr = placeholderContainer("No results yet.");
        VBox githubPrContent = new VBox(4, githubPr);
        githubPrContent.setPadding(Theme.PAD_ROW_TIGHT); VBox.setVgrow(githubPr, Priority.ALWAYS);
        ResultPanel<?> githubPrPanel = new ResultPanel<>(Icons.BRANCH_ALT + "  GITHUB PULL REQUEST", githubPrContent);
        TextArea actionDetail = new TextArea();
        actionDetail.setEditable(false); actionDetail.setWrapText(true); actionDetail.setPrefHeight(120);
        actionDetail.getStyleClass().add("log-area");
        TitledPane actionDetailPane = new TitledPane("  ACTION LOGS", actionDetail);
        actionDetailPane.setExpanded(false); actionDetailPane.setAnimated(true);
        Label actionStatus = new Label(Icons.DOT + "  Results appear after search"); actionStatus.getStyleClass().add("hint");
        VBox githubActions = placeholderContainer("No results yet.");
        VBox actionContent = new VBox(5, actionStatus, githubActions, actionDetailPane);
        actionContent.setPadding(Theme.PAD_ROW_TIGHT); VBox.setVgrow(githubActions, Priority.ALWAYS);
        ResultPanel<?> githubActionPanel = new ResultPanel<>(Icons.BRANCH_ALT + "  GITHUB ACTION RUNS", actionContent);
        return new GitHubView(github, githubActions, githubPr, githubSpinner, githubStatus, commitDetail,
                actionDetail, githubPanel, githubActionPanel, githubPrPanel);
    }

    public void populate(View view, SearchResults results, String confBase) {
        if (results.confSearch != null && !results.confSearch.isEmpty()) {
            view.confSearchTable().getItems().addAll(results.confSearch);
            setConfluenceBaseUrl.accept(results.confSearchBase != null ? results.confSearchBase : confBase);
            view.confSearchPanel().show(results.confSearch.size());
        }
        if (!results.confTrees.isEmpty()) {
            view.confTrees().getChildren().clear();
            for (SearchResults.ConfTreeResult result : results.confTrees) {
                TreeView<PageTreeItem> tree = treeFactory.get();
                tree.setRoot(result.tree());
                expandTree.accept(result.tree());
                ScrollPane scroll = UiFactory.scrollable(tree);
                scroll.setFitToHeight(true);
                scroll.setPrefHeight(360);
                ResultPanel<?> panel = new ResultPanel<>(Icons.CLOUD + "  CONFLUENCE TREE: " + result.criterionValue(), scroll);
                panel.show(result.count());
                view.confTrees().getChildren().add(panel.pane);
                confTreeEntries.add(new MainControllerSupport.ConfTreeEntry(tree, panel));
            }
            view.confTreePanel().show(results.confTrees.size());
            setConfluenceBaseUrl.accept(results.confTrees.get(0).baseUrl());
        }
        populateJira(view.jiraSearch(), view.jiraSearchPanel(), results.jiraKeywordResults, jiraSearchEntries, "JIRA SEARCH");
        populateJira(view.jiraUrl(), view.jiraUrlPanel(), results.jiraUrlResults, jiraUrlEntries, "JIRA URL");
        if (!results.jiraKeywordResults.isEmpty()) setJiraBaseUrl.accept(results.jiraKeywordResults.get(0).baseUrl());
        else if (!results.jiraUrlResults.isEmpty()) setJiraBaseUrl.accept(results.jiraUrlResults.get(0).baseUrl());
        populateGitHub(view, results);
    }

    private void populateJira(VBox container, ResultPanel<?> outer, List<SearchResults.JiraCriterionResult> results,
                              List<? extends Object> entries, String title) {
        if (results.isEmpty()) return;
        container.getChildren().clear();
        for (SearchResults.JiraCriterionResult result : results) {
            TableView<JiraTableItem> table = jiraTableFactory.get();
            table.getStyleClass().add("table-border");
            table.getItems().addAll(result.items());
            ScrollPane scroll = UiFactory.scrollable(table);
            scroll.setFitToHeight(true); scroll.setPrefHeight(220);
            ResultPanel<?> panel = new ResultPanel<>(Icons.DOT + "  " + title + ": " + result.criterionValue(), scroll);
            panel.show(result.items().size()); container.getChildren().add(panel.pane);
            if (entries == jiraSearchEntries) jiraSearchEntries.add(new MainControllerSupport.JiraSearchEntry(table, panel));
            else jiraUrlEntries.add(new MainControllerSupport.JiraUrlEntry(table, panel));
        }
        outer.show(results.size());
    }

    private void populateGitHub(View view, SearchResults results) {
        if (!results.githubCommitResults.isEmpty()) {
            view.github().getChildren().clear(); int total = 0;
            for (SearchResults.GitHubCriterionResult result : results.githubCommitResults) {
                TableView<GitHubClient.GitCommit> table = commitTableFactory.get();
                table.getStyleClass().add("table-border"); table.getItems().addAll(result.commits()); table.getSelectionModel().selectAll();
                table.getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> { if (now != null) showCommitDetail.accept(now); });
                ScrollPane scroll = UiFactory.scrollable(table); scroll.setFitToHeight(true); scroll.setPrefHeight(200);
                ResultPanel<?> panel = new ResultPanel<>(Icons.BRANCH_ALT + "  GITHUB COMMITS: " + result.criterionValue(), scroll);
                panel.show(result.commits().size()); view.github().getChildren().add(panel.pane);
                githubCommitEntries.add(new MainControllerSupport.GitHubCommitEntry(table, panel)); total += result.commits().size();
            }
            view.githubPanel().show(total);
        }
        populateSingle(view.githubPr(), view.githubPrPanel(), results.githubPrResults, prTableFactory, githubPrEntries,
                result -> GitHubPrResult.from(result.pr()), SearchResults.GitHubPrCriterionResult::criterionValue, "GITHUB PR");
        populateSingle(view.githubActions(), view.githubActionPanel(), results.githubActionResults, actionTableFactory, githubActionEntries,
                result -> GitHubActionResult.from(result.run()), SearchResults.GitHubActionCriterionResult::criterionValue, "GITHUB ACTION RUN");
    }

    private <T, R> void populateSingle(VBox container, ResultPanel<?> outer, List<R> results, Supplier<TableView<T>> factory,
                                        List<?> entries, java.util.function.Function<R, T> itemFactory,
                                        java.util.function.Function<R, String> criterionValue, String title) {
        if (results.isEmpty()) return;
        container.getChildren().clear();
        for (R result : results) {
            TableView<T> table = factory.get(); T item = itemFactory.apply(result); table.getItems().add(item);
            if (item instanceof GitHubActionResult) table.getSelectionModel().selectedItemProperty().addListener((o, old, now) -> { if (now != null) showActionLogDetail.accept((GitHubActionResult) now); });
            ScrollPane scroll = UiFactory.scrollable(table); scroll.setFitToHeight(true); scroll.setPrefHeight(200);
            ResultPanel<?> panel = new ResultPanel<>(Icons.BRANCH_ALT + "  " + title + ": " + criterionValue.apply(result), scroll);
            panel.show(1); container.getChildren().add(panel.pane);
            if (item instanceof GitHubPrResult) githubPrEntries.add(new MainControllerSupport.GitHubPrEntry((TableView<GitHubPrResult>) table, panel));
            else githubActionEntries.add(new MainControllerSupport.GitHubActionEntry((TableView<GitHubActionResult>) table, panel));
        }
        outer.show(results.size());
    }

    public void reset(View view) {
        view.confSearchPanel().hide(); view.confTreePanel().hide(); view.jiraSearchPanel().hide(); view.jiraUrlPanel().hide();
        view.githubPanel().hide(); view.githubActionPanel().hide(); view.githubPrPanel().hide();
        view.confSearchTable().getItems().clear(); view.confTrees().getChildren().setAll(placeholder("No page trees yet."));
        view.jiraSearch().getChildren().setAll(placeholder("No results yet.")); view.jiraUrl().getChildren().setAll(placeholder("No results yet."));
        view.github().getChildren().setAll(placeholder("No results yet.")); view.githubActions().getChildren().setAll(placeholder("No results yet."));
        view.githubPr().getChildren().setAll(placeholder("No results yet."));
        confTreeEntries.clear(); jiraSearchEntries.clear(); jiraUrlEntries.clear(); githubCommitEntries.clear(); githubActionEntries.clear(); githubPrEntries.clear();
    }

    private static VBox placeholderContainer(String text) { Label label = new Label(text); label.getStyleClass().add("placeholder"); return new VBox(5, label); }
    private static Label placeholder(String text) { Label label = new Label(text); label.getStyleClass().add("placeholder"); return label; }
    private static void setTreeSelected(TreeItem<PageTreeItem> node, boolean selected) { if (node == null) return; if (node.getValue() != null) node.getValue().selected.set(selected); node.getChildren().forEach(child -> setTreeSelected(child, selected)); }

    private record SummaryView(VBox summaryBox, Label summary, Label criteria) {}

    private record ConfluenceView(TableView<PageSearchResult> table, VBox trees,
                                  ResultPanel<?> searchPanel, ResultPanel<?> treePanel) {}

    private record JiraView(VBox search, VBox url, ProgressIndicator spinner, Label loading,
                            StackPane tableContainer, Label status, ResultPanel<?> searchPanel,
                            ResultPanel<?> urlPanel) {}

    private record GitHubView(VBox commits, VBox actions, VBox pr, ProgressIndicator spinner, Label status,
                              TextArea commitDetail, TextArea actionDetail, ResultPanel<?> commitPanel,
                              ResultPanel<?> actionPanel, ResultPanel<?> prPanel) {}

    public record View(VBox container, VBox summaryBox, Label summaryLabel, Label criteriaLabel,
                       TableView<PageSearchResult> confSearchTable, VBox confTrees, VBox jiraSearch, VBox jiraUrl,
                       VBox github, VBox githubActions, VBox githubPr, ProgressIndicator jiraSpinner, Label jiraLoadingLabel,
                       StackPane jiraTableContainer, Label jiraStatusLabel, ProgressIndicator githubSpinner,
                       Label githubStatusLabel, TextArea commitDetail, TextArea actionDetail,
                       ResultPanel<?> confSearchPanel,
                       ResultPanel<?> confTreePanel, ResultPanel<?> jiraSearchPanel, ResultPanel<?> jiraUrlPanel,
                       ResultPanel<?> githubPanel, ResultPanel<?> githubActionPanel, ResultPanel<?> githubPrPanel) {}
}
