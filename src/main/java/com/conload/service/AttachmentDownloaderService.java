package com.conload.service;

import com.conload.confluence.ConfluenceClient;
import com.conload.model.ConfluenceAttachment;
import com.conload.util.FileUtil;
import com.conload.ui.Icons;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Downloads attachments to the media/ folder with smart deduplication.
 *
 * Deduplication rule:
 *   Attachments that share the same logical "base name" (same content, multiple formats)
 *   are grouped. Only the BEST format is downloaded per group.
 *
 *   Priority (highest = best):
 *     1. .png  (including drawio previews like diagram.drawio.png)
 *     2. .jpg / .jpeg
 *     3. .gif
 *     4. .webp / .svg
 *     5. everything else (.drawio source, .pdf, .xml, …)
 *
 *   Example: if a page has  diagram.drawio  AND  diagram.drawio.png
 *            only  diagram.drawio.png  is downloaded (higher priority).
 *
 *   Example: if a page has  photo.jpg  AND  photo.png
 *            only  photo.png  is downloaded.
 */
public class AttachmentDownloaderService {

    private static final Logger log = Logger.getLogger(AttachmentDownloaderService.class.getName());

    // Format priority: lower number = higher priority (better format)
    private static final Map<String, Integer> FORMAT_PRIORITY = new LinkedHashMap<>();
    static {
        FORMAT_PRIORITY.put(".png",  1);
        FORMAT_PRIORITY.put(".jpg",  2);
        FORMAT_PRIORITY.put(".jpeg", 2);
        FORMAT_PRIORITY.put(".gif",  3);
        FORMAT_PRIORITY.put(".webp", 4);
        FORMAT_PRIORITY.put(".svg",  4);
        // everything else (drawio source, pdf, xml, …) gets priority 10
    }

    private final ConfluenceClient client;
    private final Consumer<String> logger;

