package com.conload.ui.terminal;

import com.conload.model.CliTypeDefinition;
import com.conload.sessionsprocessing.CliSession;
import com.conload.service.ConfigService;
import com.conload.sessionsprocessing.SessionProcessor;
import com.conload.ui.DialogStyler;
import com.conload.ui.Icons;
import com.conload.ui.ProjectColors;
import com.conload.ui.Theme;
import com.conload.ui.components.UiFactory;
import com.conload.ui.components.AppErrorNotifier;
import com.conload.ui.prompttemplate.PromptTemplatePanel;
import com.conload.ui.prompttemplate.QuickActionsBar;
import com.conload.ui.terminal.session.CliSessionController;
import com.conload.ui.terminal.session.SessionBrowserPopup;
import com.conload.ui.terminal.session.SessionExportCoordinator;
import com.conload.ui.terminal.session.TerminalSessionInfo;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Consumer;

/**
 * Embedded xterm.js terminal backed by Pty4J.
 */
public class CopilotTerminalPane extends VBox {
    private static final String HTML = new TerminalHtmlBuilder().build();
    private final Stage stage;
    private final boolean terminalOnlyMode;
    private final TerminalPtyController ptyController;
    private final CliSessionController sessions;
    private final SessionExportCoordinator exports;
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final BooleanProperty hasInput = new SimpleBooleanProperty(false);
    private final StringProperty sessionProperty = new SimpleStringProperty("");
    private final StringProperty sessionTitle = new SimpleStringProperty("");
    private final StringProperty pendingType = new SimpleStringProperty("");
    private final StringProperty pendingId = new SimpleStringProperty("");
    private Label folderLabel, shellLabel;
    private TextFlow sessionInfoFlow;
    private Button openBtn, exportBtn, sessionsBtn, restoreBtn;
    private TerminalWebViewController webController;
    private WebView webView;
    private WebEngine webEngine;
    private QuickActionsBar quickActionsBar;
    private PromptTemplatePanel promptTemplatePanel;
    private SessionBrowserPopup sessionPopup;
    private final Map<String, SessionProcessor> sessionServices = new LinkedHashMap<>();
    private SessionProcessor activeSessionService;
    private Path contextsDir;
    private String projectColor = ProjectColors.DEFAULT;
    private RobotIndicator.Character character = RobotIndicator.Character.ROBOT;
    private RobotIndicator robot;
    private Runnable onSessionDetected;
    private Consumer<Long> onPtyStarted, onPtyStopped;

    public CopilotTerminalPane(Stage stage) {
        this(stage, null, false);
    }

    public CopilotTerminalPane(Stage stage, String folder) {
        this(stage, folder, false);
    }

    public CopilotTerminalPane(Stage stage, String folder, boolean terminalOnlyMode) {
        this.stage = stage;
        this.terminalOnlyMode = terminalOnlyMode;
        sessions = new CliSessionController(this::sessionService, this::onCliDetected, this::notifySessionDetected);
        exports = new SessionExportCoordinator(stage, this::sessionService, () -> contextsDir,
                () -> folderLabel == null ? "" : folderLabel.getText());
        sessionProperty.bindBidirectional(sessions.activeProperty());
        sessionTitle.bindBidirectional(sessions.titleProperty());
        pendingType.bindBidirectional(sessions.pendingTypeProperty());
        pendingId.bindBidirectional(sessions.pendingIdProperty());
        ptyController = new TerminalPtyController(new TerminalPtyController.Listener() {
            public void onInput(String data) {
                hasInput.set(true);
                sessions.inspectInput(data);
            }

            public void onOutput(String data) {
                webController.broadcast("writeB64('" + data + "')");
            }

            public void onStarted(long pid) {
                notifyPtyStarted(pid);
                if (exportBtn != null) exportBtn.setDisable(false);
            }

            public void onStopped(long pid) {
                notifyPtyStopped(pid);
            }

            public void onEnded() {
                busy.set(false);
                hasInput.set(false);
            }
        });
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        Theme.classes(this, Theme.CL_BG_APP);
        buildUi(folder);
    }

