package com.conload.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree node representing a page and its recursive children.
 * Used for building the full page hierarchy before writing files.
 */
public class PageNode {

    private final ConfluencePage page;
    private final int depth;
    private String hierarchyPrefix;  // e.g. "A", "A--", "A---"
    private final List<PageNode> children = new ArrayList<>();

    public PageNode(ConfluencePage page, int depth, String hierarchyPrefix) {
        this.page = page;
        this.depth = depth;
        this.hierarchyPrefix = hierarchyPrefix;
    }

    public ConfluencePage getPage() { return page; }
    public int getDepth() { return depth; }
    public String getHierarchyPrefix() { return hierarchyPrefix; }
    public List<PageNode> getChildren() { return children; }

    public void addChild(PageNode child) {
        children.add(child);
    }
}

