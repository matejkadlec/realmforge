"""content/dungeons/: the datapack registry realmforge:dungeon_type, room pools and their validation (W4a, RLM-178;
architecture 15.2-15.4)."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import DATA_VERSION, REPO_ROOT, RepoPaths
from realmforge_tools.rooms.connectors import Connector, connectors, could_attach, doorway_gap
from realmforge_tools.rooms.structure import (
    BlockState,
    Structure,
    StructureBlock,
    read_structure,
    write_structure,
)
from realmforge_tools.validate import load_and_validate

from .conftest import write

DATA = "mod/src/generated/resources/data/realmforge/realmforge/dungeon_type"
EXPECTATIONS = "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedDungeonTypeExpectations.java"
POOL = "realmforge:crypt"
LAYOUT = "layout:\n  main_path: [2, 4]\n  branches: [0, 1]\n  branch_rooms: [1, 2]\n  ending: boss\n"


def dungeon(
    entry_id: str = "crypt", pool: str = POOL, layout: str = LAYOUT, key: str = "minecraft:tripwire_hook"
) -> str:
    return f"id: {entry_id}\nname:\n  en_us: Crypt\npool: {pool}\n{layout}key: {key}\nopen_minutes: 20\n"


def room_structure(
    size: tuple[int, int, int], *faces: str, name: str = "realmforge:door", target: str = "realmforge:door"
) -> Structure:
    """An empty room with one connector in the middle of each face, named ``name`` and targeting ``target``."""
    x, y, z = size
    spots = {
        "north": ((x // 2, 1, 0), "north_up"),
        "south": ((x // 2, 1, z - 1), "south_up"),
        "west": ((0, 1, z // 2), "west_up"),
        "east": ((x - 1, 1, z // 2), "east_up"),
        "up": ((x // 2, y - 1, z // 2), "up_north"),
        "down": ((x // 2, 0, z // 2), "down_north"),
    }
    blocks = tuple(
        StructureBlock(
            pos,
            BlockState.parse(f"minecraft:jigsaw[orientation={orientation}]"),
            {"id": "minecraft:jigsaw", "name": name, "target": target, "final_state": "minecraft:air"},
        )
        for pos, orientation in (spots[face] for face in faces)
    )
    return Structure(size, DATA_VERSION, blocks)


def add_room(
    repo: RepoPaths,
    room_id: str,
    tags: list[str],
    *faces: str,
    pools: tuple[str, ...] = (POOL,),
    **kwargs: str,
) -> None:
    path = repo.content / "rooms" / f"{room_id}.nbt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(write_structure(room_structure((7, 5, 7), *faces, **kwargs)))
    pool_list = "".join(f"\n  - {pool}" for pool in pools) or " []"
    write(path.with_suffix(".yaml"), f"pools:{pool_list}\ntags: [{', '.join(tags)}]\nweight: 2\n")


def crypt(repo: RepoPaths, *skip: str) -> None:
    """A pool with every kind of room a layout needs, but the ones named in ``skip``."""
    rooms = {
        "entrance": (["entrance", "dead_end"], ("south",)),
        "hall": (["hallway", "straight"], ("north", "south")),
        "crossing": (["combat", "crossing"], ("north", "south", "east", "west")),
        "lair": (["boss", "dead_end"], ("north",)),
        "nook": (["treasure", "dead_end"], ("north",)),
    }
    for name, (tags, faces) in rooms.items():
        if name not in skip:
            add_room(repo, f"crypt/{name}", tags, *faces)
    write(repo.content / "dungeons" / "crypt.yaml", dungeon())


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def test_dungeon_type_carries_its_pools_rooms(repo: RepoPaths) -> None:
    crypt(repo)
    add_room(repo, "elsewhere/hall", ["hallway"], "north", "south", pools=("realmforge:other",))
    files = generate(repo)
    data = json.loads(files[f"{DATA}/crypt.json"])
    assert data["name"] == "dungeon_type.realmforge.crypt.name"
    assert data["pool"] == POOL
    assert data["layout"] == {
        "main_path": [2, 4],
        "branches": [0, 1],
        "branch_rooms": [1, 2],
        "ending": "boss",
    }
    assert [room["template"] for room in data["rooms"]] == [
        "realmforge:rooms/crypt/crossing",
        "realmforge:rooms/crypt/entrance",
        "realmforge:rooms/crypt/hall",
        "realmforge:rooms/crypt/lair",
        "realmforge:rooms/crypt/nook",
    ], "the pool's rooms in room id order, and no others"
    hall = data["rooms"][2]
    assert (hall["size"], hall["role"], hall["weight"]) == ([7, 5, 7], "hallway", 2)
    assert hall["connectors"] == [
        {
            "pos": [3, 1, 0],
            "front": "north",
            "top": "up",
            "name": "realmforge:door",
            "target": "realmforge:door",
            "joint": "aligned",
        },
        {
            "pos": [3, 1, 6],
            "front": "south",
            "top": "up",
            "name": "realmforge:door",
            "target": "realmforge:door",
            "joint": "aligned",
        },
    ]
    lang = json.loads(files["mod/src/generated/resources/assets/realmforge/lang/en_us.json"])
    assert lang["dungeon_type.realmforge.crypt.name"] == "Crypt"
    java = files[EXPECTATIONS].decode()
    assert '"realmforge:crypt"' in java and '"dungeon_type.realmforge.crypt.name"' in java
    assert 'new RoomExpectation("realmforge:rooms/crypt/hall", 7, 5, 7, "hallway", 2,' in java
    assert (
        'new ConnectorExpectation(3, 1, 0, "north", "up", "realmforge:door", "realmforge:door", "aligned")'
        in java
    )
    assert "tools/schemas/dungeons.schema.json" in files
    assert (data["key"], data["open_minutes"]) == ("minecraft:tripwire_hook", 20)
    assert '"boss", 0, 0, 0, 0, 0,\n                    "minecraft:tripwire_hook", 20,' in java, (
        "no pacing: zeros"
    )
    assert "spawners" not in hall and "chests" not in hall, (
        "a room without spawners or chests leaves them out"
    )
    tag = json.loads(files["mod/src/generated/resources/data/realmforge/tags/item/dungeon_key.json"])
    assert tag == {"values": ["minecraft:tripwire_hook"]}, "the client reads the keys from this tag"


def test_a_key_is_an_item_that_opens_one_dungeon_type(repo: RepoPaths) -> None:
    crypt(repo)
    write(repo.content / "dungeons" / "vault.yaml", dungeon("vault"))
    write(repo.content / "dungeons" / "tomb.yaml", dungeon("tomb", key="minecraft:no_such_key"))
    problems = problems_for(repo)
    assert any(
        "vault.yaml" in p and "key" in p and "minecraft:tripwire_hook already opens" in p for p in problems
    ), problems
    assert any(
        "tomb.yaml" in p and "key" in p and "unknown id 'minecraft:no_such_key'" in p for p in problems
    )


def test_a_rooms_entry_side_reaches_the_game(repo: RepoPaths) -> None:
    # RLM-362: the sidecar names the side, the dungeon type carries the index of the connector there.
    crypt(repo)
    hall = repo.content / "rooms" / "crypt" / "hall.yaml"
    hall.write_text(hall.read_text(encoding="utf-8") + "entry: south\n", encoding="utf-8")
    files = generate(repo)
    rooms = {room["template"]: room for room in json.loads(files[f"{DATA}/crypt.json"])["rooms"]}
    assert rooms["realmforge:rooms/crypt/hall"]["entry"] == 1, "north is connector 0, south connector 1"
    assert "entry" not in rooms["realmforge:rooms/crypt/crossing"]
    java = files[EXPECTATIONS].decode()
    assert "0, false, List.of(), 1, 0, 0, false)" in java, "the hall's expectation names its entry"

    hall.write_text(hall.read_text(encoding="utf-8").replace("entry: south", "entry: east"), encoding="utf-8")
    entrance = repo.content / "rooms" / "crypt" / "entrance.yaml"
    entrance.write_text(entrance.read_text(encoding="utf-8") + "entry: south\n", encoding="utf-8")
    problems = problems_for(repo)
    assert any(
        "hall.yaml" in p and "entry" in p and "the entry side east holds 0 connectors" in p for p in problems
    ), problems
    assert any("entrance.yaml" in p and "an entrance is where players arrive" in p for p in problems), (
        problems
    )


def walled(size: tuple[int, int, int], *faces: str, wide: bool = False) -> Structure:
    """``room_structure`` with stone brick walls, floor and ceiling, each connector in a doorway three blocks wide and
    high at its foot; ``wide``: the first doorway is a block wider on one side."""
    room = room_structure(size, *faces)
    x, y, z = size
    doorway: set[tuple[int, int, int]] = set()
    for index, block in enumerate(room.blocks):
        bx, by, bz = block.pos
        across = (1, 0, 0) if bz in (0, z - 1) else (0, 0, 1)
        sides = range(-1, 3 if wide and index == 0 else 2)
        doorway |= {(bx + d * across[0], by + up, bz + d * across[2]) for d in sides for up in range(3)}
    jigsaws = {block.pos for block in room.blocks}
    bricks = tuple(
        StructureBlock(
            (cx, cy, cz), BlockState("minecraft:air" if (cx, cy, cz) in doorway else "minecraft:stone_bricks")
        )
        for cx in range(x)
        for cy in range(y)
        for cz in range(z)
        if (cx in (0, x - 1) or cy in (0, y - 1) or cz in (0, z - 1)) and (cx, cy, cz) not in jigsaws
    )
    return Structure(size, DATA_VERSION, room.blocks + bricks)


def test_walls_close_the_doorways_nothing_else_takes(repo: RepoPaths) -> None:
    # RLM-364: rf gen builds a type's wall and lists it last among its rooms; validation checks that it closes every
    # doorway of the pool.
    crypt(repo)
    for name, faces in {"entrance": ("south",), "hall": ("north", "south"), "lair": ("north",)}.items():
        path = repo.content / "rooms" / "crypt" / f"{name}.nbt"
        path.write_bytes(write_structure(walled((7, 5, 7), *faces)))
    for name in ("crossing", "nook"):
        (repo.content / "rooms" / "crypt" / f"{name}.nbt").unlink()
        (repo.content / "rooms" / "crypt" / f"{name}.yaml").unlink()
    path = repo.content / "dungeons" / "crypt.yaml"
    path.write_text(
        dungeon(layout=LAYOUT + "  walls:\n    block: minecraft:stone_bricks\n    weight: 3\n"),
        encoding="utf-8",
    )
    add_room(repo, "crypt/nook", ["hallway", "dead_end"], "north")
    (repo.content / "rooms" / "crypt" / "nook.nbt").write_bytes(write_structure(walled((7, 5, 7), "north")))

    files = generate(repo)
    data = json.loads(files[f"{DATA}/crypt.json"])
    assert "walls" not in data["layout"], "the wall is a room of the entry, not a layout rule"
    wall = data["rooms"][-1]
    assert wall == {
        "template": "realmforge:dungeon_walls/crypt",
        "size": [3, 3, 1],
        "role": "wall",
        "weight": 3,
        "connectors": [
            {
                "pos": [1, 0, 0],
                "front": "north",
                "top": "up",
                "name": "realmforge:door",
                "target": "realmforge:door",
                "joint": "aligned",
            }
        ],
    }
    template = read_structure(
        files["mod/src/generated/resources/data/realmforge/structure/dungeon_walls/crypt.nbt"]
    )
    assert template.size == (3, 3, 1) and template.data_version == DATA_VERSION
    assert sorted(block.state.name for block in template.blocks) == ["minecraft:jigsaw"] + 8 * [
        "minecraft:stone_bricks"
    ]
    (jigsaw,) = [block for block in template.blocks if block.state.name == "minecraft:jigsaw"]
    assert jigsaw.nbt is not None and jigsaw.nbt["final_state"] == "minecraft:stone_bricks", "no hole is left"
    assert [connector.front for connector in connectors(template)] == ["north"]
    java = files[EXPECTATIONS].decode()
    assert 'new RoomExpectation("realmforge:dungeon_walls/crypt", 3, 3, 1, "wall", 3,' in java

    (repo.content / "rooms" / "crypt" / "hall.nbt").write_bytes(
        write_structure(walled((7, 5, 7), "north", "south", wide=True))
    )
    path.write_text(
        path.read_text().replace("minecraft:stone_bricks", "minecraft:no_such_block"), encoding="utf-8"
    )
    problems = problems_for(repo)
    assert any("crypt.yaml" in p and "layout.walls.block: unknown block" in p for p in problems), problems
    assert any(
        "hall.yaml" in p
        and "the doorway of its connector at 3 1 0 is open at 5 1 0, wider than the 3 x 3 wall" in p
        for p in problems
    ), problems


def test_a_doorways_corners_need_no_wall() -> None:
    # A player cannot pass a gap that only touches the doorway at a corner, like item_combination_lock's.
    room = walled((7, 5, 7), "north")
    (door,) = connectors(room)
    assert doorway_gap(room, door, 3, 3) is None
    for cell, open_ in (((1, 4, 0), False), ((5, 0, 0), False), ((1, 3, 0), True), ((3, 4, 0), True)):
        blocks = tuple(
            StructureBlock(b.pos, BlockState("minecraft:air")) if b.pos == cell else b for b in room.blocks
        )
        gap = doorway_gap(Structure(room.size, DATA_VERSION, blocks), door, 3, 3)
        assert (gap == cell) == open_, (cell, gap)


PACING = "  pacing:\n    corridors: [1, 2]\n    fights: [2, 4]\n    repeat: 6\n"


def add_fight(repo: RepoPaths, room_id: str, spawners: int, chests: int) -> None:
    """A combat room with spawners and chests in its middle row, and a door north and south."""
    add_room(repo, room_id, ["combat", "straight"], "north", "south")
    path = repo.content / "rooms" / f"{room_id}.nbt"
    room = room_structure((7, 5, 7), "north", "south")
    extra = tuple(
        StructureBlock((1 + i, 1, 3), BlockState("minecraft:spawner")) for i in range(spawners)
    ) + tuple(StructureBlock((1 + i, 1, 4), BlockState("minecraft:chest")) for i in range(chests))
    path.write_bytes(write_structure(Structure(room.size, DATA_VERSION, room.blocks + extra)))


def test_a_pacing_reaches_the_game_with_the_rooms_spawners_and_chests(repo: RepoPaths) -> None:
    # RLM-364: the pacing reaches the game and the QA mod, and a room carries its spawners and chests.
    crypt(repo)
    add_fight(repo, "crypt/guard", spawners=2, chests=3)
    add_room(repo, "crypt/end", ["hallway", "dead_end"], "north")
    path = repo.content / "dungeons" / "crypt.yaml"
    path.write_text(dungeon(layout=LAYOUT + PACING), encoding="utf-8")
    files = generate(repo)
    data = json.loads(files[f"{DATA}/crypt.json"])
    assert data["layout"]["pacing"] == {"corridors": [1, 2], "fights": [2, 4], "repeat": 6}
    guard = next(room for room in data["rooms"] if room["template"].endswith("/guard"))
    assert (guard["spawners"], guard["chests"]) == (2, 3)
    java = files[EXPECTATIONS].decode()
    assert '"boss", 1, 2, 2, 4, 6,\n' in java
    assert "List.of(), -1, 2, 3, false)" in java, "the guard's expectation carries its spawners and chests"


def test_a_paced_pool_needs_corridors_a_fight_and_a_corridors_dead_end(repo: RepoPaths) -> None:
    # RLM-364: a pacing passes through corridors, hangs branches off corridors, has fights, and without walls closes
    # leftover doorways with a corridor's dead end.
    crypt(repo)
    path = repo.content / "dungeons" / "crypt.yaml"
    path.write_text(dungeon(layout=LAYOUT.replace("[0, 1]", "[1, 2]") + PACING), encoding="utf-8")
    problems = problems_for(repo)
    assert any("layout.pacing" in p and "no fight (role combat with a mob spawner" in p for p in problems), (
        problems
    )
    assert any(
        "layout.pacing" in p and "no corridor's dead end to close open connectors" in p for p in problems
    ), problems
    assert any(
        "layout.branches" in p and "(a corridor, as the pacing hangs branches off them)" in p
        for p in problems
    ), problems
    add_fight(repo, "crypt/guard", spawners=1, chests=0)
    add_room(repo, "crypt/end", ["hallway", "dead_end"], "north")
    add_room(repo, "crypt/fork", ["hallway", "t_junction"], "north", "south", "east")
    load_and_validate(repo)
    path.write_text(dungeon(layout=LAYOUT + PACING.replace("[2, 4]", "[4, 2]")))
    assert any("fights" in p and "lower bound 4 is above the upper bound 2" in p for p in problems_for(repo))
    path.write_text(dungeon(layout=LAYOUT + PACING.replace("[1, 2]", "[0, 2]")))
    assert any("corridors" in p for p in problems_for(repo))


@pytest.mark.parametrize("minutes", [0, 241])
def test_a_portal_stays_open_between_1_and_240_minutes(repo: RepoPaths, minutes: int) -> None:
    crypt(repo)
    path = repo.content / "dungeons" / "crypt.yaml"
    path.write_text(path.read_text().replace("open_minutes: 20", f"open_minutes: {minutes}"))
    assert any("crypt.yaml" in p and "open_minutes" in p for p in problems_for(repo))


def test_a_pool_without_rooms_is_a_problem(repo: RepoPaths) -> None:
    write(repo.content / "dungeons" / "crypt.yaml", dungeon())
    assert problems_for(repo) == [
        "content/dungeons/crypt.yaml: pool: no room lists realmforge:crypt in its pools"
    ]


@pytest.mark.parametrize(
    ("skip", "location", "missing"),
    [
        (("entrance",), "pool", "entrance (role entrance, a connector)"),
        (("hall", "crossing"), "pool", "room to pass through (role hallway, combat, stairs; two connectors)"),
        (("lair",), "layout.ending", "room to end the main path (role boss, a connector)"),
        (("nook",), "pool", "dead end for the branches and open connectors"),
    ],
)
def test_a_pool_needs_every_kind_of_room(
    repo: RepoPaths, skip: tuple[str, ...], location: str, missing: str
) -> None:
    crypt(repo, *skip)
    found = problems_for(repo)
    assert any(
        p.startswith(f"content/dungeons/crypt.yaml: {location}: pool realmforge:crypt has no {missing}")
        for p in found
    ), found


def test_branches_need_a_room_with_a_third_connector(repo: RepoPaths) -> None:
    crypt(repo, "crossing")
    write(repo.content / "dungeons" / "crypt.yaml", dungeon(layout=LAYOUT.replace("[0, 1]", "[1, 2]")))
    found = problems_for(repo)
    assert any(
        p.startswith("content/dungeons/crypt.yaml: layout.branches: every layout has a branch") for p in found
    )


def test_a_room_in_a_dungeon_types_pool_has_one_role(repo: RepoPaths) -> None:
    crypt(repo)
    add_room(repo, "crypt/odd", ["combat", "hallway", "straight"], "north", "south")
    add_room(repo, "crypt/plain", ["straight"], "north", "south")
    add_room(repo, "other/plain", ["straight"], "north", "south", pools=("realmforge:unused",))
    found = problems_for(repo)
    assert (
        "content/rooms/crypt/odd.yaml: tags: a room in a dungeon type's pool has exactly one role tag"
        in found[0]
    )
    assert found[0].endswith("this one has combat, hallway")
    assert found[1].endswith("this one has none") and found[1].startswith("content/rooms/crypt/plain.yaml")
    assert len(found) == 2, "a pool no dungeon type uses needs no roles"


def test_every_connector_must_meet_one_in_the_pool(repo: RepoPaths) -> None:
    crypt(repo)
    add_room(
        repo,
        "crypt/odd_door",
        ["hallway"],
        "north",
        "south",
        name="realmforge:hatch",
        target="realmforge:gate",
    )
    found = problems_for(repo)
    assert len(found) == 2
    assert found[0].startswith(
        "content/rooms/crypt/odd_door.yaml: pools: its connector at 3 1 0 (facing north, target"
    )
    assert found[0].endswith(
        "meets no connector in pool realmforge:crypt, so content/dungeons/crypt.yaml can never place it"
    )


@pytest.mark.parametrize(
    ("layout", "location"),
    [
        (LAYOUT.replace("[2, 4]", "[4, 2]"), "layout.main_path"),
        (LAYOUT.replace("[2, 4]", "[0, 4]"), "layout.main_path.0"),
        (LAYOUT.replace("[2, 4]", "[2, 97]"), "layout.main_path.1"),
        (LAYOUT.replace("[2, 4]", "[2]"), "layout.main_path"),
        (LAYOUT.replace("[0, 1]", "[0, 17]"), "layout.branches.1"),
        (LAYOUT.replace("[1, 2]", "[1, 9]"), "layout.branch_rooms.1"),
        (LAYOUT.replace("ending: boss", "ending: combat"), "layout.ending"),
    ],
)
def test_layout_rules_are_checked(repo: RepoPaths, layout: str, location: str) -> None:
    write(repo.content / "dungeons" / "crypt.yaml", dungeon(layout=layout))
    found = problems_for(repo)
    assert any(p.startswith(f"content/dungeons/crypt.yaml: {location}:") for p in found), found


def test_jigsaw_pools_must_exist(repo: RepoPaths) -> None:
    crypt(repo)
    path = repo.content / "rooms" / "crypt" / "hall.nbt"
    unknown = room_structure((7, 5, 7), "north", "south")
    blocks = tuple(
        StructureBlock(b.pos, b.state, {**(b.nbt or {}), "pool": pool})
        for b, pool in zip(unknown.blocks, ("stonevaults:rooms", POOL), strict=True)
    )
    path.write_bytes(write_structure(Structure(unknown.size, unknown.data_version, blocks)))
    found = problems_for(repo)
    assert found == [
        "content/rooms/crypt/hall.nbt: jigsaw 3 1 0: names template pool 'stonevaults:rooms', which does not "
        "exist: the game would attach nothing there; map it in the import rules' pools"
    ], "a room pool counts as a template pool"


def test_connectors_read_like_the_game() -> None:
    room = room_structure((7, 9, 7), "west", "up")
    bare = Structure(
        room.size,
        room.data_version,
        (
            *room.blocks,
            StructureBlock(
                (3, 0, 3), BlockState.parse("minecraft:jigsaw[orientation=down_east]"), {"name": "hatch"}
            ),
        ),
    )
    west, up, down = connectors(bare)
    assert west == Connector(
        (0, 1, 3), "west", "up", "realmforge:door", "realmforge:door", "minecraft:empty", "aligned"
    )
    assert (up.front, up.top, up.joint) == ("up", "north", "rollable"), (
        "a vertical connector rolls by default"
    )
    assert (down.name, down.target, down.pool) == ("minecraft:hatch", "minecraft:empty", "minecraft:empty")


def test_could_attach_turns_rooms_as_needed() -> None:
    door = Connector(
        (0, 1, 3), "west", "up", "realmforge:door", "realmforge:door", "minecraft:empty", "aligned"
    )
    east = Connector(
        (6, 1, 3), "east", "up", "realmforge:door", "realmforge:door", "minecraft:empty", "aligned"
    )
    up = Connector(
        (3, 4, 3), "up", "north", "realmforge:shaft", "realmforge:shaft", "minecraft:empty", "aligned"
    )
    down = Connector(
        (3, 0, 3), "down", "west", "realmforge:shaft", "realmforge:shaft", "minecraft:empty", "aligned"
    )
    assert could_attach(door, door), "any two sides meet once a room is turned"
    assert could_attach(door, east)
    assert could_attach(up, down) and could_attach(down, up)
    assert not could_attach(up, up), "up meets only down"
    assert not could_attach(door, up), "a side never meets a vertical connector"
    hatch = Connector(
        (0, 1, 3), "west", "up", "realmforge:door", "realmforge:hatch", "minecraft:empty", "aligned"
    )
    assert not could_attach(hatch, door), "a connector takes only one named as its target"
    assert could_attach(door, hatch)


def test_a_pooled_room_keeps_its_fluids_inside_and_its_entrance_marker_in_the_entrance(
    repo: RepoPaths,
) -> None:
    crypt(repo)
    water = StructureBlock((0, 1, 1), BlockState("minecraft:water", (("level", "0"),)))
    entrance = StructureBlock(
        (3, 1, 3),
        BlockState("minecraft:structure_block", (("mode", "data"),)),
        {"id": "minecraft:structure_block", "mode": "DATA", "metadata": "entrance"},
    )
    for room_id, tags, pools in (
        ("crypt/flooded", ["hallway"], (POOL,)),
        ("unused/flooded", ["hallway"], ("realmforge:unused",)),
    ):
        add_room(repo, room_id, tags, "north", "south", pools=pools)
        path = repo.content / "rooms" / f"{room_id}.nbt"
        room = room_structure((7, 5, 7), "north", "south")
        path.write_bytes(
            write_structure(Structure(room.size, room.data_version, (*room.blocks, water, entrance)))
        )
    found = sorted(problems_for(repo))
    assert found == [
        "content/rooms/crypt/flooded.nbt: data marker 3 1 3: an entrance marker in a room whose role is hallway: "
        "players arrive only in the entrance room",
        "content/rooms/crypt/flooded.nbt: fluid at 0 1 1: a fluid that can reach the room's sides or floor flows "
        "out of its box, where cleanup does not reach; a room in a dungeon type's pool keeps its fluids inside",
    ], "a room no dungeon type builds is not checked"


def test_a_pooled_room_fails_on_a_fluid_that_flows_out_through_an_opening(repo: RepoPaths) -> None:
    crypt(repo)
    add_room(repo, "crypt/drain", ["hallway"], "north", "south")
    room = room_structure((7, 5, 7), "north", "south")
    stone = BlockState("minecraft:stone_bricks")
    shell = [
        StructureBlock((x, y, z), stone)
        for x in range(7)
        for y in range(5)
        for z in range(7)
        if (x in (0, 6) or y in (0, 4) or z in (0, 6)) and (x, y, z) not in {c.pos for c in room.blocks}
    ]
    water = StructureBlock((3, 2, 3), BlockState("minecraft:water", (("level", "0"),)))
    drain = StructureBlock((5, 0, 5), BlockState("minecraft:air"))  # a hole in the floor
    blocks = [b for b in shell if b.pos != drain.pos] + [*room.blocks, water, drain]
    path = repo.content / "rooms" / "crypt" / "drain.nbt"
    path.write_bytes(write_structure(Structure(room.size, room.data_version, tuple(blocks))))
    assert sorted(problems_for(repo)) == [
        "content/rooms/crypt/drain.nbt: fluid at 3 2 3: a fluid that can reach the room's sides or floor flows "
        "out of its box, where cleanup does not reach; a room in a dungeon type's pool keeps its fluids inside",
    ]
    path.write_bytes(write_structure(Structure(room.size, room.data_version, (*shell, *room.blocks, water))))
    load_and_validate(repo)  # closed, the room holds its water; its doorways lead into the next rooms


def test_a_cushion_set_into_the_floor_keeps_its_water(repo: RepoPaths) -> None:
    # RLM-276: a waterlogged bottom slab in the floor layer holds its water: its underside is whole, and the floor
    # around it closes its sides. A top slab's water would flow down out of the room.
    crypt(repo)
    add_room(repo, "crypt/landing", ["hallway"], "north", "south")
    room = room_structure((7, 5, 7), "north", "south")
    stone = BlockState("minecraft:stone_bricks")
    shell = [
        StructureBlock((x, y, z), stone)
        for x in range(7)
        for y in range(5)
        for z in range(7)
        if (x in (0, 6) or y in (0, 4) or z in (0, 6)) and (x, y, z) not in {c.pos for c in room.blocks}
    ]
    path = repo.content / "rooms" / "crypt" / "landing.nbt"
    for half, leaks in (("bottom", False), ("top", True)):
        cushion = BlockState.parse(f"minecraft:stone_brick_slab[type={half},waterlogged=true]")
        blocks = [b for b in shell if b.pos != (3, 0, 3)] + [*room.blocks, StructureBlock((3, 0, 3), cushion)]
        path.write_bytes(write_structure(Structure(room.size, room.data_version, tuple(blocks))))
        if leaks:
            assert sorted(problems_for(repo)) == [
                "content/rooms/crypt/landing.nbt: fluid at 3 0 3: a fluid that can reach the room's sides or floor "
                "flows out of its box, where cleanup does not reach; a room in a dungeon type's pool keeps its fluids "
                "inside",
            ]
        else:
            load_and_validate(repo)


# Horizontal directions as (dx, dz), and each one's counter-clockwise neighbour, as Minecraft's Direction has them.
_STEPS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
_COUNTER_CLOCKWISE = {"north": "west", "west": "south", "south": "east", "east": "north"}
_OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west"}


def _stairs_shape(blocks: dict[tuple[int, int, int], BlockState], pos: tuple[int, int, int]) -> str:
    """The shape StairBlock#getStairsShape gives the stair at pos from its neighbours (26.1.2), so a stored shape that
    differs would change at the first block update."""

    def stairs(at: tuple[int, int, int]) -> BlockState | None:
        state = blocks.get(at)
        return state if state is not None and state.name.endswith("_stairs") else None

    def step(at: tuple[int, int, int], direction: str) -> tuple[int, int, int]:
        dx, dz = _STEPS[direction]
        return at[0] + dx, at[1], at[2] + dz

    state = blocks[pos]
    facing, half = state.get("facing"), state.get("half")

    def can_take_shape(direction: str) -> bool:
        other = stairs(step(pos, direction))
        return other is None or other.get("facing") != facing or other.get("half") != half

    def crosses(other: BlockState | None) -> bool:
        return (
            other is not None
            and other.get("half") == half
            and (_STEPS[other.get("facing")][0] == 0) != (_STEPS[facing][0] == 0)
        )

    behind = stairs(step(pos, facing))
    if crosses(behind) and can_take_shape(_OPPOSITE[behind.get("facing")]):
        return "outer_left" if behind.get("facing") == _COUNTER_CLOCKWISE[facing] else "outer_right"
    front = stairs(step(pos, _OPPOSITE[facing]))
    if crosses(front) and can_take_shape(front.get("facing")):
        return "inner_left" if front.get("facing") == _COUNTER_CLOCKWISE[facing] else "inner_right"
    return "straight"


def test_the_lobby_cushion_reaches_past_the_ceiling_hole() -> None:
    # RLM-276: a player who holds a direction while falling leaves the shaft's wall below the lobby's ceiling and
    # drifts outward before landing. The cushion covers the ceiling hole with waterlogged slabs and the ring one block
    # past it with waterlogged stairs, their tall half outward and L-shaped in the corners (Matěj's 5x5), with
    # nothing above it to land on first.
    path = REPO_ROOT / "content/rooms/third_party/stonevaults/dungeon/lobby/lobby_01.nbt"
    room = read_structure(path.read_bytes())
    blocks = {block.pos: block.state for block in room.blocks}
    top = room.size[1] - 1
    hole = {
        (x, z)
        for (x, y, z), state in blocks.items()
        if y == top and state.name in ("minecraft:air", "minecraft:jigsaw")
    }
    assert hole, "lobby_01 has no ceiling hole"
    reach = {(x + dx, z + dz) for x, z in hole for dx in (-1, 0, 1) for dz in (-1, 0, 1)}
    slab = BlockState.parse("minecraft:stone_brick_slab[type=bottom,waterlogged=true]")
    assert {cell for cell in hole if blocks.get((cell[0], 0, cell[1])) != slab} == set()

    rim = reach - hole
    assert len(hole) == 9 and len(rim) == 16
    for x, z in sorted(rim):
        state = blocks[(x, 0, z)]
        assert state.name == "minecraft:stone_brick_stairs", (x, z, state)
        assert (state.get("half"), state.get("waterlogged")) == ("bottom", "true"), (x, z, state)
        assert state.get("shape") == _stairs_shape(blocks, (x, 0, z)), (x, z, state)
        # The tall half faces away from the hole: the step toward the facing leaves the cushion.
        dx, dz = _STEPS[state.get("facing")]
        assert (x + dx, z + dz) not in reach, (x, z, state)
        corner = sum((x + sx, z + sz) in hole for sx, sz in _STEPS.values()) == 0
        assert state.get("shape").startswith("inner_") == corner, (x, z, state)
    assert {
        cell
        for cell in reach
        if blocks.get((cell[0], 1, cell[1]), BlockState("minecraft:air")).name != "minecraft:air"
    } == set()


def test_a_rooms_planner_and_explosion_rules_reach_the_game(repo: RepoPaths) -> None:
    # RLM-256 and RLM-257: the sidecar's rules go into the dungeon type's rooms and the QA mod's expectations.
    crypt(repo)
    nook = repo.content / "rooms" / "crypt" / "nook.yaml"
    nook.write_text(
        nook.read_text(encoding="utf-8")
        + "max_per_layout: 1\nnear_start: true\nexplosion_breaks: [minecraft:gravel, minecraft:smooth_stone]\n",
        encoding="utf-8",
    )
    files = generate(repo)
    rooms = {room["template"]: room for room in json.loads(files[f"{DATA}/crypt.json"])["rooms"]}
    nook_data = rooms["realmforge:rooms/crypt/nook"]
    assert (nook_data["max_per_layout"], nook_data["near_start"], nook_data["explosion_breaks"]) == (
        1,
        True,
        ["minecraft:gravel", "minecraft:smooth_stone"],
    )
    assert not {"max_per_layout", "near_start", "explosion_breaks"} & set(
        rooms["realmforge:rooms/crypt/hall"]
    )
    java = files[EXPECTATIONS].decode()
    assert '1, true, List.of("minecraft:gravel", "minecraft:smooth_stone"), -1, 0, 0, false)' in java
    assert "0, false, List.of(), -1, 0, 0, false)" in java
    nook.write_text(
        nook.read_text(encoding="utf-8").replace("minecraft:smooth_stone", "minecraft:no_such_block"),
        encoding="utf-8",
    )
    assert any(
        "explosion_breaks[1]: unknown block 'minecraft:no_such_block'" in p for p in problems_for(repo)
    )
