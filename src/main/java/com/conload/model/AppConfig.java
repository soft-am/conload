package com.conload.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Application configuration (credentials + Atlassian base URL + GitHub root
 * URL/token + CLI types).
 * <p>
 * Immutable record; use the {@code withXxx(...)} helpers to produce a modified
 * copy (typically followed by {@code ConfigService.saveConfig(...)}).
 * <p>
 * The Atlassian {@code baseUrl} + {@code token} are shared by both Confluence
 * and Jira (same Atlassian account email + API token). {@code githubApiUrl}
 * is the GitHub REST API root (e.g. {@code https://api.github.com} for
 * github.com, or a GHE host); blank defaults to {@code https://api.github.com}
 * via {@link #githubApiBase()}.
 */
public record AppConfig(
        String username,
        String token,
        String baseUrl,
        String githubToken,
        String githubApiUrl,
        String defaultExportFolder,
        String shell,
        List<CliTypeDefinition> cliTypes
) {
    public static final String DEFAULT_GITHUB_API_URL = "https://api.github.com";

    public AppConfig {
        username            = username            != null ? username            : "";
        token               = token               != null ? token               : "";
        baseUrl             = baseUrl             != null ? baseUrl             : "";
        githubToken         = githubToken         != null ? githubToken         : "";
        githubApiUrl        = githubApiUrl        != null ? githubApiUrl        : "";
        defaultExportFolder = defaultExportFolder != null ? defaultExportFolder : "";
        shell               = shell               != null ? shell               : "";
        cliTypes            = cliTypes            != null ? cliTypes            : List.of();
    }

    // ── Convenience constructors (kept for backward compatibility) ────────────

    public AppConfig() {
        this("", "", "", "", "", "", "", List.of());
    }

    public AppConfig(String username, String token) {
        this(username, token, "", "", "", "", "", List.of());
    }

    public AppConfig(String username, String token, String baseUrl) {
        this(username, token, baseUrl, "", "", "", "", List.of());
    }

    public AppConfig(String username, String token, String baseUrl, String githubToken) {
        this(username, token, baseUrl, githubToken, "", "", "", List.of());
    }

    public AppConfig(String username, String token, String baseUrl, String githubToken, String defaultExportFolder) {
        this(username, token, baseUrl, githubToken, "", defaultExportFolder, "", List.of());
    }

    // ── Bean-style accessors (kept for callers still using getXxx()) ─────────

    public String getUsername()            { return username; }
    public String getToken()                { return token; }
    public String getBaseUrl()             { return baseUrl; }
    public String getGithubToken()         { return githubToken; }
    /** Returns the configured GitHub REST API root (never null; blank = unset). */
    public String getGithubApiUrl()        { return githubApiUrl; }
    public String getDefaultExportFolder() { return defaultExportFolder; }
    /** Returns the configured terminal shell (never null; blank when unset). */
    public String getShell()               { return shell; }
    /** Returns the configured CLI type definitions (never null; may be empty
     *  if none configured, in which case callers fall back to
     *  {@link CliTypeDefinition#defaults()}). */
    public List<CliTypeDefinition> getCliTypes() { return cliTypes; }

    // ── Mutators — re-construct with the updated field. Each returns a new
    //    AppConfig; routes through the canonical constructor so null-coalescing
    //    still applies. Kept for source-compat with callers that previously did
    //    {@code cfg.setShell(...)} then {@code saveConfig(cfg)}. ────────────────

    public AppConfig setUsername(String v)       { return new AppConfig(v, token, baseUrl, githubToken, githubApiUrl, defaultExportFolder, shell, cliTypes); }
    public AppConfig setToken(String v)          { return new AppConfig(username, v, baseUrl, githubToken, githubApiUrl, defaultExportFolder, shell, cliTypes); }
    public AppConfig setBaseUrl(String v)        { return new AppConfig(username, token, v, githubToken, githubApiUrl, defaultExportFolder, shell, cliTypes); }
    public AppConfig setGithubToken(String v)   { return new AppConfig(username, token, baseUrl, v, githubApiUrl, defaultExportFolder, shell, cliTypes); }
    public AppConfig setGithubApiUrl(String v)   { return new AppConfig(username, token, baseUrl, githubToken, v, defaultExportFolder, shell, cliTypes); }
    public AppConfig setDefaultExportFolder(String v) { return new AppConfig(username, token, baseUrl, githubToken, githubApiUrl, v, shell, cliTypes); }
    public AppConfig setShell(String v)         { return new AppConfig(username, token, baseUrl, githubToken, githubApiUrl, defaultExportFolder, v, cliTypes); }
    public AppConfig setCliTypes(List<CliTypeDefinition> v) { return new AppConfig(username, token, baseUrl, githubToken, githubApiUrl, defaultExportFolder, shell, v != null ? v : List.of()); }

    /** Returns {@code githubApiUrl} when set, otherwise the default
     *  {@code https://api.github.com}. Used by {@code GitHubClient} and
     *  all GitHub web-URL parsers. */
    public String githubApiBase() {
        return githubApiUrl != null && !githubApiUrl.isBlank() ? githubApiUrl.strip() : DEFAULT_GITHUB_API_URL;
    }

    public boolean isValid() {
        return !username.isBlank() && !token.isBlank();
    }
}
