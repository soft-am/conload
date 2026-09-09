---
name: conload-map
description: Current conload / ONLOAD project map, package boundaries, entry points, build commands, and refactoring rules. Load first for any conload task.
---

# conload Project Map

Load this skill before working in the repository. It prevents repeated scans
and keeps future agents aligned with the current package structure.

## Application

conload is a Java 21 JavaFX desktop application that downloads Confluence,
Jira, and GitHub content into local Markdown context folders and runs CLI
agents in an xterm.js/WebView terminal backed by Pty4J. Vosk provides offline
speech recognition.

Stack: Java 21, JavaFX 21.0.2, Maven, Jackson 2.17, jsoup 1.17.2, pty4j
0.12.5, JNA, and Vosk 0.3.45. There is no test suite; Maven compilation and
packaging are the verification step.

## Build

```bash
mvn -DskipTests clean package
mvn javafx:run
```

`dependency-reduced-pom.xml` is generated. Do not edit it. Runtime JSON files
under `src/` are application state and must not be reverted as part of an
unrelated refactor.

## Discovery

Prefer the codebase-memory graph for code discovery:

1. `search_graph` for definitions and symbols.
2. `trace_path` for callers and dependencies.
3. `get_code_snippet` for exact source after finding a qualified name.
4. `query_graph` for multi-hop or aggregate questions.
5. `get_architecture` for an overview.

The graph may lag behind recent extractions. Check indexing status and re-index
in moderate mode if a recently created class is missing.

## Entry Point

`Launcher.main` → `App.main` → `App.start(Stage)` → scene composition through
the shell controller. `Theme.apply(...)` applies `styles/theme.css`. The
terminal is a real xterm.js WebView backed by Pty4J, not a process pipe.

## Package Map

| Package | Responsibility | Important classes |
|---|---|---|
| `com.conload.confluence` | Confluence REST and URLs | `ConfluenceClient`, `ConfluenceUrlParser` |
| `com.conload.jira` | Jira REST and Markdown conversion | `JiraClient`, `AdfConverter`, `JiraMarkdownExporter` |
| `com.conload.github` | GitHub commits and pull requests | `GitHubClient` |
| `com.conload.github.actions` | GitHub workflow runs/jobs/logs | `GitHubActionsClient` |
| `com.conload.markdown` | Confluence XHTML and Markdown conversion | `MarkdownConverter`, `MarkdownToHtmlConverter`, `DiagramExtractor` |
| `com.conload.model` | Domain and persisted models | `Project`, `QuickAction`, `AppConfig`, `SearchRequest` |
| `com.conload.service` | Facades and general services | `SearchDownloadService`, `ProjectService`, `OpencodeSessionService` |
| `com.conload.service.search` | Multi-source search execution | `ContentSearchService` |
| `com.conload.service.download` | Context download execution | `ContextDownloadService` |
| `com.conload.vosk` | Vosk model and recording lifecycle | `VoskModelManager`, `VoskRecorder`, `VoskLib` |
| `com.conload.ui` | Shell and screen facades | `AppShellController`, `ManagementScreensController`, `MainControllerSupport` |
| `com.conload.ui.components` | Shared JavaFX components | `UiFactory`, `AboutPanel`, `LocalContextFolderDialog` |
| `com.conload.ui.createcontext` | Search-context composition | `ContextAcquisitionController`, `SearchCriteriaPane`, `DownloadProgressPane` |
| `com.conload.ui.createcontext.search` | Search UI lifecycle | `SearchExecutionController` |
| `com.conload.ui.createcontext.download` | Download UI lifecycle | `ContextDownloadController` |
| `com.conload.ui.createcontext.presenter` | Result rendering and selection | `SearchResultsPresenter` |
| `com.conload.ui.projects` | Project workspace composition | `ProjectWorkspaceController`, `ProjectFilesPane`, `ProjectTreeItems`, `SessionFolderFiles` |
| `com.conload.ui.projects.files` | File-tree rendering and actions | `ProjectFileTreeCell` |
| `com.conload.ui.projects.sidebar` | Worktree sidebar | `WorktreeSidebarSection` |
| `com.conload.ui.projects.workspace` | Workspace terminal construction | `ProjectTerminalFactory` |
| `com.conload.ui.prompttemplate` | Prompt and quick actions | `PromptTemplatePanel`, `QuickActionsBar` |
| `com.conload.ui.prompttemplate.dialog` | Shared prompt editor | `PromptTemplateEditorDialog` |
| `com.conload.ui.workflow` | Inline workflow section + form builder | `WorkflowInlineSection`, `WorkflowFormBuilder`, `WorkflowHost` |
| `com.conload.ui.terminal` | PTY and WebView runtime | `CopilotTerminalPane`, `TerminalPtyController`, `TerminalWebViewController` |
| `com.conload.ui.terminal.session` | Session browser/state/export | `SessionBrowserPopup`, `CliSessionController`, `SessionExportCoordinator` |
| `com.conload.ui.management` | Project management screen | `ProjectManagementScreen` |
| `com.conload.ui.shell` | Focus-mode collaborator | `FocusModeController` |
| `com.conload.util` | Shared infrastructure | `Json`, `JsonStore`, `FileTreeOps`, `ProcessRunner`, `BackgroundTasks` |
| `com.conload.workflow` | Workflow engine (cross-context gathering + prompt) | `Workflow`, `PrepareToRefinementWorkflow`, `WorkflowContext`, `WorkflowRegistry`, `WorkflowEnvironment`, `WorkflowCallbacks`, `GitRemoteResolver` |
| `com.conload.workflow.crosscontext` | Unified cross-context builder (Jira→Confluence→GitHub recursion) | `CrossContextBuilder`, `CrossContextSource`, `CrossContextResult`, `ConfluenceTreeWriter`, `JiraKeyDiscoverer`, `CommitsAggregator`, `WordFrequencyAnalyzer`, `HierarchyDocWriter`, `TitleWords` |

