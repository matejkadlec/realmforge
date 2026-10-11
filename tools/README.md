# tools/

Python content pipeline (`realmforge_tools`, CLI `rf`). Requires Python 3.12+ and [uv](https://docs.astral.sh/uv/);
the project itself is defined in the root `pyproject.toml`.

```bash
uv run rf validate        # validate content/ (schema, ids, references)
uv run rf gen             # validate, then regenerate every generated file
uv run rf gen --check     # exit 1 if any generated file is out of date
uv run rf ids vanilla     # re-extract the vanilla ID universe (downloads the server jar, needs Java 21)
uv run pytest             # tests
uv run ruff check && uv run ruff format --check
```

| Path | What |
| --- | --- |
| `realmforge_tools/schema/` | pydantic models, one per content type (`CONTENT_TYPES` maps `content/<dir>` to a model) |
| `realmforge_tools/content.py` | loads `content/` (step 1: schema) |
| `realmforge_tools/validate.py` | own ids and references against the ID universe (steps 2-3) |
| `realmforge_tools/id_universe.py`, `vanilla.py` | the ID universe and the vanilla extractor |
| `realmforge_tools/generate/` | generators; `generate_all` + `sync` own the generated directories |
| `realmforge_tools/formats/` | deterministic PNG, NBT, gzip/zlib and JSON writers |
| `id_universe/` | generated vanilla id snapshot (committed) |
| `schemas/` | generated JSON Schemas for editor completion (committed) |
| `tests/` | pytest |

Generated directories (owned by `rf gen`, never edit by hand): `mod/src/generated/`, `qa-mod/src/generated/`,
`tools/schemas/`. `tools/id_universe/` is written by `rf ids vanilla`.
