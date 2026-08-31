package com.conload.model;
/**
 * Captures all inputs for one unified search run.
 */
public record SearchRequest(
        String keyword,
        String confluenceUrl,
        String jiraUrl,
        String githubRepo,
        String githubKey,
        String localMdPath
) {
    public boolean allBlank() {
        return keyword.isBlank() && confluenceUrl.isBlank() && jiraUrl.isBlank()
                && githubRepo.isBlank() && localMdPath.isBlank();
    }
    public String effectiveGithubKey() {
        return githubKey.isBlank() ? keyword : githubKey;
    }
}
