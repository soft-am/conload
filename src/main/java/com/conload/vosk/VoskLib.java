package com.conload.vosk;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * Minimal JNA binding for {@code libvosk} that declares only the symbols
 * actually used by this application.
 *
 * <p>The official {@code org.vosk.LibVosk} class declares
 * {@code vosk_recognizer_set_grm}, which does <strong>not</strong> exist in the
 * shipped {@code libvosk.dylib}.  When JNA's {@link Native#register} hits a
 * missing symbol the <em>entire</em> registration fails, making every Vosk
 * call throw {@code UnsatisfiedLinkError}.  This interface sidesteps that by
 * omitting the missing symbol, so the library loads cleanly.</p>
 *
 * <p>Usage: {@code VoskLib.INSTANCE.vosk_model_new("/path/to/model")}.</p>
 */
public interface VoskLib extends Library {

    VoskLib INSTANCE = Native.load("vosk", VoskLib.class);

    // ── Log level ──────────────────────────────────────────────────────────
    void vosk_set_log_level(int level);

    // ── Model ──────────────────────────────────────────────────────────────
    Pointer vosk_model_new(String path);
    void    vosk_model_free(Pointer model);

    // ── Recognizer ─────────────────────────────────────────────────────────
    Pointer vosk_recognizer_new(Pointer model, float sampleRate);

    boolean vosk_recognizer_accept_waveform(Pointer recognizer, byte[] data, int len);

    String  vosk_recognizer_result(Pointer recognizer);
    String  vosk_recognizer_partial_result(Pointer recognizer);
    String  vosk_recognizer_final_result(Pointer recognizer);

    void    vosk_recognizer_free(Pointer recognizer);
}
