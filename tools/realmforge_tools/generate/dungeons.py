"""Dungeon types: ``content/dungeons/<id>.yaml`` -> ``data/realmforge/realmforge/dungeon_type/<id>.json``, their
names for the lang files, the item tag ``realmforge:dungeon_key`` of their keys, and the QA mod's expectations.

A dungeon type names its room pool; the generated entry also carries the pool's rooms resolved, in room id order:
each room's template (the structure ``realmforge:rooms/<id>``), size, role, weight and connectors as the game reads
them (``rooms/connectors.py``). The game's planner lays out a dungeon from that alone (architecture 15.3), so
planning needs no template, and JUnit fuzzes the planner over the same files.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from realmforge_tools.content import Content, RoomEntry
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir, entry_data
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.project import MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.rooms.connectors import Connector, connectors
from realmforge_tools.schema.common import LocalizedText
from realmforge_tools.schema.dungeon import DungeonTypeDef, room_role
from realmforge_tools.schema.room import ROOMS_DIR

QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
REGISTRY = "dungeon_type"
# The item tag of every dungeon type's key: the client reads it to know which items a dungeon portal takes, as the
# dungeon types themselves stay on the server (architecture 15.5).
KEY_TAG = f"{MOD_ID}:dungeon_key"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    folder = data_dir(paths) / MOD_ID / REGISTRY
    files = {
        folder / f"{entry.model.id}.json": dump_json(dungeon_data(entry.model, content))
        for entry in content.dungeons
    }
    keys = sorted({entry.model.key for entry in content.dungeons})
    if keys:
        tag = KEY_TAG.split(":", 1)[1]
        files[data_dir(paths) / "tags" / "item" / f"{tag}.json"] = dump_json({"values": keys})
    expectations = java_file_path(
        paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedDungeonTypeExpectations"
    )
    files[expectations] = _expectations(content)
    return files


def translations(content: Content) -> dict[str, LocalizedText]:
    """The name of every dungeon type, by translation key."""
    texts: dict[str, LocalizedText] = {}
    for entry in content.dungeons:
        texts.update(entry_data(entry.model, REGISTRY)[1])
    return texts


def pool_rooms(dungeon: DungeonTypeDef, content: Content) -> list[RoomEntry]:
    """The rooms of the dungeon type's pool, in room id order."""
    return [room for room in content.rooms if dungeon.pool in room.model.pools]


def dungeon_data(dungeon: DungeonTypeDef, content: Content) -> dict[str, Any]:
    data, _ = entry_data(dungeon, REGISTRY)
    data["rooms"] = [_room(room) for room in pool_rooms(dungeon, content)]
    return data


def _room(room: RoomEntry) -> dict[str, Any]:
    return {
        "template": f"{MOD_ID}:{ROOMS_DIR}/{room.id}",
        "size": list(room.size),
        "role": room_role(room.model.tags),
        "weight": room.model.weight,
        "connectors": [_connector(connector) for connector in room.derived("connectors", connectors)],
    }


def _connector(connector: Connector) -> dict[str, Any]:
    return {
        "pos": list(connector.pos),
        "front": connector.front,
        "top": connector.top,
        "name": connector.name,
        "target": connector.target,
        "joint": connector.joint,
    }


def _expectations(content: Content) -> bytes:
    rows = []
    for entry in content.dungeons:
        dungeon = entry.model
        layout = dungeon.layout
        name_key = entry_data(dungeon, REGISTRY)[0]["name"]
        rooms = ",\n".join(_room_expectation(room) for room in pool_rooms(dungeon, content))
        rows.append(
            "            new DungeonTypeExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{dungeon.id}')},\n"
            f"                    {string_literal(name_key)},\n"
            f"                    {string_literal(dungeon.pool)},\n"
            f"                    {layout.main_path[0]}, {layout.main_path[1]}, {layout.branches[0]}, "
            f"{layout.branches[1]}, {layout.branch_rooms[0]}, {layout.branch_rooms[1]},\n"
            f"                    {string_literal(layout.ending)},\n"
            f"                    {string_literal(dungeon.key)}, {dungeon.open_minutes},\n"
            f"                    List.of(\n{rooms}))"
        )
    entries = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
    source = (
        header(Path(__file__), "content/dungeons/, content/rooms/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/**\n"
        " * What {@code content/dungeons/} says about every dungeon type, with its pool's rooms resolved, so GameTests can\n"
        " * check that the game agrees.\n"
        " */\n"
        "public final class GeneratedDungeonTypeExpectations {\n"
        "    /** A connector as written: its position in the template, front, top, jigsaw names and joint. */\n"
        "    public record ConnectorExpectation(int x, int y, int z, String front, String top, String name,\n"
        "            String target, String joint) {\n"
        "    }\n"
        "\n"
        "    /** A room of the pool: its structure id, size, role, weight and connectors. */\n"
        "    public record RoomExpectation(String template, int sizeX, int sizeY, int sizeZ, String role, int weight,\n"
        "            List<ConnectorExpectation> connectors) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * One dungeon type: its id, its name's translation key, its pool, its layout rules, its key, how many minutes\n"
        "     * a portal to it stays open, and its rooms.\n"
        "     */\n"
        "    public record DungeonTypeExpectation(String id, String name, String pool, int mainPathMin, int mainPathMax,\n"
        "            int branchesMin, int branchesMax, int branchRoomsMin, int branchRoomsMax, String ending, String key,\n"
        "            int openMinutes, List<RoomExpectation> rooms) {\n"
        "    }\n"
        "\n"
        f"    public static final List<DungeonTypeExpectation> DUNGEON_TYPES = {entries};\n"
        "\n"
        "    private GeneratedDungeonTypeExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _room_expectation(room: RoomEntry) -> str:
    x, y, z = room.size
    found = [
        f"new ConnectorExpectation({c.pos[0]}, {c.pos[1]}, {c.pos[2]}, {string_literal(c.front)}, "
        f"{string_literal(c.top)}, {string_literal(c.name)}, {string_literal(c.target)}, {string_literal(c.joint)})"
        for c in room.derived("connectors", connectors)
    ]
    connector_list = (
        "List.of(\n" + ",\n".join(f"                                    {c}" for c in found) + ")"
    )
    return (
        f"                            new RoomExpectation({string_literal(f'{MOD_ID}:{ROOMS_DIR}/{room.id}')}, "
        f"{x}, {y}, {z}, {string_literal(room_role(room.model.tags) or '')}, {room.model.weight},\n"
        f"                                    {connector_list})"
    )
