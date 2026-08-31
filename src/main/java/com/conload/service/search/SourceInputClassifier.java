package com.conload.service.search;

import com.conload.confluence.ConfluenceUrlParser;
import com.conload.jira.JiraUrlParser;

/**
 * Framework-neutral classification of a single user-entered Confluence or Jira
 * input as either a URL (page-tree / issue) or a keyword/key. Used by both the
 * download-context search pipeline ({@code ContentSearchService}) and the
 * "Prepare to Refinement" workflow, so the URL-vs-keyword decision lives in one
 * place — "the code compares it and chooses the proper way".
 *
 * <h2>Confluence</h2>
 * <ul>
 *   <li><b>TREE_URL</b> — input contains a parseable Confluence page ID
 *       ({@code /pages/{id}}, {@code ?pageId=}, {@code ?homepageId=}).
 *       The page tree below that page is downloaded.</li>
 *   <li><b>KEYWORD</b> — anything else; a CQL text search is executed.</li>
 * </ul>
 *
 * <h2>Jira</h2>
 * <ul>
 *   <li><b>ISSUE_URL</b> — an {@code http(s)://} URL with an extractable issue
 *       key; the issue (and its linked issues) are fetched directly.</li>
 *   <li><b>KEY</b> — a bare issue key matching {@code [A-Z][A-Z0-9_]+-\\d+};
 *       fetched directly against the configured Atlassian base URL.</li>
 *   <li><b>KEYWORD</b> — anything else; a JQL {@code text~"..."} search runs.</li>
 * </ul>
 */
public final class SourceInputClassifier {

    private static final java.util.regex.Pattern JIRA_KEY =
            java.util.regex.Pattern.compile("[A-Z][A-Z0-9_]+-\\d+");

    private final ConfluenceUrlParser confluenceParser = new ConfluenceUrlParser();
    private final JiraUrlParser jiraParser = new JiraUrlParser();

    /** Classified Confluence input. */
    public record ConfluenceInput(Kind kind, String value, String baseUrl, String pageId) {
        public enum Kind { TREE_URL, KEYWORD }
        public boolean isTreeUrl() { return kind == Kind.TREE_URL; }
    }

    /** Classified Jira input. {@code key} is populated for ISSUE_URL and KEY. */
    public record JiraInput(Kind kind, String value, String baseUrl, String key) {
        public enum Kind { ISSUE_URL, KEY, KEYWORD }
        public boolean isDirectFetch() { return kind == Kind.ISSUE_URL || kind == Kind.KEY; }
    }

    /**
     * Classify a Confluence input. A URL with a parseable page ID becomes a
     * TREE_URL; everything else is a KEYWORD (CQL text search).
     */
    public ConfluenceInput classifyConfluence(String raw) {
        if (raw == null || raw.isBlank()) return new ConfluenceInput(ConfluenceInput.Kind.KEYWORD, "", null, null);
        String trimmed = raw.strip();
        String pageId = confluenceParser.extractPageId(trimmed);
        if (pageId != null) {
            String baseUrl = confluenceParser.extractBaseUrl(trimmed);
            return new ConfluenceInput(ConfluenceInput.Kind.TREE_URL, trimmed, baseUrl, pageId);
        }
        return new ConfluenceInput(ConfluenceInput.Kind.KEYWORD, trimmed, null, null);
    }

    /**
     * Classify a Jira input. An HTTP(S) URL with an extractable key becomes
     * ISSUE_URL; a bare issue key becomes KEY; anything else is a KEYWORD
     * (JQL text search).
     */
    public JiraInput classifyJira(String raw) {
        if (raw == null || raw.isBlank()) return new JiraInput(JiraInput.Kind.KEYWORD, "", null, null);
        String trimmed = raw.strip();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            String key = jiraParser.extractIssueKey(trimmed);
            String baseUrl = jiraParser.extractBaseUrl(trimmed);
            if (key != null) return new JiraInput(JiraInput.Kind.ISSUE_URL, key, baseUrl, key);
            return new JiraInput(JiraInput.Kind.KEYWORD, trimmed, baseUrl, null);
        }
        if (JIRA_KEY.matcher(trimmed).matches()) {
            return new JiraInput(JiraInput.Kind.KEY, trimmed, null, trimmed);
        }
        return new JiraInput(JiraInput.Kind.KEYWORD, trimmed, null, null);
    }
}
