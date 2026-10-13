# 0009. Licensing

Date: 2026-10-11

Status: Accepted; config overrides noted 2026-10-13 (RLM-151)

## Context

The repository contains two products with different goals: the Realmforge mod (open source, reusable) and the Realmforge modpack (the owner's curated experience, not meant to be redistributed by others). The research report proposed MIT for code and CC0 for assets, partly because purely AI-generated outputs are probably not copyrightable ([research notes, section 5](../research-notes.md#license-and-legal-notes)). The owner chose differently.

## Decision

This supersedes the report's MIT/CC0 proposal.

- **The mod** (`mod/`, `qa-mod/`, `tools/` and everything shipped in the `realmforge` jar):
  - code under **MPL-2.0** (`LICENSE` at the repo root);
  - assets under **CC BY 4.0** (`LICENSE-ASSETS` at the repo root).
- **The modpack** (`pack/`: pack definition, configs, quests): **All Rights Reserved, no redistribution** (`pack/LICENSE`).
- `neoforge.mods.toml` declares `license = "MPL-2.0 AND CC-BY-4.0"`.
- No third-party mod jars in the repo; the pack holds packwiz metadata only, so third-party licenses (including ARR ones) are not affected.
  - 2026-10-13 (RLM-151): the pack also ships config overrides in `pack/config/` ([0013](0013-modpack-composition.md)), each the mod's own generated config file with only the pack's values changed. Shipping config files is what every modpack does, and the jars themselves still come from their original sources, so third-party licenses stay unaffected.
- Third-party code is never copied into our MPL-2.0 files unless its license allows it (Spell Engine is GPL-3.0-only, Explorer's Compass CC BY-NC-SA: no reuse; see [0004](0004-build-rpg-systems-in-house.md)).
- No vanilla textures or derivatives in our assets ([0007](0007-asset-pipeline.md)).

## Consequences

- Others may reuse and modify the mod's code (file-level copyleft) and assets (with attribution), but not republish the modpack.
- Purely AI-generated assets are probably not protected by copyright (US Copyright Office 2025 report, Thaler v. Perlmutter; Prague Municipal Court 10 C 13/2023). For such assets the CC BY 4.0 attribution requirement may not be enforceable; the owner accepts this.
- Not yet stated explicitly: the license of `content/` (it feeds both the jar and the ARR quests in the pack), `art/` and `docs/`. Clarify in a follow-up ADR if it matters.
- The name avoids third-party trademarks: "Claude" is an Anthropic trademark, and "Minecraft" must not be the dominant part of a name.
