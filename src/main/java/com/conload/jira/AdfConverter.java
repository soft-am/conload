package com.conload.jira;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Converts Atlassian Document Format (ADF) JSON to Markdown.
 *
 * ADF is the rich-text format used in Jira Cloud descriptions and comments.
 * Structure: { "type": "doc", "version": 1, "content": [...nodes...] }
 */
public class AdfConverter {

    /** Entry point: convert a top-level ADF document node. */
    public String convert(JsonNode adfDoc) {
        if (adfDoc == null || adfDoc.isNull() || adfDoc.isMissingNode()) return "";
        return convertNode(adfDoc, 0).strip();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Node dispatch
    // ─────────────────────────────────────────────────────────────────────────

    private String convertNode(JsonNode node, int depth) {
        if (node == null || node.isNull()) return "";
        String type = node.path("type").asText("");
        return switch (type) {
            case "doc"          -> convertChildren(node, depth);
            case "paragraph"    -> convertChildren(node, depth) + "\n\n";
            case "heading"      -> convertHeading(node, depth);
            case "text"         -> convertText(node);
            case "hardBreak"    -> "\n";
            case "rule"         -> "\n---\n\n";
            case "bulletList"   -> convertList(node, depth, false);
            case "orderedList"  -> convertList(node, depth, true);
            case "listItem"     -> convertListItem(node, depth);
            case "codeBlock"    -> convertCodeBlock(node);
            case "blockquote"   -> convertBlockquote(node, depth);
            case "table"        -> convertTable(node, depth);
            case "tableRow"     -> "";   // handled inside convertTable
            case "tableCell","tableHeader" -> "";
            case "mediaSingle"  -> convertMediaSingle(node);
            case "media"        -> convertMedia(node);
            case "inlineCard"   -> convertInlineCard(node);
            case "blockCard"    -> convertBlockCard(node);
            case "mention"      -> "@" + node.path("attrs").path("text").asText("?");
            case "emoji"        -> node.path("attrs").path("text").asText(
                                       node.path("attrs").path("shortName").asText(""));
            case "status"       -> "`" + node.path("attrs").path("text").asText("") + "`";
            case "date"         -> node.path("attrs").path("timestamp").asText("");
            default             -> convertChildren(node, depth);
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Specific converters
    // ─────────────────────────────────────────────────────────────────────────

    private String convertChildren(JsonNode node, int depth) {
        JsonNode content = node.path("content");
        if (!content.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode child : content) sb.append(convertNode(child, depth));
        return sb.toString();
    }

    private String convertHeading(JsonNode node, int depth) {
        int level = node.path("attrs").path("level").asInt(1);
        String prefix = "#".repeat(Math.min(level, 6));
        return prefix + " " + convertChildren(node, depth).strip() + "\n\n";
    }

    private String convertText(JsonNode node) {
        String text = node.path("text").asText("");
        JsonNode marks = node.path("marks");
        if (!marks.isArray()) return text;

        for (JsonNode mark : marks) {
            String markType = mark.path("type").asText("");
            text = switch (markType) {
                case "strong"       -> "**" + text + "**";
                case "em"           -> "*" + text + "*";
                case "code"         -> "`" + text + "`";
                case "strike"       -> "~~" + text + "~~";
                case "underline"    -> text;   // no MD equivalent — keep plain
                case "subsup"       -> text;
                case "textColor"    -> text;
                case "link"         -> {
                    String href = mark.path("attrs").path("href").asText(text);
                    yield "[" + text + "](" + href + ")";
                }
                default -> text;
            };
        }
        return text;
    }

    private String convertList(JsonNode node, int depth, boolean ordered) {
        JsonNode content = node.path("content");
        if (!content.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (JsonNode item : content) {
            String bullet = ordered ? (idx++) + ". " : "- ";
            String indent = "  ".repeat(depth);
            String itemText = convertListItem(item, depth + 1).strip();
            sb.append(indent).append(bullet).append(itemText).append("\n");
        }
        return sb + "\n";
    }

    private String convertListItem(JsonNode node, int depth) {
        JsonNode content = node.path("content");
        if (!content.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode child : content) {
            String type = child.path("type").asText("");
            if (type.equals("bulletList") || type.equals("orderedList")) {
                // nested list — add extra indent
                sb.append("\n").append(convertList(child, depth, type.equals("orderedList")));
            } else {
                sb.append(convertNode(child, depth));
            }
        }
        return sb.toString();
    }

    private String convertCodeBlock(JsonNode node) {
        String lang = node.path("attrs").path("language").asText("");
        String code = collectText(node);
        return "```" + lang + "\n" + code + "\n```\n\n";
    }

    private String convertBlockquote(JsonNode node, int depth) {
        String inner = convertChildren(node, depth);
        List<String> lines = inner.lines().toList();
        String quoted = lines.stream()
            .map(l -> "> " + l)
            .collect(Collectors.joining("\n"));
        return quoted + "\n\n";
    }

    private String convertTable(JsonNode node, int depth) {
        JsonNode content = node.path("content");
        if (!content.isArray()) return "";

        List<List<String>> rows = new ArrayList<>();
        boolean hasHeader = false;

        for (JsonNode rowNode : content) {
            String rowType = rowNode.path("type").asText("");
            if (!rowType.equals("tableRow")) continue;
            List<String> cells = new ArrayList<>();
            JsonNode rowContent = rowNode.path("content");
            boolean isHeaderRow = false;
            for (JsonNode cell : rowContent) {
                String cellType = cell.path("type").asText("");
                if (cellType.equals("tableHeader")) isHeaderRow = true;
                String cellText = convertChildren(cell, depth).strip()
                    .replace("\n", " ").replace("|", "\\|");
                cells.add(cellText);
            }
            rows.add(cells);
            if (isHeaderRow && !hasHeader) hasHeader = true;
        }

        if (rows.isEmpty()) return "";

        // Determine column count
        int cols = rows.stream().mapToInt(List::size).max().orElse(1);

        StringBuilder sb = new StringBuilder();
        boolean separatorInserted = hasHeader; // separator after first row if header

        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            sb.append("| ");
            for (int c = 0; c < cols; c++) {
                sb.append(c < row.size() ? row.get(c) : "").append(" | ");
            }
            sb.append("\n");
            if (r == 0) {
                // always insert separator row after first row
                sb.append("| ");
                for (int c = 0; c < cols; c++) sb.append("--- | ");
                sb.append("\n");
            }
        }
        return sb + "\n";
    }

    private String convertMediaSingle(JsonNode node) {
        JsonNode content = node.path("content");
        if (content.isArray()) {
            for (JsonNode child : content) {
                if ("media".equals(child.path("type").asText(""))) {
                    return convertMedia(child);
                }
            }
        }
        return "";
    }

    private String convertMedia(JsonNode node) {
        JsonNode attrs = node.path("attrs");
        String id       = attrs.path("id").asText("");
        String mediaType = attrs.path("type").asText("file");
        String alt      = attrs.path("alt").asText(id);
        // Filename resolved later (cross-ref with attachment list by media ID)
        // Use id as placeholder filename; JiraMarkdownExporter renames on save
        String placeholder = id.isBlank() ? "attachment" : id;
        if ("image".equals(mediaType)) {
            return "![" + alt + "](./media/" + placeholder + ")\n\n";
        }
        return "[" + alt + "](./media/" + placeholder + ")\n\n";
    }

    private String convertInlineCard(JsonNode node) {
        String url = node.path("attrs").path("url").asText("");
        return "[" + url + "](" + url + ")";
    }

    private String convertBlockCard(JsonNode node) {
        String url = node.path("attrs").path("url").asText("");
        return "[" + url + "](" + url + ")\n\n";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Recursively collect all text values in a node tree (used for code blocks). */
    private String collectText(JsonNode node) {
        if (node.isTextual()) return node.asText();
        JsonNode content = node.path("content");
        if (!content.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode child : content) sb.append(collectText(child));
        return sb.toString();
    }
}

