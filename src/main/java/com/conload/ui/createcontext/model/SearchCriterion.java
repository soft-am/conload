package com.conload.ui.createcontext.model;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

/**
 * One search criterion added by the user via the "Add Search Criteria" popup.
 * The {@code spinner} and {@code badge} fields are UI state populated when
 * the criterion chip is built; they are {@code transient} so they do not
 * participate in serialization.
 */
public class SearchCriterion {
    public final CriteriaType type;
    public String value;       // URL, keyword, PR-URL, etc.
    public String extraValue;  // secondary field, e.g. "owner/repo" for GITHUB_COMMIT

    // ── Live UI state (populated when the chip is built) ──────────────────────
    public transient ProgressIndicator spinner;
    public transient Label             badge;

    public SearchCriterion(CriteriaType type, String value, String extraValue) {
        this.type       = type;
        this.value      = value;
        this.extraValue = extraValue != null ? extraValue : "";
    }

    public SearchCriterion(CriteriaType type, String value) {
        this(type, value, "");
    }
}

