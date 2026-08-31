package com.conload.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Filesystem utility methods.
 */
public final class FileUtil {

    private static final Logger log = Logger.getLogger(FileUtil.class.getName());

    private FileUtil() {}

    /**
     * Sanitize a string for use as a filename on Windows and macOS.
     * Replaces illegal characters with underscores and trims length.
     */
    public static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "unnamed";
        // Replace Windows + Unix illegal chars
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        // Collapse multiple spaces/underscores
        sanitized = sanitized.replaceAll("[ _]{2,}", "_");
        sanitized = sanitized.strip();
        // Limit to 120 chars to avoid path length issues on Windows
        if (sanitized.length() > 120) {
            sanitized = sanitized.substring(0, 120).strip();
        }
        return sanitized.isBlank() ? "unnamed" : sanitized;
    }

    /**
     * Create directory and all parents, silently if already exists.
     */
    public static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warning("Could not create directory: " + dir + " - " + e.getMessage());
        }
    }

    /**
     * Write text to file with UTF-8 encoding.
     */
    public static void writeText(Path file, String content) throws IOException {
        ensureDir(file.getParent());
        Files.writeString(file, content, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Write binary data to file.
     */
    public static void writeBytes(Path file, byte[] data) throws IOException {
        ensureDir(file.getParent());
        Files.write(file, data);
    }

    /**
     * Returns a safe title for use as part of a filename (spaces -> underscores, trimmed).
     */
    public static String titleToFilenamePart(String title) {
        if (title == null || title.isBlank()) return "Untitled";
        return sanitizeFilename(title).replace(' ', '_');
    }

    /** Produces a filesystem-safe context folder name from the user-supplied
     *  or workflow-generated name. Only truly illegal filesystem characters
     *  are replaced (with {@code _}); spaces, parentheses, and other printable
     *  characters are preserved so the folder name matches the user input.
     *  A blank result falls back to a bare timestamp.
     *
     *  <p>The single source of truth for this logic — previously duplicated in
     *  ProjectService, SearchDownloadService, and ContextAcquisitionController. */
    public static String normalizeContextFolderName(String name) {
        String safe = (name == null ? "" : name)
                .replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_")
                .replaceAll("[ _]{2,}", "_")
                .strip();
        if (safe.length() > 120) safe = safe.substring(0, 120).strip();
        return safe.isBlank() ? String.valueOf(System.currentTimeMillis()) : safe;
    }
}

