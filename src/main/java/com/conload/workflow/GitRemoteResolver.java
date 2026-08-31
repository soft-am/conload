package com.conload.workflow;

import com.conload.util.ProcessRunner;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the GitHub {@code owner/repo} from the active project's working
 * directory by running {@code git remote get-url origin} and parsing the
 * resulting SSH or HTTPS URL.
 */
public final class GitRemoteResolver {

    // Host-agnostic: matches github.com OR any GitHub Enterprise host.
    private static final Pattern HTTPS = Pattern.compile("https?://[^/]+/([^/]+)/([^/]+?)(?:\\.git)?$");
    private static final Pattern SSH   = Pattern.compile("git@[^:]+:([^/]+)/([^/]+?)(?:\\.git)?$");

    private GitRemoteResolver() {}

    /**
     * @param workspacePath the project working directory (git checkout root)
     * @return {@code "owner/repo"} or empty string if it cannot be determined
     */
    public static String resolveOwnerRepo(String workspacePath) {
        if (workspacePath == null || workspacePath.isBlank()) return "";
        try {
            ProcessRunner.Result result = ProcessRunner.run(
                    List.of("git", "remote", "get-url", "origin"),
                    Path.of(workspacePath), true);
            if (result.exitCode() != 0) return "";
            String url = result.stdout().strip();
            return parseUrl(url);
        } catch (Exception e) {
            return "";
        }
    }

    /** Parse a git remote URL (SSH or HTTPS) into {@code owner/repo}. */
    static String parseUrl(String url) {
        if (url == null || url.isBlank()) return "";
        Matcher m = HTTPS.matcher(url);
        if (m.matches()) return m.group(1) + "/" + m.group(2);
        m = SSH.matcher(url);
        if (m.matches()) return m.group(1) + "/" + m.group(2);
        return "";
    }
}
