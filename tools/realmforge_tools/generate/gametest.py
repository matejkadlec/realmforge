"""Structure templates for GameTests in the QA mod (``data/realmforge_qa/structure/*.nbt``).

Every GameTest needs a structure template; tests then place whatever blocks they need themselves. The spike rooms
(``structure/spike/*.nbt``) are test fixtures for instanced dungeons (spike 3): small stone rooms with lanterns, a
chest and openings that line up when placed in a row along x, and a data marker of each kind but the chest's (W4b:
the entrance where players arrive, a mob spawn in the corridor, the exit in the hall), so the builder's markers are
tested on rooms whose blocks are known.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

from realmforge_tools.content import Content
from realmforge_tools.formats import nbt
from realmforge_tools.formats.deflate import gzip_stored
from realmforge_tools.project import DATA_VERSION, QA_MOD_ID, RepoPaths

# name -> (size x, size y, size z). Each template is a smooth stone floor (y = 0) with air above.
PLATFORMS: dict[str, tuple[int, int, int]] = {
    "platform_5x5": (5, 5, 5),
    # Room for a realm portal's frame, 5 wide and 6 tall, with space in front of it and behind it (W2b).
    "platform_9x9": (9, 8, 9),
    # Room for a mob to fight at range and to back away from its target (W3's caster).
    "platform_17x17": (17, 6, 17),
}

AIR = ("minecraft:air", ())
FLOOR = ("minecraft:polished_andesite", ())
WALL = ("minecraft:stone_bricks", ())
MOSSY_WALL = ("minecraft:mossy_stone_bricks", ())
MARKER = ("minecraft:chiseled_stone_bricks", ())
LANTERN = ("minecraft:lantern", (("hanging", "true"), ("waterlogged", "false")))
CHEST = ("minecraft:chest", (("facing", "west"), ("type", "single"), ("waterlogged", "false")))
DATA_MARKER = ("minecraft:structure_block", (("mode", "data"),))

# A block state: name and sorted (property, value) pairs, as in a structure palette.
State = tuple[str, tuple[tuple[str, str], ...]]


@dataclass(frozen=True)
class Room:
    """A box of stone with an air interior. ``openings`` are (wall, centre along it) on the walls x = 0 ("west")
    and x = max ("east"), each three blocks wide and three high above the floor. A side without walls ("open_x")
    leaves both x ends open, like a corridor."""

    size: tuple[int, int, int]
    openings: tuple[tuple[str, int], ...] = ()
    open_x: bool = False
    lanterns: tuple[tuple[int, int], ...] = ()
    marker: tuple[int, int] | None = None
    chest: tuple[int, int] | None = None
    pillars: tuple[tuple[int, int], ...] = ()
    # Data markers (rooms/check.py MARKERS): name and position, as structure blocks in data mode.
    data_markers: tuple[tuple[str, tuple[int, int, int]], ...] = ()


# The three spike rooms. Placed in a row along x (start, corridor, hall) with the corridor 4 and the hall -8 blocks
# off on z, their openings meet at z = 6..8 relative to the start room.
SPIKE_ROOMS: dict[str, Room] = {
    "start": Room(
        size=(15, 7, 15),
        openings=(("east", 7),),
        lanterns=((4, 4), (10, 4), (4, 10), (10, 10)),
        marker=(7, 7),
        data_markers=(("entrance", (7, 1, 7)),),
    ),
    "corridor": Room(
        size=(25, 6, 7), open_x=True, lanterns=((6, 3), (18, 3)), data_markers=(("mob", (12, 1, 3)),)
    ),
    "hall": Room(
        size=(31, 13, 31),
        openings=(("west", 15), ("east", 15)),
        lanterns=((8, 8), (22, 8), (8, 22), (22, 22), (15, 15)),
        chest=(27, 15),
        pillars=((8, 8), (22, 8), (8, 22), (22, 22)),
        data_markers=(("exit", (15, 1, 27)),),
    ),
}


def platform(size_x: int, size_y: int, size_z: int) -> bytes:
    """Gzipped structure NBT: a ``size_x`` x ``size_z`` floor of smooth stone, empty space above it."""
    floor = tuple(
        {"pos": nbt.int_list(x, 0, z), "state": nbt.Int(0)} for x in range(size_x) for z in range(size_z)
    )
    air = tuple(
        {"pos": nbt.int_list(x, y, z), "state": nbt.Int(1)}
        for x in range(size_x)
        for y in range(1, size_y)
        for z in range(size_z)
    )
    root = {
        "DataVersion": nbt.Int(DATA_VERSION),
        "size": nbt.int_list(size_x, size_y, size_z),
        "palette": nbt.NbtList(
            nbt.TAG_COMPOUND, ({"Name": "minecraft:smooth_stone"}, {"Name": "minecraft:air"})
        ),
        "blocks": nbt.NbtList(nbt.TAG_COMPOUND, floor + air),
        "entities": nbt.NbtList(nbt.TAG_COMPOUND, ()),
    }
    return gzip_stored(nbt.encode(root))


def room_blocks(room: Room) -> dict[tuple[int, int, int], State]:
    """Every position of the room's box and its block, air included (as a structure block saves it)."""
    size_x, size_y, size_z = room.size
    blocks: dict[tuple[int, int, int], State] = {}
    for y in range(size_y):
        for z in range(size_z):
            for x in range(size_x):
                wall_x = x in (0, size_x - 1) and not room.open_x
                wall = wall_x or z in (0, size_z - 1)
                if y == 0:
                    blocks[x, y, z] = FLOOR
                elif y == size_y - 1:
                    blocks[x, y, z] = WALL
                elif wall:
                    blocks[x, y, z] = MOSSY_WALL if (x + y + z) % 5 == 0 else WALL
                else:
                    blocks[x, y, z] = AIR
    for side, centre in room.openings:
        x = 0 if side == "west" else size_x - 1
        for z in range(centre - 1, centre + 2):
            for y in range(1, 4):
                blocks[x, y, z] = AIR
    for x, z in room.pillars:
        for y in range(1, size_y - 1):
            blocks[x, y, z] = WALL
    for x, z in room.lanterns:
        blocks[x, size_y - 2, z] = LANTERN
    if room.marker is not None:
        blocks[room.marker[0], 0, room.marker[1]] = MARKER
    if room.chest is not None:
        blocks[room.chest[0], 1, room.chest[1]] = CHEST
    for _, pos in room.data_markers:
        blocks[pos] = DATA_MARKER
    return blocks


