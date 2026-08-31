package com.conload.ui.terminal;

import javafx.application.Platform;
import javafx.concurrent.Worker.State;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Owns xterm WebView loading, JavaScript bridge installation, and viewers. */
final class TerminalWebViewController {
    interface Listener {
        void onReady();
        void onInput(String data);
        void onBusy(boolean busy);
        void onResize(int cols, int rows);
    }

    private final WebView webView = new WebView();
    private final WebEngine engine = webView.getEngine();
    private final String html;
    private final Listener listener;
    private final List<WebEngine> viewers = new CopyOnWriteArrayList<>();
    /** Keep the JavaScript bridge alive for as long as the WebView uses it. */
    private Bridge bridge;
    private boolean htmlLoaded;
    private boolean htmlReady;
    private boolean xtermReady;
    private boolean refitPending;

    TerminalWebViewController(String html, Listener listener) {
        this.html = html;
        this.listener = listener;
        webView.setMaxWidth(Double.MAX_VALUE);
        webView.setOnMouseClicked(e -> { webView.requestFocus(); execute("term.focus()"); });
        webView.sceneProperty().addListener((obs, oldScene, newScene) -> onSceneChanged(newScene != null));
        webView.widthProperty().addListener((obs, oldValue, newValue) -> refit());
        webView.heightProperty().addListener((obs, oldValue, newValue) -> refit());
        configureEngine();
    }

    WebView view() { return webView; }
    WebEngine engine() { return engine; }
    boolean isReady() { return xtermReady; }

    boolean markReady() {
        if (xtermReady) return false;
        xtermReady = true;
        execute("if(document.getElementById('t').clientWidth > 0){ try{ fit.fit(); }catch(e){} }");
        execute("term.focus()");
        return true;
    }

    void addViewer(WebEngine viewer) {
        if (!viewers.contains(viewer)) viewers.add(viewer);
    }

    void clearViewers() { viewers.clear(); }

    void execute(String script) {
        try {
            engine.executeScript(script);
        } catch (Exception e) {
            System.err.println("JS Execution Error: " + script);
            e.printStackTrace();
        }
    }

    void broadcast(String script) {
        for (WebEngine viewer : viewers) {
            try { viewer.executeScript(script); }
            catch (Exception e) {
                System.err.println("[VIEWER] Dropping a viewer engine: " + e.getMessage());
                viewers.remove(viewer);
            }
        }
    }

    private void configureEngine() {
        engine.setOnAlert(event -> System.out.println("JS Alert: " + event.getData()));
        engine.getLoadWorker().exceptionProperty().addListener((obs, old, ex) -> {
            if (ex != null) ex.printStackTrace();
        });
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            System.out.println("[TERMINAL] WebEngine state: " + old + " → " + state);
            if (state == State.SUCCEEDED) installBridge();
            if (state == State.FAILED) {
                System.err.println("[TERMINAL] ✗ WebEngine FAILED: " + engine.getLoadWorker().getException());
                if (engine.getLoadWorker().getException() != null) engine.getLoadWorker().getException().printStackTrace();
            }
        });
    }

    private void installBridge() {
        try {
            bridge = new Bridge(listener);
            Object window = engine.executeScript("window");
            Class<?> jsObject = Class.forName("netscape.javascript.JSObject");
            Method setMember = jsObject.getMethod("setMember", String.class, Object.class);
            setMember.invoke(window, "javaBridge", bridge);
            htmlReady = true;
            addViewer(engine);
            System.out.println("[TERMINAL] ✓ State.SUCCEEDED — javaBridge set on window, scheduling terminalReady()");
            Platform.runLater(listener::onReady);
        } catch (Exception e) {
            System.err.println("[TERMINAL] ✗ FAILED to set javaBridge on window — button will stay gray!");
            e.printStackTrace();
        }
    }

    private void onSceneChanged(boolean attached) {
        if (attached && !htmlLoaded) {
            htmlLoaded = true;
            System.out.println("[TERMINAL] WebView entered scene — scheduling loadContent(HTML)");
            Platform.runLater(() -> { System.out.println("[TERMINAL] loadContent(HTML) called, HTML length=" + html.length()); engine.loadContent(html); });
        } else if (attached && htmlReady && !xtermReady) {
            Platform.runLater(listener::onReady);
        } else if (attached && xtermReady) {
            Platform.runLater(() -> { execute("try { _refit(); term.focus(); } catch(e) {}"); listener.onReady(); });
        } else if (!attached) {
            System.out.println("[TERMINAL] WebView removed from scene");
        }
    }

    private void refit() {
        if (!xtermReady || refitPending) return;
        refitPending = true;
        Platform.runLater(() -> { refitPending = false; execute("try{ _refit(); }catch(e){ try{ fit.fit(); }catch(e2){} }"); });
    }

    public static final class Bridge {
        private final Listener listener;
        private Bridge(Listener listener) { this.listener = listener; }
        public void sendInput(String data) { listener.onInput(data); }
        public void onReady() { Platform.runLater(listener::onReady); }
        public void setBusy(boolean busy) { Platform.runLater(() -> listener.onBusy(busy)); }
        public void onResize(int cols, int rows) { Platform.runLater(() -> listener.onResize(cols, rows)); }
    }
}
