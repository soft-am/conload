package com.conload.ui.terminal.session;

import com.conload.model.CliTypeDefinition;
import com.conload.sessionsprocessing.CliSession;
import com.conload.service.ConfigService;
import com.conload.sessionsprocessing.SessionProcessor;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.web.WebEngine;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects configured CLI sessions and owns their observable state. */
public final class CliSessionController {
    private final StringProperty activeId = new SimpleStringProperty("");
    private final StringProperty title = new SimpleStringProperty("");
    private final StringProperty pendingType = new SimpleStringProperty("");
    private final StringProperty pendingId = new SimpleStringProperty("");
    private final Supplier<SessionProcessor> service;
    private final Consumer<CliTypeDefinition> detected;
    private final Runnable sessionFound;
    private List<CliTypeDefinition> cliTypes;
    private CliTypeDefinition current;
    private String type = "";
    private StringBuilder input = new StringBuilder();
    private PauseTransition poller;
    private boolean awaiting;
    private int scanTicks;
    private WebEngine engine;

    public CliSessionController(Supplier<SessionProcessor> service,
            Consumer<CliTypeDefinition> detected, Runnable sessionFound) {
        this.service = service; this.detected = detected; this.sessionFound = sessionFound;
    }
    public StringProperty activeProperty() { return activeId; }
    public StringProperty titleProperty() { return title; }
    public StringProperty pendingTypeProperty() { return pendingType; }
    public StringProperty pendingIdProperty() { return pendingId; }
    public String type() { return type == null ? "" : type; }
    public CliTypeDefinition definition() { return current != null ? current : findByType(type); }
    public void setEngine(WebEngine engine) { this.engine = engine; }

    /** Whether the given CLI type (detect text) has a configured resume
     *  command. Static so callers without a controller instance (e.g.
     *  {@code ProjectWorkspaceController} during tab restore) can gate which
     *  CLI types can be resumed — replacing the old hardcoded
     *  {@code "opencode"/"copilot"} literal check. */
    public static boolean isResumableType(String type) {
        if (type == null || type.isBlank()) return false;
        List<CliTypeDefinition> types;
        try { types = new ConfigService().loadConfig().getCliTypes(); }
        catch (Exception e) { types = CliTypeDefinition.defaults(); }
        if (types == null || types.isEmpty()) types = CliTypeDefinition.defaults();
        for (CliTypeDefinition def : types)
            if (type.equalsIgnoreCase(def.getDetectText())) return def.canResume();
        return false;
    }

    public void setPending(String cliType, String id) {
        Platform.runLater(() -> { pendingType.set(cliType == null ? "" : cliType); pendingId.set(id == null ? "" : id); });
        resolveTitle(id);
    }
    public void clearPending() { pendingType.set(""); pendingId.set(""); title.set(""); }
    public void reset() { awaiting = false; stopPolling(); }

    public void inspectInput(String data) {
        for (char c : data.toCharArray()) {
            if (c == '\r' || c == '\n') {
                String line = input.toString().trim(); input.setLength(0);
                if (!line.isEmpty()) {
                    CliTypeDefinition def = findByCommand(line.split("\\s+")[0]);
                    if (def != null) begin(def);
                }
            } else if (c >= 0x20) input.append(c);
        }
    }
    public void startPolling() {
        stopPolling(); awaiting = true;
        final int[] ticks = {0};
        poller = new PauseTransition(Duration.seconds(2));
        poller.setOnFinished(e -> {
            if (++ticks[0] > 60 || !awaiting) return;
            if (engine != null) try {
                Object result = engine.executeScript("getBufferText()");
                if (result instanceof String text && !text.isBlank()) inspectBuffer(text);
            } catch (Exception ignored) { }
            if (poller != null) poller.playFromStart();
        });
        poller.play();
    }
    public void stopPolling() { if (poller != null) { poller.stop(); poller = null; } }

    public void inspectBuffer(String text) {
        if (scanTicks++ < 3) System.out.println("[SESSION] Buffer scan (first 500 chars): " + text.substring(0, Math.min(text.length(), 500)));
        if (!activeId.get().isBlank()) return;
        if (type.isBlank()) for (CliTypeDefinition def : cliTypes()) {
            if (!def.getDetectText().isBlank() && text.toLowerCase().contains(def.getDetectText().toLowerCase())) { begin(def); break; }
        }
        if (type.isBlank()) return;
        String[] patterns = {
            "session\\s*(?:id|[:=])\\s*['\"]?([A-Za-z0-9][A-Za-z0-9_\\-]{4,})",
            "\\b([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\b",
            "\\.opencode/session/([a-zA-Z0-9_\\-]+)",
            "session['\"]?\\s*[:=]?\\s*[\"'\\[]+([A-Za-z0-9_\\-]{8,})[\"'\\]]+",
            "\\b([0-9A-Z]{26})\\b" };
        for (String expression : patterns) { Matcher m = Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(text); if (m.find()) { found(m.group(1)); return; } }
    }
    private void begin(CliTypeDefinition def) {
        current = def; type = def.getDetectText().toLowerCase();
        Platform.runLater(() -> activeId.set("")); detected.accept(def); startPolling();
    }
    private void found(String id) {
        awaiting = false; stopPolling();
        Platform.runLater(() -> { clearPending(); activeId.set(id); if (sessionFound != null) sessionFound.run(); });
        resolveTitle(id);
    }
    public List<CliTypeDefinition> cliTypes() {
        if (cliTypes == null) try {
            cliTypes = new ConfigService().loadConfig().getCliTypes();
            if (cliTypes == null || cliTypes.isEmpty()) cliTypes = CliTypeDefinition.defaults();
        } catch (Exception e) { cliTypes = CliTypeDefinition.defaults(); }
        return cliTypes;
    }
    public CliTypeDefinition findByType(String value) { if (value == null) return null; return cliTypes().stream().filter(d -> value.equalsIgnoreCase(d.getDetectText())).findFirst().orElse(null); }
    private CliTypeDefinition findByCommand(String value) { return findByType(value); }
    private void resolveTitle(String id) {
        if (id == null || id.isBlank()) { Platform.runLater(() -> title.set("")); return; }
        CliSession hit = null;
        try { for (CliSession s : service.get().loadCachedSessions()) if (id.equals(s.getId())) { hit = s; break; } } catch (Exception ignored) { }
        if (hit != null) { String t = hit.getTitle(); Platform.runLater(() -> title.set(t == null ? "" : t)); return; }
        service.get().fetchSessions(list -> { String t = ""; for (CliSession s : list) if (id.equals(s.getId())) { t = s.getTitle(); break; } final String result = t == null ? "" : t; Platform.runLater(() -> title.set(result)); }, e -> { });
    }
}
