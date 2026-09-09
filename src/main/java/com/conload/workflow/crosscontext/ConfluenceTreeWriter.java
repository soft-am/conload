package com.conload.workflow.crosscontext;

import com.conload.confluence.ConfluenceClient;
import com.conload.confluence.ConfluenceUrlParser;
import com.conload.markdown.MarkdownConverter;
import com.conload.model.ConfluencePage;
import com.conload.service.AttachmentDownloaderService;
import com.conload.util.FileUtil;
import com.conload.workflow.WorkflowCallbacks;
import com.conload.workflow.WorkflowStoppedException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recursively downloads a Confluence page and its child pages as Markdown,
 * writing files with the {@code confluence_} prefix in a flat directory layout:
 * <ul>
 *   <li>Root: {@code confluence_<Title>.md}</li>
 *   <li>Child: {@code confluence_<ParentTitle>_<ChildTitle>.md}</li>
 *   <li>Grandchild: {@code confluence_<Parent>_<Child>_<Grandchild>.md}</li>
 * </ul>
 * Media/attachments are downloaded into {@code <dir>/media/}.
 * <p>
 * The recursion mirrors {@link com.conload.service.RecursivePageProcessor#processNode}
 * but produces the flat filename hierarchy and the {@code confluence_} prefix
 * required by the cross-context folder layout.
 */
final class ConfluenceTreeWriter {

    private final ConfluenceClient client;
    private final ConfluenceUrlParser urlParser;
    private final MarkdownConverter converter = new MarkdownConverter();
    private final AttachmentDownloaderService attachmentDownloader;
    private final WorkflowCallbacks callbacks;
    private final Map<String, List<String>> sourceUrls;

    ConfluenceTreeWriter(ConfluenceClient client, ConfluenceUrlParser urlParser,
                          AttachmentDownloaderService attachmentDownloader,
                          WorkflowCallbacks callbacks,
                          Map<String, List<String>> sourceUrls) {
        this.client = client;
        this.urlParser = urlParser;
        this.attachmentDownloader = attachmentDownloader;
        this.callbacks = callbacks;
        this.sourceUrls = sourceUrls;
    }

    /**
     * Download a page and all its children recursively.
     *
     * @param baseUrl     e.g. https://site.atlassian.net
     * @param pageId      Confluence numeric page ID
     * @param dir         output directory (files written here, media in {@code dir/media})
     * @param namePrefix  builds up as we descend (empty for root, "ParentTitle" for children)
     * @param visited     global set of page IDs already downloaded (prevents loops)
     * @param counter     {@code int[0]} that accumulates the total page count
     */
    void download(String baseUrl, String pageId, Path dir, String namePrefix,
                   Set<String> visited, int[] counter) {
        if (callbacks.isCancelled()) return;
        if (pageId == null || pageId.isBlank() || visited.contains(pageId)) return;
        visited.add(pageId);

        try {
            ConfluencePage page = client.getPage(baseUrl, pageId);
            counter[0]++;

            String webUrl = page.getLinks() != null && page.getLinks().getWebui() != null
                    ? baseUrl + "/wiki" + page.getLinks().getWebui() : "";

            var result = converter.convert(page.getStorageValue(), page.getTitle(), webUrl);

            String safeTitle = FileUtil.sanitizeFilename(page.getTitle());
            String prefix = namePrefix.isEmpty() ? "" : namePrefix + "_";
            Path mdFile = dir.resolve("confluence_" + prefix + safeTitle + ".md");
            Path mediaFolder = dir.resolve("media");
            FileUtil.ensureDir(mediaFolder);

            for (String img : result.requiredImageFilenames()) {
                attachmentDownloader.downloadNamedAttachment(baseUrl, page.getId(), img, mediaFolder);
            }
            attachmentDownloader.downloadAttachments(baseUrl, page.getId(), mediaFolder);

            FileUtil.writeText(mdFile, result.markdown());
            callbacks.onLog("[CONFLUENCE] ✓ " + mdFile.getFileName());

            if (!webUrl.isBlank()) {
                sourceUrls.computeIfAbsent(mdFile.getFileName().toString(), k -> new java.util.ArrayList<>())
                        .add(webUrl);
            }

            String childPrefix = namePrefix.isEmpty() ? safeTitle : namePrefix + "_" + safeTitle;
            List<ConfluencePage> children = client.getChildPages(baseUrl, pageId);
            for (ConfluencePage child : children) {
                if (callbacks.isCancelled()) break;
                download(baseUrl, child.getId(), dir, childPrefix, visited, counter);
            }
        } catch (Exception e) {
            if (callbacks.isHardStopped() || WorkflowCallbacks.isInterruptCause(e)) {
                throw new WorkflowStoppedException(e);
            }
            callbacks.onError("Confluence", "Failed page " + pageId + ": " + e.getMessage());
        }
    }
}
