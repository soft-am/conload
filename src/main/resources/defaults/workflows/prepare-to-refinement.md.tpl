You are preparing a Jira refinement/implementation document. Use the cross-context gathered below and the project codebase to produce a thorough analysis.

## Context Location

All cross-context (Jira issues, Confluence pages with subpages, GitHub commits with diffs) lives in a single recursive hierarchy:

- **Context root:** ${contextRoot}
- **Hierarchy map:** ${contextRoot}/cross_context_hierarchy.md

Read the hierarchy map FIRST — it shows the full tree of related Jira issues, their linked Confluence pages (with recursive subpages and media), GitHub commits (with diffs, author, and date), and any often-word-discovered Confluence pages.

Each Jira issue has its own folder with:
  - `jira_<KEY>.md` — the issue as Markdown
  - `commits_<KEY>.json` — aggregated commits mentioning that key (sha, author, date, message, diff)
  - `confluence_<Title>.md` — linked Confluence pages and subpages
  - `related_context/` — related Jira issues (recursively, same structure)

- **Confluence full-data reference:** ${confluenceDataSource}
- **Project workspace (code):** ${workspacePath}
- **GitHub repository:** ${repoOwnerRepo}
- **Jira keys:** ${jiraKeys}

---

## Your Task

1. Read ${contextRoot}/cross_context_hierarchy.md to understand the full context tree.
2. Read ALL context files under ${contextRoot} — every `jira_*.md`, `confluence_*.md`, and `commits_*.json`.
3. Search the project code at "${workspacePath}" for the functionality related to the Jira keys "${jiraKeys}".
4. If "${confluenceDataSource}" is set, also read the full Confluence data folder there.

Produce the document at: ${outputDocPath}

The document MUST contain these sections:

### 1. Current Data Flow

Provide a very short summary of the current data flow:
- Input data (API endpoints, consumed messages, etc.) — from entry point to persistence
- Usage: read data → transform → output

For related Jiras: withhold specific class names and database table names, but include a data transformation diagram showing how the hardware-related code looks now. Then briefly describe what needs to be implemented for the current Jiras.

### 2. Clarifications / Questions for Requirements Engineers / BAs

If it is not possible to fully define the implementation, or if clarifications are needed, list specific questions for Requirements Engineers / BAs. Otherwise state "No clarifications needed."

### 3. Previous Related Jira Stories

From the GitHub commit data (${commits_*.json} files) and the related-Jira folders in the hierarchy, list previous Jira stories that have been implemented, including WHO implemented them and WHEN. Use the commit author names and dates from the JSON files.

### 4. Related Confluence Documentation

List the related Confluence documents (links or names) that relate to the Jira functionality. Include the creator and date where possible. Reference the downloaded pages in ${contextRoot} (the `confluence_*.md` files).

### 5. Draft Estimate

Provide a rough draft estimate (in story points or person-days) for the implementation, with a brief justification based on the complexity of the current data flow and the scope of changes needed.

---

Write the complete document to ${outputDocPath}. Use Markdown formatting.
