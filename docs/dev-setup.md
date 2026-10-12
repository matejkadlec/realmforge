# Development setup

Development happens in **WSL (Ubuntu)**; playing and playtesting happen on **Windows**: the dev client with the QA
mod from a Windows clone ([section 3.2](#32-on-windows-playtests)), the full modpack in Prism Launcher
([section 4](#4-playing-the-pack-on-windows-prism-launcher--packwiz-installer)). Background:
[ADR 0011](decisions/0011-repository-layout-and-workflow.md).

## 1. Tools in WSL (no sudo needed)

Keep the repository on the Linux filesystem (e.g. `~/projects/realmforge`). Never build from `/mnt/c/...`:
the Windows-WSL file bridge makes Gradle many times slower.

| Tool | Install |
| --- | --- |
| Java 21 (Temurin) | [SDKMAN](https://sdkman.io): `curl -s "https://get.sdkman.io" \| bash`, then `sdk install java 21.0.12+1.1-tem` |
| Python 3.12+ and uv | [uv](https://docs.astral.sh/uv/): `curl -LsSf https://astral.sh/uv/install.sh \| sh`; uv downloads the pinned Python itself |
| git, GitHub CLI | usually preinstalled; `gh auth login` once |
| packwiz | needs Go once: unpack the [Go tarball](https://go.dev/dl/) into `~/.local/share/go`, then `GOBIN=~/.local/bin ~/.local/share/go/bin/go install github.com/packwiz/packwiz@ef87d964f8cbd52b3b13ea42453ef322290e2b9e` |

SDKMAN adds itself to `~/.bashrc`. Scripts and non-interactive shells need
`source ~/.sdkman/bin/sdkman-init.sh` first.

**Which Java runs Gradle.** `gradlew` starts on whatever Java `JAVA_HOME` or `PATH` gives it (17 or newer). The
Gradle daemon, which compiles the build scripts and runs the build, always runs on Java 21:
`gradle/gradle-daemon-jvm.properties` (written by `./gradlew updateDaemonJvm --jvm-version=21`) makes Gradle pick an
installed Java 21 or download Temurin 21 into `~/.gradle/jdks`. Without it, a newer default Java (26, say) fails
before configuration with `Unsupported class file major version 70`, because Gradle 9.2.1 runs only on Java 17 to
25. The mod's own toolchain (`java.toolchain` in `*/build.gradle`) is Java 21 as well.

## 2. First build

```bash
uv sync                      # Python environment
uv run rf gen --check        # content valid, generated files up to date
uv run pytest
./gradlew build              # first run downloads Minecraft + assets (~1 GB) and decompiles: 5-10 minutes
./gradlew runGameTestServer  # headless GameTests
uv run rf gametest-log       # no data loading errors in the GameTest log
```

Optional, for agents and for reading Minecraft code: `./gradlew :mod:extractMinecraftSources` unpacks the
decompiled Minecraft + NeoForge sources into `build/mc-sources/`.

## 3. The dev client (`runClient`)

`./gradlew runClient` starts a dev client with the core mod and the QA mod, so the `/qa` commands work. Besides
ours, only playerAnimator is loaded (a QA mod dependency). Its game directory is `run/client` in the checkout
(saves, options, logs). Playtests run it on Windows (3.2), where mouse look works; in WSL it is good only for
"does it load" checks (3.3).

### 3.1 What every dev client gets

- **Your own Minecraft account.** The client run has `devLogin = true` (`qa-mod/build.gradle`): ModDevGradle starts
  the game through covers1624's [DevLogin](https://github.com/covers1624/DevLogin), so you play as yourself, with
  your skin, instead of an offline player named `Dev`. The first launch on a machine prints a `[DevLogin]` line
  with a Microsoft link and a code in the console; open the link, enter the code and sign in with the Microsoft
  account that owns Minecraft, and the game starts. DevLogin keeps the tokens in `~/.devlogin/accounts.json`
  (`%USERPROFILE%\.devlogin\accounts.json` on Windows) and reuses them; when they expire, the console asks again.
  The file holds login tokens: never commit, share or paste it. Deleting it logs you out.
- **Default options.** Before the client starts, `seedDevOptions` copies `dev/options.txt` into
  `run/client/options.txt`, but only while that file does not exist, so what you change in game persists. The
  defaults: GUI scale 2, master volume off, VSync off, field of view 110, FOV effects off, no accessibility
  onboarding and no tutorial hints. Delete `run/client/options.txt` to get them back. To change them for every
  fresh client, edit `dev/options.txt` without comment lines (Minecraft logs those as bad options); option names
  and encodings are vanilla's (`fov:1.0` is 70 + 40 × 1.0).
- **Better Combat on demand.** `./gradlew runClient -PwithBetterCombat` adds Better Combat and Cloth Config, the
  same versions as the pack, to check our code with Better Combat installed. In game, `/qa cast 200` makes you cast
  for 10 seconds: a test animation plays and melee is blocked until it ends
  ([ADR 0020](decisions/0020-cast-animations-and-melee-while-casting.md)).
- **Logs.** Every launch writes `run/client/logs/latest.log` and `run/client/logs/debug.log` (the same plus debug
  lines); older launches are gzipped next to them (`2026-10-12-1.log.gz`, `debug-1.log.gz`), and a crash leaves a
  report in `run/client/crash-reports/`. To hand a problem to an agent, attach the file, or paste the output of
  `grep -nE "ERROR|Exception" run/client/logs/latest.log`.

### 3.2 On Windows (playtests)

The Windows client has native graphics and working mouse look, and unlike Prism (section 4) it loads the QA mod.
It needs its own clone on the Windows file system. Never build the WSL checkout from Windows (`\\wsl$\...`), and
never a Windows folder from WSL (`/mnt/c/...`).

Once, in PowerShell, with [Git for Windows](https://git-scm.com/download/win) and a JDK installed (Temurin 21 from
[adoptium.net](https://adoptium.net/); any Java 17 or newer starts Gradle, and the daemon finds or downloads Java 21
by itself, see [section 1](#1-tools-in-wsl-no-sudo-needed)):

```powershell
cd $HOME\projects
git clone https://github.com/matejkadlec/realmforge.git realmforge-win
```

Everything the build needs is in the repository: the generated files are committed (no Python or uv), and
`.gitattributes` keeps LF line endings (CRLF only for `.bat`), so nothing comes from WSL. The first `runClient`
downloads Minecraft and NeoForge and takes several minutes; then DevLogin asks for your login (3.1).

For each playtest, switch the clone to the branch under test and start the client:

```powershell
cd $HOME\projects\realmforge-win
git fetch origin
git switch <branch>
.\gradlew.bat runClient
```

For a later revision of the same branch, `git pull --ff-only` updates it, or after a rebase `git fetch origin`
followed by `git reset --keep origin/<branch>`, which refuses to discard local edits. When you are done,
`.\gradlew.bat --stop` stops the Gradle daemon and frees its memory. Then answer in any Claude Code session:
`qa1` when the playtest passed (the batch gets its pull request), or `qa2` with what is wrong (the agent revises
the same branch; [AI development flow](ai-development-flow.md#playtest-handoff)).

### 3.3 In WSL (quick checks)

Windows 11 shows Linux GUI apps through **WSLg**, so `./gradlew runClient` in WSL opens the game window on the
Windows desktop. Use it for quick checks: does the game load, does the item show up, does a screen open.

- **Mouse look does not work.** The camera snaps to straight down and does not follow the mouse: WSLg passes input
  over Wayland and RDP and gives GLFW no relative pointer lock, which first-person mouse look needs
  ([microsoft/wslg#376](https://github.com/microsoft/wslg/issues/376)). Our code cannot fix it, so anything that
  needs looking around runs on Windows (3.2).
- **WSLg must be on.** `%USERPROFILE%\.wslconfig` must not set `guiApplications=false` (the client then fails with
  `glfwInit failed`), and `echo $DISPLAY` in WSL prints `:0`.
- **Graphics** go through WSLg's GPU path (Mesa D3D12), which is slower than native. The `EARLYDISPLAY` line
  `GL info:` in the log names the renderer; `llvmpipe` there means software rendering on the CPU. Then run
  `wsl --update` in PowerShell, update the GPU driver on Windows, run `wsl --shutdown` and retry.
  `glxinfo -B` (package `mesa-utils`) should report a `D3D12` renderer.
- **Harmless log messages** that look like errors:
  - `Failed to load library flite` with a stack trace: the narrator; WSL has no `libflite.so`.
  - `Error starting SoundSystem` and `Failed to open OpenAL device`, plus ALSA lines in the console: WSL has no
    audio device.
  - `Ambiguity between arguments [teleport, ...]`: vanilla's command tree, logged in dev environments.
  - `Missing sound for event: minecraft:item.goat_horn.play` (and `minecraft:entity.goat.screaming.horn_break`):
    vanilla.
  - `Assets URL ... uses unexpected schema`: the NeoForge dev environment.
  - `Shader rendertype_entity_translucent_emissive could not find sampler named Sampler2`: vanilla.
- `./gradlew runServer` starts a dev dedicated server in `run/server`; accept the EULA once in
  `run/server/eula.txt`.

## 4. Playing the pack on Windows (Prism Launcher + packwiz-installer)

The modpack lives in `pack/` as packwiz metadata. Prism downloads the mods itself with packwiz-installer on
every launch, so the instance always matches the repository.

1. Install [Prism Launcher](https://prismlauncher.org/) on Windows and sign in with your Microsoft account.
2. **Add Instance** → Vanilla **1.21.1**, name it `Realmforge`. Then **Edit** → **Version** → **Install Loader** →
   **NeoForge 21.1.259** (the exact version from `pack/pack.toml`).
3. Download `packwiz-installer-bootstrap.jar` from
   [packwiz-installer-bootstrap releases](https://github.com/packwiz/packwiz-installer-bootstrap/releases) and put
   it into the instance's game folder (**Folder** button → `minecraft`, called `.minecraft` in older instances).
4. **Edit** → **Settings** → **Custom commands** → enable, and set **Pre-launch command** to one of:
   - local, from your WSL checkout (start `cd pack && packwiz serve` in WSL first; WSL forwards `localhost` to
     Windows):
     `"$INST_JAVA" -jar packwiz-installer-bootstrap.jar http://localhost:8080/pack.toml`
   - from GitHub (what is on `master`):
     `"$INST_JAVA" -jar packwiz-installer-bootstrap.jar https://raw.githubusercontent.com/matejkadlec/realmforge/master/pack/pack.toml`
5. Launch. The installer window lists the mods it downloads. On the first install it asks about optional mods:
   **Quark** is optional and off by default.
6. Install our own mod into the instance (from WSL). Put the instance's mods folder into
   `~/.gradle/gradle.properties` once:

   ```properties
   realmforge.prismModsDir=/mnt/c/Users/<windows-user>/AppData/Roaming/PrismLauncher/instances/Realmforge/minecraft/mods
   ```

   then run `./gradlew :mod:installToPrism` after each change. It builds the jar, removes older `realmforge-*.jar`
   files from that folder and copies the new one. Copying to `/mnt/c` is fine; only building there is slow.
   packwiz-installer leaves files it didn't install alone, so the jar survives pack updates. The QA mod is never
   installed into Prism.
7. Shaders: put [Complementary Shaders](https://www.complementary.dev/shaders/) into the instance's
   `shaderpacks/` folder and enable it in Iris's video settings.

Tip: Prism's **Edit** → **Settings** → **Java** can download a Java 21 runtime for the instance automatically.

## 5. Claude Code

`.claude/settings.json` (committed) registers a hook that validates content after every edit in `content/` or
`tools/`, and enables the MCP servers from `.mcp.json`. Personal settings go into `.claude/settings.local.json`
(gitignored). `CLAUDE.md` is the entry point for every agent session.

**Jira (Atlassian MCP).** The flow skills (`flow1`, `flow2`) and `monitor-pr` reach Jira (space Realmforge, key
`RLM`) through the `atlassian` server in `.mcp.json`, which runs the `mcp-remote` proxy. Install it once (needs
Node.js, for example through [nvm](https://github.com/nvm-sh/nvm)):

```bash
npm install -g mcp-remote@0.1.37
```

The first Claude Code session that starts the server opens Atlassian's login in the browser; the token is cached
in `~/.mcp-auth/`, so later sessions reuse it. `/mcp` in a Claude Code terminal shows whether the server is
connected. `.claude/settings.json` allows the Atlassian tools without a prompt, because the flows move and
comment on tickets on their own. Cloud sessions use the claude.ai Atlassian connector instead.

**Gradle queue.** Agents running in several worktrees start their Gradle builds and GameTests through
`.claude/scripts/gradle-queue.sh <task>`, which waits while the machine is short of memory
([AI development flow](ai-development-flow.md#gradle-queue)).

### Cloud sessions

Cloud sessions (claude.ai/code, or a cloud session started from the desktop app) run in a fresh container with
Java 21 and uv preinstalled; there is no SDKMAN and `JAVA_HOME` is already set.

`.claude/settings.json` runs `.claude/hooks/cloud-warmup.sh` at SessionStart: `--env` on every SessionStart, the
warm-up only at `startup` and `resume` (not after `/clear`, compaction or a fork, so it never starts mid-session
next to the agent's own Gradle runs). Both exit at once unless `CLAUDE_CODE_REMOTE` is `true`, so local sessions
are unaffected.

- **`cloud-warmup.sh --env`** (synchronous, milliseconds). The container resolves no hostnames itself; every
  download goes through Claude's HTTPS proxy. Its `NO_PROXY` still lists PyPI, so uv would skip the proxy and fail
  with `dns error`. The hook removes PyPI from `NO_PROXY` for the whole session (via `CLAUDE_ENV_FILE`).
- **`cloud-warmup.sh`** (`"async": true`, never delays the session). In the background it runs `uv sync --locked`,
  then `./gradlew :mod:extractMinecraftSources classes`: the Gradle distribution, all dependencies, the NeoForm
  decompile/recompile, `build/mc-sources/` and a first compile. The Gradle daemon keeps running for the agent's
  first build. Measured in a fresh clone with an empty Gradle home (October 2026): the first `./gradlew build`
  takes about 5 minutes cold; the warm-up takes about 4 minutes in the background, and the first build after it
  about 45 seconds.
  - Maven Central sometimes answers bursts with `429 Too Many Requests` for a few minutes. After a 429 (or a
    502/503/504) the warm-up retries the Gradle step after 60 s and again after 120 s; any other failure ends it
    at once.
  - State in `.cache/warmup/` (gitignored): `status` (`running`, `done` or `failed`), `summary`, `log`, and
    `stamp`, a hash of the build inputs (`gradle.properties`, wrapper properties, `settings.gradle`, the
    `build.gradle` files, `uv.lock`, `pyproject.toml`, `.python-version`). On resume, the same stamp skips the
    warm-up.
  - When it ends, Claude Code passes the summary to Claude on the next turn. On failure the summary and the log
    name the hosts the agent proxy refused.
  - **`cloud-warmup.sh --wait [seconds]`** blocks until the warm-up has finished (default limit 540 s, exit code
    2 when it is still running). Agents run it before their first Gradle command and after a resume (rule in
    `CLAUDE.md`).
- The environment's network access (environment settings → **Network access**) must allow the default package
  managers plus `downloads.gradle.org`; NeoForge, Parchment and Mojang hosts were reachable without extra
  entries. A blocked host fails with `403` / `CONNECT tunnel failed`; `curl -sS "$HTTPS_PROXY/__agentproxy/status"`
  lists the recent denials.
- The environment's setup script (environment settings) provisions tools only. It runs before Claude Code launches
  and its result is cached for later sessions, but only when it finishes within about five minutes. The Gradle
  warm-up alone takes about four, plus any rate-limit retries, so the project warm-up stays in the hook.
