# art/

Sources for assets: generation prompts, palettes, Blockbench projects (`.bbmodel`) and raw images before they
are processed into game textures. Nothing here is loaded by the game directly; the pipeline in `tools/` turns
approved sources into files under `mod/src/generated/`.

Empty for now: every texture is a generated placeholder (`tools/realmforge_tools/generate/textures.py`).
Planned tooling and rules: [ADR 0007](../docs/decisions/0007-asset-pipeline.md). Never use vanilla textures or
recolors of them, not even as AI input.
