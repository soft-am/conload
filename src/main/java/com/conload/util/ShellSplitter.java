package com.conload.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a command string into an argv array, honouring double-quoted
 * segments (quotes are removed; backslash escapes {@code \"} and {@code \\}
 * inside quotes). Single quotes are treated literally (not a quoting
 * mechanism) to match the most common simple case. Whitespace outside
 * quotes separates arguments. Returns an empty array for blank input.
 *
 * <p>Extracted from {@link com.conload.service.OpencodeSessionService} as a
 * reusable utility.
 */
public final class ShellSplitter {

    private ShellSplitter() {}

    public static String[] split(String command) {
        if (command == null || command.isBlank()) return new String[0];
        List<String> args = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        boolean hadContent = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (inQuote) {
                if (c == '\\' && i + 1 < command.length()) {
                    char n = command.charAt(i + 1);
                    if (n == '"' || n == '\\') { cur.append(n); i++; continue; }
                    cur.append(c);
                } else if (c == '"') {
                    inQuote = false;
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuote = true;
                    hadContent = true;
                } else if (Character.isWhitespace(c)) {
                    if (hadContent || cur.length() > 0) {
                        args.add(cur.toString());
                        cur.setLength(0);
                        hadContent = false;
                    }
                } else {
                    cur.append(c);
                }
            }
        }
        if (hadContent || cur.length() > 0) args.add(cur.toString());
        return args.toArray(new String[0]);
    }
}
