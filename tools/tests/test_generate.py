from __future__ import annotations

import gzip
import json
import struct
import zlib

import pytest

from realmforge_tools.formats import nbt
from realmforge_tools.formats.png import PNG_SIGNATURE
from realmforge_tools.generate import generate_all, sync
from realmforge_tools.generate.gametest import AIR, SPIKE_ROOMS, room_blocks
from realmforge_tools.generate.textures import placeholder_texture
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import DATA_VERSION, RepoPaths
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

GENERATED_JAVA = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedItems.java"
EXPECTATIONS_JAVA = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedItemExpectations.java"
)
ASSETS = "mod/src/generated/resources/assets/realmforge"


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def test_committed_generated_files_are_up_to_date() -> None:
    paths = RepoPaths()
    content, _ = load_and_validate(paths)
    result = sync(generate_all(content, paths), paths, check=True)
    assert result.clean, "run `uv run rf gen`:\n" + "\n".join(result.lines())


def test_generation_is_deterministic(repo: RepoPaths) -> None:
    assert generate(repo) == generate(repo)


def test_item_outputs(repo: RepoPaths) -> None:
    files = generate(repo)
    java = files[GENERATED_JAVA].decode()
    assert 'ITEMS.registerSimpleItem("test_item", properties -> properties.stacksTo(16));' in java
    assert 'case "minecraft:ingredients" -> {' in java
    assert "event.accept(TEST_ITEM);" in java

    definition = json.loads(files[f"{ASSETS}/items/test_item.json"])
    assert definition == {"model": {"type": "minecraft:model", "model": "realmforge:item/test_item"}}

    model = json.loads(files[f"{ASSETS}/models/item/test_item.json"])
    assert model == {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "realmforge:item/test_item"},
    }

    assert json.loads(files[f"{ASSETS}/lang/en_us.json"])["item.realmforge.test_item"] == "Test Item"

    expectations = files[EXPECTATIONS_JAVA].decode()
    assert '"realmforge:test_item",' in expectations
    assert '"item.realmforge.test_item",' in expectations
    assert 'List.of("minecraft:ingredients")' in expectations


def test_item_use_and_flavor_become_tooltip_lines_and_a_use_tag(repo: RepoPaths) -> None:
    write(
        repo.content / "items" / "wiper.yaml",
        "id: wiper\nname: {en_us: Wiper}\nuse: unlearn_stat_points\nflavor: {en_us: It smells of chalk.}\n",
    )
    files = generate(repo)
    java = files[GENERATED_JAVA].decode()
    wiper = java[java.index('registerSimpleItem("wiper"') :]
    wiper = wiper[: wiper.index(";")]
    assert ".component(DataComponents.LORE, new ItemLore(List.of(" in wiper
    assert 'Component.translatable("item_use.realmforge.unlearn_stat_points")' in wiper
    assert wiper.index("item_use.realmforge") < wiper.index('"item.realmforge.wiper.flavor"')
    assert "import net.minecraft.world.item.component.ItemLore;" in java
    lang = json.loads(files[f"{ASSETS}/lang/en_us.json"])
    assert lang["item.realmforge.wiper.flavor"] == "It smells of chalk."
    tag = json.loads(
        files["mod/src/generated/resources/data/realmforge/tags/item/use/unlearn_stat_points.json"]
    )
    assert tag == {"values": ["realmforge:oblivion_scroll", "realmforge:wiper"]}
    assert '"realmforge:use/unlearn_stat_points")' in files[EXPECTATIONS_JAVA].decode()


def test_items_without_lore_import_nothing_for_it(repo: RepoPaths) -> None:
    # The Scroll of Oblivion has a use line, and a realm's signet names its realm: without both, no item has lore.
    for path in (
        "items/oblivion_scroll.yaml",
        "items/undervault_signet.yaml",
        "realms/stone.yaml",
        "dimensions/stone.yaml",
    ):
        (repo.content / path).unlink()
    files = generate(repo)
    assert "ItemLore" not in files[GENERATED_JAVA].decode()
    assert not any("/tags/item/use/" in path for path in files)


def test_an_item_use_needs_its_tooltip_line(repo: RepoPaths) -> None:
    (repo.content / "texts" / "item_use.yaml").unlink()
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    problems = [str(problem) for problem in excinfo.value.problems]
    assert any("oblivion_scroll.yaml" in p and "needs its tooltip line" in p for p in problems), problems


def test_default_stack_size_is_not_emitted(repo: RepoPaths) -> None:
    write(repo.content / "items" / "plain.yaml", "id: plain\nname:\n  en_us: Plain\n")
    java = generate(repo)[GENERATED_JAVA].decode()
    assert 'ITEMS.registerSimpleItem("plain");' in java


def test_english_is_the_only_lang_file(repo: RepoPaths) -> None:
    files = generate(repo)
    assert [path for path in files if path.startswith(f"{ASSETS}/lang/")] == [f"{ASSETS}/lang/en_us.json"]


