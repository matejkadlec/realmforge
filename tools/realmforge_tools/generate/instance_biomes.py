"""Instance biomes: ``content/dungeons/<id>.yaml`` -> the biome ``realmforge:dungeon_<id>``, named after the dungeon
type (RLM-409).

Every run of every dungeon type lives in the one instance dimension, whose generator fills it with
``minecraft:the_void``. The builder gives a run's rooms its dungeon type's biome and clearing puts the void back
(``InstanceBiomes``, architecture 15.5), so the mods that name the biome a player stands in show the dungeon's name:
Traveler's Titles' arrival title (``biome.<namespace>.<path>``) and Xaero's Minimap's biome line (the same key). The
biome is the void's (sky, water, no rain, no mobs), without the void's spawn platform feature. Its path has no ``/``,
because Xaero's Minimap builds the key from the raw path while Traveler's Titles turns ``/`` into ``.``.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.project import MOD_ID, RepoPaths
from realmforge_tools.schema.common import LocalizedText

PREFIX = "dungeon_"
# minecraft:the_void's settings (build/mc-sources/data/minecraft/worldgen/biome/the_void.json) without its features.
BIOME: dict[str, Any] = {
    "attributes": {"minecraft:visual/sky_color": "#7ba4ff"},
    "carvers": [],
    "downfall": 0.5,
    "effects": {"water_color": "#3f76e4"},
    "features": [],
    "has_precipitation": False,
    "spawn_costs": {},
    "spawners": {
        "ambient": [],
        "axolotls": [],
        "creature": [],
        "misc": [],
        "monster": [],
        "underground_water_creature": [],
        "water_ambient": [],
        "water_creature": [],
    },
    "temperature": 0.5,
}


def biome_id(dungeon_id: str) -> str:
    """The path of a dungeon type's biome; ``InstanceBiomes#biome`` derives the same."""
    return f"{PREFIX}{dungeon_id}"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    folder = data_dir(paths) / "worldgen" / "biome"
    return {folder / f"{biome_id(entry.model.id)}.json": dump_json(BIOME) for entry in content.dungeons}


def translations(content: Content) -> dict[str, LocalizedText]:
    """Every dungeon type's biome, named like the dungeon type."""
    return {f"biome.{MOD_ID}.{biome_id(entry.model.id)}": entry.model.name for entry in content.dungeons}