## Hard Refactoring Limits

- No production class may exceed 800 physical lines.
- No production method, constructor, anonymous method, or inner-class method may exceed 100 physical lines.
- Controllers remain composition roots/facades.
- New collaborators receive narrow dependencies, records, suppliers, and
  callback interfaces. Never pass an entire controller.
- Do not extend `MainControllerSupport` for new code.

## Canonical Components

Use these before adding a new helper:

- `LocalContextFolderDialog` for local context-folder selection.
- `PromptTemplateEditorDialog` for prompt-template editing.
- `SearchResultsPresenter` for search result presentation and selection.
- `SessionFolderFiles` for session transcript and metadata lookup.
- `FileTreeOps` for recursive filesystem copying.
- `ProcessRunner` for external process execution/output capture.
- `Json.MAPPER` for Jackson operations.
- `QuickAction.extractParameters` for `${variable}` parsing.
- `CrossContextBuilder.createCrossContext` for unified cross-context gathering
  (Jira keys / Confluence URL / GitHub PR → recursive Jira + Confluence + commits
  + top-word Confluence search + hierarchy doc). Entry point for any workflow
  that needs cross-source context.

If two classes need the same behavior, put one implementation in the package
that owns the concern and inject caller-specific callbacks. Do not duplicate
dialogs, table factories, path validation, file operations, or process setup.

## Current Size Hotspots

The remaining classes requiring class-level extraction are:

- `ContextAcquisitionController` — approximately 1050 LOC.
- `ProjectWorkspaceController` — approximately 1128 LOC.

`CopilotTerminalPane`, `ProjectFilesPane`, and `AppShellController` are already
below 800 after the latest extraction batch. See
`REFACTORING_IMPLEMENTATION_PLAN.md` for the detailed next steps.

## Workflow System

The workflow system lets the user select a named context-gathering recipe
from a combo box in the prompt header, fill a dynamic form, and gather
cross-source context that gets substituted into a prompt template pushed
into the textarea. The system is designed so that **adding a new workflow
requires only one new class implementing `Workflow` and one new template
file** — no UI changes needed.

