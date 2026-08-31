package com.conload.markdown;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * Pre-processes Confluence storage format XHTML before markdown conversion.
 * Handles namespace quirks and normalizes the markup.
 */
public class HtmlCleanerService {

    /**
     * Parse Confluence storage format XHTML into a Jsoup Document.
     * Wraps in a root element and uses XML parser to preserve namespace tags.
     */
    public Document parseStorageFormat(String storageXhtml) {
        if (storageXhtml == null || storageXhtml.isBlank()) {
            return Jsoup.parse("<body></body>", "", Parser.xmlParser());
        }

        // Wrap in a body element and define common Confluence namespaces
        // Jsoup XML parser keeps tag names with colons intact (ac:macro, ri:attachment etc.)
        String wrapped = "<root xmlns:ac=\"http://confluence.atlassian.com/schema/atlassian\" "
            + "xmlns:ri=\"http://confluence.atlassian.com/schema/atlassian\" "
            + "xmlns:at=\"http://confluence.atlassian.com/schema/atlassian\">"
            + storageXhtml
            + "</root>";

        Document doc = Jsoup.parse(wrapped, "", Parser.xmlParser());
        doc.outputSettings().prettyPrint(false);
        return doc;
    }

    /**
     * Quick cleanup: remove empty paragraphs and whitespace-only text nodes.
     */
    public void cleanup(Document doc) {
        // Remove completely empty paragraphs (but not those with only whitespace that matter)
        for (Element p : doc.select("p")) {
            if (p.text().isBlank() && p.children().isEmpty()) {
                p.remove();
            }
        }
    }
}

