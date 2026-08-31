package com.conload.util;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-platform helper for opening files and directories in the OS default
 * application or a user-chosen application.
 *
 * <p>Dispatches to the correct command per OS:
 * <ul>
 *   <li>macOS &ndash; {@code open} / {@code open -a &lt;app&gt;} / {@code osascript}</li>
 *   <li>Linux &ndash; {@code xdg-open}</li>
 *   <li>Windows &ndash; {@code cmd /c start}</li>
 * </ul>
 *
 * <p>Falls back to {@link Desktop#open(File)} when the native command is unavailable.
 */
public final class PlatformFileOpener {

    private enum Os { MAC, LINUX, WINDOWS, OTHER }

    private static final Os CURRENT = detectOs();

    private PlatformFileOpener() { }

    private static Os detectOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) return Os.MAC;
        if (os.contains("win")) return Os.WINDOWS;
        if (os.contains("nux") || os.contains("nix")) return Os.LINUX;
        return Os.OTHER;
    }

    /** True when running on macOS (where the {@code open -a} app-chooser is supported). */
    public static boolean isMac() { return CURRENT == Os.MAC; }

    /**
     * Open a file or directory in the OS default application.
     *
     * @param target   the file to open; must not be {@code null}
     * @param workDir  working directory for the spawned process (may be {@code null})
     * @throws IOException if the OS command fails
     */
    public static void openFile(File target, File workDir) throws IOException {
        openWithApp(target, null, workDir);
    }

    /**
     * Open a file with a specific application (macOS only via {@code open -a}).
     * On non-macOS platforms this falls back to {@link #openFile(File, File)}.
     *
     * @param target   the file to open
     * @param appName  application name (macOS only); {@code null} or blank = default app
     * @param workDir  working directory (may be {@code null})
     * @throws IOException if the OS command fails
     */
    public static void openWithApp(File target, String appName, File workDir) throws IOException {
        List<String> command = buildCommand(target, appName);
        if (command == null) {
            Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
            if (desktop != null && desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.open(target);
                return;
            }
            throw new IOException("No file-open mechanism available on " + System.getProperty("os.name"));
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workDir != null) pb.directory(workDir);
        pb.start();
    }

    /**
     * Show the macOS "choose application" dialog and open the file with the chosen app.
     * On non-macOS this calls {@link #openFile(File, File)} as a graceful fallback.
     */
    public static void openWithChooser(File target, File workDir) throws IOException {
        if (CURRENT != Os.MAC) {
            openFile(target, workDir);
            return;
        }
        String script = "set chosenApp to choose application with prompt \"Select an application to open:\n"
                + target.getName().replace("\"", "\\\"") + "\"\n"
                + "tell application chosenApp to open POSIX file \"" + target.getAbsolutePath() + "\"";
        ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
        if (workDir != null) pb.directory(workDir);
        pb.start();
    }

    private static List<String> buildCommand(File target, String appName) {
        String path = target.getAbsolutePath();
        return switch (CURRENT) {
            case MAC -> {
                List<String> cmd = new ArrayList<>();
                cmd.add("open");
                if (appName != null && !appName.isBlank()) { cmd.add("-a"); cmd.add(appName); }
                cmd.add(path);
                yield cmd;
            }
            case LINUX -> List.of("xdg-open", path);
            case WINDOWS -> {
                String quoted = "\"" + path + "\"";
                yield List.of("cmd", "/c", "start", "", quoted);
            }
            case OTHER -> null;
        };
    }
}
