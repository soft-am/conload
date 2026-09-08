# conload — Developer Documentation

Technical documentation: Cross-Context Engine internals, the workflow system,
building from source, project structure, and runtime state files. For the
user-facing overview, features, and installation see the [README](../README.md).

## Contents

- [Cross-Context Engine](#cross-context-engine) — the six phases, global deduplication, source normalization
- [Workflow system internals](#workflow-system-internals) — how to add a new workflow
- [Build from source](#build-from-source) — requirements, IDE setup, per-OS JARs, native installers
- [Project Structure](#project-structure) — package map
- [Runtime State Files](#runtime-state-files) — what conload writes under `~/.conload/`
- [Bundled Templates & Resources (read-only)](#bundled-templates--resources-read-only)

---

## Cross-Context Engine

This is the core of what makes `conload` more than a downloader + terminal.

Give it **one** starting point — a Jira key, a GitHub PR URL, or a Confluence page — and the `CrossContextBuilder`
builds a **recursive cross-source context tree** automatically. It follows links *between* sources: a Jira issue links
to a Confluence page, which contains a Jira key in its body, whose commits mention another Jira key — and the engine
walks the entire graph with **global deduplication** (no page, issue, or commit is fetched twice).

### The six phases

```
  Starting point (any one):
     Jira key        Confluence URL      GitHub PR URL(s)
         └──────────────┬──────────────────────┘
                        ▼
  ┌─── CrossContextBuilder ───────────────────────────────┐
  │                                                        │
  │  Phase A — Normalize source                            │
  │    Jira key → direct seed                              │
  │    Confluence URL → download page tree → discover       │
  │      Jira keys from page content → seed                 │
  │    GitHub PR → fetch PR diff + commit messages →        │
  │      scan for Jira keys + Confluence URLs → seed       │
  │                                                        │
  │  Phase B — Recursive Jira expansion          ┌──────┐  │
  │    For each seed key:                       │        │  │
  │      1. Fetch + export jira_<KEY>.md         │ loops  │  │
  │      2. Discover epic link (if any)          │ back   │  │
  │      3. Download linked Confluence          │ here   │  │
  │         page trees (subpages + media)       │        │  │
  │      4. Aggregate GitHub commits            │        │  │
  │         mentioning KEY → commits_<KEY>.json  │        │  │
  │      5. Find related Jira keys               │        │  │
  │         (issuelinks, subtasks, content)     │        │  │
  │      6. Recurse into each related key       └──────┘  │
  │         → related_context/ subfolder                    │
  │                                                        │
  │  Phase C — Extra Confluence trees                       │
  │    Page IDs discovered from PR scan are                 │
  │    downloaded as additional page trees                  │
  │                                                        │
  │  Phase D — Epic expansion                               │
  │    Discovered epics are expanded with the same          │
  │    recursive logic as Phase B                           │
  │                                                        │
  │  Phase E — Top-word Confluence discovery (full mode)   │
  │    Top-3 words from the main/epic issue title that      │
  │    appear frequently across all context files are      │
  │    used to search Confluence for additional             │
  │    topically-related pages → new page trees             │
  │                                                        │
  │  Phase F — Hierarchy document                          │
  │    Walks the entire output tree and writes               │
  │    cross_context_hierarchy.md — a human-readable         │
  │    map tagged [jira] [confluence] [commits] [pr] [often]│
  │                                                        │
  └────────────────────────────────────────────────────────┘
                        │
                        ▼
         cross_context_hierarchy.md  ←  read this first
         jira_*.md, confluence_*.md, commits_*.json,
         github/pr_*.json, related_context/, often_words_*/
```

### What you get

A single folder containing:

| File / directory                | Contents                                                                                                                                                                          |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `cross_context_hierarchy.md`    | Human-readable map of everything gathered (read this first). Tags every file with its source: `[jira]`, `[confluence]`, `[commits]`, `[pr]`, `[often]`. Includes a count summary. |
| `jira_<KEY>.md`                 | Each Jira issue as Markdown — summary, description, status, comments, linked issues, attachments. Includes metadata table.                                                        |
| `confluence_<Title>.md`         | Each linked Confluence page with recursive subpages and media attachments (images, diagrams).                                                                                     |
| `commits_<KEY>.json`            | Aggregated GitHub commits mentioning each Jira key — sha, author, date, message, full diff.                                                                                       |
| `github/pr_<number>.json`       | PR metadata — title, body, labels, state.                                                                                                                                         |
| `github/pr_<number>-diff.json`  | Full PR diff content.                                                                                                                                                             |
| `related_context/`              | Subfolders with the exact same structure, recursively. Each related Jira key gets its own folder with its issues, Confluence pages, and commits.                                  |
| `often_words_confluence_pages/` | (Full mode only) Additional Confluence pages discovered via top-word analysis.                                                                                                    |

### Global deduplication

Three dedup sets prevent redundant API calls across **all** iterations and phases, in both full and fast mode:

| Set              | What it prevents                             | Where it's checked                                                     |
|------------------|----------------------------------------------|------------------------------------------------------------------------|
| `visitedJira`    | Re-fetching the same Jira issue              | `expandJira` — early return if key already processed                   |
| `visitedConf`    | Re-downloading the same Confluence page tree | Passed to `ConfluenceTreeWriter.download` — checked at entry           |
| `visitedCommits` | Re-fetching the same commit SHA              | Passed to `CommitsAggregator.writeCommits` — skips SHAs already diffed |

### Source normalization (Phase A in detail)

The engine doesn't require a specific input type — it normalizes whatever you give it into a set of **seed Jira keys** +
**extra Confluence page IDs**:

- **Jira input** (key, URL, or keyword): issue URLs and bare keys are taken directly; keywords trigger a JQL
  `text~"..."` search whose results are added.
- **Confluence input** (URL or keyword): a URL downloads the page tree directly, then Jira keys are discovered from the
  page content and become seeds; a keyword runs a CQL search, downloads the top 3 result page trees, then discovers Jira
  keys from their combined content.
- **GitHub PR input** (one or more URLs): each PR is fetched with its commits; the PR title, body, and commit messages
  are scanned for Jira keys and Confluence page URLs — those become the seeds for the recursive expansion.

This means you can start from *any* source and the engine will find the connections automatically.

### Recursive Jira expansion (Phase B in detail)

For each seed Jira key, the engine:

1. **Fetches and exports** `jira_<KEY>.md` (via `JiraMarkdownExporter` — includes description, comments, linked issues,
   attachments).
2. **Discovers the epic** — if the issue has an "Epic Link" field, the epic key is queued for Phase D.
3. **Downloads linked Confluence pages** — remote links and ADF URLs in the issue body are resolved to Confluence page
   IDs, then each page tree (including subpages and media attachments) is downloaded via `ConfluenceTreeWriter`.
4. **Aggregates GitHub commits** — `CommitsAggregator` searches the repo's commit history for messages mentioning the
   Jira key, skipping SHAs already processed. Each commit includes sha, author, date, message, and full diff, saved as
   `commits_<KEY>.json`.
5. **Finds related Jira keys** — from `issuelinks`, subtasks, and by scanning the issue's own content for key patterns.
6. **Recurses** — each related key gets a `related_context/<KEY>/` subfolder and the same five steps run again (up to
   the configured depth limit).

### Top-word discovery (Phase E in detail, full mode only)

After the recursive expansion, the engine analyzes word frequency across all gathered `*.md` and `*.json` files. It
finds the top 3 words that (a) appear in the main or epic Jira issue title and (b) occur most frequently across the
entire context corpus. These words are then used as Confluence CQL search terms to discover additional pages that may
not be directly linked but are topically related.

**Developer value:** This catches documents you didn't know were connected. Your Jira ticket says "authentication token
rotation" — the engine finds a Confluence page about "session token lifecycle" that nobody linked, because the words
overlap. The agent gets context you would have missed.

### Hierarchy map (Phase F)

`HierarchyDocWriter` walks the actual filesystem tree and renders `cross_context_hierarchy.md` — a text-tree
representation of every file in the context folder, tagged by source (`[jira]`, `[confluence]`, `[commits]`, `[pr]`,
`[often]`), with a count summary at the top. The prompt template tells the agent to read this file *first*, so it gets
the full map before diving into individual files.

### Example output tree

```
context_PROJ-123/
├── cross_context_hierarchy.md          ← read this first
├── PROJ-123/
│   ├── jira_PROJ-123.md                ← the main issue
│   ├── confluence_Token_Rotation.md     ← linked Confluence page + subpages
│   ├── commits_PROJ-123.json           ← 8 commits mentioning PROJ-123
│   └── related_context/
│       ├── PROJ-456/                   ← related issue (from issuelinks)
│       │   ├── jira_PROJ-456.md
│       │   ├── confluence_Auth_Spec.md
│       │   ├── commits_PROJ-456.json
│       │   └── related_context/
│       │       └── PROJ-789/           ← another level of recursion
│       │           └── ...
│       └── PROJ-100/
│           └── ...
├── PROJ-200 (Epic)/                    ← the parent epic, expanded
│   ├── jira_PROJ-200.md
│   └── related_context/
│       ├── PROJ-201/
│       └── PROJ-202/
└── often_words_confluence_pages/       ← top-word discovery (full mode)
    ├── confluence_Session_Lifecycle.md
    └── confluence_JWT_Best_Practices.md
```

---

## Workflow system internals

Workflows are built-in recipes that use the Cross-Context Engine to gather context and generate a ready-to-send
prompt. The user-facing workflow guide (selecting, running, Full vs. Fast mode, built-in workflows) is in the
[README](../README.md#basic-workflow).

### Extensibility

The workflow system is designed so that **adding a new workflow requires only one new class implementing `Workflow` and
one new template file** — no UI changes needed. The `Workflow` sealed interface declares `id()`, `displayName()`,
`inputFields()`, `accumulate()`, and `templateResource()`. The `WorkflowFormBuilder` and `WorkflowInlineSection` handle
all rendering automatically from the field definitions.

To add a new workflow:

1. Create `src/main/java/com/conload/workflow/MyWorkflow.java` implementing `Workflow`.
2. Add it to `WorkflowRegistry.WORKFLOWS`.
3. Add the new class to the `permits` clause of the `Workflow` sealed interface.
4. Create `src/main/resources/workflows/my-workflow.md.tpl`.
5. Declare input fields via `WorkflowFieldDefinition` (use the 6-arg constructor with `toggle=true` for checkboxes).
6. Implement `accumulate()` — use `SourceInputClassifier` for Confluence/Jira input dispatch and
   `CrossContextBuilder.createCrossContext(...)` for cross-source gathering.

No UI changes are needed.

---

## Build from source

### Requirements

To **run** `conload`, install it via Homebrew or download a JAR — see
[Download & install](../README.md#download--install). To **build from source** or rebuild
the installers yourself:

- **JDK 21+** (any distribution: OpenJDK, Oracle, Temurin, etc.)
- **Maven 3.8+**
- For native installers: a **JavaFX-bundled JDK** (Liberica Full or Azul Zulu FX 21+)

### Run from source

Requires **JDK 21** and **Maven 3.8+**.

> ⚠ **JDK 21 is mandatory — not 22+.** Running on a newer JDK causes a SIGSEGV
> crash in the JavaFX WebView native bridge (`get_method_id` in `libjvm.dylib` /
> `libjfxwebkit.dylib`). If you see a "segmentation fault" or "Bad pointer
> dereference" crash right after the terminal WebView loads, your `java` is too
> new. Install JDK 21 and use it explicitly:
>
> ```bash
> brew install openjdk@21                    # macOS
> # or
> brew install --cask liberica-jdk21-full    # macOS (bundles JavaFX)
> ```
>
> Then run conload with:
> ```bash
> /path/to/jdk-21/bin/java -jar target/conload-1.0.1-mac.jar
> ```
>
> Or set `JAVA_HOME` to JDK 21 before running `mvn` or `java`.

```bash
git clone https://github.com/soft-am/conload.git
cd conload
mvn javafx:run
```

That's it. The app launches, creates a `~/.conload/` folder for its state on first run,
and seeds the default CLI types, prompts, and workflow templates from the bundled
defaults — nothing needs to be configured before first use.

### Run from IntelliJ IDEA

1. Open the project in IntelliJ IDEA.
2. Install **JDK 21** if you don't have it:

   ```bash
   brew install --cask liberica-jdk21-full   # macOS — bundles JavaFX
   # or
   brew install openjdk@21
   ```

3. **File → Project Structure → SDKs** → click `+` → select the JDK 21 installation.
4. **File → Project Structure → Project** → set **Project SDK** to JDK 21.
5. Open the **Run Configuration** dropdown (top bar) → Edit Configurations → select **Launcher** → set **JRE** to JDK 21.
6. Run `Launcher` from IntelliJ.

> ⚠ **JDK 21 is mandatory.** Running on a newer JDK (22/23/25/26) causes a
> SIGSEGV crash in the JavaFX WebView native bridge. The classpath contains
> JavaFX 21 native JARs; the JVM must match (21.x). If you see a crash log
> mentioning `get_method_id` or `libjvm.dylib`, your IntelliJ Run Configuration
> is using the wrong JDK — switch the JRE to 21.

### Dev JAR (host-OS only)

```bash
mvn -DskipTests package
java -jar target/conload-1.0.1.jar
```

### Per-OS distribution JARs (cross-platform)

Produce platform-specific fat JARs that include the correct JavaFX native libraries for the target OS. Each JAR runs
with `java -jar` on **any plain JDK 21+** — no JavaFX installation needed on the target machine.

```bash
mvn -DskipTests package -Pmac       # → target/conload-1.0.1-mac.jar
mvn -DskipTests package -Plinux     # → target/conload-1.0.1-linux.jar
mvn -DskipTests package -Pwindows   # → target/conload-1.0.1-windows.jar
```

| JAR                         | Target OS                                  | Run command                           |
|-----------------------------|--------------------------------------------|---------------------------------------|
| `conload-1.0.1-mac.jar`     | macOS (auto-detects Apple Silicon / Intel) | `java -jar conload-1.0.1-mac.jar`     |
| `conload-1.0.1-linux.jar`   | Linux (x86_64)                             | `java -jar conload-1.0.1-linux.jar`   |
| `conload-1.0.1-windows.jar` | Windows (x86_64)                           | `java -jar conload-1.0.1-windows.jar` |

All three can be built on any OS — the Maven classifier forces the correct native libraries. Vosk speech models are
bundled and auto-extracted to
`~/.conload/vosk-model/` on first run.

### Build native installers yourself (bundled JRE)

Cross-platform installers via `jpackage`. Requires a **JavaFX-bundled JDK** (Liberica Full or Azul Zulu FX 21+) and
must be run **on the target OS**:

| Platform | Command                       | Output                                            |
|----------|-------------------------------|---------------------------------------------------|
| macOS    | `./build-mac.sh`              | `target/installer/conload-1.0.1.dmg`               |
| Linux    | `./build-linux.sh [deb\|rpm]` | `target/installer/conload_1.0.1_amd64.<type>`     |
| Windows  | `build-windows.bat`           | `target/installer/conload-1.0.1.exe`               |

Optional: place icons at `package/mac/icon.icns`, `package/linux/icon.png`, or `package/windows/icon.ico`.

> The published installers are built by
> [`.github/workflows/release.yml`](../.github/workflows/release.yml) on every `v*` tag push, using Liberica `jdk+fx 21`
> runners on macOS, Windows, and Linux.

---

## Project Structure

```
src/main/java/com/conload/
├── App.java / Launcher.java        # JavaFX entry point
├── confluence/                     # Confluence REST client + URL parsing
├── jira/                           # Jira REST client + ADF → Markdown
├── github/                         # GitHub client (commits, PRs, Actions)
├── markdown/                       # XHTML/ADF → Markdown, diagram extraction
├── model/                          # Project, QuickAction, AppConfig, Worktree…
├── service/                        # Config, search, download, worktree, sessions
│   ├── search/                     # Multi-source search execution
│   └── download/                   # Context download execution
├── workflow/                       # Pluggable workflow engine
│   └── crosscontext/               # Recursive cross-source context builder
├── ui/                             # JavaFX UI (shell, terminal, projects, prompts)
│   ├── terminal/                   # xterm.js + Pty4J terminal + session browser
│   └── prompttemplate/             # Prompt library + quick actions
├── util/                           # JSON, file ops, process runner, background tasks
└── vosk/                           # Offline speech model management + recording
```

Runtime state files (under `~/.conload/`): `projects.json`, `quick-actions.json`, `open_tabs.json`,
`opencode-sessions.json`, `terminal_pids.json`, `config.txt`.

---

## Runtime State Files

The app writes machine-specific state and credentials to files under `~/.conload/` (resolved by `AppPaths`), so
persistence works identically when running from source or from an installed app. These live outside the repo and must
never be committed — they contain credentials and are regenerated by the app on each machine.

> ⚠️ **Do not commit these files.** They contain API tokens, personal credentials,
> and machine-specific paths. Committing them leaks secrets and breaks other
> developers' setups. The `.gitignore` already excludes the legacy `src/` locations — do not override it.

### Credential & config

| File             | Written by      | Contents                                                                                                                                                                                                                                   |
|------------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `~/.conload/config.txt` | `ConfigService` | Atlassian base URL, email, API token, GitHub token, GitHub API URL, default export folder, shell,<br/> Vosk speech language, and CLI agent definitions (label / detection string / list-resume-export commands). **Contains credentials.** |

### Project & session state

| File                         | Written by               | Contents                                                                                                                          |
|------------------------------|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `~/.conload/projects.json`          | `ProjectService`         | The list of projects — each with its name, identity color, context-folder paths, worktree paths, and per-project CLI session IDs. |
| `~/.conload/open_tabs.json`         | `OpenTabsService`        | Which terminal tabs are open per project, so the UI restores them on restart.                                                     |
| `~/.conload/terminal_pids.json`     | `PidRegistryService`     | PTY process IDs for every open terminal, so leftover shells can be killed on restart. Refreshed on every PTY spawn/close.         |
| `~/.conload/opencode-sessions.json` | `OpencodeSessionService` | Cached OpenCode session list (ID, title, message, timestamp) for the session browser. Auto-refreshes every 30 seconds.            |

### Prompts & quick actions

| File                     | Written by           | Contents                                                                                                                                                                                       |
|--------------------------|----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `~/.conload/quick-actions.json` | `QuickActionService` | Both quick actions (one-click parameterized commands with `${var}` placeholders) and prompt templates (larger, multi-line templates with live variable detection). Separated by a type marker. |

### Workflow prompt-template overrides

| Location                         | Written by              | Contents                                                                                                                                                                       |
|----------------------------------|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `~/.conload/workflow-templates/<id>.md` | `WorkflowTemplateStore` | Per-workflow prompt-template overrides (e.g. `prepare-to-refinement.md`, `code-review.md`). When absent, the bundled template is used. Produced by the in-app template editor. |

---

## Bundled Templates & Resources (read-only)

These ship inside the JAR / source tree and are **not** runtime state — they are version-controlled and safe to commit.

| Location                                | Description                                                                                                                                                                                                                                                    |
|-----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `src/main/resources/workflows/*.md.tpl` | Bundled workflow prompt templates: `prepare-to-refinement.md.tpl`, `code-review.md.tpl`, `confluence-reverse-engineering.md.tpl`. These are the source of truth for `${variable}` tokens. User overrides (above) customise the prose around the locked tokens. |
| `src/main/resources/vosk-model/en/`     | English Vosk speech model (~40 MB, offline).                                                                                                                                                                                                                   |
| `src/main/resources/vosk-model/de/`     | German Vosk speech model (~40 MB, offline).                                                                                                                                                                                                                    |
| `src/main/resources/styles/theme.css`   | Application stylesheet.                                                                                                                                                                                                                                        |
| `src/main/resources/terminal/`          | xterm.js terminal HTML, JS, and focus-mode assets.                                                                                                                                                                                                             |
| `src/main/resources/images/`            | SVG icons (robot indicators, workflow sparkle, app icon).                                                                                                                                                                                                      |
