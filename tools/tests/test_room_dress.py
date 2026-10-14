"""Dressing imported rooms (A4c, RLM-120): block variety on plain surfaces and decorations (rooms/dress.py)."""

from __future__ import annotations

from collections import Counter

import pytest

from realmforge_tools.formats import nbt
from realmforge_tools.id_universe import load_vanilla
from realmforge_tools.project import DATA_VERSION, RepoPaths
from realmforge_tools.rooms.check import check_structure
from realmforge_tools.rooms.dress import DressRules, dress, family
from realmforge_tools.rooms.structure import BlockState, Pos, Structure, StructureBlock

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
RULES = DressRules.model_validate(
    {**VARIANTS, "decorations": ["armor_stands", "vines", "cobwebs", "barrels"]}
)


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
    decorations = {"minecraft:vine", "minecraft:cobweb", "minecraft:barrel", "minecraft:stone_brick_stairs"}
    placed = [b for b in dressed.blocks if b.state.name in decorations]
    assert placed
    for block in placed:
        clearance = 2 if block.state.name == "minecraft:stone_brick_stairs" else 1
        assert all(max(abs(block.pos[0] - d[0]), abs(block.pos[2] - d[2])) > clearance for d in doors), block


def test_rooms_get_armor_stands_on_pedestals_and_hallways_none() -> None:
    dressed, notes = dress(box_room(), RULES, name="room", tags=["combat", "straight"])
    (stand,) = [e for e in dressed.entities if e.id == "minecraft:armor_stand"]
    pedestal = next(
        b for b in dressed.blocks if b.pos == (stand.block_pos[0], stand.block_pos[1] - 1, stand.block_pos[2])
    )
    assert pedestal.state.name == "minecraft:stone_brick_stairs" and pedestal.state.get("half") == "top"
    assert any("armor stand" in note for note in notes)
    hallway, _ = dress(box_room(), RULES, name="room", tags=["hallway", "straight"])
    assert not hallway.entities


@pytest.mark.parametrize(
    ("data_version", "check"),
    [
        (
            2724,
            lambda data: (
                data["ArmorItems"].items[2] == {"id": "minecraft:iron_chestplate", "Count": nbt.Byte(1)}
            ),
        ),
        (
            3955,
            lambda data: data["ArmorItems"].items[0] == {"id": "minecraft:iron_boots", "count": nbt.Int(1)},
        ),
        (
            DATA_VERSION,
            lambda data: data["equipment"]["head"] == {"id": "minecraft:iron_helmet", "count": nbt.Int(1)},
        ),
    ],
)
def test_armor_follows_the_templates_data_version(data_version: int, check) -> None:
    room = box_room()
    room = Structure(room.size, data_version, room.blocks)
    # Find a name whose stand wears iron, so the checks can name the items.
    for seed in range(50):
        dressed, notes = dress(room, RULES, name=f"armor-{seed}", tags=["combat"])
        if "armor stand (iron)" in notes[-1]:
            break
    (stand,) = [e for e in dressed.entities if e.id == "minecraft:armor_stand"]
    assert check(stand.nbt)


def test_excluded_rooms_stay_as_they_are() -> None:
    rules = RULES.model_copy(update={"exclude": ["dungeon/entrances/*"]})
    assert rules.excludes("dungeon/entrances/entrance_1")
    assert not rules.excludes("dungeon/rooms/library")
