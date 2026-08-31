package com.conload.confluence;

import com.conload.http.AbstractRestClient;
import com.conload.model.AppConfig;
import com.conload.model.ConfluenceAttachment;
import com.conload.model.ConfluencePage;
import com.conload.model.ConfluenceSpace;
import com.conload.ui.Icons;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * HTTP client for Confluence Cloud REST API v1.
 * Uses Basic Auth: Base64(email:api_token)
 *
 * Key endpoints:
 *   GET /wiki/rest/api/content/{id}?expand=body.storage
 *   GET /wiki/rest/api/content/{id}/child/page?limit=50&start=0
 *   GET /wiki/rest/api/content/{id}/child/attachment
 */
public class ConfluenceClient extends AbstractRestClient {

    private static final int PAGE_SIZE = 50;

    public ConfluenceClient(AppConfig config) {
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

    /**
     * Fetch a single page with its storage-format body.
     */
    public ConfluencePage getPage(String baseUrl, String pageId) throws IOException, InterruptedException {
        String url = baseUrl + "/wiki/rest/api/content/" + pageId
            + "?expand=body.storage,version,space";
        return mapper.readValue(getJson(url), ConfluencePage.class);
    }

    /**
     * Fetch all direct child pages of a given page (paginated).
     */
    public List<ConfluencePage> getChildPages(String baseUrl, String pageId)
        throws IOException, InterruptedException {
        return paginateConfluence(
            baseUrl + "/wiki/rest/api/content/" + pageId
                + "/child/page?limit=" + PAGE_SIZE + "&start=%d&expand=body.storage",
            ConfluencePage.class);
    }

    /**
     * Fetch all attachments for a given page (paginated).
     */
    public List<ConfluenceAttachment> getAttachments(String baseUrl, String pageId)
        throws IOException, InterruptedException {
        return paginateConfluence(
            baseUrl + "/wiki/rest/api/content/" + pageId
                + "/child/attachment?limit=" + PAGE_SIZE + "&start=%d",
            ConfluenceAttachment.class);
    }

    // -------------------------------------------------------------------------
    // Space Browser API
    // -------------------------------------------------------------------------

    /**
     * Fetch all global Confluence spaces (paginated).
     */
    public List<ConfluenceSpace> getSpaces(String baseUrl) throws IOException, InterruptedException {
        return paginateConfluence(
            baseUrl + "/wiki/rest/api/space?limit=" + PAGE_SIZE
                + "&start=%d&type=global&status=current",
            ConfluenceSpace.class);
    }

    /**
     * Fetch root-level pages of a space (metadata only, no body — fast).
     */
    public List<ConfluencePage> getRootPagesMeta(String baseUrl, String spaceKey)
            throws IOException, InterruptedException {
        return paginateConfluence(
            baseUrl + "/wiki/rest/api/content?spaceKey=" + spaceKey
                + "&depth=root&type=page&limit=" + PAGE_SIZE + "&start=%d",
            ConfluencePage.class);
    }

    /**
     * Search Confluence pages using CQL text search.
     * Returns pages with space metadata populated.
     *
     * @param baseUrl    e.g. https://company.atlassian.net
     * @param query      search term (plain text, not CQL-encoded)
     * @param maxResults maximum number of results to return
     */
    public List<ConfluencePage> searchPages(String baseUrl, String query, int maxResults)
            throws IOException, InterruptedException {
        List<ConfluencePage> result = new ArrayList<>();
        int start = 0;
        int limit = Math.min(maxResults, PAGE_SIZE);
        while (result.size() < maxResults) {
            String cql = "type=page AND text~\"" + query.replace("\"", "\\\"") + "\"";
            String url = baseUrl + "/wiki/rest/api/content/search"
                + "?cql=" + java.net.URLEncoder.encode(cql, StandardCharsets.UTF_8)
                + "&expand=space,_links"
                + "&limit=" + limit
                + "&start=" + start;
            String json = getJson(url);
            JsonNode root = mapper.readTree(json);
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) break;
            for (JsonNode node : results) {
                result.add(mapper.treeToValue(node, ConfluencePage.class));
            }
            if (results.size() < limit) break;
            start += limit;
        }
        return result;
    }

