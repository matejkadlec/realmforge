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


def test_repository_blocks_are_undervaults_stones() -> None:
    content, universe = load_and_validate(RepoPaths())
    hardness = {e.model.id: e.model.hardness for e in content.blocks}
    # Matěj, 2026-10-15: four stones of hardness 25, 20, 18 and 12; the common one has 20.
    assert hardness == {"coreslate": 25, "mottlestone": 18, "siltstone": 12, "vaultstone": 20}
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
    assert 'case "minecraft:building_blocks" -> {\n                event.accept(TEST_ROCK_ITEM);' in java
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
        (("hardness: 4.5", "hardness: -1"), "Input should be greater than or equal to 0"),
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