    public BooleanProperty busyProperty() {
        return busy;
    }

    public BooleanProperty hasInputProperty() {
        return hasInput;
    }

    public long getPid() {
        return ptyController.pid();
    }

    public String getSessionId() {
        return sessionProperty.get();
    }

    public StringProperty sessionProperty() {
        return sessionProperty;
    }

    public StringProperty sessionTitleProperty() {
        return sessionTitle;
    }

    public String getSessionType() {
        return sessions.type();
    }

    public TerminalSessionInfo sessionInfo() {
        boolean pending = !pendingId.get().isBlank() && sessionProperty.get().isBlank();
        String id = pending ? pendingId.get() : sessionProperty.get();
        String type = pending ? pendingType.get() : sessions.type();
        String ttl = sessionTitle.get();
        return new TerminalSessionInfo(type, ttl, id, pending);
    }

    public void setOnSessionDetected(Runnable cb) {
        onSessionDetected = cb;
    }

    public void setOnPtyStarted(Consumer<Long> cb) {
        onPtyStarted = cb;
    }

    public void setOnPtyStopped(Consumer<Long> cb) {
        onPtyStopped = cb;
    }

    public void setContextsDir(Path value) {
        contextsDir = value;
    }

    public void setDefaultExportFolder(String value) {
        exports.setDefaultFolder(value);
    }

    public void setOnSessionExported(Consumer<Path> cb) {
        exports.setOnExported(cb);
    }

    public void setOnExportStarted(Consumer<String> cb) {
        exports.setOnStarted(cb);
    }

    public void setOnExportFinished(Runnable cb) {
        exports.setOnFinished(cb);
    }

    public void setProjectColor(String value) {
        projectColor = value == null || value.isBlank() ? ProjectColors.DEFAULT : value;
        robot = null;
        if (promptTemplatePanel != null) promptTemplatePanel.setProjectColor(projectColor);
    }

    public void setProjectCharacter(RobotIndicator.Character value) {
        character = value == null ? RobotIndicator.Character.ROBOT : value;
        robot = null;
    }

    public RobotIndicator getRobotIndicator() {
        if (robot == null) robot = new RobotIndicator(projectColor, character);
        return robot;
    }

    public void setShell(String shell) {
        shellLabel.setText(shell == null ? "" : shell.strip());
    }

    public void setPendingResumeSession(String type, String id) {
        sessions.setPending(type, id);
    }

    public void injectDurableSession(String type, String id) {
        if (sessionProperty.get().isBlank() && pendingId.get().isBlank()) sessions.setPending(type, id);
    }

    public void triggerExport() {
        exportTerminalChat();
    }

    public void showSessionsPopupPublic() {
        showSessionsPopup();
    }

    public boolean isSessionsAvailable() {
        return sessionsBtn != null && sessionsBtn.isVisible();
    }

    public void destroyPtyNoUi() {
        ptyController.destroyNoUi();
    }

    public void sendInput(String data) {
        if (ptyController.isAlive()) ptyController.sendInput(data);
    }

    public void resizePty(int cols, int rows) {
        ptyController.resize(cols, rows);
    }

    public void closeTerminal() {
        webController.clearViewers();
        robot = null;
        ptyController.destroy();
        busy.set(false);
        hasInput.set(false);
        sessions.reset();
        sessionServices.values().forEach(SessionProcessor::stopAutoRefresh);
        if (sessionPopup != null) sessionPopup.close();
        if (sessionsBtn != null) UiFactory.hide(sessionsBtn);
        if (exportBtn != null) exportBtn.setDisable(true);
    }

    /** Proactively checks all configured CLI types that expose a session-list
     *  command. The first one whose fetch returns a non-empty list wins —
     *  its def becomes the bound service and {@link #onCliDetected} is
     *  invoked so the Sessions button appears without the user first typing
     *  the CLI command. */
    public void checkForCliSessions() {
        for (CliTypeDefinition def : sessions.cliTypes()) {
            if (!def.hasSessions()) continue;
            SessionProcessor sp = sessionServiceFor(def.getDetectText());
            sp.fetchSessions(list -> {
                if (!list.isEmpty()) onCliDetected(def);
            }, e -> { });
        }
    }

