package com.conload.service;

import com.conload.util.FileUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generates deterministic numeric hierarchy-based filenames.
 *
 * Naming scheme (dot-separated sibling index at each level):
 *   Depth 0 (root page 1):          1_Title_2026-05-25_18-10.md
 *   Depth 0 (root page 2):          2_Title_2026-05-25_18-11.md
 *   Depth 1 (child 1 of root 1):    1.1_Title_2026-05-25_18-12.md
 *   Depth 1 (child 2 of root 1):    1.2_Title_2026-05-25_18-13.md
 *   Depth 2 (grandchild 1):         1.1.1_Title_2026-05-25_18-14.md
 *   Depth 2 (grandchild 2):         1.1.2_Title_2026-05-25_18-15.md
 *
 * The numericPrefix is built by RecursivePageProcessor as the tree is traversed.
 */
public class FileNamingService {

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    /**
     * Build full filename.
     * Example: 1.2.3_Kafka_Flow_2026-05-25_18-11.md
     *
     * @param numericPrefix  e.g. "1", "1.2", "1.2.3"
     * @param title          page title
     */
    public String buildFilename(String numericPrefix, String title) {
        String safeTitle = FileUtil.titleToFilenamePart(title);
        String timestamp = LocalDateTime.now().format(DATE_FMT);
        return numericPrefix + "_" + safeTitle + "_" + timestamp + ".md";
    }

    /**
     * Build the session folder name with timestamp.
     * Example: confluencePages_for_co_pilot_2026-05-25_20-30-10
     */
    public String buildSessionFolderName() {
        DateTimeFormatter full = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        return "confluencePages_for_co_pilot_" + LocalDateTime.now().format(full);
    }
}
