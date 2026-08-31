package com.conload.markdown;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts diagram source code from Confluence storage format macros.
 * RULE: never return null for a recognised diagram macro — always emit
 *       at least a placeholder so diagrams are never silently dropped.
 */
public class DiagramExtractor {

    public record DiagramResult(
        String markdownBlock,
        List<String> imageRefs
    ) {}

    public DiagramResult extractDiagram(Element macro) {
        String macroName = macro.attr("ac:name").toLowerCase();

        return switch (macroName) {
            case "mermaid"          -> extractMermaid(macro);
            case "plantuml"         -> extractPlantUml(macro);
            case "drawio", "draw.io" -> extractDrawIo(macro);
            case "gliffy"           -> extractGliffy(macro);
            case "bpmn"             -> extractBpmn(macro);
            default                 -> null;
        };
    }

    // -----------------------------------------------------------------------

    private DiagramResult extractMermaid(Element macro) {
        String body = tryGetBody(macro);
        if (body != null && !body.isBlank()) {
            return new DiagramResult("\n```mermaid\n" + body.strip() + "\n```\n", List.of());
        }
        // Never skip — emit a visible placeholder
        return new DiagramResult(
            "\n> **[Mermaid diagram]** *(source could not be extracted — check Confluence page)*\n",
            List.of());
    }

    private DiagramResult extractPlantUml(Element macro) {
        String body = tryGetBody(macro);
        if (body != null && !body.isBlank()) {
            return new DiagramResult("\n```plantuml\n" + body.strip() + "\n```\n", List.of());
        }
        return new DiagramResult(
            "\n> **[PlantUML diagram]** *(source could not be extracted — check Confluence page)*\n",
            List.of());
    }

    private DiagramResult extractDrawIo(Element macro) {
        String xmlBody = extractPlainTextBody(macro);
        StringBuilder md = new StringBuilder();
        List<String> images = new ArrayList<>();

        if (xmlBody != null && !xmlBody.isBlank()) {
            md.append("\n<!-- draw.io diagram source -->\n");
            md.append("```xml\n").append(xmlBody.strip()).append("\n```\n");
        }

        // Reference any PNG preview attachment (diagramName parameter)
        String diagramName = getMacroParam(macro, "diagramName");
        if (diagramName == null || diagramName.isBlank()) {
            diagramName = getMacroParam(macro, "name");
        }
        if (diagramName != null && !diagramName.isBlank()) {
            String imgFile = diagramName + ".png";
            md.append("\n![").append(diagramName).append("](./media/").append(imgFile).append(")\n");
            images.add(imgFile);
        }

        if (md.isEmpty()) {
            md.append("\n> **[Draw.io diagram]** — see `./media/` folder for exported image\n");
        }

        return new DiagramResult(md.toString(), images);
    }

    private DiagramResult extractGliffy(Element macro) {
        String name = getMacroParam(macro, "name");
        if (name == null || name.isBlank()) name = getMacroParam(macro, "filename");
        List<String> images = new ArrayList<>();
        StringBuilder md = new StringBuilder();

        if (name != null && !name.isBlank()) {
            String imgFile = name + ".png";
            md.append("\n![").append(name).append("](./media/").append(imgFile).append(")\n");
            images.add(imgFile);
        } else {
            md.append("\n> **[Gliffy diagram]** — see `./media/` folder for exported image\n");
        }

        return new DiagramResult(md.toString(), images);
    }

    private DiagramResult extractBpmn(Element macro) {
        String body = tryGetBody(macro);
        if (body != null && !body.isBlank()) {
            return new DiagramResult("\n```xml\n" + body.strip() + "\n```\n", List.of());
        }
        return new DiagramResult(
            "\n> **[BPMN diagram]** — see `./media/` folder for exported image\n",
            List.of());
    }

    // -----------------------------------------------------------------------
    // Helper: try every known extraction strategy in order
    // -----------------------------------------------------------------------

    private String tryGetBody(Element macro) {
        // 1. Prefer CDATA plain-text body (Mermaid, PlantUML, code macros)
        String body = extractPlainTextBody(macro);
        if (body != null && !body.isBlank()) return body;

        // 2. Rich-text body (rare for diagrams, handles some editors)
        body = extractRichTextBody(macro);
        if (body != null && !body.isBlank()) return body;

        // 3. Direct text content of the macro element itself
        body = macro.wholeOwnText();
        if (body != null && !body.isBlank()) return body;

        // 4. Walk every descendant and collect raw text
        StringBuilder sb = new StringBuilder();
        for (Node child : macro.childNodes()) {
            if (child instanceof TextNode tn) {
                String t = tn.getWholeText();
                if (t != null && !t.isBlank()) sb.append(t);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // -----------------------------------------------------------------------
    // Public helpers (used by MarkdownConverter)
    // -----------------------------------------------------------------------

    /**
     * Extract text from ac:plain-text-body (may be CDATA).
     */
    public String extractPlainTextBody(Element macro) {
        Element body = macro.selectFirst("ac|plain-text-body, ac\\:plain-text-body");
        if (body == null) {
            for (Element el : macro.getAllElements()) {
                if (el.tagName().contains("plain-text-body")) { body = el; break; }
            }
        }
        if (body == null) return null;

        String text = body.wholeOwnText();
        if (text == null || text.isBlank()) text = body.wholeText();
        return (text != null && !text.isBlank()) ? text : null;
    }

    /**
     * Extract text content from ac:rich-text-body, preserving whitespace.
     */
    public String extractRichTextBody(Element macro) {
        for (Element el : macro.getAllElements()) {
            if (el.tagName().contains("rich-text-body")) {
                String t = el.wholeText();
                if (t != null && !t.isBlank()) return t;
            }
        }
        return null;
    }

    /**
     * Get a named parameter from an ac:structured-macro.
     */
    public String getMacroParam(Element macro, String paramName) {
        for (Element el : macro.getAllElements()) {
            if (el.tagName().contains("parameter")) {
                String name = el.attr("ac:name");
                if (name.isBlank()) name = el.attr("name");
                if (paramName.equalsIgnoreCase(name)) {
                    return el.text().strip();
                }
            }
        }
        return null;
    }
}
