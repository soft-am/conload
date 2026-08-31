package com.conload.markdown;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Confluence storage format XHTML to clean Markdown.
 *
 * Handles:
 *   - Standard HTML: h1-h6, p, ul, ol, li, table, pre, code, strong, em, a, img, blockquote, hr
 *   - Confluence macros: ac:structured-macro (code, panel, expand, note, warning, info, tip,
 *                         mermaid, plantuml, drawio, gliffy, toc, status)
 *   - Confluence images: ac:image with ri:attachment or ri:url
 *   - Confluence links: ac:link with ri:page
 *   - Layout containers: ac:layout, ac:layout-section, ac:layout-cell
 *   - ac:task-list / ac:task
 *
 * Output is optimized for GitHub Copilot / RAG / AI embeddings:
 *   - Clean headings with blank lines
 *   - Fenced code blocks with language hints
 *   - Relative image references into media/ folder
 *   - Mermaid/PlantUML preserved as code blocks
 *   - Expandable sections as blockquotes with title
 */
public class MarkdownConverter {

    /**
     * Matches a fenced code block: ```lang\n...content...\n```
     * Compiled with DOTALL so '.' matches newlines inside the block.
     */
    private static final Pattern FENCED_CODE = Pattern.compile(
        "```[^\\n]*\\n.*?\\n```", Pattern.DOTALL);

    private final HtmlCleanerService cleaner;
    private final DiagramExtractor diagramExtractor;

    /** Image filenames to be downloaded (populated during conversion). */
    private final List<String> requiredImages = new ArrayList<>();

    public MarkdownConverter() {
        this.cleaner = new HtmlCleanerService();
        this.diagramExtractor = new DiagramExtractor();
    }

    /**
     * Convert Confluence storage format XHTML to Markdown.
     *
     * @param storageXhtml  raw storage format body
     * @param pageTitle     used as H1 heading at the top
     * @param pageUrl       optional source URL for footer metadata
     * @return ConversionResult with markdown text and list of image filenames to download
     */
    public ConversionResult convert(String storageXhtml, String pageTitle, String pageUrl) {
        requiredImages.clear();

        Document doc = cleaner.parseStorageFormat(storageXhtml);
        cleaner.cleanup(doc);

        Element root = doc.selectFirst("root");
        if (root == null) root = doc.body() != null ? doc.body() : doc.root();

        StringBuilder sb = new StringBuilder();

        sb.append("# ").append(pageTitle != null ? pageTitle : "Untitled").append("\n\n");
        if (pageUrl != null && !pageUrl.isBlank()) {
            sb.append("> **Source:** [").append(pageUrl).append("](")
              .append(pageUrl).append(")\n\n");
        }

        convertChildren(root, sb, 0);

        // Normalise excessive blank lines ONLY outside fenced code blocks,
        // so that blank lines inside code are never collapsed.
        String markdown = normalizeMarkdown(sb.toString())
            .stripTrailing()
            + "\n";

        return new ConversionResult(markdown, new ArrayList<>(requiredImages));
    }

    // =========================================================================
    // Core recursive converter
    // =========================================================================

    private void convertChildren(Element parent, StringBuilder sb, int listDepth) {
        for (Node node : parent.childNodes()) {
            if (node instanceof TextNode text) {
                // Use text() for normal text nodes (normalises whitespace for prose).
                // CDataNode extends TextNode but its content must NOT be normalised –
                // it contains raw preformatted data (e.g. inline code, raw HTML).
                // CDataNode.text() unfortunately DOES normalise whitespace, collapsing
                // newlines to spaces.  Detect it by class name and use getWholeText().
                String t;
                if (text.getClass().getSimpleName().equals("CDataNode")) {
                    t = text.getWholeText();
                } else {
                    t = text.text();
                }
                if (!t.isBlank()) sb.append(t);
            } else if (node instanceof Element el) {
                convertElement(el, sb, listDepth);
            }
        }
    }

