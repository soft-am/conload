# conload

**The context layer for CLI coding agents.**

Give your coding agent the context your team already has — Jira issues, Confluence documentation, GitHub pull requests, commits, and local project files — without manually collecting and pasting everything into a prompt.

`conload` turns scattered engineering knowledge into structured, local, agent-ready context and gives you a workspace to use it with **Claude Code, Codex, OpenCode, GitHub Copilot CLI, Aider, Gemini CLI, or any other terminal-based agent**.

> **One starting point → full engineering context → your coding agent.**

---

## How it works

```text
Jira issue / Confluence page / GitHub PR
                    │
                    ▼
        Recursive Cross-Context Engine
                    │
        ┌───────────┼────────────┐
        ▼           ▼            ▼
      Jira      Confluence     GitHub
     issues        docs      PRs / commits
        └───────────┼────────────┘
                    ▼
          Structured local context
       cross_context_hierarchy.md
                    │
                    ▼
       Claude Code / Codex / OpenCode
       Copilot CLI / Aider / any CLI agent
```

Start with a single Jira key, Confluence URL, or GitHub PR.

`conload` recursively follows related engineering context — linked Jira issues, parent epics, Confluence pages, PRs, and commits — deduplicates everything, and writes the result into a structured local context folder your agent can read directly.

---

## Why conload

Coding agents are increasingly good at understanding your **codebase**.

But the reason the code exists often lives somewhere else:

* requirements in Jira
* architecture decisions in Confluence
* implementation history in commits
* discussion and decisions in pull requests
* supporting documents scattered across different systems

Without that context, an agent may understand *what the code does* while missing *why it does it*.

`conload` bridges that gap.

Instead of manually copying tickets, documentation, PR descriptions, and code history into prompts, give `conload` one starting point and let it build the surrounding context automatically.

---

## Core capabilities

### 🕸️ Recursive cross-context discovery

Start with a Jira issue, Confluence page, or GitHub PR and recursively discover related engineering knowledge.

`conload` follows relationships between sources and builds a deduplicated context tree containing Jira issues, parent epics, Confluence specifications, GitHub commits, PR data, attachments, and related references.

The generated `cross_context_hierarchy.md` gives both you and your agent a map of the collected context.

### 💻 Agent-independent terminal workspace

Run the coding agent you already use.

`conload` provides real embedded terminal sessions using **xterm.js + Pty4J**, with support for tools such as:

`claude-code` · `codex` · `opencode` · `copilot` · `aider` · `gemini-cli` · `ollama`

CLI definitions are configurable, so new terminal-based agents can be added without changing `conload`.

### 🌳 Git worktrees for parallel agent work

Create and manage Git worktrees directly from the workspace.

Each worktree gets its own:

* working directory
* terminal
* agent session
* branch context

This makes it practical to run several coding tasks or agents in parallel without repeatedly switching branches or using `git stash`.

### ⚡ Reusable workflows and prompts

Create parameterized workflows for repetitive engineering tasks such as:

* ticket refinement
* implementation planning
* code review against Jira/Confluence requirements
* reverse-engineering existing functionality
* architecture analysis
* documentation generation

Templates support `${variable}` placeholders and can reference context files directly.

### 🔒 Local-first context processing

Downloaded Jira, Confluence, and GitHub content is processed and stored locally.

**conload itself does not send your engineering context to an LLM API.**

The CLI agent you choose to run may communicate with its own provider according to that tool's configuration and privacy policy.

Offline speech-to-text is also available through Vosk.

---

## Screenshots

<p align="center">
  <img src="docs/images/screenshot-banner.png" alt="conload overview" width="100%" />
</p>

<p align="center">
  <img src="docs/images/screenshot-1.png" alt="conload screenshot" width="49%" />
  <img src="docs/images/screenshot-2.png" alt="conload screenshot" width="49%" />
</p>

---

## What makes conload different?

`conload` is **not another coding agent**.

It is a workspace and context layer around the agents you already use.

The goal is to solve a different problem:

> **Your coding agent has access to the repository.
> conload helps it understand the engineering context around the repository.**

That context can span Jira, Confluence, GitHub history, local documentation, and multiple related tickets — and `conload` turns it into something an agent can consume directly.