### Architecture (4 layers)

```
PromptTemplatePanel (header row: [✦ Prompt][combo][✦ Workflows][workflowCombo][mic][send])
        │
        │  setWorkflowSection(section)  — host calls this once at init
        ▼
WorkflowInlineSection (VBox, injected between header row and textarea)
   ├─ workflowCombo     — populated from WorkflowRegistry.all()
   ├─ formContainer     — built by WorkflowFormBuilder from inputFields()
   ├─ gatherSection     — spinner + log + file-count summary
   └─ actionRow         — Gather / Enough / Stop buttons
        │
        │  Gather → selectedWorkflow.accumulate(env, inputs, callbacks)
        ▼
Workflow (sealed interface)
   ├─ PrepareToRefinementWorkflow   (source: Jira keys)
   ├─ CodeReviewWorkflow            (source: GitHub PR URL(s), 1 or more)
   └─ ConfluenceReverseEngineeringWorkflow (source: Confluence URL or keyword)
        └─ CrossContextBuilder.createCrossContext(source, env, ownerRepo, root, cb, fullMode)
              ├── Phase A: normalizeSource  → seeds (jira keys + conf page IDs)
              ├── Phase B: expandJira       → recursive jira/confluence/commits
              ├── Phase C: extra conf trees
              ├── Phase D: expand epics
              ├── Phase E: top-word conf discovery  (full mode only)
              └── Phase F: hierarchy doc
```

### Key classes (by layer)

| Layer | Class | Role |
|---|---|---|
| UI host | `PromptTemplatePanel` | Places "Workflows" label + combo in header row; inserts section body above textarea via `setWorkflowSection()` |
| UI section | `WorkflowInlineSection` | Owns combo, form, gather/cancel, progress+log; collapses after gather |
| UI form | `WorkflowFormBuilder` | Builds `VBox` from `WorkflowFieldDefinition`s; text inputs + toggle checkboxes; returns `FormResult(container, valuesSupplier)` |
| UI host iface | `WorkflowHost` | Narrow interface the section calls: `config()`, `githubToken()`, `githubApiUrl()`, `workspacePath()`, `contextsDir()`, `activeProjectId()`, `setPromptText()`, `registerContext()`, `fullConfluenceFolder()` |
| Engine iface | `Workflow` | Sealed interface: `id()`, `displayName()`, `description()`, `inputFields()`, `accumulate()`, `templateResource()`, `buildPrompt()` (default impl loads template + `QuickAction.substitute`) |
| Engine impl | `PrepareToRefinementWorkflow` | Jira keys/URLs/keywords → `SourceInputClassifier` → `CrossContextBuilder` |
| Engine impl | `CodeReviewWorkflow` | GitHub PR URL(s) (1 or more, comma/space/newline separated) → `ContentSearchService.parseGitHubPrUrl` → `CrossContextSource.GitHubPr` or `GitHubPrs` |
| Engine impl | `ConfluenceReverseEngineeringWorkflow` | Confluence URL or keyword → `SourceInputClassifier` → URL direct, keyword CQL top-3 → `CrossContextSource.Confluence(List<urls>)` |
| Engine env | `WorkflowEnvironment` | Record passed to `accumulate()`: config, githubToken, githubApiUrl, workspacePath, contextsDir, projectId, fullConfluenceFolder |
| Engine ctx | `WorkflowContext` | Record returned from `accumulate()`: paths + summaries; `toVariableMap()` feeds `${var}` substitution in the template |
| Registry | `WorkflowRegistry` | `List.of(workflows)` — add new workflows here; first entry is default |
| Cross-context | `CrossContextBuilder` | Static `createCrossContext(source, env, ownerRepo, root, cb, fullMode)` — normalizes source → seeds → recursive Jira expansion → Confluence trees → GitHub commits → word-frequency discovery (full only) → hierarchy doc |
| Cross-context src | `CrossContextSource` | Sealed: `Jira(keys, extraConfluenceUrl)`, `Confluence(pageUrls)`, `GitHubPr(ownerRepo, prNumber)`, `GitHubPrs(List<GitHubPr>)` |
| Cross-context dedup | `CrossContextBuilder` fields | `visitedJira` (Set), `visitedConf` (Set, passed to `ConfluenceTreeWriter.download`), `visitedCommits` (Set, passed to `CommitsAggregator.writeCommits`) |
| Commits | `CommitsAggregator` | `writeCommits(dir, jiraKey, visitedShas)` — searches commits for a Jira key, skips SHAs already processed |
| Classifier | `SourceInputClassifier` | Shared URL-vs-keyword dispatch: `classifyConfluence()` → TREE_URL/KEYWORD, `classifyJira()` → ISSUE_URL/KEY/KEYWORD |
| Template | `src/main/resources/workflows/<id>.md.tpl` | Bundled (not user-editable); variables substituted via `QuickAction.substitute` |