def room_structure(room: Room) -> bytes:
    """Gzipped structure NBT of a room. Like vanilla, full blocks come first, then the rest, then block entities;
    the chest holds one bread, so clearing it must not drop items, and a data marker's structure block names its
    marker in its metadata."""
    blocks = room_blocks(room)
    marker_names = {pos: name for name, pos in room.data_markers}
    palette: list[State] = []
    for state in blocks.values():
        if state not in palette:
            palette.append(state)

    def entry(pos: tuple[int, int, int], state: State) -> dict[str, Any]:
        result: dict[str, Any] = {"pos": nbt.int_list(*pos), "state": nbt.Int(palette.index(state))}
        if state == CHEST:
            bread = {"Slot": nbt.Byte(0), "id": "minecraft:bread", "count": nbt.Int(1)}
            result["nbt"] = {"id": "minecraft:chest", "Items": nbt.NbtList(nbt.TAG_COMPOUND, (bread,))}
        if state == DATA_MARKER:
            result["nbt"] = {"id": "minecraft:structure_block", "mode": "DATA", "metadata": marker_names[pos]}
        return result

    def order(item: tuple[tuple[int, int, int], State]) -> int:
        state = item[1]
        return 2 if state in (CHEST, DATA_MARKER) else 1 if state in (AIR, LANTERN) else 0

    entries = tuple(entry(pos, state) for pos, state in sorted(blocks.items(), key=order))
    root = {
        "DataVersion": nbt.Int(DATA_VERSION),
        "size": nbt.int_list(*room.size),
        "palette": nbt.NbtList(nbt.TAG_COMPOUND, tuple(_palette_entry(state) for state in palette)),
        "blocks": nbt.NbtList(nbt.TAG_COMPOUND, entries),
        "entities": nbt.NbtList(nbt.TAG_COMPOUND, ()),
    }
    return gzip_stored(nbt.encode(root))


def _palette_entry(state: State) -> dict[str, Any]:
    name, properties = state
    if not properties:
        return {"Name": name}
    return {"Name": name, "Properties": dict(properties)}


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    structure_dir = paths.qa_generated / "resources" / "data" / QA_MOD_ID / "structure"
    files = {structure_dir / f"{name}.nbt": platform(*size) for name, size in PLATFORMS.items()}
    files.update(
        {structure_dir / "spike" / f"{name}.nbt": room_structure(room) for name, room in SPIKE_ROOMS.items()}
    )
    return files
