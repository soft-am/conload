package com.conload.workflow.crosscontext;

import java.nio.file.Path;

/**
 * Immutable result of a {@code createCrossContext} run.
 *
 * @param contextRoot      the root directory where all cross-context files live
 * @param jiraCount       number of Jira issues expanded (including related/epic)
 * @param confluenceCount number of Confluence pages downloaded (including subpages)
 * @param commitCount     number of commit JSON files written
 * @param hierarchyFile    path to {@code cross_context_hierarchy.md}
 */
public record CrossContextResult(
        Path contextRoot,
        int jiraCount,
        int confluenceCount,
        int commitCount,
        Path hierarchyFile
) {}