### UI behavior (selection model)

1. **Startup**: combo shows "Workflow…" placeholder, no selection. Section is **hidden** (`UiFactory.hide(this)` in constructor). Textarea gets full space.
2. **User selects a workflow**: `onWorkflowSelected()` builds the form from `selectedWorkflow.inputFields()` → `UiFactory.show(this)`. Section appears **above the textarea**.
3. **User clicks Gather**: section runs `accumulate()` on a virtual thread; spinner + log are visible; Cancel button appears.
4. **Gather complete**: `buildPrompt(ctx)` substitutes template variables → `host.setPromptText(prompt)` → writes `workflow-info.json` metadata into `contextRoot` → `UiFactory.hide(this)` (section collapses). Textarea expands with the substituted prompt. Progress label shows file-count summary: `✓ Gathered: 47 files, 5 Jira, 12 Confluence, 8 commits`.
5. **Combo is the only show/hide control** — no collapse toggle. Selecting a different workflow rebuilds the form in place. The user cannot manually collapse without changing selection.

### Re-running a workflow from the file tree

After gather, the context folder contains `workflow-info.json` (workflowId, jiraKeys, repoOwnerRepo, confluenceDataSource, workspacePath, outputDocPath, gatheredAt, type). Right-clicking a context folder that contains this file shows a **"✦ Run Workflow"** menu item (after Rename, before Remove). Clicking it:
1. Reads `workflow-info.json` via `Json.MAPPER`
2. Looks up the `Workflow` via `WorkflowRegistry.findById(workflowId)`
3. Reconstructs a `WorkflowContext` (all paths → `contextRoot`, empty summary lists — template reads files directly)
4. Calls `workflow.buildPrompt(ctx)` — loads the template and substitutes `${contextRoot}`, `${outputDocPath}`, etc.
5. Pushes the substituted prompt to `sharedPromptPanel.setPromptText(prompt)` — no API calls

Wired via: `ProjectFileTreeCell.Callbacks.runWorkflow` → `ProjectFilesPane.setOnRunWorkflow` → `ProjectWorkspaceController.runWorkflowFromContext`.

### Header styling

Both the "Prompt" and "Workflows" labels use `prompt-header-accent-icon` + `bold` CSS classes, giving them `-accent-color` text fill (project tab color). The `WorkflowsIcon` SVG paths (`.workflow-seg`) get `-fx-fill: -accent-color` via `.prompt-header-accent-icon .workflow-seg` CSS rule. The workflow combo shares the `prompt-template-combo` CSS class with the template combo — same sizing (max 300px, `USE_PREF_SIZE`), hover, cursor, and colors.

### Important notes

- `WorkflowInlineSection` constructor does NOT auto-select (removed). The combo starts empty and hidden.
- `UiFactory.show(node)` / `UiFactory.hide(node)` set both `setVisible` and `setManaged`.
- `ComboBox.setOnAction` does NOT fire when re-selecting the already-selected item.
- The "Workflows" label uses `WorkflowsIcon` (14px sparkle SVG), styled as `prompt-header-accent-icon bold` — accent-colored, same treatment as the "Prompt" label and sparkle icon.
- The `WorkflowHost` Javadoc still references `WorkflowDialog` (deleted) — cosmetic only.
- `fullConfluenceFolder` is now a global setting in the Config/Settings tab, not per-workflow. It is injected into `WorkflowEnvironment` and passed to the template as `${confluenceDataSource}`.

