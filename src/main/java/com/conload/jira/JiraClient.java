package com.conload.jira;

import com.conload.http.AbstractRestClient;
import com.conload.model.AppConfig;
import com.conload.model.JiraIssue;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * HTTP client for Jira Cloud REST API v3.
 * Auth: Basic Base64(email:api_token) — same token as Confluence.
 */
public class JiraClient extends AbstractRestClient {

    /** Default fields requested for JQL search results. */
    private static final List<String> DEFAULT_SEARCH_FIELDS =
        List.of("summary", "issuetype", "status", "priority", "assignee", "reporter");

    /** A remote link on a Jira issue (e.g. a Confluence page URL). */
    public record RemoteLink(String id, String title, String url, String relationship) {
        public String getId()           { return id != null ? id : ""; }
        public String getTitle()        { return title != null ? title : ""; }
        public String getUrl()          { return url != null ? url : ""; }
        public String getRelationship() { return relationship != null ? relationship : ""; }
    }

    public JiraClient(AppConfig config) {
        super(basicAuth(config),
              Duration.ofSeconds(30),
              Duration.ofSeconds(60),
              3,
              1000L);
    }

    private static String basicAuth(AppConfig config) {
        String credentials = config.getUsername() + ":" + config.getToken();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetch a Jira issue with all relevant fields.
     *
     * @param baseUrl  e.g. https://company.atlassian.net
     * @param issueKey e.g. PROJ-123
     */
    public JiraIssue getIssue(String baseUrl, String issueKey)
            throws IOException, InterruptedException {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey
            + "?fields=summary,description,issuetype,status,priority,"
            + "assignee,reporter,labels,components,fixVersions,"
            + "subtasks,issuelinks,attachment,comment,created,updated,duedate,parent";
        return mapper.readValue(getJson(url), JiraIssue.class);
    }

    /**
     * Download binary attachment bytes, following redirects with Authorization header.
     * The {@code downloadUrl} comes from {@code JiraIssue.Attachment#getContent()}.
     */
    public byte[] downloadAttachment(String downloadUrl)
            throws IOException, InterruptedException {
        return downloadPreservingAuth(downloadUrl);
    }

    /**
     * Fetch all remote links (web links) attached to a Jira issue.
     * Used by the workflow to discover Confluence pages linked from a Jira.
     *
     * @param baseUrl  e.g. https://company.atlassian.net
     * @param issueKey e.g. PROJ-123
     */
    public List<RemoteLink> getRemoteLinks(String baseUrl, String issueKey)
            throws IOException, InterruptedException {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey + "/remotelink";
        String json = getJson(url);
        JsonNode array = mapper.readTree(json);
        List<RemoteLink> result = new ArrayList<>();
        if (!array.isArray()) return result;
        for (JsonNode node : array) {
            String id = node.path("id").asText("");
            String relationship = node.path("relationship").asText("");
            String title = node.path("object").path("title").asText("");
            String linkUrl = node.path("object").path("url").asText("");
            result.add(new RemoteLink(id, title, linkUrl, relationship));
        }
        return result;
    }

    /**
     * Batch-fetch multiple issues by key using JQL via the POST search/jql API.
     *
     * @param keys list of issue keys, e.g. ["PROJ-100", "PROJ-124"]
     */
    public List<JiraIssue> getIssuesBatch(String baseUrl, List<String> keys)
            throws IOException, InterruptedException {
        if (keys == null || keys.isEmpty()) return List.of();
        String jql = "key in (" + String.join(",", keys) + ")";
        return searchByJql(baseUrl, jql, keys.size());
    }

    /**
     * Search Jira issues using JQL via the POST search/jql API.
     *
     * @param baseUrl    e.g. https://company.atlassian.net
     * @param jql        JQL expression, e.g. {@code text~"keyword" ORDER BY updated DESC}
     * @param maxResults max number of issues to return
     */
    public List<JiraIssue> searchIssues(String baseUrl, String jql, int maxResults)
            throws IOException, InterruptedException {
        return searchByJql(baseUrl, jql, maxResults);
    }

    /**
     * Fetch the parent/epic key for an issue.
     * Tries {@code customfield_10014} (Jira Cloud default "Epic Link"),
     * then falls back to {@code parent.key}.
     *
     * @return epic/parent key, or empty string if none found
     */
    public String getEpicLinkKey(String baseUrl, String issueKey)
            throws IOException, InterruptedException {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey
                + "?fields=parent,customfield_10014";
        JsonNode root = mapper.readTree(getJson(url));
        JsonNode fields = root.path("fields");
        String epicKey = fields.path("customfield_10014").asText("");
        if (!epicKey.isBlank()) return epicKey;
        return fields.path("parent").path("key").asText("");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared JQL search helper (merges the former getIssuesBatch + searchIssues)
    // ─────────────────────────────────────────────────────────────────────────

    private List<JiraIssue> searchByJql(String baseUrl, String jql, int maxResults)
            throws IOException, InterruptedException {
        String body = mapper.writeValueAsString(java.util.Map.of(
            "jql", jql,
            "fields", DEFAULT_SEARCH_FIELDS,
            "maxResults", maxResults
        ));
        String json = postJson(baseUrl + "/rest/api/3/search/jql", body);
        JsonNode root = mapper.readTree(json);
        JsonNode issues = root.path("issues");
        List<JiraIssue> result = new ArrayList<>();
        if (issues.isArray()) {
            for (JsonNode node : issues) {
                result.add(mapper.treeToValue(node, JiraIssue.class));
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vendor error mapping
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void ensureSuccess(int status, String url, String body) throws IOException {
        if (status >= 200 && status < 300) return;
        if (status == 401) throw new IOException(
            "Jira authentication failed (401). Check email/API token in Config tab.");
        if (status == 403) throw new IOException(
            "Jira access denied (403). Check account has Jira access.");
        if (status == 404) throw new IOException(
            "Jira issue not found (404): " + url);
        throw new IOException("Jira API error: HTTP " + status + " for " + url);
    }
}
