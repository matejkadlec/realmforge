"""Rooms (A4b, RLM-119): templates, the three source formats, the importer and the rooms content type."""

from __future__ import annotations

import base64
import gzip
import json
from dataclasses import replace
from pathlib import Path

import nucleation
import pytest

from realmforge_tools.formats import nbt
from realmforge_tools.formats.deflate import gzip_stored
from realmforge_tools.generate import generate_all
from realmforge_tools.generate.gametest import SPIKE_ROOMS, room_blocks, room_structure
from realmforge_tools.id_universe import load_vanilla
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import DATA_VERSION, RepoPaths
from realmforge_tools.rooms import importer
from realmforge_tools.rooms.check import check_structure, fluid_edges, markers
from realmforge_tools.rooms.slots import slot_tags
from realmforge_tools.rooms.sources import read_source, structure_from_sponge
from realmforge_tools.rooms.structure import (
    BlockState,
    Structure,
    StructureBlock,
    StructureEntity,
    StructureError,
    read_structure,
    split_state_text,
    write_structure,
)
from realmforge_tools.validate import load_and_validate

from .conftest import write

STONE = BlockState("minecraft:stone_bricks")
AIR = BlockState("minecraft:air")


def small_room(**changes: object) -> Structure:
    """A 5x4x5 room: a floor and a ceiling, a jigsaw on its east face, a chest with an item, an armor stand. Every
    layer holds blocks, because nucleation's writer (which makes the .schem and .litematic samples) leaves out
    air-only layers."""
    blocks = [StructureBlock((x, y, z), STONE) for x in range(5) for y in (0, 3) for z in range(5)]
    blocks += [StructureBlock((x, y, z), AIR) for x in range(5) for y in (1, 2) for z in range(5)]
    blocks = [b for b in blocks if b.pos not in {(4, 1, 2), (1, 1, 1)}]
    jigsaw = {
        "id": "minecraft:jigsaw",
        "name": "src:door",
        "target": "src:door",
        "pool": "minecraft:empty",
        "final_state": "minecraft:air",
        "joint": "aligned",
    }
    blocks.append(
        StructureBlock((4, 1, 2), BlockState.parse("minecraft:jigsaw[orientation=east_up]"), jigsaw)
    )
    chest = {
        "id": "minecraft:chest",
        "Items": nbt.NbtList(
            nbt.TAG_COMPOUND, ({"Slot": nbt.Byte(0), "id": "minecraft:bread", "count": nbt.Int(2)},)
        ),
        "seeds": nbt.LongArray((1, -1)),
    }
    blocks.append(
        StructureBlock(
            (1, 1, 1), BlockState.parse("minecraft:chest[facing=west,type=single,waterlogged=false]"), chest
        )
    )
    stand = {"id": "minecraft:armor_stand", "UUID": nbt.IntArray((1, 2, 3, 4)), "Invisible": nbt.Byte(1)}
    room = Structure(
        (5, 4, 5), DATA_VERSION, tuple(blocks), (StructureEntity((2.5, 1.0, 2.5), (2, 1, 2), stand),)
    )
    return replace(room, **changes)


def schematic_of(structure: Structure, data_version: int = DATA_VERSION) -> nucleation.Schematic:
    """The same room as a nucleation schematic (what WorldEdit or Litematica would save)."""
    schematic = nucleation.Schematic.create("sample")
    for block in structure.blocks:
        if block.nbt is not None and block.state.name == "minecraft:jigsaw":
            values = {k: v for k, v in block.nbt.items() if k != "id"}
            schematic.set_block_with_nbt(*block.pos, str(block.state), json.dumps(values))
        else:
            schematic.set_block_from_string(*block.pos, str(block.state))
        if block.state.name == "minecraft:chest":
            snbt = '{Items:[{Slot:0b,id:"minecraft:bread",count:2}],seeds:[L;1L,-1L]}'
            schematic.set_block_entity(*block.pos, "minecraft:chest", snbt)
    schematic.add_entity_from_snbt('{id:"minecraft:armor_stand",Pos:[2.5d,1.0d,2.5d],UUID:[I;1,2,3,4]}')
    schematic.add_entity_from_snbt('{id:"minecraft:zombie",Pos:[1.5d,1.0d,3.5d],UUID:[I;5,6,7,8]}')
    schematic.set_mc_version(data_version)
    return schematic


def write_samples(folder: Path) -> dict[str, Path]:
    """One sample room per format: ours as .nbt, nucleation's .schem and .litematic of the same room."""
    folder.mkdir(parents=True, exist_ok=True)
    room = small_room()
    schematic = schematic_of(room)
    files = {
        "nbt": folder / "sample_nbt.nbt",
        "schem": folder / "Sample Schem.schem",
        "litematic": folder / "sample-lit.litematic",
    }
    files["nbt"].write_bytes(write_structure(room))
    files["schem"].write_bytes(base64.b64decode(schematic.to_schematic_b64()))
    files["litematic"].write_bytes(base64.b64decode(schematic.to_litematic_b64()))
    return files


RULES = importer.ImportRules.model_validate(
    {
        "connectors": {"src:door": "realmforge:door"},
        "pools": {"src:halls": "realmforge:rooms/halls"},
        "entities": {"keep": ["minecraft:armor_stand"]},
    }
)


def third_party(repo: RepoPaths, source: str = "sample") -> Path:
    folder = repo.content / "rooms" / "third_party" / source
    write(folder / "LICENSE", "MIT License\n")
    write(folder / "NOTICE", "Sample rooms, test fixture.\n")
    return folder


# --- templates ---------------------------------------------------------------------------------------------------


def test_template_with_entities_and_array_tags_round_trips() -> None:
    room = small_room()
    data = write_structure(room)
    again = read_structure(data)
    assert again.size == room.size and again.data_version == DATA_VERSION
    assert sorted(again.blocks, key=lambda b: b.pos) == sorted(room.blocks, key=lambda b: b.pos)
    assert again.entities == room.entities
    assert again.entities[0].nbt["UUID"] == nbt.IntArray((1, 2, 3, 4))
    assert write_structure(again) == data  # normalized output is stable


def test_normalized_output_ignores_input_order() -> None:
    room = small_room()
    shuffled = replace(room, blocks=tuple(reversed(room.blocks)))
    assert write_structure(shuffled) == write_structure(room)


def test_normalized_template_is_vanilla_shaped() -> None:
    _, root = nbt.decode(gzip.decompress(write_structure(small_room())))
    assert root["DataVersion"] == nbt.Int(DATA_VERSION)
    assert root["size"] == nbt.int_list(5, 4, 5)
    first = root["blocks"].items[0]
    assert first["pos"] == nbt.int_list(0, 0, 0)
    entity = root["entities"].items[0]
    assert entity["blockPos"] == nbt.int_list(2, 1, 2)
    assert entity["pos"].element_type == nbt.TAG_DOUBLE


def test_spike_room_reads_back() -> None:
    hall = read_structure(room_structure(SPIKE_ROOMS["hall"]))
    assert hall.size == SPIKE_ROOMS["hall"].size
    assert len(hall.blocks) == len(room_blocks(SPIKE_ROOMS["hall"]))


def test_spike_rooms_hold_valid_data_markers(repo: RepoPaths) -> None:
    rooms = {name: read_structure(room_structure(room)) for name, room in SPIKE_ROOMS.items()}
    assert {name: markers(room) for name, room in rooms.items()} == {
        "start": [("entrance", (7, 1, 7))],
        "corridor": [("mob", (12, 1, 3))],
        "hall": [("exit", (15, 1, 27))],
    }
    for room in rooms.values():
        assert check_structure(room, load_vanilla(repo)) == []