---

## Download & install

### Quick start — run from source

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

### Download a JAR (no build needed)

Pre-built distribution JARs are available on the [Releases](https://github.com/soft-am/conload/releases/latest) page.
Each JAR is self-contained (includes JavaFX natives for the target OS) and runs with `java -jar` — no Maven needed.

| Platform | Download | Run command |
|---|---|---|
| **macOS** (Apple Silicon / Intel) | [`conload-mac.jar`](https://github.com/soft-am/conload/releases/latest/download/conload-mac.jar) | `java -jar conload-mac.jar` |
| **Windows** (x86_64) | [`conload-windows.jar`](https://github.com/soft-am/conload/releases/latest/download/conload-windows.jar) | `java -jar conload-windows.jar` |
| **Linux** (x86_64) | [`conload-linux.jar`](https://github.com/soft-am/conload/releases/latest/download/conload-linux.jar) | `java -jar conload-linux.jar` |

Requires **JDK 21** installed on your machine (the JARs do not bundle a JRE). Do **not**
use JDK 22+ — the JavaFX WebView native bridge will crash with a segmentation fault.
Check your Java version with `java -version` before running.

### Homebrew (macOS & Linux)

Single-command install via our Homebrew tap. It pulls in `openjdk@21` as a dependency,
so the correct JDK is installed automatically — you never run on the crashing JDK 22+,
and no separate Java setup is needed.

```bash
brew install soft-am/tap/conload
```

Then run:

```bash
conload
```

Works on macOS (Apple Silicon and Intel) and Linux (x86_64) via
[Homebrew on Linux](https://docs.brew.sh/Homebrew-on-Linux). The tap downloads the
same pre-built platform JAR as above and wraps it in a `conload` launcher script pinned
to Homebrew's `openjdk@21`.

The tap's formula is regenerated by CI on every tagged release (new version + sha256
checksums), so upgrading to the latest release is a plain brew command:

```bash
brew upgrade soft-am/tap/conload
```

To uninstall:

```bash
brew uninstall soft-am/tap/conload
brew untap soft-am/tap
```

### Native installers — Coming Soon

Native installers (`.dmg` / `.exe` / `.deb` / `.rpm`), each bundling its own JRE so no
separate JDK install is needed, are pending code-signing certificates (Apple Developer ID,
Windows Authenticode) so they pass Gatekeeper / SmartScreen without warnings.

| Platform | Installer | Status |
|---|---|---|
| **macOS** (Apple Silicon / Intel) | `conload.dmg` | Coming Soon |
| **Windows** (x86_64) | `conload.exe` | Coming Soon |
| **Linux** (Debian/Ubuntu) | `conload.deb` | Coming Soon |
| **Linux** (Fedora/RHEL/SUSE) | `conload.rpm` | Coming Soon |

> See [Build from source](#run-from-source) for building JARs, native installers,
> and per-OS build details.

[All releases](https://github.com/soft-am/conload/releases) · [Latest release](https://github.com/soft-am/conload/releases/latest)

---

## Features

### Multi-terminal for any Agentic CLI

Every project gets its own embedded terminal — a real **xterm.js + Pty4J** terminal (the same engine VS Code uses), not
a process pipe. You can run *any* CLI-based agent in it:

- `opencode`, GitHub Copilot, `aider`, `claude-code`, `gemini-cli`, `ollama`, or any future tool.

CLI definitions are **user-configurable** in the Config tab. For each agent you can set a detection string, a
session-listing command, a resume command, and an export command — with `{id}` placeholders substituted at runtime. Out
of the box, OpenCode is fully wired (list / resume / export sessions); GitHub Copilot is detected and labelled. Adding a
new agent takes seconds and requires no code changes.

**Developer value:** Stop juggling terminal windows. Each project has its own terminal with its own working directory,
its own agent session, and its own identity color — so you always know which context you're in.

### Terminal tabs & session persistence

- **Per-project terminals** with tab persistence — sessions are restored on restart.
- **Session browser** — list, resume, and switch CLI agent sessions (for agents that expose a JSON session list like
  OpenCode).
- **Terminal buffer export** to Markdown — capture the full agent conversation for documentation or handoff.
- **Animated robot indicator** shows when the agent is busy, with per-project colors and selectable character (robot,
  alien, cat, Yoda).
- **Focus mode** — a native full-screen black overlay with an animated agent indicator, so you can let the agent work
  undisturbed.

**Developer value:** Close a tab, restart your machine, come back tomorrow — your agent sessions are still there,
resumable with one click. Export the transcript when you need to document what the agent did.

### Git worktrees integration

`conload` is a first-class **`git worktree`** client. For any project that's a git repo, a sidebar lists all worktrees
(primary + linked), and you can:

- **Create** a new linked worktree from any remote branch (`git fetch --all --prune` runs first).
- **Switch** between worktrees — each worktree gets its own terminal with its own working directory and its own
  remembered CLI session.
- **Remove** worktrees (with force option for dirty trees).

Worktree sessions are durable: closing a worktree's tab remembers its last CLI session so you can resume later.

**Developer value:** Work on three features in parallel without `git stash` chaos. Each worktree is a separate working
directory with its own agent — the terminal opens in the right path, the file tree shows the right branch, and the agent
only sees that branch's code.

### Context loading: Confluence, Jira, GitHub

Download enterprise knowledge as Markdown, organized into structured `context_<name>/` folders your agent can read.

**Confluence**

- REST API client (Basic Auth, paginated, retry with backoff).
- Recursive page-tree download with hierarchy-based file naming.
- Storage-format XHTML → Markdown (macros, tables, code blocks, task lists).
- Diagram extraction: Mermaid, PlantUML, draw.io, Gliffy, BPMN.
- CQL keyword search; attachment deduplication (PNG > JPG > SVG priority).

**Jira**

- REST API v3 (JQL search, batch fetch, attachments).
- Atlassian Document Format (ADF) JSON → Markdown conversion.
- Issue → Markdown with metadata tables, comments, linked issues, attachments.
- URL parsing (`/browse/PROJ-123`, `?selectedIssue=`, bare keys).

**GitHub**

- Commit search by keyword within `owner/repo`.
- PR fetch by URL + diff export as JSON.
- Bearer token auth — works with github.com and GitHub Enterprise.

**Developer value:** Your agent can't open a browser. But it *can* read `confluence_Authentication.md`,
`jiraSEC-1234.md`, and `commits_SEC-1234.json` from a local folder. One click and the full ticket, its spec, and the
code that implements it are on disk.

### Unified multi-source search & download

One dialog, six criterion types across three platforms. Search runs in **parallel** with per-criterion spinners and
result-count badges. Results are collapsible panels with checkboxes, select-all/none bars. One click downloads
everything into a structured folder with `confluence/`, `jira/`, and `github/` subdirectories. Progress logs, progress
bar, and success/warning/error status keep you informed.

**Developer value:** "I need the spec, the ticket, and the PR for this feature" — one search, one download, one folder.
Your agent reads it all.

### Prompt library

A full **prompt template CRUD** system built on the same engine as quick actions:

- **Template selector** dropdown with create / edit / delete.
- **`${variable}` substitution** — templates auto-detect placeholders and render input fields for them.
- **Context picker mode** — click files in the file tree to insert their paths into `${var}` placeholders in the prompt.
- **Live variable detection** — the editor highlights variables as you type.
- Templates are marked separately from quick actions (larger text, multi-line editor) and are persisted to
  `quick-actions.json`.

**Developer value:** Stop typing the same long prompt every time. Write it once with placeholders, then fill in the Jira
key / file paths / topic — and send it straight to the agent.

### Quick actions

A **quick actions bar** sits above the terminal with one-click parameterized commands:

- Commands may contain `${varName}` placeholders — clicking the action pops up an input dialog to fill them.
- "With-input" actions are grouped at the front, transparent-styled.
- Full CRUD — add, edit, delete, reorder.
- Persisted to `src/quick-actions.json`.

**Developer value:** `git checkout ${branch}`, `opencode --session ${id}`, `npm run test:${suite}` — one click, fill the
blank, done. Your most-used commands, always one tap away.

### Offline speech-to-text (Vosk)

- **Vosk-based voice dictation** — recognized text is sent directly to the terminal PTY.
- **Speech model is bundled** (~40 MB, English + German) — no download, no internet needed.
- Custom JNA binding works around a broken symbol in vosk 0.3.45.
- Non-blocking: all native loading runs on background threads; the UI stays responsive.

**Developer value:** Dictate your prompt or command instead of typing. Fully offline — safe for air-gapped and
restricted-network environments.

### Project workspaces

- **Projects** persisted to `projects.json` with identity colors and context-folder lists.
- Create from an existing folder, or from external data (downloads into a new project).
- **Multi-tab project ribbon** with close buttons and busy-state robot indicators.
- **Per-project file tree** (lazy-loading, accent-colored, mouse-wheel scroll).
- Context CRUD: add local import, download, remove, reorder.
- File tree operations: Open, "Open in…" (macOS app chooser), Copy Path, Put into Terminal.

**Developer value:** Like browser tabs for your agents. Each project is a separate workspace — its own terminal, file
tree, context folders, and color — so context never leaks between projects.

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

## Basic Workflow

Workflows are built-in recipes that use the Cross-Context Engine to gather context and generate a ready-to-send prompt.
Each workflow:

1. Takes a minimal input (a Jira key, a PR URL, a Confluence keyword).
2. Runs `CrossContextBuilder.createCrossContext(...)` which executes all six phases.
3. Substitutes the results into a bundled prompt template (`${contextRoot}`, `${outputDocPath}`, `${workspacePath}`,
   etc.).
4. Pushes the finished prompt into the prompt textarea — review it and click Send.

### Selecting and running a workflow

1. In the prompt header, pick a workflow from the **workflow dropdown**.
2. A dynamic form appears (each workflow defines its own fields — text inputs + the Full Mode checkbox).
3. Fill the fields and click **Gather Context** — the workflow runs on a background thread with live progress and log
   output.
4. When done, the prompt template is populated and pushed to the prompt textarea. The workflow section collapses.
5. Review the prompt and click **Send** to push it to the terminal.

### Full Mode vs. Fast Mode

Every workflow has a **Full Mode** toggle (checkbox, default on):

|                                         | Full Mode (default)                                       | Fast Mode                                                                              |
|-----------------------------------------|-----------------------------------------------------------|----------------------------------------------------------------------------------------|
| Recursion depth                         | Unlimited — follows all related issues to any depth       | Capped at depth 1 (direct related issues only, no grandchildren)                       |
| Epics                                   | Fully expanded — all child issues are recursively fetched | Summary only — the epic issue is fetched + exported, but its children are not expanded |
| Related keys per issue                  | Unlimited                                                 | Capped at 10 per issue                                                                 |
| Top-word Confluence discovery (Phase E) | Runs                                                      | Skipped entirely                                                                       |
| Global dedup                            | Yes (all three sets active)                               | Yes (all three sets active)                                                            |
| Use when                                | You want maximum context for a thorough analysis          | You need a quick first pass or a large epic with hundreds of children                  |

### Built-in workflows

#### Prepare to Refinement

**Input:** Jira issue keys, URLs, or keywords (comma/space separated). Plus optional GitHub `owner/repo` (auto-detected
from the workspace git remote) and an optional additional Confluence URL or keyword.

**What it gathers:** The seed Jira issue (s) + all related issues (recursive) + linked Confluence page trees (with
subpages and media) + GitHub commits mentioning each key (with diffs) + the parent epic (if any) expanded + top-word
Confluence discovery (full mode).

**Output prompt:** Instructs the agent to produce a refinement/implementation document with these sections:

1. Current Data Flow
2. Clarifications / Questions for Requirements Engineers / BAs
3. Previous Related Jira Stories (who implemented what and when, from commit data)
4. Related Confluence Documentation
5. Draft Estimate

**Developer value:** Before your refinement meeting, paste the Jira key, click Gather. The agent reads the ticket, all
linked specs, the commit history of related features, and produces a draft implementation document with data flow, open
questions, and a story-point estimate — grounded in actual code and docs, not guesses.

#### Code Review

**Input:** One or more GitHub PR URLs (space/comma/newline separated). Multiple PRs can be from different repos.

**What it gathers:** Each PR's metadata + diff + commit messages. Jira keys and Confluence URLs are discovered from the
PR title, body, and commit messages, then the recursive cross-context engine runs over all combined seeds.

**Output prompt:** Instructs the agent to perform a structured code review with these sections:

1. PR Summary
2. Related Jira & Confluence Context
3. Code Quality Assessment (correctness, design, naming, error handling)
4. Security & Performance
5. Test Coverage
6. Risks & Recommendations

**Developer value:** Paste a PR URL, click Gather. The agent gets the diff, the ticket it implements, the spec it
follows, and the commit history — then reviews the code against all of that context. It can spot "this PR doesn't match
what the Jira ticket actually asks for" or "this change contradicts the Confluence spec" — things a code review tool
that only reads the diff would miss.

#### Confluence Reverse Engineering

**Input:** A Confluence page URL or a search keyword.

**What it gathers:** If a URL — the page tree below that page is downloaded, then Jira keys are discovered from the page
content and the recursive engine takes over. If a keyword — a CQL search runs, the top 3 result pages serve as starting
points (each tree downloaded before Jira-key discovery).

**Output prompt:** Instructs the agent to reverse-engineer the documented architecture with these sections:

1. System Overview
2. Documented Architecture (components, data flow, integrations, storage — mapped to actual code)
3. Data Model
4. Business Rules & Workflows
5. Related Jira Issues & Implementation History
6. Documentation vs. Code Gaps
7. Recommendations

**Developer value:** New to a project with 200 Confluence pages and no idea how they map to code? Give the app a
keyword. It downloads the relevant docs, discovers the Jira history, finds the commits, and the agent cross-references
the documentation against your actual codebase — telling you where the docs are outdated, incomplete, or wrong.

### Re-running a workflow from the file tree

After a gather completes, the context folder contains `workflow-info.json` (workflowId, inputs, repo, output path,
timestamp). **Right-click** that folder in the file tree → **Run Workflow** → the prompt is regenerated from the
existing context files by substituting paths into the template — no API calls are made. This lets you refine the prompt
without re-downloading everything.

### Extensibility

The workflow system is designed so that **adding a new workflow requires only one new class implementing `Workflow` and
one new template file** — no UI changes needed. The `Workflow` sealed interface declares `id()`, `displayName()`,
`inputFields()`, `accumulate()`, and `templateResource()`. The `WorkflowFormBuilder` and `WorkflowInlineSection` handle
all rendering automatically from the field definitions.

---

## Requirements

To **run** `conload`, just download the installer for your OS from [Download & install](#download--install) above — it
bundles its own JRE, so no separate JDK install is needed.

To **build from source** or rebuild the installers yourself:

- **JDK 21+** (any distribution: OpenJDK, Oracle, Temurin, etc.)
- **Maven 3.8+**
- For native installers: a **JavaFX-bundled JDK** (Liberica Full or Azul Zulu FX 21+)

---

## Install & Run

The easiest way to get `conload` is the [pre-built installers](#download--install) above. The steps below are for running
from source or building the installers yourself.

### Run from source

Requires **JDK 21** (exactly — not 22+, see note below) and **Maven 3.8+**.

```bash
mvn javafx:run
```

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

> The published installers (top of this README) are built by
> [`.github/workflows/release.yml`](.github/workflows/release.yml) on every `v*` tag push, using Liberica `jdk+fx 21`
> runners on macOS, Windows, and Linux.

---

## Configuration

Open the in-app **Config** tab (gear icon) and set:

| Field                      | Description                                                                                                                             |
|----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| **Atlassian base URL**     | Your Confluence/Jira host (e.g. `https://yourcompany.atlassian.net`). Shared by both.                                                   |
| **Email**                  | Your Atlassian account email.                                                                                                           |
| **API token**              | Atlassian API token ([create one](https://support.atlassian.com/atlassian-account/docs/manage-api-tokens-for-your-atlassian-account/)). |
| **GitHub token**           | Personal access token with `repo` / `read:org` scope.                                                                                   |
| **GitHub API URL**         | `https://api.github.com` (default) or your GitHub Enterprise host.                                                                      |
| **Default export folder**  | Where context folders are downloaded by default.                                                                                        |
| **Shell**                  | Terminal shell (e.g. `/bin/zsh`, `/bin/bash`). Blank = system default.                                                                  |
| **CLI types**              | Editable list of CLI agent definitions — label, detection string, and list/resume/export commands with `{id}` placeholders.             |
| **Full Confluence folder** | Global setting: a persistent Confluence data folder that workflows reference as `${confluenceDataSource}`.                              |

Configuration is stored in `~/.conload/config.txt` — outside the repo, contains credentials.

---

## Privacy & Offline Operation

- **No external LLM API calls.** `conload` runs your CLI agent locally; it does not call any AI service itself.
- **No data leaves the network.** All downloads go to local folders within your corporate network.
- **Offline speech recognition.** The Vosk speech model is bundled in the app resources. Dictation works entirely
  offline — no internet connection required.

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

---

## License

`conload` is licensed under the **Apache License 2.0** — see [LICENSE](LICENSE). Attribution and third-party
notices are in [NOTICE](NOTICE).

### Bundled third-party components

The app bundles the following third-party libraries and assets. Each is compatible with the Apache 2.0
license; see [NOTICE](NOTICE) for full attribution and copyright holders.

| Component | Version | License | Source |
|---|---|---|---|
| Vosk speech-to-text API | 0.3.45 | Apache 2.0 | [alphacep/vosk-api](https://github.com/alphacep/vosk-api) |
| Vosk English model (en) | — | Apache 2.0 — © 2020 Alpha Cephei Inc | bundled in `src/main/resources/vosk-model/en/` |
| Vosk German model (de) | — | Apache 2.0 — © 2020 Alpha Cephei Inc | bundled in `src/main/resources/vosk-model/de/` |
| JavaFX (controls, fxml, web, graphics, base) | 21.0.2 | GPL-2.0 + Classpath Exception | [openjdk/jfx](https://github.com/openjdk/jfx) |
| pty4j (PTY terminal) | 0.12.5 | EPL-1.0 | [JetBrains/pty4j](https://github.com/JetBrains/pty4j) |
| purejavacomm | 0.0.11.1 | BSD-3-Clause | [nyholku/purejavacomm](https://github.com/nyholku/purejavacomm) |
| JNA / jna-platform | 5.14.0 / 5.9.0 | Apache 2.0 | [java-native-access/jna](https://github.com/java-native-access/jna) |
| Jackson (databind, annotations, core) | 2.17.0 | Apache 2.0 | [FasterXML/jackson](https://github.com/FasterXML/jackson) |
| jsoup | 1.17.2 | MIT | [jhy/jsoup](https://github.com/jhy/jsoup) |
| Guava (+ failureaccess) | 30.1.1-jre | Apache 2.0 | [google/guava](https://github.com/google/guava) |
| ByteBuddy | 1.14.9 | Apache 2.0 | [raphw/byte-buddy](https://github.com/raphw/byte-buddy) |
| jsr305 | 3.0.2 | BSD-3-Clause | [findbugs/jsr305](https://github.com/findbugs/jsr305) |
| checker-qual | 3.8.0 | MIT | [typetools/checker-framework](https://github.com/typetools/checker-framework) |
| error_prone_annotations | 2.5.1 | Apache 2.0 | [google/error-prone](https://github.com/google/error-prone) |
| j2objc-annotations | 1.3 | Apache 2.0 | [google/j2objc](https://github.com/google/j2objc) |
| JetBrains annotations | 20.1.0 | Apache 2.0 | [JetBrains/java-annotations](https://github.com/JetBrains/java-annotations) |
| log4j 1.x (transitive via pty4j) | 1.2.17 | Apache 2.0 | [logging.apache.org](https://logging.apache.org/log4j/1.2/) |

The **Classpath Exception** on JavaFX permits bundling and distribution with conload under Apache 2.0 terms;
the JavaFX source is used unmodified. **EPL-1.0** (pty4j) is weak copyleft — source remains available at
the linked repository. No component in the dependency tree is licensed under GPL (without the Classpath
Exception) or AGPL.
