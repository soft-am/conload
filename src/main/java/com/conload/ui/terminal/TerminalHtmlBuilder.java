package com.conload.ui.terminal;

import com.conload.ui.AppColors;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Builds the xterm.js HTML document for the embedded terminal.
 * Loads xterm.min.js, xterm.css, and addon-fit.min.js from resources.
 * Contains terminal-specific ANSI colors (not part of the JavaFX UI theme).
 */
public final class TerminalHtmlBuilder {

    /** Terminal color palette (xterm.js theme — separate from JavaFX CSS variables). */
    public record TerminalPalette(
            String background,
            String foreground,
            String cursor,
            String cursorAccent,
            String selectionBackground,
            String ansiBlack, String ansiRed, String ansiGreen, String ansiYellow,
            String ansiBlue, String ansiMagenta, String ansiCyan, String ansiWhite,
            String ansiBrightBlack, String ansiBrightWhite
    ) {}

    /** Dark-terminal palette (used for both dark and light UI themes).
     *  All hex values sourced from {@link AppColors} — the single source of
     *  truth — so terminal ANSI colors stay in sync with the JavaFX UI theme.
     *  Only {@code ansiBlack}, {@code ansiBrightBlack}, and {@code ansiBrightWhite}
     *  remain terminal-specific (no app-color equivalent). */
    public static TerminalPalette darkPalette() {
        return new TerminalPalette(
                AppColors.INPUT_DARK,  AppColors.TEXT_DARK,   AppColors.TEXT_DARK,
                AppColors.INPUT_DARK,  "rgba(132,147,157,0.25)",
                "#1C1C22",             AppColors.ERROR_DARK,  AppColors.SUCCESS_DARK,
                AppColors.WARNING_DARK,
                AppColors.PROJECT[3],  AppColors.PROJECT[4],  AppColors.PROJECT[5],
                AppColors.TEXT_DARK,
                "#2A3A52",             "#FFFFFF"
        );
    }

    private static final String FONT_FAMILY = "'IBM Plex Mono','Fira Code','Menlo',monospace";
    private static final int FONT_SIZE = 14;

    private final TerminalPalette palette;

    public TerminalHtmlBuilder() {
        this(darkPalette());
    }

    public TerminalHtmlBuilder(TerminalPalette palette) {
        this.palette = palette;
    }

