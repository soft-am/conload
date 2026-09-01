package com.conload.ui.terminal.session;

import com.conload.model.CliTypeDefinition;
import com.conload.sessionsprocessing.CliSession;
import com.conload.sessionsprocessing.SessionProcessor;
import com.conload.ui.Icons;
import com.conload.ui.Theme;
import com.conload.ui.components.AppErrorNotifier;
import com.conload.ui.components.UiFactory;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** The sessions browser for a terminal CLI with a JSON session list.
 *  Supports multiple CLI types via a filter dropdown (hidden when only one
 *  session-capable CLI is configured). */
public final class SessionBrowserPopup {
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Stage owner;
    private final List<CliTypeDefinition> availableClis;
    private final Function<String, SessionProcessor> serviceFor;
    private final String initialCliType;
    private final Supplier<CliTypeDefinition> cliDefinition;
    private final Supplier<String> activeSessionId;
    private final BiConsumer<String, String> resume;
    private final Supplier<String> projectColor;
    private final BiConsumer<CliSession, Button> export;
    private Stage popup;
    private TableView<CliSession> table;
    private ComboBox<CliTypeDefinition> cliFilter;
    private Button refreshButton;
    private Label loadingLabel;

    public SessionBrowserPopup(Stage owner, List<CliTypeDefinition> availableClis,
            Function<String, SessionProcessor> serviceFor, String initialCliType,
            Supplier<CliTypeDefinition> cliDefinition, Supplier<String> activeSessionId,
            BiConsumer<String, String> resume, Supplier<String> projectColor,
            BiConsumer<CliSession, Button> export) {
        this.owner = owner;
        this.availableClis = availableClis;
        this.serviceFor = serviceFor;
        this.initialCliType = initialCliType;
        this.cliDefinition = cliDefinition;
        this.activeSessionId = activeSessionId;
        this.resume = resume;
        this.projectColor = projectColor;
        this.export = export;
    }

    public void show() {
        if (popup != null && popup.isShowing()) { popup.toFront(); return; }
        popup = new Stage();
        if (owner != null) popup.initOwner(owner);
        popup.setTitle("Sessions");
        popup.setMinWidth(800); popup.setMinHeight(600);

        Button refresh = new Button(Icons.REFRESH);
        Theme.classes(refresh, Theme.CL_CLOSE_BTN);
        refresh.setTooltip(new Tooltip("Refresh session list"));
        refreshButton = refresh;
        loadingLabel = new Label("Loading sessions…");
        loadingLabel.getStyleClass().addAll("hint", "small");
        StackPane placeholder = new StackPane(loadingLabel);
        placeholder.setPadding(new Insets(20));
        table = createTable();
        table.setPlaceholder(placeholder);
        cliFilter = buildCliFilter();
        Buttons buttons = createButtons();
        HBox header = buildHeader(refresh);
        VBox root = new VBox(0, header, table, buttons.footer());
        root.getStyleClass().addAll("bg-app", "popup-window-root");
        popup.setScene(new Scene(root, Math.max(UiFactory.widePopupWidth(owner), 800),
            Math.max(UiFactory.widePopupHeight(owner), 600)));
        Theme.apply(root);
        popup.setOnCloseRequest(e -> { popup = null; table = null; });
        popup.show();
        installFetch(refresh, loadingLabel);
    }

