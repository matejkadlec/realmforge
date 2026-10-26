"""Items: Java registrations, item model definitions and models, placeholder textures, the item tags of item uses
(``data/realmforge/tags/item/use/<use>.json``), and the QA mod's expectations. An item's use and flavor line become
its tooltip: the vanilla lore component, set on the item's default components. A realm's signet (``content/realms/``)
names its realm there, with the realm's element, and says it is never used up (realm portal spec, "Realm signets").

Gear (architecture 13) gets its durability, enchantability, repair material and fixed stats as vanilla components:
attribute modifiers with ids per slot (architecture 8.2), a wand's attack damage and speed as vanilla's base
modifiers, armor's slot, look and equip sound. Components that name registry entries (attributes, the repair tag, the
sound) are built once the registries exist (``Item.Properties#delayedComponent``). A wand joins ``realmforge:wands``,
which the spell engine reads; gear joins vanilla's enchantable tags of its kind. Armor's look is an equipment asset
(``assets/realmforge/equipment/<look>.json``) with placeholder textures until its art exists.

An item a vanilla mob drops (``dropped_by``) gets a loot table per mob and a NeoForge loot modifier that adds it to the
mob's own loot (``neoforge:add_table``), so the mob's loot table stays vanilla's."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.generate.mobs import drop_expectation, drop_pool
from realmforge_tools.generate.textures import placeholder_equipment_texture, placeholder_texture
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.item import (
    ARMOR_KINDS,
    PLAYER_ATTACK_DAMAGE,
    PLAYER_ATTACK_SPEED,
    TWO_HANDED_TAG,
    USE_TEXT_GROUP,
    WANDS_TAG,
    GearDef,
    ItemDef,
    drop_path,
    entity_loot_table,
    use_tag,
)
from realmforge_tools.schema.realm import RealmDef
from realmforge_tools.schema.recipe import is_tag
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
# What gear's components need (_gear_properties).
GEAR_IMPORTS = (
    "net.minecraft.core.HolderLookup",
    "net.minecraft.core.component.DataComponents",
    "net.minecraft.core.registries.Registries",
    "net.minecraft.resources.Identifier",
    "net.minecraft.resources.ResourceKey",
    "net.minecraft.world.entity.EquipmentSlotGroup",
    "net.minecraft.world.entity.ai.attributes.Attribute",
    "net.minecraft.world.entity.ai.attributes.AttributeModifier",
    "net.minecraft.world.item.component.ItemAttributeModifiers",
)
WAND_IMPORTS = (
    "net.minecraft.world.entity.ai.attributes.Attributes",
    "net.minecraft.world.item.component.Weapon",
)
ARMOR_IMPORTS = (
    "net.minecraft.world.entity.EquipmentSlot",
    "net.minecraft.world.item.equipment.EquipmentAssets",
    "net.minecraft.world.item.equipment.Equippable",
)
REPAIR_IMPORTS = ("net.minecraft.world.item.enchantment.Repairable",)
REPAIR_ITEM_IMPORTS = ("net.minecraft.core.HolderSet",)
REPAIR_TAG_IMPORTS = ("net.minecraft.tags.TagKey",)
# YAML operation -> AttributeModifier.Operation.
OPERATIONS = {
    "add_value": "ADD_VALUE",
    "add_multiplied_base": "ADD_MULTIPLIED_BASE",
    "add_multiplied_total": "ADD_MULTIPLIED_TOTAL",
}
# Gear slot -> EquipmentSlotGroup / EquipmentSlot constant.
SLOT_CONSTANTS = {"mainhand": "MAINHAND", "head": "HEAD", "chest": "CHEST", "legs": "LEGS", "feet": "FEET"}
# Armor kind -> vanilla's tag of the items that take that slot's armor enchantments (Protection and the rest).
ENCHANTABLE_ARMOR_TAGS = {
    "head": "minecraft:enchantable/head_armor",
    "chest": "minecraft:enchantable/chest_armor",
    "legs": "minecraft:enchantable/leg_armor",
    "feet": "minecraft:enchantable/foot_armor",
}
# Vanilla's tags of the items that take Unbreaking and Mending (all gear), and the Curse of Binding (armor).
ENCHANTABLE_DURABILITY_TAG = "minecraft:enchantable/durability"
ENCHANTABLE_EQUIPPABLE_TAG = "minecraft:enchantable/equippable"
# Worn armor's texture layers: the body (head, chest, feet) and the leggings.
EQUIPMENT_LAYERS = ("humanoid", "humanoid_leggings")
# How much a wand wears per melee hit, as a sword (vanilla's Weapon component).
WAND_WEAR_PER_HIT = 1


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
    files.update(_looks(content, assets))

    tags: dict[str, list[str]] = {}
    for entry in content.items:
        item = f"{MOD_ID}:{entry.model.id}"
        if entry.model.use is not None:
            tags.setdefault(use_tag(entry.model.use), []).append(item)
        for tag in _gear_tags(entry.model.gear):
            tags.setdefault(tag, []).append(item)
    for tag, items in sorted(tags.items()):
        namespace, path = tag.split(":", 1)
        tag_file = paths.mod_generated / "resources" / "data" / namespace / "tags" / "item" / f"{path}.json"
        files[tag_file] = dump_json({"values": sorted(items)})

    files.update(_drops(content, paths))

    qa_java_root = paths.qa_generated / "java"
    files[java_file_path(qa_java_root, QA_GENERATED_PACKAGE, "GeneratedItemExpectations")] = _expectations(
        content, paths
    )
    return files


def _gear_tags(gear: GearDef | None) -> list[str]:
    """The item tags gear joins: a wand ``realmforge:wands``, and ``realmforge:two_handed`` when it is; all gear
    vanilla's durability enchantments, and armor its slot's armor enchantments and the Curse of Binding."""
    if gear is None:
        return []
    if gear.kind == "wand":
        return [WANDS_TAG, *([TWO_HANDED_TAG] if gear.two_handed else []), ENCHANTABLE_DURABILITY_TAG]
    return [ENCHANTABLE_ARMOR_TAGS[gear.kind], ENCHANTABLE_DURABILITY_TAG, ENCHANTABLE_EQUIPPABLE_TAG]


def _looks(content: Content, assets: Path) -> dict[Path, bytes]:
    """Each armor look's equipment asset, a body and a leggings layer of one texture, and its placeholder texture in
    the colour of its items (validation makes them share one)."""
    files: dict[Path, bytes] = {}
    colors: dict[str, str | None] = {}
    for entry in content.items:
        gear = entry.model.gear
        if gear is not None and gear.look is not None:
            colors.setdefault(gear.look, entry.model.color)
    for look, color in sorted(colors.items()):
        layer = [{"texture": f"{MOD_ID}:{look}"}]
        files[assets / "equipment" / f"{look}.json"] = dump_json(
            {"layers": {name: layer for name in EQUIPMENT_LAYERS}}
        )
        texture = placeholder_equipment_texture(f"{MOD_ID}:{look}", _rgb(color))
        for name in EQUIPMENT_LAYERS:
            files[assets / "textures" / "entity" / "equipment" / name / f"{look}.png"] = texture
    return files


def _drops(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    """Each ``dropped_by`` entry: a loot table with the drop's one pool, and the loot modifier that adds that table to
    the mob's own loot."""
    files: dict[Path, bytes] = {}
    data = data_dir(paths)
    for entry in content.items:
        item = f"{MOD_ID}:{entry.model.id}"
        for drop in entry.model.dropped_by:
            path = drop_path(entry.model.id, drop.entity)
            files[data / "loot_table" / f"{path}.json"] = dump_json(
                {
                    "type": "minecraft:entity",
                    "pools": [drop_pool(item, drop)],
                    "random_sequence": f"{MOD_ID}:{path}",
                }
            )
            files[data / "loot_modifiers" / f"{path}.json"] = dump_json(
                {
                    "type": "neoforge:add_table",
                    "conditions": [
                        {
                            "condition": "neoforge:loot_table_id",
                            "loot_table_id": entity_loot_table(drop.entity),
                        }
                    ],
                    "table": f"{MOD_ID}:{path}",
                }
            )
    return files


