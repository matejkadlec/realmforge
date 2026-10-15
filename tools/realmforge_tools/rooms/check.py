"""Room validation on the template itself (architecture 15.4): size, ids inside the ID universe (blocks, block
entities, entities, container loot tables, jigsaw pools), connectors and data markers. ``validate.py`` adds the
checks that need the room's sidecar: the entrance marker's room and fluids at the edge of a room in a dungeon's pool.

The blocks themes may swap come with the room generator (A4d).
"""

from __future__ import annotations

import math
from typing import Any

from realmforge_tools.id_universe import IdUniverse
from realmforge_tools.project import DATA_VERSION
from realmforge_tools.rooms.structure import JIGSAW, Pos, Structure, split_state_text

BLOCK_REGISTRY = "minecraft:block"
BLOCK_ENTITY_REGISTRY = "minecraft:block_entity_type"
ENTITY_REGISTRY = "minecraft:entity_type"
LOOT_TABLE_REGISTRY = "minecraft:loot_table"
TEMPLATE_POOL_REGISTRY = "minecraft:worldgen/template_pool"
# The block entity key that names a container's loot table (RandomizableContainer.LOOT_TABLE_TAG).
LOOT_TABLE = "LootTable"

# The most a structure block saves per axis (StructureBlockEntity.MAX_SIZE_PER_AXIS).
MAX_SIZE = 48

# The pool a jigsaw names when its data names none (JigsawBlockEntity reads a missing pool as Pools.EMPTY).
EMPTY_POOL = "minecraft:empty"

STRUCTURE_BLOCK = "minecraft:structure_block"
# The data markers a room may hold (architecture 15.3, step 3): structure blocks in data mode whose metadata names what
# stands there. The builder records them and never places them; the names are DataMarker.Kind in the core mod.
ENTRANCE_MARKER = "entrance"
MARKERS = (ENTRANCE_MARKER, "exit", "mob", "chest")
# The structure block mode of a data marker, as its block entity data names it (StructureMode.DATA).
DATA_MODE = "DATA"

# Blocks that are a fluid or always hold water (vanilla's LiquidBlock and LiquidBlockContainer blocks without a
# waterlogged property); any block whose state says waterlogged=true holds water too.
FLUID_BLOCKS = frozenset(
    {
        "minecraft:water",
        "minecraft:lava",
        "minecraft:bubble_column",
        "minecraft:kelp",
        "minecraft:kelp_plant",
        "minecraft:seagrass",
        "minecraft:tall_seagrass",
    }
)

# A jigsaw's front (the first part of its orientation) -> (axis, side) of the room's box it must sit on.
_JIGSAW_FACES = {
    "west": (0, "min"),
    "east": (0, "max"),
    "down": (1, "min"),
    "up": (1, "max"),
    "north": (2, "min"),
    "south": (2, "max"),
}


def check_structure(structure: Structure, universe: IdUniverse) -> list[tuple[str, str]]:
    """Problems as (location, message) pairs; an empty list means the room is valid."""
    problems: list[tuple[str, str]] = []
    size = structure.size
    if any(not 1 <= axis <= MAX_SIZE for axis in size):
        problems.append(("size", f"{_fmt(size)} exceeds {MAX_SIZE} blocks per axis or is empty"))
    if structure.data_version is None:
        problems.append(
            (
                "DataVersion",
                "missing: the game would read the room as saved with version 500 and run every upgrade since on "
                "it (a sign loses its text); a source known to be modern gets one from the import rules' "
                "data_version",
            )
        )
    elif structure.data_version > DATA_VERSION:
        problems.append(
            ("DataVersion", f"{structure.data_version} is newer than the game's ({DATA_VERSION})")
        )
    hint = ""
    if structure.data_version is None or structure.data_version < DATA_VERSION:
        hint = (
            f"; the room was saved with data version {structure.data_version or 'unknown'}, so the id may have "
            "been renamed since: remap it on import"
        )

    seen: set[Pos] = set()
    unknown: dict[tuple[str, str], list[Pos]] = {}
    unknown_loot: dict[str, list[Pos]] = {}
    for block in structure.blocks:
        if not all(0 <= c < s for c, s in zip(block.pos, size, strict=True)):
            problems.append((f"block {_fmt(block.pos)}", f"lies outside the room's size {_fmt(size)}"))
        if block.pos in seen:
            problems.append((f"block {_fmt(block.pos)}", "appears twice"))
        seen.add(block.pos)
        if not universe.contains(BLOCK_REGISTRY, block.state.name):
            unknown.setdefault((BLOCK_REGISTRY, block.state.name), []).append(block.pos)
        entity_id = block.block_entity_id
        if entity_id is not None and not universe.contains(BLOCK_ENTITY_REGISTRY, entity_id):
            unknown.setdefault((BLOCK_ENTITY_REGISTRY, entity_id), []).append(block.pos)
        loot_table = (block.nbt or {}).get(LOOT_TABLE)
        if isinstance(loot_table, str) and not universe.contains(LOOT_TABLE_REGISTRY, loot_table):
            unknown_loot.setdefault(loot_table, []).append(block.pos)
        if block.state.name == STRUCTURE_BLOCK:
            problems += _check_marker(block.pos, block.nbt)
        if block.state.name == JIGSAW:
            problems += _check_jigsaw(block.pos, block.state.get("orientation"), size)
            pool = (block.nbt or {}).get("pool")
            pool_id = pool if isinstance(pool, str) and pool else EMPTY_POOL
            if ":" not in pool_id:
                pool_id = f"minecraft:{pool_id}"
            if not universe.contains(TEMPLATE_POOL_REGISTRY, pool_id):
                problems.append(
                    (
                        f"jigsaw {_fmt(block.pos)}",
                        f"names template pool {pool_id!r}, which does not exist: the game would attach nothing "
                        "there; map it in the import rules' pools",
                    )
                )
            final_state = (block.nbt or {}).get("final_state")
            if isinstance(final_state, str):
                name = split_state_text(final_state)[0]
                if not universe.contains(BLOCK_REGISTRY, name):
                    problems.append(
                        (
                            f"jigsaw {_fmt(block.pos)}",
                            f"final_state names unknown block {name!r}, which the game cannot place{hint}",
                        )
                    )

    entrances = [pos for name, pos in markers(structure) if name == ENTRANCE_MARKER]
    if len(entrances) > 1:
        problems.append(
            (
                f"data marker {_fmt(entrances[1])}",
                f"the room holds {len(entrances)} entrance markers; players arrive at one",
            )
        )

    for entity in structure.entities:
        where = f"entity {entity.id or '?'} at {_fmt(entity.block_pos)}"
        if entity.id is None:
            problems.append((where, "has no id"))
        elif not universe.contains(ENTITY_REGISTRY, entity.id):
            unknown.setdefault((ENTITY_REGISTRY, entity.id), []).append(entity.block_pos)
        # Vanilla places an entity at its exact position (pos); blockPos only travels along. An entity may sit on
        # the room's far faces (an item frame on a wall), so both may reach the size itself.
        inside = all(math.isfinite(c) and 0 <= c <= s for c, s in zip(entity.pos, size, strict=True))
        if not inside or not all(0 <= c <= s for c, s in zip(entity.block_pos, size, strict=True)):
            position = " ".join(f"{c:g}" for c in entity.pos)
            problems.append((where, f"lies outside the room's size {_fmt(size)} (pos {position})"))

    for (registry, id_), positions in sorted(unknown.items()):
        count = f" ({len(positions)} times)" if len(positions) > 1 else ""
        problems.append(
            (f"at {_fmt(positions[0])}", f"unknown id {id_!r} in registry {registry}{count}{hint}")
        )
    for loot_table, positions in sorted(unknown_loot.items()):
        count = f" ({len(positions)} times)" if len(positions) > 1 else ""
        problems.append(
            (
                f"at {_fmt(positions[0])}",
                f"unknown loot table {loot_table!r}{count}: the container would stay empty; map it in the "
                "import rules' loot_tables",
            )
        )
    return problems


