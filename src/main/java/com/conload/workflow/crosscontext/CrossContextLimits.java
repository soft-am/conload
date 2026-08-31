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
    public static final int MAX_DEPTH_FULL = 5;

    /** Not-full mode: max number of related keys to expand per issue. */
    public static final int MAX_RELATED_NON_FULL = 10;

    /** Max Confluence pages returned per CQL keyword search. */
    public static final int MAX_CONFLUENCE_SEARCH_RESULTS = 10;

    /** Max top-frequency words used for the often-words Confluence discovery phase. */
    public static final int MAX_OFTEN_WORDS = 3;

    /** Max commits fetched and written per Jira key. */
    public static final int MAX_COMMITS_PER_KEY = 30;

    /** Confluence reverse-engineering workflow: max page trees from a keyword search. */
    public static final int KEYWORD_TOP_RESULTS = 3;
}