def _registrations(content: Content, paths: RepoPaths) -> bytes:
    fields: list[str] = []
    tabs: dict[str, list[str]] = {}
    imports = {"net.minecraft.world.item.Item"}
    realms_by_signet = {entry.model.signet: entry.model for entry in content.realms if entry.model.signet}
    gear_items = [entry.model for entry in content.items if entry.model.gear is not None]
    for entry in content.items:
        item = entry.model
        # The register call sets the item's id on the properties; the lambda only adds to them.
        arguments = string_literal(item.id)
        properties = ""
        if item.max_stack_size != VANILLA_DEFAULT_STACK_SIZE and item.gear is None:
            properties += f".stacksTo({item.max_stack_size})"
        if item.fire_resistant:
            properties += ".fireResistant()"
        if item.gear is not None:
            gear_properties, gear_imports = _gear_properties(item.gear)
            properties += gear_properties
            imports.update(gear_imports)
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
    helpers = ""
    if gear_items:
        imports.add("net.minecraft.core.Holder")
        helpers = (
            "\n"
            "    /** The attribute {@code id} names, from the registries the item's components are built with. */\n"
            "    private static Holder<Attribute> attribute(HolderLookup.Provider context, String id) {\n"
            "        return context.getOrThrow(ResourceKey.create(Registries.ATTRIBUTE, Identifier.parse(id)));\n"
            "    }\n"
            "\n"
            "    /** A gear modifier with its id per slot and position (architecture 8.2). */\n"
            "    private static AttributeModifier modifier(String id, double amount, AttributeModifier.Operation operation) {\n"
            "        return new AttributeModifier(Identifier.parse(id), amount, operation);\n"
            "    }\n"
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
        + helpers
        + "}\n"
    )
    return source.encode("utf-8")


