package com.conload.workflow.crosscontext;

import com.conload.util.FileUtil;
import com.conload.workflow.WorkflowCallbacks;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Walks the actual filesystem tree produced by {@link CrossContextBuilder}
 * and renders a human-readable {@code cross_context_hierarchy.md} file
 * showing every context file with its source tag and nesting level.
 * <p>
 * Tags: {@code [jira]}, {@code [confluence]}, {@code [commits]}, {@code [pr]}, {@code [often]}.
 * The hierarchy file itself is excluded from the listing.
 */
final class HierarchyDocWriter {

    private static final Pattern JIRA_KEY_DIR =
            Pattern.compile("^([A-Z][A-Z0-9_]+-\\d+)(?:\\s*\\(Epic\\))?$");

    private final WorkflowCallbacks callbacks;

    HierarchyDocWriter(WorkflowCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    /**
     * Walk {@code contextRoot} and write {@code cross_context_hierarchy.md}
     * at the top level.
     *
     * @param sourceUrls  maps leaf file/directory name → original source URLs;
     *                    each URL list is appended (comma-separated) to the
     *                    matching hierarchy line
     */
    void render(Path contextRoot, int jiraCount, int confluenceCount, int commitCount,
                Map<String, List<String>> sourceUrls) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Cross-Context Hierarchy\n\n");
        sb.append("| Metric | Count |\n|---|---|\n");
        sb.append("| Jira issues | ").append(jiraCount).append(" |\n");
        sb.append("| Confluence pages | ").append(confluenceCount).append(" |\n");
        sb.append("| Commit files | ").append(commitCount).append(" |\n\n");
        sb.append("```\n");
        sb.append(contextRoot.getFileName()).append("/")
                .append(formatUrls(sourceUrls.get(contextRoot.getFileName().toString()))).append("\n");
        appendTree(sb, contextRoot, contextRoot, 1, sourceUrls);
        sb.append("```\n");

        Path outFile = contextRoot.resolve("cross_context_hierarchy.md");
        try {
            FileUtil.writeText(outFile, sb.toString());
            callbacks.onLog("[HIERARCHY] ✓ " + outFile.getFileName());
        } catch (Exception e) {
            callbacks.onError("Hierarchy", "Failed: " + e.getMessage());
        }
    }

    private void appendTree(StringBuilder sb, Path root, Path dir, int depth,
                            Map<String, List<String>> sourceUrls) {
        File[] children = dir.toFile().listFiles();
        if (children == null) return;
        Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        String indent = "  ".repeat(depth);
        for (File child : children) {
            String name = child.getName();
            if (name.equals("media")) continue;
            if (name.equals("cross_context_hierarchy.md")) continue;

            String tag = tagFor(name);
            sb.append(indent);
            if (tag != null) sb.append("[").append(tag).append("] ");
            sb.append(name);
            if (child.isDirectory()) sb.append("/");
            sb.append(formatUrls(sourceUrls.get(name)));
            sb.append("\n");

            if (child.isDirectory()) {
                appendTree(sb, root, child.toPath(), depth + 1, sourceUrls);
            }
        }
    }

    /** Format a URL list as {@code " - url1, url2"} or empty string. */
    private static String formatUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) return "";
        return " - " + String.join(", ", urls);
    }

    private static String tagFor(String name) {
        if (JIRA_KEY_DIR.matcher(name).matches()) return "jira";
        if (name.startsWith("jira_")) return "jira";
        if (name.startsWith("confluence_")) return "confluence";
        if (name.startsWith("commits_")) return "commits";
        if (name.startsWith("pr_")) return "pr";
        if (name.contains("often_words")) return "often";
        return null;
    }
}
