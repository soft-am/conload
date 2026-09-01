package com.conload.ui.createcontext.presenter;

import com.conload.github.GitHubClient;
import com.conload.ui.Icons;
import com.conload.ui.Theme;
import com.conload.ui.components.UiFactory;
import com.conload.ui.createcontext.GitHubActionResult;
import com.conload.ui.createcontext.GitHubPrResult;
import com.conload.ui.createcontext.model.JiraTableItem;
import com.conload.ui.createcontext.model.PageSearchResult;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.util.function.Function;

/** Stateless factories for search-result TableViews, columns, and detail viewers.
 *  Extracted from {@code ContextAcquisitionController} to keep it under the
 *  800-line limit. All methods are self-contained — no controller state. */
public final class SearchResultTables {
    private SearchResultTables() {}

    /** Generic checkbox column: 44px, click-to-toggle label. */
    public static <S> TableColumn<S, Boolean> checkboxColumn(Function<S, BooleanProperty> selectedProp) {
        TableColumn<S, Boolean> col = new TableColumn<>("");
        col.setMaxWidth(44); col.setMinWidth(44);
        col.setCellValueFactory(cd -> selectedProp.apply(cd.getValue()));
        col.setCellFactory(tc -> new TableCell<>() {
            private final Label lbl = new Label(Icons.UNCHECKED);
            {
                lbl.getStyleClass().addAll("bold", "icon");
                lbl.setOnMouseClicked(e -> {
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) return;
                    S item = getTableView().getItems().get(getIndex());
                    selectedProp.apply(item).set(!selectedProp.apply(item).get());
                });
            }
            @Override protected void updateItem(Boolean val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setGraphic(null); } else {
                    lbl.setText(val ? Icons.CHECKED : Icons.UNCHECKED);
                    S item = getTableView().getItems().get(getIndex());
                    selectedProp.apply(item).addListener((o, ov, nv) -> lbl.setText(nv ? Icons.CHECKED : Icons.UNCHECKED));
                    setGraphic(lbl);
                }
            }
        });
        return col;
    }

    /** Generic table column: PropertyValueFactory-backed, Label-headered, transparent-empty-cell. */
    public static <S, T> TableColumn<S, T> textColumn(String header, String prop, double width) {
        TableColumn<S, T> col = new TableColumn<>();
        col.setCellValueFactory(new PropertyValueFactory<>(prop));
        col.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); getStyleClass().add("bg-transparent"); } else {
                    setText(item.toString());
                }
            }
        });
        Label h = new Label(header);
        h.getStyleClass().addAll("bold", "small");
        col.setGraphic(h); col.setText("");
        if (width > 0) { col.setMinWidth(width); col.setMaxWidth(width); }
        return col;
    }

    public static TableView<PageSearchResult> buildConfSearchTable() {
        TableView<PageSearchResult> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add("table-border");
        table.getColumns().addAll(
                checkboxColumn(PageSearchResult::selectedProperty),
                textColumn("TITLE", "title", -1),
                textColumn("SPACE KEY", "spaceKey", 100),
                textColumn("SPACE NAME", "spaceTitle", 160));
        return table;
    }

    public static TableView<JiraTableItem> buildJiraTable() {
        TableView<JiraTableItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().addAll(
                checkboxColumn(JiraTableItem::selectedProperty),
                textColumn("KEY", "key", 110),
                textColumn("TYPE", "type", 90),
                textColumn("STATUS", "status", 120),
                textColumn("SUMMARY", "summary", -1));
        return table;
    }

    public static TableView<GitHubClient.GitCommit> buildCommitTable() {
        TableView<GitHubClient.GitCommit> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().addAll(
                textColumn("SHA", "shortSha", 70),
                textColumn("MESSAGE", "shortMessage", -1),
                textColumn("AUTHOR", "authorName", 140),
                textColumn("DATE", "committerDate", 130));
        return table;
    }

    public static TableView<GitHubActionResult> buildActionTable() {
        TableView<GitHubActionResult> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().addAll(
                checkboxColumn(GitHubActionResult::selectedProperty),
                textColumn("RUN", "runName", 200),
                textColumn("JOB", "jobName", 130),
                textColumn("STATUS", "status", 90),
                textColumn("CONCLUSION", "conclusion", 90));
        return table;
    }

    public static TableView<GitHubPrResult> buildPrTable() {
        TableView<GitHubPrResult> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().addAll(
                checkboxColumn(GitHubPrResult::selectedProperty),
                textColumn("#", "number", 50),
                textColumn("TITLE", "title", -1),
                textColumn("AUTHOR", "author", 140),
                textColumn("STATE", "state", 90));
        return table;
    }

    public static HBox buildSelectAllBar(Runnable selectAll, Runnable deselectAll) {
        Button selAllBtn = UiFactory.actionButton(Icons.CHECKED + "  ALL");
        Button deselAllBtn = UiFactory.actionButton(Icons.UNCHECKED + "  NONE");
        selAllBtn.setOnAction(e -> selectAll.run());
        deselAllBtn.setOnAction(e -> deselectAll.run());
        HBox bar = new HBox(6, selAllBtn, deselAllBtn);
        bar.setPadding(Theme.PAD_ROW_FLAT);
        return bar;
    }

    /** Populates the detail area with the commit's diff JSON. */
    public static void showCommitDetail(TextArea detailArea, GitHubClient.GitCommit commit) {
        if (detailArea == null || commit == null) return;
        detailArea.setText(commit.toDiffJson());
    }

    /** Populates the detail area with steps + logs of a GitHub Action run. */
    public static void showActionLogDetail(TextArea detailArea, GitHubActionResult run) {
        if (detailArea == null || run == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("Run:       ").append(run.getRunName()).append("\n");
        sb.append("Workflow:  ").append(run.getWorkflowName()).append("\n");
        sb.append("Status:    ").append(run.getStatus())
          .append("  /  ").append(run.getConclusion()).append("\n");
        sb.append("URL:       ").append(run.getHtmlUrl()).append("\n\n");
        sb.append("── STEPS ──\n");
        if (run.getStepsJson() != null && !run.getStepsJson().isBlank()
                && !run.getStepsJson().equals("[]")) {
            try {
                var steps = new com.fasterxml.jackson.databind.ObjectMapper().readTree(run.getStepsJson());
                for (var s : steps) {
                    int num = s.path("number").asInt(0);
                    String name = s.path("name").asText("(unnamed)");
                    String status = s.path("status").asText("");
                    String conc = s.path("conclusion").asText("");
                    sb.append(String.format("  [%d] %-40s  %s/%s%n", num, name, status, conc));
                }
            } catch (Exception e) {
                sb.append("  (failed to parse steps)\n");
            }
        } else {
            sb.append("  (no steps available)\n");
        }
        sb.append("\n── RAW LOGS ──\n");
        String logs = run.getJobLogs();
        sb.append(logs.isBlank() ? "(no logs available for this job)" : logs);
        detailArea.setText(sb.toString());
    }
}