def _gear_properties(gear: GearDef) -> tuple[str, set[str]]:
    """The item properties that make it gear, as Java appended to ``properties``, and the imports they need."""
    line = "\n                    "
    inner = "\n                            "
    imports = set(GEAR_IMPORTS)
    java = f"{line}.durability({gear.durability})"
    if gear.enchantability is not None:
        java += f"{line}.enchantable({gear.enchantability})"
    if gear.repair_with is not None:
        imports.update(REPAIR_IMPORTS)
        if is_tag(gear.repair_with):
            imports.update(REPAIR_TAG_IMPORTS)
            material = (
                "context.getOrThrow(TagKey.create(Registries.ITEM, "
                f"Identifier.parse({string_literal(gear.repair_with.removeprefix('#'))})))"
            )
        else:
            imports.update(REPAIR_ITEM_IMPORTS)
            material = (
                "HolderSet.direct(context.getOrThrow(ResourceKey.create(Registries.ITEM, "
                f"Identifier.parse({string_literal(gear.repair_with)}))))"
            )
        java += f"{line}.delayedComponent(DataComponents.REPAIRABLE, context -> new Repairable({inner}{material}))"
    group = f"EquipmentSlotGroup.{SLOT_CONSTANTS[gear.slot]}"
    modifiers: list[str] = []
    if gear.kind == "wand":
        imports.update(WAND_IMPORTS)
        java += f"{line}.component(DataComponents.WEAPON, new Weapon({WAND_WEAR_PER_HIT}))"
        assert gear.attack_damage is not None and gear.attack_speed is not None
        modifiers += [
            f".add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, "
            f"{_double(gear.attack_damage - PLAYER_ATTACK_DAMAGE)}, AttributeModifier.Operation.ADD_VALUE), {group})",
            f".add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, "
            f"{_double(gear.attack_speed - PLAYER_ATTACK_SPEED)}, AttributeModifier.Operation.ADD_VALUE), {group})",
        ]
    else:
        assert gear.kind in ARMOR_KINDS and gear.look is not None
        imports.update(ARMOR_IMPORTS)
        sound = (
            "context.getOrThrow(ResourceKey.create(Registries.SOUND_EVENT, "
            f"Identifier.parse({string_literal(gear.equip_sound)})))"
        )
        asset = f"ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.parse({string_literal(f'{MOD_ID}:{gear.look}')}))"
        java += (
            f"{line}.delayedComponent(DataComponents.EQUIPPABLE, context -> "
            f"Equippable.builder(EquipmentSlot.{SLOT_CONSTANTS[gear.slot]})"
            f"{inner}.setEquipSound({sound})"
            f"{inner}.setAsset({asset})"
            f"{inner}.build())"
        )
    for n, modifier in enumerate(gear.modifiers):
        modifiers.append(
            f".add(attribute(context, {string_literal(modifier.attribute)}), "
            f"modifier({string_literal(gear.modifier_id(n))}, {_double(modifier.game_amount)}, "
            f"AttributeModifier.Operation.{OPERATIONS[modifier.operation]}), {group})"
        )
    if modifiers:
        java += (
            f"{line}.delayedComponent(DataComponents.ATTRIBUTE_MODIFIERS, context -> ItemAttributeModifiers.builder()"
            + "".join(f"{inner}{modifier}" for modifier in modifiers)
            + f"{inner}.build())"
        )
    return java, imports


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


