package com.conload.ui.components;

import com.conload.markdown.MarkdownToHtmlConverter;
import com.conload.ui.Icons;
import com.conload.ui.Theme;
import com.conload.util.BackgroundTasks;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;

/**
 * A large, scrollable popup that renders a Markdown file as styled HTML inside
 * a {@link WebView}. Used by the project-tree right-click "View Markdown"
 * action for {@code .md} files (including exported session transcripts and
 * {@code session_info.md}).
 *
 * <p>Two display modes:
 * <ul>
 *   <li><b>Read-only</b> (default): the rendered HTML is shown; the footer has
 *       "Open Externally" and "Close" buttons.</li>
 *   <li><b>Editable</b> ({@code editable=true}): an extra "Edit Source" button
 *       toggles between the rendered view and a {@link TextArea} raw-source
 *       editor. "Save" writes the edited content back to the file and
 *       re-renders. An optional {@code onSaved} callback is fired so callers
 *       (e.g. {@code ProjectFilesPane}) can refresh an inline preview.</li>
 * </ul>
 *
 * <p>Styling uses an inline HTML &lt;style&gt; block whose colours are derived
 * from the active {@link Theme} palette so the popup matches the app's dark
 * theme.
 */
public class MarkdownViewerPopup {

    private final Stage stage;
    private final File mdFile;
    private final boolean editable;
    private Runnable onSaved;

    private WebView webView;
    private TextArea sourceEditor;
    private boolean editMode = false;

    /** Create a read-only viewer popup owned by {@code owner}. */
    public MarkdownViewerPopup(File mdFile, Window owner) {
        this(mdFile, owner, false);
    }

    /**
     * Create a viewer popup.
     *
     * @param mdFile   the {@code .md} file to render
     * @param owner    owning window (for modality/centering), may be {@code null}
     * @param editable if {@code true}, a raw-source editor + save button is offered
     */
    public MarkdownViewerPopup(File mdFile, Window owner, boolean editable) {
        this.mdFile = mdFile;
        this.editable = editable;
        this.stage = new Stage();
        if (owner != null) stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle(mdFile.getName() + (editable ? "  —  View / Edit" : "  —  View"));
        buildUI();
    }

    /** Set a callback invoked after the file content is saved (edit mode). */
    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void show() {
        stage.show();
        loadContent();
    }

    // ── UI construction ──────────────────────────────────────────────────────

    private void buildUI() {
        // WebView for rendered output
        webView = new WebView();
        webView.setContextMenuEnabled(true);
        VBox.setVgrow(webView, Priority.ALWAYS);

        // Source editor (hidden until "Edit Source" toggled on)
        sourceEditor = new TextArea();
        sourceEditor.setWrapText(true);
        sourceEditor.getStyleClass().addAll("input", "text-area", "log-area", "mono-editor");
        sourceEditor.setVisible(false);
        sourceEditor.setManaged(false);
        VBox.setVgrow(sourceEditor, Priority.ALWAYS);

        // Both layered in a stack — only one visible at a time
        StackPane viewStack = new StackPane(webView, sourceEditor);
        VBox.setVgrow(viewStack, Priority.ALWAYS);

        // Footer buttons
        Button openExtBtn = new Button("Open Externally");
        openExtBtn.getStyleClass().add("app-button");
        openExtBtn.setOnAction(e -> openExternally());

        Button editBtn = null;
        Button saveBtn = null;
        Button cancelEditBtn = null;
        if (editable) {
            editBtn = new Button(Icons.EDIT + " Edit Source");
            editBtn.getStyleClass().add("app-button");
            editBtn.setOnAction(e -> toggleEditMode(true));

            saveBtn = new Button(Icons.CHECK + " Save");
            saveBtn.getStyleClass().addAll("app-button", "accent");
            saveBtn.setVisible(false);
            saveBtn.setManaged(false);
            saveBtn.setOnAction(e -> saveAndRender());

            cancelEditBtn = new Button("Cancel Edit");
            cancelEditBtn.getStyleClass().addAll("app-button", "danger");
            cancelEditBtn.setVisible(false);
            cancelEditBtn.setManaged(false);
            cancelEditBtn.setOnAction(e -> toggleEditMode(false));
        }

        Button footerClose = new Button("Close");
        footerClose.getStyleClass().addAll("app-button", "danger");
        footerClose.setOnAction(e -> stage.close());

        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(Theme.PAD_FOOTER);
        Theme.classes(footer, Theme.CL_FOOTER_ROW);
        footer.getChildren().add(UiFactory.hSpacer());
        footer.getChildren().add(openExtBtn);
        if (editBtn != null) footer.getChildren().addAll(editBtn, saveBtn, cancelEditBtn);
        footer.getChildren().add(footerClose);

        VBox root = new VBox(0, viewStack, footer);
        root.getStyleClass().addAll("bg-app", "popup-window-root", "md-viewer-root");
        root.setMinSize(800, 650);

        Scene scene = new Scene(root);
        Theme.apply(root);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> stage.close());

