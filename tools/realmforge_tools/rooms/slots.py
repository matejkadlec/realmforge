"""Draft slot tags for an imported room (A4c, architecture 15.4): what kind of piece it is, read from its connectors
and its name.

A room's sidecar tags say which slots of a dungeon layout it can fill. ``rf rooms import`` drafts them for a new
sidecar, and retagging by hand or in the gallery review corrects them; a re-import keeps the sidecar's tags.

- **Shape**, from the faces with a connector (a jigsaw on the room's boundary, pointing out): one side
  ``dead_end``, two opposite sides ``straight``, two adjacent sides ``curved``, three ``t_junction``, four
  ``crossing``. A connector up or down adds ``vertical``.
- **Role**, from a word in the room's path (``treasure/egg``, ``boss_room``): ``boss``, ``treasure``,
  ``entrance``, ``stairs`` or ``hallway``. Without one, a straight or curved piece at most ``HALLWAY_WIDTH`` blocks
  wide (across a straight piece's passage; either way for a curved one) is a ``hallway`` and anything else a
  ``combat`` room.
"""

from __future__ import annotations

import re

from realmforge_tools.rooms.check import connector_face
from realmforge_tools.rooms.structure import JIGSAW, Structure

# The widest a straight or curved piece without a role word may be and still count as a hallway.
HALLWAY_WIDTH = 7

_SIDES = ("north", "east", "south", "west")
_OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
# Role words, first match wins: a "boss_hallway" is a boss room.
_ROLES = (
    ("boss", ("boss", "final", "arena")),
    ("treasure", ("treasure", "loot", "reward")),
    ("entrance", ("entrance", "start", "spawn", "lobby")),
    ("stairs", ("stair", "fall", "drop", "shaft", "elevator")),
    ("hallway", ("hall", "corridor", "bridge", "passage", "tunnel")),
)
_WORD = re.compile(r"[a-z]+")


def connector_faces(structure: Structure) -> set[str]:
    """The faces with at least one connector."""
    return {
        face
        for block in structure.blocks
        if block.state.name == JIGSAW
        and (face := connector_face(block.pos, block.state.get("orientation"), structure.size)) is not None
    }


def shape(faces: set[str]) -> str | None:
    sides = [side for side in _SIDES if side in faces]
    if not sides:
        return None
    if len(sides) == 2:
        return "straight" if _OPPOSITE[sides[0]] == sides[1] else "curved"
    return {1: "dead_end", 3: "t_junction", 4: "crossing"}[len(sides)]


def role(name: str, faces: set[str], size: tuple[int, int, int]) -> str:
    words = _WORD.findall(name.lower())
    for tag, hints in _ROLES:
        if any(word.startswith(hint) for word in words for hint in hints):
            return tag
    piece_shape = shape(faces)
    if piece_shape == "straight":
        width = size[0] if "north" in faces else size[2]  # across the passage
    elif piece_shape == "curved":
        width = min(size[0], size[2])
    else:
        return "combat"
    return "hallway" if width <= HALLWAY_WIDTH else "combat"


def slot_tags(structure: Structure, name: str) -> list[str]:
    """Draft tags for a room: its role, its shape, ``vertical`` when it connects up or down. ``name`` is the room's
    path below its source (``dungeon/treasure/egg``)."""
    faces = connector_faces(structure)
    piece_shape = shape(faces)
    tags = [role(name, faces, structure.size)]
    if piece_shape is not None:
        tags.append(piece_shape)
    if faces & {"up", "down"}:
        tags.append("vertical")
    return tags
