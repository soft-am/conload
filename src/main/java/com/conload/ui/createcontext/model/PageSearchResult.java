package com.conload.ui.createcontext.model;

import com.conload.service.RecursivePageProcessor;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/** Represents a single Confluence page returned by a keyword or URL search. */
public class PageSearchResult {
    private final BooleanProperty selected = new SimpleBooleanProperty(true);
    public final String pageId, title, spaceKey, spaceTitle;

    public PageSearchResult(String pageId, String title, String spaceKey, String spaceTitle) {
        this.pageId     = pageId;
        this.title      = title;
        this.spaceKey   = spaceKey   != null ? spaceKey   : "";
        this.spaceTitle = spaceTitle != null ? spaceTitle : "";
    }

    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected()    { return selected.get(); }
    public String  getTitle()      { return title; }
    public String  getSpaceKey()   { return spaceKey; }
    public String  getSpaceTitle() { return spaceTitle; }

    public RecursivePageProcessor.SelectedPage toSelectedPage() {
        return new RecursivePageProcessor.SelectedPage(pageId, title, false);
    }
}

