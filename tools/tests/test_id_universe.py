from __future__ import annotations

import json

from realmforge_tools.id_universe import load_vanilla, vanilla_snapshot_path
from realmforge_tools.project import DATA_VERSION, MINECRAFT_VERSION, RepoPaths
from realmforge_tools.vanilla import _match


def test_vanilla_snapshot_matches_pinned_version() -> None:
    snapshot = json.loads(vanilla_snapshot_path(RepoPaths()).read_text(encoding="utf-8"))
    assert snapshot["minecraft_version"] == MINECRAFT_VERSION
    assert snapshot["data_version"] == DATA_VERSION


def test_vanilla_snapshot_has_static_and_data_driven_registries() -> None:
    universe = load_vanilla(RepoPaths())
    assert universe.contains("minecraft:item", "minecraft:stick")
    assert universe.contains("minecraft:block", "minecraft:stone")
    assert universe.contains("minecraft:creative_mode_tab", "minecraft:ingredients")
    assert universe.contains("minecraft:enchantment", "minecraft:sharpness")
    assert universe.contains("minecraft:worldgen/biome", "minecraft:plains")
    assert universe.contains("minecraft:loot_table", "minecraft:blocks/stone")
    assert universe.contains("minecraft:recipe", "minecraft:stick")
    assert universe.contains_tag("minecraft:item", "minecraft:logs")
    assert universe.contains_tag("minecraft:worldgen/biome", "minecraft:is_ocean")
    assert not universe.contains("minecraft:item", "realmforge:test_item")


def test_snapshot_is_sorted_for_stable_diffs() -> None:
    snapshot = json.loads(vanilla_snapshot_path(RepoPaths()).read_text(encoding="utf-8"))
    assert list(snapshot["registries"]) == sorted(snapshot["registries"])
    for ids in snapshot["registries"].values():
        assert ids == sorted(ids)


def test_longest_folder_wins() -> None:
    folders = sorted(["worldgen/structure", "structure", "worldgen/structure_set"], key=len, reverse=True)
    assert _match("worldgen/structure_set/villages", folders) == "worldgen/structure_set"
    assert _match("worldgen/structure/village_plains", folders) == "worldgen/structure"
    assert _match("structure/igloo/top", folders) == "structure"
    assert _match("unknown/x", folders) is None
