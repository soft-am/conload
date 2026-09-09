package com.conload.workflow.crosscontext;

/**
 * Centralized tuning constants for the cross-context builder pipeline.
 * All limits that control recursion depth, search breadth, and result caps
 * live here so they can be adjusted in one place.
 */
public final class CrossContextLimits {

    private CrossContextLimits() {}

    /** Not-full mode: max recursion depth for related Jira issues. */
    public static final int MAX_DEPTH_NON_FULL = 1;

    /** Full mode: max recursion depth for related Jira issues. */
    public static final int MAX_DEPTH_FULL = 3;

    /** Not-full mode: max number of related keys to expand per issue. */
    public static final int MAX_RELATED_NON_FULL = 10;

    /** Full mode: max number of related keys to expand per issue. */
    public static final int MAX_RELATED_FULL = 10;

    /** Max Confluence pages returned per CQL keyword search. */
    public static final int MAX_CONFLUENCE_SEARCH_RESULTS = 5;

    /** Max top-frequency words used for the often-words Confluence discovery phase. */
    public static final int MAX_OFTEN_WORDS = 2;

    /** Max commits fetched and written per Jira key. */
    public static final int MAX_COMMITS_PER_KEY = 15;

    /** Max child issues (Epic children + sub-tasks) fetched per Jira key via JQL. */
    public static final int MAX_CHILD_ISSUES = 20;

    /** Confluence reverse-engineering workflow: max page trees from a keyword search. */
    public static final int KEYWORD_TOP_RESULTS = 2;
}
