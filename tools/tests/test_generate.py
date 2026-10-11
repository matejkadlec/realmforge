from __future__ import annotations

import gzip
import json
import struct
import zlib

from realmforge_tools.formats import nbt
from realmforge_tools.formats.png import PNG_SIGNATURE
from realmforge_tools.generate import generate_all, sync
from realmforge_tools.generate.textures import placeholder_texture
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
    assert 'ITEMS.registerSimpleItem("test_item", new Item.Properties().stacksTo(16));' in java
    assert 'case "minecraft:ingredients" -> {' in java
    assert "event.accept(TEST_ITEM);" in java

    model = json.loads(files[f"{ASSETS}/models/item/test_item.json"])
    assert model == {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "realmforge:item/test_item"},
    }

    assert json.loads(files[f"{ASSETS}/lang/en_us.json"]) == {"item.realmforge.test_item": "Test Item"}
    assert json.loads(files[f"{ASSETS}/lang/cs_cz.json"]) == {
        "item.realmforge.test_item": "Testovací předmět"
    }

    expectations = files[EXPECTATIONS_JAVA].decode()
    assert '"realmforge:test_item",' in expectations
    assert '"item.realmforge.test_item",' in expectations
    assert 'List.of("minecraft:ingredients")' in expectations


def test_default_stack_size_is_not_emitted(repo: RepoPaths) -> None:
    write(repo.content / "items" / "plain.yaml", "id: plain\nname:\n  en_us: Plain\n")
    java = generate(repo)[GENERATED_JAVA].decode()
    assert 'ITEMS.registerSimpleItem("plain", new Item.Properties());' in java


def test_locale_without_texts_is_omitted(repo: RepoPaths) -> None:
    (repo.content / "items" / "test_item.yaml").unlink()
    write(repo.content / "items" / "plain.yaml", "id: plain\nname:\n  en_us: Plain\n")
    files = generate(repo)
    assert f"{ASSETS}/lang/en_us.json" in files
    assert f"{ASSETS}/lang/cs_cz.json" not in files


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


def test_json_schema_is_generated(repo: RepoPaths) -> None:
    schema = json.loads(generate(repo)["tools/schemas/items.schema.json"])
    assert schema["title"] == "ItemDef"
    assert schema["additionalProperties"] is False
    assert {"id", "name"} <= set(schema["required"])
    assert schema["properties"]["creative_tabs"]["uniqueItems"] is True
