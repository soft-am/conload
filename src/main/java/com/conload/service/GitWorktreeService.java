package com.conload.service;

import com.conload.model.Worktree;
import com.conload.util.ProcessRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Runs {@code git worktree} commands on a background thread to list, create,
 * and remove linked working trees of a git repository.
 *
 * <p>All operations shell out to the system {@code git} executable via
 * {@link ProcessRunner}. Every method blocks until git exits, so callers
 * <b>must</b> invoke them off the JavaFX thread.
 *
 * <h2>Commands used</h2>
 * <ul>
 *   <li>{@code git rev-parse --is-inside-work-tree} — repo detection.</li>
 *   <li>{@code git fetch --all --prune} — refresh remote branch refs.</li>
 *   <li>{@code git branch -r --format=%(refname:short)} — list remote branches.</li>
 *   <li>{@code git worktree list --porcelain} — enumerate worktrees.</li>
 *   <li>{@code git worktree add <path> <branch>} — create a linked worktree.</li>
 *   <li>{@code git worktree remove [--force] <path>} — remove a worktree.</li>
 * </ul>
 */
public class GitWorktreeService {

    private static final Logger log = Logger.getLogger(GitWorktreeService.class.getName());

    /** Single-flight guard so concurrent refresh/create ops on the same repo
     *  don't race (git serializes anyway, but this avoids duplicate UI churn). */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    // ── Repo detection ──────────────────────────────────────────────────────

    /** Returns true if {@code dir} is inside a git working tree. */
    public boolean isGitRepo(Path dir) {
        if (dir == null) return false;
        try {
            String out = run(List.of("git", "-C", dir.toString(),
                    "rev-parse", "--is-inside-work-tree"));
            return out.trim().equalsIgnoreCase("true");
        } catch (Exception e) {
            return false;
        }
    }

    // ── Fetch ───────────────────────────────────────────────────────────────

    /** Fetches all remotes and prunes deleted branches. Blocks. */
    public void fetchAll(Path repoDir) throws IOException, InterruptedException {
        run(List.of("git", "-C", repoDir.toString(), "fetch", "--all", "--prune"));
    }

    // ── Branch listing ──────────────────────────────────────────────────────

    /** Lists remote-tracking branch refs (e.g. {@code origin/main},
     *  {@code origin/feature/pgvk}). The full ref is returned (not stripped)
     *  because {@code git worktree add <path> <ref>} needs a valid commit-ish;
     *  a bare {@code main} would fail when no local {@code main} branch exists
     *  yet. The dialog renders shorter display names. Call
     *  {@link #fetchAll(Path)} first so freshly-created remote branches appear. */
    public List<String> listRemoteBranches(Path repoDir) throws IOException, InterruptedException {
        String out = run(List.of("git", "-C", repoDir.toString(),
                "branch", "-r", "--format=%(refname:short)"));
        List<String> branches = new ArrayList<>();
        for (String line : out.split("\n")) {
            String b = line.trim();
            if (b.isEmpty()) continue;
            if (b.contains("HEAD ->")) continue;        // "origin/HEAD -> origin/main"
            if (b.endsWith("/HEAD")) continue;          // "origin/HEAD"
            if (!b.contains("/")) continue;             // bare remote name, e.g. "origin"
            if (b.indexOf('/') == b.length() - 1) continue; // "origin/" (empty branch)
            branches.add(b);
        }
        return branches;
    }

    // ── Worktree listing ───────────────────────────────────────────────────

    /** Parses {@code git worktree list --porcelain} into {@link Worktree}s.
     *  The first entry is always the primary (main) checkout. */
    public List<Worktree> listWorktrees(Path repoDir) throws IOException, InterruptedException {
        String out = run(List.of("git", "-C", repoDir.toString(),
                "worktree", "list", "--porcelain"));
        return parsePorcelain(out);
    }

