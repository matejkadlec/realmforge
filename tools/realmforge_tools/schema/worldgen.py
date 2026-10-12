"""Worldgen JSON: ``content/worldgen/<directory>/<id>.json`` (architecture 15.1, ADR 0012).

Vanilla worldgen formats are large and owned by Minecraft's codecs, so these files stay JSON and are validated
(shape, ids, a few references) and copied into the jar unchanged; decoding them is the game's job, at world load.
"""

from __future__ import annotations

# content/worldgen/<directory> -> the vanilla datapack registry its files belong to. A file is copied to
# data/realmforge/<registry path>/<id>.json, e.g. content/worldgen/biome/x.json -> data/realmforge/worldgen/biome/.
WORLDGEN_REGISTRIES: dict[str, str] = {
    "dimension": "minecraft:dimension",
    "dimension_type": "minecraft:dimension_type",
    "biome": "minecraft:worldgen/biome",
    "configured_carver": "minecraft:worldgen/configured_carver",
    "configured_feature": "minecraft:worldgen/configured_feature",
    "placed_feature": "minecraft:worldgen/placed_feature",
    "density_function": "minecraft:worldgen/density_function",
    "noise": "minecraft:worldgen/noise",
    "noise_settings": "minecraft:worldgen/noise_settings",
    "multi_noise_biome_source_parameter_list": "minecraft:worldgen/multi_noise_biome_source_parameter_list",
    "structure": "minecraft:worldgen/structure",
    "structure_set": "minecraft:worldgen/structure_set",
    "processor_list": "minecraft:worldgen/processor_list",
    "template_pool": "minecraft:worldgen/template_pool",
    "world_preset": "minecraft:worldgen/world_preset",
    "flat_level_generator_preset": "minecraft:worldgen/flat_level_generator_preset",
}


def data_path(directory: str) -> str:
    """The folder under ``data/<namespace>/`` that holds the registry's entries (``worldgen/biome``)."""
    return WORLDGEN_REGISTRIES[directory].removeprefix("minecraft:")
