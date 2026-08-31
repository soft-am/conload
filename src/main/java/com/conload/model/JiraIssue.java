package com.conload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Jackson-mapped model for a Jira REST API v3 issue response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssue(
        String id,
        String key,
        Fields fields
) {
    public String getId()     { return id; }
    public String getKey()    { return key; }
    public Fields getFields() { return fields; }

    // ─────────────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fields(
            String       summary,
            JsonNode     description,                   // ADF node
            @JsonProperty("issuetype") NamedObject issueType,
            NamedObject  status,
            NamedObject  priority,
            NamedObject  parent,
            UserObject   assignee,
            UserObject   reporter,
            List<String>      labels,
            List<NamedObject> components,
            List<NamedObject> fixVersions,
            List<SubTask>     subtasks,
            @JsonProperty("issuelinks") List<IssueLink> issuelinks,
            List<Attachment>  attachment,
            CommentContainer  comment,
            String            created,
            String            updated,
            String            duedate
    ) {
        public Fields {
            labels      = labels      != null ? labels      : List.of();
            components  = components  != null ? components  : List.of();
            fixVersions = fixVersions != null ? fixVersions : List.of();
            subtasks    = subtasks    != null ? subtasks    : List.of();
            issuelinks  = issuelinks  != null ? issuelinks  : List.of();
            attachment  = attachment  != null ? attachment  : List.of();
        }

        public String   getSummary()     { return summary != null ? summary : ""; }
        public JsonNode getDescription() { return description; }
        public NamedObject getIssueType()   { return issueType; }
        public NamedObject getStatus()      { return status; }
        public NamedObject getPriority()    { return priority; }
        public NamedObject getParent()      { return parent; }
        public UserObject  getAssignee()    { return assignee; }
        public UserObject  getReporter()    { return reporter; }
        public List<String>      getLabels()      { return labels; }
        public List<NamedObject> getComponents()  { return components; }
        public List<NamedObject> getFixVersions() { return fixVersions; }
        public List<SubTask>     getSubtasks()    { return subtasks; }
        public List<IssueLink>   getIssuelinks()  { return issuelinks; }
        public List<Attachment>  getAttachment()  { return attachment; }
        public CommentContainer  getComment()     { return comment; }
        public String getCreated() { return created != null && created.length() >= 10 ? created.substring(0, 10) : ""; }
        public String getUpdated() { return updated != null && updated.length() >= 10 ? updated.substring(0, 10) : ""; }
        public String getDuedate() { return duedate != null && duedate.length() >= 10 ? duedate.substring(0, 10) : ""; }
    }

    // ─────────────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NamedObject(String name) {
        public String getName() { return name != null ? name : ""; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserObject(String displayName, String emailAddress) {
        public String getDisplayName()  { return displayName  != null ? displayName  : ""; }
        public String getEmailAddress() { return emailAddress != null ? emailAddress : ""; }
    }

    // ─────────────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubTask(
            String        id,
            String        key,
            SubTaskFields fields
    ) {
        public String        getKey()    { return key != null ? key : ""; }
        public SubTaskFields getFields() { return fields; }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record SubTaskFields(
                String     summary,
                NamedObject status,
                @JsonProperty("issuetype") NamedObject issueType
        ) {
            public String      getSummary()   { return summary  != null ? summary  : ""; }
            public NamedObject getStatus()    { return status; }
            public NamedObject getIssueType() { return issueType; }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IssueLink(
            LinkType    type,
            LinkedIssue inwardIssue,
            LinkedIssue outwardIssue
    ) {
        public LinkType    getType()         { return type; }
        public LinkedIssue getInwardIssue()  { return inwardIssue; }
        public LinkedIssue getOutwardIssue() { return outwardIssue; }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record LinkType(String name, String inward, String outward) {
            public String getName()    { return name    != null ? name    : ""; }
            public String getInward()  { return inward  != null ? inward  : ""; }
            public String getOutward() { return outward != null ? outward : ""; }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record LinkedIssue(String key, LinkedIssueFields fields) {
            public String            getKey()    { return key != null ? key : ""; }
            public LinkedIssueFields getFields() { return fields; }

            @JsonIgnoreProperties(ignoreUnknown = true)
            public record LinkedIssueFields(
                    String     summary,
                    NamedObject status,
                    @JsonProperty("issuetype") NamedObject issueType
            ) {
                public String      getSummary()   { return summary  != null ? summary  : ""; }
                public NamedObject getStatus()    { return status; }
                public NamedObject getIssueType() { return issueType; }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attachment(
            String id,
            String filename,
            String content,    // download URL
            String mimeType
    ) {
        public String getId()       { return id       != null ? id       : ""; }
        public String getFilename() { return filename != null ? filename : ""; }
        public String getContent()  { return content  != null ? content  : ""; }
        public String getMimeType() { return mimeType != null ? mimeType : ""; }
    }

    // ─────────────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommentContainer(List<Comment> comments) {
        public CommentContainer {
            comments = comments != null ? comments : List.of();
        }
        public List<Comment> getComments() { return comments; }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Comment(
                UserObject author,
                JsonNode   body,     // ADF
                String     created
        ) {
            public UserObject getAuthor()  { return author; }
            public JsonNode   getBody()    { return body; }
            public String     getCreated() { return created != null && created.length() >= 10
                                                     ? created.substring(0, 10) : ""; }
        }
    }
}
