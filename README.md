# Realmforge

An RPG mod and modpack for Minecraft **26.1.2** (NeoForge), inspired by World of Warcraft (Classic, The Burning
Crusade and Wrath of the Lich King): races and classes, spells and talents, leveling with story chapters,
procedurally generated dungeons and dimensions, bosses with phases, professions, and a quest campaign.
Set in the world of Realmforge.

**Status:** early development. Milestone M0 (bootstrap) provides the build, CI, the content pipeline and a single
test item; there is no gameplay yet. Next is M1, a small playable vertical slice. Goals:
[docs/vision.md](docs/vision.md); plan: [docs/roadmap.md](docs/roadmap.md).

## How it is built

- **Core mod** (`mod/`, mod ID `realmforge`): all RPG systems, written by us.
- **Modpack** (`pack/`, [packwiz](https://packwiz.infra.link/)): the core mod plus third-party QoL, map, storage
  and performance mods (JEI, Jade, Xaero's maps, Sophisticated Storage, Sodium, Iris, ...).
- **Content pipeline** (`content/` + `tools/`): all game content is YAML, validated and turned into Java
  registrations, assets and data by Python. Generated files are committed and never edited by hand.
- **QA mod** (`qa-mod/`, `realmforge_qa`): development-only GameTests and debug tools; never released.

| Path | Contents |
| --- | --- |
| `content/` | YAML game content (single source of truth) |
| `tools/` | Python pipeline: validation, generators, tests |
| `mod/`, `qa-mod/` | NeoForge mods (Gradle, ModDevGradle) |
| `pack/` | packwiz metadata and config overrides (`config/`), no jars |
| `art/` | asset sources |
| `docs/` | vision, [architecture](docs/architecture.md), [roadmap](docs/roadmap.md), [design documents](docs/design/README.md), [lore](docs/lore/README.md), [decisions (ADRs)](docs/decisions/README.md), [research notes](docs/research-notes.md), [dev setup](docs/dev-setup.md) |

## Quick start

Requirements: Java 25, [uv](https://docs.astral.sh/uv/), and [packwiz](https://github.com/packwiz/packwiz) for pack
work. Full setup, including playing the pack with Prism Launcher on Windows: [docs/dev-setup.md](docs/dev-setup.md).

```bash
uv sync && uv run rf gen && uv run pytest   # validate content, regenerate, test
./gradlew build                             # build both mods + JUnit
./gradlew runGameTestServer                 # headless GameTests
./gradlew runClient                         # dev client
```

This project is developed mostly by AI agents; [CLAUDE.md](CLAUDE.md) is their guide and the best technical
overview of the repository.

## Git worktree cleanup

`./git_clean_worktrees.sh` removes local branches already merged into `origin/master`, with their attached
worktrees, and orphaned worktree folders in the two worktree homes: Claude Code's session worktrees in
`.claude/worktrees/` (gitignored) and the flow worktrees in `../realmforge-worktrees/`. It fails closed: anything
that may hold unmerged work, or that it cannot inspect, is kept and reported; keeping a checkout with real work is
an ordinary keep, and `ERROR` means a worktree could not be inspected. Run it from the primary checkout yourself;
agents never run it on a real checkout.

```bash
./git_clean_worktrees.sh
./git_clean_worktrees.sh --closed-prs        # also closed, unmerged PRs; type DELETE to confirm
./git_clean_worktrees.sh --closed-prs --yes  # the same, confirmed non-interactively
./git_clean_worktrees.sh --rewritten-prs     # also partly covered rewritten branches; type DELETE to confirm
bash tests/test_git_clean_worktrees.sh       # its safety regression suite (CI runs it in the content job)
```

Requirements: Git 2.40 or newer, and `gh` authenticated for `matejkadlec/realmforge`. Every rule that needs a pull
request fails closed without `gh`: the branch or folder is kept.

**On Windows** (the playtest clone), `git_clean_worktrees.ps1` is the same cleanup in PowerShell: the same rules,
options and messages, in Windows PowerShell 5.1 or PowerShell 7. Windows runs no unsigned script by default, so
start it through `powershell -ExecutionPolicy Bypass -File` (or allow local scripts once with
`Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`, after which `.\git_clean_worktrees.ps1` works). It never
follows a junction or symlink, and compares paths without regard to case, as Windows does. Without `gh`
(`winget install --id GitHub.cli`, then `gh auth login`) it still removes merged branches and branches whose
commits are provably in `origin/master`, and keeps the ones only a merged pull request can vouch for.

```powershell
git switch master    # the current branch is always kept
powershell -ExecutionPolicy Bypass -File .\git_clean_worktrees.ps1
powershell -ExecutionPolicy Bypass -File .\git_clean_worktrees.ps1 --closed-prs
powershell -ExecutionPolicy Bypass -File .\git_clean_worktrees.ps1 --rewritten-prs
```

The two scripts are kept in step: a change to one goes into the other, and the safety regression suite runs
against both (`bash tests/test_git_clean_worktrees.sh git_clean_worktrees.ps1` tests the port and needs `pwsh`,
PowerShell 7, which CI's runner has).

- A branch whose commits were rewritten before they merged (a playtest branch that `qa1` rebased, a commit a
  later review fix in the same pull request changed) is no ancestor of `origin/master` although its work is
  there. It is removed like a merged branch when its remote branch is gone or already in `origin/master`, and
  every commit it holds outside `origin/master` is already delivered: merging the whole branch changes nothing (as
  after a squash), or, commit by commit, `origin/master` gained the same patch, whitespace included
  (`git patch-id --verbatim`; `git cherry` ignores whitespace), or applying the commit alone changes nothing (a
  merge commit against its first parent), or (for a commit a rebase with conflicts or a later review fix rewrote,
  and only once GitHub's newest pull request for the branch is merged into `master` as a merge commit with none
  open) GitHub holds the exact commit, a commit of that pull request has its subject (conventional-commit type
  and scope aside), and every line the commit adds or removes is covered, file by file: the pull request's final
  change (its merge commit against the first parent, never its intermediate commits) made it in that file, or
  `origin/master`'s version of the file holds the added line or no longer holds the removed one. The merges need
  `git merge-tree --merge-base` (Git 2.40; older Git relies on the other checks and says so). A commit made only
  in this checkout, or a change the merge left out, passes none of them, and a branch whose remote still holds
  unmerged commits may be in a playtest or review, so both are kept.
- A rewritten branch its merged pull request covers only in part (review rewrote some of its lines, so those
  lines of the local copy are not in `origin/master`) is kept and reported with its uncovered line count.
  `--rewritten-prs` deletes it, with its worktree, once `DELETE` or `--yes` confirms (asked separately from
  `--closed-prs`), when every other condition above holds and the pull request's own commits made at least 80 % of
  each commit's changed lines, so a subject never decides alone.
- Known limitation: the coverage check reads text lines, so binary, empty-file and mode-only changes are not
  measured. A commit made only of them is kept; one that mixes them with text is judged by its text lines alone.
- `claude/*` session branches follow the same rules; the prefix decides nothing. A session that never committed
  is an ancestor of `origin/master` and goes with its clean worktree; one whose pull request merged after a rebase
  or a revision goes under the rewritten-branch rules or is offered by `--rewritten-prs`; one with uncommitted
  changes or commits that exist nowhere else is kept and reported.
- A worktree is removed only when it is pristine or every change Git reports is already byte-identical to
  `origin/master`, and it holds no file under `run/` (dev client and server worlds, which may hold rooms built by
  hand) or `.local/` (private inputs). Both are gitignored, so Git calls such a worktree clean: the cleanup checks
  them itself, on every removal path (merged, rewritten, `--rewritten-prs`, `--closed-prs`) and again right before
  the removal, keeps the worktree and its branch, and reports `Keeping run/ or .local/ files in the worktree of:`;
  move or delete those files, then run it again. `run/gametest/`, which every GameTest run recreates, keeps
  nothing. The other ignored files (build output, caches, `.venv/`) are discarded with the worktree. Renames,
  copies, unmerged records, permission-bit differences, a staged change the working tree then modified
  or deleted, and any content not already in `origin/master` keep the checkout.
- An orphaned folder directly inside a worktree home is removed only when it is recognized generated residue
  (only `.gradle/`, `build/`, `mod/build/`, `qa-mod/build/`, `.venv/`, `.pytest_cache/`, `.ruff_cache/`,
  `.cache/` and `__pycache__/` content; never `run/` or `.local/`), or when its stale `.git` pointer belongs to
  this repository, GitHub identifies one merged branch head for it, and every remaining tracked file still matches
  that head. A folder matches a branch by name: `realmforge-worktrees/feat-rlm-17-x` matches `feat/rlm-17-x`
  (every `/` as `-`), `.claude/worktrees/bold-hawking` matches `claude/bold-hawking` (or `NAME-<slug>` under at
  most one prefix) and `worktree-bold-hawking` (`claude --worktree`), and every matching merged pull request and
  local branch must be the same merged commit.
- The current branch, `master`, `recovery/*` branches, worktrees with real uncommitted work, empty or unknown
  folders, nested registered worktrees, a symlinked home (or a symlinked `.claude/`), modified orphan files, and
  anything with ambiguous or partially unavailable metadata are kept. Only the two homes are scanned for orphans;
  registered worktrees elsewhere are handled through their branch. A worktree location changed in the Claude
  desktop app's settings is not scanned.
- `--closed-prs` deletes a branch only when its remote branch is gone, GitHub reports one closed unmerged pull
  request whose head exactly matches it, and any attached worktree is removable under the same rule.

## Distribution

Releases will be published on GitHub Releases, CurseForge later. Not on Modrinth.

## License

The repository contains two products with different licenses:

- **The Realmforge mod** (`mod/`, `qa-mod/`, `tools/` and everything shipped in the `realmforge` jar): source code
  under the [Mozilla Public License 2.0](LICENSE), assets (textures, models, sounds, texts) under
  [Creative Commons Attribution 4.0 International](LICENSE-ASSETS). Dungeon rooms imported from other projects
  keep their own licenses, each source in its own folder under `content/rooms/third_party/` with its license and
  notice, which the jar carries in `META-INF/third_party/<source>/`
  ([ADR 0028](docs/decisions/0028-dungeon-rooms-generated-or-imported.md)):
  - MIT: Stonevaults (TheGrimsey), DungeonZ (Globox_Z, Zura), Desert Dungeon (D1scoball, Globox_Z);
  - CC0 1.0: Dungeon Now Loading (Hexnowloading);
  - LGPL-3.0: Mo' Structures (Frqnny).
- **The Realmforge modpack** (`pack/`: pack definition, configuration, quests): **All Rights Reserved**, no
  redistribution. See [pack/LICENSE](pack/LICENSE).

Third-party mods are not part of this repository; the pack only references them, and each keeps its own license.
Details and reasoning: [ADR 0009](docs/decisions/0009-licensing.md).

Minecraft is a trademark of Mojang Studios. Realmforge is not affiliated with Mojang or Microsoft.
