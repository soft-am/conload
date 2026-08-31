package com.conload.service;

import com.conload.model.AppConfig;
import com.conload.model.CliTypeDefinition;
import com.conload.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Loads and saves application config (username, API token, base URL) from/to
 * {@code ~/.conload/config.txt} (see {@link AppPaths#configTxt()}).
 */
public class ConfigService {

    private static final Logger log = Logger.getLogger(ConfigService.class.getName());
    private static final Path CONFIG_PATH = AppPaths.configTxt();

    public ConfigService() {}

    /** Load the saved Vosk speech language code ("en" or "de"). Defaults to "en". */
    public String getVoskLang() {
        Path path = CONFIG_PATH;
        if (!Files.exists(path)) return "en";
        try {
            Properties props = new Properties();
            try (var reader = Files.newBufferedReader(path)) {
                props.load(reader);
            }
            return props.getProperty("vosk.lang", "en").strip();
        } catch (IOException e) {
            return "en";
        }
    }

    /** Save the Vosk speech language code to config.txt (merges with existing keys). */
    public void setVoskLang(String langCode) {
        Path path = CONFIG_PATH;
        try {
            Properties props = new Properties();
            if (Files.exists(path)) {
                try (var reader = Files.newBufferedReader(path)) {
                    props.load(reader);
                }
            }
            props.setProperty("vosk.lang", langCode);
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path)) {
                props.store(writer, "Copilot Context Loader - credentials");
            }
        } catch (IOException e) {
            log.warning("Failed to save vosk.lang: " + e.getMessage());
        }
    }

    /**
     * Load config from {@code ~/.conload/config.txt}. Returns empty config if file missing.
     */
    public AppConfig loadConfig() {
        Path path = CONFIG_PATH;
        if (!Files.exists(path)) {
            log.info("Config file not found: " + CONFIG_PATH);
            return new AppConfig();
        }
        try {
            Properties props = new Properties();
            try (var reader = Files.newBufferedReader(path)) {
                props.load(reader);
            }
            String username     = props.getProperty("username",     "").strip();
            String token        = props.getProperty("token",        "").strip();
            String baseUrl      = props.getProperty("baseUrl",      "").strip();
            String githubToken  = props.getProperty("githubToken",  "").strip();
            String githubApiUrl = props.getProperty("githubApiUrl", "").strip();
            String defaultExportFolder = props.getProperty("defaultExportFolder", "").strip();
            String shell = props.getProperty("shell", "").strip();
            List<CliTypeDefinition> cliTypes = loadCliTypes(props);
            log.info("Config loaded from " + CONFIG_PATH);
            return new AppConfig(username, token, baseUrl, githubToken,
                    githubApiUrl, defaultExportFolder, shell, cliTypes);
        } catch (IOException e) {
            log.warning("Failed to load config: " + e.getMessage());
            return new AppConfig();
        }
    }

    /**
     * Save config to {@code ~/.conload/config.txt}.
     */
    public void saveConfig(AppConfig config) throws IOException {
        Path path = CONFIG_PATH;
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Properties props = new Properties();
        props.setProperty("username",    config.getUsername()    != null ? config.getUsername()    : "");
        props.setProperty("token",       config.getToken()       != null ? config.getToken()       : "");
        props.setProperty("baseUrl",     config.getBaseUrl()     != null ? config.getBaseUrl()     : "");
        props.setProperty("githubToken", config.getGithubToken() != null ? config.getGithubToken() : "");
        props.setProperty("githubApiUrl", config.getGithubApiUrl() != null ? config.getGithubApiUrl() : "");
        props.setProperty("defaultExportFolder", config.getDefaultExportFolder() != null ? config.getDefaultExportFolder() : "");
        props.setProperty("shell", config.getShell() != null ? config.getShell() : "");
        saveCliTypes(props, config.getCliTypes());
        try (var writer = Files.newBufferedWriter(path)) {
            props.store(writer, "Copilot Context Loader - credentials");
        }
        log.info("Config saved to " + CONFIG_PATH);
    }

    // ── CLI type definitions (indexed keys cli.N.*) ───────────────────────────

    /** Reads the {@code cli.count} + {@code cli.N.*} keys into a list. Returns
     *  an empty list if none are present (callers fall back to
     *  {@link CliTypeDefinition#defaults()}). Stale keys above {@code cli.count}
     *  are ignored. */
    private List<CliTypeDefinition> loadCliTypes(Properties props) {
        List<CliTypeDefinition> list = new ArrayList<>();
        int count;
        try { count = Integer.parseInt(props.getProperty("cli.count", "0").strip()); }
        catch (NumberFormatException e) { count = 0; }
        for (int i = 1; i <= count; i++) {
            String pfx = "cli." + i + ".";
            String label  = props.getProperty(pfx + "label",  "").strip();
            String detect = props.getProperty(pfx + "detect", "").strip();
            if (label.isBlank() && detect.isBlank()) continue; // skip empty rows
            String listCmd   = props.getProperty(pfx + "listCommand",   "").strip();
            String resumeCmd = props.getProperty(pfx + "resumeCommand", "").strip();
            String exportCmd = props.getProperty(pfx + "exportCommand", "").strip();
            list.add(new CliTypeDefinition(label, detect, listCmd, resumeCmd, exportCmd));
        }
        return list;
    }

    /** Writes {@code cli.count} + {@code cli.N.*} keys for the given list.
     *  Removes any stale {@code cli.N.*} keys above the new count so deleted
     *  rows don't linger. Preserves all other properties (credentials etc.). */
    private void saveCliTypes(Properties props, List<CliTypeDefinition> cliTypes) {
        // Remove stale cli.N.* keys from a previous save.
        for (String name : new ArrayList<>(props.stringPropertyNames())) {
            if (name.startsWith("cli.") && !name.equals("cli.count")) {
                props.remove(name);
            }
        }
        List<CliTypeDefinition> list = cliTypes != null ? cliTypes : new ArrayList<>();
        // Drop fully-blank rows so we don't persist empty definitions.
        List<CliTypeDefinition> clean = new ArrayList<>();
        for (CliTypeDefinition d : list) {
            if (d == null) continue;
            if (d.getLabel().isBlank() && d.getDetectText().isBlank()
                && d.getListCommand().isBlank() && d.getResumeCommand().isBlank()
                && d.getExportCommand().isBlank()) continue;
            clean.add(d);
        }
        props.setProperty("cli.count", String.valueOf(clean.size()));
        for (int i = 0; i < clean.size(); i++) {
            String pfx = "cli." + (i + 1) + ".";
            CliTypeDefinition d = clean.get(i);
            props.setProperty(pfx + "label",         d.getLabel());
            props.setProperty(pfx + "detect",        d.getDetectText());
            props.setProperty(pfx + "listCommand",   d.getListCommand());
            props.setProperty(pfx + "resumeCommand", d.getResumeCommand());
            props.setProperty(pfx + "exportCommand", d.getExportCommand());
        }
    }

    public String getConfigPath() {
        return CONFIG_PATH.toAbsolutePath().toString();
    }
}
