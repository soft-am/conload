---
name: conload-refactoring
description: DRY-first refactoring workflow for conload. Use when splitting god classes, creating package collaborators, reducing duplication, or enforcing the 800-line class and 100-line method limits.
---

# conload Refactoring Guide

## Before Editing

Read:

- `AGENTS.md`
- `REFACTORING_IMPLEMENTATION_PLAN.md`
- `.opencode/skills/conload-map/SKILL.md`

Use the codebase-memory graph first for definitions, callers, and dependencies.
Use regular grep/glob only for literals, configuration, resources, and when the
graph is stale.

## Hard Limits

- Production classes must be below 800 physical lines.
- Production methods, constructors, anonymous methods, and inner-class methods
  must be at most 100 physical lines.
- A facade may preserve a public API, but implementation belongs in cohesive
  package-level collaborators.
- Do not move a god class into another god class.

## Package Placement

Put new code beside the concern it owns:

- Search execution: `com.conload.service.search`
- Download execution: `com.conload.service.download`
- GitHub Actions: `com.conload.github.actions`
- Vosk model/recording: `com.conload.vosk`
- Context search UI: `com.conload.ui.createcontext.search`
- Context download UI: `com.conload.ui.createcontext.download`
- Context result rendering: `com.conload.ui.createcontext.presenter`
- Terminal runtime: `com.conload.ui.terminal`
- Terminal sessions: `com.conload.ui.terminal.session`
- Project files: `com.conload.ui.projects.files`
- Project sidebar: `com.conload.ui.projects.sidebar`
- Workspace components: `com.conload.ui.projects.workspace`
- Shared JavaFX components: `com.conload.ui.components`
- Shared filesystem/process infrastructure: `com.conload.util`

## Canonical DRY Components

Search these before creating anything:

- `LocalContextFolderDialog`
- `PromptTemplateEditorDialog`
- `SearchResultsPresenter`
- `SessionFolderFiles`
- `FileTreeOps`
- `ProcessRunner`
- `Json.MAPPER`
- `QuickAction.extractParameters`

When multiple classes require the same behavior, create one component and pass
caller-specific behavior through a narrow callback or interface. Do not copy
JavaFX dialogs, table factories, path validation, process execution, or file
operations into each caller.

## Current Remaining Work

The only production classes currently above 800 lines are:

- `ContextAcquisitionController` at approximately 1050 LOC.
- `ProjectWorkspaceController` at approximately 1128 LOC.

Recommended extractions:

### Context acquisition

- `SearchDialogCoordinator` for popup construction and mode transitions.
- Complete `ContextDownloadController` ownership of selection, target, task,
  and completion handling.
- Keep `SearchResultsPresenter` as the sole result-presentation owner.
- Use `SearchCriteriaPane` as the sole criteria-row/state owner.

### Project workspace

- `ProjectWorktreeCoordinator` for Git worktree discovery and lifecycle.
- `ProjectTabStripController` for project tabs and fallback selection.
- `ProjectSidebarSynchronizer` for context/session path refresh.
- Reuse `ProjectTerminalFactory` and `SessionFolderFiles`.

## Safe Extraction Workflow

1. Measure the current class and locate all callers.
2. Group fields and methods by one responsibility.
3. Search for the same behavior in other classes.
4. Choose the owning package and define a narrow constructor/API.
5. Move code once; migrate all callers to that implementation.
6. Keep compatibility wrappers only when external callers require them.
7. Delete confirmed dead code and obsolete commented implementations.
8. Build and verify limits:

```bash
mvn -DskipTests clean package
git diff --check
```

## Terminal Safety

- Do not alter `TerminalHtmlBuilder` custom CSS/theme.
- Do not alter terminal scroll behavior.
- Do not replace xterm.js/WebView with process pipes.
- Preserve PTY cleanup, reader lifecycle, session callbacks, and FX-thread
  dispatch.

## Completion Checklist

- Every production class is below 800 physical lines.
- Every production method is at most 100 physical lines.
- No duplicate canonical component exists.
- Existing public APIs and UI callbacks compile.
- `mvn -DskipTests clean package` prints `BUILD SUCCESS`.
- `git diff --check` passes.
- No unrelated runtime state is reverted.
- No commit is created unless explicitly requested.