def _java_list(rows: list[str], indent: str) -> str:
    """``List.of(...)`` with one row per line at ``indent``, or ``List.of()``."""
    return (
        "List.of("
        + "".join(f"\n{indent}{row}" + ("," if n < len(rows) - 1 else "") for n, row in enumerate(rows))
        + ")"
    )


def _gear_expectation(gear: GearDef | None) -> str:
    if gear is None:
        return "Optional.empty()"
    indent = " " * 28
    modifiers = [
        f"new GearModifier({string_literal(gear.modifier_id(n))}, {string_literal(modifier.attribute)}, "
        f"{string_literal(modifier.operation)}, {_double(modifier.game_amount)})"
        for n, modifier in enumerate(gear.modifiers)
    ]
    return (
        "Optional.of(new Gear(\n"
        f"{indent}{string_literal(gear.kind)}, {string_literal(gear.slot)}, {gear.durability}, "
        f"{gear.enchantability or 0}, {string_literal(gear.repair_with or '')},\n"
        f"{indent}{_double(gear.attack_damage or 0)}, {_double(gear.attack_speed or 0)}, "
        f"{string_literal(f'{MOD_ID}:{gear.look}' if gear.look else '')}, {str(gear.two_handed).lower()},\n"
        f"{indent}{_java_list(modifiers, indent + ' ' * 8)}))"
    )


def _drops_expectation(item: ItemDef) -> str:
    indent = " " * 28
    drops = [
        f"new ItemDrop({string_literal(drop.entity)}, {string_literal(entity_loot_table(drop.entity))},\n"
        f"{indent}        {drop_expectation(f'{MOD_ID}:{item.id}', drop)})"
        for drop in item.dropped_by
    ]
    return _java_list(drops, indent)


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
            f"                    {string_literal(use_tag(item.use) if item.use else '')},\n"
            f"                    {_gear_expectation(item.gear)},\n"
            f"                    {_drops_expectation(item)})"
        )
    items_list = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"

    source = (
        header(Path(__file__), "content/items/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        f"import {QA_GENERATED_PACKAGE}.GeneratedMobExpectations.DropExpectation;\n"
        "import java.util.List;\n"
        "import java.util.Optional;\n"
        "\n"
        "/** What {@code content/items/} says about every item, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedItemExpectations {\n"
        "    /**\n"
        "     * Expected state of one item. Ids are full resource locations, e.g. {@code realmforge:test_item}; the use\n"
        "     * tag is the item tag of its use, empty for an item without one; gear is empty for an item that is no\n"
        "     * gear; drops are the mobs that are not ours that drop it.\n"
        "     */\n"
        "    public record ItemExpectation(String id, String translationKey, int maxStackSize, List<String> creativeTabs,\n"
        "            String useTag, Optional<Gear> gear, List<ItemDrop> drops) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * A piece of gear: its kind ({@code wand}, {@code head}, ...), the slot its stats count in ({@code mainhand}\n"
        "     * or the armor slot), its durability, enchantability (0: none) and repair material (an item, a {@code #}tag\n"
        "     * or empty), a wand's attack damage and speed as the tooltip shows them (0 for armor), armor's equipment\n"
        "     * asset (empty for a wand), whether it is two-handed, and its fixed stats.\n"
        "     */\n"
        "    public record Gear(String kind, String slot, int durability, int enchantability, String repairWith,\n"
        "            double attackDamage, double attackSpeed, String look, boolean twoHanded,\n"
        "            List<GearModifier> modifiers) {\n"
        "    }\n"
        "\n"
        "    /** One fixed stat: its modifier id, attribute, operation (as in YAML) and amount in game units. */\n"
        "    public record GearModifier(String id, String attribute, String operation, double amount) {\n"
        "    }\n"
        "\n"
        "    /** The item dropped by a mob that is not ours, through a loot modifier on the mob's own loot table. */\n"
        "    public record ItemDrop(String entity, String lootTable, DropExpectation drop) {\n"
        "    }\n"
        "\n"
        f"    public static final List<ItemExpectation> ITEMS = {items_list};\n"
        "\n"
        "    private GeneratedItemExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _double(value: float) -> str:
    """A Java double literal, always with a decimal point."""
    text = repr(float(value))
    return text if "e" not in text else f"{value:.10f}".rstrip("0")


def _rgb(color: str | None) -> int | None:
    return int(color.removeprefix("#"), 16) if color is not None else None
