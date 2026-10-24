"""Dressing imported rooms (A4c, RLM-120): block variety on plain surfaces and decorations (rooms/dress.py)."""

from __future__ import annotations

from collections import Counter

import pytest
from pydantic import ValidationError

from realmforge_tools.id_universe import load_vanilla
from realmforge_tools.project import DATA_VERSION, REPO_ROOT, RepoPaths
from realmforge_tools.rooms.check import check_structure
from realmforge_tools.rooms.dress import DressRules, dress, family
from realmforge_tools.rooms.structure import (
    BlockState,
    Pos,
    Structure,
    StructureBlock,
    StructureEntity,
    read_structure,
)

STONE_BRICKS = "minecraft:stone_bricks"
COBBLESTONE = "minecraft:cobblestone"
VARIANTS = {
    "floor": {
        "target": 4,
        "replace": [COBBLESTONE, STONE_BRICKS],
        "variants": ["minecraft:mossy_cobblestone", "minecraft:cracked_stone_bricks", "minecraft:gravel"],
    },
    "walls": {
        "target": 4,
        "replace": [STONE_BRICKS, COBBLESTONE],
        "variants": [
            "minecraft:cracked_stone_bricks",
            "minecraft:mossy_stone_bricks",
            "minecraft:mossy_cobblestone",
        ],
    },
    "ceiling": {
        "target": 3,
        "replace": [STONE_BRICKS, COBBLESTONE],
        "variants": ["minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks"],
    },
}
RULES = DressRules.model_validate({**VARIANTS, "decorations": ["vines", "cobwebs", "barrels"]})


