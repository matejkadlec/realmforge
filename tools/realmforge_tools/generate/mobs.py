"""Custom mobs: the entity type registrations, loot tables, placeholder models, animations and textures, and the QA
mod's expectations (the names: the lang files, lang.py). Architecture 14.2.

The placeholder model is a statue-like humanoid sized to the mob's hitbox, with the bones the archetypes animate
(``head``, ``body``, ``left_arm``, ``right_arm``, ``left_leg``, ``right_leg``), in the Bedrock geometry format GeckoLib
reads; its animations are the ones the archetypes play (``misc.idle``, ``move.walk``, ``attack.swing``,
``attack.cast``). A2 (RLM-48) replaces them with real art.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir, percent_to_fraction
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.generate.textures import placeholder_mob_texture
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.common import LocalizedText
from realmforge_tools.schema.mob import MobDef, MobDropDef, mob_description_id, mob_id, mob_loot_table

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
SOURCES = "content/mobs/"

# YAML archetype -> its class in mob/custom/.
ARCHETYPE_CLASSES = {"melee": "MeleeMob", "caster": "CasterMob"}
_IDLE = {"stroll": "STROLL", "guard": "GUARD"}
# How far away clients see the mob, in chunks: vanilla's for monsters.
CLIENT_TRACKING_RANGE = 8
PIXELS_PER_BLOCK = 16
# The placeholder texture's side: the smallest of these that the model's box UVs fit.
TEXTURE_SIZES = (64, 128, 256)


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    assets = paths.mod_generated / "resources" / "assets" / MOD_ID
    files = {
        java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedMobs"): _registrations(
            content
        ),
        java_file_path(paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedMobExpectations"): (
            _expectations(content)
        ),
    }
    for entry in content.mobs:
        mob = entry.model
        model = placeholder_model(mob)
        files[data_dir(paths) / "loot_table" / "entities" / f"{mob.id}.json"] = dump_json(loot_table(mob))
        files[assets / "geckolib" / "models" / "entity" / f"{mob.id}.geo.json"] = dump_json(
            model.geometry(mob.id)
        )
        files[assets / "geckolib" / "animations" / "entity" / f"{mob.id}.animation.json"] = dump_json(
            ANIMATIONS
        )
        files[assets / "textures" / "entity" / f"{mob.id}.png"] = placeholder_mob_texture(
            mob.id, _rgb(mob.color), model.texture_size, model.texture_size, model.eyes()
        )
    return files


def translations(content: Content) -> dict[str, LocalizedText]:
    """Every mob's name, by its entity type's translation key ``entity.realmforge.<id>``."""
    return {mob_description_id(entry.model.id): entry.model.name for entry in content.mobs}


def loot_table(mob: MobDef) -> dict[str, object]:
    """The mob's vanilla loot table: one pool per drop, so each drop rolls on its own."""
    return {
        "type": "minecraft:entity",
        "pools": [_pool(drop) for drop in mob.loot],
        "random_sequence": mob_loot_table(mob.id),
    }


def _pool(drop: MobDropDef) -> dict[str, object]:
    entry: dict[str, object] = {"type": "minecraft:item", "name": drop.item}
    if drop.count.max > 1:
        entry["functions"] = [
            {
                "function": "minecraft:set_count",
                "count": {
                    "type": "minecraft:uniform",
                    "min": float(drop.count.min),
                    "max": float(drop.count.max),
                },
                "add": False,
            }
        ]
    pool: dict[str, object] = {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry]}
    if drop.chance_percent < 100:
        pool["conditions"] = [
            {"condition": "minecraft:random_chance", "chance": percent_to_fraction(drop.chance_percent)}
        ]
    return pool


@dataclass(frozen=True)
class Cube:
    """One box of the placeholder model, in model pixels: its bone, origin (lowest corner), size and texture
    corner."""

    bone: str
    origin: tuple[float, float, float]
    size: tuple[int, int, int]
    uv: tuple[int, int] = (0, 0)