@pytest.mark.parametrize(
    ("text", "state"),
    [
        ("minecraft:stone", BlockState("minecraft:stone")),
        (
            "minecraft:oak_stairs[half=bottom,facing=east]",
            BlockState("minecraft:oak_stairs", (("facing", "east"), ("half", "bottom"))),
        ),
        ("minecraft:chest[]", BlockState("minecraft:chest")),
    ],
)
def test_block_state_parse(text: str, state: BlockState) -> None:
    assert BlockState.parse(text) == state
    assert BlockState.parse(str(state)) == state


@pytest.mark.parametrize("text", ["minecraft:stairs[facing]", "minecraft:x[a=b", "[a=b]"])
def test_block_state_parse_rejects_garbage(text: str) -> None:
    with pytest.raises(StructureError):
        BlockState.parse(text)


def test_template_with_several_palettes_is_refused_unless_asked() -> None:
    _, root = nbt.decode(gzip.decompress(write_structure(small_room())))
    palette = root.pop("palette")
    root["palettes"] = nbt.NbtList(nbt.TAG_LIST, (palette, palette))
    data = gzip_stored(nbt.encode(root))
    with pytest.raises(StructureError, match="2 palettes"):
        read_structure(data)
    assert read_structure(data, first_palette=True).size == (5, 4, 5)


def test_garbage_is_not_a_template() -> None:
    with pytest.raises(StructureError):
        read_structure(b"\x1f\x8bnot gzip")
    with pytest.raises(StructureError):
        read_structure(nbt.encode({"size": nbt.int_list(1, 1, 1)}))


def corrupt(data: bytes) -> bytes:
    """A template whose gzip header is fine but whose deflate stream is not: the stored block's length check (NLEN,
    after the 10-byte gzip header and the block's first 3 bytes) is flipped, so zlib fails mid-stream."""
    broken = bytearray(data)
    broken[13] ^= 0xFF
    return bytes(broken)


def test_corrupt_gzip_body_is_a_structure_error() -> None:
    with pytest.raises(StructureError, match="broken gzip data"):
        read_structure(corrupt(write_structure(small_room())))


# --- source formats ----------------------------------------------------------------------------------------------


@pytest.mark.parametrize("fmt", ["nbt", "schem", "litematic"])
def test_every_format_reads_the_same_room(tmp_path: Path, fmt: str) -> None:
    source = read_source(write_samples(tmp_path)[fmt])
    structure = source.structure
    assert source.format == fmt
    assert structure.size == (5, 4, 5)
    assert structure.data_version == DATA_VERSION
    states = {b.pos: b.state for b in structure.blocks}
    assert states[(4, 1, 2)] == BlockState("minecraft:jigsaw", (("orientation", "east_up"),))
    assert states[(0, 0, 0)] == STONE
    jigsaw = next(b for b in structure.blocks if b.pos == (4, 1, 2))
    assert (
        jigsaw.nbt is not None
        and jigsaw.nbt["id"] == "minecraft:jigsaw"
        and jigsaw.nbt["pool"] == "minecraft:empty"
    )
    assert (
        not {"Id", "Pos", "x", "y", "z"} & jigsaw.nbt.keys()
    )  # Sponge and Litematica position keys are gone
    chest = next(b for b in structure.blocks if b.pos == (1, 1, 1))
    assert chest.nbt is not None and chest.nbt["seeds"] == nbt.LongArray((1, -1))
    stand = next(e for e in structure.entities if e.id == "minecraft:armor_stand")
    assert stand.pos == (2.5, 1.0, 2.5) and stand.block_pos == (2, 1, 2)
    assert stand.nbt["UUID"] == nbt.IntArray((1, 2, 3, 4))


def test_old_data_version_is_kept(tmp_path: Path) -> None:
    path = tmp_path / "old.schem"
    path.write_bytes(base64.b64decode(schematic_of(small_room(), data_version=3955).to_schematic_b64()))
    assert read_source(path).structure.data_version == 3955


def sponge_v1(path: Path) -> Path:
    """A Sponge v1 schematic, which has no DataVersion: a 1x2x1 stone block under a jigsaw-free air block."""
    root = {
        "Version": nbt.Int(1),
        "Width": nbt.Short(1),
        "Height": nbt.Short(2),
        "Length": nbt.Short(1),
        "Palette": {"minecraft:stone": nbt.Int(0), "minecraft:air": nbt.Int(1)},
        "BlockData": nbt.ByteArray((0, 1)),
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip_stored(nbt.encode(root, "Schematic")))
    return path


def test_sponge_reader_decodes_multi_byte_varints() -> None:
    names = ["minecraft:air"] + [f"minecraft:block_{i}" for i in range(199)]
    # Indices 0 and 199 in a 2x1x1 schematic: 199 needs two varint bytes (0xC7 0x01).
    root = {
        "Schematic": {
            "Version": nbt.Int(3),
            "DataVersion": nbt.Int(DATA_VERSION),
            "Width": nbt.Short(2),
            "Height": nbt.Short(1),
            "Length": nbt.Short(1),
            "Blocks": {
                "Palette": {name: nbt.Int(i) for i, name in enumerate(names)},
                "Data": nbt.ByteArray((0, -57, 1)),
            },
        }
    }
    structure = structure_from_sponge(root)
    assert [str(b.state) for b in structure.blocks] == ["minecraft:air", "minecraft:block_198"]
    root["Schematic"]["Blocks"]["Data"] = nbt.ByteArray((0, -57))
    with pytest.raises(StructureError, match="varint"):
        structure_from_sponge(root)


def test_sponge_v2_keeps_air_layers_and_flat_data(tmp_path: Path) -> None:
    # 2 x 3 x 1: stone at the bottom, then air, then a sign whose block entity data sits flat (v2).
    root = {
        "Version": nbt.Int(2),
        "DataVersion": nbt.Int(3955),
        "Width": nbt.Short(2),
        "Height": nbt.Short(3),
        "Length": nbt.Short(1),
        "Palette": {
            "minecraft:stone": nbt.Int(0),
            "minecraft:air": nbt.Int(1),
            "minecraft:oak_sign": nbt.Int(2),
        },
        "BlockData": nbt.ByteArray((0, 0, 1, 1, 2, 1)),
        "BlockEntities": nbt.NbtList(
            nbt.TAG_COMPOUND,
            ({"Pos": nbt.IntArray((0, 2, 0)), "Id": "minecraft:sign", "is_waxed": nbt.Byte(1)},),
        ),
        "Entities": nbt.NbtList(
            nbt.TAG_COMPOUND,
            (
                {
                    "Pos": nbt.NbtList(nbt.TAG_DOUBLE, (nbt.Double(1.5), nbt.Double(1.0), nbt.Double(0.5))),
                    "Id": "minecraft:armor_stand",
                    "UUID": nbt.IntArray((1, 2, 3, 4)),
                },
            ),
        ),
    }
    path = tmp_path / "v2.schem"
    path.write_bytes(gzip_stored(nbt.encode(root, "Schematic")))
    structure = read_source(path).structure
    assert structure.size == (2, 3, 1) and structure.data_version == 3955
    states = {b.pos: b.state.name for b in structure.blocks}
    assert states == {
        (0, 0, 0): "minecraft:stone",
        (1, 0, 0): "minecraft:stone",
        (0, 1, 0): "minecraft:air",
        (1, 1, 0): "minecraft:air",
        (0, 2, 0): "minecraft:oak_sign",
        (1, 2, 0): "minecraft:air",
    }
    sign = next(b for b in structure.blocks if b.pos == (0, 2, 0))
    assert sign.nbt == {"id": "minecraft:sign", "is_waxed": nbt.Byte(1)}
    (stand,) = structure.entities
    assert stand.nbt == {
        "id": "minecraft:armor_stand",
        "Pos": root["Entities"].items[0]["Pos"],
        "UUID": nbt.IntArray((1, 2, 3, 4)),
    }


