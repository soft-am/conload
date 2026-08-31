package com.conload.workflow;

import java.util.Map;

/**
 * Carries the user-supplied form values for a workflow run.
 * Values are keyed by {@link WorkflowFieldDefinition#key()} plus the
 * shared overrides ({@code githubOwnerRepo}, {@code confluenceDataSource}).
 */
public record WorkflowInputs(Map<String, String> values) {

    public WorkflowInputs {
        values = values != null ? Map.copyOf(values) : Map.of();
    }

    /** Returns the value for {@code key}, or empty string when absent. */
    public String get(String key) {
        return values.getOrDefault(key, "");
    }

    /** Returns the value for {@code key}, or {@code def} when absent/blank. */
    public String getOrDefault(String key, String def) {
        String v = values.get(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
