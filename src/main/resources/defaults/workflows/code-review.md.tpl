You are performing a structured code review. Use the cross-context gathered below (GitHub PRs, related Jira issues, Confluence pages, commit history) and the project codebase to produce a thorough review.

## Context Location

All cross-context (PR artifacts, Jira issues, Confluence pages with subpages, GitHub commits with diffs) lives in a single recursive hierarchy:

- **Context root:** ${contextRoot}
- **Hierarchy map:** ${contextRoot}/cross_context_hierarchy.md

Read the hierarchy map FIRST — it shows the full tree of PR artifacts, discovered Jira issues, their linked Confluence pages, and GitHub commits.

The GitHub PR artifacts are under:
  - `${contextRoot}/github/pr_<number>.json` — PR metadata (title, body, labels)
  - `${contextRoot}/github/pr_<number>-diff.json` — PR diff content
  - `${contextRoot}/github/commits_*.json` — commits mentioning related Jira keys

One or more PRs may have been gathered. Review ALL of them.

- **Confluence full-data reference:** ${confluenceDataSource}
- **Project workspace (code):** ${workspacePath}
- **GitHub repository:** ${repoOwnerRepo}
- **PR ref(s):** ${jiraKeys}

---

## Your Task

1. Read ${contextRoot}/cross_context_hierarchy.md to understand the full context tree.
2. Read ALL PR diffs and metadata under `${contextRoot}/github/`.
3. Read ALL related context files — every `jira_*.md`, `confluence_*.md`, and `commits_*.json`.
4. Search the project code at "${workspacePath}" for the functionality touched by the PR(s).
5. If "${confluenceDataSource}" is set, also read the full Confluence data folder there.

Produce the review document at: ${outputDocPath}

The document MUST contain these sections:

### 1. PR Summary

For each PR, summarize its purpose, scope, and the changes it introduces. What problem does it solve? Which Jira issues does it reference? If multiple PRs are reviewed, group related ones together.

### 2. Related Jira & Confluence Context

List the related Jira issues (keys, summaries, statuses) and Confluence documentation that provide context for the PR(s). Reference the files in ${contextRoot}.

### 3. Code Quality Assessment

Evaluate the code changes across all PR(s):
- **Correctness** — are there logic errors, edge cases, or race conditions?
- **Design** — does the change follow existing patterns? Is it overly complex?
- **Naming & readability** — are names clear and consistent?
- **Error handling** — are errors handled appropriately?

### 4. Security & Performance

Identify any security concerns (input validation, secrets, injection) and performance issues (N+1 queries, unnecessary allocations, blocking operations).

### 5. Test Coverage

Assess test coverage for the changes. Which code paths are tested? Which are not? Suggest specific test cases that should be added.

### 6. Risks & Recommendations

List risks (breaking changes, migration concerns, deployment order, cross-PR conflicts) and actionable recommendations for the author(s) before merging.

---

Write the complete review to ${outputDocPath}. Use Markdown formatting.
