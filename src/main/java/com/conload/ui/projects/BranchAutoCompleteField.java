package com.conload.ui.projects;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.util.List;
import java.util.Locale;

/**
 * A lightweight, reliable branch-search autocomplete: a {@link TextField}
 * whose text filters a {@link ListView} of branch short-names shown in a
 * {@link Popup}. Commits the full remote-tracking ref (e.g.
 * {@code origin/feature/foo}) as the selected value.
 *
 * <p>Replaces the fragile editable-{@code ComboBox} + mutate-{@code getItems()}
 * + {@code show()}-from-editor-listener pattern (which silently failed to
 * display the suggestion dropdown in {@link CreateWorktreeDialog}).
 *
 * <p>Keyboard: ↑/↓ to move selection, Enter to commit the highlighted row,
 * Escape to close. Mouse: click a row to commit. Focus loss hides the popup.
 */
public final class BranchAutoCompleteField {

    /** Max visible rows in the suggestion dropdown (applied via cell-height
     *  on the ListView, which has no {@code setVisibleRowCount}). */
    private static final int MAX_VISIBLE_ROWS = 8;

    private final TextField editor = new TextField();
    private final StringProperty value = new SimpleStringProperty("");
    private final Popup popup = new Popup();
    private final ListView<String> list = new ListView<>();
    private final ObservableList<String> items = FXCollections.observableArrayList();

    private List<String> allBranches = List.of();
    private boolean suppressFilter;

    public BranchAutoCompleteField() {
        editor.setPromptText("search branch…");
        list.setItems(items);
        list.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        list.setFixedCellSize(26);
        list.setPrefHeight(Region.USE_COMPUTED_SIZE);
        list.setMaxHeight(MAX_VISIBLE_ROWS * 26);
        list.getStyleClass().add("combo-box-popup-list");
        popup.getContent().setAll(list);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setAutoFix(true);

        editor.textProperty().addListener((obs, o, n) -> {
            if (suppressFilter) return;
            filterAndShow(n);
        });
        editor.focusedProperty().addListener((obs, hadFocus, hasFocus) -> {
            if (!hasFocus) hidePopup();
        });
        editor.addEventFilter(KeyEvent.KEY_PRESSED, e -> onKeyPressed(e));
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 1) commitSelection();
        });
    }

    /** The editable text field for layout. */
    public TextField editor() { return editor; }

    /** The committed remote-tracking ref (blank until a row is picked). */
    public StringProperty valueProperty() { return value; }

    public String getValue() { return value.get(); }

    public void setDisable(boolean d) { editor.setDisable(d); }

    public boolean isDisabled() { return editor.isDisabled(); }

    public void requestFocus() { editor.requestFocus(); }

    /** Replace the full set of branches (full remote refs). */
    public void setBranches(List<String> branches) {
        allBranches = branches != null ? branches : List.of();
    }

    /** Select the first branch in the list (used for auto-pick on load). */
    public void selectFirst() {
        if (allBranches.isEmpty()) return;
        String first = allBranches.get(0);
        setFromRef(first);
    }

    private void filterAndShow(String query) {
        if (query == null) query = "";
        String q = query.toLowerCase(Locale.ROOT);
        List<String> filtered;
        if (q.isBlank()) {
            filtered = allBranches;
        } else {
            filtered = allBranches.stream()
                    .filter(ref -> shortName(ref).toLowerCase(Locale.ROOT).contains(q)
                            || ref.toLowerCase(Locale.ROOT).contains(q))
                    .toList();
        }
        items.setAll(filtered.stream().map(BranchAutoCompleteField::shortName).toList());
        if (filtered.isEmpty()) {
            hidePopup();
            return;
        }
        list.getSelectionModel().select(0);
        list.scrollTo(0);
        showPopup();
    }

    private void showPopup() {
        if (popup.isShowing()) return;
        Window win = editor.getScene() != null ? editor.getScene().getWindow() : null;
        if (win == null) return;
        var bounds = editor.localToScreen(editor.getBoundsInLocal());
        if (bounds == null) return;
        popup.show(editor, bounds.getMinX(), bounds.getMaxY() + 2);
    }

    private void hidePopup() {
        if (popup.isShowing()) popup.hide();
    }

    private void onKeyPressed(KeyEvent e) {
        if (e.getCode() == KeyCode.DOWN) {
            e.consume();
            if (!popup.isShowing()) { filterAndShow(editor.getText()); return; }
            int i = list.getSelectionModel().getSelectedIndex();
            if (i < 0) i = -1;
            if (i + 1 < items.size()) {
                list.getSelectionModel().select(i + 1);
                list.scrollTo(i + 1);
            }
        } else if (e.getCode() == KeyCode.UP) {
            e.consume();
            if (!popup.isShowing()) return;
            int i = list.getSelectionModel().getSelectedIndex();
            if (i > 0) {
                list.getSelectionModel().select(i - 1);
                list.scrollTo(i - 1);
            }
        } else if (e.getCode() == KeyCode.ENTER) {
            if (popup.isShowing() && !items.isEmpty()) {
                e.consume();
                commitSelection();
            }
        } else if (e.getCode() == KeyCode.ESCAPE) {
            if (popup.isShowing()) {
                e.consume();
                hidePopup();
            }
        }
    }

    /** Commit the currently-highlighted list row → set editor text + value. */
    private void commitSelection() {
        String shortName = list.getSelectionModel().getSelectedItem();
        if (shortName == null) return;
        // Find the first full ref whose short name matches (short names are
        // branch names; multiple remotes can share one — pick the first).
        String ref = allBranches.stream()
                .filter(r -> shortName(r).equals(shortName))
                .findFirst().orElse(shortName);
        setFromRef(ref);
        hidePopup();
    }

    /** Update both editor (short) and value (full ref) without re-triggering
     *  the filter. */
    private void setFromRef(String ref) {
        suppressFilter = true;
        try {
            editor.setText(shortName(ref));
            editor.positionCaret(editor.getText().length());
            value.set(ref);
        } finally {
            suppressFilter = false;
        }
    }

    /** {@code "origin/feature/foo"} → {@code "feature/foo"} (strips the first
     *  path segment — the remote name). */
    static String shortName(String ref) {
        if (ref == null || ref.isBlank()) return "";
        int i = ref.indexOf('/');
        return (i >= 0 && i < ref.length() - 1) ? ref.substring(i + 1) : ref;
    }

    /** Make the editor's padding match a combo box editor (so the row aligns
     *  with the other fields). */
    public void padLikeCombo() {
        editor.setPadding(new Insets(5, 8, 5, 8));
    }
}
