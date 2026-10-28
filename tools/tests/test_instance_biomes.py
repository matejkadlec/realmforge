"""Instance biomes: a biome per dungeon type, named after it, for the run's rooms (RLM-409; architecture 15.5)."""

from __future__ import annotations

import json

from realmforge_tools.project import RepoPaths

from .test_dungeons import crypt, generate

BIOMES = "mod/src/generated/resources/data/realmforge/worldgen/biome"
LANG = "mod/src/generated/resources/assets/realmforge/lang/en_us.json"


def test_every_dungeon_type_gets_a_biome_named_after_it(repo: RepoPaths) -> None:
    crypt(repo)
    files = generate(repo)
    biome = json.loads(files[f"{BIOMES}/dungeon_crypt.json"])
    assert biome["features"] == [], "no feature, not even the void's spawn platform"
    # minecraft:the_void's sky, water and weather, and no mobs.
    assert biome["attributes"] == {"minecraft:visual/sky_color": "#7ba4ff"}
    assert biome["effects"] == {"water_color": "#3f76e4"}
    assert (biome["has_precipitation"], biome["temperature"], biome["downfall"]) == (False, 0.5, 0.5)
    assert biome["spawn_costs"] == {} and not any(biome["spawners"].values())
    lang = json.loads(files[LANG])
    # Traveler's Titles and Xaero's Minimap both read biome.<namespace>.<path>; Xaero's with the raw path, so the path
    # has no slash.
    assert lang["biome.realmforge.dungeon_crypt"] == "Crypt"
    assert lang["biome.realmforge.dungeon_crypt"] == lang["dungeon_type.realmforge.crypt.name"]


def test_no_dungeon_type_no_biome(repo: RepoPaths) -> None:
    files = generate(repo)
    assert not [path for path in files if path.startswith(f"{BIOMES}/dungeon_")]
    assert not [key for key in json.loads(files[LANG]) if key.startswith("biome.realmforge.dungeon_")]