def markers(structure: Structure) -> list[tuple[str, Pos]]:
    """The room's data markers as (name, position), in block order: structure blocks in data mode, whatever they
    name (``check_structure`` reports unknown names)."""
    found = []
    for block in structure.blocks:
        data = block.nbt or {}
        if block.state.name == STRUCTURE_BLOCK and data.get("mode") == DATA_MODE:
            name = data.get("metadata")
            found.append((name if isinstance(name, str) else "", block.pos))
    return found


def fluid_edges(structure: Structure) -> list[Pos]:
    """Positions on the room's sides or floor that hold a fluid. A fluid flows down and sideways, so from there it
    leaves the room's box, past what cleanup clears (architecture 15.5); one on the roof stays inside."""
    size_x, _, size_z = structure.size
    edges = []
    for block in structure.blocks:
        if block.state.name not in FLUID_BLOCKS and block.state.get("waterlogged") != "true":
            continue
        x, y, z = block.pos
        if y == 0 or x in (0, size_x - 1) or z in (0, size_z - 1):
            edges.append(block.pos)
    return sorted(edges)


def connector_face(pos: Pos, orientation: str | None, size: Pos) -> str | None:
    """The face a jigsaw is a connector on (``north``, ``up``, ...): the one it points to, when it sits on it.
    None for a jigsaw inside the room or without a valid orientation."""
    front = (orientation or "").split("_")[0]
    face = _JIGSAW_FACES.get(front)
    if face is None:
        return None
    axis, side = face
    edge = 0 if side == "min" else size[axis] - 1
    return front if pos[axis] == edge else None


def _check_marker(pos: Pos, data: dict[str, Any] | None) -> list[tuple[str, str]]:
    """A structure block in a room is a data marker: data mode, and metadata naming one of ``MARKERS``."""
    data = data or {}
    mode = data.get("mode")
    if mode != DATA_MODE:
        return [
            (
                f"structure block {_fmt(pos)}",
                f"is in {mode or 'no'} mode; a room holds structure blocks only as data markers (mode {DATA_MODE}) "
                f"naming {', '.join(MARKERS)}",
            )
        ]
    name = data.get("metadata")
    if name not in MARKERS:
        return [(f"data marker {_fmt(pos)}", f"names {name!r}, which is no marker: {', '.join(MARKERS)}")]
    return []


def _check_jigsaw(pos: Pos, orientation: str | None, size: Pos) -> list[tuple[str, str]]:
    front = (orientation or "").split("_")[0]
    if front not in _JIGSAW_FACES:
        return [(f"jigsaw {_fmt(pos)}", f"has no valid orientation ({orientation!r})")]
    if connector_face(pos, orientation, size) is None:
        return [
            (
                f"jigsaw {_fmt(pos)}",
                f"faces {front} but is not on the room's {front} face; connectors sit on the boundary",
            )
        ]
    return []


def _fmt(pos: Pos) -> str:
    return " ".join(str(c) for c in pos)
