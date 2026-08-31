package com.conload.workflow;

import com.conload.model.AppConfig;

import java.nio.file.Path;

/**
 * Host-provided runtime environment for a workflow accumulation.
 * Built by {@link com.conload.ui.workflow.WorkflowHost} and passed to
 * {@link Workflow#accumulate}.
 *
 * @param config               the active Atlassian config (username, token, baseUrl)
 * @param githubToken          the GitHub personal access token
 * @param githubApiUrl         the GitHub REST API root (blank = https://api.github.com)
 * @param workspacePath        the active project's working directory (terminal cwd)
 * @param contextsDir          the active project's contexts directory
 * @param projectId            the active project id (for context-folder registration)
 * @param fullConfluenceFolder global default — local folder path with full Confluence
 *                             data, injected into every workflow's prompt template
 */
public record WorkflowEnvironment(
        AppConfig config,
        String githubToken,
        String githubApiUrl,
        String workspacePath,
        Path contextsDir,
        String projectId,
        String fullConfluenceFolder
) {}
