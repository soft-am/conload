package com.conload.service;

import com.conload.service.download.ContextDownloadService;
import com.conload.service.search.ContentSearchService;
import com.conload.ui.createcontext.model.SearchCriterion;
import com.conload.ui.createcontext.model.SearchResults;
import javafx.concurrent.Task;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Facade for the search and context-download services. */
public class SearchDownloadService {
    private final ContentSearchService searchService = new ContentSearchService();
    private final ContextDownloadService downloadService = new ContextDownloadService();

    public Task<SearchResults> createSearchTask(String username, String token, String ghToken, String ghApiUrl,
                                                  String confBase, String jiraBase,
                                                  List<SearchCriterion> activeCriteria,
                                                  java.util.function.Consumer<String> log,
                                                  AtomicBoolean cancelled) {
        return searchService.createSearchTask(username, token, ghToken, ghApiUrl, confBase, jiraBase,
                activeCriteria, log, cancelled);
    }

    public Task<String> createDownloadTask(String username, String token, String ghToken,
                                           String confBase, String jiraBase,
                                           Map<String, List<?>> selectedItems, String folderPath,
                                           String contextName, java.util.function.Consumer<String> log,
                                           AtomicBoolean cancelled) {
        return downloadService.createDownloadTask(username, token, ghToken, confBase, jiraBase,
                selectedItems, folderPath, contextName, log, cancelled);
    }

    public static String[] parseGitHubRepo(String ghRepo) { return ContentSearchService.parseGitHubRepo(ghRepo); }
    public static String[] parseGitHubPrUrl(String url) { return ContentSearchService.parseGitHubPrUrl(url); }
    public static String[] parseGitHubCommitUrl(String url) { return ContentSearchService.parseGitHubCommitUrl(url); }
    public static String[] parseGitHubActionUrl(String url) { return ContentSearchService.parseGitHubActionUrl(url); }
}