    /**
     * Fetch direct child pages (metadata only, no body — used for tree lazy-loading).
     */
    public List<ConfluencePage> getChildPagesMeta(String baseUrl, String pageId)
            throws IOException, InterruptedException {
        return paginateConfluence(
            baseUrl + "/wiki/rest/api/content/" + pageId
                + "/child/page?limit=" + PAGE_SIZE + "&start=%d",
            ConfluencePage.class);
    }

    /**
     * Build the ONLY working download URL for Confluence Cloud attachments with Basic Auth.
     *
     *   ❌  /wiki/download/attachments/{pageId}/{filename}?...    → 401 (no Basic Auth)
     *   ❌  /wiki/rest/api/content/{attachmentId}/download        → 404
     *   ✅  /wiki/rest/api/content/{pageId}/child/attachment/{attachmentId}/download → 200
     */
    public String buildAttachmentDownloadUrl(String baseUrl, String pageId, String attachmentId) {
        return baseUrl + "/wiki/rest/api/content/" + pageId
            + "/child/attachment/" + attachmentId + "/download";
    }

    /**
     * Download binary attachment content, following 302 redirects manually while
     * re-attaching the Authorization header on every hop (Java's
     * {@code HttpClient.Redirect.NORMAL} strips it by design).
     */
    public byte[] downloadAttachment(String url) throws IOException, InterruptedException {
        return downloadPreservingAuth(url);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Generic Atlassian pagination loop. The URL template must contain {@code %d} for {@code start}. */
    private <T> List<T> paginateConfluence(String urlTemplate, Class<T> type)
            throws IOException, InterruptedException {
        List<T> result = new ArrayList<>();
        int start = 0;
        while (true) {
            String json = getJson(String.format(urlTemplate, start));
            JsonNode root = mapper.readTree(json);
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) break;
            for (JsonNode node : results) {
                result.add(mapper.treeToValue(node, type));
            }
            if (root.path("size").asInt(0) < PAGE_SIZE) break;
            start += PAGE_SIZE;
        }
        return result;
    }

    /** Vendor-specific status mapping with helpful messages for Confluence auth failures. */
    @Override
    protected void ensureSuccess(int status, String url, String body) throws IOException {
        if (status >= 200 && status < 300) return;
        if (status == 401) {
            throw new IOException(
                "Authentication failed (401).\n" +
                "• Username must be your Atlassian account EMAIL (e.g. user@example.com)\n" +
                "• Token must be an API token from https://id.atlassian.com/manage-profile/security/api-tokens\n" +
                "• Do NOT use your Atlassian password – only API tokens work with REST API.");
        }
        if (status == 403) {
            String serverMsg = extractServerMessage(body);
            if (serverMsg != null && serverMsg.contains("not permitted to use Confluence")) {
                throw new IOException(
                    "Access denied (403): " + serverMsg + "\n\n" +
                    "This means your Atlassian account exists but does NOT have a Confluence license.\n" +
                    "How to fix:\n" +
                    "  1. Ask your Atlassian admin to assign you a Confluence user license at\n" +
                    "     https://<your-site>.atlassian.net/admin/users\n" +
                    "  2. Or use a different account that already has Confluence access.\n" +
                    "  3. If you ARE the admin: go to https://admin.atlassian.com " + Icons.ARROW_RIGHT + " Products " + Icons.ARROW_RIGHT + " Confluence " + Icons.ARROW_RIGHT + " Manage access.");
            }
            throw new IOException(
                "Access denied (403)" + (serverMsg != null ? ": " + serverMsg : "") + "\n" +
                "Check that your account has permission to view this Confluence space.");
        }
        if (status == 404) {
            throw new IOException("Page not found (404) for URL: " + url +
                "\nVerify the page ID is correct and that the page has not been deleted.");
        }
        String serverMsg = extractServerMessage(body);
        throw new IOException("Confluence API error: HTTP " + status +
            (serverMsg != null ? " – " + serverMsg : "") + "\nURL: " + url);
    }

    /** Try to extract the "message" field from a Confluence JSON error response. */
    private String extractServerMessage(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode node = mapper.readTree(body);
            if (node.has("message")) return node.get("message").asText();
            if (node.has("errorMessage")) return node.get("errorMessage").asText();
        } catch (Exception ignored) {}
        return null;
    }
}
