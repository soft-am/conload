package com.conload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Confluence attachment (image, file, diagram export).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfluenceAttachment(
        String id,
        String title,
        String type,
        Metadata metadata,
        @JsonProperty("_links") Links links
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(String mediaType) {
        public String getMediaType() { return mediaType; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Links(String download, String self) {
        public String getDownload() { return download; }
        public String getSelf()      { return self; }
    }

    // Bean-style accessors
    public String   getId()       { return id; }
    public String   getTitle()    { return title; }
    public String   getType()     { return type; }
    public Metadata getMetadata() { return metadata; }
    public Links    getLinks()    { return links; }
}
