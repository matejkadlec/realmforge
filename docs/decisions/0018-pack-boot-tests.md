# 0018. Pack boot tests: server and client self tests on every PR

Date: 2026-10-11

Status: Accepted (amends [0010](0010-testing-strategy.md))

## Context

[0010](0010-testing-strategy.md) planned two "later" layers for the whole pack: a dedicated server that boots with a
clean log on every PR, and a client that loads with Iris and Sodium through mc-runtime-test, nightly. Roadmap spike 6
asked whether the whole pack plus our jar boots on a dedicated server, whether the full client loads, how CI tests
that, and whether to test with Quark, the optional mod that packwiz-installer's command line always installs
([0013](0013-modpack-composition.md)).

NeoForge 21.1.259 has self tests built in (`SelfTest`, `SelfTestClient`): with `NEOFORGE_DEDICATED_SERVER_SELFTEST`
or `NEOFORGE_CLIENT_SELFTEST` set to a file path, the game writes that file and stops at the server's first tick, or
at the first client tick after the loading overlay (the title screen).

What the spike measured on 2026-10-11 (WSL2, the pack of `master` at `a5afcd1`, `realmforge` 0.0.1):

- **Server:** the 44 server-side mods and our jar boot in 30 to 35 s, with and without Quark. The only ERROR line
  came from a first start without `server.properties`; it is gone once the test writes that file. About 300 WARN
  lines remain, mostly NeoForge noting that Quark and Regions Unexplored add mob spawns under another mob category.
- **Client:** the 58 client mods and our jar, started by the portablemc launcher under Xvfb on Mesa's software
  renderer (llvmpipe, OpenGL 4.5), reach the title screen in about 41 s. Two ERROR lines, both from the headless
  machine: Xaero's World Map could not read its online version data, and the sound system found no audio device.
- **A broken pack** (Balm removed, which Waystones and TrashSlot need): the server stops before its first tick with
  FATAL lines and a crash report, yet the process exits with code 0. The report file, not the exit code, says
  whether the game loaded.

## Decision

- **One command, both sides:** `uv run rf pack-boot` (`tools/realmforge_tools/packboot.py`) installs each side the
  way it is installed in practice and boots it: the NeoForge server from its installer, the client through
  portablemc; the mods with packwiz-installer's command line from `pack/`, served over a local HTTP server; our jar
  from `mod/build/libs/`. `--side server|client` runs one side.
- **Pinned tools:** the NeoForge installer, packwiz-installer 0.5.14 and its bootstrap 0.0.3 by sha256, portablemc
  4.4.1 by version (through `uvx`). portablemc is GPL-3.0-only; it runs as a separate tool and is never imported or
  shipped.
- **A boot passes** when the game exits with code 0, writes the self-test report, writes no crash report, loads our
  jar, and logs no ERROR or FATAL line outside its side's allowlist. The allowlists stay short, and every entry
  carries its reason. The client's three headless lines are its only entries today: Xaero's World Map version
  check, no audio device, and no flite library for the narrator (missing on CI runners).
- **Optional mods both ways:** each side boots with the optional mods (what every command-line install has) and
  without them (the default choice in Prism).
- **CI on every PR and `master` push:** the `pack-boot` job boots the server, the `pack-boot-client` job the client
  under Xvfb with Mesa. They cache the installers and the installed game (server libraries; the launcher's
  libraries and assets), not the mods, which are installed fresh like on a new machine.
- **The client layer of [0010](0010-testing-strategy.md)** ("client loads with Iris and Sodium") becomes the client
  self test on every PR instead of mc-runtime-test nightly. Screenshots and the item icon sheet still need a tool
  that renders a world; that choice comes with the asset work.

## Consequences

- Every PR proves that the pack and our jar load on both sides, including the startup of our `client/` code, which
  no GameTest runs.
- The two jobs run beside the Gradle job, so they add little wall time, but each run downloads every mod jar from
  Modrinth and CurseForge: an outage there fails the job. Caching the mods is the fix if that becomes frequent.
- The jobs protect `master` only once Matěj adds them to the ruleset's required checks.
- The client runs on software rendering and stops at the title screen: it proves that the client loads, not how
  the game looks or performs, and it never joins a world (the server boot covers world generation). Matěj's
  playtests in Prism remain the judge of rendering, shaders and frame rate.
- Changing the NeoForge version means updating the pin in `packboot.py`; a pytest fails while it disagrees with
  `pack/pack.toml` or `gradle.properties`.
