package com.conload.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight Markdown-to-HTML converter for the in-app Markdown viewer popup.
 * Handles the common subset of GitHub-flavoured Markdown produced by the
 * session export / session_info writers:
 * <ul>
 *   <li>ATX headings ({@code #} … {@code ######})</li>
 *   <li>Fenced code blocks ({@code ```})</li>
 *   <li>Inline code ({@code `code`})</li>
 *   <li>Tables (pipe syntax)</li>
 *   <li>Bold ({@code **} / {@code __}), italic ({@code *} / {@code _}),
 *       strikethrough ({@code ~~})</li>
 *   <li>Links ({@code [text](url)}) and images ({@code ![alt](src)})</li>
 *   <li>Blockquotes ({@code >})</li>
 *   <li>Unordered ({@code -} / {@code *}) and ordered ({@code 1.}) lists</li>
 *   <li>Horizontal rules ({@code ---})</li>
 *   <li>Paragraphs and line breaks</li>
 * </ul>
 * <p>
 * This is intentionally dependency-free (no flexmark / commonmark) to keep the
 * Maven build unchanged and the app fully offline. It is geared at rendering
 * the well-formed markdown that conload itself produces — not an adversarial
 * markdown parser.
 */
public final class MarkdownToHtmlConverter {

    private MarkdownToHtmlConverter() {}

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern FENCE = Pattern.compile("^```(.*)$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.*)\\|$");
    private static final Pattern HRULE = Pattern.compile("^(?:-{3,}|\\*{3,}|_{3,})$");
    private static final Pattern BLOCKQUOTE = Pattern.compile("^>\\s?(.*)$");
    private static final Pattern ULIST = Pattern.compile("^(?:[-*+]|\\u2022)\\s+(.*)$");
    private static final Pattern OLIST = Pattern.compile("^(\\d+)\\.\\s+(.*)$");

    public static String convert(String md) {
        if (md == null) return "";
        String[] lines = md.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            Matcher fence = FENCE.matcher(line);
            Matcher heading = HEADING.matcher(line);
            if (fence.matches()) i = renderFence(lines, i, out, fence);
            else if (heading.matches()) i = renderHeading(i, out, heading);
            else if (HRULE.matcher(line).matches()) { out.append("<hr/>\n"); i++; }
            else if (isTableStart(lines, i)) i = renderTableBlock(lines, i, out);
            else if (BLOCKQUOTE.matcher(line).matches()) i = renderBlockquote(lines, i, out);
            else if (ULIST.matcher(line).matches()) i = renderList(lines, i, out, false);
            else if (OLIST.matcher(line).matches()) i = renderList(lines, i, out, true);
            else if (line.trim().isEmpty()) i++;
            else i = renderParagraph(lines, i, out);
        }
        return out.toString();
    }

    private static int renderFence(String[] lines, int i, StringBuilder out, Matcher fm) {
        String lang = fm.group(1).trim();
        StringBuilder code = new StringBuilder();
        i++;
        while (i < lines.length && !FENCE.matcher(lines[i]).matches()) {
            code.append(escapeHtml(lines[i])).append("\n");
            i++;
        }
        i++;
        out.append("<pre><code");
        if (!lang.isBlank()) out.append(" class=\"language-").append(escapeAttr(lang)).append("\"");
        out.append(">").append(code).append("</code></pre>\n");
        return i;
    }

    private static int renderHeading(int i, StringBuilder out, Matcher hm) {
        int level = hm.group(1).length();
        out.append("<h").append(level).append(">").append(inline(hm.group(2).trim()))
           .append("</h").append(level).append(">\n");
        return i + 1;
    }

    private static boolean isTableStart(String[] lines, int i) {
        return TABLE_ROW.matcher(lines[i]).matches() && i + 1 < lines.length
            && isTableSeparator(lines[i + 1]);
    }

    private static int renderTableBlock(String[] lines, int i, StringBuilder out) {
        List<List<String>> rows = new ArrayList<>();
        while (i < lines.length && TABLE_ROW.matcher(lines[i]).matches()) {
            rows.add(splitTableRow(lines[i++]));
        }
        if (rows.size() >= 2) out.append(renderTable(rows));
        return i;
    }

    private static int renderBlockquote(String[] lines, int i, StringBuilder out) {
        StringBuilder q = new StringBuilder();
        while (i < lines.length) {
            Matcher qm = BLOCKQUOTE.matcher(lines[i]);
            if (!qm.matches()) break;
            q.append(qm.group(1)).append("\n");
            i++;
        }
        out.append("<blockquote>").append(inline(q.toString().trim())).append("</blockquote>\n");
        return i;
    }

    private static int renderList(String[] lines, int i, StringBuilder out, boolean ordered) {
        out.append(ordered ? "<ol>\n" : "<ul>\n");
        Pattern pattern = ordered ? OLIST : ULIST;
        while (i < lines.length) {
            Matcher item = pattern.matcher(lines[i]);
            if (!item.matches()) {
                if (lines[i].trim().isEmpty()) i++;
                break;
            }
            out.append("<li>").append(inline(item.group(ordered ? 2 : 1).trim())).append("</li>\n");
            i++;
        }
        out.append(ordered ? "</ol>\n" : "</ul>\n");
        return i;
    }

    private static int renderParagraph(String[] lines, int i, StringBuilder out) {
        StringBuilder para = new StringBuilder();
        while (i < lines.length && isParagraphLine(lines, i)) {
            if (!para.isEmpty()) para.append("<br/>\n");
            para.append(lines[i++]);
        }
        out.append("<p>").append(inline(para.toString())).append("</p>\n");
        return i;
    }

    private static boolean isParagraphLine(String[] lines, int i) {
        String line = lines[i];
        return !line.trim().isEmpty() && !HEADING.matcher(line).matches()
            && !FENCE.matcher(line).matches() && !BLOCKQUOTE.matcher(line).matches()
            && !ULIST.matcher(line).matches() && !OLIST.matcher(line).matches()
            && !HRULE.matcher(line).matches() && !isTableStart(lines, i);
    }

    private static boolean isTableSeparator(String line) {
        Matcher m = TABLE_ROW.matcher(line);
        if (!m.matches()) return false;
        String cells = m.group(1);
        // each cell must be only dashes, colons, spaces
        return cells.matches("[:\\s\\-| ]+");
    }

    private static List<String> splitTableRow(String line) {
        Matcher m = TABLE_ROW.matcher(line);
        if (!m.matches()) return List.of();
        String[] parts = m.group(1).split("\\|", -1);
        List<String> cells = new ArrayList<>(parts.length);
        for (String p : parts) cells.add(p.trim());
        return cells;
    }

    private static String renderTable(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder("<table>\n");
        // Header
        sb.append("<thead><tr>");
        for (String h : rows.get(0)) sb.append("<th>").append(inline(h)).append("</th>");
        sb.append("</tr></thead>\n<tbody>\n");
        // Body (skip separator row at index 1)
        for (int r = 2; r < rows.size(); r++) {
            sb.append("<tr>");
            for (String c : rows.get(r)) sb.append("<td>").append(inline(c)).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</tbody>\n</table>\n");
        return sb.toString();
    }

    /** Process inline formatting: bold, italic, strikethrough, code, links, images. */
    private static String inline(String text) {
        if (text == null) return "";
        String s = escapeHtml(text);
        // Images: ![alt](src)
        s = replaceAll(s, Pattern.compile("!\\[(.*?)]\\((.*?)\\)"),
            m -> "<img alt=\"" + escapeAttr(m.group(1)) + "\" src=\"" + escapeAttr(m.group(2)) + "\"/>");
        // Links: [text](url)
        s = replaceAll(s, Pattern.compile("\\[(.*?)]\\((.*?)\\)"),
            m -> "<a href=\"" + escapeAttr(m.group(2)) + "\">" + m.group(1) + "</a>");
        // Bold: **text** or __text__
        s = replaceAll(s, Pattern.compile("\\*\\*(.+?)\\*\\*"), m -> "<strong>" + m.group(1) + "</strong>");
        s = replaceAll(s, Pattern.compile("__(.+?)__"), m -> "<strong>" + m.group(1) + "</strong>");
        // Strikethrough: ~~text~~
        s = replaceAll(s, Pattern.compile("~~(.+?)~~"), m -> "<del>" + m.group(1) + "</del>");
        // Inline code: `code` — must run before italic so * inside code is preserved
        s = replaceAll(s, Pattern.compile("`([^`]+)`"), m -> "<code>" + m.group(1) + "</code>");
        // Italic: *text* or _text_
        s = replaceAll(s, Pattern.compile("\\*(.+?)\\*"), m -> "<em>" + m.group(1) + "</em>");
        s = replaceAll(s, Pattern.compile("(?<=\\s|^)_(.+?)_(?=\\s|$)"), m -> "<em>" + m.group(1) + "</em>");
        return s;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        if (s == null) return "";
        return escapeHtml(s).replace("\"", "&quot;");
    }

    private static String replaceAll(String input, Pattern p,
                                     java.util.function.Function<Matcher, String> fn) {
        Matcher m = p.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(fn.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
