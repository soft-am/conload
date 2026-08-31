package com.conload.ui.createcontext.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Represents a single node in the Confluence page tree view. */
public class PageTreeItem {
    public final String          pageId;
    public final StringProperty  title    = new SimpleStringProperty();
    public final BooleanProperty selected = new SimpleBooleanProperty(false);

    public PageTreeItem(String pageId, String title) {
        this.pageId = pageId;
        this.title.set(title);
    }
}

