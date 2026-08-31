package com.conload.service.download;

import com.conload.confluence.ConfluenceClient;
import com.conload.confluence.ConfluenceUrlParser;
import com.conload.github.GitHubClient;
import com.conload.jira.JiraMarkdownExporter;
import com.conload.model.AppConfig;
import com.conload.service.AttachmentDownloaderService;
import com.conload.service.FileNamingService;
import com.conload.service.RecursivePageProcessor;
import com.conload.ui.createcontext.GitHubActionResult;
import com.conload.ui.createcontext.GitHubPrResult;
import com.conload.ui.createcontext.model.JiraTableItem;
import com.conload.ui.createcontext.model.PageSearchResult;
import com.conload.util.FileUtil;
import com.conload.util.Json;
import com.conload.ui.Icons;
import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ContextDownloadService {
    public Task<String> createDownloadTask(String username, String token, String ghToken,
                                           String confBase, String jiraBase, Map<String, List<?>> selectedItems,
                                           String folderPath, String contextName,
                                           java.util.function.Consumer<String> log, AtomicBoolean cancelled) {
        return new Task<>() {
            @Override protected String call() throws Exception {
                return runDownload(username, token, confBase, jiraBase, selectedItems, folderPath,
                        contextName, log, cancelled, this::isCancelled, this::updateMessage, this::updateProgress);
            }
        };
    }

    private String runDownload(String username, String token, String confBase, String jiraBase,
                               Map<String, List<?>> selectedItems, String folderPath, String contextName,
                               java.util.function.Consumer<String> log, AtomicBoolean cancelled,
                               java.util.function.BooleanSupplier isCancelled,
                               java.util.function.Consumer<String> updateMessage, ProgressReporter updateProgress) throws Exception {
        updateMessage.accept("Starting download…");
        Path root = Path.of(folderPath, FileUtil.normalizeContextFolderName(contextName));
        Files.createDirectories(root);
        log.accept("Download session root: " + root);
        processConfluence(username, token, confBase, selectedItems, root, log, cancelled);
        processJira(username, token, confBase, jiraBase, selectedItems, root, log, isCancelled);
        List<Downloadable> files = collectGitHubFiles(selectedItems);
        writeGitHubFiles(files, root, log, isCancelled, updateProgress);
        return root.toString();
    }
    private void processConfluence(String username, String token, String confBase, Map<String, List<?>> selectedItems, Path root, java.util.function.Consumer<String> log, AtomicBoolean cancelled) throws Exception {
        List<RecursivePageProcessor.SelectedPage> pages = new ArrayList<>();
        if (selectedItems.containsKey("CONF_SEARCH")) { @SuppressWarnings("unchecked") List<PageSearchResult> results = (List<PageSearchResult>) selectedItems.get("CONF_SEARCH"); pages.addAll(results.stream().map(PageSearchResult::toSelectedPage).toList()); }
        if (selectedItems.containsKey("CONF_TREE")) { @SuppressWarnings("unchecked") List<RecursivePageProcessor.SelectedPage> tree = (List<RecursivePageProcessor.SelectedPage>) selectedItems.get("CONF_TREE"); pages.addAll(tree); }
        if (pages.isEmpty()) return;
        log.accept("--- Processing " + pages.size() + " Confluence page(s) ---");
        AppConfig config = new AppConfig(username, token, confBase); ConfluenceClient client = new ConfluenceClient(config);
        RecursivePageProcessor processor = new RecursivePageProcessor(client, new ConfluenceUrlParser(), new AttachmentDownloaderService(client, log), new FileNamingService(), log, cancelled);
        processor.processSelectedPages(confBase, pages, root.toString(), config);
    }
    private void processJira(String username, String token, String confBase, String jiraBase, Map<String, List<?>> selectedItems, Path root, java.util.function.Consumer<String> log, java.util.function.BooleanSupplier isCancelled) throws Exception {
        List<JiraTableItem> issues = new ArrayList<>(); addItems(selectedItems, "JIRA_SEARCH", issues); addItems(selectedItems, "JIRA_URL", issues); if (issues.isEmpty()) return;
        log.accept("--- Processing " + issues.size() + " Jira issue(s) ---"); String base = valid(jiraBase) ? jiraBase : confBase; JiraMarkdownExporter exporter = new JiraMarkdownExporter(new AppConfig(username, token, base), log); Path output = root.resolve("jira"); Files.createDirectories(output);
        for (JiraTableItem issue : issues) { if (isCancelled.getAsBoolean()) break; exporter.fetchAndExport(base, issue.getKey(), output); }
    }
    @SuppressWarnings("unchecked") private static void addItems(Map<String, List<?>> selectedItems, String key, List<JiraTableItem> target) { if (selectedItems.containsKey(key)) target.addAll((List<JiraTableItem>) selectedItems.get(key)); }
    private List<Downloadable> collectGitHubFiles(Map<String, List<?>> selectedItems) { List<Downloadable> files = new ArrayList<>(); collectCommits(selectedItems, files); collectActions(selectedItems, files); collectPrs(selectedItems, files); return files; }
    @SuppressWarnings("unchecked") private static void collectCommits(Map<String, List<?>> selectedItems, List<Downloadable> files) { if (!selectedItems.containsKey("GITHUB_COMMIT")) return; for (GitHubClient.GitCommit commit : (List<GitHubClient.GitCommit>) selectedItems.get("GITHUB_COMMIT")) { files.add(new Downloadable("github-commit-" + commit.shortSha() + ".json", commit.rawJson() != null ? commit.rawJson() : "{}")); if (commit.diffContent() != null && !commit.diffContent().isBlank()) files.add(new Downloadable("github-commit-" + commit.shortSha() + "-diff.json", commit.toDiffJson())); } }
    @SuppressWarnings("unchecked") private static void collectActions(Map<String, List<?>> selectedItems, List<Downloadable> files) { if (!selectedItems.containsKey("GITHUB_ACTION")) return; for (GitHubActionResult action : (List<GitHubActionResult>) selectedItems.get("GITHUB_ACTION")) { String part = "run-" + action.getRunId(); if (action.getJobId() > 0) part += "-job-" + action.getJobId(); files.add(new Downloadable("github-action-" + part + ".json", action.getRawJson() != null ? action.getRawJson() : "{}")); var node = Json.MAPPER.createObjectNode().put("runId", action.getRunId()).put("jobId", action.getJobId()).put("url", action.getHtmlUrl() != null ? action.getHtmlUrl() : "").put("status", action.getStatus() != null ? action.getStatus() : "").put("conclusion", action.getConclusion() != null ? action.getConclusion() : "").put("logs", action.getJobLogs() != null ? action.getJobLogs() : ""); try { node.set("steps", Json.MAPPER.readTree(action.getStepsJson())); } catch (Exception ignored) { node.put("steps", "[]"); } String json; try { json = Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node); } catch (Exception ignored) { json = node.toString(); } files.add(new Downloadable("github-action-" + part + "-logs.json", json)); } }
    @SuppressWarnings("unchecked") private static void collectPrs(Map<String, List<?>> selectedItems, List<Downloadable> files) { if (!selectedItems.containsKey("GITHUB_PR")) return; for (GitHubPrResult pr : (List<GitHubPrResult>) selectedItems.get("GITHUB_PR")) { files.add(new Downloadable("github-pr-" + pr.getNumber() + ".json", pr.getRawJson() != null ? pr.getRawJson() : "{}")); String diffJson = Json.MAPPER.createObjectNode().put("number", pr.getNumber()).put("url", pr.getUrl()).put("diff", pr.getDiffContent() != null ? pr.getDiffContent() : "").toString(); files.add(new Downloadable("github-pr-" + pr.getNumber() + "-diff.json", diffJson)); } }
    private static void writeGitHubFiles(List<Downloadable> files, Path root, java.util.function.Consumer<String> log, java.util.function.BooleanSupplier isCancelled, ProgressReporter updateProgress) throws IOException { if (files.isEmpty()) return; Path output = root.resolve("github"); Files.createDirectories(output); for (int i = 0; i < files.size(); i++) { if (isCancelled.getAsBoolean()) break; Downloadable file = files.get(i); Path path = output.resolve(file.filename()); Files.writeString(path, file.content()); log.accept(Icons.CHECK + " Saved " + path.getFileName()); updateProgress.report(i + 1, files.size()); } }
    private static boolean valid(String value) { return value != null && !value.isBlank(); }
    @FunctionalInterface private interface ProgressReporter { void report(double workDone, double totalWork); }
    private record Downloadable(String filename, String content) {}
}