    private void convertElement(Element el, StringBuilder sb, int listDepth) {
        String tag = el.tagName().toLowerCase();

        switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> convertHeading(el, sb);
            case "p" -> convertParagraph(el, sb, listDepth);
            case "strong", "b", "em", "i", "code", "s", "del", "strike",
                 "sup", "sub", "br", "hr" -> convertInlineElement(el, sb, listDepth);
            case "a", "img" -> convertLinkOrImage(el, sb);
            case "ul" -> convertList(el, sb, listDepth, false);
            case "ol" -> convertList(el, sb, listDepth, true);
            case "li" -> convertStandaloneListItem(el, sb, listDepth);
            case "table" -> convertTable(el, sb);
            case "pre" -> convertPreformatted(el, sb);
            case "blockquote" -> convertBlockquote(el, sb, listDepth);
            case "ac:layout", "ac:layout-section", "ac:layout-cell",
                 "div", "span", "section", "article", "aside", "header", "footer",
                 "nav", "main", "body", "root" -> convertChildren(el, sb, listDepth);
            case "ac:image" -> convertAcImage(el, sb);
            case "ac:link" -> convertAcLink(el, sb);
            case "ac:structured-macro" -> convertMacro(el, sb, listDepth);
            case "ac:task-list" -> convertTaskList(el, sb);
            default -> appendUnknownElement(el, sb);
        }
    }

    private void convertHeading(Element el, StringBuilder sb) {
        block(sb, "#".repeat(Integer.parseInt(el.tagName().substring(1))) + " " + el.text());
    }

    private void convertParagraph(Element el, StringBuilder sb, int listDepth) {
        StringBuilder inner = new StringBuilder();
        convertChildren(el, inner, listDepth);
        String text = inner.toString().strip();
        if (!text.isBlank()) block(sb, text);
    }

    private void convertInlineElement(Element el, StringBuilder sb, int listDepth) {
        switch (el.tagName().toLowerCase()) {
            case "strong", "b" -> { sb.append("**"); convertChildren(el, sb, listDepth); sb.append("**"); }
            case "em", "i" -> { sb.append("_"); convertChildren(el, sb, listDepth); sb.append("_"); }
            case "code" -> appendInlineCode(el, sb);
            case "s", "del", "strike" -> sb.append("~~").append(el.text()).append("~~");
            case "sup" -> sb.append("<sup>").append(el.text()).append("</sup>");
            case "sub" -> sb.append("<sub>").append(el.text()).append("</sub>");
            case "br" -> sb.append("  \n");
            case "hr" -> block(sb, "---");
            default -> { }
        }
    }

    private void appendInlineCode(Element el, StringBuilder sb) {
        String codeText = el.wholeOwnText();
        if (codeText == null || codeText.isEmpty()) codeText = el.wholeText();
        if (codeText.contains("\n")) block(sb, "```\n" + codeText.strip() + "\n```");
        else sb.append("`").append(codeText).append("`");
    }

    private void convertLinkOrImage(Element el, StringBuilder sb) {
        if (el.tagName().equalsIgnoreCase("a")) {
            String href = el.attr("href");
            String text = el.text();
            if (href.isBlank()) sb.append(text);
            else sb.append("[").append(text.isBlank() ? href : text).append("](").append(href).append(")");
        } else {
            String alt = el.attr("alt");
            if (alt.isBlank()) alt = "image";
            sb.append("![").append(alt).append("](").append(el.attr("src")).append(")");
        }
    }

    private void convertStandaloneListItem(Element el, StringBuilder sb, int listDepth) {
        sb.append("- ");
        convertChildrenInline(el, sb, listDepth);
        sb.append("\n");
    }

    private void convertPreformatted(Element el, StringBuilder sb) {
        Element codeEl = el.selectFirst("code");
        String lang = "";
        if (codeEl != null) {
            String cls = codeEl.attr("class");
            if (cls.contains("language-")) lang = cls.substring(cls.indexOf("language-") + 9).split("\\s")[0];
        }
        Element target = codeEl != null ? codeEl : el;
        String code = target.wholeText();
        if (code == null || code.isEmpty()) code = target.wholeOwnText();
        block(sb, "```" + lang + "\n" + code.strip() + "\n```");
    }

    private void convertBlockquote(Element el, StringBuilder sb, int listDepth) {
        StringBuilder inner = new StringBuilder();
        convertChildren(el, inner, listDepth);
        appendQuotedBody(sb, "", inner.toString(), false, false);
    }

    private void convertTaskList(Element el, StringBuilder sb) {
        sb.append("\n");
        for (Element task : el.select("ac|task, ac\\:task")) {
            Element status = task.selectFirst("ac|task-status, ac\\:task-status");
            Element body = task.selectFirst("ac|task-body, ac\\:task-body");
            boolean done = status != null && "COMPLETE".equalsIgnoreCase(status.text().strip());
            sb.append("- [").append(done ? "x" : " ").append("] ");
            if (body != null) {
                StringBuilder bodyText = new StringBuilder();
                convertChildren(body, bodyText, 0);
                sb.append(bodyText.toString().strip());
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private void appendUnknownElement(Element el, StringBuilder sb) {
        String text = el.text().strip();
        if (!text.isBlank()) sb.append(text).append(" ");
    }

    // =========================================================================
    // Confluence Macro handling
    // =========================================================================

    private void convertMacro(Element macro, StringBuilder sb, int listDepth) {
        // ac:name attribute may be qualified or plain depending on Jsoup version
        String name = macro.attr("ac:name");
        if (name.isBlank()) name = macro.attr("name");
        name = name.toLowerCase();

        // First check if it's a diagram — extractDiagram now ALWAYS returns a result
        // for recognised diagram macros (never returns null)
        DiagramExtractor.DiagramResult diagram = diagramExtractor.extractDiagram(macro);
        if (diagram != null) {
            sb.append(diagram.markdownBlock());
            requiredImages.addAll(diagram.imageRefs());
            return;
        }

        switch (name) {
            case "code" -> convertCodeMacro(macro, sb);
            case "panel" -> convertPanelMacro(macro, sb, listDepth);
            case "note", "info", "tip" -> convertCalloutMacro(macro, sb, listDepth, name);
            case "warning" -> appendQuotedBody(sb, "⚠️ **WARNING**\n",
                getRichTextBodyAsMarkdown(macro, listDepth), true, false);
            case "expand" -> convertExpandMacro(macro, sb, listDepth);
            case "toc" -> block(sb, "<!-- Table of Contents -->");
            case "status" -> appendStatusMacro(macro, sb);
            case "anchor" -> appendAnchorMacro(macro, sb);
            case "jira" -> appendJiraMacro(macro, sb);
            case "noformat" -> convertNoformatMacro(macro, sb);
            default -> convertUnknownMacro(macro, sb, listDepth);
        }
    }

    private void convertCodeMacro(Element macro, StringBuilder sb) {
        String lang = diagramExtractor.getMacroParam(macro, "language");
        if (lang == null) lang = diagramExtractor.getMacroParam(macro, "lang");
        if (lang == null) lang = "";
        String body = diagramExtractor.extractPlainTextBody(macro);
        if (body == null) body = "";
        block(sb, "```" + lang.toLowerCase() + "\n" + body.strip() + "\n```");
    }

    private void convertPanelMacro(Element macro, StringBuilder sb, int listDepth) {
        String title = diagramExtractor.getMacroParam(macro, "title");
        if (title == null) title = "Panel";
        appendQuotedBody(sb, "**" + title + "**\n",
            getRichTextBodyAsMarkdown(macro, listDepth), false, true);
    }

    private void convertCalloutMacro(Element macro, StringBuilder sb, int listDepth, String name) {
        String icon = switch (name) { case "note" -> "📝"; case "tip" -> "💡"; default -> "ℹ️"; };
        appendQuotedBody(sb, icon + " **" + name.toUpperCase() + "**\n",
            getRichTextBodyAsMarkdown(macro, listDepth), true, false);
    }

    private void convertExpandMacro(Element macro, StringBuilder sb, int listDepth) {
        String title = diagramExtractor.getMacroParam(macro, "title");
        if (title == null) title = "Expand";
        String body = getRichTextBodyAsMarkdown(macro, listDepth);
        sb.append("\n<details>\n<summary>").append(title).append("</summary>\n\n");
        sb.append(body.strip()).append("\n\n</details>\n\n");
    }

    private void appendStatusMacro(Element macro, StringBuilder sb) {
        String title = diagramExtractor.getMacroParam(macro, "title");
        if (title != null) sb.append("`").append(title).append("`");
    }

    private void appendAnchorMacro(Element macro, StringBuilder sb) {
        String name = diagramExtractor.getMacroParam(macro, "");
        if (name == null) name = macro.attr("ac:parameter");
        if (name != null) sb.append("<a name=\"").append(name).append("\"></a>");
    }

    private void appendJiraMacro(Element macro, StringBuilder sb) {
        String key = diagramExtractor.getMacroParam(macro, "key");
        if (key != null) sb.append("`JIRA: ").append(key).append("`");
    }

    private void convertNoformatMacro(Element macro, StringBuilder sb) {
        String body = diagramExtractor.extractPlainTextBody(macro);
        if (body != null && !body.isBlank()) block(sb, "```\n" + body.strip() + "\n```");
    }

    private void convertUnknownMacro(Element macro, StringBuilder sb, int listDepth) {
        String richBody = getRichTextBodyAsMarkdown(macro, listDepth);
        if (!richBody.isBlank()) {
            sb.append(richBody);
            return;
        }
        String plainBody = diagramExtractor.extractPlainTextBody(macro);
        if (plainBody != null && !plainBody.isBlank()) {
            block(sb, "```\n" + plainBody.strip() + "\n```");
            return;
        }
        String rawText = macro.wholeText();
        if (rawText != null && !rawText.isBlank()) block(sb, rawText.strip());
    }

    // =========================================================================
    // Confluence image: ac:image
    // =========================================================================

    private void convertAcImage(Element acImage, StringBuilder sb) {
        // Check for ri:attachment (page attachment)
        Element riAttachment = findDescendant(acImage, "ri:attachment");
        if (riAttachment != null) {
            String filename = riAttachment.attr("ri:filename");
            if (filename != null && !filename.isBlank()) {
                String safeFilename = sanitizeImageFilename(filename);
                sb.append("![").append(safeFilename).append("](./media/").append(safeFilename).append(")");
                requiredImages.add(filename);
                return;
            }
        }

        // Check for ri:url (external image)
        Element riUrl = findDescendant(acImage, "ri:url");
        if (riUrl != null) {
            String url = riUrl.attr("ri:value");
            if (url != null && !url.isBlank()) {
                sb.append("![image](").append(url).append(")");
                return;
            }
        }

        sb.append("![image]()");
    }

    // =========================================================================
    // Confluence link: ac:link
    // =========================================================================

    private void convertAcLink(Element acLink, StringBuilder sb) {
        // Try to get link text from ac:link-body or ac:plain-text-link-body
        String linkText = null;
        for (var child : acLink.children()) {
            String tag = child.tagName();
            if (tag.contains("link-body") || tag.contains("plain-text-link")) {
                linkText = child.text().strip();
                break;
            }
        }

        // Try ri:page for target title
        Element riPage = findDescendant(acLink, "ri:page");
        String targetTitle = riPage != null ? riPage.attr("ri:content-title") : null;

        if (linkText == null || linkText.isBlank()) {
            linkText = targetTitle != null ? targetTitle : "link";
        }

        if (targetTitle != null && !targetTitle.isBlank()) {
            // Internal Confluence link – just render as bold reference
            sb.append("[").append(linkText).append("](#)");
        } else {
            sb.append(linkText);
        }
    }

    // =========================================================================
    // Table conversion
    // =========================================================================

    private void convertTable(Element table, StringBuilder sb) {
        sb.append("\n");
        List<Element> rows = table.select("tr");
        if (rows.isEmpty()) return;

        boolean headerWritten = false;
        for (int i = 0; i < rows.size(); i++) {
            Element row = rows.get(i);
            List<Element> cells = row.select("th, td");
            if (cells.isEmpty()) continue;

            sb.append("| ");
            for (Element cell : cells) {
                StringBuilder cellContent = new StringBuilder();
                convertChildrenInline(cell, cellContent, 0);
                // Code blocks inside a Markdown table cell cannot use fenced ``` syntax
                // (it breaks the table).  Convert them to HTML <pre> tags instead,
                // which every major renderer (GitHub, GitLab, VS Code) honours inside tables.
                String text = flattenCellContent(cellContent.toString().strip());
                sb.append(text).append(" | ");
            }
            sb.append("\n");

            if (!headerWritten && (row.selectFirst("th") != null || i == 0)) {
                sb.append("| ");
                for (int c = 0; c < cells.size(); c++) {
                    sb.append("--- | ");
                }
                sb.append("\n");
                headerWritten = true;
            }
        }
        sb.append("\n");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Collapse 4+ consecutive blank lines to at most 3, but ONLY outside fenced
     * code blocks.  Lines inside ``` ... ``` are kept verbatim so that alignment
     * spaces and blank lines within code are not destroyed.
     */
    private String normalizeMarkdown(String raw) {
        StringBuilder result = new StringBuilder();
        Matcher m = FENCED_CODE.matcher(raw);
        int last = 0;
        while (m.find()) {
            // Normalize only the prose section before this code block
            String prose = raw.substring(last, m.start()).replaceAll("\n{4,}", "\n\n\n");
            result.append(prose);
            // Keep the code block completely untouched
            result.append(m.group());
            last = m.end();
        }
        // Normalize any trailing prose after the last code block
        result.append(raw.substring(last).replaceAll("\n{4,}", "\n\n\n"));
        return result.toString();
    }

    private void convertList(Element list, StringBuilder sb, int depth, boolean ordered) {
        sb.append("\n");
        int number = 1;
        for (Element item : list.children()) {
            if (!item.tagName().equalsIgnoreCase("li")) continue;
            sb.append("  ".repeat(depth));
            if (ordered) sb.append(number++).append(". ");
            else sb.append("- ");
            convertChildrenInline(item, sb, depth + 1);
            sb.append("\n");
        }
        sb.append("\n");
    }

    private void appendQuotedBody(StringBuilder sb, String header, String body,
                                  boolean skipBlankLines, boolean blankAfterHeader) {
        sb.append("\n> ").append(header);
        if (header.endsWith("\n") && blankAfterHeader) sb.append("> \n");
        else sb.append("\n");
        for (String line : body.split("\n")) {
            if (!skipBlankLines || !line.isBlank()) sb.append("> ").append(line).append("\n");
        }
        sb.append("\n");
    }

    /** Convert children into inline context (for table cells, list items). */
    private void convertChildrenInline(Element parent, StringBuilder sb, int depth) {
        for (Node node : parent.childNodes()) {
            if (node instanceof TextNode text) {
                sb.append(text.text());
            } else if (node instanceof Element el) {
                String tag = el.tagName().toLowerCase();
                if (tag.equals("ul") || tag.equals("ol")) {
                    sb.append("\n");
                    convertElement(el, sb, depth);
                } else {
                    convertElement(el, sb, depth);
                }
            }
        }
    }

    /** Get the rich-text-body of a macro converted to markdown. */
    private String getRichTextBodyAsMarkdown(Element macro, int listDepth) {
        for (Element el : macro.getAllElements()) {
            if (el.tagName().contains("rich-text-body")) {
                StringBuilder inner = new StringBuilder();
                convertChildren(el, inner, listDepth);
                return inner.toString();
            }
        }
        return "";
    }

    /** Append a block-level element (preceded and followed by blank line). */
    private void block(StringBuilder sb, String content) {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append("\n");
        sb.append("\n").append(content).append("\n");
    }

    /** Find first descendant matching tag (handles colon in tag names). */
    private Element findDescendant(Element parent, String tagName) {
        for (Element el : parent.getAllElements()) {
            if (el.tagName().equalsIgnoreCase(tagName)) return el;
        }
        return null;
    }

    private String sanitizeImageFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    /**
     * Flatten cell content for use inside a Markdown table row.
     *
     * - Ordinary text: pipes escaped, newlines → single space (standard table behaviour).
     * - Fenced code blocks: converted to HTML {@code <pre>} tags with {@code <br>} line
     *   endings so that multi-line code and alignment spaces are rendered correctly
     *   inside the table cell.
     */
    private String flattenCellContent(String raw) {
        if (raw == null || raw.isEmpty()) return "";

        if (!raw.contains("```")) {
            // No code block – normal prose flattening
            return raw.replace("|", "\\|").replace("\n", " ");
        }

        // Has at least one fenced code block: process segment by segment
        StringBuilder result = new StringBuilder();
        Matcher m = FENCED_CODE.matcher(raw);
        int last = 0;
        while (m.find()) {
            // Prose before this code block
            String before = raw.substring(last, m.start())
                .replace("|", "\\|").replace("\n", " ").strip();
            if (!before.isEmpty()) {
                result.append(before).append(" ");
            }

            // Extract code body (everything between the opening ``` line and closing ``")
            String matched = m.group();
            int firstNl  = matched.indexOf('\n');
            int lastNl   = matched.lastIndexOf('\n');
            String codeBody = (firstNl >= 0 && lastNl > firstNl)
                ? matched.substring(firstNl + 1, lastNl)
                : "";

            // HTML-escape the code body and join lines with <br>
            String escaped = codeBody
                .replace("&",  "&amp;")
                .replace("<",  "&lt;")
                .replace(">",  "&gt;")
                .replace("|",  "&#124;");
            result.append("<pre>").append(escaped.replace("\n", "<br>")).append("</pre>");
            last = m.end();
        }

        // Remaining prose after last code block
        String after = raw.substring(last).replace("|", "\\|").replace("\n", " ").strip();
        if (!after.isEmpty()) {
            if (result.length() > 0) result.append(" ");
            result.append(after);
        }
        return result.toString();
    }

    // =========================================================================
    // Result DTO
    // =========================================================================

    public record ConversionResult(String markdown, List<String> requiredImageFilenames) {}
}
