package com.conload.service;

import com.conload.model.Project;
import com.conload.util.AppPaths;
import com.conload.util.FileUtil;
import com.conload.util.FileTreeOps;
import com.conload.util.Json;
import com.conload.util.JsonStore;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * CRUD service for Project entities, persisted to
 *   {@code ~/.conload/projects.json} (see {@link AppPaths#projectsJson()})
 *
 * <p>A "context" is now simply a folder on disk living under the project's
 * {@code contexts/} directory. The project keeps an ordered list of those
 * context folder absolute paths in {@link Project#getContextFolders()}.
 * The legacy {@code ProjectContext} entity and {@code contexts.json}
 * registry have been removed.
 */
public class ProjectService {

    private static final Logger log = Logger.getLogger(ProjectService.class.getName());

    private static final Path PROJECTS_PATH = AppPaths.projectsJson();

    private final JsonStore<List<Project>> store = JsonStore.list(PROJECTS_PATH.toString(), Project.class);

    // Project accent colours cycled on creation
    private static final String[] PALETTE = {
        "#4ec9b0", "#569cd6", "#c586c0", "#dcdcaa",
        "#ce9178", "#9cdcfe", "#f44747", "#b5cea8"
    };
    private int colourIndex = 0;

    public ProjectService() {}

    /** Find a project by id (null if not found). */
    public Project findById(String id) {
        if (id == null) return null;
        for (Project p : loadProjects()) {
            if (p.getId().equals(id)) return p;
        }
        return null;
    }

    // =========================================================================
    // Projects
    // =========================================================================

    public List<Project> loadProjects() {
        return store.load();
    }

    public void saveProjects(List<Project> projects) throws IOException {
        store.save(projects);
    }

    public Project createProject(String name, List<String> contextFolders) throws IOException {
        String colour = PALETTE[colourIndex++ % PALETTE.length];
        Project p = new Project(UUID.randomUUID().toString(), name, colour);
        p.setContextFolders(new ArrayList<>(contextFolders));
        List<Project> list = loadProjects();
        list.add(p);
        saveProjects(list);
        return p;
    }

    public Project createProjectInFolder(String name, List<String> contextFolders, String parentPath) throws IOException {
        String colour = PALETTE[colourIndex++ % PALETTE.length];
        Project p = new Project(UUID.randomUUID().toString(), name, colour);
        p.setContextFolders(new ArrayList<>(contextFolders));
        if (parentPath != null && !parentPath.isBlank()) {
            p.setParentPath(parentPath);
        }
        List<Project> list = loadProjects();
        list.add(p);
        saveProjects(list);
        syncProjectFolder(p);
        return p;
    }

    /**
     * Per-project contexts directory. If the project has an explicit
     * {@code contextsDir} override it is used; otherwise the default is
     * {@code ~/conload-contexts/<sanitized project name>} (project id when the
     * name is blank).
     */
    public Path resolveContextsDir(Project project) {
        String dir = project.getContextsDir();
        if (dir != null && !dir.isBlank()) return Path.of(dir);
        String safe = project.getName() == null ? "" : project.getName().replaceAll("[^A-Za-z0-9_-]", "_");
        if (safe.isBlank()) safe = project.getId();
        return Path.of(System.getProperty("user.home"), "conload-contexts", safe);
    }

    /**
     * Ensures the project's {@code contexts/} directory exists.  Contexts are
     * now real folders on disk (no symlink layer); this method therefore only
     * creates the parent structure and writes the {@code .project.json}
     * metadata file.
     */
    public void syncProjectFolder(Project project) throws IOException {
        if (project.getParentPath() == null || project.getParentPath().isBlank()) return;
        Path projectPath = Path.of(project.getParentPath());
        Files.createDirectories(projectPath);
        Path contextsDir = projectPath.resolve("contexts");
        Files.createDirectories(contextsDir);
        Files.createDirectories(resolveContextsDir(project));

        // Write metadata
        Path metadataFile = projectPath.resolve(".project.json");
        Json.write(metadataFile, project);
    }

    public void updateProject(Project updated) throws IOException {
        List<Project> list = loadProjects();
        list.replaceAll(p -> p.getId().equals(updated.getId()) ? updated : p);
        saveProjects(list);
    }

    public void deleteProject(String id) throws IOException {
        List<Project> list = loadProjects();
        list.removeIf(p -> p.getId().equals(id));
        saveProjects(list);
    }

    /**
     * Returns the terminal working directory for a project.
     * If the project has a defined {@code parentPath}, returns that directory.
     * Otherwise falls back to the user's home directory.
     */
    public Path resolveWorkspace(Project project) throws IOException {
        if (project.getParentPath() != null && !project.getParentPath().isBlank()) {
            Path p = Path.of(project.getParentPath());
            Files.createDirectories(p);
            syncProjectFolder(project);
            return p;
        }
        return Path.of(System.getProperty("user.home"));
    }

    // =========================================================================
    // Context folders (plain filesystem folders owned by a project)
    // =========================================================================

    /** Append a context folder path to a project and persist. */
    public void addContextFolder(String projectId, String folderPath) throws IOException {
        addFolderRef(projectId, folderPath, Project::getContextFolders);
    }

    /** Remove a context folder reference from a project, persisting the
     *  change and <b>deleting the physical folder</b> from disk.
     *  <p>Safety guard: the folder is only deleted if {@code folderPath} is
     *  actually registered as a context of the given project — arbitrary
     *  paths are refused (no-op). If the on-disk folder does not exist
     *  (already removed manually), the reference is still cleared.
     *  @return {@code true} if the context was registered and removed */
    public boolean removeContextFolder(String projectId, String folderPath) throws IOException {
        return removeFolderRef(projectId, folderPath, Project::getContextFolders);
    }

    /**
     * Renames a registered context folder: physically renames the directory on
     * disk ({@code Files.move}) and updates the path reference in
     * {@code projects.json}. The new name is sanitised for the filesystem.
     * Safety guard: {@code oldPath} must be registered as a context of the
     * project — arbitrary paths are refused.
     *
     * @return the new absolute path of the renamed folder, or {@code null} on
     *         failure (e.g. not registered, folder missing, target exists)
     */
    public String renameContextFolder(String projectId, String oldPath, String newName) throws IOException {
        return renameRegisteredFolder(projectId, oldPath, newName, true);
    }

    /**
     * Renames a registered session folder: physically renames the directory on
     * disk ({@code Files.move}) and updates the path reference in
     * {@code projects.json}. The new name is sanitised for the filesystem.
     * Safety guard: {@code oldPath} must be registered as a session of the
     * project — arbitrary paths are refused.
     *
     * @return the new absolute path of the renamed folder, or {@code null} on
     *         failure (e.g. not registered, folder missing, target exists)
     */
    public String renameSessionFolder(String projectId, String oldPath, String newName) throws IOException {
        return renameRegisteredFolder(projectId, oldPath, newName, false);
    }

    /**
     * Shared rename implementation for context and session folders.
     * Physically moves the directory, then replaces the old path with the new
     * path in the project's context/session folder list and persists.
     *
     * @param isContext {@code true} for context folders, {@code false} for sessions
     * @return the new absolute path, or {@code null} if not registered/missing
     */
    private String renameRegisteredFolder(String projectId, String oldPath, String newName,
                                          boolean isContext) throws IOException {
        String safe = sanitizeFolderName(newName);
        if (safe.isBlank()) return null;
        Path oldDir = Path.of(oldPath);
        Path parentDir = oldDir.getParent();
        Path newDir = parentDir == null ? Path.of(safe) : parentDir.resolve(safe);
        // Reject if target already exists (avoid overwriting).
        if (Files.exists(newDir) && !newDir.equals(oldDir)) return null;

        List<Project> list = loadProjects();
        for (Project p : list) {
            if (!p.getId().equals(projectId)) continue;
            List<String> folders = isContext ? p.getContextFolders() : p.getSessionFolders();
            int idx = folders.indexOf(oldPath);
            if (idx == -1) return null; // not registered — refuse
            // Physically move on disk.
            if (Files.exists(oldDir)) {
                Files.move(oldDir, newDir);
            }
            String newPath = newDir.toAbsolutePath().toString();
            folders.set(idx, newPath);
            saveProjects(list);
            syncProjectFolder(p);
            return newPath;
        }
        return null;
    }

    /** Sanitises a folder name for the filesystem (no path separators).
     *  Returns blank if the result is empty. */
    public static String sanitizeFolderName(String name) {
        if (name == null) return "";
        String safe = name.trim().replaceAll("[/\\\\]", "");
        safe = safe.replaceAll("[^A-Za-z0-9._\\- ]", "_");
        return safe.trim();
    }

    // =========================================================================
    // Session folders (exported session transcripts, separate from contexts)
    // =========================================================================

    /** Append a session folder path to a project and persist. */
    public void addSessionFolder(String projectId, String folderPath) throws IOException {
        addFolderRef(projectId, folderPath, Project::getSessionFolders);
    }

    /**
     * Persistently records the last-known CLI agent session for a project
     * (durable across tab close/restart). Used by the resume-on-restart
     * feature: {@code sessionType} is the discriminator (e.g. "opencode",
     * "copilot"), {@code sessionId} the opaque id returned by that CLI.
     * Passing a blank {@code sessionId} clears both fields.
     * <p>Live (currently-restored) session state still lives in
     * {@link com.conload.service.OpenTabsService.OpenTab}; this method only
     * updates the durable fallback on the {@link Project} model.
     */
    public void updateLastSession(String projectId, String sessionType, String sessionId) {
        if (projectId == null || projectId.isBlank()) return;
        String type = sessionType != null ? sessionType : "";
        String id   = sessionId   != null ? sessionId   : "";
        mutateProject(projectId, "updateLastSession", p -> {
            if (id.isBlank()) {
                if (p.getLastSessionId().isBlank()) return false;
                p.setLastSessionType("");
                p.setLastSessionId("");
            } else {
                if (type.equals(p.getLastSessionType()) && id.equals(p.getLastSessionId())) return false;
                p.setLastSessionType(type);
                p.setLastSessionId(id);
            }
            return true;
        });
    }

    /**
     * Persistently records the last-known CLI agent session for a specific
     * <em>worktree</em> of a project (the durable fallback used to offer a
     * resume after that worktree's terminal is closed). The base (primary)
     * workspace is NOT stored here — it uses {@link #updateLastSession}; this
     * map is keyed by the worktree's absolute path.
     * <p>Values are encoded as {@code "<type>:<id>"} (the id is everything
     * after the first colon). Passing a blank {@code sessionId} removes the
     * entry for that worktree.
     */
    public void updateWorktreeSession(String projectId, String worktreePath,
                                      String sessionType, String sessionId) {
        if (projectId == null || projectId.isBlank()) return;
        String wt   = worktreePath != null ? worktreePath : "";
        if (wt.isBlank()) return;                         // base uses updateLastSession
        String type = sessionType != null ? sessionType : "";
        String id   = sessionId   != null ? sessionId   : "";
        String encoded = type + ":" + id;
        mutateProject(projectId, "updateWorktreeSession", p -> {
            Map<String, String> sessions = p.getWorktreeSessions();
            if (id.isBlank()) {
                if (!sessions.containsKey(wt)) return false;
                sessions.remove(wt);
            } else {
                if (encoded.equals(sessions.get(wt))) return false;
                sessions.put(wt, encoded);
            }
            return true;
        });
    }

    /** Returns the durable {@code "<type>:<id>"} session string recorded for a
     *  worktree (or {@code null} when none is stored). */
    public String getWorktreeSession(String projectId, String worktreePath) {
        if (projectId == null || worktreePath == null || worktreePath.isBlank()) return null;
        for (Project p : loadProjects()) {
            if (p.getId().equals(projectId)) {
                return p.getWorktreeSessions().get(worktreePath);
            }
        }
        return null;
    }

    /** Records which worktree the user had selected so it can be re-selected
     *  on the next restore (blank = base workspace). */
    public void setLastWorktreePath(String projectId, String worktreePath) {
        if (projectId == null || projectId.isBlank()) return;
        String wt = worktreePath != null ? worktreePath : "";
        mutateProject(projectId, "setLastWorktreePath", p -> {
            if (wt.equals(p.getLastWorktreePath())) return false;
            p.setLastWorktreePath(wt);
            return true;
        });
    }

    private void mutateProject(String projectId, String operation, Predicate<Project> mutation) {
        try {
            List<Project> projects = loadProjects();
            for (Project project : projects) {
                if (project.getId().equals(projectId) && mutation.test(project)) {
                    saveProjects(projects);
                    return;
                }
            }
        } catch (IOException e) {
            log.warning("[PROJECT] " + operation + " failed: " + e.getMessage());
        }
    }

    /** Splits an encoded {@code "<type>:<id>"} string into [type, id]. */
    public static String[] decodeWorktreeSession(String encoded) {
        if (encoded == null || encoded.isBlank()) return new String[]{"", ""};
        int i = encoded.indexOf(':');
        if (i < 0) return new String[]{encoded, ""};
        return new String[]{encoded.substring(0, i), encoded.substring(i + 1)};
    }

    /** Remove a session folder reference from a project, persisting the
     *  change and <b>deleting the physical folder</b> from disk.
     *  <p>Safety guard: the folder is only deleted if {@code folderPath} is
     *  actually registered as a session of the given project — arbitrary
     *  paths are refused (no-op). If the on-disk folder does not exist
     *  (already removed manually), the reference is still cleared.
     *  @return {@code true} if the session was registered and removed */
    public boolean removeSessionFolder(String projectId, String folderPath) throws IOException {
        return removeFolderRef(projectId, folderPath, Project::getSessionFolders);
    }

    /** Reorder a session folder within a project's list (up = towards index 0). */
    public void moveSessionFolder(String projectId, String folderPath, boolean up) throws IOException {
        moveFolderRef(projectId, folderPath, up, Project::getSessionFolders);
    }

    /** Recursively delete a directory tree (files visited, then directories
     *  emptied on the way back up). Throws on the first failure. */
    private void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Delete an arbitrary folder from disk (no project registration involved).
     *  Used for context folders that exist on disk but aren't registered in
     *  projects.json. Does nothing (silently) if the path does not exist. */
    public void deleteFolder(Path folder) throws IOException {
        if (Files.exists(folder)) deleteRecursively(folder);
    }

    /** Reorder a context folder within a project's list (up = towards index 0). */
    public void moveContextFolder(String projectId, String folderPath, boolean up) throws IOException {
        moveFolderRef(projectId, folderPath, up, Project::getContextFolders);
    }

    /**
     * Imports a set of local files/folders into a brand-new context folder
     * named after the user-supplied {@code name} (filesystem-sanitised),
     * located under {@code destBaseDir}.
     * Each source is copied (recursively for directories) into the new folder.
     *
     * @param project      the project to register the new context folder with
     * @param name         the user-supplied context name (sanitised for the filesystem)
     * @param sources      the local files/folders the user picked
     * @param destBaseDir  the parent directory in which the new context folder is created
     * @return the absolute path of the newly created context folder
     */
    public String importContextFolder(Project project, String name, List<Path> sources, Path destBaseDir) throws IOException {
        String safe = FileUtil.normalizeContextFolderName(name);
        Path contextDir = destBaseDir.resolve(safe);
        Files.createDirectories(contextDir);

        for (Path src : sources) {
            if (src == null) continue;
            Path dest = contextDir.resolve(src.getFileName());
            if (Files.isDirectory(src)) {
                FileTreeOps.copyRecursively(src, dest);
            } else if (Files.isRegularFile(src)) {
                Files.createDirectories(dest.getParent());
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        String absPath = contextDir.toAbsolutePath().toString();
        // Register with the project (persisted) — re-load to reflect latest state.
        addContextFolder(project.getId(), absPath);
        return absPath;
    }

    // =========================================================================
    // Generic folder-ref helpers (shared by context + session variants)
    // =========================================================================

    private void addFolderRef(String projectId, String folderPath,
                               Function<Project, List<String>> foldersFn) throws IOException {
        List<Project> list = loadProjects();
        for (Project p : list) {
            if (p.getId().equals(projectId)) {
                if (!foldersFn.apply(p).contains(folderPath)) {
                    foldersFn.apply(p).add(folderPath);
                }
                saveProjects(list);
                syncProjectFolder(p);
                return;
            }
        }
    }

    private boolean removeFolderRef(String projectId, String folderPath,
                                    Function<Project, List<String>> foldersFn) throws IOException {
        List<Project> list = loadProjects();
        for (Project p : list) {
            if (p.getId().equals(projectId)) {
                if (!foldersFn.apply(p).contains(folderPath)) {
                    return false;
                }
                Path folder = Path.of(folderPath);
                if (Files.exists(folder)) {
                    deleteRecursively(folder);
                }
                foldersFn.apply(p).remove(folderPath);
                saveProjects(list);
                syncProjectFolder(p);
                return true;
            }
        }
        return false;
    }

    private void moveFolderRef(String projectId, String folderPath, boolean up,
                                Function<Project, List<String>> foldersFn) throws IOException {
        List<Project> list = loadProjects();
        for (Project p : list) {
            if (p.getId().equals(projectId)) {
                List<String> folders = foldersFn.apply(p);
                int idx = folders.indexOf(folderPath);
                if (idx == -1) return;
                int target = up ? idx - 1 : idx + 1;
                if (target >= 0 && target < folders.size()) {
                    Collections.swap(folders, idx, target);
                    saveProjects(list);
                    syncProjectFolder(p);
                }
                return;
            }
        }
    }

}
