"""Rooms: ``content/rooms/<id>.nbt`` -> ``data/realmforge/structure/rooms/<id>.nbt``, normalized, plus the
notices of the third-party sources whose rooms ship.

The jar's structure folders mirror ``content/rooms/`` (ADR 0028), so a room is the structure
``realmforge:rooms/<id>``. The template is written back normalized (``rooms/structure.py``): a room saved by a
structure block and the same room imported give the same bytes. The sidecars feed the template pools of the
dungeon framework (W4). Each source in ``third_party/<source>/`` that has a room gets its ``LICENSE`` and
``NOTICE`` copied byte for byte to ``META-INF/third_party/<source>/``, so the jar carries them next to its own
licenses.
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
    files = {folder / f"{room.id}.nbt": write_structure(room.structure) for room in content.rooms}
    shipped = {room.id.split("/")[1] for room in content.rooms if room.id.startswith("third_party/")}
    notices = paths.mod_generated / "resources" / "META-INF" / "third_party"
    for notice in content.room_notices:
        if notice.source in shipped:
            files[notices / notice.source / notice.name] = notice.raw
    return files
