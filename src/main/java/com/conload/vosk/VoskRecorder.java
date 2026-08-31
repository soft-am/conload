package com.conload.vosk;

import com.conload.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.jna.Pointer;
import javafx.application.Platform;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.function.Consumer;

/** Owns the microphone and recognizer lifecycle for a Vosk model manager. */
public class VoskRecorder {
    private static final float SAMPLE_RATE = 16000.0f;
    private final VoskModelManager modelManager;
    private Thread recordingThread;
    private volatile boolean recording;
    private TargetDataLine micLine;

    public VoskRecorder(VoskModelManager modelManager) {
        this.modelManager = modelManager;
    }

    public void startRecording(Consumer<String> textConsumer, Consumer<String> statusConsumer) {
        if (recording) return;
        recording = true;
        recordingThread = new Thread(() -> record(textConsumer, statusConsumer), "vosk-recording");
        recordingThread.setDaemon(true);
        recordingThread.start();
    }

    private void record(Consumer<String> textConsumer, Consumer<String> statusConsumer) {
        Pointer recognizerPtr = null;
        try {
            Platform.runLater(() -> statusConsumer.accept("Loading model…"));
            Pointer modelPtr = modelManager.ensureModelLoaded();
            recognizerPtr = VoskLib.INSTANCE.vosk_recognizer_new(modelPtr, SAMPLE_RATE);
            if (recognizerPtr == null) throw new RuntimeException("Recognizer creation failed");
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                Platform.runLater(() -> statusConsumer.accept("Microphone not available"));
                recording = false;
                return;
            }
            micLine = (TargetDataLine) AudioSystem.getLine(info);
            micLine.open(format);
            micLine.start();
            Platform.runLater(() -> statusConsumer.accept("Listening…"));
            byte[] buffer = new byte[4096];
            String lastPartial = "";
            while (recording) {
                int count = micLine.read(buffer, 0, buffer.length);
                if (count > 0) {
                    boolean complete = VoskLib.INSTANCE.vosk_recognizer_accept_waveform(recognizerPtr, buffer, count);
                    if (complete) {
                        String text = extractText(VoskLib.INSTANCE.vosk_recognizer_result(recognizerPtr), "text");
                        if (text != null && !text.isBlank()) { lastPartial = ""; publishText(textConsumer, text + " "); }
                    } else {
                        String partial = extractText(VoskLib.INSTANCE.vosk_recognizer_partial_result(recognizerPtr), "partial");
                        if (partial != null && !partial.isBlank() && !partial.equals(lastPartial)) {
                            lastPartial = partial;
                            Platform.runLater(() -> statusConsumer.accept(partial));
                        }
                    }
                }
            }
            String finalText = extractText(VoskLib.INSTANCE.vosk_recognizer_final_result(recognizerPtr), "text");
            if (finalText != null && !finalText.isBlank()) publishText(textConsumer, finalText + " ");
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> statusConsumer.accept("Error: " + e.getMessage()));
        } finally {
            recording = false;
            if (recognizerPtr != null) try { VoskLib.INSTANCE.vosk_recognizer_free(recognizerPtr); } catch (Exception ignored) {}
            if (micLine != null) { micLine.stop(); micLine.close(); micLine = null; }
        }
    }

    public void stopRecording() { recording = false; }

    public boolean isRecording() { return recording; }

    private void publishText(Consumer<String> consumer, String text) {
        Platform.runLater(() -> consumer.accept(text));
    }

    private String extractText(String json, String field) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode textNode = Json.MAPPER.readTree(json).get(field);
            if (textNode != null) return textNode.asText();
        } catch (Exception ignored) {}
        return null;
    }
}
