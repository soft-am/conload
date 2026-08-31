package com.conload.ui.createcontext;

import com.conload.ui.Icons;
import com.conload.ui.Theme;
import com.conload.ui.components.SvgIcon;
import com.conload.ui.components.UiFactory;
import com.conload.ui.createcontext.model.CriteriaType;
import com.conload.ui.createcontext.model.SearchCriterion;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Builds and owns the editable criteria rows used by the search popup. */
public final class SearchCriteriaPane {
    private final VBox rows = new VBox(4);
    private final Consumer<List<SearchCriterion>> criteriaChanged;
    private final Supplier<Boolean> searchRunning;
    private List<SearchCriterion> currentCriteria = List.of();

    public SearchCriteriaPane(Consumer<List<SearchCriterion>> criteriaChanged,
                              Supplier<Boolean> searchRunning) {
        this.criteriaChanged = criteriaChanged;
        this.searchRunning = searchRunning;
        rows.setPadding(Theme.PAD_ROW_FLAT);
        addRow();
    }

    public VBox rows() { return rows; }

    public HBox createSearchCriteriaRow() {
        ProgressIndicator spinner = new ProgressIndicator(-1);
        spinner.setPrefSize(14, 14);
        Theme.classes(spinner, Theme.CL_PROGRESS_ACCENT);
        UiFactory.hide(spinner);
        ComboBox<String> source = createSourceBox();
        ComboBox<CriteriaType> type = createTypeBox();
        TextField value = UiFactory.darkTextField("value…");
        HBox.setHgrow(value, Priority.ALWAYS);
        TextField extra = UiFactory.darkTextField("repo  owner/repo");
        extra.setPrefWidth(170);
        UiFactory.hide(extra);
        type.getItems().addAll(CriteriaType.CONFLUENCE);
        type.setValue(CriteriaType.CONFLUENCE);
        value.setPromptText(promptForType(CriteriaType.CONFLUENCE));
        wireSelectors(source, type, value, extra);
        value.textProperty().addListener((obs, old, next) -> synchronize());
        extra.textProperty().addListener((obs, old, next) -> synchronize());

        Button remove = new Button(Icons.CLOSE);
        remove.getStyleClass().addAll("remove-button", "icon");
        remove.setOnAction(e -> {
            rows.getChildren().remove(remove.getParent());
            synchronize();
        });
        HBox row = new HBox(6, spinner, source, type, value, extra, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 0, 3, 0));
        row.getProperties().put("sourceBox", source);
        row.getProperties().put("typeBox", type);
        row.getProperties().put("valueField", value);
        row.getProperties().put("extraField", extra);
        row.getProperties().put("spinner", spinner);
        return row;
    }

    public void addRow() {
        rows.getChildren().add(createSearchCriteriaRow());
        synchronize();
    }

    public void clear() { rows.getChildren().clear(); }

    public void synchronize() {
        List<SearchCriterion> criteria = new java.util.ArrayList<>();
        for (javafx.scene.Node node : rows.getChildren()) {
            if (!(node instanceof HBox row)) continue;
            Object sourceObject = row.getProperties().get("sourceBox");
            Object typeObject = row.getProperties().get("typeBox");
            Object valueObject = row.getProperties().get("valueField");
            Object extraObject = row.getProperties().get("extraField");
            if (!(sourceObject instanceof ComboBox<?> source) || !(valueObject instanceof TextField value)) continue;
            String text = value.getText().strip();
            if (text.isBlank()) continue;
            String selectedSource = String.valueOf(source.getValue());
            CriteriaType selectedType;
            if (selectedSource.contains("Everywhere")) selectedType = CriteriaType.EVERYWHERE;
            else if (typeObject instanceof ComboBox<?> box && box.getValue() instanceof CriteriaType type) selectedType = type;
            else continue;
            String extra = extraObject instanceof TextField field && selectedType == CriteriaType.GITHUB_COMMIT
                    ? field.getText().strip() : "";
            SearchCriterion criterion = new SearchCriterion(selectedType, text, extra);
            if (row.getProperties().get("spinner") instanceof ProgressIndicator pi) criterion.spinner = pi;
            if (row.getProperties().get("badge") instanceof Label badge) criterion.badge = badge;
            criteria.add(criterion);
        }
        currentCriteria = List.copyOf(criteria);
        criteriaChanged.accept(currentCriteria);
    }

    public void updateSearchButtonState(Button search, Button stop) {
        if (search == null) return;
        search.setDisable(currentCriteria.isEmpty()
                || stop != null && !stop.isDisable() || Boolean.TRUE.equals(searchRunning.get()));
    }

    private ComboBox<String> createSourceBox() {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().addAll(Icons.TARGET + "  Everywhere", "  Confluence", "  Jira", "  GitHub");
        box.setValue("  Confluence");
        box.setPrefWidth(170);
        box.setCellFactory(list -> sourceCategoryCell());
        box.setButtonCell(sourceCategoryCell());
        return box;
    }

    private ListCell<String> sourceCategoryCell() {
        return new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setGraphic(empty || item == null ? null : sourceCategoryGraphic(item));
            }
        };
    }

    private static SvgIcon sourceCategoryGraphic(String item) {
        if (item == null) return null;
        String path = item.contains("Confluence") ? "/images/confluence-logo.svg"
                : item.contains("Jira") ? "/images/jira-logo.svg"
                : item.contains("GitHub") ? "/images/github-logo.svg" : null;
        if (path == null) return null;
        SvgIcon icon = new SvgIcon(path, 14, "-app-panel");
        icon.setMouseTransparent(true);
        icon.setPickOnBounds(false);
        return icon;
    }

    private ComboBox<CriteriaType> createTypeBox() {
        ComboBox<CriteriaType> box = new ComboBox<>();
        box.setPrefWidth(215);
        box.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(CriteriaType type) { return type == null ? "" : subtypeLabel(type); }
            @Override public CriteriaType fromString(String value) { return null; }
        });
        return box;
    }

    private void wireSelectors(ComboBox<String> source, ComboBox<CriteriaType> type,
                               TextField value, TextField extra) {
        source.setOnAction(e -> {
            String selected = source.getValue();
            boolean everywhere = selected != null && selected.contains("Everywhere");
            type.getItems().setAll(criteriaTypesForSource(selected));
            extra.clear();
            UiFactory.hide(extra);
            value.clear();
            if (!everywhere && type.getItems().size() == 1) {
                CriteriaType only = type.getItems().get(0);
                type.setValue(only);
                value.setPromptText(promptForType(only));
                value.setDisable(false);
                UiFactory.setVisible(extra, only == CriteriaType.GITHUB_COMMIT);
            } else {
                type.setValue(null);
                type.setDisable(everywhere);
                value.setDisable(!everywhere);
                value.setPromptText(everywhere ? "search everywhere keyword…" : "value…");
            }
            synchronize();
        });
        type.setOnAction(e -> {
            CriteriaType selected = type.getValue();
            value.setPromptText(selected == null ? "value…" : promptForType(selected));
            value.setDisable(selected == null);
            UiFactory.setVisible(extra, selected == CriteriaType.GITHUB_COMMIT);
            synchronize();
        });
    }

    private List<CriteriaType> criteriaTypesForSource(String source) {
        if (source == null || source.contains("Everywhere")) return List.of();
        if (source.contains("Confluence")) return List.of(CriteriaType.CONFLUENCE);
        if (source.contains("Jira")) return List.of(CriteriaType.JIRA);
        return List.of(CriteriaType.GITHUB_COMMIT, CriteriaType.GITHUB_COMMIT_URL,
                CriteriaType.GITHUB_ACTION_URL, CriteriaType.GITHUB_PR);
    }

    private String promptForType(CriteriaType type) {
        return switch (type) {
            case EVERYWHERE -> "search everywhere keyword…";
            case CONFLUENCE -> "Confluence page URL or search keyword";
            case JIRA -> "Jira issue URL, key (PROJ-123), or keyword";
            case GITHUB_COMMIT -> "commit message keyword";
            case GITHUB_COMMIT_URL -> "GitHub commit URL  (https://github.com/owner/repo/commit/<sha>)";
            case GITHUB_ACTION_URL -> "GitHub Action URL  (https://github.com/owner/repo/actions/runs/<runId>/job/<jobId>)";
            case GITHUB_PR -> "GitHub PR URL";
        };
    }

    private String subtypeLabel(CriteriaType type) {
        return switch (type) {
            case EVERYWHERE -> "Search (Everywhere)";
            case CONFLUENCE -> "URL or Keyword";
            case JIRA -> "URL, Key, or Keyword";
            case GITHUB_COMMIT -> "Commit Search (keyword)";
            case GITHUB_COMMIT_URL -> "Commit (URL)";
            case GITHUB_ACTION_URL -> "Action Run (URL)";
            case GITHUB_PR -> "PR (URL)";
        };
    }
}
