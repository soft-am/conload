package com.conload.ui.components;

import com.conload.ui.Icons;
import com.conload.ui.Theme;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Compact developer user guide shown via the top-bar "Help" button (full-window
 * view). Mirrors the {@link AboutPanel} styling (section headers + bullet cards,
 * scrollable, themed) so it sits naturally next to the existing panels.
 *
 * <p>Sections are intentionally short — one or two bullets each, ordered from
 * first run (setup) → projects → terminal → worktrees → context loading →
 * multi-source search → cross-context engine → workflows → prompts & quick
 * actions → speech & focus mode.
 */
public final class HelpView {

    private HelpView() {
    }

    public static ScrollPane build() {
        VBox root = new VBox(0);
        Theme.classes(root, Theme.CL_BG_APP);
        root.getChildren().addAll(buildHero(), buildContent());
        return UiFactory.scrollable(root);
    }

    private static VBox buildHero() {
        VBox hero = new VBox(4);
        hero.setPadding(new Insets(16, 40, 12, 40));
        hero.getStyleClass().add("panel-border-bottom");

        Label heroIcon = new Label(Icons.INFO);
        heroIcon.getStyleClass().addAll("title", "icon");
        Label heroTitle = new Label("conload — Developer Guide");
        heroTitle.getStyleClass().addAll("title");
        Label summary = new Label(
                "A local context workspace for any CLI-based AI agent. Download Confluence, Jira, and GitHub "
                + "content as Markdown, run any CLI agent in a real terminal, and gather cross-source context "
                + "for a single Jira key or PR URL.");
        summary.setWrapText(true);
        summary.getStyleClass().addAll("secondary", "small");

        hero.getChildren().addAll(heroIcon, heroTitle, new Separator(), summary);
        return hero;
    }

    private static VBox buildContent() {
        VBox content = new VBox(0);
        content.setPadding(new Insets(16, 40, 20, 40));
        Theme.classes(content, Theme.CL_BG_APP);

        content.getChildren().add(sectionHeader("1. SETUP (ONCE)"));
        content.getChildren().add(bulletCard(new String[]{
                "Click ⚙ Settings: enter Atlassian base URL, email, API token, and GitHub token. Save.",
                "Set your default export folder and shell. Default CLI types and prompt templates ship bundled — "
                + "no extra config needed.",
                "State lives in ~/.conload/ (config, projects, sessions) — outside the repo, never committed."
        }));

        content.getChildren().add(sectionHeader("2. PROJECTS & TABS"));
        content.getChildren().add(bulletCard(new String[]{
                "Create a project from an existing folder, or from external data (downloads into a new project).",
                "Each project = its own terminal, file tree, context folders, and identity color.",
                "Multi-tab ribbon like browser tabs — sessions restore on restart. Close a tab, come back "
                + "tomorrow, the session is still resumable."
        }));

        content.getChildren().add(sectionHeader("3. TERMINAL — ANY CLI AGENT"));
        content.getChildren().add(bulletCard(new String[]{
                "Real xterm.js + Pty4J terminal (same engine VS Code uses), one per project.",
                "Run opencode, GitHub Copilot, aider, claude-code, gemini-cli, ollama — or any future CLI.",
                "Session browser: list / resume / switch agent sessions. Export the full transcript to Markdown."
        }));

        content.getChildren().add(sectionHeader("4. GIT WORKTREES"));
        content.getChildren().add(bulletCard(new String[]{
                "For any git repo, a sidebar lists primary + linked worktrees.",
                "Create from a remote branch, switch, remove — each worktree gets its own terminal, working "
                + "directory, and remembered CLI session.",
                "Work on three features in parallel without git stash chaos."
        }));

        content.getChildren().add(sectionHeader("5. CONTEXT LOADING"));
        content.getChildren().add(bulletCard(new String[]{
                "Download Confluence pages, Jira issues, and GitHub PRs/commits as clean Markdown into structured "
                + "context_<name>/ folders your agent can read.",
                "Confluence: recursive page-tree, XHTML→MD, diagram extraction (Mermaid, PlantUML, draw.io).",
                "Jira: JQL + batch fetch, ADF→MD, linked issues + attachments. GitHub: commit search + PR diffs."
        }));

        content.getChildren().add(sectionHeader("6. MULTI-SOURCE SEARCH & DOWNLOAD"));
        content.getChildren().add(bulletCard(new String[]{
                "One dialog, six criterion types across three platforms — search runs in parallel with per-criterion "
                + "spinners and result-count badges.",
                "One click downloads everything into confluence/ + jira/ + github/ subfolders with progress logs.",
                "\"I need the spec, the ticket, and the PR for this feature\" — one search, one download, one folder."
        }));

        content.getChildren().add(sectionHeader("7. CROSS-CONTEXT ENGINE"));
        content.getChildren().add(bulletCard(new String[]{
                "Give one starting point — a Jira key, a PR URL, or a Confluence page — and the engine walks the "
                + "graph across all sources with global deduplication (no page, issue, or commit fetched twice).",
                "Six phases: normalize source → recursive Jira expansion → extra Confluence trees → epic expansion "
                + "→ top-word discovery (full mode) → hierarchy map.",
                "Output: one folder + cross_context_hierarchy.md (read this first) listing every file tagged by source."
        }));

        content.getChildren().add(sectionHeader("8. WORKFLOWS"));
        content.getChildren().add(bulletCard(new String[]{
                "Pick a workflow from the dropdown in the prompt header, fill the dynamic form, click Gather.",
                "Built-in: Prepare-to-Refinement (Jira key), Code Review (PR URL), Confluence Reverse Engineering "
                + "(page URL / keyword).",
                "Full Mode = unlimited recursion + top-word discovery; Fast Mode = depth 1, summary-only epics.",
                "Right-click a context folder → Run Workflow regenerates the prompt from existing files (no API calls)."
        }));

        content.getChildren().add(sectionHeader("9. PROMPT LIBRARY & QUICK ACTIONS"));
        content.getChildren().add(bulletCard(new String[]{
                "Prompt templates with ${variable} placeholders auto-render input fields — write once, fill the blanks.",
                "Context-picker mode: click files in the tree to insert their paths into ${var} placeholders.",
                "Quick actions bar: one-click parameterized commands (git checkout ${branch}, opencode --session ${id}).",
                "Full CRUD for both — add, edit, delete, reorder."
        }));

        content.getChildren().add(sectionHeader("10. SPEECH & FOCUS MODE"));
        content.getChildren().add(bulletCard(new String[]{
                "Vosk offline speech-to-text — dictation goes straight to the terminal PTY. Model is bundled, "
                + "fully offline.",
                "Focus mode: native full-screen black overlay with an animated agent indicator (robot, alien, cat, "
                + "or Yoda) — let the agent work undisturbed."
        }));

        return content;
    }

    private static Label sectionHeader(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-header");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    /** A card with bullet rows, matching the AboutPanel use-case cards. */
    private static VBox bulletCard(String[] bullets) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(14, 20, 14, 20));
        card.getStyleClass().addAll("card");
        VBox.setMargin(card, new Insets(8, 0, 4, 0));

        for (String bullet : bullets) {
            HBox row = new HBox(8);
            Label dot = new Label(Icons.BULLET);
            dot.getStyleClass().addAll("small", "icon");
            Label text = new Label(bullet);
            text.setWrapText(true);
            text.getStyleClass().addAll("secondary", "small");
            HBox.setHgrow(text, Priority.ALWAYS);
            row.getChildren().addAll(dot, text);
            card.getChildren().add(row);
        }
        return card;
    }
}
