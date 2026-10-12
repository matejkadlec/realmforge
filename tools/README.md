# tools/

Python content pipeline (`realmforge_tools`, CLI `rf`). Requires Python 3.12+ and [uv](https://docs.astral.sh/uv/);
the project itself is defined in the root `pyproject.toml`.

```bash
uv run rf validate        # validate content/ (schema, ids, references)
uv run rf gen             # validate, then regenerate every generated file
uv run rf gen --check     # exit 1 if any generated file is out of date
uv run rf ids vanilla     # re-extract the vanilla ID universe (downloads the server jar, needs Java 21)
uv run rf pack-boot       # boot the whole pack plus mod/build/libs' jar on a server and a client (needs Java 21)
uv run rf gametest-log    # after ./gradlew runGameTestServer: exit 1 on data loading errors in its log
uv run rf art clean SRC DST   # snap a generated texture to the 16x16 grid and the project palette
uv run rf art gallery DIR     # review gallery for DIR/<subject>/<candidate>.png (.cache/art/)
uv run pytest             # tests
uv run ruff check && uv run ruff format --check
```

| Path | What |
| --- | --- |
| `realmforge_tools/schema/` | pydantic models, one per content type (`CONTENT_TYPES` maps `content/<dir>` to a model) |
| `realmforge_tools/content.py` | loads `content/` (step 1: schema) |
| `realmforge_tools/validate.py` | own ids and references against the ID universe (steps 2-3) |
| `realmforge_tools/id_universe.py`, `vanilla.py` | the ID universe and the vanilla extractor |
| `realmforge_tools/generate/` | generators; `generate_all` + `sync` own the generated directories; `datapack.py` writes every type in `DATAPACK_TYPES` as datapack registry entries (translation keys, unit conversion) |
| `realmforge_tools/formats/` | deterministic PNG, NBT, gzip/zlib and JSON writers; a PNG reader for generator output |
| `realmforge_tools/art/` | texture pipeline ([ADR 0023](../docs/decisions/0023-texture-generator.md)): palette, `rf art clean`, `rf art gallery` |
| `realmforge_tools/packboot.py` | `rf pack-boot`: installs and boots the pack with NeoForge's self tests ([below](#pack-boot)) |
| `realmforge_tools/gamelog.py` | NeoForge log parsing shared by `rf pack-boot` and `rf gametest-log` ([below](#gametest-log)) |
| `realmforge_tools/client_isolation.py` | the import check that keeps client classes under `client/` (architecture 18) |
| `id_universe/` | generated vanilla id snapshot (committed) |
| `schemas/` | generated JSON Schemas for editor completion (committed) |
| `tests/` | pytest |

Generated directories (owned by `rf gen`, never edit by hand): `mod/src/generated/`, `qa-mod/src/generated/`,
`tools/schemas/`. `tools/id_universe/` is written by `rf ids vanilla`.

## Pack boot

`uv run rf pack-boot` ([ADR 0018](../docs/decisions/0018-pack-boot-tests.md)) checks that the whole modpack plus the
core mod jar loads, on both sides, the way players and server owners install it. Build the jar first
(`./gradlew :mod:jar`). It works in `.cache/pack-boot/`:

1. installs the game: the NeoForge server from its installer (`server/`), or the client through the
   [portablemc](https://github.com/mindstorm38/portablemc) launcher run by `uvx` (`client/` is the game directory,
   `launcher/` holds the vanilla client, NeoForge, libraries and about 1 GB of assets);
2. installs the pack's mods for that side with packwiz-installer, serving `pack/` over a local HTTP server;
3. copies the jar into `mods/` and boots with NeoForge's self test, once with the optional mods (Quark) and once
   without; the client runs under Xvfb when `xvfb-run` exists (`sudo apt install xvfb`);
4. fails on a non-zero exit, a missing self-test report, a crash report, a jar that was not loaded, or an ERROR or
   FATAL log line that `ALLOWED_ERRORS` in `packboot.py` does not explain.

`--side server|client` boots one side, `--optional with|without` one variant, `--pack-dir` another pack (to try a
change, or a deliberately broken copy). Logs land in `.cache/pack-boot/reports/<side>/<variant>/`. The NeoForge
installer, packwiz-installer and its bootstrap are sha256-pinned and portablemc is version-pinned in `packboot.py`;
a NeoForge upgrade updates the pin there too (`test_packboot.py` fails until it matches `pack/pack.toml` and
`gradle.properties`).

## GameTest log

`./gradlew runGameTestServer` fails when a GameTest fails or when one of our datapack registries does not decode (the
server stops). Vanilla data is different: a broken loot table, recipe, advancement or tag is logged and skipped, so
the GameTests still pass. `uv run rf gametest-log` (CI's gradle job, after the GameTests) therefore reads
`run/gametest/logs/latest.log` and fails on every ERROR or FATAL line that `GAMETEST_ALLOWED_ERRORS` in `gamelog.py`
does not explain, on loot table validation warnings, and on a log without the "All N required tests passed" line.
`--log` checks another file.
