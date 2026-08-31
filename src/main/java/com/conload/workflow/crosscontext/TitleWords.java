package com.conload.workflow.crosscontext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks title tokens from the main Jira issue and its epic, used by
 * {@link WordFrequencyAnalyzer} to filter the top-3 words.
 * <p>
 * Only words that appear frequently across all context files <em>and</em>
 * are present in the main or epic title qualify for the Confluence
 * "often words" discovery pass (strict mode).
 */
final class TitleWords {

    private final Set<String> mainTokens = new LinkedHashSet<>();
    private final Set<String> epicTokens = new LinkedHashSet<>();

    /** Set the main Jira's title; tokens are extracted (lower-cased, ≥4 chars). */
    void setMain(String title) {
        mainTokens.clear();
        mainTokens.addAll(tokenize(title));
    }

    /** Set the epic Jira's title. */
    void setEpic(String title) {
        epicTokens.clear();
        epicTokens.addAll(tokenize(title));
    }

    /** All tokens from main + epic titles (for filtering). */
    Collection<String> allTitleTokens() {
        List<String> all = new ArrayList<>(mainTokens);
        all.addAll(epicTokens);
        return all;
    }

    private static List<String> tokenize(String title) {
        List<String> tokens = new ArrayList<>();
        if (title == null || title.isBlank()) return tokens;
        for (String word : title.split("[\\s,;:!?/\\[\\](){}\"'._-]+")) {
            String w = word.strip().toLowerCase();
            if (w.length() >= 4) tokens.add(w);
        }
        return tokens;
    }
}