@dataclass(frozen=True)
class PlaceholderModel:
    """A statue-like humanoid that fills a mob's hitbox: legs, body and head stacked to its height, arms at the
    body's sides."""

    cubes: tuple[Cube, ...]
    pivots: dict[str, tuple[float, float, float]]
    texture_size: int

    def geometry(self, mob: str) -> dict[str, object]:
        top = max(cube.origin[1] + cube.size[1] for cube in self.cubes) / PIXELS_PER_BLOCK
        width = max(abs(cube.origin[0]) + cube.size[0] for cube in self.cubes) * 2 / PIXELS_PER_BLOCK
        bones = []
        for bone in BONES:
            data: dict[str, object] = {"name": bone, "pivot": list(self.pivots[bone])}
            if bone != "body":
                data["parent"] = "body"
            data["cubes"] = [
                {"origin": list(cube.origin), "size": list(cube.size), "uv": list(cube.uv)}
                for cube in self.cubes
                if cube.bone == bone
            ]
            bones.append(data)
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [
                {
                    "description": {
                        "identifier": f"geometry.{MOD_ID}.{mob}",
                        "texture_width": self.texture_size,
                        "texture_height": self.texture_size,
                        "visible_bounds_width": math.ceil(width) + 1,
                        "visible_bounds_height": math.ceil(top) + 1,
                        "visible_bounds_offset": [0, top / 2, 0],
                    },
                    "bones": bones,
                }
            ],
        }

    def eyes(self) -> set[tuple[int, int]]:
        """Two texture pixels on the head's front face, which box UV puts at the texture corner plus the depth."""
        head = next(cube for cube in self.cubes if cube.bone == "head")
        width, height, depth = head.size
        u, v = head.uv[0] + depth, head.uv[1] + depth
        row = v + height * 2 // 5
        return {(u + width // 4, row), (u + width - 1 - width // 4, row)}


# The bones of the placeholder model, the body first: every other bone is its child.
BONES = ("body", "head", "left_arm", "right_arm", "left_leg", "right_leg")


def placeholder_model(mob: MobDef) -> PlaceholderModel:
    """The placeholder model of ``mob``: legs and body three eighths of its height each, the head the rest; the body
    a little narrower than the hitbox and the arms outside it, as vanilla's humanoids. The cubes' box UVs are packed
    in rows into the smallest texture of ``TEXTURE_SIZES`` they fit."""
    height = round(mob.size.height * PIXELS_PER_BLOCK)
    across = mob.size.width * PIXELS_PER_BLOCK
    legs = round(height * 3 / 8)
    body = round(height * 3 / 8)
    head = height - legs - body
    body_width = max(4, round(across * 0.4) * 2)
    arm = max(2, round(across * 0.3))
    depth = max(2, round(across * 0.45))
    head_width = min(body_width, max(4, head))
    leg_width = body_width // 2
    cubes = [
        Cube("body", (-body_width / 2, legs, -depth / 2), (body_width, body, depth)),
        Cube("head", (-head_width / 2, legs + body, -head_width / 2), (head_width, head, head_width)),
        Cube("left_arm", (body_width / 2, legs, -arm / 2), (arm, body, arm)),
        Cube("right_arm", (-body_width / 2 - arm, legs, -arm / 2), (arm, body, arm)),
        Cube("left_leg", (0, 0, -depth / 2), (leg_width, legs, depth)),
        Cube("right_leg", (-leg_width, 0, -depth / 2), (leg_width, legs, depth)),
    ]
    shoulder = legs + body
    pivots = {
        "body": (0, legs, 0),
        "head": (0, shoulder, 0),
        "left_arm": (body_width / 2 + arm / 2, shoulder - 1, 0),
        "right_arm": (-body_width / 2 - arm / 2, shoulder - 1, 0),
        "left_leg": (leg_width / 2, legs, 0),
        "right_leg": (-leg_width / 2, legs, 0),
    }
    for size in TEXTURE_SIZES:
        packed = _pack(cubes, size)
        if packed is not None:
            return PlaceholderModel(tuple(packed), pivots, size)
    raise ValueError(f"the placeholder model of {mob.id} does not fit a {TEXTURE_SIZES[-1]}px texture")


def _pack(cubes: list[Cube], size: int) -> list[Cube] | None:
    """Gives each cube the corner of its box UV (2 x (width + depth) wide, depth + height tall), placed in rows
    across a ``size`` x ``size`` texture; None when they do not fit it."""
    packed: list[Cube] = []
    u = v = row_height = 0
    for cube in cubes:
        width, height, depth = cube.size
        unwrapped = (2 * (width + depth), depth + height)
        if u + unwrapped[0] > size:
            u, v, row_height = 0, v + row_height, 0
        if unwrapped[0] > size or v + unwrapped[1] > size:
            return None
        packed.append(Cube(cube.bone, cube.origin, cube.size, (u, v)))
        u += unwrapped[0]
        row_height = max(row_height, unwrapped[1])
    return packed


def _keyframes(*frames: tuple[float, tuple[float, float, float]]) -> dict[str, list[float]]:
    return {f"{time:g}" if time % 1 else f"{time:.1f}": list(rotation) for time, rotation in frames}


def _swing(amplitude: float) -> dict[str, list[float]]:
    return _keyframes((0.0, (amplitude, 0, 0)), (0.5, (-amplitude, 0, 0)), (1.0, (amplitude, 0, 0)))


# The head follows the mob's look in every animation that always plays (idle or walk): GeckoLib's molang queries give
# its pitch and its yaw from the body, and GeckoLib negates x and y rotations as Bedrock's format does.
_LOOK = {"rotation": ["query.head_x_rotation", "query.head_y_rotation", 0]}

# The placeholder animations, by the names the archetypes play (GeckoLib's DefaultAnimations): a slow sway while it
# stands, a stiff walk, both fists hammering down for a melee hit, and both arms raised while it casts.
ANIMATIONS: dict[str, object] = {
    "format_version": "1.8.0",
    "animations": {
        "misc.idle": {
            "loop": True,
            "animation_length": 4.0,
            "bones": {
                "body": {"rotation": _keyframes((0.0, (0, 0, 0)), (2.0, (0, 0, 1.5)), (4.0, (0, 0, 0)))},
                "head": _LOOK,
            },
        },
        "move.walk": {
            "loop": True,
            "animation_length": 1.0,
            "bones": {
                "head": _LOOK,
                "left_leg": {"rotation": _swing(25)},
                "right_leg": {"rotation": _swing(-25)},
                "left_arm": {"rotation": _swing(-15)},
                "right_arm": {"rotation": _swing(15)},
            },
        },
        "attack.swing": {
            "animation_length": 0.5,
            "bones": {
                arm: {
                    "rotation": _keyframes(
                        (0.0, (0, 0, 0)), (0.2, (-150, 0, 0)), (0.35, (10, 0, 0)), (0.5, (0, 0, 0))
                    )
                }
                for arm in ("left_arm", "right_arm")
            },
        },
        "attack.cast": {
            "loop": True,
            "animation_length": 1.0,
            "bones": {
                "left_arm": {
                    "rotation": _keyframes(
                        (0.0, (-160, 0, -20)), (0.5, (-170, 0, -30)), (1.0, (-160, 0, -20))
                    )
                },
                "right_arm": {
                    "rotation": _keyframes((0.0, (-160, 0, 20)), (0.5, (-170, 0, 30)), (1.0, (-160, 0, 20)))
                },
            },
        },
    },
}


def _rgb(color: str) -> int:
    return int(color.removeprefix("#"), 16)


def _float(value: float) -> str:
    """A Java float literal; repr() gives the shortest text that reads back as the same double."""
    return f"{float(value)!r}F"


def _double(value: float) -> str:
    return repr(float(value))


def _comment(mob: MobDef) -> str:
    english = mob.name.en_us.replace("*/", "*&#47;").replace("@", "&#64;")
    return f'{mob.archetype}, "{english}"'


def _sound(sound: str | None) -> str:
    return "Optional.empty()" if sound is None else f"CustomMobSpec.sound({string_literal(sound)})"


def _spec(mob: MobDef) -> str:
    spells = (
        ", ".join(f"GeneratedSpells.{constant_name(spell.split(':', 1)[1])}" for spell in mob.caster.spells)
        if mob.caster is not None
        else ""
    )
    keep_distance = mob.caster.keep_distance if mob.caster is not None else 0
    sounds = mob.sounds
    return (
        f"new CustomMobSpec(CustomMobSpec.Idle.{_IDLE[mob.idle]},\n"
        f"            {_sound(sounds.ambient)},\n"
        f"            {_sound(sounds.hurt)},\n"
        f"            {_sound(sounds.death)},\n"
        f"            {_sound(sounds.step)},\n"
        f"            List.of({spells}), {_double(keep_distance)})"
    )


def _attributes(mob: MobDef) -> str:
    values = mob.attributes
    pairs = (
        ("MAX_HEALTH", values.max_health),
        ("ATTACK_DAMAGE", values.attack_damage),
        ("ARMOR", values.armor),
        ("MOVEMENT_SPEED", values.movement_speed),
        ("FOLLOW_RANGE", values.follow_range),
        ("KNOCKBACK_RESISTANCE", percent_to_fraction(values.knockback_resistance_percent)),
    )
    adds = "".join(f"\n                .add(Attributes.{name}, {_double(value)})" for name, value in pairs)
    return f"Monster.createMonsterAttributes(){adds}\n                .build()"


def _registrations(content: Content) -> bytes:
    mobs = [entry.model for entry in content.mobs]
    fields = "".join(
        f"    /** {{@code {mob_id(entry.model.id)}}} from {{@code {entry.source}}}: {_comment(entry.model)}. */\n"
        f"    private static final CustomMobSpec {constant_name(entry.model.id)}_SPEC = {_spec(entry.model)};\n"
        "\n"
        f"    public static final DeferredHolder<EntityType<?>, EntityType<{ARCHETYPE_CLASSES[entry.model.archetype]}>> "
        f"{constant_name(entry.model.id)} =\n"
        f"            ENTITY_TYPES.registerEntityType({string_literal(entry.model.id)},\n"
        f"                    (type, level) -> new {ARCHETYPE_CLASSES[entry.model.archetype]}(type, level, "
        f"{constant_name(entry.model.id)}_SPEC),\n"
        f"                    MobCategory.MONSTER, builder -> builder.sized({_float(entry.model.size.width)}, "
        f"{_float(entry.model.size.height)})\n"
        f"                            .clientTrackingRange({CLIENT_TRACKING_RANGE}));\n"
        "\n"
        for entry in content.mobs
    )
    every = ", ".join(constant_name(mob.id) for mob in mobs)
    puts = "".join(f"        event.put({constant_name(mob.id)}.get(), {_attributes(mob)});\n" for mob in mobs)
    custom_imports = "".join(
        f"import {JAVA_PACKAGE}.mob.custom.{name};\n"
        for name in sorted(
            {ARCHETYPE_CLASSES[mob.archetype] for mob in mobs} | {"CustomMob", "CustomMobSpec"}
        )
    )
    source = (
        header(Path(__file__), SOURCES) + f"package {GENERATED_PACKAGE};\n"
        "\n"
        f"import {JAVA_PACKAGE}.Realmforge;\n" + custom_imports + "import java.util.List;\n"
        "import java.util.Optional;\n"
        "import net.minecraft.world.entity.EntityType;\n"
        "import net.minecraft.world.entity.MobCategory;\n"
        "import net.minecraft.world.entity.ai.attributes.Attributes;\n"
        "import net.minecraft.world.entity.monster.Monster;\n"
        "import net.neoforged.bus.api.IEventBus;\n"
        "import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;\n"
        "import net.neoforged.neoforge.registries.DeferredHolder;\n"
        "import net.neoforged.neoforge.registries.DeferredRegister;\n"
        "\n"
        "/**\n"
        " * Custom mobs generated from {@code content/mobs/} (architecture 14.2): each an entity type of the archetype it\n"
        " * picks, with its {@link CustomMobSpec}, size and attributes at level 1. Its loot table is vanilla's default,\n"
        " * {@code realmforge:entities/<id>}.\n"
        " */\n"
        "public final class GeneratedMobs {\n"
        "    public static final DeferredRegister.Entities ENTITY_TYPES =\n"
        "            DeferredRegister.createEntities(Realmforge.MOD_ID);\n"
        "\n" + fields + "    /** Every mob above, in id order. */\n"
        "    public static final List<DeferredHolder<EntityType<?>, ? extends EntityType<? extends CustomMob>>> ALL =\n"
        f"            List.of({every});\n"
        "\n"
        "    private GeneratedMobs() {\n"
        "    }\n"
        "\n"
        "    /** Registers the entity types. Call once from the mod constructor. */\n"
        "    public static void register(IEventBus modEventBus) {\n"
        "        ENTITY_TYPES.register(modEventBus);\n"
        "    }\n"
        "\n"
        "    /** Gives every mob above its attributes ({@code EntityAttributeCreationEvent} on the mod bus). */\n"
        "    public static void createAttributes(EntityAttributeCreationEvent event) {\n" + puts + "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _drop_expectation(drop: MobDropDef) -> str:
    return (
        f"new DropExpectation({string_literal(drop.item)}, {drop.count.min}, {drop.count.max}, "
        f"{_double(percent_to_fraction(drop.chance_percent))})"
    )


def _expectation(mob: MobDef) -> str:
    values = mob.attributes
    spells = ", ".join(
        string_literal(spell) for spell in (mob.caster.spells if mob.caster is not None else [])
    )
    keep_distance = mob.caster.keep_distance if mob.caster is not None else 0
    drops = ",\n                            ".join(_drop_expectation(drop) for drop in mob.loot)
    return (
        "            new MobExpectation(\n"
        f"                    {string_literal(mob_id(mob.id))},\n"
        f"                    {string_literal(mob_description_id(mob.id))},\n"
        f"                    {string_literal(mob.archetype)},\n"
        f"                    {_float(mob.size.width)}, {_float(mob.size.height)},\n"
        f"                    {_double(values.max_health)}, {_double(values.attack_damage)}, {_double(values.armor)},\n"
        f"                    {_double(values.movement_speed)}, {_double(values.follow_range)}, "
        f"{_double(percent_to_fraction(values.knockback_resistance_percent))},\n"
        f"                    {string_literal(mob.idle)},\n"
        f"                    List.of({spells}), {_double(keep_distance)},\n"
        f"                    {string_literal(mob_loot_table(mob.id))},\n"
        f"                    List.of({drops}))"
    )


def _expectations(content: Content) -> bytes:
    rows = [_expectation(entry.model) for entry in content.mobs]
    mobs = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
    source = (
        header(Path(__file__), SOURCES) + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/**\n"
        " * What {@code content/mobs/} says about every custom mob, so GameTests can check that the game agrees. Ids are\n"
        " * full resource locations, e.g. {@code realmforge:hewn_sentinel}; numbers are in game units (fractions).\n"
        " */\n"
        "public final class GeneratedMobExpectations {\n"
        "    /** One drop of its loot table: how many of the item (from min to max) and the chance it drops at all. */\n"
        "    public record DropExpectation(String item, int min, int max, double chance) {\n"
        "    }\n"
        "\n"
        "    /** Expected state of one mob: its entity type, attributes at level 1, behaviour and loot. */\n"
        "    public record MobExpectation(String id, String descriptionId, String archetype, float width, float height,\n"
        "            double maxHealth, double attackDamage, double armor, double movementSpeed, double followRange,\n"
        "            double knockbackResistance, String idle, List<String> spells, double keepDistance, String lootTable,\n"
        "            List<DropExpectation> loot) {\n"
        "    }\n"
        "\n"
        f"    public static final List<MobExpectation> MOBS = {mobs};\n"
        "\n"
        "    private GeneratedMobExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
