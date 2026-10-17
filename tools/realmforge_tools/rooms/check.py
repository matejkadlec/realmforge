"""Room validation on the template itself (architecture 15.4): size, ids inside the ID universe (blocks, block
entities, entities, container loot tables, jigsaw pools), connectors and data markers. ``validate.py`` adds the
checks that need the room's sidecar: the entrance marker's room and fluids that can flow out of a room in a dungeon's pool.

The blocks themes may swap come with the room generator (A4d).
"""

from __future__ import annotations

import math
from typing import Any

from realmforge_tools.id_universe import IdUniverse
from realmforge_tools.project import DATA_VERSION
from realmforge_tools.rooms.structure import (
    JIGSAW,
    STRUCTURE_VOID,
    Pos,
    Structure,
    StructureBlock,
    split_state_text,
)

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

# Blocks a flowing fluid never enters beside the waterloggable ones (FlowingFluid#canHoldFluid): doors, signs and
# the rest of its list.
_CLOSED_BLOCKS = frozenset(
    {
        "minecraft:ladder",
        "minecraft:sugar_cane",
        "minecraft:bubble_column",
        "minecraft:nether_portal",
        "minecraft:end_portal",
        "minecraft:end_gateway",
    }
)
_CLOSED_SUFFIXES = ("_door", "_sign", "_wall_sign", "_hanging_sign")
# Full solid blocks (BlockState#blocksMotion), by name or name ending: the stone and wood the rooms are built of. A
# fluid flows into anything not named here, so the list only has to grow when a room's fluid sits against a solid
# block it lacks.
SOLID_BLOCKS = frozenset(
    {
        "minecraft:stone",
        "minecraft:cobblestone",
        "minecraft:mossy_cobblestone",
        "minecraft:smooth_stone",
        "minecraft:andesite",
        "minecraft:diorite",
        "minecraft:granite",
        "minecraft:polished_andesite",
        "minecraft:polished_diorite",
        "minecraft:polished_granite",
        "minecraft:deepslate",
        "minecraft:cobbled_deepslate",
        "minecraft:polished_deepslate",
        "minecraft:tuff",
        "minecraft:polished_tuff",
        "minecraft:calcite",
        "minecraft:blackstone",
        "minecraft:polished_blackstone",
        "minecraft:gilded_blackstone",
        "minecraft:basalt",
        "minecraft:polished_basalt",
        "minecraft:smooth_basalt",
        "minecraft:netherrack",
        "minecraft:obsidian",
        "minecraft:crying_obsidian",
        "minecraft:bedrock",
        "minecraft:dirt",
        "minecraft:coarse_dirt",
        "minecraft:rooted_dirt",
        "minecraft:grass_block",
        "minecraft:podzol",
        "minecraft:mycelium",
        "minecraft:gravel",
        "minecraft:sand",
        "minecraft:red_sand",
        "minecraft:sandstone",
        "minecraft:red_sandstone",
        "minecraft:smooth_sandstone",
        "minecraft:smooth_red_sandstone",
        "minecraft:cut_sandstone",
        "minecraft:cut_red_sandstone",
        "minecraft:chiseled_sandstone",
        "minecraft:chiseled_red_sandstone",
        "minecraft:clay",
        "minecraft:mud",
        "minecraft:packed_mud",
        "minecraft:prismarine",
        "minecraft:dark_prismarine",
        "minecraft:sea_lantern",
        "minecraft:glowstone",
        "minecraft:end_stone",
        "minecraft:purpur_pillar",
        "minecraft:quartz_pillar",
        "minecraft:smooth_quartz",
        "minecraft:bookshelf",
        "minecraft:chiseled_bookshelf",
        "minecraft:spawner",
        "minecraft:trial_spawner",
        "minecraft:vault",
        "minecraft:glass",
        "minecraft:terracotta",
        "minecraft:magma_block",
        "minecraft:soul_sand",
        "minecraft:soul_soil",
        "minecraft:sponge",
        "minecraft:wet_sponge",
        "minecraft:ice",
        "minecraft:packed_ice",
        "minecraft:blue_ice",
        "minecraft:snow_block",
        "minecraft:crafting_table",
        "minecraft:barrel",
        "minecraft:furnace",
        "minecraft:blast_furnace",
        "minecraft:smoker",
        "minecraft:dispenser",
        "minecraft:dropper",
        "minecraft:observer",
        "minecraft:piston",
        "minecraft:sticky_piston",
        "minecraft:note_block",
        "minecraft:jukebox",
        "minecraft:tnt",
        "minecraft:target",
        "minecraft:hay_block",
        "minecraft:bone_block",
        "minecraft:crimson_stem",
        "minecraft:warped_stem",
        "minecraft:stripped_crimson_stem",
        "minecraft:stripped_warped_stem",
        "minecraft:pumpkin",
        "minecraft:carved_pumpkin",
        "minecraft:jack_o_lantern",
        "minecraft:melon",
        "minecraft:redstone_lamp",
        "minecraft:infested_stone",
        "minecraft:infested_cobblestone",
        "minecraft:infested_stone_bricks",
        "minecraft:infested_mossy_stone_bricks",
        "minecraft:infested_cracked_stone_bricks",
        "minecraft:infested_chiseled_stone_bricks",
        "minecraft:infested_deepslate",
        "minecraft:barrier",
    }
)
_SOLID_SUFFIXES = (
    "_bricks",
    "_planks",
    "_log",
    "_wood",
    "_hyphae",
    "_ore",
    "_block",
    "_wool",
    "_concrete",
    "_concrete_powder",
    "_terracotta",
    "_glass",
    "_leaves",
    "_tiles",
)
# The directions a fluid flows: down and to the four sides, never up.
_FLOW = ((0, -1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1))

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
    """Positions of the room's fluids that can flow out of its box. A fluid flows down and sideways, never up, so
    each one is followed through every cell it can enter (``_holds_flowing_fluid``). Once it reaches the floor or a
    side it leaves the box, past what cleanup clears (architecture 15.5), unless it leaves through a connector, where
    the next room continues. A fluid on the roof or in a closed basin stays inside."""
    size = structure.size
    at = {block.pos: block for block in structure.blocks}
    exits = {block.pos for block in structure.blocks if block.state.name == JIGSAW}
    contained: set[Pos] = (
        set()
    )  # cells from which no fluid leaves: everything they reach was followed already
    edges = []
    for block in structure.blocks:
        if block.state.name not in FLUID_BLOCKS and block.state.get("waterlogged") != "true":
            continue
        if _fluid_leaves(block.pos, size, at, exits, contained):
            edges.append(block.pos)
    return sorted(edges)


