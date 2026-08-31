package com.conload.ui.projects.sidebar;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Shared builder for sidebar section separator headers (WORKTREES, CODE, CONTEXT).
 * Produces one uniform layout: label text at the left, a single action button at
 * the far right, separated by an expanding rule. All headers share the same
 * {@code sidebar-section-header} CSS class and therefore the same width, height
 * and padding.
 */
public final class SidebarSectionHeader {
    private SidebarSectionHeader() { }

    /**
     * Builds a section header with a label and an optional trailing action.
     *
     * @param labelText the section label (e.g. "CODE")
     * @param action    the far-right button node, or {@code null} for a plain label row
     * @return the header row
     */
    public static HBox create(String labelText, Node action) {
        Label title = new Label(labelText);
        title.getStyleClass().add("sidebar-section-label");

        Region rule = new Region();
        rule.getStyleClass().add("sidebar-section-rule");
        HBox.setHgrow(rule, Priority.ALWAYS);

        HBox header = action != null
                ? new HBox(6, title, rule, action)
                : new HBox(6, title, rule);
        header.getStyleClass().add("sidebar-section-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }
}
