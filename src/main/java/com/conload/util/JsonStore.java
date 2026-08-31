package com.conload.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Generic JSON-backed persistence for a single typed value.
 *
 * <p>Replaces the duplicated {@code load()/save()} boilerplate that previously
 * appeared in {@code ProjectService}, {@code QuickActionService},
 * {@code OpenTabsService}, {@code PidRegistryService}, and
 * {@code OpencodeSessionService} — each carrying its own {@code ObjectMapper}
 * field, its own {@code Files.exists} guard, and its own catch-and-log.
 *
 * <p>Usage:
 * <pre>{@code
 * private final JsonStore<List<Project>> store =
 *     JsonStore.list(AppPaths.projectsJson().toString(), Project.class);
 *
 * public List<Project> loadProjects()        { return store.load(); }
 * public void saveProjects(List<Project> ps) throws IOException { store.save(ps); }
 * }</pre>
 *
 * @param <T> the persisted value type
 */
public final class JsonStore<T> {

    private final Path path;
    private final JavaType type;
    private final Supplier<T> emptyDefault;

    private JsonStore(String path, JavaType type, Supplier<T> emptyDefault) {
        this.path = Path.of(path);
        this.type = type;
        this.emptyDefault = emptyDefault;
    }

    /** Convenience for storing a single object (not a list). */
    public static <T> JsonStore<T> of(String path, Class<T> type, Supplier<T> emptyDefault) {
        return new JsonStore<>(path, Json.MAPPER.constructType(type), emptyDefault);
    }

    /** Convenience for storing a {@code List<T>}. */
    public static <T> JsonStore<List<T>> list(String path, Class<T> elementType) {
        JavaType listType = Json.MAPPER.getTypeFactory()
            .constructCollectionType(List.class, elementType);
        return new JsonStore<>(path, listType, ArrayList::new);
    }

    /** Advanced constructor for arbitrary {@link TypeReference} types
     *  (e.g. {@code Map<String, X>}). */
    public static <T> JsonStore<T> of(String path, TypeReference<T> typeRef, Supplier<T> emptyDefault) {
        return new JsonStore<>(path, Json.MAPPER.constructType(typeRef), emptyDefault);
    }

    /** Load the value, or the empty default if the file is missing or unreadable. */
    public T load() {
        if (!Files.exists(path)) return emptyDefault.get();
        try {
            T value = Json.MAPPER.readValue(path.toFile(), type);
            return value != null ? value : emptyDefault.get();
        } catch (IOException e) {
            return emptyDefault.get();
        }
    }

    /** Overwrite the file with {@code value} (creates parent directories). */
    public void save(T value) throws IOException {
        Json.write(path, value);
    }

    /** Delete the file if it exists. */
    public void clear() throws IOException {
        Files.deleteIfExists(path);
    }

    /** The configured path (mostly for diagnostics). */
    public Path path() {
        return path;
    }
}
