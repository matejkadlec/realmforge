"""Dungeon types: ``content/dungeons/<id>.yaml`` -> ``data/realmforge/realmforge/dungeon_type/<id>.json``, their
names for the lang files, the item tag ``realmforge:dungeon_key`` of their keys, and the QA mod's expectations.

A dungeon type names its room pool; the generated entry also carries the pool's rooms resolved, in room id order:
each room's template (the structure ``realmforge:rooms/<id>``), size, role, weight and connectors as the game reads
them (``rooms/connectors.py``). The game's planner lays out a dungeon from that alone (architecture 15.3), so
planning needs no template, and JUnit fuzzes the planner over the same files.

A room's ``entry`` side becomes the index of its connector there (RLM-362), and a room with mob spawners or chests
carries how many (``spawners``, ``chests``): a combat room with a spawner is a fight in a layout's pacing, and the
planner ends the main path and branches in the rooms with the most chests (RLM-364); a room with lava a player
reaches, TNT or a dispenser is a ``hazard``, which the planner keeps from the start, from right before a fight and from
other hazards (lava sealed away, as over the egg room's glass ceiling, hurts nobody: RLM-361). A
type with ``walls`` also gets its wall (RLM-364): ``rf gen`` builds a template of 3 x 3 blocks of the wall's block,
written as the structure ``realmforge:dungeon_walls/<id>``, with one connector at the middle of its bottom row whose
jigsaw turns into the same block, and lists it last among the rooms with the role ``wall``: the planner may close a
doorway with it instead of a dead end.

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
from realmforge_tools.project import DATA_VERSION, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.rooms.connectors import Connector, connectors
from realmforge_tools.rooms.dress import reach
from realmforge_tools.rooms.generator import DOOR, EMPTY_POOL, GENERATED_DIR, paths
from realmforge_tools.rooms.set_pieces import DENSITY, Fit, Pattern, RoomView, match
from realmforge_tools.rooms.structure import (
    HAZARDS,
    JIGSAW,
    LAVA,
    BlockState,
    Structure,
    StructureBlock,
    count_chests,
    count_spawners,
    write_structure,
)
from realmforge_tools.schema.common import LocalizedText
from realmforge_tools.schema.dungeon import (
    ROOM_ROLES,
    WALL_HEIGHT,
    WALL_ROLE,
    WALL_WIDTH,
    DungeonTypeDef,
    WallsDef,
    room_role,
)
from realmforge_tools.schema.room import ROOMS_DIR

QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
REGISTRY = "dungeon_type"
# The item tag of every dungeon type's key: the client reads it to know which items a dungeon portal takes, as the
# dungeon types themselves stay on the server (architecture 15.5).
KEY_TAG = f"{MOD_ID}:dungeon_key"
# The structure folder of the dungeon types' walls: realmforge:dungeon_walls/<dungeon type id> (RLM-364).
WALLS_DIR = "dungeon_walls"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    folder = data_dir(paths) / MOD_ID / REGISTRY
    files = {
        folder / f"{entry.model.id}.json": dump_json(dungeon_data(entry.model, content))
        for entry in content.dungeons
    }
    for entry in content.dungeons:
        walls = entry.model.layout.walls
        if walls is not None:
            files[data_dir(paths) / "structure" / WALLS_DIR / f"{entry.model.id}.nbt"] = write_structure(
                wall_structure(walls)
            )
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
    walls = dungeon.layout.walls
    if walls is not None:
        del data["layout"]["walls"]
        rooms.append(_wall(dungeon, walls))
    data["rooms"] = rooms
    data["set_piece_density"] = {role: list(DENSITY[role]) for role in ROOM_ROLES}
    pieces = {f"{MOD_ID}:{piece.id}": piece for piece in content.set_pieces}
    if used:
        data["set_pieces"] = {piece: _piece(pieces[piece]) for piece in sorted(used)}
    return data


def wall_structure(walls: WallsDef) -> Structure:
    """The wall of a dungeon type: ``WALL_WIDTH`` x ``WALL_HEIGHT`` blocks, one deep, with its connector facing north
    at the middle of its bottom row. Placed against a doorway, it fills the three by three blocks behind it; its jigsaw
    turns into the wall's block like the rest."""
    block = BlockState(walls.block, ())
    jigsaw = (WALL_WIDTH // 2, 0, 0)
    blocks = []
    for y in range(WALL_HEIGHT):
        for x in range(WALL_WIDTH):
            if (x, y, 0) == jigsaw:
                blocks.append(
                    StructureBlock(
                        jigsaw,
                        BlockState(JIGSAW, (("orientation", "north_up"),)),
                        {
                            "id": JIGSAW,
                            "final_state": walls.block,
                            "joint": "aligned",
                            "name": DOOR,
                            "pool": EMPTY_POOL,
                            "target": DOOR,
                        },
                    )
                )
            else:
                blocks.append(StructureBlock((x, y, 0), block, None))
    return Structure((WALL_WIDTH, WALL_HEIGHT, 1), DATA_VERSION, tuple(blocks))


def wall_template(dungeon: DungeonTypeDef) -> str:
    return f"{MOD_ID}:{WALLS_DIR}/{dungeon.id}"


def _wall(dungeon: DungeonTypeDef, walls: WallsDef) -> dict[str, Any]:
    return {
        "template": wall_template(dungeon),
        "size": [WALL_WIDTH, WALL_HEIGHT, 1],
        "role": WALL_ROLE,
        "weight": walls.weight,
        "connectors": [_connector(connector) for connector in connectors(wall_structure(walls))],
    }


def entry_index(room: RoomEntry) -> int | None:
    """The index of the room's connector on its ``entry`` side, or None without one; validation makes sure the side
    has exactly one."""
    if room.model.entry is None:
        return None
    found = [i for i, c in enumerate(room.derived("connectors", connectors)) if c.front == room.model.entry]
    return found[0] if len(found) == 1 else None


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
    entry = entry_index(room)
    if entry is not None:
        data["entry"] = entry
    if spawners(room):
        data["spawners"] = spawners(room)
    if chests(room):
        data["chests"] = chests(room)
    if hazard(room):
        data["hazard"] = True
    return data


def spawners(room: RoomEntry) -> int:
    """The mob spawners in the room's template."""
    return room.derived("spawners", count_spawners)


def chests(room: RoomEntry) -> int:
    """The chests, trapped ones included, in the room's template."""
    return room.derived("chests", count_chests)


def hazard(room: RoomEntry) -> bool:
    """Whether the room's template holds lava a player reaches, TNT or a dispenser: the planner spaces such rooms out
    (RLM-364)."""
    return room.derived("reachable_hazards", reachable_hazards) > 0


def reachable_hazards(structure: Structure) -> int:
    """The template's TNT and dispensers, and its lava that touches a cell a player reaches from the doorways
    (``rooms/dress.py``'s ``reach``): lava sealed away, over the egg room's glass ceiling (RLM-361), hurts nobody."""
    reached = reach(structure)
    count = 0
    for block in structure.blocks:
        if block.state.name not in HAZARDS:
            continue
        x, y, z = block.pos
        sides = ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1))
        if block.state.name != LAVA or any(side in reached for side in sides):
            count += 1
    return count


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
        expected = [_room_expectation(room) for room in pool_rooms(dungeon, content)]
        if layout.walls is not None:
            expected.append(_wall_expectation(dungeon, layout.walls))
        rooms = ",\n".join(expected)
        pacing = layout.pacing
        rhythm = (
            f"{pacing.corridors[0]}, {pacing.corridors[1]}, {pacing.fights[0]}, {pacing.fights[1]}, {pacing.repeat}"
            if pacing is not None
            else "0, 0, 0, 0, 0"
        )
        rows.append(
            "            new DungeonTypeExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{dungeon.id}')},\n"
            f"                    {string_literal(name_key)},\n"
            f"                    {string_literal(dungeon.pool)},\n"
            f"                    {layout.main_path[0]}, {layout.main_path[1]}, {layout.branches[0]}, "
            f"{layout.branches[1]}, {layout.branch_rooms[0]}, {layout.branch_rooms[1]},\n"
            f"                    {string_literal(layout.ending)}, {rhythm},\n"
            f"                    {string_literal(dungeon.key)}, {dungeon.open_minutes}, {dungeon.level},\n"
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
        "     * A room of the pool, or the type's wall: its structure id, size, role, weight, connectors, how often a layout\n"
        "     * may hold it (0: no limit), whether it stays at the start, the blocks an explosion may break in it, and the\n"
        "     * connector it is entered through (-1: any), its spawners and chests, and whether it is a hazard.\n"
        "     */\n"
        "    public record RoomExpectation(String template, int sizeX, int sizeY, int sizeZ, String role, int weight,\n"
        "            List<ConnectorExpectation> connectors, int maxPerLayout, boolean nearStart,\n"
        "            List<String> explosionBreaks, int entry, int spawners, int chests, boolean hazard) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * One dungeon type: its id, its name's translation key, its pool, its layout rules (a pacing of zeros: none),\n"
        "     * its key, how many minutes a portal to it stays open, its base level, and its rooms, its wall last.\n"
        "     */\n"
        "    public record DungeonTypeExpectation(String id, String name, String pool, int mainPathMin, int mainPathMax,\n"
        "            int branchesMin, int branchesMax, int branchRoomsMin, int branchRoomsMax, String ending,\n"
        "            int corridorsMin, int corridorsMax, int fightsMin, int fightsMax, int repeat, String key,\n"
        "            int openMinutes, int level, List<RoomExpectation> rooms) {\n"
        "    }\n"
        "\n"
        f"    public static final List<DungeonTypeExpectation> DUNGEON_TYPES = {entries};\n"
        "\n"
        "    private GeneratedDungeonTypeExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _connector_expectations(found: list[Connector]) -> str:
    rows = [
        f"new ConnectorExpectation({c.pos[0]}, {c.pos[1]}, {c.pos[2]}, {string_literal(c.front)}, "
        f"{string_literal(c.top)}, {string_literal(c.name)}, {string_literal(c.target)}, {string_literal(c.joint)})"
        for c in found
    ]
    return "List.of(\n" + ",\n".join(f"                                    {c}" for c in rows) + ")"


def _room_expectation(room: RoomEntry) -> str:
    x, y, z = room.size
    connector_list = _connector_expectations(room.derived("connectors", connectors))
    breaks = ", ".join(string_literal(block) for block in room.model.explosion_breaks)
    entry = entry_index(room)
    return (
        f"                            new RoomExpectation({string_literal(f'{MOD_ID}:{ROOMS_DIR}/{room.id}')}, "
        f"{x}, {y}, {z}, {string_literal(room_role(room.model.tags) or '')}, {room.model.weight},\n"
        f"                                    {connector_list},\n"
        f"                                    {room.model.max_per_layout or 0}, "
        f"{'true' if room.model.near_start else 'false'}, List.of({breaks}), {-1 if entry is None else entry}, "
        f"{spawners(room)}, {chests(room)}, {'true' if hazard(room) else 'false'})"
    )


def _wall_expectation(dungeon: DungeonTypeDef, walls: WallsDef) -> str:
    connector_list = _connector_expectations(connectors(wall_structure(walls)))
    return (
        f"                            new RoomExpectation({string_literal(wall_template(dungeon))}, "
        f"{WALL_WIDTH}, {WALL_HEIGHT}, 1, {string_literal(WALL_ROLE)}, {walls.weight},\n"
        f"                                    {connector_list},\n"
        "                                    0, false, List.of(), -1, 0, 0, false)"
    )