def box_room(
    size: Pos = (13, 6, 13), *, floor: str = COBBLESTONE, under_floor: str | None = None
) -> Structure:
    """A closed box: a floor (over a layer of ``under_floor`` when given), stone brick walls and ceiling, air
    inside, and a connector in the middle of the west and east walls."""
    x_size, y_size, z_size = size
    base = 1 if under_floor else 0
    blocks: list[StructureBlock] = []
    for x in range(x_size):
        for y in range(y_size):
            for z in range(z_size):
                pos = (x, y, z)
                if under_floor and y == 0:
                    name = under_floor
                elif y == base:
                    name = floor
                elif y == y_size - 1 or x in (0, x_size - 1) or z in (0, z_size - 1):
                    name = STONE_BRICKS
                else:
                    name = "minecraft:air"
                blocks.append(StructureBlock(pos, BlockState(name)))
    doors = {(0, base + 1, z_size // 2): "west_up", (x_size - 1, base + 1, z_size // 2): "east_up"}
    blocks = [b for b in blocks if b.pos not in doors]
    for pos, orientation in doors.items():
        data = {"id": "minecraft:jigsaw", "name": "realmforge:door", "target": "realmforge:door"}
        blocks.append(
            StructureBlock(pos, BlockState.parse(f"minecraft:jigsaw[orientation={orientation}]"), data)
        )
    return Structure(size, DATA_VERSION, tuple(blocks))


def names(structure: Structure) -> Counter[str]:
    return Counter(block.state.name for block in structure.blocks)


@pytest.mark.parametrize(
    ("name", "material"),
    [
        ("minecraft:stone_bricks", "stone_brick"),
        ("minecraft:stone_brick_stairs", "stone_brick"),
        ("minecraft:stone_brick_slab", "stone_brick"),
        ("minecraft:oak_planks", "oak"),
        ("minecraft:cobblestone", "cobblestone"),
        ("minecraft:mossy_cobblestone_wall", "mossy_cobblestone"),
    ],
)
def test_family_is_the_material_whatever_the_shape(name: str, material: str) -> None:
    assert family(name) == material


def test_a_plain_room_gets_variants_up_to_its_targets(repo: RepoPaths) -> None:
    room = box_room(under_floor=STONE_BRICKS)
    dressed, notes = dress(room, RULES, name="plain")
    counts = names(dressed)
    for variant in ("mossy_cobblestone", "cracked_stone_bricks", "gravel", "mossy_stone_bricks"):
        assert counts[f"minecraft:{variant}"] > 0, variant
    assert any(note.startswith("dressed the floor with") for note in notes)
    assert check_structure(dressed, load_vanilla(repo)) == []


def test_a_varied_surface_gets_nothing_more() -> None:
    # A floor of four materials already meets its target of four: it stays as it is.
    room = box_room()
    floor_names = [
        "minecraft:cobblestone",
        "minecraft:polished_andesite",
        "minecraft:grass_block",
        "minecraft:dirt",
    ]
    varied = tuple(
        StructureBlock(b.pos, BlockState(floor_names[(b.pos[0] + b.pos[2]) % 4])) if b.pos[1] == 0 else b
        for b in room.blocks
    )
    dressed, notes = dress(Structure(room.size, room.data_version, varied), RULES, name="varied")
    floor = Counter(b.state.name for b in dressed.blocks if b.pos[1] == 0)
    assert set(floor) <= set(floor_names) | {"minecraft:jigsaw"}
    assert not any(note.startswith("dressed the floor") for note in notes)


def test_gravel_needs_a_block_below() -> None:
    dressed, _ = dress(box_room(), RULES, name="no-support")  # the floor is the bottom layer
    assert names(dressed)["minecraft:gravel"] == 0
    supported, _ = dress(box_room(under_floor=STONE_BRICKS), RULES, name="no-support")
    gravel = [b.pos for b in supported.blocks if b.state.name == "minecraft:gravel"]
    assert gravel and all(pos[1] == 1 for pos in gravel)


def test_dressing_is_deterministic_and_seeded_by_the_room() -> None:
    room = box_room(under_floor=STONE_BRICKS)
    first, _ = dress(room, RULES, name="a")
    again, _ = dress(room, RULES, name="a")
    other, _ = dress(room, RULES, name="b")
    assert sorted(first.blocks, key=lambda b: b.pos) == sorted(again.blocks, key=lambda b: b.pos)
    assert sorted(first.blocks, key=lambda b: b.pos) != sorted(other.blocks, key=lambda b: b.pos)


def test_decorations_keep_doorways_clear() -> None:
    room = box_room()
    doors = [b.pos for b in room.blocks if b.state.name == "minecraft:jigsaw"]
    dressed, _ = dress(room, RULES, name="doors")
    decorations = {"minecraft:vine", "minecraft:cobweb", "minecraft:barrel"}
    placed = [b for b in dressed.blocks if b.state.name in decorations]
    assert placed
    for block in placed:
        assert all(max(abs(block.pos[0] - d[0]), abs(block.pos[2] - d[2])) > 1 for d in doors), block


def test_dressing_adds_no_armor_stands() -> None:
    # Matěj's call after the playtest (2026-10-14): armor stands placed by rule looked out of place, so the dressing
    # has none, and the rules cannot ask for them.
    dressed, _ = dress(box_room(), RULES, name="room", tags=["combat"])
    assert dressed.entities == ()
    with pytest.raises(ValidationError):
        DressRules.model_validate({**VARIANTS, "decorations": ["armor_stands"]})


def test_excluded_rooms_stay_as_they_are() -> None:
    rules = RULES.model_copy(update={"exclude": ["dungeon/entrances/*"]})
    assert rules.excludes("dungeon/entrances/entrance_1")
    assert not rules.excludes("dungeon/rooms/library")


def test_without_leaves_a_decoration_out_of_the_rooms_it_names() -> None:
    rules = RULES.model_copy(update={"without": {"barrels": ["dungeon/rooms/*"]}})
    assert rules.decorations_for("dungeon/rooms/library") == ["vines", "cobwebs"]
    assert "barrels" in rules.decorations_for("dungeon/lobby/lobby_01")
    room = box_room()
    with_barrels, _ = dress(room, RULES, name="dungeon/rooms/library", tags=["combat"])
    without, _ = dress(room, rules, name="dungeon/rooms/library", tags=["combat"])
    assert names(with_barrels)["minecraft:barrel"] > 0 and names(without)["minecraft:barrel"] == 0

    # Barrels come last: every other block and entity of the room stays as it was.
    def others(structure: Structure) -> dict[Pos, StructureBlock]:
        return {b.pos: b for b in structure.blocks if b.state.name != "minecraft:barrel"}

    barrels = {b.pos for b in with_barrels.blocks if b.state.name == "minecraft:barrel"}
    assert {pos: b for pos, b in others(without).items() if pos not in barrels} == others(with_barrels)
    assert {b.state.name for b in without.blocks if b.pos in barrels} == {"minecraft:air"}
    assert with_barrels.entities == without.entities


def test_decorations_keep_off_the_rooms_own_entities() -> None:
    room = box_room()
    plain, _ = dress(room, RULES, name="frames", tags=["combat"])
    decorations = {"minecraft:vine", "minecraft:cobweb", "minecraft:barrel"}
    spots = [b.pos for b in plain.blocks if b.state.name in decorations]
    # Hang a painting on each spot the dressing used: a block there would make it pop off.
    paintings = tuple(
        StructureEntity((x + 0.5, y + 0.5, z + 0.5), (x, y, z), {"id": "minecraft:painting"})
        for x, y, z in spots
    )
    dressed, _ = dress(Structure(room.size, room.data_version, room.blocks, paintings), RULES, name="frames")
    for painting in paintings:
        x, y, z = painting.block_pos
        near = {(x + dx, y + dy, z + dz) for dx in range(-2, 3) for dy in range(-2, 3) for dz in range(-2, 3)}
        placed = [b.pos for b in dressed.blocks if b.state.name in decorations and b.pos in near]
        assert not placed, (painting.block_pos, placed)


# --- barrels in patterns (ADR 0036, RLM-262; the throne room, RLM-256) ------------------------------------------


def barrels(structure: Structure) -> set[Pos]:
    return {b.pos for b in structure.blocks if b.state.name == "minecraft:barrel"}


def with_blocks(room: Structure, placed: dict[Pos, str]) -> Structure:
    blocks = tuple(
        StructureBlock(b.pos, BlockState(placed[b.pos])) if b.pos in placed else b for b in room.blocks
    )
    return Structure(room.size, room.data_version, blocks)


def is_pattern(cells: set[Pos], size: Pos) -> bool:
    """A mirror of the room maps the barrels onto themselves (mirrored corners, mirrored pairs, a pair or a row in a
    wall's middle), or they are a pair side by side, or a single barrel, in a corner."""
    mirrored = {(size[0] - 1 - x, y, z) for x, y, z in cells} == cells or {
        (x, y, size[2] - 1 - z) for x, y, z in cells
    } == cells
    return mirrored or (len(cells) in (1, 2) and any(corner(pos, size) for pos in cells))


def corner(pos: Pos, size: Pos) -> bool:
    return pos[0] in (1, size[0] - 2) and pos[2] in (1, size[2] - 2)


@pytest.mark.parametrize("name", [f"room-{i}" for i in range(12)])
def test_barrels_stand_against_the_walls_in_a_pattern(name: str) -> None:
    room = box_room()
    dressed, _ = dress(room, RULES, name=name, tags=["combat"])
    placed = barrels(dressed)
    assert 2 <= len(placed) <= 3  # 121 blocks of floor
    assert all(pos[1] == 1 for pos in placed)
    assert all(pos[0] in (1, 11) or pos[2] in (1, 11) for pos in placed), placed
    assert is_pattern(placed, room.size), placed


def test_a_single_barrel_stands_only_in_a_corner_of_a_hallway() -> None:
    for i in range(40):
        dressed, _ = dress(box_room(), RULES, name=f"hall-{i}", tags=["hallway", "straight"])
        placed = barrels(dressed)
        assert len(placed) <= 1
        assert all(corner(pos, dressed.size) for pos in placed), placed
    stairs, _ = dress(box_room(), RULES, name="stairs", tags=["stairs", "straight"])
    assert not barrels(stairs)


def test_barrels_keep_off_a_throne_in_the_room() -> None:
    # A gold throne in the middle, as in Stonevaults' mooshroom_king: its top and sides are no walls to stand
    # barrels against, however the hashes fall.
    throne = {
        (x, y, z): "minecraft:gold_block" for x in range(5, 8) for y in range(1, 4) for z in range(5, 8)
    }
    room = with_blocks(box_room(), throne)
    for i in range(24):
        dressed, _ = dress(room, RULES, name=f"throne-{i}", tags=["treasure", "dead_end"])
        placed = barrels(dressed)
        assert placed and all(pos[1] == 1 for pos in placed)
        near = {(x + dx, y, z + dz) for x, y, z in throne for dx in (-1, 0, 1) for dz in (-1, 0, 1)}
        assert not placed & near, placed


def test_barrels_never_close_a_passage() -> None:
    # A wall across the room with a gap one block wide: barrels never fill the gap, nor cut the floor in two.
    wall = {(x, 1, 6): STONE_BRICKS for x in range(1, 12) if x != 6}
    room = with_blocks(box_room(), wall)
    for i in range(24):
        dressed, _ = dress(room, RULES, name=f"gap-{i}", tags=["combat"])
        placed = barrels(dressed)
        assert (6, 1, 6) not in placed
        floor = {b.pos for b in dressed.blocks if b.pos[1] == 1 and b.state.name == "minecraft:air"}
        seen, todo = set(), [next(iter(floor))]
        while todo:
            x, y, z = todo.pop()
            if (x, y, z) in seen or (x, y, z) not in floor:
                continue
            seen.add((x, y, z))
            todo += [(x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1)]
        assert seen == floor, placed


def test_no_pattern_fits_means_no_barrel() -> None:
    # A room too small for any pattern but a single barrel in a wall's middle gets none.
    dressed, _ = dress(box_room((5, 4, 9)), RULES, name="narrow", tags=["combat"])
    assert not barrels(dressed)


def test_the_throne_room_has_no_barrel_on_or_beside_its_throne() -> None:
    # The committed Stonevaults room after its re-import with these rules (RLM-256): the barrels stand on the main
    # floor against the outer walls, none on the gold throne or next to it.
    path = REPO_ROOT / "content/rooms/third_party/stonevaults/dungeon/treasure/mooshroom_king.nbt"
    room = read_structure(path.read_bytes())
    gold = {b.pos for b in room.blocks if b.state.name == "minecraft:gold_block"}
    placed = barrels(room)
    assert gold and placed
    assert all(pos[1] == 1 for pos in placed)
    near = {
        (x + dx, y + dy, z + dz)
        for x, y, z in gold
        for dx in (-1, 0, 1)
        for dy in (-1, 0, 1)
        for dz in (-1, 0, 1)
    }
    assert not placed & near


def with_states(room: Structure, placed: dict[Pos, str]) -> Structure:
    """``room`` with the blocks of ``placed`` (block states as commands write them) set."""
    blocks = {block.pos: block for block in room.blocks}
    for pos, state in placed.items():
        blocks[pos] = StructureBlock(pos, BlockState.parse(state))
    return Structure(room.size, room.data_version, tuple(blocks.values()))


def changed(before: Structure, after: Structure) -> set[Pos]:
    old = {block.pos: block.state for block in before.blocks}
    return {block.pos for block in after.blocks if old.get(block.pos) != block.state}


def test_dressing_keeps_off_mechanisms() -> None:
    # Matěj's playtest (2026-10-24): a cobweb in a lock's redstone stopped its TNT. Nothing dressing adds or swaps
    # lies next to, above, below or diagonally beside a mechanism block.
    mechanisms = {
        (3, 1, 6): "minecraft:redstone_wire",
        (4, 1, 6): "minecraft:redstone_wire",
        (6, 2, 1): "minecraft:lever[face=wall,facing=south,powered=false]",
        (9, 1, 9): "minecraft:tnt",
        (10, 4, 11): "minecraft:sticky_piston[extended=false,facing=down]",
    }
    room = with_states(box_room(under_floor=STONE_BRICKS), mechanisms)
    for name in ("mechanism-a", "mechanism-b", "mechanism-c"):
        dressed, notes = dress(room, RULES, name=name)
        assert notes, "the plain room still gets its dressing"
        for x, y, z in changed(room, dressed):
            near = [p for p in mechanisms if max(abs(x - p[0]), abs(y - p[1]), abs(z - p[2])) <= 1]
            assert not near, f"{name}: dressing changed {(x, y, z)} beside {near}"


def test_decorations_go_only_where_a_player_reaches() -> None:
    # A sealed pocket inside the walls, like the hidden redstone runs behind a lock's walls, gets no vine, cobweb or
    # barrel; the room around it still does.
    room = box_room((13, 7, 13), under_floor=STONE_BRICKS)
    pocket = {(x, y, z) for x in range(2, 5) for y in range(2, 5) for z in range(2, 5)}
    shell = {(x, y, z) for x in range(1, 6) for y in range(1, 6) for z in range(1, 6)} - pocket
    room = with_states(room, {pos: STONE_BRICKS for pos in shell} | {pos: "minecraft:air" for pos in pocket})
    decorations = ("minecraft:vine", "minecraft:cobweb", "minecraft:barrel")
    for name in ("pocket-a", "pocket-b", "pocket-c", "pocket-d"):
        dressed, _ = dress(room, RULES, name=name)
        inside = [b for b in dressed.blocks if b.pos in pocket and b.state.name in decorations]
        assert not inside, f"{name}: {inside}"
        assert any(b.state.name in decorations for b in dressed.blocks), f"{name}: the open room got nothing"


def test_a_lever_that_has_to_be_on_gets_stairs_under_it() -> None:
    # Matěj's playtest (2026-10-24): [lever] [block] [torch] marks a lever that has to be on. The floor under it
    # becomes stairs whose tall half faces the room, leaving a notch along the wall; a lever without a torch behind
    # its block stays as it is.
    room = with_states(
        box_room(),
        {
            (3, 2, 2): "minecraft:lever[face=wall,facing=south,powered=false]",
            (3, 2, 1): "minecraft:smooth_stone",
            (3, 2, 0): "minecraft:redstone_wall_torch[facing=north,lit=true]",
            (7, 2, 2): "minecraft:lever[face=wall,facing=south,powered=false]",
            (7, 2, 1): "minecraft:smooth_stone",
            (7, 2, 0): "minecraft:redstone_wire",
        },
    )
    rules = DressRules.model_validate({"lever_hints": "minecraft:stone_brick_stairs"})
    dressed, notes = dress(room, rules, name="levers")
    blocks = {block.pos: block.state for block in dressed.blocks}
    assert str(blocks[(3, 0, 2)]) == (
        "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"
    )
    assert blocks[(7, 0, 2)].name == COBBLESTONE, "a lever with no torch behind its block needs no hint"
    assert notes == ["marked 1 lever(s) that have to be on"]
    assert dress(room, DressRules(), name="levers")[1] == [], "without the rule, no hints"
