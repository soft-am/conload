package com.conload.ui.createcontext.search;

import com.conload.service.SearchDownloadService;
import com.conload.ui.Icons;
import com.conload.ui.components.UiFactory;
import com.conload.ui.createcontext.model.CriteriaType;
import com.conload.ui.createcontext.model.SearchCriterion;
import com.conload.ui.createcontext.model.SearchResults;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns validation, planning, and the asynchronous lifecycle of a context search. */
public final class SearchExecutionController {
    public record SearchPlan(String username, String token, String ghToken, String ghApiUrl,
                              String confBase, String jiraBase, List<SearchCriterion> everywheres,
                              List<SearchCriterion> confluence, List<SearchCriterion> jira,
                              List<SearchCriterion> ghCommits, List<SearchCriterion> ghCommitUrls,
                              List<SearchCriterion> ghActionUrls, List<SearchCriterion> ghPrs,
                              boolean hasCreds, boolean willRunAnything) {}

    public record SearchUi(Supplier<List<SearchCriterion>> criteria, Supplier<String> username,
                           Supplier<String> token, Supplier<String> ghToken, Supplier<String> ghApiUrl,
                           Supplier<String> confBase, Supplier<String> jiraBase,
                           Button searchButton, Button stopButton, Label statusLabel,
                           Consumer<Boolean> setSearching, Runnable showResults, Runnable resetResults,
                           Runnable updateButtons, Consumer<String> log) {}

    public interface Callbacks {
        void succeeded(SearchResults results, SearchPlan plan);
        void failed(Throwable error);
        void cancelled();
    }

    private final SearchDownloadService service;
    private final SearchUi ui;
    private final Callbacks callbacks;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private Task<SearchResults> task;

    public SearchExecutionController(SearchDownloadService service, SearchUi ui, Callbacks callbacks) {
        this.service = service;
        this.ui = ui;
        this.callbacks = callbacks;
    }

    public Task<SearchResults> start() {
        SearchPlan plan = createPlan();
        if (plan == null) return null;
        logPlan(plan);
        prepare(plan);
        task = service.createSearchTask(plan.username(), plan.token(), plan.ghToken(), plan.ghApiUrl(),
                plan.confBase(), plan.jiraBase(), new ArrayList<>(ui.criteria().get()), ui.log(), cancelled);
        task.setOnSucceeded(e -> Platform.runLater(() -> finishSuccess(task.getValue(), plan)));
        task.setOnFailed(e -> Platform.runLater(() -> finishFailure(task.getException())));
        task.setOnCancelled(e -> Platform.runLater(this::finishCancelled));
        Thread thread = new Thread(task, "search");
        thread.setDaemon(true);
        thread.start();
        return task;
    }

    public void stop() {
        cancelled.set(true);
        if (task != null) task.cancel(true);
    }

    public boolean isRunning() { return task != null && task.isRunning(); }

    public void setSpinner(ProgressIndicator spinner, boolean active) {
        UiFactory.setVisible(spinner, active);
    }

    public void setAllSpinners(boolean active) {
        for (SearchCriterion criterion : ui.criteria().get()) {
            if (criterion.spinner != null) setSpinner(criterion.spinner, active);
        }
    }

    private SearchPlan createPlan() {
        List<SearchCriterion> criteria = ui.criteria().get();
        if (criteria.isEmpty()) {
            setStatus(Icons.WARNING + "  Fill at least one criteria row", "warning");
            return null;
        }
        String username = value(ui.username());
        String token = value(ui.token());
        String ghToken = value(ui.ghToken());
        String ghApiUrl = value(ui.ghApiUrl());
        String confBase = ui.confBase().get();
        String jiraBase = ui.jiraBase().get();
        List<SearchCriterion> everywhere = criteriaOf(criteria, CriteriaType.EVERYWHERE);
        List<SearchCriterion> confluence = criteriaOf(criteria, CriteriaType.CONFLUENCE);
        List<SearchCriterion> jira = criteriaOf(criteria, CriteriaType.JIRA);
        List<SearchCriterion> ghCommits = criteria.stream().filter(c -> c.type == CriteriaType.GITHUB_COMMIT
                && !c.value.isBlank() && !c.extraValue.isBlank()).toList();
        List<SearchCriterion> ghCommitUrls = criteriaOf(criteria, CriteriaType.GITHUB_COMMIT_URL);
        List<SearchCriterion> ghActionUrls = criteriaOf(criteria, CriteriaType.GITHUB_ACTION_URL);
        List<SearchCriterion> ghPrs = criteriaOf(criteria, CriteriaType.GITHUB_PR);
        boolean creds = !username.isBlank() && !token.isBlank();
        boolean keyword = !everywhere.isEmpty();
        boolean anything = keyword && creds && ((confBase != null && !confBase.isBlank())
                || (jiraBase != null && !jiraBase.isBlank())) || creds && (!confluence.isEmpty()
                || !jira.isEmpty())
                || !ghCommits.isEmpty() && !ghToken.isBlank() || !ghCommitUrls.isEmpty() && !ghToken.isBlank()
                || !ghActionUrls.isEmpty() && !ghToken.isBlank() || !ghPrs.isEmpty() && !ghToken.isBlank();
        if (!creds && (keyword || !confluence.isEmpty() || !jira.isEmpty())) {
            ui.log().accept("[WARN] Confluence/Jira skipped — fill Email + API Token in Config " + Icons.SETTINGS);
            setStatus(Icons.WARNING + "  Confluence/Jira skipped — no credentials in Config " + Icons.SETTINGS, "warning");
        }
        return new SearchPlan(username, token, ghToken, ghApiUrl, confBase, jiraBase, everywhere, confluence,
                jira, ghCommits, ghCommitUrls, ghActionUrls, ghPrs, creds, anything);
    }

