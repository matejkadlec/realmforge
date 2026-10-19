"""Blocks: Java registrations (the block and its block item), blockstates, block and item models, placeholder
textures, loot tables (the block drops itself), the vanilla mining tags, and the QA mod's expectations
(architecture 6.2). A block's kind decides its Java class (a kind other than simple comes from ``world/BlockKinds``,
which the feature owning the kind fills), its blockstate and its models."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.generate.textures import placeholder_block_texture
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.block import BlockDef

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
SOURCES = "content/blocks/"


def translation_key(block_id: str) -> str:
    """A block's name; its block item uses the same key (``Item.Properties#useBlockDescriptionPrefix``)."""
    return f"block.{MOD_ID}.{block_id}"


def loot_table(block_id: str) -> str:
    """The loot table a block drops: vanilla's default, ``<namespace>:blocks/<path>``."""
    return f"{MOD_ID}:blocks/{block_id}"


def mining_tags(block: BlockDef) -> list[str]:
    """The vanilla block tags that say which tool mines the block and which tier it needs."""
    tags = []
    if block.tool is not None:
        tags.append(f"minecraft:mineable/{block.tool}")
    if block.tool_tier is not None:
        tags.append(f"minecraft:needs_{block.tool_tier}_tool")
    return tags


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    java_root = paths.mod_generated / "java"
    files[java_file_path(java_root, GENERATED_PACKAGE, "GeneratedBlocks")] = _registrations(content)

    assets = paths.mod_generated / "resources" / "assets" / MOD_ID
    tags: dict[str, list[str]] = {}
    for entry in content.blocks:
        block_id = entry.model.id
        model = f"{MOD_ID}:block/{block_id}"
        for path, data in _models(entry.model).items():
            files[assets / path] = data
        files[assets / "items" / f"{block_id}.json"] = dump_json(
            {"model": {"type": "minecraft:model", "model": model}}
        )
        files[data_dir(paths) / "loot_table" / "blocks" / f"{block_id}.json"] = dump_json(_loot(block_id))
        for tag in mining_tags(entry.model):
            tags.setdefault(tag, []).append(f"{MOD_ID}:{block_id}")
    minecraft_data = paths.mod_generated / "resources" / "data" / "minecraft"
    for tag, blocks in sorted(tags.items()):
        path = tag.removeprefix("minecraft:")
        files[minecraft_data / "tags" / "block" / f"{path}.json"] = dump_json({"values": sorted(blocks)})

    qa_java = paths.qa_generated / "java"
    files[java_file_path(qa_java, QA_GENERATED_PACKAGE, "GeneratedBlockExpectations")] = _expectations(
        content
    )
    return files


def _models(block: BlockDef) -> dict[str, bytes]:
    """The blockstate, block models and placeholder textures of a block, by path under ``assets/realmforge/``.

    - simple: one full cube.
    - dungeon_portal: a full cube that looks different while open (``open=true``: the model and texture
      ``<id>_open``).
    - dungeon_exit: a plate one pixel high, vanilla's carpet shape.
    """
    block_id = block.id
    model = f"{MOD_ID}:block/{block_id}"
    files: dict[str, bytes] = {}
    if block.kind == "dungeon_portal":
        open_model = f"{model}_open"
        variants = {"open=false": {"model": model}, "open=true": {"model": open_model}}
        files[f"blockstates/{block_id}.json"] = dump_json({"variants": variants})
        for name in (block_id, f"{block_id}_open"):
            texture = f"{MOD_ID}:block/{name}"
            files[f"models/block/{name}.json"] = dump_json(
                {"parent": "minecraft:block/cube_all", "textures": {"all": texture}}
            )
            files[f"textures/block/{name}.png"] = placeholder_block_texture(f"{MOD_ID}:{name}")
        return files
    files[f"blockstates/{block_id}.json"] = dump_json({"variants": {"": {"model": model}}})
    if block.kind == "dungeon_exit":
        files[f"models/block/{block_id}.json"] = dump_json(
            {"parent": "minecraft:block/carpet", "textures": {"wool": model}}
        )
    else:
        files[f"models/block/{block_id}.json"] = dump_json(
            {"parent": "minecraft:block/cube_all", "textures": {"all": model}}
        )
    files[f"textures/block/{block_id}.png"] = placeholder_block_texture(f"{MOD_ID}:{block_id}")
    return files


def _loot(block_id: str) -> dict[str, object]:
    """One copy of the block, unless an explosion destroyed it, as vanilla's own simple blocks drop."""
    return {
        "type": "minecraft:block",
        "pools": [
            {
                "bonus_rolls": 0.0,
                "conditions": [{"condition": "minecraft:survives_explosion"}],
                "entries": [{"type": "minecraft:item", "name": f"{MOD_ID}:{block_id}"}],
                "rolls": 1.0,
            }
        ],
        "random_sequence": loot_table(block_id),
    }


def _float(value: float) -> str:
    """A Java float literal; repr() gives the shortest text that reads back as the same number."""
    return f"{float(value)!r}F"


