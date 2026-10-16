"""Rooms: ``content/rooms/<id>.nbt`` -> ``data/realmforge/structure/rooms/<id>.nbt``, normalized, plus the
notices of the third-party sources whose rooms ship, and the expectations a GameTest checks the loaded rooms against.

The jar's structure folders mirror ``content/rooms/`` (ADR 0028), so a room is the structure
``realmforge:rooms/<id>``. The template is written back normalized (``rooms/structure.py``): a room saved by a
structure block and the same room imported give the same bytes. The sidecars' pools are the room pools that dungeon
types are built from (``generate/dungeons.py``). Each source in ``third_party/<source>/`` that has a room gets its
``LICENSE``, ``NOTICE`` and (LGPL-3.0) ``COPYING`` copied byte for byte to ``META-INF/third_party/<source>/``, so
the jar carries them next to its own licenses.

``GeneratedRoomExpectations`` (QA mod) lists every room with its size, its jigsaws and its air blocks:
``ContentRoomGameTests`` loads each one through the game's structure manager, which upgrades an imported room from
the data version it was saved with, and compares. A block the game cannot read becomes air, so the air count catches
it.
"""

from __future__ import annotations

from functools import partial
from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.project import MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.rooms.structure import AIR, JIGSAW, count_blocks, write_structure
from realmforge_tools.schema.room import ROOMS_DIR

QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    folder = data_dir(paths) / "structure" / ROOMS_DIR
    files = {folder / f"{room.id}.nbt": room.derived("normalized", write_structure) for room in content.rooms}
    shipped = {room.id.split("/")[1] for room in content.rooms if room.id.startswith("third_party/")}
    notices = paths.mod_generated / "resources" / "META-INF" / "third_party"
    for notice in content.room_notices:
        if notice.source in shipped:
            files[notices / notice.source / notice.name] = notice.raw
    expectations = java_file_path(
        paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedRoomExpectations"
    )
    files[expectations] = _expectations(content)
    return files


def _expectations(content: Content) -> bytes:
    rows = []
    for room in content.rooms:
        x, y, z = room.size
        jigsaws = room.derived("jigsaws", partial(count_blocks, name=JIGSAW))
        air = room.derived("air", partial(count_blocks, name=AIR))
        structure_id = string_literal(f"{MOD_ID}:{ROOMS_DIR}/{room.id}")
        rows.append(f"            new RoomExpectation({structure_id}, {x}, {y}, {z}, {jigsaws}, {air})")
    rooms_list = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
    source = (
        header(Path(__file__), "content/rooms/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/** What {@code content/rooms/} holds, so a GameTest can check that the game loads every room as written. */\n"
        "public final class GeneratedRoomExpectations {\n"
        "    /**\n"
        "     * One room: its structure id ({@code realmforge:rooms/<path>}), its size, the jigsaw blocks it holds and the\n"
        "     * blocks that are air in the template.\n"
        "     */\n"
        "    public record RoomExpectation(String id, int sizeX, int sizeY, int sizeZ, int jigsaws, int air) {\n"
        "    }\n"
        "\n"
        f"    public static final List<RoomExpectation> ROOMS = {rooms_list};\n"
        "\n"
        "    private GeneratedRoomExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
