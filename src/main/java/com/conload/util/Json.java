package com.conload.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared Jackson {@link ObjectMapper} singleton plus thin read/write helpers.
 *
 * <p>Constructing an {@code ObjectMapper} is non-trivial; the project previously
 * had ~13 separate {@code new ObjectMapper()} instances across services,
 * controllers, and converters. Use {@link #MAPPER} (or {@link #read}/{@link #write})
 * everywhere so configuration lives in exactly one place.
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@link SerializationFeature#INDENT_OUTPUT} — pretty-printed JSON
 *       (all on-disk JSON in this project is human-readable by design).
 *   <li>{@link com.fasterxml.jackson.databind.DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES}
 *       disabled — forward-compatible with new model fields.
 *   <li>Visibility: fields only (no getters) — supports both Java records and
 *       POJOs that don't bother with getters, with one consistent rule.
 * </ul>
 */
public final class Json {

    /** Shared, thread-safe, pre-configured mapper. */
    public static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private Json() {}

    /** Read a typed value from a file. */
    public static <T> T read(Path file, com.fasterxml.jackson.core.type.TypeReference<T> type) throws IOException {
        return MAPPER.readValue(file.toFile(), type);
    }

    /** Read a typed value from a string. */
    public static <T> T read(String json, Class<T> type) throws IOException {
        return MAPPER.readValue(json, type);
    }

    /** Serialize {@code value} to the given file (pretty-printed, UTF-8). */
    public static void write(Path file, Object value) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        MAPPER.writeValue(file.toFile(), value);
    }

    /** Serialize {@code value} to a pretty JSON string. */
    public static String toPretty(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize JSON: " + e.getMessage(), e);
        }
    }

    /** Parse a value, returning {@code null} on failure (never throws). */
    public static <T> T readOrNull(Path file, com.fasterxml.jackson.core.type.TypeReference<T> type) {
        try {
            return MAPPER.readValue(file.toFile(), type);
        } catch (IOException e) {
            return null;
        }
    }
}
