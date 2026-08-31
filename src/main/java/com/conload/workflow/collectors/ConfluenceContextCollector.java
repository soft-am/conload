package com.conload.workflow.collectors;

import com.conload.confluence.ConfluenceClient;
import com.conload.confluence.ConfluenceUrlParser;
import com.conload.github.GitHubClient;
import com.conload.jira.JiraClient;
import com.conload.markdown.MarkdownConverter;
import com.conload.model.AppConfig;
import com.conload.model.ConfluencePage;
import com.conload.model.JiraIssue;
import com.conload.service.AttachmentDownloaderService;
import com.conload.util.FileUtil;
import com.conload.workflow.WorkflowCallbacks;
import com.conload.workflow.WorkflowContext;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collector for the Confluence context (step a3):
 * <ol>
 *   <li>Fetch Jira remote links and scan the ADF description/comments for
 *       Confluence page URLs.</li>
 *   <li>Deduplicate page IDs.</li>
 *   <li>Download each linked Confluence page as Markdown.</li>
 * </ol>
 * All MDs land in {@code <contextRoot>/confluence/}.
 */
public final class ConfluenceContextCollector {

    private static final int MAX_PAGES = 20;
    private static final Pattern CONFLUENCE_URL =
            Pattern.compile("https?://[^\\s\"<>]+/wiki/(?:spaces/[^/]+/)?(?:pages/\\d+|[^\\s\"<>]*pageId=\\d+)");

    private final AppConfig config;
    private final String confluenceBaseUrl;
    private final WorkflowCallbacks callbacks;

    public ConfluenceContextCollector(AppConfig config, String confluenceBaseUrl, WorkflowCallbacks callbacks) {
        this.config = config;
        this.confluenceBaseUrl = confluenceBaseUrl;
        this.callbacks = callbacks;
    }

    /**
     * @param jiraClient   the Jira client (for remote links)
     * @param issuesToScan Jira issues whose description/comments to scan
     * @param confluenceDir output directory ({@code <contextRoot>/confluence})
     * @return summaries of downloaded Confluence pages
     */
    public List<WorkflowContext.ConfluencePageSummary> collect(
            JiraClient jiraClient, List<JiraIssue> issuesToScan, Path confluenceDir) throws Exception {

        if (confluenceBaseUrl == null || confluenceBaseUrl.isBlank()) {
            callbacks.onLog("[CONFLUENCE] No Confluence base URL configured — skipping.");
            return List.of();
        }

        Files.createDirectories(confluenceDir);
        ConfluenceClient confClient = new ConfluenceClient(config);
        ConfluenceUrlParser urlParser = new ConfluenceUrlParser();
        MarkdownConverter mdConverter = new MarkdownConverter();
        AttachmentDownloaderService attachmentDownloader = new AttachmentDownloaderService(confClient, callbacks::onLog);

        // 1. Collect page IDs from remote links + ADF URLs
        Set<String> pageIds = new LinkedHashSet<>();
        for (JiraIssue issue : issuesToScan) {
            if (callbacks.isCancelled()) break;
            collectFromRemoteLinks(jiraClient, issue.getKey(), urlParser, pageIds);
            collectFromAdf(issue.getFields().getDescription(), urlParser, pageIds);
            if (issue.getFields().getComment() != null) {
                for (var c : issue.getFields().getComment().getComments()) {
                    collectFromAdf(c.getBody(), urlParser, pageIds);
                }
            }
        }

        if (pageIds.isEmpty()) {
            callbacks.onLog("[CONFLUENCE] No linked Confluence pages found.");
            return List.of();
        }

        // 2. Download each page as MD (cap at MAX_PAGES)
        callbacks.onProgress("Confluence", "Downloading " + Math.min(pageIds.size(), MAX_PAGES) + " page(s)…");
        List<WorkflowContext.ConfluencePageSummary> summaries = new ArrayList<>();
        int count = 0;
        for (String pageId : pageIds) {
            if (count >= MAX_PAGES || callbacks.isCancelled()) break;
            try {
                var summary = downloadPage(confClient, mdConverter, attachmentDownloader, pageId, confluenceDir);
                if (summary != null) summaries.add(summary);
                count++;
            } catch (Exception e) {
                callbacks.onLog("[CONFLUENCE] Failed to download page " + pageId + ": " + e.getMessage());
            }
        }
        return summaries;
    }

    private void collectFromRemoteLinks(JiraClient client, String issueKey,
                                         ConfluenceUrlParser parser, Set<String> pageIds) {
        try {
            for (JiraClient.RemoteLink link : client.getRemoteLinks(confluenceBaseUrl, issueKey)) {
                String pageId = parser.extractPageId(link.getUrl());
                if (pageId != null) pageIds.add(pageId);
            }
        } catch (Exception e) {
            callbacks.onLog("[CONFLUENCE] Remote links fetch failed for " + issueKey + ": " + e.getMessage());
        }
    }

    /** Scan an ADF document tree for Confluence page URLs. */
    private void collectFromAdf(JsonNode adf, ConfluenceUrlParser parser, Set<String> pageIds) {
        if (adf == null || adf.isNull()) return;
        scanNodeForUrls(adf, parser, pageIds);
    }

    private void scanNodeForUrls(JsonNode node, ConfluenceUrlParser parser, Set<String> pageIds) {
        if (node == null || node.isNull()) return;
        if (node.isTextual()) {
            Matcher m = CONFLUENCE_URL.matcher(node.asText());
            while (m.find()) {
                String pageId = parser.extractPageId(m.group());
                if (pageId != null) pageIds.add(pageId);
            }
        }
        if (node.isObject() || node.isArray()) {
            for (JsonNode child : node) {
                scanNodeForUrls(child, parser, pageIds);
            }
        }
    }

    private WorkflowContext.ConfluencePageSummary downloadPage(
            ConfluenceClient client, MarkdownConverter converter,
            AttachmentDownloaderService attachmentDownloader,
            String pageId, Path dir) throws Exception {
        callbacks.onProgress("Confluence", "Downloading page " + pageId + "…");
        ConfluencePage page = client.getPage(confluenceBaseUrl, pageId);
        String storageXhtml = page.getStorageValue();
        String webUrl = page.getLinks() != null && page.getLinks().getWebui() != null
                ? confluenceBaseUrl + "/wiki" + page.getLinks().getWebui() : "";

        var result = converter.convert(storageXhtml, page.getTitle(), webUrl);
        String safeName = FileUtil.sanitizeFilename(page.getTitle());
        Path mdFile = dir.resolve(safeName + ".md");
        Path mediaFolder = dir.resolve("media");
        FileUtil.ensureDir(mediaFolder);

        // Download images referenced by ac:image in the converted markdown
        for (String imgFilename : result.requiredImageFilenames()) {
            attachmentDownloader.downloadNamedAttachment(confluenceBaseUrl, page.getId(), imgFilename, mediaFolder);
        }

        // Download ALL remaining attachments (dedup handled internally)
        attachmentDownloader.downloadAttachments(confluenceBaseUrl, page.getId(), mediaFolder);

        FileUtil.writeText(mdFile, result.markdown());

        callbacks.onLog("[CONFLUENCE] ✓ " + page.getTitle());

        return new WorkflowContext.ConfluencePageSummary(
                page.getTitle(),
                "", // creator not in the basic page model; would need /expand=version,history
                "",
                webUrl,
                mdFile.toAbsolutePath().toString()
        );
    }
}
