package com.conload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a Confluence space (global or personal).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfluenceSpace(
        String id,
        String key,
        String name,
        String type   // "global" or "personal"
) {
    @Override public String toString() { return name + " [" + key + "]"; }
}
