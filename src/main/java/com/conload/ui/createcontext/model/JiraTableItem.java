package com.conload.ui.createcontext.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/** Represents a single Jira issue row shown in a table. */
public class JiraTableItem {
    private final BooleanProperty selected = new SimpleBooleanProperty(true);
    private final String key, type, status, summary;

    public JiraTableItem(String key, String type, String status, String summary) {
        this.key = key;
        this.type = type;
        this.status = status;
        this.summary = summary;
    }

    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected()  { return selected.get(); }
    public String  getKey()      { return key; }
    public String  getType()     { return type; }
    public String  getStatus()   { return status; }
    public String  getSummary()  { return summary; }
}

