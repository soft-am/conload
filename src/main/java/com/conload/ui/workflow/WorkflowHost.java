package com.conload.ui.workflow;

import com.conload.model.AppConfig;

import javafx.stage.Window;

/**
 * Narrow interface the {@link WorkflowDialog} uses to interact with the host
 * application. The host (typically {@link com.conload.ui.AppShellController})
 * implements this to provide config, workspace paths, and terminal access.
 */
public interface WorkflowHost {

    /** The active Atlassian config (username, token, baseUrl). */
    AppConfig config();

    /** The GitHub personal access token. */
    String githubToken();

    /** The GitHub REST API root (blank = https://api.github.com). */
    String githubApiUrl();

    /** The active project id (null if no project is open). */
    String activeProjectId();

    /** The active project's working directory (the terminal cwd). */
    String workspacePath();

    /** The active project's contexts directory. */
    java.nio.file.Path contextsDir();

    /** Register a context folder with the active project (for the file tree). */
    void registerContext(String contextFolder);

    /** Push a substituted prompt into the shared prompt panel's text area,
     *  auto-expanding it so the user can review and apply via ▶ Send. */
    void setPromptText(String text);

    /** Send a command string to the active terminal. */
    void sendToTerminal(String command);

    /** Global default — local folder path with full Confluence data, same for all workflows. */
    String fullConfluenceFolder();

    /** The main app window, used to center the workflow dialog. */
    Window ownerWindow();
}
