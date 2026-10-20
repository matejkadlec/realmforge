"""content/blocks/: simple full blocks with their block items (architecture 6.2)."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.formats.png import decode_rgba
from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

ASSETS = "mod/src/generated/resources/assets/realmforge"
DATA = "mod/src/generated/resources/data"
JAVA = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedBlocks.java"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedBlockExpectations.java"
)
BLOCK = (
    "id: test_rock\n"
    "name: {en_us: Test Rock}\n"
    "hardness: 4.5\n"
    "tool: pickaxe\n"
    "requires_tool: true\n"
    "tool_tier: iron\n"
    "sound: deepslate\n"
    "map_color: deepslate\n"
    "creative_tabs: [minecraft:building_blocks]\n"
)


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def test_repository_blocks_are_undervaults_stones_and_the_portal_materials() -> None:
    content, universe = load_and_validate(RepoPaths())
    hardness = {e.model.id: e.model.hardness for e in content.blocks if e.model.kind == "simple"}
    # Matěj, 2026-10-15: four stones of hardness 25, 20, 18 and 12; the common one has 20.
    stones = {"coreslate": 25, "mottlestone": 18, "siltstone": 12, "vaultstone": 20}
    # Matěj, 2026-10-20: Realmforge Stone from 8 Overworld bricks, three of them ours, with vanilla's brick hardness.
    materials = {"realmforge_stone": 3, "diorite_bricks": 1.5, "granite_bricks": 1.5, "andesite_bricks": 1.5}
    assert hardness == stones | materials
    assert universe.contains("minecraft:block", "realmforge:vaultstone")
    assert universe.contains("minecraft:item", "realmforge:vaultstone")


def test_a_block_becomes_its_registration_assets_loot_and_tags(repo: RepoPaths) -> None:
    write(repo.content / "blocks" / "test_rock.yaml", BLOCK)
    files = generate(repo)
    java = files[JAVA].decode()
    assert 'BLOCKS.registerSimpleBlock("test_rock", properties -> properties' in java
    assert ".strength(4.5F, 6.0F)" in java and ".sound(SoundType.DEEPSLATE)" in java
    assert ".mapColor(MapColor.DEEPSLATE)" in java and ".requiresCorrectToolForDrops()" in java
    assert "TEST_ROCK_ITEM = ITEMS.registerSimpleBlockItem(TEST_ROCK)" in java
    building_blocks = java.split('case "minecraft:building_blocks" -> {')[1].split("}")[0]
    assert "event.accept(TEST_ROCK_ITEM);" in building_blocks
    assert json.loads(files[f"{ASSETS}/blockstates/test_rock.json"]) == {
        "variants": {"": {"model": "realmforge:block/test_rock"}}
    }
    assert json.loads(files[f"{ASSETS}/models/block/test_rock.json"]) == {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": "realmforge:block/test_rock"},
    }
    assert (
        json.loads(files[f"{ASSETS}/items/test_rock.json"])["model"]["model"] == "realmforge:block/test_rock"
    )
    loot = json.loads(files[f"{DATA}/realmforge/loot_table/blocks/test_rock.json"])
    assert loot["pools"][0]["entries"] == [{"type": "minecraft:item", "name": "realmforge:test_rock"}]
    assert loot["pools"][0]["conditions"] == [{"condition": "minecraft:survives_explosion"}]
    assert (
        "realmforge:test_rock"
        in json.loads(files[f"{DATA}/minecraft/tags/block/mineable/pickaxe.json"])["values"]
    )
    assert json.loads(files[f"{DATA}/minecraft/tags/block/needs_iron_tool.json"]) == {
        "values": ["realmforge:test_rock"]
    }
    assert json.loads(files[f"{ASSETS}/lang/en_us.json"])["block.realmforge.test_rock"] == "Test Rock"
    assert (
        '"realmforge:test_rock",\n                    "block.realmforge.test_rock",\n'
        in files[EXPECTATIONS].decode()
    )


def test_block_textures_are_opaque_and_differ_by_id(repo: RepoPaths) -> None:
    files = generate(repo)
    vaultstone = decode_rgba(files[f"{ASSETS}/textures/block/vaultstone.png"])
    coreslate = decode_rgba(files[f"{ASSETS}/textures/block/coreslate.png"])
    assert vaultstone[0] == coreslate[0] == 16
    assert all(pixel[3] == 255 for pixel in vaultstone[2])
    assert vaultstone[2] != coreslate[2]


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (("tool: pickaxe\n", ""), "requires_tool and tool_tier need a tool"),
        (("requires_tool: true\n", ""), "a tool_tier only matters when requires_tool is true"),
        (("hardness: 4.5", "hardness: -2"), "Input should be greater than or equal to -1"),
        (("sound: deepslate", "sound: wool"), "Input should be 'stone'"),
        (("minecraft:building_blocks", "minecraft:building"), "unknown id 'minecraft:building'"),
    ],
)
def test_invalid_blocks_are_rejected(repo: RepoPaths, change: tuple[str, str], expected: str) -> None:
    write(repo.content / "blocks" / "test_rock.yaml", BLOCK.replace(*change))
    assert any(expected in p for p in problems_for(repo)), problems_for(repo)


def test_a_block_and_an_item_cannot_share_an_id(repo: RepoPaths) -> None:
    write(repo.content / "blocks" / "test_item.yaml", BLOCK.replace("test_rock", "test_item"))
    assert any(
        "the block's item would clash with the item in content/items/test_item.yaml" in p
        for p in problems_for(repo)
    )


PORTAL = "id: test_portal\nname: {en_us: Test Portal}\nkind: dungeon_portal\nhardness: 5\n"
EXIT = "id: test_exit\nname: {en_us: Test Exit}\nkind: dungeon_exit\nhardness: 1.5\n"


def test_a_block_kind_comes_from_its_feature_with_its_own_blockstate(repo: RepoPaths) -> None:
    (repo.content / "blocks" / "dungeon_exit.yaml").unlink()  # one exit at most
    write(repo.content / "blocks" / "test_rock.yaml", BLOCK)
    write(repo.content / "blocks" / "test_portal.yaml", PORTAL)
    write(repo.content / "blocks" / "test_exit.yaml", EXIT)
    files = generate(repo)
    java = files[JAVA].decode()
    assert "import io.github.matejkadlec.realmforge.world.BlockKinds;" in java
    assert 'BLOCKS.registerSimpleBlock("test_rock", properties -> properties' in java
    assert (
        'BLOCKS.registerBlock("test_portal",\n'
        '                    properties -> BlockKinds.create("dungeon_portal", properties),\n'
        "                    properties -> properties\n"
    ) in java
    assert 'properties -> BlockKinds.create("dungeon_exit", properties)' in java
    assert "TEST_PORTAL_ITEM = ITEMS.registerSimpleBlockItem(TEST_PORTAL)" in java

    assert json.loads(files[f"{ASSETS}/blockstates/test_portal.json"]) == {
        "variants": {
            "open=false": {"model": "realmforge:block/test_portal"},
            "open=true": {"model": "realmforge:block/test_portal_open"},
        }
    }
    assert json.loads(files[f"{ASSETS}/models/block/test_portal_open.json"]) == {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": "realmforge:block/test_portal_open"},
    }
    assert (
        files[f"{ASSETS}/textures/block/test_portal.png"]
        != files[f"{ASSETS}/textures/block/test_portal_open.png"]
    )
    assert json.loads(files[f"{ASSETS}/blockstates/test_exit.json"]) == {
        "variants": {"": {"model": "realmforge:block/test_exit"}}
    }
    assert json.loads(files[f"{ASSETS}/models/block/test_exit.json"]) == {
        "parent": "minecraft:block/carpet",
        "textures": {"wool": "realmforge:block/test_exit"},
    }
    assert f"{DATA}/realmforge/loot_table/blocks/test_portal.json" in files


def test_simple_blocks_alone_need_no_block_kinds(repo: RepoPaths) -> None:
    for path in (repo.content / "blocks").glob("*.yaml"):
        if "kind:" in path.read_text(encoding="utf-8"):
            path.unlink()
    write(repo.content / "blocks" / "test_rock.yaml", BLOCK)
    assert "BlockKinds" not in generate(repo)[JAVA].decode()


def test_the_game_uses_one_dungeon_exit(repo: RepoPaths) -> None:
    write(repo.content / "blocks" / "test_exit.yaml", EXIT)
    problems = problems_for(repo)
    assert any("test_exit.yaml" in p and "kind" in p and "one dungeon_exit block" in p for p in problems), (
        problems
    )


def test_an_unknown_kind_is_a_problem(repo: RepoPaths) -> None:
    write(repo.content / "blocks" / "test_rock.yaml", BLOCK + "kind: teleporter\n")
    assert any("test_rock.yaml" in p and "kind" in p for p in problems_for(repo))


def test_the_realm_portals_blocks_have_their_own_shapes_loot_and_items(repo: RepoPaths) -> None:
    files = generate(repo)
    java = files[JAVA].decode()
    # The portal block: no item, no loot table.
    assert 'properties -> BlockKinds.create("realm_portal", properties)' in java
    assert ".strength(-1.0F, 0.0F)" in java and ".noLootTable()" in java
    assert "REALM_PORTAL_ITEM" not in java
    assert f"{ASSETS}/items/realm_portal.json" not in files
    assert f"{DATA}/realmforge/loot_table/blocks/realm_portal.json" not in files
    assert json.loads(files[f"{ASSETS}/blockstates/realm_portal.json"]) == {
        "variants": {
            "axis=x": {"model": "realmforge:block/realm_portal_ns"},
            "axis=z": {"model": "realmforge:block/realm_portal_ew"},
        }
    }
    pane = json.loads(files[f"{ASSETS}/models/block/realm_portal_ns.json"])["elements"][0]
    assert (pane["from"], pane["to"]) == ([0, 0, 6], [16, 16, 10])
    assert {face["tintindex"] for face in pane["faces"].values()} == {0}
    # See-through, so the game renders it translucent.
    _, _, pixels = decode_rgba(files[f"{ASSETS}/textures/block/realm_portal.png"])
    assert all(alpha < 255 for *_, alpha in pixels)
    assert '"realmforge:realm_portal",\n                    "block.realmforge.realm_portal",' in (
        files[EXPECTATIONS].decode()
    )
    # The frame's blocks: both states look alike, and only a block without the generated flag drops itself.
    for block in ("realmforge_bricks", "realmforge_sealstone"):
        assert json.loads(files[f"{ASSETS}/blockstates/{block}.json"]) == {
            "variants": {
                "generated=false": {"model": f"realmforge:block/{block}"},
                "generated=true": {"model": f"realmforge:block/{block}"},
            }
        }
        pool = json.loads(files[f"{DATA}/realmforge/loot_table/blocks/{block}.json"])["pools"][0]
        assert {
            "block": f"realmforge:{block}",
            "condition": "minecraft:block_state_property",
            "properties": {"generated": "false"},
        } in pool["conditions"]
        assert f"{block.upper()}_ITEM = ITEMS.registerSimpleBlockItem" in java


def test_the_realm_portal_needs_all_three_kinds(repo: RepoPaths) -> None:
    (repo.content / "blocks" / "realmforge_sealstone.yaml").unlink()
    problems = problems_for(repo)
    assert any(
        "kind: the realm portal needs a block of each of" in p and "none is realmforge_sealstone" in p
        for p in problems
    ), problems


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        ("hardness: -0.5\n", "hardness is 0 or more, or -1"),
        ("hardness: -1\ntool: pickaxe\n", "a block nothing breaks needs no tool"),
    ],
)
def test_unbreakable_blocks_are_minus_one_and_need_no_tool(
    repo: RepoPaths, change: str, expected: str
) -> None:
    write(repo.content / "blocks" / "test_rock.yaml", "id: test_rock\nname: {en_us: Test Rock}\n" + change)
    assert any("test_rock.yaml" in p and expected in p for p in problems_for(repo))


def test_a_block_without_an_item_has_no_creative_tab(repo: RepoPaths) -> None:
    portal = repo.content / "blocks" / "realm_portal.yaml"
    portal.write_text(
        portal.read_text(encoding="utf-8") + "creative_tabs: [minecraft:functional_blocks]\n",
        encoding="utf-8",
    )
    assert any(
        "realm_portal.yaml" in p
        and "a realm_portal block has no item, so no creative_tabs and no recipe" in p
        for p in problems_for(repo)
    )