### Full Mode vs Not-Full Mode

Every workflow has a **Full Mode** toggle (CheckBox, default **off**) in its
form, as does the standalone Cross Context section. The value flows:
`WorkflowFieldDefinition("fullMode", ..., toggle=true)` →
`WorkflowFormBuilder` renders CheckBox (prefill `"fullMode"="false"` from
`WorkflowInlineSection.onWorkflowSelected`) → `inputs.get("fullMode")` in
`accumulate()` → passed as `boolean fullMode` to
`CrossContextBuilder.createCrossContext(..., fullMode)`.
`CrossContextSection.fullModeBox` also starts unchecked.

**Full Mode (opt-in — toggled on by the user):**
- Recursion depth capped at `MAX_DEPTH_FULL` (= 3) — deep but bounded
- Epic children are recursively expanded (full tree)
- Related keys per issue capped at `MAX_RELATED_FULL` (= 10)
- Phase E: top-word Confluence discovery runs (searches Confluence for words
  found in the main/epic issue titles)

**Not-Full Mode (default):**
- Recursion depth capped at `MAX_DEPTH_NON_FULL` (= 1) — only direct related issues, no grandchildren
- Epics: summary-only (the `jira_<KEY>.md` is fetched + exported, but no child Jira issues are expanded)
- Related keys per issue capped at `MAX_RELATED_NON_FULL` (= 10)
- Phase E (top-word Confluence discovery) is **skipped entirely**

### Global dedup (all modes)

Three dedup sets in `CrossContextBuilder` prevent re-fetching across all
iterations and phases:

| Set | What it deduplicates | Where it's checked |
|---|---|---|
| `visitedJira` | Jira issue keys | `expandJira` — early return if key already processed |
| `visitedConf` | Confluence page IDs | Passed to `ConfluenceTreeWriter.download` — checked at entry, prevents re-download of same page tree |
| `visitedCommits` | GitHub commit SHAs | Passed to `CommitsAggregator.writeCommits` — skips SHAs already fetched/diffed in any previous iteration |

These sets exist in both full and not-full modes — they prevent redundant API
calls and duplicate files regardless of depth setting.

### Gathering summary

After `accumulate()` completes, `WorkflowInlineSection.onGatherComplete` walks
the context root recursively and counts:
- Total files (all files in the tree)
- Jira issues (`jira_*.md` files)
- Confluence pages (`confluence_*` / `conf_*` files)
- Commits (`commits_*.json` files)

Displayed as progress label: `✓ Gathered: 47 files, 5 Jira, 12 Confluence, 8 commits — prompt loaded below.`

### Enough vs Stop (gather cancellation)

Two buttons are shown during a cross-context gather (both in the inline
workflow section and the standalone Cross Context search popup):

| | Enough (soft stop) | Stop (hard stop) |
|---|---|---|
| Signal | `enoughRequested = true` | `hardStopped = true` + `thread.interrupt()` |
| `isCancelled()` | `true` (breaks loop boundaries) | `true` |
| `isHardStopped()` | **false** | **true** |
| In-flight HTTP call | **completes naturally** | **interrupted → re-thrown → unwinds stack in ms** |
| Phase F (hierarchy doc) | runs (on partial) | skipped (exception propagates) |
| `accumulate()` returns | partial `WorkflowContext` | throws `WorkflowStoppedException` |
| UI finalize | registers context + pushes prompt + "(partial — Enough)" | "Stopped", no finalize, partial files left on disk unregistered |

**How Stop gets fast (was slow before):**
`java.net.http.HttpClient.send()` IS interruptible — `task.cancel(true)` /
`thread.interrupt()` causes it to throw `InterruptedException`. The problem
was that ~10 broad `catch (Exception e)` blocks along the pipeline swallowed
this interrupt, logged it as a recoverable error via `onError(...)`, and
continued iterating. Now each HTTP-wrapping catch block checks
`callbacks.isHardStopped() || WorkflowCallbacks.isInterruptCause(e)` and
re-throws a `WorkflowStoppedException` (unchecked) so the entire stack
unwinds in milliseconds instead of waiting for the next `isCancelled()` loop
boundary.

