"""Rooms: ``content/rooms/<id>.nbt`` -> ``data/realmforge/structure/rooms/<id>.nbt``, normalized.

The jar's structure folders mirror ``content/rooms/`` (ADR 0028), so a room is the structure
``realmforge:rooms/<id>``. The template is written back normalized (``rooms/structure.py``): a room saved by a
structure block and the same room imported give the same bytes. The sidecars feed the template pools of the
dungeon framework (W4).
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.project import RepoPaths
from realmforge_tools.rooms.structure import write_structure
from realmforge_tools.schema.room import ROOMS_DIR


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    folder = data_dir(paths) / "structure" / ROOMS_DIR
    return {folder / f"{room.id}.nbt": write_structure(room.structure) for room in content.rooms}
