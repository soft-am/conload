package com.conload.ui.createcontext.model;

import com.conload.ui.Icons;

/** Enum of supported search criterion types shown in the criteria popup.
 *
 *  <p>Confluence and Jira use a single unified type each: the user enters either
 *  a URL or a keyword/key in the same field, and {@code SourceInputClassifier}
 *  decides the proper dispatch (tree download vs keyword search, direct issue
 *  fetch vs JQL search). GitHub keeps its dedicated subtypes. */
public enum CriteriaType {
    EVERYWHERE    (Icons.TARGET + "  Everywhere (Keyword)"),
    CONFLUENCE    (Icons.CLOUD + "  Confluence (URL or Keyword)"),
    JIRA          (Icons.DOT + "  Jira (URL, Key, or Keyword)"),
    GITHUB_COMMIT (Icons.BRANCH_ALT + "  GitHub Commit Search"),
    GITHUB_COMMIT_URL (Icons.BRANCH_ALT + "  GitHub Commit (URL)"),
    GITHUB_ACTION_URL (Icons.BRANCH_ALT + "  GitHub Action Run (URL)"),
    GITHUB_PR     (Icons.BRANCH_ALT + "  GitHub PR (URL)");

    public final String label;
    CriteriaType(String l) { this.label = l; }
    @Override public String toString() { return label; }
}