def _pack(values: list[int], bits: int) -> nbt.LongArray:
    """Litematica's packing: ``bits`` per value, values may span two longs."""
    total = 0
    for i, value in enumerate(values):
        total |= value << (i * bits)
    count = -(-len(values) * bits // 64)
    words = [(total >> (64 * i)) & ((1 << 64) - 1) for i in range(count)]
    return nbt.LongArray(tuple(w - (1 << 64) if w >= 1 << 63 else w for w in words))


def _int_xyz(x: int, y: int, z: int) -> dict[str, nbt.Int]:
    return {"x": nbt.Int(x), "y": nbt.Int(y), "z": nbt.Int(z)}


def test_litematic_regions_merge_with_negative_sizes_and_spanning_values(tmp_path: Path) -> None:
    names = ["minecraft:air", "minecraft:stone", "minecraft:dirt", "minecraft:oak_planks", "minecraft:glass"]
    palette = nbt.NbtList(nbt.TAG_COMPOUND, tuple({"Name": n} for n in names))
    # Region "a": position (3, 0, 0) with size (-4, 1, 6), so it covers x 0..3 and z 0..5: 24 values of 3 bits, the
    # 22nd of which spans two longs.
    a_values = [(i % 4) + 1 for i in range(24)]
    region_a = {
        "Position": _int_xyz(3, 0, 0),
        "Size": _int_xyz(-4, 1, 6),
        "BlockStatePalette": palette,
        "BlockStates": _pack(a_values, 3),
        "TileEntities": nbt.NbtList(nbt.TAG_COMPOUND, ({**_int_xyz(0, 0, 5), "id": "minecraft:chest"},)),
        "Entities": nbt.NbtList(
            nbt.TAG_COMPOUND,
            (
                {
                    "id": "minecraft:armor_stand",
                    "Pos": nbt.NbtList(nbt.TAG_DOUBLE, (nbt.Double(-0.5), nbt.Double(0.0), nbt.Double(0.5))),
                },
            ),
        ),
    }
    # Region "b": one glass block above, at (1, 2, 1).
    region_b = {
        "Position": _int_xyz(1, 2, 1),
        "Size": _int_xyz(1, 1, 1),
        "BlockStatePalette": palette,
        "BlockStates": _pack([4], 3),
    }
    root = {
        "MinecraftDataVersion": nbt.Int(DATA_VERSION),
        "Version": nbt.Int(7),
        "Regions": {"a": region_a, "b": region_b},
    }
    path = tmp_path / "two.litematic"
    path.write_bytes(gzip_stored(nbt.encode(root)))
    source = read_source(path)
    structure = source.structure
    assert structure.size == (4, 3, 6)
    assert source.notes == ("merged 2 regions into one room",)
    states = {b.pos: b.state.name for b in structure.blocks}
    for index, value in enumerate(a_values):
        assert states[(index % 4, 0, index // 4)] == names[value]
    assert states[(1, 2, 1)] == "minecraft:glass"
    assert (0, 1, 0) not in states  # covered by no region: the template leaves the world there as it is
    chest = next(b for b in structure.blocks if b.pos == (0, 0, 5))
    assert chest.nbt == {"id": "minecraft:chest"}
    # Entities are relative to the region's position (3, 0, 0), not its lowest corner.
    (stand,) = structure.entities
    assert stand.pos == (2.5, 0.0, 0.5) and stand.block_pos == (2, 0, 0)


def test_litematic_with_short_block_states_is_refused(tmp_path: Path) -> None:
    region = {
        "Position": _int_xyz(0, 0, 0),
        "Size": _int_xyz(8, 8, 8),
        "BlockStatePalette": nbt.NbtList(nbt.TAG_COMPOUND, ({"Name": "minecraft:air"},)),
        "BlockStates": nbt.LongArray((0,)),
    }
    path = tmp_path / "short.litematic"
    path.write_bytes(gzip_stored(nbt.encode({"Version": nbt.Int(7), "Regions": {"r": region}})))
    with pytest.raises(StructureError, match="shorter than the region"):
        read_source(path)


@pytest.mark.parametrize(
    ("root", "message"),
    [
        (
            {"Version": nbt.Int(1)},
            "version 1 is not supported \\(5 to 7\\): it stores Minecraft 1.12 block states",
        ),
        ({"Version": nbt.Int(4)}, "version 4 is not supported"),
        ({"Version": nbt.Int(8)}, "version 8 is not supported \\(5 to 7\\): it is newer than this reader"),
        ({}, "states no Litematica Version"),
    ],
)
def test_litematic_versions_outside_5_to_7_are_refused(tmp_path: Path, root: dict, message: str) -> None:
    # Version 1 wraps block entities in TileNBT; reading it as a current file would drop their data silently.
    region = {
        "Position": _int_xyz(0, 0, 0),
        "Size": _int_xyz(1, 1, 1),
        "BlockStatePalette": nbt.NbtList(nbt.TAG_COMPOUND, ({"Name": "minecraft:chest"},)),
        "BlockStates": nbt.LongArray((0,)),
        "TileEntities": nbt.NbtList(
            nbt.TAG_COMPOUND, ({**_int_xyz(0, 0, 0), "TileNBT": {"id": "minecraft:chest"}},)
        ),
    }
    path = tmp_path / "old.litematic"
    path.write_bytes(gzip_stored(nbt.encode({**root, "Regions": {"r": region}})))
    with pytest.raises(StructureError, match=message):
        read_source(path)


@pytest.mark.parametrize(
    ("text", "parts"),
    [
        ("minecraft:air", ("minecraft:air", "", "")),
        ("minecraft:chest[facing=north]", ("minecraft:chest", "[facing=north]", "")),
        (
            'minecraft:chest[facing=north]{LootTable:"x"}',
            ("minecraft:chest", "[facing=north]", '{LootTable:"x"}'),
        ),
        ('minecraft:barrel{LootTable:"x"}', ("minecraft:barrel", "", '{LootTable:"x"}')),
    ],
)
def test_split_state_text(text: str, parts: tuple[str, str, str]) -> None:
    assert split_state_text(text) == parts


def test_unknown_format_is_refused(tmp_path: Path) -> None:
    path = write(tmp_path / "room.schematic", "x")
    with pytest.raises(StructureError, match="unknown room format"):
        read_source(path)


# --- import rules ------------------------------------------------------------------------------------------------


def test_block_remap_keeps_or_replaces_properties() -> None:
    room = small_room(
        blocks=(
            StructureBlock((0, 0, 0), BlockState.parse("src:fancy_stairs[facing=north,half=top]")),
            StructureBlock((1, 0, 0), BlockState.parse("src:pillar[axis=y]")),
        )
    )
    rules = importer.ImportRules.model_validate(
        {
            "blocks": {
                "src:fancy_stairs": "minecraft:stone_brick_stairs",
                "src:pillar": "minecraft:stone_bricks[]",
            }
        }
    )
    structure, notes = importer.apply_rules(room, rules)
    assert [str(b.state) for b in structure.blocks] == [
        "minecraft:stone_brick_stairs[facing=north,half=top]",
        "minecraft:stone_bricks",
    ]
    assert "remapped 1 x src:fancy_stairs to minecraft:stone_brick_stairs" in notes


def test_remapped_block_entities_keep_data_only_when_mapped() -> None:
    loot = {"id": "src:loot_chest", "LootTable": "minecraft:chests/simple_dungeon"}
    spawner = {"id": "src:spawner", "Delay": nbt.Short(20)}
    room = small_room(
        blocks=(
            StructureBlock((0, 0, 0), BlockState.parse("src:loot_chest[facing=west]"), loot),
            StructureBlock((1, 0, 0), BlockState("src:spawner"), spawner),
        )
    )
    rules = importer.ImportRules.model_validate(
        {
            "blocks": {"src:loot_chest": "minecraft:chest", "src:spawner": "minecraft:spawner"},
            "block_entities": {"src:loot_chest": "minecraft:chest"},
        }
    )
    structure, notes = importer.apply_rules(room, rules)
    chest, spawner_block = structure.blocks
    assert chest.nbt == {"id": "minecraft:chest", "LootTable": "minecraft:chests/simple_dungeon"}
    assert spawner_block.nbt is None
    assert any("dropped the block entity data of 1 x src:spawner" in note for note in notes)


def test_jigsaw_names_and_entities_follow_the_rules() -> None:
    zombie = StructureEntity((1.5, 1.0, 1.5), (1, 1, 1), {"id": "minecraft:zombie"})
    blocks = tuple(
        replace(b, nbt={**b.nbt, "pool": "src:halls"}) if b.state.name == "minecraft:jigsaw" else b
        for b in small_room().blocks
    )
    room = small_room(blocks=blocks, entities=(*small_room().entities, zombie))
    structure, notes = importer.apply_rules(room, RULES)
    jigsaw = next(b for b in structure.blocks if b.state.name == "minecraft:jigsaw")
    assert jigsaw.nbt is not None
    assert (jigsaw.nbt["name"], jigsaw.nbt["target"], jigsaw.nbt["pool"]) == (
        "realmforge:door",
        "realmforge:door",
        "realmforge:rooms/halls",
    )
    assert [e.id for e in structure.entities] == ["minecraft:armor_stand"]
    assert "dropped 1 x entity minecraft:zombie" in notes
    _, unmapped = importer.apply_rules(room, importer.ImportRules())
    assert "jigsaw names and pools left as they are: src:door, src:halls" in unmapped


def test_jigsaw_final_state_follows_the_block_rules() -> None:
    def room_with(final_state: str) -> Structure:
        return small_room(
            blocks=tuple(
                replace(b, nbt={**b.nbt, "final_state": final_state})
                if b.state.name == "minecraft:jigsaw"
                else b
                for b in small_room().blocks
            )
        )

    rules = importer.ImportRules.model_validate(
        {
            "blocks": {
                "src:fancy_stairs": "minecraft:stone_brick_stairs",
                "src:pillar": "minecraft:stone_bricks[]",
            }
        }
    )

    def final_state(structure: Structure) -> str:
        jigsaw = next(b for b in structure.blocks if b.state.name == "minecraft:jigsaw")
        assert jigsaw.nbt is not None
        return jigsaw.nbt["final_state"]

    stairs, notes = importer.apply_rules(room_with("src:fancy_stairs[facing=north]{Lock:1b}"), rules)
    assert final_state(stairs) == "minecraft:stone_brick_stairs[facing=north]{Lock:1b}"
    assert "remapped 1 x src:fancy_stairs to minecraft:stone_brick_stairs" in notes
    pillar, _ = importer.apply_rules(room_with("src:pillar[axis=y]"), rules)
    assert final_state(pillar) == "minecraft:stone_bricks[]"
    untouched, _ = importer.apply_rules(room_with("minecraft:air"), rules)
    assert final_state(untouched) == "minecraft:air"


def test_unknown_jigsaw_final_state_is_a_problem(repo: RepoPaths) -> None:
    room = small_room(
        blocks=tuple(
            replace(b, nbt={**b.nbt, "final_state": "src:fancy_bricks[x=y]"})
            if b.state.name == "minecraft:jigsaw"
            else b
            for b in small_room().blocks
        )
    )
    (problem,) = check_structure(room, load_vanilla(repo))
    assert problem == (
        "jigsaw 4 1 2",
        "final_state names unknown block 'src:fancy_bricks', which the game cannot place",
    )


def test_missing_data_version_is_a_problem(repo: RepoPaths) -> None:
    (problem,) = check_structure(small_room(data_version=None), load_vanilla(repo))
    assert problem[0] == "DataVersion" and problem[1].startswith("missing: the game would read the room as")


def test_rules_data_version_fills_only_a_missing_one() -> None:
    rules = RULES.model_copy(update={"data_version": DATA_VERSION})
    filled, notes = importer.apply_rules(small_room(data_version=None), rules)
    assert filled.data_version == DATA_VERSION
    assert f"the file states no data version; took {DATA_VERSION} from the rules" in notes
    kept, notes = importer.apply_rules(small_room(data_version=3955), rules)
    assert kept.data_version == 3955 and not any("data version" in note for note in notes)


def with_jigsaws(*jigsaws: tuple[tuple[int, int, int], str, str]) -> Structure:
    """The small room with its jigsaw replaced by these (position, orientation, pool)."""
    room = small_room()
    blocks = [b for b in room.blocks if b.state.name != "minecraft:jigsaw"]
    taken = {pos for pos, _, _ in jigsaws}
    blocks = [b for b in blocks if b.pos not in taken]
    for pos, orientation, pool in jigsaws:
        data = {
            "id": "minecraft:jigsaw",
            "name": "src:door",
            "target": "src:door",
            "pool": pool,
            "final_state": "src:fancy_bricks",
            "joint": "aligned",
        }
        blocks.append(
            StructureBlock(pos, BlockState.parse(f"minecraft:jigsaw[orientation={orientation}]"), data)
        )
    return replace(room, blocks=tuple(blocks))


def test_interior_jigsaws_become_their_final_state_and_connectors_get_neutral_names() -> None:
    room = with_jigsaws(
        ((4, 1, 2), "east_up", "src:halls"),  # a connector on the east face
        ((2, 3, 2), "up_north", "src:halls"),  # a shaft up through the ceiling
        ((2, 1, 2), "up_north", "src:props"),  # inside: it pulls in a prop
    )
    rules = importer.ImportRules.model_validate(
        {
            "blocks": {"src:fancy_bricks": "minecraft:stone_bricks"},
            "jigsaws": {"interior": "drop", "normalize": True},
        }
    )
    structure, notes = importer.apply_rules(room, rules)
    by_pos = {b.pos: b for b in structure.blocks}
    assert str(by_pos[2, 1, 2].state) == "minecraft:stone_bricks" and by_pos[2, 1, 2].nbt is None
    door, shaft = by_pos[4, 1, 2].nbt, by_pos[2, 3, 2].nbt
    assert door is not None and shaft is not None
    assert (door["name"], door["target"], door["pool"]) == (
        "realmforge:door",
        "realmforge:door",
        "minecraft:empty",
    )
    assert (shaft["name"], shaft["target"]) == ("realmforge:shaft", "realmforge:shaft")
    assert (
        door["final_state"] == "minecraft:stone_bricks"
    )  # the block rules still reach a connector's final_state
    assert "replaced 1 x interior jigsaw (pool src:props) with its final_state" in notes
    assert "named 2 connector(s) realmforge:door or realmforge:shaft" in notes
    assert not any(note.startswith("jigsaw names and pools left") for note in notes)

    kept, _ = importer.apply_rules(room, importer.ImportRules())  # by default nothing changes
    assert sum(b.state.name == "minecraft:jigsaw" for b in kept.blocks) == 3


def test_normalize_leaves_no_room_for_connector_rules() -> None:
    with pytest.raises(ValueError, match="normalize names every connector itself"):
        importer.ImportRules.model_validate(
            {"jigsaws": {"normalize": True}, "connectors": {"src:door": "realmforge:door"}}
        )


def test_loot_tables_are_mapped_or_dropped() -> None:
    def chest(table: str) -> dict:
        return {"id": "minecraft:chest", "LootTable": table, "LootTableSeed": nbt.Long(7)}

    room = small_room(
        blocks=(
            StructureBlock((0, 0, 0), BlockState("minecraft:chest"), chest("src:dungeon/common")),
            StructureBlock((1, 0, 0), BlockState("minecraft:chest"), chest("src:dungeon/rare")),
            StructureBlock((2, 0, 0), BlockState("minecraft:barrel"), chest("other:loot")),
            StructureBlock(
                (3, 0, 0), BlockState("minecraft:chest"), chest("minecraft:chests/simple_dungeon")
            ),
        )
    )
    rules = importer.ImportRules.model_validate(
        {"loot_tables": {"src:dungeon/rare": "minecraft:chests/simple_dungeon", "src:*": None}}
    )
    structure, notes = importer.apply_rules(room, rules)
    common, rare, other, vanilla = (b.nbt for b in structure.blocks)
    assert common == {"id": "minecraft:chest"}  # the table and its seed are gone
    assert rare == chest("minecraft:chests/simple_dungeon")  # an exact key wins over the namespace
    assert other == chest("other:loot")  # unmapped: validation reports it
    assert vanilla == chest("minecraft:chests/simple_dungeon")
    assert "dropped the loot table of 1 x container (src:dungeon/common)" in notes
    assert (
        "mapped the loot table to minecraft:chests/simple_dungeon of 1 x container (src:dungeon/rare)"
        in notes
    )


def test_unknown_loot_table_is_a_problem(repo: RepoPaths) -> None:
    loot = {"id": "minecraft:chest", "LootTable": "src:dungeon/common"}
    blocks = tuple(StructureBlock((x, 0, 0), BlockState("minecraft:chest"), loot) for x in range(2))
    (problem,) = check_structure(small_room(blocks=blocks), load_vanilla(repo))
    assert problem == (
        "at 0 0 0",
        "unknown loot table 'src:dungeon/common' (2 times): the container would stay empty; map it in the "
        "import rules' loot_tables",
    )


def test_include_and_exclude_pick_the_files() -> None:
    rules = importer.ImportRules.model_validate(
        {"include": ["dungeon/*"], "exclude": ["*/props/*", "dungeon/broken.nbt"]}
    )
    assert not rules.excludes(Path("dungeon/rooms/hall.nbt"))  # * also matches /
    assert rules.excludes(Path("dungeon/rooms/props/vase.nbt"))
    assert rules.excludes(Path("dungeon/broken.nbt"))
    assert rules.excludes(Path("igloo/hut.nbt"))  # not included
    assert not importer.ImportRules().excludes(Path("igloo/hut.nbt"))  # no include: every file


def test_rules_file_is_strict(tmp_path: Path) -> None:
    rules = write(tmp_path / "rules.yaml", "block: {a:b: minecraft:stone}\n")
    with pytest.raises(importer.RoomImportError, match="block"):
        importer.load_rules(rules)


def test_room_names_are_snake_case() -> None:
    assert importer.room_name(Path("Halls/Big Hall-2.nbt")) == Path("halls/big_hall_2")
    with pytest.raises(importer.RoomImportError):
        importer.room_name(Path("---.nbt"))


# --- import into content -----------------------------------------------------------------------------------------


def test_each_format_imports_into_a_room_that_validates(repo: RepoPaths, tmp_path: Path) -> None:
    samples = write_samples(tmp_path / "src")
    target = third_party(repo)
    results = importer.import_rooms([tmp_path / "src"], target, RULES, load_vanilla(repo), repo)
    assert all(r.written for r in results), [r.problems for r in results]
    assert sorted(p.name for p in target.iterdir()) == [
        "LICENSE",
        "NOTICE",
        "sample_lit.nbt",
        "sample_lit.yaml",
        "sample_nbt.nbt",
        "sample_nbt.yaml",
        "sample_schem.nbt",
        "sample_schem.yaml",
    ]
    content, _ = load_and_validate(repo)
    rooms = {room.id: room for room in content.rooms}
    assert set(rooms) == {
        f"third_party/sample/{name}" for name in ("sample_lit", "sample_nbt", "sample_schem")
    }
    schem = rooms["third_party/sample/sample_schem"]
    assert schem.model.source is not None
    assert (schem.model.source.file, schem.model.source.format) == (samples["schem"].name, "schem")
    assert schem.model.source.data_version == DATA_VERSION
    # A new sidecar gets draft slot tags: one connector, no role word, so a combat dead end.
    assert schem.model.pools == [] and schem.model.tags == ["combat", "dead_end"]
    sidecar = (target / "sample_schem.yaml").read_text(encoding="utf-8")
    assert sidecar.startswith("# yaml-language-server: $schema=../../../../tools/schemas/rooms.schema.json\n")

    # The three formats of one room give the same blocks. nucleation's .schem adds an empty `components` to the
    # chest (and keys of its own to entity data), which the reader keeps as it finds them.
    def blocks(name: str) -> list[StructureBlock]:
        found = rooms[f"third_party/sample/{name}"].structure.blocks
        return sorted((_without_empty_components(b) for b in found), key=lambda b: b.pos)

    assert blocks("sample_nbt") == blocks("sample_schem") == blocks("sample_lit")


def _without_empty_components(block: StructureBlock) -> StructureBlock:
    if block.nbt is None or block.nbt.get("components") != {}:
        return block
    return replace(block, nbt={k: v for k, v in block.nbt.items() if k != "components"})


def test_import_refuses_a_room_that_does_not_validate(repo: RepoPaths, tmp_path: Path) -> None:
    fancy = BlockState("src:fancy_bricks")
    room = small_room()
    room = replace(
        room, blocks=tuple(replace(b, state=fancy) if b.pos == (0, 3, 0) else b for b in room.blocks)
    )
    source = tmp_path / "fancy.nbt"
    source.write_bytes(write_structure(room))
    target = third_party(repo)
    (result,) = importer.import_rooms([source], target, RULES, load_vanilla(repo), repo)
    assert not result.written
    assert any("unknown id 'src:fancy_bricks' in registry minecraft:block" in p for p in result.problems)
    assert not (target / "fancy.nbt").exists() and not (target / "fancy.yaml").exists()
    rules = RULES.model_copy(update={"blocks": {"src:fancy_bricks": "minecraft:chiseled_stone_bricks"}})
    (result,) = importer.import_rooms([source], target, rules, load_vanilla(repo), repo)
    assert result.written


def test_reimport_keeps_pools_tags_and_weight(repo: RepoPaths, tmp_path: Path) -> None:
    source = tmp_path / "hall.nbt"
    source.write_bytes(write_structure(small_room()))
    target = third_party(repo)
    importer.import_rooms([source], target, RULES, load_vanilla(repo), repo)
    sidecar = target / "hall.yaml"
    text = sidecar.read_text(encoding="utf-8")
    assert 'tags:\n  - "hallway"\n  - "dead_end"\n' in text  # drafted from the name and the connectors
    sidecar.write_text(
        text.replace("pools: []", "pools:\n  - realmforge:rooms/halls")
        .replace('tags:\n  - "hallway"\n  - "dead_end"', "tags: [hallway]")
        .replace("weight: 1", "weight: 3"),
        encoding="utf-8",
    )
    importer.import_rooms([source], target, RULES, load_vanilla(repo), repo)
    content, _ = load_and_validate(repo)
    (room,) = content.rooms
    assert (room.model.pools, room.model.tags, room.model.weight) == (
        ["realmforge:rooms/halls"],
        ["hallway"],
        3,
    )


def test_import_into_content_needs_third_party_or_ours(repo: RepoPaths, tmp_path: Path) -> None:
    source = tmp_path / "hall.nbt"
    source.write_bytes(write_structure(small_room()))
    stone = repo.content / "rooms" / "stone"
    with pytest.raises(importer.RoomImportError, match="third-party rooms go to content/rooms/third_party"):
        importer.import_rooms([source], stone, RULES, load_vanilla(repo), repo)
    assert not stone.exists()
    with pytest.raises(importer.RoomImportError, match="do not go into third_party"):
        importer.import_rooms([source], third_party(repo), RULES, load_vanilla(repo), repo, ours=True)
    (result,) = importer.import_rooms([source], stone, RULES, load_vanilla(repo), repo, ours=True)
    assert result.written
    assert (stone / "hall.yaml").read_text(encoding="utf-8").endswith("  ours: true\n")
    content, _ = load_and_validate(repo)
    (room,) = content.rooms
    assert room.model.source is not None and room.model.source.ours


def test_imported_rooms_outside_third_party_must_be_ours(repo: RepoPaths) -> None:
    sidecar = "source:\n  file: hall.schem\n  format: schem\n  data_version: 4790\n"
    add_room(repo, "stone/hall", sidecar=sidecar)
    third_party(repo)
    add_room(repo, "third_party/sample/hall", sidecar=sidecar + "  ours: true\n")
    found = problems(repo)
    assert any(
        p.startswith("content/rooms/stone/hall.yaml: source: an imported room lives in") for p in found
    )
    assert (
        "content/rooms/third_party/sample/hall.yaml: source.ours: a room in third_party/ is not ours" in found
    )


def test_import_refuses_a_room_without_a_data_version_unless_the_rules_give_one(
    repo: RepoPaths, tmp_path: Path
) -> None:
    source = sponge_v1(tmp_path / "old.schem")
    target = third_party(repo)
    (result,) = importer.import_rooms([source], target, RULES, load_vanilla(repo), repo)
    assert not result.written
    assert any(p.startswith("DataVersion: missing") for p in result.problems)
    assert not (target / "old.nbt").exists()

    rules = RULES.model_copy(update={"data_version": DATA_VERSION})
    (result,) = importer.import_rooms([source], target, rules, load_vanilla(repo), repo)
    assert result.written, result.problems
    assert read_structure((target / "old.nbt").read_bytes()).data_version == DATA_VERSION
    assert f"  data_version: {DATA_VERSION}\n" in (target / "old.yaml").read_text(encoding="utf-8")
    content, _ = load_and_validate(repo)
    (room,) = content.rooms
    assert room.model.source is not None and room.model.source.data_version == DATA_VERSION


def test_rules_data_version_is_a_positive_int(tmp_path: Path) -> None:
    rules = write(tmp_path / "rules.yaml", "data_version: 0\n")
    with pytest.raises(importer.RoomImportError, match="data_version"):
        importer.load_rules(rules)
    assert importer.load_rules(write(rules, f"data_version: {DATA_VERSION}\n")).data_version == DATA_VERSION


# --- review decisions at import ------------------------------------------------------------------------------------


def test_import_skips_rooms_a_review_rejects(repo: RepoPaths, tmp_path: Path) -> None:
    for name in ("hall", "corridor", "crypt"):
        (tmp_path / "src" / f"{name}.nbt").parent.mkdir(parents=True, exist_ok=True)
        (tmp_path / "src" / f"{name}.nbt").write_bytes(write_structure(small_room()))
    target = third_party(repo)
    write(target / "review.yaml", "hall: reject\ncrypt: keep\n")
    # A review further up keys rooms by their path below its own folder; the nearest decision counts.
    write(
        repo.content / "rooms" / "third_party" / "review.yaml",
        "sample/corridor: reject\nsample/crypt: reject\n",
    )
    results = {
        r.source.stem: r
        for r in importer.import_rooms([tmp_path / "src"], target, RULES, load_vanilla(repo), repo)
    }
    assert results["hall"].rejected_by == target / "review.yaml" and not results["hall"].written
    assert results["hall"].structure is None
    assert results["corridor"].rejected_by == repo.content / "rooms" / "third_party" / "review.yaml"
    assert results["crypt"].written and results["crypt"].rejected_by is None
    assert sorted(p.name for p in target.glob("*.nbt")) == ["crypt.nbt"]


def test_import_reports_a_broken_review(repo: RepoPaths, tmp_path: Path) -> None:
    source = tmp_path / "hall.nbt"
    source.write_bytes(write_structure(small_room()))
    target = third_party(repo)
    write(target / "review.yaml", "hall: maybe\n")
    with pytest.raises(
        importer.RoomImportError, match=r"review\.yaml: hall: Input should be 'keep' or 'reject'"
    ):
        importer.import_rooms([source], target, RULES, load_vanilla(repo), repo)


def test_import_refuses_two_sources_with_one_name(repo: RepoPaths, tmp_path: Path) -> None:
    for name in ("Hall.nbt", "hall.schem"):
        (tmp_path / name).write_bytes(b"")
    with pytest.raises(importer.RoomImportError, match="would both become"):
        importer.import_rooms([tmp_path], third_party(repo), RULES, load_vanilla(repo), repo)


def test_import_skips_excluded_and_reports_corrupt_files(repo: RepoPaths, tmp_path: Path) -> None:
    source = tmp_path / "src"
    (source / "props").mkdir(parents=True)
    for name in ("hall.nbt", "props/hall.nbt"):  # the excluded one would collide with hall otherwise
        (source / name).write_bytes(write_structure(small_room()))
    (source / "broken.nbt").write_bytes(corrupt(write_structure(small_room())))
    target = third_party(repo)
    rules = RULES.model_copy(update={"exclude": ["props/*"]})
    results = {
        r.source.relative_to(source).as_posix(): r
        for r in importer.import_rooms([source], target, rules, load_vanilla(repo), repo)
    }
    assert results["props/hall.nbt"].excluded and not results["props/hall.nbt"].written
    assert results["hall.nbt"].written
    broken = results["broken.nbt"]
    assert not broken.written and broken.structure is None
    assert broken.problems[0].startswith("not a usable room file: broken gzip data")
    assert sorted(p.name for p in target.glob("*.nbt")) == ["hall.nbt"]


def test_import_leaves_out_rooms_above_max_size(repo: RepoPaths, tmp_path: Path) -> None:
    source = tmp_path / "hall.nbt"
    source.write_bytes(write_structure(small_room()))  # 5 x 4 x 5
    target = third_party(repo)
    (result,) = importer.import_rooms(
        [source], target, RULES.model_copy(update={"max_size": 4}), load_vanilla(repo), repo
    )
    assert result.excluded and not result.written and not (target / "hall.nbt").exists()
    (result,) = importer.import_rooms(
        [source], target, RULES.model_copy(update={"max_size": 5}), load_vanilla(repo), repo
    )
    assert result.written


# --- draft slot tags ---------------------------------------------------------------------------------------------


def jigsaw_room(size: tuple[int, int, int], *faces: str) -> Structure:
    """An empty room of ``size`` with one connector in the middle of each face."""
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
            pos, BlockState.parse(f"minecraft:jigsaw[orientation={orientation}]"), {"id": "minecraft:jigsaw"}
        )
        for pos, orientation in (spots[face] for face in faces)
    )
    return Structure(size, DATA_VERSION, blocks)


@pytest.mark.parametrize(
    ("name", "size", "faces", "tags"),
    [
        ("library", (13, 6, 13), ("west",), ["combat", "dead_end"]),
        ("library", (13, 6, 13), ("west", "east"), ["combat", "straight"]),
        ("library", (13, 6, 13), ("north", "east"), ["combat", "curved"]),
        ("library", (13, 6, 13), ("north", "east", "south"), ["combat", "t_junction"]),
        ("library", (13, 6, 13), ("north", "east", "south", "west"), ["combat", "crossing"]),
        ("corridor_4", (25, 5, 8), ("west", "east"), ["hallway", "straight"]),  # the role word
        ("d1", (9, 5, 5), ("west", "east"), ["hallway", "straight"]),  # 5 wide across the passage
        ("c1", (6, 5, 9), ("west", "east"), ["combat", "straight"]),  # 9 wide across it, though 6 long
        ("c1", (6, 5, 9), ("north", "south"), ["hallway", "straight"]),
        ("e1", (7, 5, 12), ("south", "east"), ["hallway", "curved"]),
        ("d1", (5, 5, 5), ("west",), ["combat", "dead_end"]),  # narrow, but a dead end
        ("treasure/egg", (13, 8, 13), ("west",), ["treasure", "dead_end"]),
        ("dungeon_bossroom", (32, 23, 31), ("north",), ["boss", "dead_end"]),
        ("boss_hallway", (7, 5, 20), ("north", "south"), ["boss", "straight"]),  # the first role wins
        ("entrances/entrance_1", (7, 6, 7), ("down",), ["entrance", "vertical"]),
        ("stairway/fall_01", (5, 16, 5), ("up", "down"), ["stairs", "vertical"]),
        (
            "lobby_01",
            (13, 6, 13),
            ("north", "east", "south", "west", "up"),
            ["entrance", "crossing", "vertical"],
        ),
    ],
)
def test_slot_tags(name: str, size: tuple[int, int, int], faces: tuple[str, ...], tags: list[str]) -> None:
    assert slot_tags(jigsaw_room(size, *faces), name) == tags


def test_interior_jigsaws_are_no_connectors() -> None:
    room = jigsaw_room((13, 6, 13), "west")
    inside = StructureBlock((6, 1, 6), BlockState.parse("minecraft:jigsaw[orientation=up_north]"), {})
    assert slot_tags(replace(room, blocks=(*room.blocks, inside)), "x") == ["combat", "dead_end"]


# --- the rooms content type --------------------------------------------------------------------------------------


def add_room(
    repo: RepoPaths, room_id: str, structure: Structure | None = None, sidecar: str = "weight: 1\n"
) -> Path:
    path = repo.content / "rooms" / f"{room_id}.nbt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(write_structure(structure or importer.apply_rules(small_room(), RULES)[0]))
    write(path.with_suffix(".yaml"), sidecar)
    return path


def problems(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as failed:
        load_and_validate(repo)
    return [str(p) for p in failed.value.problems]


def test_valid_room_generates_its_structure(repo: RepoPaths) -> None:
    add_room(repo, "stone/hall")
    content, _ = load_and_validate(repo)
    files = generate_all(content, repo)
    target = repo.mod_generated / "resources/data/realmforge/structure/rooms/stone/hall.nbt"
    assert files[target] == write_structure(content.rooms[0].structure)
    assert repo.schemas / "rooms.schema.json" in files


def test_rooms_get_expectations_for_the_game_test(repo: RepoPaths) -> None:
    add_room(repo, "stone/hall")  # 5 x 4 x 5: one jigsaw, 48 air blocks
    content, _ = load_and_validate(repo)
    files = generate_all(content, repo)
    java = (
        repo.qa_generated
        / "java/io/github/matejkadlec/realmforge/qa/generated/GeneratedRoomExpectations.java"
    )
    assert 'new RoomExpectation("realmforge:rooms/stone/hall", 5, 4, 5, 1, 48))' in files[java].decode()


def test_third_party_rooms_ship_their_notices(repo: RepoPaths) -> None:
    folder = third_party(repo)
    add_room(repo, "third_party/sample/hall")
    third_party(repo, "unused")  # a source without rooms ships nothing
    content, _ = load_and_validate(repo)
    files = generate_all(content, repo)
    meta = repo.mod_generated / "resources/META-INF/third_party"
    assert files[meta / "sample" / "LICENSE"] == (folder / "LICENSE").read_bytes()
    assert files[meta / "sample" / "NOTICE"] == (folder / "NOTICE").read_bytes()
    assert not [path for path in files if path.is_relative_to(meta / "unused")]


def test_room_needs_both_files(repo: RepoPaths) -> None:
    add_room(repo, "hall").with_suffix(".yaml").unlink()
    write(repo.content / "rooms" / "lonely.yaml", "weight: 1\n")
    found = problems(repo)
    assert "content/rooms/hall.nbt: the room has no .yaml sidecar next to it" in found
    assert "content/rooms/lonely.yaml: the room has no .nbt template next to it" in found


@pytest.mark.parametrize("room_id", ["Stone/hall", "hall-2", "review"])
def test_room_paths_are_snake_case(repo: RepoPaths, room_id: str) -> None:
    add_room(repo, room_id)
    assert any("must be lowercase snake_case" in p for p in problems(repo))


def test_sidecar_is_validated(repo: RepoPaths) -> None:
    add_room(repo, "hall", sidecar="weight: 0\npools: [halls]\ncolour: red\n")
    add_room(
        repo,
        "old",
        sidecar="source:\n  file: old.schem\n  format: schem\n  data_version: null\n  ours: true\n",
    )
    found = problems(repo)
    assert any("content/rooms/hall.yaml: weight:" in p for p in found)
    assert any("content/rooms/hall.yaml: pools.0:" in p for p in found)
    assert any("content/rooms/hall.yaml: colour:" in p for p in found)
    assert any("content/rooms/old.yaml: source.data_version:" in p for p in found)


def test_third_party_rooms_need_license_and_notice(repo: RepoPaths) -> None:
    add_room(repo, "third_party/sample/hall")
    found = problems(repo)
    assert any("third-party rooms need the source's LICENSE" in p for p in found)
    assert any("third-party rooms need the source's NOTICE" in p for p in found)
    write(repo.content / "rooms" / "LICENSE", "x\n")
    assert any("LICENSE belongs directly in content/rooms/third_party/<source>/" in p for p in problems(repo))


LGPL = (
    "                   GNU LESSER GENERAL PUBLIC LICENSE\n                       Version 3, 29 June 2007\n"
)
GPL = "                    GNU GENERAL PUBLIC LICENSE\n                       Version 3, 29 June 2007\n"


def test_lgpl_rooms_carry_the_gpl_text_as_copying(repo: RepoPaths) -> None:
    folder = third_party(repo, "lgpl")
    write(folder / "LICENSE", LGPL)
    add_room(repo, "third_party/lgpl/hall")
    assert problems(repo) == [
        "content/rooms/third_party/lgpl/: an LGPL-3.0 source needs the GPL-3.0 text as COPYING (ADR 0028)"
    ]
    write(folder / "COPYING", LGPL)
    assert problems(repo) == ["content/rooms/third_party/lgpl/COPYING: COPYING must hold the GPL-3.0 text"]
    write(folder / "COPYING", GPL)
    content, _ = load_and_validate(repo)
    files = generate_all(content, repo)
    assert files[repo.mod_generated / "resources/META-INF/third_party/lgpl/COPYING"] == GPL.encode()


def test_only_lgpl_sources_carry_copying(repo: RepoPaths) -> None:
    folder = third_party(repo)  # MIT
    add_room(repo, "third_party/sample/hall")
    write(folder / "COPYING", GPL)
    assert problems(repo) == [
        "content/rooms/third_party/sample/COPYING: only an LGPL-3.0 source (its LICENSE) carries COPYING"
    ]
    write(repo.content / "rooms" / "COPYING", GPL)
    assert any("COPYING belongs directly in content/rooms/third_party/<source>/" in p for p in problems(repo))


def test_room_template_is_checked(repo: RepoPaths) -> None:
    room = small_room()
    jigsaw_moved = tuple(
        replace(b, pos=(3, 1, 2)) if b.state.name == "minecraft:jigsaw" else b
        for b in room.blocks
        if b.pos != (3, 1, 2)
    )
    add_room(repo, "bad_jigsaw", replace(room, blocks=jigsaw_moved))
    add_room(
        repo,
        "unknown",
        replace(room, entities=(StructureEntity((1.5, 1, 1.5), (1, 1, 1), {"id": "src:ghost"}),)),
    )
    add_room(repo, "too_big", replace(room, size=(49, 4, 5)))
    add_room(repo, "too_new", replace(room, data_version=DATA_VERSION + 1))
    add_room(repo, "no_version", replace(room, data_version=None))
    found = problems(repo)
    assert any(
        "bad_jigsaw.nbt: jigsaw 3 1 2: faces east but is not on the room's east face" in p for p in found
    )
    assert any(
        "unknown.nbt: at 1 1 1: unknown id 'src:ghost' in registry minecraft:entity_type" in p for p in found
    )
    assert any("too_big.nbt: size: 49 4 5" in p for p in found)
    assert any(f"too_new.nbt: DataVersion: {DATA_VERSION + 1} is newer" in p for p in found)
    assert any("no_version.nbt: DataVersion: missing" in p for p in found)


def test_corrupt_template_is_a_validation_problem(repo: RepoPaths) -> None:
    template = add_room(repo, "corrupt")
    template.write_bytes(corrupt(template.read_bytes()))
    (problem,) = problems(repo)
    assert problem.startswith("content/rooms/corrupt.nbt: not a usable structure template: broken gzip data")


@pytest.mark.parametrize(
    ("pos", "block_pos", "inside"),
    [
        ((2.5, 1.0, 2.5), (2, 1, 2), True),
        ((5.0, 1.0, 2.5), (5, 1, 2), True),  # on the far face, like an item frame on a wall
        ((9.5, 1.0, 2.5), (2, 1, 2), False),  # vanilla places it at pos, whatever blockPos says
        ((2.5, -0.5, 2.5), (2, 1, 2), False),
        ((float("nan"), 1.0, 2.5), (2, 1, 2), False),
        ((2.5, 1.0, float("inf")), (2, 1, 2), False),
        ((2.5, 1.0, 2.5), (2, 9, 2), False),
    ],
)
def test_entities_must_lie_inside_the_room(
    repo: RepoPaths, pos: tuple, block_pos: tuple, inside: bool
) -> None:
    stand = StructureEntity(pos, block_pos, {"id": "minecraft:armor_stand"})
    found = check_structure(small_room(entities=(stand,)), load_vanilla(repo))
    assert (found == []) is inside
    if not inside:
        assert "lies outside the room's size 5 4 5" in found[0][1]


def test_unknown_block_in_an_old_room_hints_at_renames(repo: RepoPaths) -> None:
    room = small_room(data_version=3955)
    room = replace(
        room,
        blocks=tuple(
            replace(b, state=BlockState("minecraft:grass")) if b.pos == (0, 0, 0) else b for b in room.blocks
        ),
    )
    (problem,) = check_structure(room, load_vanilla(repo))
    assert problem[1].startswith("unknown id 'minecraft:grass' in registry minecraft:block")
    assert "saved with data version 3955" in problem[1]


def test_review_keeps_name_existing_rooms(repo: RepoPaths) -> None:
    add_room(repo, "stone/hall")
    write(repo.content / "rooms" / "stone" / "review.yaml", "hall: keep\ncorridor: keep\n")
    write(repo.content / "rooms" / "review.yaml", "stone/hall: reject\n")
    assert problems(repo) == [
        "content/rooms/stone/review.yaml: corridor: kept, but there is no room content/rooms/stone/corridor.nbt"
    ]


def test_reject_decision_outlives_its_room(repo: RepoPaths) -> None:
    hall = add_room(repo, "stone/hall")
    write(repo.content / "rooms" / "stone" / "review.yaml", "hall: reject\n")
    load_and_validate(repo)
    hall.unlink()
    hall.with_suffix(".yaml").unlink()  # the rejected import is dropped; its decision stays
    content, _ = load_and_validate(repo)
    assert content.rooms == () and content.room_reviews[0].decisions == {"hall": "reject"}


def test_review_decisions_are_keep_or_reject(repo: RepoPaths) -> None:
    add_room(repo, "stone/hall")
    write(repo.content / "rooms" / "review.yaml", "stone/hall: maybe\n")
    (problem,) = problems(repo)
    assert problem.startswith("content/rooms/review.yaml: stone/hall: Input should be 'keep' or 'reject'")


def test_rooms_join_the_structure_ids(repo: RepoPaths) -> None:
    add_room(repo, "stone/hall")
    _, universe = load_and_validate(repo)
    assert universe.contains("minecraft:structure", "realmforge:rooms/stone/hall")


def marker(pos: tuple[int, int, int], name: str, mode: str = "DATA") -> StructureBlock:
    """A structure block as a template saves it, in ``mode`` with ``name`` as its metadata."""
    data = {"id": "minecraft:structure_block", "mode": mode, "metadata": name, "name": "", "author": "?"}
    return StructureBlock(pos, BlockState("minecraft:structure_block", (("mode", mode.lower()),)), data)


def with_blocks(*placed: StructureBlock) -> Structure:
    """The small room with ``placed`` instead of the blocks at their positions."""
    where = {block.pos for block in placed}
    return small_room(blocks=tuple(b for b in small_room().blocks if b.pos not in where) + placed)


def test_data_markers_must_name_known_markers(repo: RepoPaths) -> None:
    room = with_blocks(
        marker((2, 1, 2), "chest"),
        marker((3, 1, 3), "loot"),
        marker((2, 2, 2), "", mode="SAVE"),
    )
    problems = check_structure(room, load_vanilla(repo))
    assert problems == [
        ("data marker 3 1 3", "names 'loot', which is no marker: entrance, exit, mob, chest"),
        (
            "structure block 2 2 2",
            "is in SAVE mode; a room holds structure blocks only as data markers (mode DATA) naming entrance, exit, "
            "mob, chest",
        ),
    ]
    assert markers(room) == [("chest", (2, 1, 2)), ("loot", (3, 1, 3))], "only data mode makes a marker"


def test_a_room_has_one_entrance_marker_at_most(repo: RepoPaths) -> None:
    one = with_blocks(marker((2, 1, 2), "entrance"), marker((3, 1, 3), "mob"))
    assert check_structure(one, load_vanilla(repo)) == []
    two = with_blocks(marker((2, 1, 2), "entrance"), marker((3, 1, 3), "entrance"))
    assert check_structure(two, load_vanilla(repo)) == [
        ("data marker 3 1 3", "the room holds 2 entrance markers; players arrive at one")
    ]


def test_fluids_count_at_the_sides_and_floor_but_not_the_roof() -> None:
    water = BlockState("minecraft:water", (("level", "0"),))
    room = with_blocks(
        StructureBlock((0, 1, 2), water),  # west side
        StructureBlock((2, 0, 2), BlockState("minecraft:lava", (("level", "0"),))),  # floor
        StructureBlock((2, 3, 2), water),  # roof only: it cannot flow out
        StructureBlock((2, 1, 2), water),  # inside
        StructureBlock((3, 2, 4), BlockState.parse("minecraft:oak_stairs[facing=east,waterlogged=true]")),
        StructureBlock((3, 1, 0), BlockState("minecraft:seagrass")),  # always holds water
        StructureBlock((1, 2, 4), BlockState.parse("minecraft:oak_stairs[facing=east,waterlogged=false]")),
    )
    assert fluid_edges(room) == [(0, 1, 2), (2, 0, 2), (3, 1, 0), (3, 2, 4)]
