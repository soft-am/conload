package com.conload.workflow;

import com.conload.util.AppPaths;
import com.conload.util.JsonStore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists global workflow settings to {@code ~/.conload/workflow-settings.json}
 * (see {@link AppPaths#workflowSettingsJson()}).
 * <p>
 * The "full Confluence folder" is a global default (same for all workflows) —
 * a local folder path on disk injected into every workflow's prompt template
 * as {@code ${confluenceDataSource}}.
 */
public final class WorkflowSettingsService {

    private static final String SETTINGS_PATH = AppPaths.workflowSettingsJson().toString();
    private static final String KEY_FULL_CONF_FOLDER = "fullConfluenceFolder";

    @SuppressWarnings("unchecked")
    private final JsonStore<Map<String, String>> store =
            JsonStore.of(SETTINGS_PATH,
                         new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {},
                         LinkedHashMap::new);

    /** Load all settings. */
    public Map<String, String> loadAll() {
        return store.load();
    }

    /** Get the global full Confluence folder path (empty if unset). */
    public String getFullConfluenceFolder() {
        return loadAll().getOrDefault(KEY_FULL_CONF_FOLDER, "");
    }

    /** Set the global full Confluence folder path and persist. */
    public void setFullConfluenceFolder(String value) throws java.io.IOException {
        Map<String, String> all = new LinkedHashMap<>(loadAll());
        all.put(KEY_FULL_CONF_FOLDER, value != null ? value : "");
        store.save(all);
    }
}