    private void buildUi(String folder) {
        folderLabel = new Label(folder == null || folder.isBlank() ? System.getProperty("user.home") : folder);
        folderLabel.getStyleClass().addAll("hint", "terminal-bar-label");
        folderLabel.setMaxWidth(Double.MAX_VALUE);
        shellLabel = new Label("");
        shellLabel.getStyleClass().addAll("hint", "terminal-bar-label");
        sessionInfoFlow = new TextFlow();
        sessionInfoFlow.getStyleClass().addAll("hint", "terminal-bar-session");
        sessionInfoFlow.managedProperty().bind(Bindings.createBooleanBinding(() -> !sessionInfo().isEmpty(), sessionProperty, pendingId, pendingType, sessionTitle));
        sessionInfoFlow.visibleProperty().bind(sessionInfoFlow.managedProperty());
        restoreBtn = new Button("Restore");
        restoreBtn.getStyleClass().addAll("app-button", "terminal-action-btn");
        restoreBtn.setTooltip(new Tooltip("Resume this session"));
        restoreBtn.visibleProperty().bind(Bindings.createBooleanBinding(() -> sessionInfo().pending(), pendingId, sessionProperty));
        restoreBtn.managedProperty().bind(restoreBtn.visibleProperty());
        restoreBtn.setOnAction(e -> resumePending());
        Runnable updateInfo = () -> {
            TerminalSessionInfo si = sessionInfo();
            sessionInfoFlow.getChildren().clear();
            if (si.isEmpty()) return;
            CliTypeDefinition d = sessions.findByType(si.type());
            String label = d != null && !d.getLabel().isBlank() ? d.getLabel() : si.type().isBlank() ? "session" : si.type();
            Text main = new Text(si.title().isBlank() ? label : label + ": " + si.title());
            main.getStyleClass().add("terminal-bar-session-main");
            Text idText = new Text("  (" + si.id() + ")");
            idText.getStyleClass().add("terminal-bar-session-id");
            sessionInfoFlow.getChildren().addAll(main, idText);
        };
        sessionProperty.addListener((o, a, n) -> updateInfo.run());
        pendingId.addListener((o, a, n) -> updateInfo.run());
        pendingType.addListener((o, a, n) -> updateInfo.run());
        sessionTitle.addListener((o, a, n) -> updateInfo.run());
        openBtn = new Button(Icons.REFRESH);
        openBtn.getStyleClass().addAll("icon-button", "secondary", "icon");
        openBtn.setDisable(true);
        openBtn.setOnAction(e -> openTerminal());
        exportBtn = new Button(" Export");
        exportBtn.getStyleClass().addAll("app-button", "terminal-action-btn");
        exportBtn.setDisable(true);
        exportBtn.setOnAction(e -> exportTerminalChat());
        sessionsBtn = new Button(" Sessions");
        sessionsBtn.getStyleClass().addAll("app-button", "terminal-action-btn");
        UiFactory.hide(sessionsBtn);
        sessionsBtn.setOnAction(e -> showSessionsPopup());
        HBox group = new HBox(6, sessionInfoFlow, restoreBtn, exportBtn);
        group.setAlignment(Pos.CENTER);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(6, shellLabel, folderLabel, openBtn, spacer, group, sessionsBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(5, 12, 5, 12));
        bar.getStyleClass().add("panel-border-bottom");
        webController = new TerminalWebViewController(HTML, new TerminalWebViewController.Listener() {
            public void onReady() {
                terminalReady();
            }

            public void onInput(String d) {
                sendInput(d);
            }

            public void onBusy(boolean b) {
                busy.set(b);
            }

            public void onResize(int c, int r) {
                resizePty(c, r);
            }
        });
        webView = webController.view();
        webEngine = webController.engine();
        sessions.setEngine(webEngine);
        Theme.classes(webView, Theme.CL_BG_APP);
        VBox.setVgrow(webView, Priority.ALWAYS);
        if (!terminalOnlyMode) {
            quickActionsBar = new QuickActionsBar(this::sendInput);
            promptTemplatePanel = new PromptTemplatePanel(this::sendInput, () -> quickActionsBar.refresh());
            promptTemplatePanel.setWorkDir(new File(folderLabel.getText()));
        }
        VBox right = terminalOnlyMode ? new VBox(webView) : new VBox(quickActionsBar, promptTemplatePanel, webView);
        VBox.setVgrow(right, Priority.ALWAYS);
        getChildren().addAll(bar, right);
    }

