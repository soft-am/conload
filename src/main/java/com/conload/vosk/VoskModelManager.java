package com.conload.vosk;

import com.conload.service.SpeechRecognitionService.ModelLang;
import javafx.application.Platform;
import com.sun.jna.Pointer;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Owns Vosk native library probing, model loading, unloading, and downloads. */
public class VoskModelManager {
    private volatile ModelLang currentLang = ModelLang.EN;
    private Pointer modelPtr;
    private volatile boolean modelLoaded;
    private volatile boolean nativeLibOk;
    private volatile boolean nativeLibChecked;
    private volatile boolean downloading;
    private volatile boolean preloading;

    private synchronized void probeNativeLib() {
        if (nativeLibChecked) return;
        nativeLibChecked = true;
        try {
            VoskLib lib = VoskLib.INSTANCE;
            lib.vosk_set_log_level(2);
            nativeLibOk = true;
        } catch (Throwable t) {
            System.err.println("[VOSK] Native library load failed: " + t.getMessage());
            nativeLibOk = false;
        }
    }

    public boolean isNativeLibOk() {
        if (!nativeLibChecked) probeNativeLib();
        return nativeLibOk;
    }

    public ModelLang getCurrentLang() {
        return currentLang;
    }

    public void setCurrentLang(ModelLang lang) {
        if (lang == currentLang) return;
        currentLang = lang;
        modelLoaded = false;
        unloadModel();
    }

    public File findModelDir() {
        return resolveModelDir(currentLang);
    }

    public File findModelDir(ModelLang lang) {
        return resolveModelDir(lang);
    }

    /**
     * Resolve the model directory for a language:
     * 1. User-local dir ({@code ~/.conload/vosk-model/<lang>})
     * 2. Dev source-tree dir ({@code src/main/resources/vosk-model/<lang>})
     * 3. Extract bundled model from JAR classpath resources → user-local dir
     */
    private File resolveModelDir(ModelLang lang) {
        File dir = lang.modelDir();
        if (dir.isDirectory() && isModelDir(dir)) return dir;

        File devLangDir = new File("src/main/resources/vosk-model", lang.code);
        if (devLangDir.isDirectory() && isModelDir(devLangDir)) return devLangDir;
        File devBaseDir = new File("src/main/resources/vosk-model");
        if (devBaseDir.isDirectory() && isModelDir(devBaseDir)) return devBaseDir;

        File extracted = extractBundledModel(lang);
        if (extracted != null) return extracted;

        return null;
    }

