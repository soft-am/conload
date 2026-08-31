package com.conload.service;

import com.conload.vosk.VoskModelManager;
import com.conload.vosk.VoskRecorder;

import java.io.File;
import java.util.function.Consumer;

/**
 * Offline speech-to-text via Vosk — supports multiple language models.
 *
 * <p>This class is the public facade for model selection and recording. Model
 * management and recording lifecycle are implemented in the Vosk package.</p>
 */
public class SpeechRecognitionService {

    /** Base directory where all language models live.
     *  User-local so models work from a distributed JAR (CWD may be anywhere). */
    private static final File MODEL_BASE_DIR =
            new File(System.getProperty("user.home"), ".conload/vosk-model");

    /** Supported Vosk models. */
    public enum ModelLang {
        EN("en", "English",
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"),
        DE("de", "German",
            "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip");

        public final String code;
        public final String displayName;
        public final String downloadUrl;

        ModelLang(String code, String displayName, String downloadUrl) {
            this.code = code;
            this.displayName = displayName;
            this.downloadUrl = downloadUrl;
        }

        /** The directory where this language's model files live. */
        public File modelDir() {
            return new File(MODEL_BASE_DIR, code);
        }

        /** Toggle to the other language. */
        public ModelLang toggle() {
            return this == EN ? DE : EN;
        }
    }

    private final VoskModelManager modelManager = new VoskModelManager();
    private final VoskRecorder recorder = new VoskRecorder(modelManager);

    public boolean isNativeLibOk() {
        return modelManager.isNativeLibOk();
    }

    /** Returns the currently active model language. */
    public ModelLang getCurrentLang() {
        return modelManager.getCurrentLang();
    }

    /** Switch the active language and unload the current native model. */
    public void setCurrentLang(ModelLang lang) {
        modelManager.setCurrentLang(lang);
    }

    /** Find the model directory for the current language. */
    public File findModelDir() {
        return modelManager.findModelDir();
    }

    /** Find the model directory for a specific language. */
    public File findModelDir(ModelLang lang) {
        return modelManager.findModelDir(lang);
    }

    public boolean isModelAvailable() {
        return modelManager.isModelAvailable();
    }

    /** Check if a specific language model is available. */
    public boolean isModelAvailable(ModelLang lang) {
        return modelManager.isModelAvailable(lang);
    }

    public String getModelStatus() {
        return modelManager.getModelStatus();
    }

    public boolean isDownloading() {
        return modelManager.isDownloading();
    }

    /** Downloads a language model in the background. */
    public void downloadModel(ModelLang lang, Consumer<String> statusConsumer, Consumer<Boolean> onComplete) {
        modelManager.downloadModel(lang, statusConsumer, onComplete);
    }

    /** Eagerly loads the native model in the background. */
    public void preloadModel() {
        modelManager.preloadModel();
    }

    public void startRecording(Consumer<String> textConsumer, Consumer<String> statusConsumer) {
        recorder.startRecording(textConsumer, statusConsumer);
    }

    public void stopRecording() {
        recorder.stopRecording();
    }

    public boolean isRecording() {
        return recorder.isRecording();
    }
}