    /**
     * Build the complete HTML document for xterm.js, including all JavaScript
     * for terminal initialization, JS→Java bridge communication, resize handling,
     * base64 output writing, and prompt detection.
     */
    public String build() {
        String xtermJs = readResource("/terminal/xterm.min.js");
        String xtermCss = readResource("/terminal/xterm.css");
        String fitJs = readResource("/terminal/addon-fit.min.js");

        String css = xtermCss
                + "* { margin:0; padding:0; box-sizing:border-box; }\n"
                + "html,body { width:100%; height:100%; background:" + palette.background() + "; overflow:hidden; }\n"
                + "#t { width:100%; height:100%; }\n"
                + ".xterm .xterm-viewport { overflow-y: scroll !important; }\n"
                + ".xterm-viewport::-webkit-scrollbar { width: 0; height: 0; -webkit-appearance: none; }\n";

        String js = xtermJs + "\n" + fitJs + "\n"
                + "window.onerror = function(msg,src,line,col,err){"
                + "  window.alert('[JS ERROR] ' + msg + ' @ line ' + line);"
                + "};\n"
                + "console.error = function() {"
                + "  window.alert('[JS console.error] ' + Array.prototype.join.call(arguments,' '));"
                + "};\n"
                + "var term = new Terminal({\n"
                + "  fontFamily:\"" + FONT_FAMILY + "\", fontSize:" + FONT_SIZE + ",\n"
                + "  cursorBlink:true, scrollback:10000,\n"
                + "  theme:{ background:'" + palette.background() + "', foreground:'" + palette.foreground() + "',\n"
                + "          cursor:'" + palette.cursor() + "', cursorAccent:'" + palette.cursorAccent() + "',\n"
                + "          selectionBackground:'" + palette.selectionBackground() + "',\n"
                + "          black:'" + palette.ansiBlack() + "', red:'" + palette.ansiRed() + "', green:'" + palette.ansiGreen() + "', yellow:'" + palette.ansiYellow() + "',\n"
                + "          blue:'" + palette.ansiBlue() + "', magenta:'" + palette.ansiMagenta() + "', cyan:'" + palette.ansiCyan() + "', white:'" + palette.ansiWhite() + "',\n"
                + "          brightBlack:'" + palette.ansiBrightBlack() + "', brightWhite:'" + palette.ansiBrightWhite() + "' }\n"
                + "});\n"
                + "var fit = new FitAddon.FitAddon();\n"
                + "term.loadAddon(fit);\n"
                + "term.open(document.getElementById('t'));\n"
                + " if(document.getElementById('t').clientWidth > 0){ fit.fit(); } \n"
                + "function _refit(){\n"
                + "  if (document.getElementById('t').clientWidth <= 0) return;\n"
                + "  try { fit.fit(); } catch(e) { console.error(e); }\n"
                + "}\n"
                + "window.addEventListener('resize', function(){ _refit(); });\n"
                + "if (typeof ResizeObserver !== 'undefined')\n"
                + "  new ResizeObserver(function(){ _refit(); })\n"
                + "    .observe(document.getElementById('t'));\n"
                + "term.onData(function(d){\n"
                + "  try{ if(window.javaBridge) window.javaBridge.sendInput(d); } catch(e) { console.error(e); }\n"
                + "  if(d==='\\r') try{ window.javaBridge&&window.javaBridge.setBusy(true); } catch(e) { console.error(e); }\n"
                + "});\n"
                + "term.onResize(function(size){ try{ if(window.javaBridge) window.javaBridge.onResize(size.cols, size.rows); }catch(e){ console.error(e); } });\n"
                + "function writeB64(b64){\n"
                + "  var s=atob(b64),u=new Uint8Array(s.length);\n"
                + "  for(var i=0;i<s.length;i++) u[i]=s.charCodeAt(i);\n"
                + "  term.write(u);\n"
                + "  if(/[$%>#]\\s*$/.test(s.trimEnd()))\n"
                + "    try{ window.javaBridge&&window.javaBridge.setBusy(false); }catch(e){}\n"
                + "}\n"
                + "function getBufferText(){\n"
                + "  var b=term.buffer.active,lines=[],n=b.length;\n"
                + "  for(var i=0;i<n;i++){var ln=b.getLine(i);if(ln)lines.push(ln.translateToString(true));}\n"
                + "  return lines.join('\\n');\n"
                + "}\n"
                + "(function poll(){\n"
                + "  if(window.javaBridge){\n"
                + "    try {\n"
                + "      if(document.getElementById('t').clientWidth > 0) {\n"
                + "        fit.fit();\n"
                + "      }\n"
                + "    } catch(e) { console.error('Early fit omitted:', e); }\n"
                + "    try { window.javaBridge.onReady(); } catch(e) { console.error(e); }\n"
                + "  } else { setTimeout(poll,30); }\n"
                + "})();\n";

        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
                + css + "</style></head><body><div id=\"t\"></div>"
                + "<script>" + js + "</script></body></html>";
    }

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(TerminalHtmlBuilder.class.getName());

    private static String readResource(String path) {
        try (InputStream is = TerminalHtmlBuilder.class.getResourceAsStream(path)) {
            if (is == null) {
                log.warning("Missing resource: " + path);
                return "/* missing: " + path + " */";
            }
            byte[] bytes = is.readAllBytes();
            log.fine("Loaded resource: " + path + " (" + bytes.length + " bytes)");
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warning("Error reading resource: " + path + " — " + e.getMessage());
            return "/* error: " + e.getMessage() + " */";
        }
    }

}
