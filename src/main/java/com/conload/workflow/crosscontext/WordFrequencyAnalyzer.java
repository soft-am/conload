package com.conload.workflow.crosscontext;

import com.conload.workflow.WorkflowCallbacks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Finds the top-3 words that appear frequently across all context files
 * <em>and</em> are present in the main/epic Jira title (strict mode).
 * <p>
 * Used by the "often words" Confluence discovery pass: the matched words
 * are used to search Confluence for additional pages that may not be
 * directly linked but are topically related.
 */
final class WordFrequencyAnalyzer {

    private final WorkflowCallbacks callbacks;

    WordFrequencyAnalyzer(WorkflowCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    /**
     * Scan all {@code *.md} and {@code *.json} files under {@code contextRoot}
     * and return up to 3 words from {@code titleTokens} that appear most
     * frequently across the corpus.
     *
     * @param contextRoot the directory to walk recursively
     * @param titleTokens words extracted from the main/epic Jira title
     * @return 0–3 words sorted by frequency descending
     */
    java.util.List<String> topStrict(Path contextRoot, Collection<String> titleTokens) {
        if (titleTokens == null || titleTokens.isEmpty()) return java.util.List.of();
        // Use these to count
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (String t : titleTokens) freq.put(t, 0);

        try (Stream<Path> walk = Files.walk(contextRoot)) {
            walk.filter(p -> {
                String s = p.toString();
                return s.endsWith(".md") || s.endsWith(".json");
            }).forEach(p -> {
                try {
                    String text = Files.readString(p).toLowerCase();
                    String[] words = text.split("[^a-z0-9]+");
                    for (String w : words) {
                        if (freq.containsKey(w)) {
                            freq.merge(w, 1, Integer::sum);
                        }
                    }
                } catch (IOException ignored) { /* best-effort */ }
            });
        } catch (IOException e) {
            callbacks.onError("Words", "Corpus scan failed: " + e.getMessage());
        }

        var result = new ArrayList<Map.Entry<String, Integer>>();
        for (var e : freq.entrySet()) {
            if (e.getValue() > 0) result.add(e);
        }
        result.sort(Comparator.comparingInt(Map.Entry<String, Integer>::getValue).reversed());

        var top3 = new ArrayList<String>();
        for (int i = 0; i < Math.min(CrossContextLimits.MAX_OFTEN_WORDS, result.size()); i++) {
            top3.add(result.get(i).getKey());
            callbacks.onLog("[WORDS] Top word: \"" + result.get(i).getKey()
                    + "\" (" + result.get(i).getValue() + " occurrences)");
        }
        return top3;
    }
}
