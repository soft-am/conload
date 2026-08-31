You are reverse-engineering the architecture and behavior of a system from Confluence documentation. Use the cross-context gathered below (Confluence page trees, related Jira issues, GitHub commits) and the project codebase to produce a thorough analysis.

## Context Location

All cross-context (Confluence pages with subpages, Jira issues, GitHub commits with diffs) lives in a single recursive hierarchy:

- **Context root:** ${contextRoot}
- **Hierarchy map:** ${contextRoot}/cross_context_hierarchy.md

Read the hierarchy map FIRST — it shows the full tree of downloaded Confluence pages, discovered Jira issues, their linked pages, and GitHub commits.

Each Confluence page is stored as `confluence_*.md` with any attachments and subpages. Jira issues discovered from Confluence content are in their own folders with related context.

- **Confluence full-data reference:** ${confluenceDataSource}
- **Project workspace (code):** ${workspacePath}
- **GitHub repository:** ${repoOwnerRepo}
- **Search term / source:** ${jiraKeys}

---

## Your Task

1. Read ${contextRoot}/cross_context_hierarchy.md to understand the full context tree.
2. Read ALL Confluence page files under ${contextRoot} — every `confluence_*.md`.
3. Read ALL discovered Jira issues — every `jira_*.md`.
4. Read commit data — every `commits_*.json` — to understand implementation history.
5. Search the project code at "${workspacePath}" for the functionality described in the Confluence pages.
6. If "${confluenceDataSource}" is set, also read the full Confluence data folder there.

Produce the analysis document at: ${outputDocPath}

The document MUST contain these sections:

### 1. System Overview

Provide a high-level overview of the system as described in the Confluence documentation. What is the purpose, scope, and boundaries of the system?

### 2. Documented Architecture

Reverse-engineer the architecture:
- **Components** — what major components/modules are described?
- **Data flow** — how does data move through the system?
- **Integrations** — what external systems does it interact with?
- **Storage** — what persistence mechanisms are used?

Map each documented component to actual code locations in "${workspacePath}" where possible.

### 3. Data Model

Extract the data model from the Confluence documentation. List entities, their attributes, and relationships. Cross-reference with actual database tables, model classes, or schema files in the codebase.

### 4. Business Rules & Workflows

List the business rules and workflows described in the documentation. For each, identify:
- Where it is implemented in the code
- Whether the implementation matches the documentation
- Any gaps or discrepancies

### 5. Related Jira Issues & Implementation History

From the discovered Jira issues and GitHub commit data, list what has been implemented: features, bug fixes, and changes. Include WHO implemented them and WHEN, using the commit author names and dates.

### 6. Documentation vs. Code Gaps

Identify where the documentation is outdated, incomplete, or contradicts the actual code. Provide specific file paths and line numbers from the codebase where mismatches occur.

### 7. Recommendations

Suggest documentation updates, code improvements, or further investigation areas to close the gaps between documentation and implementation.

---

Write the complete analysis to ${outputDocPath}. Use Markdown formatting.