    // Track filenames already written to disk across all pages in this session
    private final Set<String> downloadedFiles = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public AttachmentDownloaderService(ConfluenceClient client, Consumer<String> logger) {
        this.client = client;
        this.logger = logger;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Download all attachments for a page into mediaFolder, applying deduplication.
     */
    public List<String> downloadAttachments(String baseUrl, String pageId, Path mediaFolder) {
        List<ConfluenceAttachment> raw;
        try {
            raw = client.getAttachments(baseUrl, pageId);
        } catch (Exception e) {
            logger.accept("  [WARN] Could not list attachments for page " + pageId + ": " + e.getMessage());
            return List.of();
        }

        // Deduplicate: one file per logical content item
        List<ConfluenceAttachment> selected = deduplicateAttachments(raw);
        int skipped = raw.size() - selected.size();
        if (skipped > 0) {
            logger.accept("  [DEDUP] " + raw.size() + " attachments " + Icons.ARROW_RIGHT + " " + selected.size()
                + " selected (skipped " + skipped + " lower-priority duplicates)");
        }

        FileUtil.ensureDir(mediaFolder);
        List<String> downloaded = new ArrayList<>();

        for (ConfluenceAttachment att : selected) {
            String filename = FileUtil.sanitizeFilename(att.getTitle());
            Path targetFile  = mediaFolder.resolve(filename);

            if (downloadedFiles.contains(filename) || Files.exists(targetFile)) {
                log.fine("Skip (already exists): " + filename);
                downloaded.add(filename);
                continue;
            }
            if (att.getId() == null) {
                logger.accept("  [WARN] No ID for attachment: " + filename);
                continue;
            }

            String url = client.buildAttachmentDownloadUrl(baseUrl, pageId, att.getId());
            try {
                byte[] data = client.downloadAttachment(url);
                FileUtil.writeBytes(targetFile, data);
                downloadedFiles.add(filename);
                downloaded.add(filename);
                logger.accept("  [DL] " + filename + " (" + data.length / 1024 + " KB)");
            } catch (Exception e) {
                logger.accept("  [ERROR] Failed to download " + filename + ": " + e.getMessage());
            }
        }
        return downloaded;
    }

    /**
     * Download a single attachment by filename (referenced from storage format ac:image).
     * Applies the same priority logic: if the requested file is already present, skip.
     */
    public boolean downloadNamedAttachment(String baseUrl, String pageId,
                                            String filename, Path mediaFolder) {
        String safeFilename = FileUtil.sanitizeFilename(filename);
        Path targetFile = mediaFolder.resolve(safeFilename);
        if (Files.exists(targetFile)) return true;

        try {
            List<ConfluenceAttachment> all = client.getAttachments(baseUrl, pageId);

            // Check if a higher-priority version of this file already exists or is in the list
            String base = getBaseName(safeFilename);
            Optional<ConfluenceAttachment> best = all.stream()
                .filter(a -> getBaseName(FileUtil.sanitizeFilename(a.getTitle())).equalsIgnoreCase(base))
                .min(Comparator.comparingInt(a -> formatPriority(a.getTitle())));

            ConfluenceAttachment toDownload = best
                .or(() -> all.stream()
                    .filter(a -> safeFilename.equalsIgnoreCase(FileUtil.sanitizeFilename(a.getTitle())))
                    .findFirst())
                .orElse(null);

            if (toDownload == null || toDownload.getId() == null) return false;

            // If the best version is different from what was requested, use its filename
            String actualFilename = FileUtil.sanitizeFilename(toDownload.getTitle());
            Path actualTarget = mediaFolder.resolve(actualFilename);
            if (Files.exists(actualTarget)) return true;

            String url = client.buildAttachmentDownloadUrl(baseUrl, pageId, toDownload.getId());
            byte[] data = client.downloadAttachment(url);
            FileUtil.ensureDir(mediaFolder);
            FileUtil.writeBytes(actualTarget, data);
            downloadedFiles.add(actualFilename);
            logger.accept("  [DL] " + actualFilename + " (" + data.length / 1024 + " KB)");
            return true;
        } catch (Exception e) {
            logger.accept("  [WARN] Could not download image " + filename + ": " + e.getMessage());
        }
        return false;
    }

    // =========================================================================
    // Deduplication
    // =========================================================================

    /**
     * From a list of attachments, select only the best-format representative
     * for each logical content item.
     *
     * Grouping key = base name (filename with all known extensions stripped).
     * Within each group, the attachment with the lowest formatPriority() wins.
     *
     * If a group has only one member it is kept as-is.
     */
    private List<ConfluenceAttachment> deduplicateAttachments(List<ConfluenceAttachment> attachments) {
        // Group by normalised base name (case-insensitive)
        Map<String, List<ConfluenceAttachment>> groups = new LinkedHashMap<>();
        for (ConfluenceAttachment att : attachments) {
            String base = getBaseName(att.getTitle()).toLowerCase();
            groups.computeIfAbsent(base, k -> new ArrayList<>()).add(att);
        }

        List<ConfluenceAttachment> result = new ArrayList<>();
        for (List<ConfluenceAttachment> group : groups.values()) {
            // Pick the one with the best (lowest) format priority
            ConfluenceAttachment best = group.stream()
                .min(Comparator.comparingInt(a -> formatPriority(a.getTitle())))
                .orElseThrow();
            result.add(best);

            if (group.size() > 1) {
                group.stream()
                    .filter(a -> a != best)
                    .forEach(a -> log.fine("DEDUP skip: " + a.getTitle()
                        + " (kept: " + best.getTitle() + ")"));
            }
        }
        return result;
    }

    /**
     * Strip all known extensions to find the logical "base name".
     *
     * Examples:
     *   diagram.drawio.png  →  diagram
     *   diagram.drawio      →  diagram
     *   photo.jpg           →  photo
     *   photo.png           →  photo
     *   report.pdf          →  report
     */
    private static String getBaseName(String filename) {
        if (filename == null) return "";
        String name = filename;
        // Strip known double extensions first
        String lower = name.toLowerCase();
        for (String dbl : List.of(".drawio.png", ".drawio.svg", ".drawio.xml",
                                   ".gliffy.png", ".gliffy.svg")) {
            if (lower.endsWith(dbl)) {
                return name.substring(0, name.length() - dbl.length());
            }
        }
        // Strip single extension
        int dot = lower.lastIndexOf('.');
        if (dot > 0) return name.substring(0, dot);
        return name;
    }

    /**
     * Return the format priority for a filename.
     * Lower number = preferred format.
     * PNG = 1 (best), JPG = 2, GIF = 3, SVG/WEBP = 4, everything else = 10.
     */
    private static int formatPriority(String filename) {
        if (filename == null) return 10;
        String lower = filename.toLowerCase();
        // Check double extensions first (drawio.png is still a PNG → priority 1)
        if (lower.endsWith(".png"))  return 1;
        if (lower.endsWith(".jpg"))  return 2;
        if (lower.endsWith(".jpeg")) return 2;
        if (lower.endsWith(".gif"))  return 3;
        if (lower.endsWith(".webp")) return 4;
        if (lower.endsWith(".svg"))  return 4;
        return 10; // .drawio, .pdf, .xml, .docx, …
    }
}