    private static String value(Supplier<String> supplier) {
        String value = supplier.get();
        return value == null ? "" : value.strip();
    }

    private static List<SearchCriterion> criteriaOf(List<SearchCriterion> criteria, CriteriaType type) {
        return criteria.stream().filter(c -> c.type == type && !c.value.isBlank()).toList();
    }

    private void logPlan(SearchPlan plan) {
        ui.log().accept("━━━ Search started ━━━");
        plan.everywheres().forEach(c -> ui.log().accept("[SEARCH] " + Icons.ARROW_RIGHT + " Everywhere keyword: \"" + c.value + "\""));
        plan.confluence().forEach(c -> ui.log().accept("[SEARCH] " + Icons.ARROW_RIGHT + " Confluence (URL or keyword): " + c.value));
        plan.jira().forEach(c -> ui.log().accept("[SEARCH] " + Icons.ARROW_RIGHT + " Jira (URL, key, or keyword): " + c.value));
        plan.ghCommits().forEach(c -> ui.log().accept("[SEARCH] " + Icons.ARROW_RIGHT + " GitHub commits: repo=" + c.extraValue + "  keyword=\"" + c.value + "\""));
        plan.ghCommitUrls().forEach(c -> ui.log().accept("[SEARCH] " + Icons.ARROW_RIGHT + " GitHub commit URL: " + c.value));
        plan.ghActionUrls().forEach(c -> ui.log().accept("[SEARCH] " + Icons.ARROW_RIGHT + " GitHub action URL: " + c.value));
        plan.ghPrs().forEach(c -> ui.log().accept("[SEARCH] " + Icons.ARROW_RIGHT + " GitHub PR URL: " + c.value));
        if (!plan.willRunAnything()) ui.log().accept("[SEARCH] Nothing to run — check credentials and criteria");
    }

    private void prepare(SearchPlan plan) {
        setAllSpinners(false);
        for (SearchCriterion criterion : ui.criteria().get()) {
            if (criterion.spinner != null && willRun(criterion, plan))
                setSpinner(criterion.spinner, true);
            if (criterion.badge != null) criterion.badge.setText(Icons.DOT);
        }
        cancelled.set(false);
        if (ui.searchButton() != null) ui.searchButton().setDisable(true);
        ui.stopButton().setDisable(false);
        setStatus(Icons.LOADING + "  Searching — please wait…", "warning");
        ui.setSearching().accept(true);
        ui.resetResults().run();
    }

    private boolean willRun(SearchCriterion criterion, SearchPlan plan) {
        return switch (criterion.type) {
            case EVERYWHERE -> !plan.everywheres().isEmpty() && plan.hasCreds()
                    && ((plan.confBase() != null && !plan.confBase().isBlank())
                    || (plan.jiraBase() != null && !plan.jiraBase().isBlank()));
            case CONFLUENCE, JIRA -> plan.hasCreds();
            case GITHUB_COMMIT -> !plan.ghToken().isBlank() && plan.ghCommits().contains(criterion);
            case GITHUB_COMMIT_URL, GITHUB_ACTION_URL, GITHUB_PR -> !plan.ghToken().isBlank();
        };
    }

    private void finishSuccess(SearchResults results, SearchPlan plan) {
        ui.stopButton().setDisable(true);
        ui.setSearching().accept(false);
        ui.updateButtons().run();
        setAllSpinners(false);
        ui.showResults().run();
        callbacks.succeeded(results, plan);
    }

    private void finishFailure(Throwable error) {
        ui.stopButton().setDisable(true);
        ui.updateButtons().run();
        setAllSpinners(false);
        ui.setSearching().accept(false);
        String detail = error != null && error.getMessage() != null ? error.getMessage() : "Unknown error";
        ui.log().accept("[SEARCH][ERROR] Search task failed: " + detail);
        setStatus("✗  Error: " + detail, "error");
        callbacks.failed(error);
    }

    private void finishCancelled() {
        ui.stopButton().setDisable(true);
        ui.updateButtons().run();
        setAllSpinners(false);
        ui.setSearching().accept(false);
        ui.log().accept("[SEARCH] Search cancelled by user");
        setStatus(Icons.STOP + "  Search stopped", "warning");
        callbacks.cancelled();
    }

    private void setStatus(String message, String style) {
        Label label = ui.statusLabel();
        label.setText(message);
        label.getStyleClass().removeAll("success", "warning", "error");
        if (style != null) label.getStyleClass().add(style);
    }
}