    /** Parses the {@code --porcelain} output of {@code git worktree list}. */
    static List<Worktree> parsePorcelain(String output) {
        List<Worktree> result = new ArrayList<>();
        if (output == null || output.isBlank()) return result;
        Worktree cur = null;
        boolean first = true;
        for (String raw : output.split("\n", -1)) {
            String line = raw.trim();
            if (line.startsWith("worktree ")) {
                if (cur != null) result.add(cur);
                cur = new Worktree(line.substring("worktree ".length()).trim());
                cur.setPrimary(first);
                first = false;
            } else if (cur == null) {
                continue; // skip stray blank/leading lines
            } else if (line.startsWith("HEAD ")) {
                cur.setHead(line.substring("HEAD ".length()).trim());
            } else if (line.startsWith("branch ")) {
                String ref = line.substring("branch ".length()).trim();
                cur.setBranch(shortBranch(ref));
            } else if (line.equals("detached")) {
                cur.setDetached(true);
            } else if (line.equals("locked") || line.startsWith("locked ")) {
                cur.setLocked(true);
            } else if (line.equals("prunable")) {
                cur.setPrunable(true);
            } else if (line.equals("bare")) {
                cur.setBare(true);
            } else if (line.isEmpty()) {
                // blank line separates blocks; flush on next "worktree"
            }
        }
        if (cur != null) result.add(cur);
        return result;
    }

    /** {@code refs/heads/feature/foo} → {@code feature/foo}. */
    private static String shortBranch(String ref) {
        if (ref == null) return "";
        String s = ref.trim();
        String pfx = "refs/heads/";
        if (s.startsWith(pfx)) return s.substring(pfx.length());
        return s;
    }

    // ── Create / remove ────────────────────────────────────────────────────

    /** Creates a linked worktree at {@code target} checking out {@code branchRef}
     *  (a full remote-tracking ref like {@code origin/feature/foo}). A new
     *  <em>local</em> branch named after the branch (with the remote prefix
     *  stripped) is created tracking the remote — this avoids landing in
     *  detached-HEAD mode (which a bare {@code origin/...} commit-ish would
     *  cause). If that local branch <em>already exists</em> (e.g. the user
     *  re-picks {@code origin/master} after a local {@code master} was created
     *  earlier), falls back to checking out the existing local branch instead
     *  (git still refuses if that branch is checked out in another worktree).
     *  Git's own error is surfaced on other failures. */
    public void addWorktree(Path repoDir, Path target, String branchRef)
            throws IOException, InterruptedException {
        String localName = stripRemotePrefix(branchRef);
        try {
            run(List.of("git", "-C", repoDir.toString(),
                    "worktree", "add", target.toString(), "-b", localName, branchRef));
        } catch (IOException e) {
            // "a branch named 'X' already exists" → the local branch exists;
            // just check it out into the new worktree instead of creating it.
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                run(List.of("git", "-C", repoDir.toString(),
                        "worktree", "add", target.toString(), localName));
            } else {
                throw e;
            }
        }
    }

    /** {@code "origin/feature/foo"} → {@code "feature/foo"} (strips the first
     *  path segment, i.e. the remote name). Returns the input unchanged when
     *  it has no {@code /}. */
    private static String stripRemotePrefix(String ref) {
        if (ref == null) return "";
        int i = ref.indexOf('/');
        return (i >= 0 && i < ref.length() - 1) ? ref.substring(i + 1) : ref;
    }

    /** Removes a linked worktree. Pass {@code force=true} to override the
     *  safety check when the worktree is dirty or has a running process. */
    public void removeWorktree(Path repoDir, Path target, boolean force)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of("git", "-C", repoDir.toString(),
                "worktree", "remove"));
        if (force) cmd.add("--force");
        cmd.add(target.toString());
        run(cmd);
    }

    // ── Single-flight ───────────────────────────────────────────────────────

    /** Tries to acquire the single-flight lock. Returns true if acquired. */
    public boolean tryAcquire() { return busy.compareAndSet(false, true); }

    /** Releases the single-flight lock. */
    public void release() { busy.set(false); }

    // ── Command runner ──────────────────────────────────────────────────────

    /** Runs a command, waits for completion, returns stdout. Throws
     *  {@link IOException} (with stderr) on a non-zero exit. */
    private String run(List<String> cmd) throws IOException, InterruptedException {
        ProcessRunner.Result result = ProcessRunner.run(cmd, null, false);
        if (result.exitCode() != 0) {
            String msg = result.stderr().isBlank() ? result.stdout().strip() : result.stderr().strip();
            throw new IOException("git exited " + result.exitCode() + ": " + msg
                    + "  [cmd: " + String.join(" ", cmd) + "]");
        }
        return result.stdout();
    }
}
