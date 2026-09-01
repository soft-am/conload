package com.conload.ui.projects.files;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a nested, expandable/collapsible tree of Confluence page titles
 * from the flat, numeric-prefixed filenames written by
 * {@link com.conload.service.FileNamingService} /
 * {@link com.conload.service.RecursivePageProcessor}.
 *
 * <p>On-disk filename shape:
 * <pre>{@code
 *   <prefix>_<safeTitle>_<yyyy-MM-dd_HH-mm>.md
 * }</pre>
 * where {@code <prefix>} is a dot-separated sibling ordinal ("1", "1.6",
 * "1.6.1"). This collaborator parses that prefix to reconstruct the page
 * hierarchy for the side panel — pages appear as titled, indented nodes
 * (e.g. {@code "1.6  Kafka Flow"}) rather than as raw filenames.
 *
 * <p>Display-only: no files are renamed or moved. Nodes wrap the original
 * {@link File} so open / rename / export menu actions keep working.
 */
public final class ConfluencePageTree {
    private ConfluencePageTree() {}

    /** Folder name written by {@code FileNamingService.buildSessionFolderName}. */
    private static final Pattern LOT_FOLDER_RE =
        Pattern.compile("^confluencePages_for_co_pilot_.+");
    /** Matches {@code 1.6_Kafka_Flow_2026-09-02_14-31.md} → prefix "1.6",
     *  title "Kafka_Flow" (underscores stood in for spaces on write). */
    private static final Pattern PAGE_RE =
        Pattern.compile("^(\\d+(?:\\.\\d+)*)_(.+)_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2})\\.md$");

    /** True when {@code folder} is a Confluence page-lot produced by the
     *  UI download path (name + at least one page file). */
    public static boolean isConfluencePageLot(File folder) {
        if (folder == null || !folder.isDirectory()) return false;
        String name = folder.getName();
        if (name == null || !LOT_FOLDER_RE.matcher(name).matches()) return false;
        File[] files = folder.listFiles();
        if (files == null) return false;
        for (File f : files)
            if (f.isFile() && PAGE_RE.matcher(f.getName()).matches()) return true;
        return false;
    }

    /** Builds the root-level page nodes (prefixes "1", "2", …) with their
     *  nested descendants. Returns an empty list for non-lot folders. */
    public static List<TreeItem<File>> buildNestedTree(File folder) {
        if (folder == null || !folder.isDirectory()) return List.of();
        File[] files = folder.listFiles();
        if (files == null) return List.of();
        List<PageEntry> pages = new ArrayList<>();
        for (File f : files) {
            Matcher m = PAGE_RE.matcher(f.getName());
            if (m.matches()) {
                String prefix = m.group(1);
                String title = m.group(2).replace('_', ' ');
                pages.add(new PageEntry(prefix, prefix.split("\\."), prefix + "  " + title, f));
            }
        }
        if (pages.isEmpty()) return List.of();
        Map<String, List<PageEntry>> byParent = new HashMap<>();
        for (PageEntry p : pages)
            byParent.computeIfAbsent(parentPrefix(p.parts), k -> new ArrayList<>()).add(p);
        return buildLevel("", byParent);
    }

    private static List<TreeItem<File>> buildLevel(String parentPrefix, Map<String, List<PageEntry>> byParent) {
        List<PageEntry> siblings = byParent.get(parentPrefix);
        if (siblings == null || siblings.isEmpty()) return List.of();
        siblings.sort(ConfluencePageTree::comparePrefixes);
        List<TreeItem<File>> items = new ArrayList<>(siblings.size());
        for (PageEntry p : siblings) {
            List<TreeItem<File>> kids = buildLevel(p.prefix, byParent);
            items.add(new ConfluencePageTreeItem(p.file, p.label, kids));
        }
        return items;
    }

    /** Parent prefix of a dotted path: "1.6" → "1", "1.6.1" → "1.6", "1" → "". */
    private static String parentPrefix(String[] parts) {
        if (parts.length <= 1) return "";
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length - 1; i++) sb.append('.').append(parts[i]);
        return sb.toString();
    }

    /** Numeric-aware ordering: "1" < "1.1" < "1.6" < "1.6.1" < "2". */
    private static int comparePrefixes(PageEntry a, PageEntry b) {
        int n = Math.min(a.parts.length, b.parts.length);
        for (int i = 0; i < n; i++) {
            int cmp = Integer.compare(Integer.parseInt(a.parts[i]), Integer.parseInt(b.parts[i]));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.parts.length, b.parts.length);
    }

    private record PageEntry(String prefix, String[] parts, String label, File file) {}

    /** A {@link TreeItem} that wraps a Confluence page file and exposes a
     *  clean {@code "prefix  Title"} label. Children (nested sub-pages)
     *  are supplied explicitly and never re-read from disk, so the
     *  synthesized hierarchy stays stable. */
    public static final class ConfluencePageTreeItem extends TreeItem<File> {
        private final String displayLabel;
        private final List<TreeItem<File>> pageChildren;
        private boolean loaded;

        public ConfluencePageTreeItem(File file, String displayLabel, List<TreeItem<File>> children) {
            super(file);
            this.displayLabel = displayLabel;
            this.pageChildren = children;
        }

        public String getDisplayLabel() { return displayLabel; }

        @Override public ObservableList<TreeItem<File>> getChildren() {
            if (!loaded) {
                loaded = true;
                super.getChildren().setAll(FXCollections.observableArrayList(pageChildren));
            }
            return super.getChildren();
        }

        @Override public boolean isLeaf() { return pageChildren.isEmpty(); }
    }
}