def _fluid_leaves(
    start: Pos, size: Pos, at: dict[Pos, StructureBlock], exits: set[Pos], contained: set[Pos]
) -> bool:
    """Whether a fluid at ``start`` can flow out of the box: a flood fill down and sideways through the cells that
    hold flowing fluid. A fill that stays inside adds its cells to ``contained``, which later fills skip."""
    seen = {start}
    todo = [start]
    while todo:
        x, y, z = todo.pop()
        for dx, dy, dz in _FLOW:
            next_pos = (x + dx, y + dy, z + dz)
            if not all(0 <= c < s for c, s in zip(next_pos, size, strict=True)):
                if (x, y, z) not in exits:
                    return True
                continue
            if next_pos in seen or next_pos in contained:
                continue
            block = at.get(next_pos)
            if block is not None and not _holds_flowing_fluid(block):
                continue
            seen.add(next_pos)
            todo.append(next_pos)
    contained.update(seen)
    return False


def _holds_flowing_fluid(block: StructureBlock) -> bool:
    """Whether a flowing fluid can enter the block (``FlowingFluid#canHoldFluid``). Python knows only names, so a
    block counts as closed only when it surely is: a block that takes water only as a source (any block with a
    waterlogged property), a door, a sign, a ladder, sugar cane, a portal, or a block in ``SOLID_BLOCKS``. Anything
    else lets the fluid through: an unknown block, a missing cell (structure void keeps whatever the plot holds), a
    jigsaw (it becomes its final state, usually air) and a data marker (never placed). A room that fails on a solid
    block missing from the list gets the block added there."""
    state = block.state
    name = state.name
    if name in (STRUCTURE_BLOCK, STRUCTURE_VOID):  # never placed: the cell keeps whatever the plot holds
        return True
    if state.get("waterlogged") is not None or name in _CLOSED_BLOCKS or is_solid(name):
        return False
    return not name.partition(":")[2].endswith(_CLOSED_SUFFIXES)


def is_solid(name: str) -> bool:
    """Whether a block is surely a full solid block: in ``SOLID_BLOCKS``, or named like one (``_bricks``,
    ``_planks``, ...)."""
    return name in SOLID_BLOCKS or name.partition(":")[2].endswith(_SOLID_SUFFIXES)


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
