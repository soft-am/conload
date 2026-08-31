package com.conload.http;

import com.conload.util.Json;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Shared HTTP plumbing for the three vendor REST clients
 * ({@link com.conload.confluence.ConfluenceClient},
 * {@link com.conload.jira.JiraClient},
 * {@link com.conload.github.GitHubClient}).
 *
 * <p>Each vendor previously re-implemented:
 * <ul>
 *   <li>two {@link HttpClient} instances (with-redirect / no-redirect),
 *   <li>an {@link ObjectMapper} field,
 *   <li>a {@code SupplierWithThrows} functional interface (defined 3×),
 *   <li>a {@code withRetry(action)} method (3 near-identical copies with
 *       linear backoff),
 *   <li>a {@code getJson(url)} method building an authenticated GET and
 *       mapping HTTP status codes to vendor-specific exception messages,
 *   <li>a manual-redirect {@code downloadAttachment(url)} loop following
 *       {@code 3xx} hops while re-attaching the {@code Authorization} header.
 * </ul>
 *
 * <p>This base class consolidates those. Subclasses implement
 * {@link #ensureSuccess(int, String, String)} with vendor-specific messaging.
 *
 * <p>All HTTP methods accept an optional {@code extraHeaders} map (used by
 * GitHub, which adds {@code X-GitHub-Api-Version} on every request).
 */
public abstract class AbstractRestClient {

    private static final int MAX_REDIRECTS = 10;

    /** Vendor-specific logger; uses the subclass's class name. */
    protected final Logger log = Logger.getLogger(getClass().getName());

    /** Follows redirects automatically (NORMAL removes Authorization on redirect). */
    protected final HttpClient http;
    /** NEVER follows redirects — for manual redirect handling. */
    protected final HttpClient httpNoRedirect;

    /** Shared, thread-safe mapper (see {@link Json#MAPPER}). */
    protected final ObjectMapper mapper = Json.MAPPER;

    protected final String authHeader;
    protected final Duration timeout;
    protected final int maxRetries;
    protected final long backoffMillis;

    protected AbstractRestClient(String authHeader,
                                  Duration connectTimeout,
                                  Duration requestTimeout,
                                  int maxRetries,
                                  long backoffMillis) {
        this.authHeader = authHeader;
        this.timeout = requestTimeout;
        this.maxRetries = maxRetries;
        this.backoffMillis = backoffMillis;
        this.http = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.httpNoRedirect = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Retry
    // ─────────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws IOException, InterruptedException;
    }

    /** Retry with linear backoff ({@code backoffMillis * attempt}). */
    protected <T> T withRetry(ThrowingSupplier<T> action) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return action.get();
            } catch (IOException e) {
                last = e;
                if (attempt < maxRetries) {
                    log.warning("Retry " + attempt + "/" + maxRetries + ": " + e.getMessage());
                    Thread.sleep(backoffMillis * attempt);
                }
            }
        }
        throw last;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Request builders
    // ─────────────────────────────────────────────────────────────────────────

    /** Authenticated JSON GET. Returns the response body. */
    protected String getJson(String url) throws IOException, InterruptedException {
        return getJson(url, "application/json", Map.of());
    }

    /** Authenticated GET with custom Accept + extra headers (e.g. {@code X-GitHub-Api-Version}). */
    protected String getJson(String url, String accept, Map<String, String> extraHeaders)
            throws IOException, InterruptedException {
        HttpRequest req = newRequest(url, accept, "", extraHeaders).GET().build();
        HttpResponse<String> resp = withRetry(() ->
            http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        ensureSuccess(resp.statusCode(), url, resp.body());
        return resp.body();
    }

    /** Authenticated JSON POST. Returns the response body. */
    protected String postJson(String url, String jsonBody) throws IOException, InterruptedException {
        return postJson(url, jsonBody, "application/json", Map.of());
    }

    /** Authenticated POST with custom Accept + extra headers. */
    protected String postJson(String url, String jsonBody, String accept, Map<String, String> extraHeaders)
            throws IOException, InterruptedException {
        HttpRequest req = newRequest(url, accept, "application/json", extraHeaders)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> resp = withRetry(() ->
            http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        ensureSuccess(resp.statusCode(), url, resp.body());
        return resp.body();
    }

    /** Returns the full {@link HttpResponse} for a GET so callers can inspect
     *  status/headers (used by GitHub's commit-diff fetch and PR-fetch that
     *  need the raw response). */
    protected HttpResponse<String> getRaw(String url, String accept, Map<String, String> extraHeaders)
            throws IOException, InterruptedException {
        HttpRequest req = newRequest(url, accept, "", extraHeaders).GET().build();
        HttpResponse<String> resp = withRetry(() ->
            http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        ensureSuccess(resp.statusCode(), url, resp.body());
        return resp;
    }

    /** Shared request builder primed with Auth + Accept + Content-Type + extra headers. */
    private HttpRequest.Builder newRequest(String url, String accept, String contentType,
                                            Map<String, String> extraHeaders) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Accept", accept)
            .timeout(timeout);
        if (!contentType.isBlank()) b.header("Content-Type", contentType);
        extraHeaders.forEach(b::header);
        return b;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manual redirect handling (for attachment/log downloads)
    // ─────────────────────────────────────────────────────────────────────────

    /** Follow 3xx redirects manually, <b>preserving</b> the Authorization header on every hop
     *  (uses {@link #httpNoRedirect}). Used by Confluence + Jira attachment downloads. */
    protected byte[] downloadPreservingAuth(String url) throws IOException, InterruptedException {
        String currentUrl = url;
        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(currentUrl))
                .header("Authorization", authHeader)
                .GET()
                .timeout(timeout)
                .build();
            HttpResponse<byte[]> response = httpNoRedirect.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status == 200) return response.body();
            if (isRedirect(status)) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null || location.isBlank()) {
                    throw new IOException("Redirect (HTTP " + status + ") with no Location header from: " + currentUrl);
                }
                currentUrl = resolveLocation(currentUrl, location);
                log.fine("Redirect " + status + " -> " + currentUrl);
                continue;
            }
            ensureSuccess(status, url, null);  // throws IOException with vendor-specific message
            throw new IOException("Download failed: HTTP " + status + " for " + url);
        }
        throw new IOException("Too many redirects (" + MAX_REDIRECTS + ") for: " + url);
    }

    /** First hop is authed; the 302-redirected CDN hop carries NO auth header
     *  (CDN URLs are self-authenticating and reject extra auth). Used by GitHub log downloads. */
    protected String fetchRedirectedBody(String url, String accept, Map<String, String> extraHeaders)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Accept", accept)
            .timeout(timeout);
        extraHeaders.forEach(b::header);
        HttpRequest authedReq = b.GET().build();
        HttpResponse<String> resp = withRetry(() ->
            httpNoRedirect.send(authedReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        int status = resp.statusCode();
        if (status == 200) return resp.body();
        if (status == 302 || status == 301) {
            String location = resp.headers().firstValue("Location").orElse(null);
            if (location == null || location.isBlank())
                throw new IOException("Redirect (" + status + ") without Location header");
            HttpRequest cdnReq = HttpRequest.newBuilder()
                .uri(URI.create(location))
                .header("Accept", "text/plain")
                .GET()
                .timeout(timeout)
                .build();
            HttpResponse<String> cdnResp = http.send(cdnReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int cdnStatus = cdnResp.statusCode();
            if (cdnStatus < 200 || cdnStatus >= 300)
                throw new IOException("CDN log download failed: HTTP " + cdnStatus);
            return cdnResp.body();
        }
        throw new IOException("Unexpected status " + status + " fetching logs from " + url);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vendor hooks
    // ─────────────────────────────────────────────────────────────────────────

    /** Map an HTTP failure status to an {@link IOException} with vendor-specific messaging.
     *  Called from {@link #getJson}, {@link #postJson}, {@link #getRaw}, and {@link #downloadPreservingAuth}.
     *  Must throw on failure status; return silently on 2xx. */
    protected abstract void ensureSuccess(int status, String url, String body) throws IOException;

    // ─────────────────────────────────────────────────────────────────────────
    // Static helpers
    // ─────────────────────────────────────────────────────────────────────────

    protected static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    /** Resolve a relative {@code Location} header against the source URL. */
    protected static String resolveLocation(String sourceUrl, String location) {
        if (location.startsWith("/")) {
            URI base = URI.create(sourceUrl);
            return base.getScheme() + "://" + base.getHost() + location;
        }
        return location;
    }

    /** Convenience for building an immutable header map from key/value pairs. */
    protected static Map<String, String> headers(String... kv) {
        if (kv.length == 0) return Map.of();
        var m = new LinkedHashMap<String, String>(kv.length / 2);
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return Map.copyOf(m);
    }
}
