package com.conload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Confluence page with metadata and storage-format body.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfluencePage(
        String id,
        String title,
        String type,
        Body body,
        @JsonProperty("_links") Links links,
        Space space
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Storage storage) {
        public Storage getStorage() { return storage; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Storage(String value, String representation) {
        public String getValue()         { return value; }
        public String getRepresentation() { return representation; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Links(String self, String webui) {
        public String getSelf()  { return self; }
        public String getWebui() { return webui; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Space(String key, String name) {
        public String getKey()  { return key; }
        public String getName() { return name; }
    }

    // Bean-style accessors (kept for callers still using getXxx())
    public String    getId()    { return id; }
    public String    getTitle() { return title; }
    public String    getType() { return type; }
    public Body      getBody()  { return body; }
    public Links     getLinks() { return links; }
    public Space     getSpace() { return space; }

    public String getStorageValue() {
        if (body != null && body.storage != null) {
            return body.storage.value;
        }
        return "";
    }

    @Override
    public String toString() {
        return "ConfluencePage{id='" + id + "', title='" + title + "'}";
    }
}
