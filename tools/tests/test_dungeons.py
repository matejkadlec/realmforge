"""content/dungeons/: the datapack registry realmforge:dungeon_type, room pools and their validation (W4a, RLM-178;
architecture 15.2-15.4)."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import DATA_VERSION, RepoPaths
from realmforge_tools.rooms.connectors import Connector, connectors, could_attach
from realmforge_tools.rooms.structure import BlockState, Structure, StructureBlock, write_structure
from realmforge_tools.validate import load_and_validate

from .conftest import write

DATA = "mod/src/generated/resources/data/realmforge/realmforge/dungeon_type"
EXPECTATIONS = "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedDungeonTypeExpectations.java"
POOL = "realmforge:crypt"
LAYOUT = "layout:\n  main_path: [2, 4]\n  branches: [0, 1]\n  branch_rooms: [1, 2]\n  ending: boss\n"


def dungeon(entry_id: str = "crypt", pool: str = POOL, layout: str = LAYOUT) -> str:
    return f"id: {entry_id}\nname:\n  en_us: Crypt\npool: {pool}\n{layout}"


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
        (LAYOUT.replace("[2, 4]", "[2, 33]"), "layout.main_path.1"),
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