Key files:
- `WorkflowCallbacks.isHardStopped()` — default `false`; `WorkflowStoppedException` — unchecked.
- `WorkflowCallbacks.isInterruptCause(Throwable)` — static helper that checks the cause chain for `InterruptedException` / `InterruptedIOException`.
- `WorkflowInlineSection` — Enough/Stop buttons, `gatherThread.interrupt()` on Stop, `onGatherFailed` handles `WorkflowStoppedException`.
- `ContextDownloadController.startCrossContextGather` — Enough/Stop via `DownloadProgressPane`, callbacks with `isHardStopped()`, `failure()` handles `WorkflowStoppedException`.
- `DownloadProgressPane` — Enough button (4th constructor arg `Runnable enough`), `showEnough(boolean)` / `setEnoughEnabled(boolean)`.

Re-throw guards are in these catch blocks:
- `ConfluenceTreeWriter.download` (page tree)
- `CrossContextBuilder.exportJiraMd`, `resolveExtraConfluence`, `searchConfluenceForWord`, `normalizeGitHubPr`/`normalizeGitHubPrs` (PR fetch + commits)
- `CommitsAggregator.writeCommits` (search + per-commit diff)
- `JiraKeyDiscoverer.epicLinkKey`, `confluencePageIds` (remote links)
- `JiraMarkdownExporter.fetchAndExport` (attachment download loop)
- `PrepareToRefinementWorkflow.resolveJiraKeys` (JQL keyword search)
- `ConfluenceReverseEngineeringWorkflow.resolvePageUrls` (CQL keyword search)

### Adding a new workflow (checklist)

1. Create `src/main/java/com/conload/workflow/MyWorkflow.java` implementing `Workflow`.
2. Add it to `WorkflowRegistry.WORKFLOWS`.
3. Add `MyWorkflow` to the `permits` clause of the `Workflow` sealed interface.
4. Create `src/main/resources/workflows/my-workflow.md.tpl` template file.
5. Declare input fields via `List.of(new WorkflowFieldDefinition(key, label, prompt, required, multiline), ...)`.
   - For a toggle (checkbox), use the 6-arg constructor with `toggle=true`.
6. Implement `accumulate()` — use `SourceInputClassifier` for any Confluence/Jira input, use `CrossContextBuilder.createCrossContext(source, env, ownerRepo, contextRoot, callbacks, fullMode)` if cross-source gathering is needed.
7. No UI changes — `WorkflowInlineSection` + `WorkflowFormBuilder` handle rendering automatically.

### Shared classifier (`SourceInputClassifier`)

Used by **both** the download-context search (`ContentSearchService`) and the
workflow (`PrepareToRefinementWorkflow` resolveJiraKeys + `CrossContextBuilder`
resolveExtraConfluence). Single source of truth for URL-vs-keyword dispatch:

- **Confluence**: `classifyConfluence(raw)` → `TREE_URL` (page-tree download) or `KEYWORD` (CQL search).
- **Jira**: `classifyJira(raw)` → `ISSUE_URL` or `KEY` (direct fetch) or `KEYWORD` (JQL `text~"..."` search).

Downstream result/download buckets (`CONF_SEARCH`, `CONF_TREE`, `JIRA_SEARCH`,
`JIRA_URL`) are **not** changed — the classifier only affects dispatch, not
how results are stored.

## Safety Rules

- Never simplify or remove `TerminalHtmlBuilder` custom CSS/theme.
- Never change terminal scroll behavior.
- Preserve existing public APIs and JavaFX callback behavior.
- Keep JavaFX out of service-layer components where possible.
- Do not revert unrelated runtime state or user worktree changes.
- Run `mvn -DskipTests clean package` and `git diff --check` after every batch.