    private void terminalReady() {
        openBtn.setDisable(false);
        Platform.runLater(() -> {
            boolean firstReady = webController.markReady();
            webController.execute("term.focus()");
            if (!firstReady) return;
            if (!ptyController.isAlive()) openTerminal();
            sessions.startPolling();
        });
    }

    void openTerminal() {
        if (!webController.isReady()) return;
        if (ptyController.isAlive()) ptyController.destroy();
        String shell = shellLabel.getText().strip();
        if (shell.isBlank()) {
            shell = defaultShell();
            if (shell == null) {
                alert("Terminal cannot start", "No shell configured.");
                return;
            }
            setShell(shell);
        }
        String folder = folderLabel.getText().strip();
        File dir = new File(folder);
        if (!dir.isDirectory()) {
            alert("Directory not found", folder);
            return;
        }
        try {
            Path n = Path.of(System.getProperty("user.home"), ".pty4j-native");
            Files.createDirectories(n);
            System.setProperty("pty4j.tmpdir", n.toString());
            ptyController.start(shell, dir, environment());
            webController.execute("term.clear();term.focus()");
        } catch (IOException e) {
            alert("Cannot start terminal", e.getMessage());
        }
    }

    private Map<String, String> environment() {
        Map<String, String> e = new HashMap<>(System.getenv());
        e.put("TERM", "xterm-256color");
        e.put("PROMPT", "%1~ ❯ ");
        return e;
    }

