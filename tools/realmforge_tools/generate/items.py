"""Items: Java registrations, item model definitions and models, placeholder textures, the item tags of item uses
(``data/realmforge/tags/item/use/<use>.json``), and the QA mod's expectations. An item's use and flavor line become
its tooltip: the vanilla lore component, set on the item's default components. A realm's signet (``content/realms/``)
names its realm there, with the realm's element, and says it is never used up (realm portal spec, "Realm signets")."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.generate.textures import placeholder_texture
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.item import USE_TEXT_GROUP, ItemDef, use_tag
from realmforge_tools.schema.realm import RealmDef
from realmforge_tools.schema.text import text_key

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
VANILLA_DEFAULT_STACK_SIZE = 64
# A signet's tooltip line, a text in content/texts/tooltip.yaml: it is not consumed on use (Matěj, 2026-10-20).
SIGNET_KEPT_TEXT = ("tooltip", "signet_not_consumed")
# What an item's tooltip lines need (_lore).
LORE_IMPORTS = (
    "java.util.List",
    "net.minecraft.ChatFormatting",
    "net.minecraft.core.component.DataComponents",
    "net.minecraft.network.chat.Component",
    "net.minecraft.world.item.component.ItemLore",
)


def translation_key(item_id: str) -> str:
    return f"item.{MOD_ID}.{item_id}"


def flavor_key(item_id: str) -> str:
    """The translation key of an item's flavor line."""
    return f"{translation_key(item_id)}.flavor"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    java_root = paths.mod_generated / "java"
    files[java_file_path(java_root, GENERATED_PACKAGE, "GeneratedItems")] = _registrations(content, paths)

    assets = paths.mod_generated / "resources" / "assets" / MOD_ID
    for entry in content.items:
        item_id = entry.model.id
        # The item model definition (assets/<ns>/items/) picks the model the item renders with; the model itself
        # stays in models/item/.
        definition = {"model": {"type": "minecraft:model", "model": f"{MOD_ID}:item/{item_id}"}}
        files[assets / "items" / f"{item_id}.json"] = dump_json(definition)
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD_ID}:item/{item_id}"}}
        files[assets / "models" / "item" / f"{item_id}.json"] = dump_json(model)
        files[assets / "textures" / "item" / f"{item_id}.png"] = placeholder_texture(
            f"{MOD_ID}:{item_id}", _rgb(entry.model.color)
        )

    uses: dict[str, list[str]] = {}
    for entry in content.items:
        if entry.model.use is not None:
            uses.setdefault(entry.model.use, []).append(f"{MOD_ID}:{entry.model.id}")
    for use, items in sorted(uses.items()):
        path = use_tag(use).split(":", 1)[1]
        files[data_dir(paths) / "tags" / "item" / f"{path}.json"] = dump_json({"values": sorted(items)})

    qa_java_root = paths.qa_generated / "java"
    files[java_file_path(qa_java_root, QA_GENERATED_PACKAGE, "GeneratedItemExpectations")] = _expectations(
        content, paths
    )
    return files


def _registrations(content: Content, paths: RepoPaths) -> bytes:
    fields: list[str] = []
    tabs: dict[str, list[str]] = {}
    imports = {"net.minecraft.world.item.Item"}
    realms_by_signet = {entry.model.signet: entry.model for entry in content.realms if entry.model.signet}
    for entry in content.items:
        item = entry.model
        # The register call sets the item's id on the properties; the lambda only adds to them.
        arguments = string_literal(item.id)
        properties = ""
        if item.max_stack_size != VANILLA_DEFAULT_STACK_SIZE:
            properties += f".stacksTo({item.max_stack_size})"
        if item.fire_resistant:
            properties += ".fireResistant()"
        lore = _lore(item, realms_by_signet.get(f"{MOD_ID}:{item.id}"))
        if lore:
            imports.update(LORE_IMPORTS)
            properties += (
                f"\n                    .component(DataComponents.LORE, new ItemLore(List.of(\n{lore})))"
            )
        if properties:
            arguments += f", properties -> properties{properties}"
        constant = constant_name(item.id)
        fields.append(
            f"    /** {{@code {MOD_ID}:{item.id}}} from {{@code {entry.source}}}. */\n"
            f"    public static final DeferredItem<Item> {constant} =\n"
            f"            ITEMS.registerSimpleItem({arguments});\n"
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
        "        switch (event.getTabKey().identifier().toString()) {\n"
        + cases
        + "            default -> {\n"
        "            }\n"
        "        }\n"
    )

    source = (
        header(Path(__file__), "content/items/") + f"package {GENERATED_PACKAGE};\n"
        "\n"
        f"import {JAVA_PACKAGE}.Realmforge;\n"
        + "".join(f"import {name};\n" for name in sorted(imports))
        + "import net.neoforged.bus.api.IEventBus;\n"
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


def _lore(item: ItemDef, realm: RealmDef | None) -> str:
    """The item's tooltip lines as Java: for a realm's signet, that it is not consumed on use (grey, not italic); its
    use in green, as a "Use:" line; then its flavor line in vanilla's lore style (purple, italic). Empty when it has
    none of them."""
    indent = " " * 28
    lines = []
    if realm is not None:
        plain = (
            f"\n{indent}        .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false))"
        )
        lines.append(f"Component.translatable({string_literal(text_key(*SIGNET_KEPT_TEXT))}){plain}")
    if item.use is not None:
        key = text_key(USE_TEXT_GROUP, item.use)
        lines.append(
            f"Component.translatable({string_literal(key)})\n"
            f"{indent}        .withStyle(style -> style.withColor(ChatFormatting.GREEN).withItalic(false))"
        )
    if item.flavor is not None:
        lines.append(f"Component.translatable({string_literal(flavor_key(item.id))})")
    return ",\n".join(indent + line for line in lines)


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
            f"                    List.of({tabs}),\n"
            f"                    {string_literal(use_tag(item.use) if item.use else '')})"
        )
    items_list = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"

    source = (
        header(Path(__file__), "content/items/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/** What {@code content/items/} says about every item, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedItemExpectations {\n"
        "    /**\n"
        "     * Expected state of one item. Ids are full resource locations, e.g. {@code realmforge:test_item}; the use\n"
        "     * tag is the item tag of its use, empty for an item without one.\n"
        "     */\n"
        "    public record ItemExpectation(String id, String translationKey, int maxStackSize, List<String> creativeTabs,\n"
        "            String useTag) {\n"
        "    }\n"
        "\n"
        f"    public static final List<ItemExpectation> ITEMS = {items_list};\n"
        "\n"
        "    private GeneratedItemExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _rgb(color: str | None) -> int | None:
    return int(color.removeprefix("#"), 16) if color is not None else None
