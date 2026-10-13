"""Room validation on the template itself (architecture 15.4): size, ids inside the ID universe, connectors.

Markers and the blocks themes may swap come with the dungeon framework (W4) and the room generator (A4d).
"""

from __future__ import annotations

from realmforge_tools.id_universe import IdUniverse
from realmforge_tools.project import DATA_VERSION
from realmforge_tools.rooms.structure import JIGSAW, Pos, Structure, split_state_text

BLOCK_REGISTRY = "minecraft:block"
BLOCK_ENTITY_REGISTRY = "minecraft:block_entity_type"
ENTITY_REGISTRY = "minecraft:entity_type"

# The most a structure block saves per axis (StructureBlockEntity.MAX_SIZE_PER_AXIS).
MAX_SIZE = 48

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
    if structure.data_version is not None and structure.data_version > DATA_VERSION:
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
        if block.state.name == JIGSAW:
            problems += _check_jigsaw(block.pos, block.state.get("orientation"), size)
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

    for entity in structure.entities:
        where = f"entity {entity.id or '?'} at {_fmt(entity.block_pos)}"
        if entity.id is None:
            problems.append((where, "has no id"))
        elif not universe.contains(ENTITY_REGISTRY, entity.id):
            unknown.setdefault((ENTITY_REGISTRY, entity.id), []).append(entity.block_pos)
        if not all(0 <= c < s for c, s in zip(entity.block_pos, size, strict=True)):
            problems.append((where, f"lies outside the room's size {_fmt(size)}"))

    for (registry, id_), positions in sorted(unknown.items()):
        count = f" ({len(positions)} times)" if len(positions) > 1 else ""
        problems.append(
            (f"at {_fmt(positions[0])}", f"unknown id {id_!r} in registry {registry}{count}{hint}")
        )
    return problems


def _check_jigsaw(pos: Pos, orientation: str | None, size: Pos) -> list[tuple[str, str]]:
    front = (orientation or "").split("_")[0]
    face = _JIGSAW_FACES.get(front)
    if face is None:
        return [(f"jigsaw {_fmt(pos)}", f"has no valid orientation ({orientation!r})")]
    axis, side = face
    edge = 0 if side == "min" else size[axis] - 1
    if pos[axis] != edge:
        return [
            (
                f"jigsaw {_fmt(pos)}",
                f"faces {front} but is not on the room's {front} face; connectors sit on the boundary",
            )
        ]
    return []


def _fmt(pos: Pos) -> str:
    return " ".join(str(c) for c in pos)