def test_sync_writes_updates_and_removes_stale_files(repo: RepoPaths) -> None:
    content, _ = load_and_validate(repo)
    files = generate_all(content, repo)
    first = sync(files, repo)
    assert first.added and not first.changed and not first.removed

    stale = write(repo.mod_generated / "resources" / "assets" / "realmforge" / "lang" / "xx_xx.json", "{}")
    edited = repo.root / GENERATED_JAVA
    edited.write_text("// hand edit\n")
    check = sync(files, repo, check=True)
    assert check.removed == [repo.rel(stale)]
    assert check.changed == [GENERATED_JAVA]
    assert stale.exists()  # check mode writes nothing

    sync(files, repo)
    assert not stale.exists()
    assert sync(files, repo, check=True).clean


def test_placeholder_texture_is_a_16x16_rgba_png() -> None:
    png = placeholder_texture("realmforge:test_item")
    assert png.startswith(PNG_SIGNATURE)
    width, height, depth, color_type = struct.unpack(">IIBB", png[16:26])
    assert (width, height, depth, color_type) == (16, 16, 8, 6)
    # IDAT is the second chunk; it must decompress to 16 rows of (filter byte + 16 RGBA pixels).
    idat_length = struct.unpack(">I", png[33:37])[0]
    raw = zlib.decompress(png[41 : 41 + idat_length])
    assert len(raw) == 16 * (1 + 16 * 4)


def test_placeholder_colors_differ_per_id() -> None:
    assert placeholder_texture("realmforge:a") != placeholder_texture("realmforge:b")


def test_gametest_platform_structure(repo: RepoPaths) -> None:
    data = generate(repo)["qa-mod/src/generated/resources/data/realmforge_qa/structure/platform_5x5.nbt"]
    name, root = nbt.decode(gzip.decompress(data))
    assert name == ""
    assert root["DataVersion"] == nbt.Int(DATA_VERSION)
    assert root["size"] == nbt.int_list(5, 5, 5)
    assert [p["Name"] for p in root["palette"].items] == ["minecraft:smooth_stone", "minecraft:air"]
    assert len(root["blocks"].items) == 5 * 5 * 5


SPIKE_DIR = "qa-mod/src/generated/resources/data/realmforge_qa/structure/spike"


def test_spike_rooms_store_every_position_of_their_box_once(repo: RepoPaths) -> None:
    files = generate(repo)
    for name, room in SPIKE_ROOMS.items():
        _, root = nbt.decode(gzip.decompress(files[f"{SPIKE_DIR}/{name}.nbt"]))
        size_x, size_y, size_z = room.size
        assert root["size"] == nbt.int_list(size_x, size_y, size_z)
        positions = [tuple(i.value for i in block["pos"].items) for block in root["blocks"].items]
        assert len(positions) == len(set(positions)) == size_x * size_y * size_z
        palette = root["palette"].items
        assert all(0 <= block["state"].value < len(palette) for block in root["blocks"].items)


def test_spike_hall_chest_holds_an_item_and_lanterns_hang(repo: RepoPaths) -> None:
    _, root = nbt.decode(gzip.decompress(generate(repo)[f"{SPIKE_DIR}/hall.nbt"]))
    palette = [entry["Name"] for entry in root["palette"].items]
    lantern = root["palette"].items[palette.index("minecraft:lantern")]
    assert lantern["Properties"] == {"hanging": "true", "waterlogged": "false"}
    with_nbt = [block for block in root["blocks"].items if "nbt" in block]
    chest, marker = sorted(with_nbt, key=lambda block: palette[block["state"].value])
    assert palette[chest["state"].value] == "minecraft:chest"
    assert chest["nbt"]["Items"].items[0]["id"] == "minecraft:bread"
    assert palette[marker["state"].value] == "minecraft:structure_block"
    assert marker["nbt"] == {"id": "minecraft:structure_block", "mode": "DATA", "metadata": "exit"}
    # Block entities come last, as vanilla orders them, so their neighbours exist when they load.
    assert root["blocks"].items[-2:] == tuple(with_nbt)


def test_spike_room_openings_line_up_in_a_row() -> None:
    # Placed along x: start at z 0, corridor at z +4, hall at z -8; the openings meet at z 6..8 of the start room.
    start, corridor, hall = (room_blocks(SPIKE_ROOMS[name]) for name in ("start", "corridor", "hall"))
    for z in range(6, 9):
        for y in range(1, 4):
            assert start[14, y, z] == AIR
            assert corridor[0, y, z - 4] == corridor[24, y, z - 4] == AIR
            assert hall[0, y, z + 8] == hall[30, y, z + 8] == AIR
    assert start[14, 1, 5] != AIR and hall[0, 1, 13] != AIR


def test_json_schema_is_generated(repo: RepoPaths) -> None:
    schema = json.loads(generate(repo)["tools/schemas/items.schema.json"])
    assert schema["title"] == "ItemDef"
    assert schema["additionalProperties"] is False
    assert {"id", "name"} <= set(schema["required"])
    assert schema["properties"]["creative_tabs"]["uniqueItems"] is True