    /**
     * Extract a bundled Vosk model from JAR classpath resources to the user-local
     * model directory.  Called on first run from a distributed JAR when no model
     * is found on disk.
     */
    private File extractBundledModel(ModelLang lang) {
        File destDir = lang.modelDir();
        String prefix = "vosk-model/" + lang.code + "/";
        try {
            CodeSource cs = getClass().getProtectionDomain().getCodeSource();
            if (cs == null) return null;
            File source = new File(cs.getLocation().toURI());
            if (!source.isFile()) return null;
            try (JarFile jar = new JarFile(source)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith(prefix) && !entry.isDirectory()) {
                        File dest = new File(destDir, name.substring(prefix.length()));
                        Files.createDirectories(dest.getParentFile().toPath());
                        try (InputStream in = jar.getInputStream(entry)) {
                            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
            if (destDir.isDirectory() && isModelDir(destDir)) return destDir;
        } catch (Exception e) {
            System.err.println("[VOSK] Failed to extract bundled " + lang.displayName + " model: " + e.getMessage());
        }
        return null;
    }

    private boolean isModelDir(File dir) {
        return Files.exists(dir.toPath().resolve("conf"))
            || Files.exists(dir.toPath().resolve("am"));
    }

    public boolean isModelAvailable() {
        return findModelDir() != null;
    }

    public boolean isModelAvailable(ModelLang lang) {
        return findModelDir(lang) != null;
    }

    public String getModelStatus() {
        File dir = findModelDir();
        return dir == null ? "No " + currentLang.displayName + " model"
            : "Model: " + currentLang.displayName + " (" + currentLang.code + ")";
    }

    public boolean isDownloading() {
        return downloading;
    }

    public void downloadModel(ModelLang lang, Consumer<String> statusConsumer, Consumer<Boolean> onComplete) {
        if (downloading) return;
        downloading = true;
        Thread thread = new Thread(() -> download(lang, statusConsumer, onComplete),
            "vosk-model-download-" + lang.code);
        thread.setDaemon(true);
        thread.start();
    }

    private void download(ModelLang lang, Consumer<String> statusConsumer, Consumer<Boolean> onComplete) {
        Path tempZip = null;
        try {
            Platform.runLater(() -> statusConsumer.accept("Downloading " + lang.displayName + " model (~40 MB)…"));
            tempZip = Files.createTempFile("vosk-model-" + lang.code, ".zip");
            long totalBytes = 0;
            try (InputStream in = URI.create(lang.downloadUrl).toURL().openStream(); var out = Files.newOutputStream(tempZip)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    totalBytes += count;
                    if (totalBytes % (1024 * 1024) == 0) {
                        final long mb = totalBytes / (1024 * 1024);
                        Platform.runLater(() -> statusConsumer.accept("Downloading… " + mb + " MB"));
                    }
                }
            }
            final long finalMb = totalBytes / (1024 * 1024);
            Platform.runLater(() -> statusConsumer.accept("Downloaded " + finalMb + " MB — extracting…"));
            File destDir = lang.modelDir();
            Files.createDirectories(destDir.toPath());
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String name = entry.getName();
                    int slash = name.indexOf('/');
                    if (slash >= 0) name = name.substring(slash + 1);
                    if (name.isBlank()) continue;
                    Path dest = destDir.toPath().resolve(name);
                    Files.createDirectories(dest.getParent());
                    try (var out = Files.newOutputStream(dest)) {
                        int count;
                        while ((count = zis.read(buffer)) != -1) out.write(buffer, 0, count);
                    }
                }
            }
            modelLoaded = false;
            unloadModel();
            final boolean ok = isModelDir(destDir);
            Platform.runLater(() -> {
                statusConsumer.accept(ok ? lang.displayName + " model ready" : "Download finished but model looks incomplete");
                onComplete.accept(ok);
            });
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                statusConsumer.accept("Download failed: " + e.getMessage());
                onComplete.accept(false);
            });
        } finally {
            downloading = false;
            if (tempZip != null) try { Files.deleteIfExists(tempZip); } catch (Exception ignored) {}
        }
    }

    public synchronized Pointer ensureModelLoaded() throws Exception {
        if (modelLoaded) return modelPtr;
        probeNativeLib();
        if (!nativeLibOk) throw new RuntimeException("libvosk could not be loaded");
        File modelDir = findModelDir();
        if (modelDir == null) throw new RuntimeException(currentLang.displayName + " model not found. Download it first.");
        modelPtr = VoskLib.INSTANCE.vosk_model_new(modelDir.getAbsolutePath());
        if (modelPtr == null) throw new RuntimeException("Failed to load Vosk model from " + modelDir);
        modelLoaded = true;
        return modelPtr;
    }

    public synchronized void unloadModel() {
        if (modelPtr == null) return;
        try { VoskLib.INSTANCE.vosk_model_free(modelPtr); } catch (Exception ignored) {}
        modelPtr = null;
    }

    public synchronized void preloadModel() {
        if (modelLoaded || preloading) return;
        preloading = true;
        Thread thread = new Thread(() -> {
            try { ensureModelLoaded(); }
            catch (Exception e) { System.err.println("[VOSK] preload failed: " + e.getMessage()); }
            finally { preloading = false; }
        }, "vosk-model-preload");
        thread.setDaemon(true);
        thread.start();
    }
}
