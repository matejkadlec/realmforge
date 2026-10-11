"""Items: Java registrations, item models, placeholder textures, and the QA mod's expectations."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.generate.textures import placeholder_texture
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
VANILLA_DEFAULT_STACK_SIZE = 64


def translation_key(item_id: str) -> str:
    return f"item.{MOD_ID}.{item_id}"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    java_root = paths.mod_generated / "java"
    files[java_file_path(java_root, GENERATED_PACKAGE, "GeneratedItems")] = _registrations(content, paths)

    assets = paths.mod_generated / "resources" / "assets" / MOD_ID
    for entry in content.items:
        item_id = entry.model.id
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD_ID}:item/{item_id}"}}
        files[assets / "models" / "item" / f"{item_id}.json"] = dump_json(model)
        files[assets / "textures" / "item" / f"{item_id}.png"] = placeholder_texture(f"{MOD_ID}:{item_id}")

    qa_java_root = paths.qa_generated / "java"
    files[java_file_path(qa_java_root, QA_GENERATED_PACKAGE, "GeneratedItemExpectations")] = _expectations(
        content, paths
    )
    return files


def _registrations(content: Content, paths: RepoPaths) -> bytes:
    fields: list[str] = []
    tabs: dict[str, list[str]] = {}
    for entry in content.items:
        item = entry.model
        properties = "new Item.Properties()"
        if item.max_stack_size != VANILLA_DEFAULT_STACK_SIZE:
            properties += f".stacksTo({item.max_stack_size})"
        constant = constant_name(item.id)
        fields.append(
            f"    /** {{@code {MOD_ID}:{item.id}}} from {{@code {entry.source}}}. */\n"
            f"    public static final DeferredItem<Item> {constant} =\n"
            f"            ITEMS.registerSimpleItem({string_literal(item.id)}, {properties});\n"
        )
        for tab in item.creative_tabs:
            tabs.setdefault(tab, []).append(constant)

    cases = "".join(
        f"            case {string_literal(tab)} -> {{\n"
        + "".join(f"                event.accept({constant});\n" for constant in constants)
        + "            }\n"
        for tab, constants in sorted(tabs.items())
    )
    switch = (
        "        switch (event.getTabKey().location().toString()) {\n" + cases + "            default -> {\n"
        "            }\n"
        "        }\n"
    )

    source = (
        header(Path(__file__), "content/items/") + f"package {GENERATED_PACKAGE};\n"
        "\n"
        f"import {JAVA_PACKAGE}.Realmforge;\n"
        "import net.minecraft.world.item.Item;\n"
        "import net.neoforged.bus.api.IEventBus;\n"
        "import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;\n"
        "import net.neoforged.neoforge.registries.DeferredItem;\n"
        "import net.neoforged.neoforge.registries.DeferredRegister;\n"
        "\n"
        "/** Item registrations generated from {@code content/items/}. */\n"
        "public final class GeneratedItems {\n"
        "    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Realmforge.MOD_ID);\n"
        "\n" + "\n".join(fields) + "\n"
        "    private GeneratedItems() {\n"
        "    }\n"
        "\n"
        "    /** Registers the items and their creative tab entries. Call once from the mod constructor. */\n"
        "    public static void register(IEventBus modEventBus) {\n"
        "        ITEMS.register(modEventBus);\n"
        "        modEventBus.addListener(GeneratedItems::addToCreativeTabs);\n"
        "    }\n"
        "\n"
        "    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {\n"
        + switch
        + "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _expectations(content: Content, paths: RepoPaths) -> bytes:
    rows = []
    for entry in content.items:
        item = entry.model
        tabs = ", ".join(string_literal(tab) for tab in item.creative_tabs)
        rows.append(
            "            new ItemExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{item.id}')},\n"
            f"                    {string_literal(translation_key(item.id))},\n"
            f"                    {item.max_stack_size},\n"
            f"                    List.of({tabs}))"
        )
    items_list = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"

    source = (
        header(Path(__file__), "content/items/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/** What {@code content/items/} says about every item, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedItemExpectations {\n"
        "    /** Expected state of one item. Ids are full resource locations, e.g. {@code realmforge:test_item}. */\n"
        "    public record ItemExpectation(String id, String translationKey, int maxStackSize, List<String> creativeTabs) {\n"
        "    }\n"
        "\n"
        f"    public static final List<ItemExpectation> ITEMS = {items_list};\n"
        "\n"
        "    private GeneratedItemExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
