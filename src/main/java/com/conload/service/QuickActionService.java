package com.conload.service;

import com.conload.model.QuickAction;
import com.conload.util.AppPaths;
import com.conload.util.Json;
import com.conload.util.JsonStore;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** CRUD for QuickAction, persisted to {@code ~/.conload/quick-actions.json}
 *  (see {@link AppPaths#quickActionsJson()}). */
public class QuickActionService {

    private static final String DEFAULTS_INDEX_RESOURCE = "/defaults/prompts/index.json";
    private static final String DEFAULTS_PROMPTS_PREFIX = "/defaults/prompts/";

    private final JsonStore<List<QuickAction>> store =
        JsonStore.list(AppPaths.quickActionsJson().toString(), QuickAction.class);

    public List<QuickAction> load() {
        if (!Files.exists(store.path())) return loadDefaults();
        return store.load();
    }

    /** Seed defaults from the committed per-prompt classpath resources
     *  (enumerated by {@code defaults/prompts/index.json}) when the live file
     *  is missing. */
    private List<QuickAction> loadDefaults() {
        try (var indexIs = QuickActionService.class.getResourceAsStream(DEFAULTS_INDEX_RESOURCE)) {
            if (indexIs == null) return new ArrayList<>();
            List<String> fileNames = Json.MAPPER.readValue(indexIs,
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            List<QuickAction> out = new ArrayList<>();
            for (String name : fileNames) {
                try (var is = QuickActionService.class.getResourceAsStream(DEFAULTS_PROMPTS_PREFIX + name)) {
                    if (is != null) out.add(Json.MAPPER.readValue(is, QuickAction.class));
                }
            }
            return out;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void save(List<QuickAction> list) throws IOException {
        store.save(list);
    }

    public QuickAction add(String name, String command) throws IOException {
        return add(name, command, null);
    }

    public QuickAction add(String name, String command, List<String> parameters) throws IOException {
        List<QuickAction> list = load();
        QuickAction qa = new QuickAction(UUID.randomUUID().toString(), name, command, parameters);
        list.add(qa);
        save(list);
        return qa;
    }

    public void update(QuickAction updated) throws IOException {
        List<QuickAction> list = load();
        list.replaceAll(q -> q.getId().equals(updated.getId()) ? updated : q);
        save(list);
    }

    public void delete(String id) throws IOException {
        List<QuickAction> list = load();
        list.removeIf(q -> q.getId().equals(id));
        save(list);
    }
}
