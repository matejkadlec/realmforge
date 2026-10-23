"""Dungeon types: ``content/dungeons/<id>.yaml`` -> ``data/realmforge/realmforge/dungeon_type/<id>.json``, their
names for the lang files, the item tag ``realmforge:dungeon_key`` of their keys, and the QA mod's expectations.

A dungeon type names its room pool; the generated entry also carries the pool's rooms resolved, in room id order:
each room's template (the structure ``realmforge:rooms/<id>``), size, role, weight and connectors as the game reads
them (``rooms/connectors.py``). The game's planner lays out a dungeon from that alone (architecture 15.3), so
planning needs no template, and JUnit fuzzes the planner over the same files.

Each room also gets its set-piece spots (``rooms/set_pieces.py``, ADR 0050): the patterns of spots the pieces that
reach its role fit, each with the pieces that may fill it. The entry lists those pieces (template, processor list,
weight, tags, whether they may be mirrored) and the patterns a room of each role fills by default
(``set_piece_density``), so the builder picks pieces without reading their fit rules (RLM-282).
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

from realmforge_tools.content import Content, RoomEntry, SetPieceEntry
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir, entry_data
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.generate.set_pieces import processor_list_id, template_id
from realmforge_tools.project import MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.rooms.connectors import Connector, connectors
from realmforge_tools.rooms.generator import GENERATED_DIR, paths
from realmforge_tools.rooms.set_pieces import DENSITY, Fit, Pattern, RoomView, match
from realmforge_tools.schema.common import LocalizedText
from realmforge_tools.schema.dungeon import ROOM_ROLES, DungeonTypeDef, room_role
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
    generated = generated_rooms(content)
    rooms, used = [], set()
    for room in pool_rooms(dungeon, content):
        entry = _room(room)
        spots = room_spots(room, content.set_pieces, *generated.get(room.id, (None, frozenset())))
        if spots:
            entry["set_piece_spots"] = spots
            used |= {piece for pattern in spots for piece in pattern["pieces"]}
        rooms.append(entry)
    data["rooms"] = rooms
    data["set_piece_density"] = {role: list(DENSITY[role]) for role in ROOM_ROLES}
    pieces = {f"{MOD_ID}:{piece.id}": piece for piece in content.set_pieces}
    if used:
        data["set_pieces"] = {piece: _piece(pieces[piece]) for piece in sorted(used)}
    return data


def generated_rooms(content: Content) -> dict[str, tuple[dict[str, str] | None, frozenset[tuple[int, int]]]]:
    """What the set-piece matcher knows of a generated room, by room id: its theme's block of every role (the blocks
    it reads as walls) and its paths (which pieces keep off, ``rooms/generator.py``)."""
    themes = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.room_themes}
    specs = {entry.model.id: entry.model for entry in content.room_specs}
    found = {}
    for room in content.rooms:
        if room.id.startswith(f"{GENERATED_DIR}/"):
            spec = specs[room.id.split("/")[1]]
            theme = themes.get(spec.theme)
            found[room.id] = (
                theme.blocks.model_dump() if theme is not None else None,
                frozenset(paths(spec)),
            )
    return found


def room_fits(role: str | None, pieces: tuple[SetPieceEntry, ...]) -> list[tuple[Fit, list[SetPieceEntry]]]:
    """The fits of the pieces that reach a room of ``role`` (any piece for None), each with its pieces, by key."""
    groups: dict[str, tuple[Fit, list[SetPieceEntry]]] = {}
    for piece in pieces:
        if role is None or role in piece.model.roles:
            fit = piece.model.fit()
            groups.setdefault(fit.key, (fit, []))[1].append(piece)
    return [groups[key] for key in sorted(groups)]


def room_patterns(
    room: RoomEntry,
    fits: list[tuple[Fit, list[SetPieceEntry]]],
    theme_blocks: dict[str, str] | None,
    room_paths: frozenset[tuple[int, int]] = frozenset(),
) -> list[Pattern]:
    """The room's patterns for ``fits``, cached with the room's template under a kind that names everything else
    they depend on (the fits, which of them may be mirrored, the room's theme and paths)."""
    if not fits:
        return []
    mirrorable = [any(piece.model.variants.mirror for piece in group) for _, group in fits]
    source = [[fit.key for fit, _ in fits], mirrorable, theme_blocks, sorted(room_paths)]
    kind = f"set_pieces_{hashlib.sha256(json.dumps(source, sort_keys=True).encode()).hexdigest()[:16]}"
    only_fits = [fit for fit, _ in fits]
    return room.derived(
        kind,
        lambda structure: match(
            RoomView(structure, theme_blocks, room_paths), only_fits, mirrorable=mirrorable
        ),
    )


def room_spots(
    room: RoomEntry,
    pieces: tuple[SetPieceEntry, ...],
    theme_blocks: dict[str, str] | None,
    room_paths: frozenset[tuple[int, int]] = frozenset(),
) -> list[dict[str, Any]]:
    """The room's set-piece patterns as the dungeon type entry carries them: each with the pieces that may fill it
    and its spots."""
    fits = room_fits(room_role(room.model.tags), pieces)
    found = []
    for pattern in room_patterns(room, fits, theme_blocks, room_paths):
        candidates = pattern_pieces(fits, pattern)
        if not candidates:
            continue
        found.append(
            {
                "pieces": [f"{MOD_ID}:{piece.id}" for piece in candidates],
                "spots": [
                    {"pos": list(spot.pos), "facing": spot.facing, "mirror": spot.mirror}
                    for spot in pattern.spots
                ],
            }
        )
    return found


def pattern_pieces(fits: list[tuple[Fit, list[SetPieceEntry]]], pattern: Pattern) -> list[SetPieceEntry]:
    """The pieces that may fill a pattern: those of its fit, but a mirrored spot of a mask that mirroring changes
    takes only pieces that may be mirrored."""
    fit, group = fits[pattern.fit]
    mirrored = any(spot.mirror for spot in pattern.spots) and not fit.mask.mirror_symmetric
    return [piece for piece in group if piece.model.variants.mirror or not mirrored]


def _piece(piece: SetPieceEntry) -> dict[str, Any]:
    data: dict[str, Any] = {"template": template_id(piece)}
    processors = processor_list_id(piece)
    if processors is not None:
        data["processors"] = processors
    data["weight"] = piece.model.weight
    data["tags"] = list(piece.model.tags)
    data["mirror"] = piece.model.variants.mirror
    return data


def _room(room: RoomEntry) -> dict[str, Any]:
    data: dict[str, Any] = {
        "template": f"{MOD_ID}:{ROOMS_DIR}/{room.id}",
        "size": list(room.size),
        "role": room_role(room.model.tags),
        "weight": room.model.weight,
        "connectors": [_connector(connector) for connector in room.derived("connectors", connectors)],
    }
    if room.model.max_per_layout is not None:
        data["max_per_layout"] = room.model.max_per_layout
    if room.model.near_start:
        data["near_start"] = True
    if room.model.explosion_breaks:
        data["explosion_breaks"] = list(room.model.explosion_breaks)
    return data


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
        "    /**\n"
        "     * A room of the pool: its structure id, size, role, weight, connectors, how often a layout may hold it (0:\n"
        "     * no limit), whether it stays at the start, and the blocks an explosion may break in it.\n"
        "     */\n"
        "    public record RoomExpectation(String template, int sizeX, int sizeY, int sizeZ, String role, int weight,\n"
        "            List<ConnectorExpectation> connectors, int maxPerLayout, boolean nearStart,\n"
        "            List<String> explosionBreaks) {\n"
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
    breaks = ", ".join(string_literal(block) for block in room.model.explosion_breaks)
    return (
        f"                            new RoomExpectation({string_literal(f'{MOD_ID}:{ROOMS_DIR}/{room.id}')}, "
        f"{x}, {y}, {z}, {string_literal(room_role(room.model.tags) or '')}, {room.model.weight},\n"
        f"                                    {connector_list},\n"
        f"                                    {room.model.max_per_layout or 0}, "
        f"{'true' if room.model.near_start else 'false'}, List.of({breaks}))"
    )
