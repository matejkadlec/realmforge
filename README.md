# Realmforge

An RPG mod and modpack for Minecraft **1.21.1** (NeoForge), inspired by World of Warcraft: Wrath of the Lich King:
races and classes, spells and talents, leveling with story chapters, procedurally generated dungeons and
dimensions, bosses with phases, professions, and a quest campaign. Set in the world of Realmforge.

**Status:** early development. Milestone M0 (bootstrap) provides the build, CI, the content pipeline and a single
test item; there is no gameplay yet. Goals: [docs/vision.md](docs/vision.md).

## How it is built

- **Core mod** (`mod/`, mod ID `realmforge`): all RPG systems, written by us.
- **Modpack** (`pack/`, [packwiz](https://packwiz.infra.link/)): the core mod plus third-party QoL, map, storage
  and performance mods (JEI/EMI, Jade, Xaero's maps, Sophisticated Storage, Sodium, Iris, ...).
- **Content pipeline** (`content/` + `tools/`): all game content is YAML, validated and turned into Java
  registrations, assets and data by Python. Generated files are committed and never edited by hand.
- **QA mod** (`qa-mod/`, `realmforge_qa`): development-only GameTests and debug tools; never released.

| Path | Contents |
| --- | --- |
| `content/` | YAML game content (single source of truth) |
| `tools/` | Python pipeline: validation, generators, tests |
| `mod/`, `qa-mod/` | NeoForge mods (Gradle, ModDevGradle) |
| `pack/` | packwiz modpack definition (metadata only, no jars) |
| `art/` | asset sources |
| `docs/` | vision, [decisions (ADRs)](docs/decisions/README.md), [research notes](docs/research-notes.md), [dev setup](docs/dev-setup.md) |

## Quick start

Requirements: Java 21, [uv](https://docs.astral.sh/uv/), and [packwiz](https://github.com/packwiz/packwiz) for pack
work. Full setup, including playing the pack with Prism Launcher on Windows: [docs/dev-setup.md](docs/dev-setup.md).

```bash
uv sync && uv run rf gen && uv run pytest   # validate content, regenerate, test
./gradlew build                             # build both mods + JUnit
./gradlew runGameTestServer                 # headless GameTests
./gradlew runClient                         # dev client
```

This project is developed mostly by AI agents; [CLAUDE.md](CLAUDE.md) is their guide and the best technical
overview of the repository.

## Distribution

Releases will be published on GitHub Releases, CurseForge later. Not on Modrinth.

## License

The repository contains two products with different licenses:

- **The Realmforge mod** (`mod/`, `qa-mod/`, `tools/` and everything shipped in the `realmforge` jar): source code
  under the [Mozilla Public License 2.0](LICENSE), assets (textures, models, sounds, texts) under
  [Creative Commons Attribution 4.0 International](LICENSE-ASSETS).
- **The Realmforge modpack** (`pack/`: pack definition, configuration, quests): **All Rights Reserved**, no
  redistribution. See [pack/LICENSE](pack/LICENSE).

Third-party mods are not part of this repository; the pack only references them, and each keeps its own license.
Details and reasoning: [ADR 0009](docs/decisions/0009-licensing.md).

Minecraft is a trademark of Mojang Studios. Realmforge is not affiliated with Mojang or Microsoft.