    private String defaultShell() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "powershell";
        if (Files.exists(Path.of("/bin/zsh"))) return "zsh";
        if (Files.exists(Path.of("/bin/bash"))) return "bash";
        return null;
    }

    private void showSessionsPopup() {
        CliTypeDefinition activeDef = sessions.definition();
        String activeType = activeDef != null ? activeDef.getDetectText() : null;
        if (sessionPopup == null) {
            sessionPopup = new SessionBrowserPopup(stage, availableSessionClis(),
                    this::sessionServiceFor, activeType, sessions::definition,
                    this::getSessionId, (type, id) -> resumeSession(type, id), () -> projectColor,
                    (s, b) -> exports.export(s));
        }
        sessionPopup.show();
    }

    private void resumePending() {
        resumeSession(pendingId.get());
    }

    private void resumeSession(String id) {
        CliTypeDefinition d = sessions.definition();
        resumeSession(d != null ? d.getDetectText() : pendingType.get(), id);
    }

    private void resumeSession(String cliType, String id) {
        CliTypeDefinition d = sessions.findByType(cliType);
        if (d == null) d = sessions.definition();
        if (d == null) {
            AppErrorNotifier.report("Cannot resume session: no CLI type was detected.");
            return;
        }
        if (!d.canResume()) {
            AppErrorNotifier.report("Cannot resume session for CLI \"" + cliName(d)
                    + "\": no resume command is configured.");
            return;
        }
        String command = CliTypeDefinition.substituteId(d.getResumeCommand(), id);
        sessions.clearPending();
        if (ptyController.isAlive()) openTerminal();
        sendInput("SESSION_ID=\"" + id + "\"\n");
        Pause.delay(300, () -> sendInput(command + "\n"));
        Pause.delay(600, () -> {
            sessionProperty.set(id);
            if (onSessionDetected != null) onSessionDetected.run();
        });
    }

    private static String cliName(CliTypeDefinition def) {
        return def.getLabel().isBlank() ? def.getDetectText() : def.getLabel();
    }

    private void exportTerminalChat() {
        String id = sessionProperty.get().isBlank() ? pendingId.get() : sessionProperty.get();
        if (id.isBlank()) {
            alert("Nothing to export", "No CLI session is active in this terminal.");
            return;
        }
        CliSession found = null;
        for (CliSession s : sessionService().loadCachedSessions())
            if (id.equals(s.getId())) {
                found = s;
                break;
            }
        if (found == null) found = new CliSession(id, sessionTitle.get(), sessionTitle.get(), "", 0, 0, "", "");
        exports.export(found);
    }

    private void onCliDetected(CliTypeDefinition def) {
        if (def == null) return;
        if (def.hasSessions()) {
            UiFactory.show(sessionsBtn);
            sessionsBtn.setTooltip(new Tooltip("Browse " + def.getLabel() + " sessions"));
            SessionProcessor sp = sessionServiceFor(def.getDetectText());
            activeSessionService = sp;
            sp.startAutoRefresh(s -> {
                if (sessionPopup != null) sessionPopup.updateSessions(s, true);
            });
        } else UiFactory.hide(sessionsBtn);
    }

    /** Returns the session processor for the active CLI type, lazily creating
     *  one bound to the active {@link CliTypeDefinition}. Falls back to the
     *  first session-capable def when no CLI has been detected yet. */
    private SessionProcessor sessionService() {
        if (activeSessionService != null) {
            activeSessionService.setWorkDir(workDirFromFolderLabel());
            return activeSessionService;
        }
        CliTypeDefinition d = sessions.definition();
        if (d == null || !d.hasSessions()) {
            for (CliTypeDefinition def : sessions.cliTypes())
                if (def.hasSessions()) { d = def; break; }
        }
        if (d == null) d = sessions.cliTypes().isEmpty() ? null : sessions.cliTypes().get(0);
        if (d == null) d = new CliTypeDefinition("default", "default", "", "", "", false);
        return sessionServiceFor(d.getDetectText());
    }

    /** Returns (creating if absent) a {@link SessionProcessor} bound to the
     *  given CLI type. The def is looked up from the configured CLI types;
     *  if not found, a minimal def is synthesised so callers always get a
     *  non-null instance. */
    private SessionProcessor sessionServiceFor(String cliType) {
        return sessionServices.computeIfAbsent(cliType, k -> {
            CliTypeDefinition def = sessions.findByType(k);
            if (def == null) def = new CliTypeDefinition(k, k, "", "", "", false);
            return new SessionProcessor(def, workDirFromFolderLabel());
        });
    }

    private File workDirFromFolderLabel() {
        return (folderLabel != null && !folderLabel.getText().isBlank())
                ? new File(folderLabel.getText()) : null;
    }

    /** Lazily-initialized list of session-capable CLI defs, shared with the
     *  Sessions popup so it can populate its CLI filter dropdown. */
    private List<CliTypeDefinition> availableSessionClis() {
        return sessions.cliTypes().stream().filter(CliTypeDefinition::hasSessions).toList();
    }

    private void notifySessionDetected() {
        if (onSessionDetected != null) onSessionDetected.run();
    }

    private void notifyPtyStarted(long pid) {
        if (onPtyStarted != null) onPtyStarted.accept(pid);
    }

    private void notifyPtyStopped(long pid) {
        if (onPtyStopped != null) onPtyStopped.accept(pid);
    }

    private void alert(String title, String text) {
        Alert a = new Alert(Alert.AlertType.WARNING, text);
        a.setTitle(title);
        DialogStyler.style(a);
        a.showAndWait();
    }

    private static final class Pause {
        static void delay(long ms, Runnable r) {
            javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(javafx.util.Duration.millis(ms));
            p.setOnFinished(e -> r.run());
            p.play();
        }
    }
}
