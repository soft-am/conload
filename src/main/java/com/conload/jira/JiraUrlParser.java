package com.conload.jira;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * Parses Jira Cloud URLs to extract base URL and issue key.
 *
 * Supported formats:
 *   https://site.atlassian.net/browse/PROJ-123
 *   https://site.atlassian.net/jira/.../issues/PROJ-123
 *   https://site.atlassian.net/jira/...?selectedIssue=PROJ-123
 */
public class JiraUrlParser {
    private static final Pattern PATH_KEY =
        Pattern.compile("(https://[^/]+)(?:/browse|/jira(?:/[^?]*)?/issues)/([A-Z][A-Z0-9_]+-\\d+)");
    private static final Pattern QUERY_KEY =
        Pattern.compile("[?&]selectedIssue=([A-Z][A-Z0-9_]+-\\d+)");
    private static final Pattern BARE_KEY =
        Pattern.compile("\\b([A-Z][A-Z0-9_]+-\\d+)\\b");
    private static final Pattern BASE_URL_PAT =
        Pattern.compile("(https://[^/]+)");
    public String extractIssueKey(String url) {
        if (url == null || url.isBlank()) return null;
        Matcher m = PATH_KEY.matcher(url);
        if (m.find()) return m.group(2);
        m = QUERY_KEY.matcher(url);
        if (m.find()) return m.group(1);
        m = BARE_KEY.matcher(url);
        if (m.find()) return m.group(1);
        return null;
    }
    public String extractBaseUrl(String url) {
        if (url == null || url.isBlank()) return null;
        Matcher m = BASE_URL_PAT.matcher(url.strip());
        return m.find() ? m.group(1) : null;
    }
}
