package com.conload.workflow.crosscontext;

import java.util.List;

/**
 * Sealed source type for {@link CrossContextBuilder#createCrossContext}.
 * The permitted variants map to the entry points:
 * <ol>
 *   <li>{@link Jira} — one or more Jira issue keys</li>
 *   <li>{@link Confluence} — one or more Confluence page URLs (tree download, then Jira-key discovery)</li>
 *   <li>{@link GitHubPr} — a single GitHub PR (diff download, then Jira-key + Confluence-URL discovery)</li>
 *   <li>{@link GitHubPrs} — multiple GitHub PRs (possibly from different repos)</li>
 * </ol>
 * All four funnel through {@code normalizeSource} into the same recursive core.
 */
public sealed interface CrossContextSource
        permits CrossContextSource.Jira, CrossContextSource.Confluence,
                CrossContextSource.GitHubPr, CrossContextSource.GitHubPrs {

    /** Source 1: start from Jira issue keys (optionally with an extra Confluence page URL). */
    record Jira(List<String> keys, String extraConfluenceUrl) implements CrossContextSource {
        public Jira(List<String> keys) { this(keys, ""); }
        public Jira {
            keys = keys == null ? List.of() : List.copyOf(keys);
            extraConfluenceUrl = extraConfluenceUrl != null ? extraConfluenceUrl : "";
        }
    }

    /** Source 2: start from one or more Confluence page URLs. All page trees are
     *  downloaded into the same context root before Jira-key discovery. */
    record Confluence(List<String> pageUrls) implements CrossContextSource {
        public Confluence(String pageUrl) { this(List.of(pageUrl)); }
        public Confluence {
            pageUrls = pageUrls == null ? List.of() : List.copyOf(pageUrls);
        }
    }

    /** Source 3: start from a single GitHub PR. */
    record GitHubPr(String ownerRepo, int prNumber) implements CrossContextSource {}

    /** Source 4: start from multiple GitHub PRs (possibly from different repos).
     *  All PRs are fetched, their artifacts saved, and discovered Jira keys +
     *  Confluence page IDs are accumulated before the recursive core runs. */
    record GitHubPrs(List<GitHubPr> prs) implements CrossContextSource {
        public GitHubPrs {
            prs = prs == null ? List.of() : List.copyOf(prs);
        }
    }
}
