package com.conload.ui.projects.workspace;

import com.conload.model.Project;
import com.conload.service.ConfigService;
import com.conload.service.PidRegistryService;
import com.conload.service.ProjectService;
import com.conload.ui.ProjectColors;
import com.conload.ui.projects.ProjectFilesPane;
import com.conload.ui.terminal.CopilotTerminalPane;
import com.conload.ui.terminal.RobotIndicator;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

/** Creates and wires terminals owned by each project workspace.
 *  Each workspace key maps to a {@link TerminalGroup} holding N terminals. */
public final class ProjectTerminalFactory {
    private final Stage stage;
    private final ConfigService configService;
    private final ProjectService projectService;
    private final PidRegistryService pidRegistry;
    private final Map<String, CopilotTerminalPane> legacyTerminals;
    private final Map<String, TerminalGroup> projectTerminals;
    private final Map<String, String> projectColors;
    private final Map<String, RobotIndicator.Character> projectCharacters;
    private final Map<String, ProjectFilesPane> projectFilesPanes;
    private final Runnable refreshProjectTabStates;
    private final Consumer<String> setTerminalSeparatorColor;
    private final Runnable saveOpenTabs;

    public ProjectTerminalFactory(Stage stage, ConfigService configService,
                                  ProjectService projectService, PidRegistryService pidRegistry,
                                  Map<String, CopilotTerminalPane> legacyTerminals,
                                  Map<String, TerminalGroup> projectTerminals,
                                  Map<String, String> projectColors,
                                  Map<String, RobotIndicator.Character> projectCharacters,
                                  Map<String, ProjectFilesPane> projectFilesPanes,
                                  Consumer<String> setTerminalSeparatorColor,
                                  Runnable refreshProjectTabStates,
                                  Runnable saveOpenTabs) {
        this.stage = stage;
        this.configService = configService;
        this.projectService = projectService;
        this.pidRegistry = pidRegistry;
        this.legacyTerminals = legacyTerminals;
        this.projectTerminals = projectTerminals;
        this.projectColors = projectColors;
        this.projectCharacters = projectCharacters;
        this.projectFilesPanes = projectFilesPanes;
        this.setTerminalSeparatorColor = setTerminalSeparatorColor;
        this.refreshProjectTabStates = refreshProjectTabStates;
        this.saveOpenTabs = saveOpenTabs;
    }

    /** Gets-or-creates the terminal group for a workspace key. Returns the
     *  active sub-terminal (creating+adding a wired terminal if the group is new). */
    public CopilotTerminalPane getOrCreate(Project project, String workspaceKey, String workDir) {
        TerminalGroup group = projectTerminals.computeIfAbsent(workspaceKey, k -> new TerminalGroup());
        if (group.size() == 0) {
            CopilotTerminalPane terminal = createTerminal(project, workspaceKey, workDir);
            group.add(terminal);
        }
        return group.active();
    }

    /** Always creates a new terminal in the group (the subtab + button). */
    public CopilotTerminalPane createNew(Project project, String workspaceKey, String workDir) {
        TerminalGroup group = projectTerminals.computeIfAbsent(workspaceKey, k -> new TerminalGroup());
        CopilotTerminalPane terminal = createTerminal(project, workspaceKey, workDir);
        group.add(terminal);
        return terminal;
    }

    private CopilotTerminalPane createTerminal(Project project, String workspaceKey, String workDir) {
        String projectId = project.getId();
        CopilotTerminalPane terminal = new CopilotTerminalPane(stage, workDir, true);
        try {
            String shell = configService.loadConfig().getShell();
            if (shell != null && !shell.isBlank()) terminal.setShell(shell);
        } catch (Exception ex) {
            System.err.println("[TERMINAL] Could not load shell config: " + ex.getMessage());
        }

        String color = projectColors.computeIfAbsent(projectId, p -> ProjectColors.next());
        terminal.setProjectColor(color);
        setTerminalSeparatorColor.accept(color);
        RobotIndicator.Character character = projectCharacters.computeIfAbsent(
                projectId, p -> RobotIndicator.Character.next());
        terminal.setProjectCharacter(character);
        terminal.setContextsDir(projectService.resolveContextsDir(project));
        terminal.setDefaultExportFolder(new ConfigService().loadConfig().getDefaultExportFolder());
        terminal.setOnSessionExported(sessionFolder -> {
            try {
                projectService.addSessionFolder(projectId, sessionFolder.toString());
                Platform.runLater(() -> {
                    ProjectFilesPane pane = projectFilesPanes.get(projectId);
                    if (pane != null) {
                        Project current = projectService.findById(projectId);
                        if (current != null) pane.setSessionFolderPaths(current.getSessionFolders());
                        pane.refresh();
                        pane.highlightAndExpandPath(sessionFolder);
                    }
                });
            } catch (IOException ex) {
                System.err.println("[SESSION-EXPORT] register session folder failed: " + ex.getMessage());
            }
        });
        terminal.setOnExportStarted(label -> {
            ProjectFilesPane pane = projectFilesPanes.get(projectId);
            if (pane != null) pane.showTaskBadge(label);
        });
        terminal.setOnExportFinished(() -> {
            ProjectFilesPane pane = projectFilesPanes.get(projectId);
            if (pane != null) pane.hideTaskBadge();
        });
        terminal.busyProperty().addListener((obs, old, busy) -> refreshProjectTabStates.run());
        terminal.sessionProperty().addListener((obs, old, value) -> refreshProjectTabStates.run());
        terminal.hasInputProperty().addListener((obs, old, value) -> refreshProjectTabStates.run());
        terminal.setOnSessionDetected(saveOpenTabs);
        String worktreePath = keyWorktreePath(workspaceKey);
        terminal.setOnPtyStarted(pid -> {
            try { pidRegistry.add(pid, projectId, worktreePath); }
            catch (Exception ex) { System.err.println("[PID-REGISTRY] add failed: " + ex.getMessage()); }
        });
        terminal.setOnPtyStopped(pid -> {
            try { pidRegistry.remove(pid); }
            catch (Exception ex) { System.err.println("[PID-REGISTRY] remove failed: " + ex.getMessage()); }
        });
        return terminal;
    }

    private String keyWorktreePath(String workspaceKey) {
        int separator = workspaceKey.indexOf('\u0001');
        return separator < 0 ? "" : workspaceKey.substring(separator + 1);
    }

    /** Not used; kept for compatibility. */
    @SuppressWarnings("unused")
    private Map<String, CopilotTerminalPane> legacyTerminals() { return legacyTerminals; }
}
