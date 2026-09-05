# conload — Agent Notes

## JDK requirement

**JDK 21 is mandatory.** Running on a newer JDK (e.g. 22/23/26) causes a
SIGSEGV crash in the JavaFX WebView native bridge. Install JDK 21:

```bash
brew install openjdk@21          # macOS (then configure IntelliJ Project SDK)
```

Or use Liberica Full JDK 21 (bundles JavaFX, recommended for local builds):
```bash
brew install --cask liberica-jdk21-full
```

In IntelliJ: File → Project Structure → SDKs → add JDK 21 → set as Project SDK.
Run Configuration JRE must also be 21.

## Build / verification

No test suite. The build is the typecheck + verification step:

```bash
mvn -DskipTests package        # ~12s; produces target/conload-1.0.1.jar (host-OS natives)
mvn javafx:run                  # run the app interactively
```

Per-OS distribution JARs (cross-platform, include correct JavaFX natives):
```bash
mvn -DskipTests package -Pmac       # → target/conload-1.0.1-mac.jar
mvn -DskipTests package -Plinux      # → target/conload-1.0.1-linux.jar
mvn -DskipTests package -Pwindows    # → target/conload-1.0.1-windows.jar
```

Native installers (.dmg / .exe / .deb / .rpm) are built by
`.github/workflows/release.yml` on every `v*` tag push. It runs on 3 OS runners
(macOS/Ubuntu/Windows) using Liberica `jdk+fx 21` (bundles JavaFX so `jpackage`
`--add-modules` works). The `-Pnative` Maven profile only preps `target/libs/`;
`jpackage` itself is invoked by the build scripts (`build-mac.sh`,
`build-linux.sh`, `build-windows.bat`) which CI calls. To publish a release:
`git tag v1.0.1 && git push origin v1.0.1`. Assets are renamed to stable
version-less names (`conload.dmg` etc.) so README download links survive
version bumps. Release assets are hosted on the public `soft-am/conload`
GitHub repo and attached to each tag's Release by the CI workflow.

A separate workflow, `.github/workflows/update-formula.yml`, pushes the
regenerated Homebrew formula to the public `soft-am/homebrew-tap` repo (single
source of truth) — generated from `Formula/conload.rb.template` in this repo,
triggered automatically when a Release is published or manually from the
Actions tab, using the `HOMEBREW_TAP_TOKEN` repo secret (fine-grained PAT,
Contents:write on the tap repo only). Users install with:
`brew install soft-am/tap/conload` (macOS + Linuxbrew, pulls in `openjdk@21`).
The tap repo must exist and contain the secret before the formula sync can
succeed ("Update Homebrew tap formula" workflow run).

Run `mvn -DskipTests package` after every change. BUILD SUCCESS = green.

## Layout

- Java sources under `src/main/java/com/conload/` (single Maven module).
- Stylesheet: `src/main/resources/styles/theme.css`.
- Runtime state files (written/read by the app, outside the repo under
  `~/.conload/` via `com.conload.util.AppPaths`): `projects.json`,
  `quick-actions.json`, `open_tabs.json`, `opencode-sessions.json`,
  `terminal_pids.json`, `workflow-settings.json`, `config.txt`, plus the
  `workflow-templates/` override dir. `AppPaths.bootstrap()` (called from
  `App.main`) creates `~/.conload/` and one-time-migrates any legacy
  `src/<file>` state into it. The legacy `src/*.json` paths must NOT be used in
  new code — always go through `AppPaths`.

## Conventions

- No tests; manual smoke-test via `mvn javafx:run` for UI-touching changes.
- `dependency-reduced-pom.xml` is a generated shade-plugin byproduct — git-ignored; do not edit or commit.
- Root Python scripts (`check_fonts.py`, `find_lines.py`, `fix_fonts.py`, `fix_sizes.py`, `write_files.py`)
  are one-off maintenance utilities, not part of the build.
- Build is Java 21 + JavaFX 21.0.2 + Jackson 2.17 + pty4j 0.12.5 + JNA 5.9 + vosk 0.3.45.
  Prefer idiomatic Java 21 (records, switch expressions, `Map.of`, `@FunctionalInterface`, virtual threads,
  text blocks). Do NOT introduce new frameworks (no Spring, no Guice, no FXML, no DI containers).

## Current Architecture

- Read `REFACTORING_IMPLEMENTATION_PLAN.md` before changing architecture.
- No production class may exceed 800 physical lines.
- No production method, constructor, anonymous method, or inner-class method may exceed 100 physical lines.
- Keep controllers as composition roots and facades. Move cohesive behavior into package-level collaborators.
- Pass narrow constructor dependencies, records, suppliers, and callback interfaces. Do not pass whole controllers or extend `MainControllerSupport` for new code.

### Canonical Reusable Components

- `ui.components.LocalContextFolderDialog` — shared local context-folder picker.
- `ui.prompttemplate.dialog.PromptTemplateEditorDialog` — shared prompt-template editor.
- `ui.createcontext.presenter.SearchResultsPresenter` — search result presentation and selection controls.
- `ui.projects.SessionFolderFiles` — session transcript and metadata lookup.
- `util.FileTreeOps` — recursive filesystem copy operations.
- `util.ProcessRunner` — external process execution and output capture.
- `util.Json.MAPPER` — the only shared Jackson mapper.
- `model.QuickAction.extractParameters` — `${variable}` parsing.

### Major Package Boundaries

- `service.search` — multi-source search execution.
- `service.download` — context download execution.
- `github.actions` — GitHub Actions API workflows.
- `vosk` — model management and recording lifecycle.
- `ui.createcontext.search` / `ui.createcontext.download` — search and download UI workflows.
- `ui.terminal.session` — terminal session browser, CLI session state, and export.
- `ui.terminal` — PTY and WebView runtime.
- `ui.projects.files` / `ui.projects.sidebar` / `ui.projects.workspace` — file tree, worktree sidebar, and terminal construction.
- `ui.management` / `ui.shell` — management screen and focus-mode collaborators.

### Safe Refactoring Workflow

1. Search definitions and callers with the codebase-memory graph before editing.
2. Search canonical reusable components before adding a helper.
3. Extract one cohesive responsibility into the package that owns it.
4. Preserve public APIs and UI behavior unless the task explicitly changes them.
5. Run `mvn -DskipTests clean package` and `git diff --check` after each batch.
6. Never change `TerminalHtmlBuilder` custom CSS/theme or terminal scroll behavior.
7. Do not commit unless explicitly requested.
