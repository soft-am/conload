package com.conload.confluence;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Confluence Cloud URLs to extract base URL and page ID.
 *
 * Supported URL formats
 * ─────────────────────
 *  1. Standard page URL (page ID in path):
 *       https://yourcompany.atlassian.net/wiki/spaces/SPACE/pages/379706390/Title
 *
 *  2. Space overview with homepageId query parameter:
 *       https://yourcompany.atlassian.net/wiki/spaces/SPACE/overview?homepageId=379682826
 *
 *  3. Short/canonical page URL (Confluence Cloud):
 *       https://yourcompany.atlassian.net/wiki/x/ABCDEF   (base-36 encoded — NOT supported, needs API)
 *
 *  4. Bare base URL (no page):
 *       https://yourcompany.atlassian.net
 *       → extractPageId() returns null; caller must handle (e.g. show error or pick space)
 */
public class ConfluenceUrlParser {

    // /pages/{numericId}/…
    private static final Pattern PAGE_ID_IN_PATH =
        Pattern.compile("(https://[^/]+)/wiki/spaces/[^/]+/pages/(\\d+)");

    // ?homepageId={numericId}  or  &homepageId={numericId}
    private static final Pattern HOMEPAGE_QUERY =
        Pattern.compile("[?&]homepageId=(\\d+)");

    // ?pageId={numericId}  (used by some Confluence views)
    private static final Pattern PAGE_ID_QUERY =
        Pattern.compile("[?&]pageId=(\\d+)");

    // Any Confluence host — captures scheme+host
    private static final Pattern BASE_PATTERN =
        Pattern.compile("(https?://[^/?#]+)");

    // -------------------------------------------------------------------------

    /**
     * Extract the numeric page ID from any supported Confluence URL.
     * Returns {@code null} if no page ID can be determined.
     */
    public String extractPageId(String url) {
        if (url == null || url.isBlank()) return null;
        String u = url.trim();

        // 1. /pages/{id}/
        Matcher m = PAGE_ID_IN_PATH.matcher(u);
        if (m.find()) return m.group(2);

        // 2. ?homepageId={id}
        m = HOMEPAGE_QUERY.matcher(u);
        if (m.find()) return m.group(1);

        // 3. ?pageId={id}
        m = PAGE_ID_QUERY.matcher(u);
        if (m.find()) return m.group(1);

        return null;
    }

    /**
     * Extract the scheme+host base URL from any Confluence URL.
     * E.g. "https://yourcompany.atlassian.net"
     */
    public String extractBaseUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String u = url.trim();

        // Prefer /wiki/ boundary
        int wikiIdx = u.indexOf("/wiki/");
        if (wikiIdx > 0) return u.substring(0, wikiIdx);

        // Fall back to scheme+host
        Matcher m = BASE_PATTERN.matcher(u);
        if (m.find()) return m.group(1);

        return null;
    }

    /** True when the URL contains a parseable numeric page ID. */
    public boolean hasPageId(String url) {
        return extractPageId(url) != null;
    }
}

