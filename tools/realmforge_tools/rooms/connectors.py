"""A room's connectors as the game reads them: the jigsaw blocks on the room's boundary, pointing out (architecture
15.3, 15.4).

The dungeon planner joins two rooms where a connector of one meets a connector of the other, by vanilla's jigsaw
rules (``JigsawBlock#canAttach``): their fronts are opposite, the first's ``target`` is the second's ``name``, and a
vertical connector with an ``aligned`` joint keeps its top. Missing fields read as vanilla reads them
(``StructureTemplate.JigsawBlockInfo#of``): ``minecraft:empty`` for the name, target and pool, and the joint
``aligned`` for a connector on a side, ``rollable`` for one up or down.
"""

from __future__ import annotations

from dataclasses import dataclass

from realmforge_tools.rooms.check import connector_face
from realmforge_tools.rooms.structure import AIR, JIGSAW, STRUCTURE_VOID, Pos, Structure

EMPTY = "minecraft:empty"
_VERTICAL = ("up", "down")
# Cells a doorway leaves open: air, and cells the template does not place (structure void, a missing cell).
_OPEN = frozenset({AIR, "minecraft:cave_air", "minecraft:void_air", STRUCTURE_VOID})
# The way across a side connector's face, to its right as one looks out through it.
_ACROSS = {"north": (1, 0, 0), "south": (-1, 0, 0), "east": (0, 0, 1), "west": (0, 0, -1)}


@dataclass(frozen=True)
class Connector:
    """One connector: where it sits in the template, which way it points (``front``) and which way its top faces
    (``up`` for a connector on a side; a side for one up or down), its jigsaw names, its pool and its joint."""

    pos: Pos
    front: str
    top: str
    name: str
    target: str
    pool: str
    joint: str

    @property
    def vertical(self) -> bool:
        return self.front in _VERTICAL


def connectors(structure: Structure) -> list[Connector]:
    """The room's connectors in template order. A jigsaw inside the room is none; room validation refuses one."""
    found: list[Connector] = []
    for block in structure.blocks:
        if block.state.name != JIGSAW:
            continue
        orientation = block.state.get("orientation") or ""
        if connector_face(block.pos, orientation, structure.size) is None:
            continue
        front, _, top = orientation.partition("_")
        nbt = block.nbt or {}
        joint = nbt.get("joint")
        if not isinstance(joint, str) or joint not in ("aligned", "rollable"):
            joint = "rollable" if front in _VERTICAL else "aligned"
        found.append(
            Connector(
                pos=block.pos,
                front=front,
                top=top,
                name=_identifier(nbt.get("name")),
                target=_identifier(nbt.get("target")),
                pool=_identifier(nbt.get("pool")),
                joint=joint,
            )
        )
    return found


def could_attach(source: Connector, target: Connector) -> bool:
    """Whether ``target``'s room can be turned so that ``target`` takes ``source``: the source's target is the
    target's name, and both sit on a side, or one points up and the other down. Turning a room about the vertical
    axis brings any side, and any top of a vertical connector, round to the one needed."""
    if source.target != target.name:
        return False
    if source.vertical or target.vertical:
        return {source.front, target.front} == set(_VERTICAL)
    return True


def doorway_gap(structure: Structure, connector: Connector, width: int, height: int) -> Pos | None:
    """A cell of the room's face just around the doorway of a side connector that a wall of ``width`` x ``height``
    blocks (its connector at the middle of its bottom row, ``generate/dungeons.py``) leaves open, or None when the
    wall closes the doorway (RLM-364). Every cell of the face beside the wall's face, above or below it (not the
    corners, which a player cannot pass) must be closed: no air, nothing the template does not place. Cells outside
    the template are outside the room."""
    if connector.vertical:
        return None
    blocks = {block.pos: block.state.name for block in structure.blocks}
    dx, _, dz = _ACROSS[connector.front]
    half = width // 2
    x, y, z = connector.pos
    for up in range(-1, height + 1):
        for side in range(-half - 1, half + 2):
            beside = abs(side) == half + 1 and 0 <= up < height
            across = abs(side) <= half and up in (-1, height)
            if not (beside or across):
                continue
            cell = (x + side * dx, y + up, z + side * dz)
            if not all(0 <= c < size for c, size in zip(cell, structure.size, strict=True)):
                continue
            if blocks.get(cell, STRUCTURE_VOID) in _OPEN:
                return cell
    return None


def _identifier(value: object) -> str:
    """A jigsaw's name, target or pool as the game reads it: an id without a namespace is ``minecraft:``'s."""
    if not isinstance(value, str) or not value:
        return EMPTY
    return value if ":" in value else f"minecraft:{value}"
