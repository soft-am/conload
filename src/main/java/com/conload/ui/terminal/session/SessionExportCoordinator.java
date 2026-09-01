package com.conload.ui.terminal.session;

import com.conload.model.CliTypeDefinition;
import com.conload.sessionsprocessing.CliSession;
import com.conload.sessionsprocessing.SessionProcessor;
import com.conload.ui.DialogStyler;
import com.conload.ui.Icons;
import com.conload.ui.components.AppErrorNotifier;
import com.conload.ui.components.UiFactory;
import com.conload.util.BackgroundTasks;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Coordinates the dialog and background work for session Markdown exports. */
public final class SessionExportCoordinator {
    private final Stage stage; private final Supplier<SessionProcessor> services;
    private final Supplier<Path> contexts; private final Supplier<String> folder;
    private String defaultFolder; private Consumer<Path> exported; private Consumer<String> started; private Runnable finished;
    public SessionExportCoordinator(Stage stage, Supplier<SessionProcessor> services, Supplier<Path> contexts, Supplier<String> folder) { this.stage=stage; this.services=services; this.contexts=contexts; this.folder=folder; }
    public void setDefaultFolder(String value) { defaultFolder=value; }
    public void setOnExported(Consumer<Path> value) { exported=value; }
    public void setOnStarted(Consumer<String> value) { started=value; }
    public void setOnFinished(Runnable value) { finished=value; }
    public void export(CliSession selected) {
        if (selected == null) return;
        SessionProcessor service = services.get();
        CliTypeDefinition def = service != null ? service.definition() : null;
        if (def == null) {
            AppErrorNotifier.report("Cannot export session: no CLI type was detected.");
            return;
        }
        if (!def.canExport()) {
            AppErrorNotifier.report("Cannot export session for CLI \"" + cliName(def)
                    + "\": no export command is configured.");
            return;
        }
        String title = safe(selected.getTitle().isBlank() ? selected.getMessage() : selected.getTitle()); if (title.isBlank()) title=selected.getId();
        Path base = defaultFolder != null && !defaultFolder.isBlank() ? Path.of(defaultFolder) : contexts.get();
        if (base == null) base = Path.of(folder.get().isBlank() ? System.getProperty("user.home") : folder.get());
        Path sessions = base.resolve("sessions");
        Dialog<String[]> dialog = new Dialog<>(); dialog.setTitle("Export session as Markdown"); dialog.setHeaderText(Icons.DOCUMENT + "  Export compacted session context"); dialog.getDialogPane().setPrefWidth(600);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE); dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL); DialogStyler.style(dialog);
        TextField dir = UiFactory.darkTextField(sessions.toString()); HBox.setHgrow(dir, Priority.ALWAYS); Button browse=UiFactory.actionButton("Browse");
        browse.setOnAction(e -> { DirectoryChooser c=new DirectoryChooser(); File f=new File(dir.getText().strip()); if(f.isDirectory()) c.setInitialDirectory(f); File chosen=c.showDialog(stage); if(chosen!=null) dir.setText(chosen.getAbsolutePath()); });
        TextField name=UiFactory.darkTextField(title); TextArea summary=new TextArea(); summary.setWrapText(true); summary.setPrefRowCount(4); summary.getStyleClass().add("log-area"); UiFactory.styleInput(summary,"Optional summary for this session (written to session_info.md)");
        VBox box=new VBox(6,new Label("Sessions directory:"),new HBox(4,dir,browse),new Label("Session folder name:"),name,new Label("Session summary (optional → session_info.md):"),summary); box.setPadding(new Insets(12)); dialog.getDialogPane().setContent(box);
        dialog.setResultConverter(b -> b==save ? new String[]{dir.getText().strip(),name.getText().strip(),summary.getText()} : null);
        final String defaultName=title; dialog.showAndWait().ifPresent(a -> { String n=a[1].isBlank()?defaultName:safe(a[1]); if(n.isBlank())n=selected.getId(); String stamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")); Path out=Path.of(a[0].isBlank()?sessions.toString():a[0]); run(selected,out.resolve("session_"+n+"_"+stamp),a[2]); });
    }
    private void run(CliSession selected, Path out, String summary) {
        if(started!=null) started.accept("Exporting session…"); SessionProcessor service=services.get();
        BackgroundTasks.runIOTask("cli-session-export", () -> { String compact=service.compactSession(selected.getId(),"anthropic","claude-sonnet-4-5"); String title=safe(selected.getTitle().isBlank()?selected.getMessage():selected.getTitle()); if(title.isBlank())title=selected.getId(); String stamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")); String error=service.exportSessionMarkdown(selected.getId(),out.resolve(title+"_"+stamp+".md")); String info=null; try { writeInfo(selected,out,summary,service.cliType()); } catch(Exception e){info=e.getMessage();} final String compactError=compact, infoError=info; Platform.runLater(() -> { if(finished!=null)finished.run(); if(error==null){String msg="Exported to:\n"+out+(compactError==null?"":"\n\n⚠ Compaction was skipped ("+compactError+").")+(infoError==null?"":"\n\n⚠ session_info.md write failed: "+infoError); Alert a=new Alert(Alert.AlertType.INFORMATION,msg);a.setTitle("Export complete");a.setHeaderText("Session exported successfully");DialogStyler.style(a);a.showAndWait();}else{AppErrorNotifier.report("Cannot export session for CLI \""+cliName(service.definition())+"\" with command \""+service.definition().getExportCommand()+"\": "+error);} if(exported!=null)exported.accept(out.toAbsolutePath()); }); });
    }
    private static String cliName(CliTypeDefinition def) { return def.getLabel().isBlank() ? def.getDetectText() : def.getLabel(); }
    private static void writeInfo(CliSession s,Path out,String summary,String cliLabel)throws IOException { Files.createDirectories(out); String title=s.getTitle().isBlank()?s.getMessage():s.getTitle(); String date=s.getCreated()>0?LocalDateTime.ofInstant(Instant.ofEpochMilli(s.getCreated()),ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")):s.getTime().isBlank()?"unknown":s.getTime(); String text="# Session Info\n\n| Field | Value |\n|---|---|\n| Session ID | `"+s.getId()+"` |\n| Date | "+date+" |\n| Agent | "+cliLabel+" |\n"+(title.isBlank()?"":"| Title | "+title+" |\n")+"\n## Summary\n\n"+(summary==null||summary.isBlank()?"":summary.strip()+"\n"); Files.writeString(out.resolve("session_info.md"),text); }
    private static String safe(String value){return value==null?"":value.trim().replaceAll("[^a-zA-Z0-9._-]","_").replaceAll("_+","_").replaceAll("^_+|_+$","");}
}
