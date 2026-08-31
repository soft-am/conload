package com.conload.ui.createcontext;

import com.conload.ui.components.UiFactory;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseEvent;

/**
 * Generic reusable collapsible result panel.
 * Wraps a TitledPane — hidden by default, shown when results arrive.
 * The title is updated to include the item count when shown.
 */
public class ResultPanel<T> {

    public final TitledPane       pane;
    public final Node             content;
    private final String          baseTitle;
    private final Label           titleLabel;

    public ResultPanel(String baseTitle, Node content) {
        this.baseTitle   = baseTitle;
        this.content     = content;

        titleLabel = new Label("  " + baseTitle);
        titleLabel.getStyleClass().add("bold");
        this.pane = new TitledPane("", content);
        pane.setGraphic(titleLabel);
        titleLabel.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            pane.setExpanded(!pane.isExpanded());
            event.consume();
        });
        pane.setExpanded(false);
        UiFactory.hide(pane);
        pane.setAnimated(true);
    }

    /** Show this panel, update the title with the item count, and expand it. */
    public void show(int count) {
        titleLabel.setText("  " + baseTitle + "  (" + count + ")");
        // Expanding an animated pane immediately after it was unmanaged can leave
        // its content at zero height. Open it without animation for the first
        // layout pass, then restore animation for subsequent user toggles.
        pane.setAnimated(false);
        UiFactory.show(pane);
        pane.setExpanded(true);
        pane.applyCss();
        pane.requestLayout();
        Platform.runLater(() -> {
            if (pane.isVisible()) {
                pane.setExpanded(true);
                pane.requestLayout();
                pane.setAnimated(true);
            }
        });
    }

    /** Hide this panel. */
    public void hide() {
        pane.setAnimated(false);
        pane.setExpanded(false);
        UiFactory.hide(pane);
    }
}