    /** Builds the CLI filter dropdown — hidden (unmanaged) when only one
     *  session-capable CLI is available. Selection triggers a re-fetch. */
    private ComboBox<CliTypeDefinition> buildCliFilter() {
        ComboBox<CliTypeDefinition> combo = new ComboBox<>();
        combo.getStyleClass().add("prompt-template-combo");
        combo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(CliTypeDefinition item, boolean empty) {
                super.updateItem(item, empty); setText(empty || item == null ? null : item.getLabel());
            }
        });
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(CliTypeDefinition item, boolean empty) {
                super.updateItem(item, empty); setText(empty || item == null ? null : item.getLabel());
            }
        });
        combo.getItems().addAll(availableClis);
        CliTypeDefinition initial = pickInitialCli();
        if (initial != null) combo.getSelectionModel().select(initial);
        combo.setOnAction(e -> { if (table != null && refreshButton != null) { table.getItems().clear(); installFetch(refreshButton, loadingLabel); } });
        UiFactory.setVisible(combo, availableClis.size() > 1);
        combo.setManaged(availableClis.size() > 1);
        return combo;
    }

    private CliTypeDefinition pickInitialCli() {
        if (initialCliType != null)
            for (CliTypeDefinition d : availableClis)
                if (initialCliType.equals(d.getDetectText())) return d;
        return availableClis.isEmpty() ? null : availableClis.get(0);
    }

    private HBox buildHeader(Button refresh) {
        Label title = new Label("Sessions");
        title.getStyleClass().addAll("title", "small");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, spacer, cliFilter, refresh);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 12, 8, 14));
        header.getStyleClass().add("panel-border-bottom");
        return header;
    }

    /** Returns the session processor for the currently selected CLI filter. */
    private SessionProcessor currentService() {
        CliTypeDefinition sel = cliFilter != null ? cliFilter.getValue() : null;
        String type = sel != null ? sel.getDetectText() : (initialCliType != null ? initialCliType : "opencode");
        return serviceFor.apply(type);
    }

    public void close() {
        if (popup != null) { popup.close(); popup = null; table = null; }
    }

    public boolean isShowing() { return popup != null && popup.isShowing(); }

    public void updateSessions(List<CliSession> sessions, boolean preserveSelection) {
        if (table == null) return;
        String selected = preserveSelection && table.getSelectionModel().getSelectedItem() != null
            ? table.getSelectionModel().getSelectedItem().getId() : null;
        String label = cliLabel();
        table.getItems().setAll(sessions.stream().map(s -> s.withCli(label)).toList());
        if (selected != null) table.getItems().stream().filter(s -> selected.equals(s.getId()))
            .findFirst().ifPresent(s -> table.getSelectionModel().select(s));
    }

    private TableView<CliSession> createTable() {
        TableView<CliSession> result = new TableView<>();
        result.getStyleClass().add("sessions-table");
        result.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        VBox.setVgrow(result, Priority.ALWAYS);
        result.getColumns().setAll(noColumn(), idColumn(), titleColumn(), cliColumn(),
            dateColumn("CREATED", 130, 110, s -> date(s.getCreated())),
            dateColumn("UPDATED", 90, 70, s -> relative(s.getUpdated())), pathColumn());
        result.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(CliSession item, boolean empty) {
                getStyleClass().removeAll("session-active"); super.updateItem(item, empty);
                if (!empty && item != null && activeSessionId.get() != null
                        && activeSessionId.get().equals(item.getId())) {
                    setStyle("-accent-color: " + projectColor.get() + ";");
                    getStyleClass().add("session-active");
                } else setStyle(null);
            }
        });
        return result;
    }

    private TableColumn<CliSession, String> noColumn() {
        TableColumn<CliSession, String> c = column("#", 40, 40); c.setMaxWidth(40);
        c.setCellFactory(x -> new TableCell<>() { protected void updateItem(String v, boolean e) {
            super.updateItem(v, e); setText(e ? null : String.valueOf(getIndex() + 1));
        }}); return c;
    }
    private TableColumn<CliSession, String> idColumn() {
        TableColumn<CliSession, String> c = column("SESSION ID", 150, 110);
        c.getStyleClass().add("session-meta");
        c.setCellValueFactory(x -> new javafx.beans.property.SimpleStringProperty(x.getValue().getId()));
        return c;
    }
    private TableColumn<CliSession, String> titleColumn() {
        TableColumn<CliSession, String> c = column("TITLE", 400, 160);
        c.setCellValueFactory(x -> new javafx.beans.property.SimpleStringProperty(title(x.getValue())));
        c.setCellFactory(x -> ellipsisCell()); return c;
    }
    private TableColumn<CliSession, String> cliColumn() {
        TableColumn<CliSession, String> c = column("CLI", 130, 100);
        c.setCellValueFactory(x -> new javafx.beans.property.SimpleStringProperty(x.getValue().getCli()));
        c.setCellFactory(x -> new TableCell<>() { protected void updateItem(String v, boolean e) {
            super.updateItem(v, e); setText(null); setGraphic(null);
            if (!e && v != null && !v.isBlank()) { Label badge = new Label(v); badge.getStyleClass().add("cli-badge"); setGraphic(badge); }
        }}); return c;
    }
    private TableColumn<CliSession, String> pathColumn() {
        TableColumn<CliSession, String> c = column("CONTEXT PATH", 240, 160); c.getStyleClass().add("session-meta");
        c.setCellValueFactory(x -> new javafx.beans.property.SimpleStringProperty(x.getValue().getDirectory()));
        c.setCellFactory(x -> ellipsisCell()); return c;
    }
    private TableColumn<CliSession, String> dateColumn(String title, int pref, int min,
            java.util.function.Function<CliSession, String> value) {
        TableColumn<CliSession, String> c = column(title, pref, min); c.getStyleClass().add("session-meta");
        c.setCellValueFactory(x -> new javafx.beans.property.SimpleStringProperty(value.apply(x.getValue()))); return c;
    }
    private TableColumn<CliSession, String> column(String title, int pref, int min) {
        TableColumn<CliSession, String> c = new TableColumn<>(title); c.setSortable(false); c.setReorderable(false);
        c.setPrefWidth(pref); c.setMinWidth(min); return c;
    }
    private TableCell<CliSession, String> ellipsisCell() { return new TableCell<>() { protected void updateItem(String v, boolean e) {
        super.updateItem(v, e); setText(e || v == null || v.isBlank() ? null : v); setTooltip(e || v == null ? null : new Tooltip(v)); setTextOverrun(OverrunStyle.ELLIPSIS);
    }}; }

    private record Buttons(Button export, Button resume, HBox footer) {}
    private Buttons createButtons() {
        Button export = UiFactory.actionButton("Export compacted context"), resumeButton = UiFactory.accentButton("Resume selected"), cancel = UiFactory.errorButton("Cancel");
        export.setDisable(true); resumeButton.setDisable(true);
        resumeButton.setOnAction(e -> {
            CliSession s = selected();
            CliTypeDefinition def = cliFilter != null ? cliFilter.getValue() : cliDefinition.get();
            if (s != null && def != null) {
                resume.accept(def.getDetectText(), s.getId());
                close();
            }
        });
        export.setOnAction(e -> { CliSession s = selected(); if (s != null) this.export.accept(s, export); }); cancel.setOnAction(e -> close());
        table.getSelectionModel().selectedItemProperty().addListener((o, old, s) -> {
            boolean has = s != null;
            resumeButton.setDisable(!has);
            export.setDisable(!has);
        });
        return new Buttons(export, resumeButton, UiFactory.footerRow(UiFactory.hSpacer(), export, resumeButton, cancel));
    }
    private CliSession selected() { return table.getSelectionModel().getSelectedItem(); }

    private void installFetch(Button refresh, Label loading) {
        Runnable fetch = () -> { refresh.setDisable(true); loading.setText(table.getItems().isEmpty() ? "Loading sessions…" : "Refreshing…");
            SessionProcessor svc = currentService();
            svc.fetchSessions(s -> Platform.runLater(() -> { refresh.setDisable(false); if (s.isEmpty()) { table.getItems().clear(); loading.setText("No sessions found"); } else updateSessions(s, false); }),
            error -> Platform.runLater(() -> {
                refresh.setDisable(false);
                loading.setText(error);
                AppErrorNotifier.report(sessionListError(svc, error));
                if (table.getItems().isEmpty()) {
                    List<CliSession> cached = svc.loadCachedSessions();
                    if (!cached.isEmpty()) updateSessions(cached, false);
                } else System.err.println("[SESSIONS] Refresh error: " + error);
            })); };
        refresh.setOnAction(e -> fetch.run()); fetch.run();
    }

    private static String sessionListError(SessionProcessor service, String error) {
        CliTypeDefinition def = service.definition();
        String name = def.getLabel().isBlank() ? def.getDetectText() : def.getLabel();
        return "Cannot list sessions for CLI \"" + name + "\" with command \""
                + def.getListCommand() + "\": " + error;
    }

    private String cliLabel() {
        CliTypeDefinition d = cliFilter != null ? cliFilter.getValue() : cliDefinition.get();
        return d != null && !d.getLabel().isBlank() ? d.getLabel() : "—";
    }
    private static String title(CliSession s) { String t = s.getTitle(); return t == null || t.isBlank() ? s.getMessage() : t; }
    private static String date(long ms) { if (ms <= 0) return ""; try { return DATE_FMT.format(Instant.ofEpochMilli(ms)); } catch (Exception e) { return ""; } }
    private static String relative(long ms) { if (ms <= 0) return ""; long diff = System.currentTimeMillis() - ms; if (diff < 0) return date(ms); long sec = diff / 1000; if (sec < 60) return "just now"; long min = sec / 60; if (min < 60) return min + "m ago"; long hr = min / 60; if (hr < 24) return hr + "h ago"; long d = hr / 24; return d < 7 ? d + "d ago" : date(ms); }
}
