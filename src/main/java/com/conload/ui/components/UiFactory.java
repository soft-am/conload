package com.conload.ui.components;

import com.conload.ui.Icons;
import com.conload.ui.Theme;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Window;

public final class UiFactory {

    private UiFactory() {
    }

    public static Button actionButton(String name) {
        Button b = new Button(name);
        b.getStyleClass().addAll("app-button");
        return b;
    }

    public static Button accentButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("app-button", "accent");
        return b;
    }

    public static Button errorButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("app-button", "danger");
        return b;
    }

    public static Button iconButton(String icon, String variant) {
        Button b = new Button(icon);
        b.getStyleClass().addAll("icon-button", "icon");
        if ("error".equals(variant)) {
            b.getStyleClass().add("error");
        } else if ("secondary".equals(variant) || "muted".equals(variant)) {
            b.getStyleClass().add("secondary");
        } else if ("accent".equals(variant)) {
            b.getStyleClass().add("accent");
        }
        return b;
    }

    public static TextField textField(String text, double width) {
        TextField f = new TextField(text);
        f.setPrefWidth(width);
        f.getStyleClass().add("input");
        return f;
    }

    public static TextArea commandArea(String text) {
        TextArea a = new TextArea(text);
        a.setPrefRowCount(1);
        a.setPrefHeight(30);
        a.setWrapText(false);
        a.getStyleClass().addAll("input", "text-area");
        return a;
    }

    public static TextField darkTextField(String prompt) {
        TextField tf = new TextField();
        styleInput(tf, prompt);
        return tf;
    }

    public static <T extends TextInputControl> void styleInput(T field, String prompt) {
        if (field instanceof TextField tf) tf.setPromptText(prompt);
        else if (field instanceof PasswordField pf) pf.setPromptText(prompt);
        field.getStyleClass().add("input");
    }

    public static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }

    public static HBox row(Node... nodes) {
        HBox r = new HBox(4, nodes);
        r.setAlignment(Pos.CENTER_LEFT);
        r.setPadding(Theme.PAD_ROW_FLAT);
        return r;
    }

    // ── Layout helpers (centralised styling boilerplate) ──────────────────────

    /** Returns 90% of the owner window width (or primary screen width if
     *  {@code owner} is null or not yet showing). Never smaller than 600. */
    public static double widePopupWidth(Window owner) {
        double refW = (owner != null && owner.isShowing())
                ? owner.getWidth()
                : Screen.getPrimary().getVisualBounds().getWidth();
        return Math.max(refW * 0.9, 600);
    }

    /** Returns 85% of the owner window height (or primary screen height if
     *  {@code owner} is null or not yet showing). Never smaller than 400. */
    public static double widePopupHeight(Window owner) {
        double refH = (owner != null && owner.isShowing())
                ? owner.getHeight()
                : Screen.getPrimary().getVisualBounds().getHeight();
        return Math.max(refH * 0.85, 400);
    }

    /** A {@link Region} configured as a horizontal grow-spacer that pushes
     *  trailing nodes to the right inside an {@link HBox}. */
    public static Region hSpacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    /** Standard app scroll pane: {@code fitToWidth}, no horizontal bar, themed. */
    public static ScrollPane scrollable(Region content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Theme.classes(sp, Theme.CL_SCROLL);
        return sp;
    }

    /** Toggle a node in/out of both layout and rendering in one call
     *  (replaces the {@code setManaged+setVisible} pair). */
    public static void setVisible(Node n, boolean visible) {
        if (n == null) return;
        n.setVisible(visible);
        n.setManaged(visible);
    }

    /** Remove a node from layout and rendering. */
    public static void hide(Node n) { setVisible(n, false); }

    /** Add a node back into layout and rendering. */
    public static void show(Node n) { setVisible(n, true); }

    /** Builds the standard modal-header row: {@code "[icon]  Title" + spacer +
     *  close button}, with {@code panel-border-bottom} and the default header
     *  padding/alignment.
     *
     *  @param titleText the header label text (without icon).
     *  @param icon      an {@link Icons} constant (e.g. {@link Icons#SETTINGS}),
     *                   or {@code null} for no icon prefix.
     *  @param onClose   callback invoked when the close button is clicked;
     *                   pass {@code null} for no action. */
    public static HBox headerRow(String titleText, String icon, Runnable onClose) {
        return headerRow(titleText, icon, onClose, (Node[]) null);
    }

    /** Header-row overload that inserts extra trailing nodes (e.g. a refresh
     *  button) between the spacer and the close button. */
    public static HBox headerRow(String titleText, String icon, Runnable onClose, Node... trailing) {
        Label header = new Label((icon == null ? "" : icon + "  ") + titleText);
        Theme.classes(header, Theme.CL_TITLE_SMALL);
        Button closeBtn = new Button(Icons.CLOSE);
        Theme.classes(closeBtn, Theme.CL_CLOSE_BTN);
        closeBtn.setTooltip(new Tooltip("Close"));
        if (onClose != null) closeBtn.setOnAction(e -> onClose.run());
        Region spacer = hSpacer();
        HBox row;
        if (trailing != null && trailing.length > 0) {
            Node[] children = new Node[3 + trailing.length];
            children[0] = header;
            children[1] = spacer;
            System.arraycopy(trailing, 0, children, 2, trailing.length);
            children[children.length - 1] = closeBtn;
            row = new HBox(8, children);
        } else {
            row = new HBox(8, header, spacer, closeBtn);
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(Theme.PAD_HEADER);
        Theme.classes(row, Theme.CL_HEADER_ROW);
        return row;
    }

    /** Header-row variant that omits the in-content close button. Use this
     *  for decorated {@code Stage} popups whose native OS title bar already
     *  supplies a close button, so there is no duplicate close. Trailing
     *  controls (e.g. a refresh button) are placed after the spacer. */
    public static HBox headerRow(String titleText, String icon, Node... trailing) {
        Label header = new Label((icon == null ? "" : icon + "  ") + titleText);
        Theme.classes(header, Theme.CL_TITLE_SMALL);
        Region spacer = hSpacer();
        HBox row;
        if (trailing != null && trailing.length > 0) {
            Node[] children = new Node[2 + trailing.length];
            children[0] = header;
            children[1] = spacer;
            System.arraycopy(trailing, 0, children, 2, trailing.length);
            row = new HBox(8, children);
        } else {
            row = new HBox(8, header, spacer);
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(Theme.PAD_HEADER);
        Theme.classes(row, Theme.CL_HEADER_ROW);
        return row;
    }

    /** Builds the standard modal-footer row with {@code panel-border-top} and
     *  the default footer padding/alignment. The caller supplies its own
     *  nodes (typically spacer + action buttons). */
    public static HBox footerRow(Node... children) {
        HBox row = new HBox(8, children);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(Theme.PAD_FOOTER);
        Theme.classes(row, Theme.CL_FOOTER_ROW);
        return row;
    }

    /** Rebuilds a list of removable source-folder rows inside {@code container}.
     *  Each row shows a document icon + absolute path + a small red × button
     *  that removes the source from the {@code sources} list and rebuilds.
     *  Previously duplicated as {@code refreshImportSources} and
     *  {@code refreshSourcesList} (byte-for-byte identical except method name). */
    public static void fillFolderList(javafx.scene.layout.VBox container,
                                       java.util.List<java.nio.file.Path> sources) {
        container.getChildren().clear();
        for (java.nio.file.Path src : sources) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Label lbl = new Label(Icons.DOCUMENT + " " + src.toAbsolutePath().toString());
            lbl.getStyleClass().add("secondary");
            Region sp = hSpacer();
            Button rm = new Button(Icons.CLOSE);
            rm.getStyleClass().addAll("remove-button", "small", "icon");
            rm.setOnAction(e -> { sources.remove(src); fillFolderList(container, sources); });
            row.getChildren().addAll(lbl, sp, rm);
            container.getChildren().add(row);
        }
    }
}