def _registrations(content: Content) -> bytes:
    fields: list[str] = []
    tabs: dict[str, list[str]] = {}
    for entry in content.blocks:
        block = entry.model
        constant = constant_name(block.id)
        properties = (
            f"properties -> properties\n"
            f"                    .mapColor(MapColor.{block.map_color.upper()})\n"
            f"                    .strength({_float(block.hardness)}, {_float(block.blast_resistance)})\n"
            f"                    .sound(SoundType.{block.sound.upper()})"
        )
        if block.requires_tool:
            properties += "\n                    .requiresCorrectToolForDrops()"
        if block.kind == "simple":
            register = f"BLOCKS.registerSimpleBlock({string_literal(block.id)}, {properties})"
        else:
            # The feature that owns the kind provides its block class (world/BlockKinds).
            register = (
                f"BLOCKS.registerBlock({string_literal(block.id)},\n"
                f"                    properties -> BlockKinds.create({string_literal(block.kind)}, properties),\n"
                f"                    {properties})"
            )
        fields.append(
            f"    /** {{@code {MOD_ID}:{block.id}}} from {{@code {entry.source}}}. */\n"
            f"    public static final DeferredBlock<Block> {constant} =\n"
            f"            {register};\n"
            f"\n"
            f"    /** The block item of {{@link #{constant}}}. */\n"
            f"    public static final DeferredItem<BlockItem> {constant}_ITEM = ITEMS.registerSimpleBlockItem({constant});\n"
        )
        for tab in block.creative_tabs:
            tabs.setdefault(tab, []).append(f"{constant}_ITEM")

    cases = "".join(
        f"            case {string_literal(tab)} -> {{\n"
        + "".join(f"                event.accept({constant});\n" for constant in constants)
        + "            }\n"
        for tab, constants in sorted(tabs.items())
    )
    source = (
        header(Path(__file__), SOURCES) + f"package {GENERATED_PACKAGE};\n"
        "\n"
        f"import {JAVA_PACKAGE}.Realmforge;\n"
        + (
            f"import {JAVA_PACKAGE}.world.BlockKinds;\n"
            if any(e.model.kind != "simple" for e in content.blocks)
            else ""
        )
        + "import net.minecraft.world.item.BlockItem;\n"
        "import net.minecraft.world.level.block.Block;\n"
        "import net.minecraft.world.level.block.SoundType;\n"
        "import net.minecraft.world.level.material.MapColor;\n"
        "import net.neoforged.bus.api.IEventBus;\n"
        "import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;\n"
        "import net.neoforged.neoforge.registries.DeferredBlock;\n"
        "import net.neoforged.neoforge.registries.DeferredItem;\n"
        "import net.neoforged.neoforge.registries.DeferredRegister;\n"
        "\n"
        "/** Block registrations, with their block items, generated from {@code content/blocks/}. */\n"
        "public final class GeneratedBlocks {\n"
        "    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Realmforge.MOD_ID);\n"
        "    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Realmforge.MOD_ID);\n"
        "\n" + "\n".join(fields) + "\n"
        "    private GeneratedBlocks() {\n"
        "    }\n"
        "\n"
        "    /** Registers the blocks, their items and their creative tab entries. Call once from the mod constructor. */\n"
        "    public static void register(IEventBus modEventBus) {\n"
        "        BLOCKS.register(modEventBus);\n"
        "        ITEMS.register(modEventBus);\n"
        "        modEventBus.addListener(GeneratedBlocks::addToCreativeTabs);\n"
        "    }\n"
        "\n"
        "    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {\n"
        "        switch (event.getTabKey().identifier().toString()) {\n"
        + cases
        + "            default -> {\n"
        "            }\n"
        "        }\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _expectations(content: Content) -> bytes:
    rows = []
    for entry in content.blocks:
        block = entry.model
        tags = ", ".join(string_literal(tag) for tag in mining_tags(block))
        rows.append(
            "            new BlockExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{block.id}')},\n"
            f"                    {string_literal(translation_key(block.id))},\n"
            f"                    {_float(block.hardness)}, {_float(block.blast_resistance)}, {str(block.requires_tool).lower()},\n"
            f"                    List.of({tags}),\n"
            f"                    {string_literal(loot_table(block.id))})"
        )
    blocks = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
    source = (
        header(Path(__file__), SOURCES) + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/** What {@code content/blocks/} says about every block, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedBlockExpectations {\n"
        "    /**\n"
        "     * Expected state of one block. Ids are full resource locations, e.g. {@code realmforge:vaultstone}; the tags\n"
        "     * are the vanilla mining tags it is in, and its block item has the block's id.\n"
        "     */\n"
        "    public record BlockExpectation(String id, String translationKey, float hardness, float blastResistance,\n"
        "            boolean requiresTool, List<String> miningTags, String lootTable) {\n"
        "    }\n"
        "\n"
        f"    public static final List<BlockExpectation> BLOCKS = {blocks};\n"
        "\n"
        "    private GeneratedBlockExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
