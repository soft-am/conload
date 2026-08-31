package com.conload.service;

import com.conload.confluence.ConfluenceClient;
import com.conload.confluence.ConfluenceUrlParser;
import com.conload.markdown.MarkdownConverter;
import com.conload.model.ConfluencePage;
import com.conload.model.PageNode;
import com.conload.util.FileUtil;
import com.conload.ui.Icons;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Orchestrates the full recursive page download.
 *
 * Entry point:
 *  - {@link #processSelectedPages(String, List, String, AppConfig)} — multiple pageIds from space browser
 */
public class RecursivePageProcessor {

    private static final Logger log = Logger.getLogger(RecursivePageProcessor.class.getName());

    private final ConfluenceClient client;
    private final ConfluenceUrlParser urlParser;
    private final MarkdownConverter markdownConverter;
    private final AttachmentDownloaderService attachmentDownloader;
    private final FileNamingService fileNaming;
    private final Consumer<String> logger;
    private final AtomicBoolean cancelled;

    private final Set<String> visitedPageIds = new HashSet<>();

    public RecursivePageProcessor(
        ConfluenceClient client,
        ConfluenceUrlParser urlParser,
        AttachmentDownloaderService attachmentDownloader,
        FileNamingService fileNaming,
        Consumer<String> logger,
        AtomicBoolean cancelled
    ) {
        this.client = client;
        this.urlParser = urlParser;
        this.markdownConverter = new MarkdownConverter();
        this.attachmentDownloader = attachmentDownloader;
        this.fileNaming = fileNaming;
        this.logger = logger;
        this.cancelled = cancelled;
    }

    // =========================================================================
    // Space-browser multi-page entry point
    // =========================================================================

    /**
     * A page selected by the user in the Space Browser.
     *
     * @param pageId    Confluence numeric page ID
     * @param title     display title (for logging)
     * @param recursive whether to also download all child pages
     */
    public record SelectedPage(String pageId, String title, boolean recursive) {}

    /**
     * Download a user-defined list of pages selected in the Space Browser.
     *
     * @param baseUrl   e.g. https://company.atlassian.net
     * @param pages     pages chosen by the user (each may be recursive)
     * @param savePath  output root directory
     */
    public void processSelectedPages(String baseUrl,
                                     List<SelectedPage> pages,
                                     String savePath,
                                     com.conload.model.AppConfig appConfig)
            throws Exception {

        logger.accept("=== Confluence AI Exporter — Space Browser mode ===");
        logger.accept("Base URL : " + baseUrl);
        logger.accept("Pages    : " + pages.size() + " selected");
        logger.accept("Output   : " + savePath);
        logger.accept("");

        String sessionDir = fileNaming.buildSessionFolderName();
        Path sessionRoot = Path.of(savePath, sessionDir);
        FileUtil.ensureDir(sessionRoot);

        int total = pages.size();
        int[] counter = {0};

        for (SelectedPage sel : pages) {
            if (cancelled.get()) break;
            counter[0]++;

            logger.accept("[" + counter[0] + "/" + total + "] Fetching: \"" + sel.title() + "\"  (id=" + sel.pageId() + ")");

            ConfluencePage root = client.getPage(baseUrl, sel.pageId());

            String prefix = String.valueOf(counter[0]);
            PageNode rootNode = new PageNode(root, 0, prefix);

            if (sel.recursive()) {
                buildTree(baseUrl, rootNode, prefix);
            }

            String pageWebUrl = baseUrl + "/wiki/pages/" + sel.pageId();
            processNode(rootNode, baseUrl, counter, total, pageWebUrl, sessionRoot);
        }

        if (!cancelled.get()) {
            logger.accept("");
            logger.accept("=== Done! " + counter[0] + " page(s) exported. ===");
            logger.accept("Output: " + sessionRoot);
        } else {
            logger.accept("\n[STOPPED] Download cancelled.");
        }
    }

    // =========================================================================
    // Tree building
    // =========================================================================

    private void buildTree(String baseUrl, PageNode node, String prefix) throws Exception {
        if (cancelled.get()) return;
        String pageId = node.getPage().getId();
        if (visitedPageIds.contains(pageId)) return;
        visitedPageIds.add(pageId);

        List<ConfluencePage> children = client.getChildPages(baseUrl, pageId);
        logger.accept("  " + prefix + " " + Icons.ARROW_RIGHT + " " + children.size() + " child page(s)");

        for (int i = 0; i < children.size(); i++) {
            if (cancelled.get()) return;
            // Child sibling prefix: "1.1", "1.2", "1.3"; grandchild: "1.1.1" …
            String childPrefix = prefix + "." + (i + 1);
            ConfluencePage fullChild = client.getPage(baseUrl, children.get(i).getId());
            PageNode childNode = new PageNode(fullChild, node.getDepth() + 1, childPrefix);
            node.addChild(childNode);
            buildTree(baseUrl, childNode, childPrefix);
        }
    }

    // =========================================================================
    // Writing pages
    // =========================================================================

    private void processNode(PageNode node, String baseUrl,
                              int[] counter, int total, String sourceUrl,
                              Path sessionRoot) throws Exception {
        if (cancelled.get()) return;

        ConfluencePage page = node.getPage();
        String numericPrefix = node.getHierarchyPrefix();
        counter[0]++;

        String filename = fileNaming.buildFilename(numericPrefix, page.getTitle());
        Path outputFile  = sessionRoot.resolve(filename);
        Path mediaFolder = outputFile.getParent().resolve("media");
        FileUtil.ensureDir(mediaFolder);

        String indent = "  ".repeat(node.getDepth());
        logger.accept("[" + counter[0] + "/" + total + "] " + indent
            + numericPrefix + " \"" + page.getTitle() + "\"  " + Icons.ARROW_RIGHT + "  " + filename);

        String pageWebUrl = sourceUrl;
        if (page.getLinks() != null && page.getLinks().getWebui() != null) {
            pageWebUrl = baseUrl + "/wiki" + page.getLinks().getWebui();
        }

        MarkdownConverter.ConversionResult result =
            markdownConverter.convert(page.getStorageValue(), page.getTitle(), pageWebUrl);

        for (String imgFilename : result.requiredImageFilenames()) {
            attachmentDownloader.downloadNamedAttachment(baseUrl, page.getId(), imgFilename, mediaFolder);
        }

        attachmentDownloader.downloadAttachments(baseUrl, page.getId(), mediaFolder);

        FileUtil.writeText(outputFile, result.markdown());

        for (PageNode child : node.getChildren()) {
            if (cancelled.get()) return;
            processNode(child, baseUrl, counter, total, sourceUrl, sessionRoot);
        }
    }
}
