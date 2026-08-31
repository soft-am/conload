package com.conload.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralises all writable application state under a single OS-portable
 * directory rooted at {@code ~/.conload/}.
 *
 * <p>This replaces the legacy convention of writing runtime state (config,
 * projects, sessions, …) to relative {@code src/...} paths that were only
 * writable when the process working directory was the repository root. An
 * installed application (.dmg / .exe / .deb) launches with an arbitrary CWD
 * (often {@code /} or the system directory), so the legacy paths could not be
 * created and state silently failed to persist. {@code AppPaths} resolves
 * every state file against {@code ~/.conload/} so persistence works
 * identically in dev and in an installed app.
 *
 * <p>The Vosk model directory ({@code ~/.conload/vosk-model/}) already
 * followed this convention; the runtime-state files are now aligned with it.
 *
 * <h2>Files owned by AppPaths</h2>
 * <ul>
 *   <li>{@link #configTxt()}            — credentials & CLI definitions</li>
 *   <li>{@link #projectsJson()}         — project list</li>
 *   <li>{@link #quickActionsJson()}     — quick actions & prompt templates</li>
 *   <li>{@link #openTabsJson()}         — open terminal tabs</li>
 *   <li>{@link #terminalPidsJson()}     — live PTY process IDs</li>
 *   <li>{@link #opencodeSessionsJson()} — cached CLI agent sessions</li>
 *   <li>{@link #workflowSettingsJson()} — global workflow settings</li>
 *   <li>{@link #workflowTemplatesDir()} — per-workflow prompt-template overrides</li>
 * </ul>
 *
 * <h2>Legacy migration</h2>
 * {@link #bootstrap()} is called once at startup. It creates the data
 * directory and, when a target file is absent, copies any existing legacy
 * {@code src/<file>} over so existing developers keep their credentials and
 * state without manual action. The migration is idempotent and silent.
 */
public final class AppPaths {

    private static final Logger log = Logger.getLogger(AppPaths.class.getName());

    /** Root directory for all writable application state. */
    private static final Path DATA_DIR =
        Path.of(System.getProperty("user.home"), ".conload");

    private static final Path LEGACY_SRC_DIR = Path.of("src");

    private AppPaths() {}

    /** {@code ~/.conload/} */
    public static Path dataDir() { return DATA_DIR; }

    public static Path configTxt()            { return DATA_DIR.resolve("config.txt"); }
    public static Path projectsJson()         { return DATA_DIR.resolve("projects.json"); }
    public static Path quickActionsJson()     { return DATA_DIR.resolve("quick-actions.json"); }
    public static Path openTabsJson()         { return DATA_DIR.resolve("open_tabs.json"); }
    public static Path terminalPidsJson()     { return DATA_DIR.resolve("terminal_pids.json"); }
    public static Path opencodeSessionsJson() { return DATA_DIR.resolve("opencode-sessions.json"); }
    public static Path workflowSettingsJson() { return DATA_DIR.resolve("workflow-settings.json"); }
    public static Path workflowTemplatesDir() { return DATA_DIR.resolve("workflow-templates"); }

    /**
     * Ensure the data directory and workflow-templates subdirectory exist,
     * then perform a one-time migration of any legacy {@code src/<file>}
     * state into {@code ~/.conload/}. Safe to call on every startup.
     */
    public static void bootstrap() {
        try {
            Files.createDirectories(DATA_DIR);
            Files.createDirectories(workflowTemplatesDir());
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not create data dir " + DATA_DIR, e);
            return;
        }
        migrateLegacy("config.txt",             configTxt());
        migrateLegacy("projects.json",           projectsJson());
        migrateLegacy("quick-actions.json",     quickActionsJson());
        migrateLegacy("open_tabs.json",         openTabsJson());
        migrateLegacy("terminal_pids.json",     terminalPidsJson());
        migrateLegacy("opencode-sessions.json", opencodeSessionsJson());
        migrateLegacy("workflow-settings.json", workflowSettingsJson());
        migrateLegacyDir(Path.of("src", "workflow-templates"), workflowTemplatesDir());
    }

    /**
     * Copy {@code src/<name>} to {@code target} only when the target is
     * absent and the legacy source exists. Never overwrites existing state.
     */
    private static void migrateLegacy(String legacyName, Path target) {
        if (Files.exists(target)) return;
        Path legacy = LEGACY_SRC_DIR.resolve(legacyName);
        if (Files.isRegularFile(legacy)) {
            try {
                Files.copy(legacy, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("Migrated legacy state: " + legacy + " -> " + target);
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to migrate " + legacy + " to " + target, e);
            }
        }
    }

    /** Copy each {@code .md} override from a legacy {@code src/workflow-templates/}
     *  directory that does not yet exist in the target. */
    private static void migrateLegacyDir(Path legacyDir, Path targetDir) {
        if (!Files.isDirectory(legacyDir)) return;
        try (var stream = Files.list(legacyDir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().endsWith(".md"))
                  .forEach(legacy -> {
                      Path target = targetDir.resolve(legacy.getFileName());
                      if (Files.exists(target)) return;
                      try {
                          Files.copy(legacy, target, StandardCopyOption.REPLACE_EXISTING);
                          log.info("Migrated legacy template: " + legacy + " -> " + target);
                      } catch (IOException e) {
                          log.log(Level.WARNING, "Failed to migrate " + legacy, e);
                      }
                  });
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to list legacy dir " + legacyDir, e);
        }
    }
}