        // Size to 90% of owner window (or screen)
        stage.setOnShown(e -> {
            double w = Math.max(UiFactory.widePopupWidth(stage.getOwner()), 800);
            double h = Math.max(UiFactory.widePopupHeight(stage.getOwner()), 650);
            stage.setWidth(w);
            stage.setHeight(h);
        });
    }

    // ── Content rendering ───────────────────────────────────────────────────

    /** Reads the file and loads the rendered HTML into the WebView. */
    private void loadContent() {
        BackgroundTasks.<String>runOnFxThread("md-viewer-load",
            () -> {
                try {
                    return Files.readString(mdFile.toPath());
                } catch (Exception ex) {
                    return "# Error\n\nCould not read file:\n```\n" + ex.getMessage() + "\n```";
                }
            },
            md -> {
                final String html = buildHtmlDocument(mdFile.getName(), md);
                webView.getEngine().loadContent(html);
                sourceEditor.setText(md);
            },
            null
        );
    }

    /** Builds a full HTML document with dark-theme CSS and the rendered body. */
    private String buildHtmlDocument(String filename, String md) {
        String body = MarkdownToHtmlConverter.convert(md);
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8"/>
            <style>
              html, body {
                margin: 0; padding: 18px 26px;
                background: #18181B;
                color: #E8EAF0;
                font-family: -apple-system, "Segoe UI", system-ui, sans-serif;
                font-size: 14px; line-height: 1.6;
              }
              h1, h2, h3, h4, h5, h6 { color: #FFFFFF; line-height: 1.3; margin: 18px 0 8px; }
              h1 { font-size: 1.7em; border-bottom: 1px solid #2A2A32; padding-bottom: 6px; }
              h2 { font-size: 1.4em; border-bottom: 1px solid #2A2A32; padding-bottom: 5px; }
              h3 { font-size: 1.2em; }
              h4 { font-size: 1.05em; }
              p  { margin: 8px 0; }
              a  { color: #58A6FF; text-decoration: none; }
              a:hover { text-decoration: underline; }
              code {
                font-family: "Menlo", "SF Mono", Consolas, monospace;
                font-size: 0.9em;
                background: #26262E;
                color: #F0883E;
                padding: 2px 6px; border-radius: 4px;
              }
              pre {
                background: #1C1C20; border: 1px solid #2A2A32; border-radius: 6px;
                padding: 12px 14px; overflow-x: auto; margin: 10px 0;
              }
              pre code {
                background: transparent; color: #E8EAF0; padding: 0;
              }
              blockquote {
                border-left: 3px solid #569cd6;
                margin: 8px 0; padding: 4px 14px;
                color: #9CA3AF; background: #202024;
              }
              table {
                border-collapse: collapse; margin: 10px 0; width: 100%;
              }
              th, td {
                border: 1px solid #2A2A32; padding: 6px 12px; text-align: left;
              }
              th { background: #26262E; color: #FFFFFF; font-weight: 600; }
              tr:nth-child(even) td { background: #1C1C20; }
              hr { border: none; border-top: 1px solid #2A2A32; margin: 16px 0; }
              img { max-width: 100%; }
              ul, ol { padding-left: 24px; }
              li { margin: 3px 0; }
              strong { color: #FFFFFF; }
            </style>
            </head>
            <body>
            """ + body + """
            </body>
            </html>
            """;
    }

    // ── Edit mode ───────────────────────────────────────────────────────────

    private void toggleEditMode(boolean on) {
        editMode = on;
        webView.setVisible(!on);
        webView.setManaged(!on);
        sourceEditor.setVisible(on);
        sourceEditor.setManaged(on);
        // Toggle footer save/cancel buttons (they are the 3rd-from-last group)
        HBox footer = (HBox) root().getChildren().get(2);
        // find by iterating
        for (javafx.scene.Node n : footer.getChildren()) {
            if (!(n instanceof Button b)) continue;
            String text = b.getText();
            if (text != null && text.contains("Edit Source")) b.setVisible(!on);
            else if (text != null && text.startsWith(Icons.CHECK)) { b.setVisible(on); b.setManaged(on); }
            else if (text != null && text.equals("Cancel Edit")) { b.setVisible(on); b.setManaged(on); }
        }
    }

    private void saveAndRender() {
        String src = sourceEditor.getText();
        try {
            Files.writeString(mdFile.toPath(), src);
        } catch (Exception ex) {
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR,
                "Failed to save:\n" + ex.getMessage());
            com.conload.ui.DialogStyler.style(a);
            a.showAndWait();
            return;
        }
        // Re-render
        webView.getEngine().loadContent(buildHtmlDocument(mdFile.getName(), src));
        toggleEditMode(false);
        if (onSaved != null) onSaved.run();
    }

    private void openExternally() {
        try {
            com.conload.util.PlatformFileOpener.openFile(mdFile, null);
        } catch (Exception ex) {
            // ignore
        }
    }

    private VBox root() {
        return (VBox) stage.getScene().getRoot();
    }
}
