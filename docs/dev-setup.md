# Development setup

Development happens in **WSL (Ubuntu)**; playing and playtesting the full modpack happens on **Windows** with
Prism Launcher. Background: [ADR 0011](decisions/0011-repository-layout-and-workflow.md).

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

## 2. First build

```bash
uv sync                      # Python environment
uv run rf gen --check        # content valid, generated files up to date
uv run pytest
./gradlew build              # first run downloads Minecraft + assets (~1 GB) and decompiles: 5-10 minutes
./gradlew runGameTestServer  # headless GameTests
```

Optional, for agents and for reading Minecraft code: `./gradlew :mod:extractMinecraftSources` unpacks the
decompiled Minecraft + NeoForge sources into `build/mc-sources/`.

## 3. `runClient` in WSL (quick checks)

`./gradlew runClient` starts a dev client with the core mod and the QA mod (game directory `run/client`, offline
player named `Dev`). Windows 11 shows Linux GUI apps through **WSLg**, so the game window opens on the Windows
desktop.

- Good for quick checks: does it load, does the item show up, does a screen open. No third-party mods are loaded,
  only ours.
- Graphics go through WSLg's GPU path (Mesa D3D12), which is slower than native. For real play and shaders use
  Prism on Windows (next section).
- If the window doesn't open or OpenGL fails: run `wsl --update` in PowerShell, update the GPU driver on Windows,
  then `wsl --shutdown` and retry. `glxinfo -B` (package `mesa-utils`) should report a `D3D12` renderer.
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
`tools/`. Personal settings go into `.claude/settings.local.json` (gitignored). `CLAUDE.md` is the entry point
for every agent session.
