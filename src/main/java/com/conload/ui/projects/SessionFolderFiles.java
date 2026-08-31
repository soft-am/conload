package com.conload.ui.projects;

import java.io.File;

/** Shared parsing and file selection rules for exported session folders. */
public final class SessionFolderFiles {
    private SessionFolderFiles() {}

    public record Info(String id, String date, String title, String summary, String agent) {
        public static Info parse(File sessionFolder) {
            if (sessionFolder == null || !sessionFolder.isDirectory()) return empty();
            File infoFile = new File(sessionFolder, "session_info.md");
            if (!infoFile.isFile()) return empty();
            String id = null, date = null, title = null, agent = null;
            StringBuilder summary = new StringBuilder();
            try {
                String raw = java.nio.file.Files.readString(infoFile.toPath()).replace("\r", "");
                boolean inSummary = false;
                for (String line : raw.split("\n", -1)) {
                    String t = line.trim();
                    if (t.equalsIgnoreCase("## Summary")) { inSummary = true; continue; }
                    if (inSummary) {
                        if (!t.isBlank()) {
                            if (summary.length() > 0) summary.append(' ');
                            summary.append(t);
                        }
                        continue;
                    }
                    if (t.startsWith("|") && t.endsWith("|")) {
                        String[] parts = t.substring(1, t.length() - 1).split("\\|");
                        if (parts.length >= 2) {
                            String key = parts[0].trim();
                            String val = parts[1].trim();
                            switch (key) {
                                case "Session ID" -> { if (!val.isBlank()) id = stripBackticks(val); }
                                case "Date", "Exported" -> { if (date == null && !val.isBlank()) date = val; }
                                case "Title" -> { if (!val.isBlank()) title = val; }
                                case "Agent" -> { if (!val.isBlank()) agent = val; }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            return new Info(id, date, title, summary.toString().isBlank() ? null : summary.toString().trim(), agent);
        }

        private static Info empty() { return new Info(null, null, null, null, null); }
        private static String stripBackticks(String value) {
            String result = value.trim();
            return result.startsWith("`") && result.endsWith("`") && result.length() >= 2
                    ? result.substring(1, result.length() - 1) : result;
        }
    }

    public static File resolveMarkdown(File sessionFolder) {
        if (sessionFolder == null || !sessionFolder.isDirectory()) return null;
        File transcript = null;
        File[] files = sessionFolder.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".md")
                    && !"session_info.md".equalsIgnoreCase(file.getName())
                    && (transcript == null || file.length() > transcript.length())) transcript = file;
        }
        if (transcript != null) return transcript;
        File info = new File(sessionFolder, "session_info.md");
        return info.isFile() ? info : null;
    }

    public static String friendlySessionName(String raw) {
        return stripPrefixAndTimestamp(raw, "session_").replace('_', ' ');
    }

    public static String formatLabel(String type, String title, String id) {
        String actualType = type == null || type.isBlank() ? "session" : type;
        String pretty = actualType.length() <= 1 ? actualType.toUpperCase()
                : Character.toUpperCase(actualType.charAt(0)) + actualType.substring(1);
        String label = pretty + ": " + (title == null || title.isBlank() ? id : title);
        return label.length() > 60 ? label.substring(0, 57) + "…" : label;
    }

    private static String stripPrefixAndTimestamp(String raw, String prefix) {
        String name = raw;
        if (name.startsWith(prefix)) name = name.substring(prefix.length());
        name = name.replaceAll("_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})$", "");
        name = name.replaceAll("_(\\d{4}-\\d{2}-\\d{2})$", "");
        return name.isBlank() ? raw : name;
    }
}
