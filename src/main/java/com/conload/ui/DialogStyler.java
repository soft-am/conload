package com.conload.ui;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Applies the theme stylesheet to dialog panes.
 * Dialog panes are in separate windows/stages that don't inherit the scene
 * stylesheet, so the stylesheet must be added explicitly. All visual styling
 * is done via CSS selectors in theme.css (.dialog-pane, .dialog-pane .button, etc.).
 *
 * <p>Also centers the dialog over its owner window (or the primary stage)
 * so popups don't appear far away from the app.</p>
 */
public final class DialogStyler {

    private static final String STYLESHEET = Theme.stylesheetUrl();

    /**
     * Add the theme stylesheet to the dialog's pane so CSS rules apply,
     * and center the dialog over its owner window.
     */
    public static void style(Dialog<?> dialog) {
        if (dialog == null) return;
        DialogPane pane = dialog.getDialogPane();
        if (!pane.getStylesheets().contains(STYLESHEET)) {
            pane.getStylesheets().add(STYLESHEET);
        }
        pane.getStyleClass().removeAll("theme-dark", "theme-light");
        pane.getStyleClass().add(Theme.currentMode().styleClass());
        pane.getStyleClass().add("dialog-pane");

        // Center the dialog over its owner window (or the primary stage as fallback)
        Window owner = dialog.getOwner();
        if (owner == null) {
            // No owner set — bind to the primary stage so the dialog appears
            // centered over the app window instead of far away on the screen.
            Stage primary = Stage.getWindows().stream()
                .filter(w -> w.isShowing() && w instanceof Stage)
                .map(w -> (Stage) w)
                .findFirst()
                .orElse(null);
            if (primary != null) {
                dialog.initOwner(primary);
                owner = primary;
            }
        }
    }

    private DialogStyler() {}
}
