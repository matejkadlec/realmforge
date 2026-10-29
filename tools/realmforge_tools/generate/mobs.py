"""Custom mobs: the entity type registrations, loot tables, entity type tags, placeholder models, animations and
textures, and the QA mod's expectations (the names: the lang files, lang.py). Architecture 14.2.

The placeholder model fills the mob's hitbox in the build its YAML picks (``humanoid``, ``slim`` or ``golem``), with the
bones the archetypes animate (``head``, ``body``, ``left_arm``, ``right_arm``, ``left_leg``, ``right_leg``), in the
Bedrock geometry format GeckoLib reads; its texture paints each box by the YAML's surface, eyes and colours, one
texture per variant; its animations are the ones the archetypes play (``misc.idle``, ``move.walk``, ``attack.swing``,
``attack.cast``, ``attack.slam``). Its spec says where its raised hands are while it casts, so a projectile that its spell
forms grows there. A2 (RLM-48) replaces them with real art.
"""

from __future__ import annotations

import hashlib
import math
from collections.abc import Iterator
from dataclasses import dataclass, replace
from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.formats.png import Rgba, encode_rgba
from realmforge_tools.generate.datapack import data_dir, percent_to_fraction, seconds_to_ticks
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.common import LocalizedText
from realmforge_tools.schema.mob import (
    BOSS,
    DropDef,
    MobDef,
    MobDropDef,
    MobVariantDef,
    idle_of,
    melee_of,
    mob_description_id,
    mob_id,
    mob_loot_table,
    tags_of,
)

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
SOURCES = "content/mobs/"

# YAML archetype -> its class in mob/custom/. A boss's body (boss/BossMob) belongs to the boss feature, above mob, so
# its registration creates it through mob/custom/Archetypes, which that feature fills (architecture 4.4).
ARCHETYPE_CLASSES = {"melee": "MeleeMob", "caster": "CasterMob"}
_IDLE = {"stroll": "STROLL", "guard": "GUARD"}
# How far away clients see the mob, in chunks: vanilla's for monsters, and a vanilla boss's for a boss, whose boss bar
# shows while its client tracks it (architecture 14.3).
CLIENT_TRACKING_RANGE = 8
BOSS_TRACKING_RANGE = 10
PIXELS_PER_BLOCK = 16
# The placeholder texture's side: the smallest of these that the model's box UVs fit.
TEXTURE_SIZES = (64, 128, 256)
# How far the arms swing up from hanging while a mob casts with its arms raised (attack.cast), in degrees: forward and
# 30 degrees above level (Matěj's playtests, 2026-10-23 and 2026-10-24).
CAST_RAISE = 120
# One slam of attack.slam, in seconds: the fists come down as a channel of this interval ticks (a quake).
SLAM_SECONDS = 0.75
LOOTING = "minecraft:looting"


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
    loot_dir = data_dir(paths) / "loot_table" / "entities"
    for entry in content.mobs:
        mob = entry.model
        model = placeholder_model(mob)
        files[loot_dir / f"{mob.id}.json"] = dump_json(loot_table(mob))
        for variant in mob.variants:
            files[loot_dir / mob.id / f"{variant.id}.json"] = dump_json(loot_table(mob, variant))
        files[assets / "geckolib" / "models" / "entity" / f"{mob.id}.geo.json"] = dump_json(
            model.geometry(mob.id)
        )
        files[assets / "geckolib" / "animations" / "entity" / f"{mob.id}.animation.json"] = dump_json(
            animations(mob)
        )
        for variant in mob.variants or [None]:
            files[assets / "textures" / "entity" / f"{texture_name(mob, variant)}.png"] = placeholder_texture(
                mob, model, variant
            )
    tags: dict[str, list[str]] = {}
    for entry in content.mobs:
        for tag in tags_of(entry.model):
            tags.setdefault(tag, []).append(mob_id(entry.model.id))
    for tag, mobs in sorted(tags.items()):
        namespace, path = tag.split(":", 1)
        tag_file = (
            paths.mod_generated / "resources" / "data" / namespace / "tags" / "entity_type" / f"{path}.json"
        )
        files[tag_file] = dump_json({"values": sorted(mobs)})
    return files


def translations(content: Content) -> dict[str, LocalizedText]:
    """Every mob's name, by its entity type's translation key ``entity.realmforge.<id>``."""
    return {mob_description_id(entry.model.id): entry.model.name for entry in content.mobs}


def texture_name(mob: MobDef, variant: MobVariantDef | None) -> str:
    """The texture's name under ``textures/entity/``: the mob's id, a variant's ``<id>_<variant>``."""
    return mob.id if variant is None else f"{mob.id}_{variant.id}"


def loot_table(mob: MobDef, variant: MobVariantDef | None = None) -> dict[str, object]:
    """The mob's vanilla loot table, or a variant's with the mob's own drops and its own: one pool per drop, so each
    drop rolls on its own."""
    drops = mob.loot + (variant.loot if variant is not None else [])
    return {
        "type": "minecraft:entity",
        "pools": [drop_pool(drop.item, drop) for drop in drops],
        "random_sequence": mob_loot_table(mob.id, variant.id if variant is not None else None),
    }


def _uniform(low: float, high: float) -> dict[str, object]:
    return {"type": "minecraft:uniform", "min": float(low), "max": float(high)}


def drop_pool(item: str, drop: DropDef) -> dict[str, object]:
    """One drop's pool of ``item``: its count, Looting's extra count (vanilla's ``enchanted_count_increase``) and its
    chance, which Looting raises by its points per level (vanilla's ``random_chance_with_enchanted_bonus``)."""
    entry: dict[str, object] = {"type": "minecraft:item", "name": item}
    functions: list[dict[str, object]] = []
    if (drop.count.min, drop.count.max) != (1, 1):
        functions.append(
            {
                "function": "minecraft:set_count",
                "count": _uniform(drop.count.min, drop.count.max),
                "add": False,
            }
        )
    if drop.looting_count > 0:
        functions.append(
            {
                "function": "minecraft:enchanted_count_increase",
                "enchantment": LOOTING,
                "count": _uniform(0, drop.looting_count),
            }
        )
    if functions:
        entry["functions"] = functions
    pool: dict[str, object] = {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry]}
    chance = percent_to_fraction(drop.chance_percent)
    if drop.looting_chance_percent > 0:
        bonus = percent_to_fraction(drop.looting_chance_percent)
        pool["conditions"] = [
            {
                "condition": "minecraft:random_chance_with_enchanted_bonus",
                "enchantment": LOOTING,
                "unenchanted_chance": chance,
                "enchanted_chance": {
                    "type": "minecraft:linear",
                    "base": round(chance + bonus, 10),
                    "per_level_above_first": bonus,
                },
            }
        ]
    elif drop.chance_percent < 100:
        pool["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return pool


@dataclass(frozen=True)
class Cube:
    """One box of the placeholder model, in model pixels: its bone, origin (lowest corner), size, texture corner and
    what its texture is painted as: ``surface`` (the mob's surface), ``shell`` (the surface, with the variant's gems in
    cracks of its outer face) or ``moss`` (moss hanging off the body)."""

    bone: str
    origin: tuple[float, float, float]
    size: tuple[int, int, int]
    uv: tuple[int, int] = (0, 0)
    material: str = "surface"

    def faces(self) -> Iterator[tuple[str, int, int, int, int]]:
        """Each face's name, texture corner and size, by box UV: the top and bottom in the first row, then the
        right side, front, left side and back."""
        width, height, depth = self.size
        u, v = self.uv
        yield "top", u + depth, v, width, depth
        yield "bottom", u + depth + width, v, width, depth
        yield "right", u, v + depth, depth, height
        yield "front", u + depth, v + depth, width, height
        yield "left", u + depth + width, v + depth, depth, height
        yield "back", u + 2 * depth + width, v + depth, width, height


@dataclass(frozen=True)
class PlaceholderModel:
    """A statue-like model that fills a mob's hitbox: legs, body and head stacked to its height, arms at the body's
    sides."""

    cubes: tuple[Cube, ...]
    pivots: dict[str, tuple[float, float, float]]
    texture_size: int

    def hands(self) -> tuple[float, float]:
        """Where the hands are while the mob casts with its arms raised (attack.cast at its start), in blocks in front
        of its middle and above its feet: the middle of each arm's last cube-width, half the arm's thickness back from
        its end, where a projectile its spell forms grows between the hands."""
        arm = next(cube for cube in self.cubes if cube.bone == "left_arm")
        pivot_y = self.pivots["left_arm"][1]
        reach = pivot_y - arm.origin[1] - arm.size[0] / 2
        angle = math.radians(CAST_RAISE)
        ahead = reach * math.sin(angle) / PIXELS_PER_BLOCK
        up = (pivot_y - reach * math.cos(angle)) / PIXELS_PER_BLOCK
        return round(ahead, 4), round(up, 4)

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

    def head(self) -> Cube:
        return next(cube for cube in self.cubes if cube.bone == "head")

    def eye_corners(self, eye_width: int, eye_height: int) -> list[tuple[int, int]]:
        """The top-left texture pixel of each eye on the head's front face (box UV puts it at the texture corner
        plus the depth), two fifths down the face and a quarter in from each side."""
        width, height, depth = self.head().size
        u, v = self.head().uv[0] + depth, self.head().uv[1] + depth
        row = v + max(0, height * 2 // 5 - eye_height // 2)
        inset = max(1, width // 4 - (eye_width - 1) // 2)
        return [(u + inset, row), (u + width - inset - eye_width, row)]

    def eyes(self) -> set[tuple[int, int]]:
        """Every texture pixel of the eyes, for the dot eyes' size (1 x 1)."""
        return {corner for corner in self.eye_corners(1, 1)}


# The bones of the placeholder model, the body first: every other bone is its child.
BONES = ("body", "head", "left_arm", "right_arm", "left_leg", "right_leg")


def placeholder_model(mob: MobDef) -> PlaceholderModel:
    """The placeholder model of ``mob`` in its build, the cubes' box UVs packed in rows into the smallest texture of
    ``TEXTURE_SIZES`` they fit."""
    height = round(mob.size.height * PIXELS_PER_BLOCK)
    across = mob.size.width * PIXELS_PER_BLOCK
    build = {"humanoid": _humanoid, "slim": _slim, "golem": _golem}[mob.model.build]
    cubes, pivots = build(height, across, mob.id, mob.model.arm_thickness)
    for size in TEXTURE_SIZES:
        packed = _pack(cubes, size)
        if packed is not None:
            return PlaceholderModel(tuple(packed), pivots, size)
    raise ValueError(f"the placeholder model of {mob.id} does not fit a {TEXTURE_SIZES[-1]}px texture")


Pivots = dict[str, tuple[float, float, float]]


def _limbs(
    legs: int,
    body: int,
    body_width: int,
    arm: tuple[int, int, int],
    leg: tuple[int, int, int],
    leg_gap: float,
) -> tuple[list[Cube], Pivots]:
    """Arms hanging from the shoulders at the body's sides and legs under it, ``leg_gap`` pixels apart, with the
    pivots every build shares."""
    arm_width, arm_length, arm_depth = arm
    leg_width, _, leg_depth = leg
    shoulder = legs + body
    half = body_width / 2
    cubes = [
        Cube("left_arm", (half, shoulder - arm_length, -arm_depth / 2), arm),
        Cube("right_arm", (-half - arm_width, shoulder - arm_length, -arm_depth / 2), arm),
        Cube("left_leg", (leg_gap / 2, 0, -leg_depth / 2), leg),
        Cube("right_leg", (-leg_gap / 2 - leg_width, 0, -leg_depth / 2), leg),
    ]
    pivots = {
        "body": (0, legs, 0),
        "head": (0, shoulder, 0),
        "left_arm": (half + arm_width / 2, shoulder - 1, 0),
        "right_arm": (-half - arm_width / 2, shoulder - 1, 0),
        "left_leg": (leg_gap / 2 + leg_width / 2, legs, 0),
        "right_leg": (-leg_gap / 2 - leg_width / 2, legs, 0),
    }
    return cubes, pivots


def _humanoid(height: int, across: float, mob: str, arms: int | None) -> tuple[list[Cube], Pivots]:
    """Legs and body three eighths of the height each, the head the rest; the body a little narrower than the hitbox
    and the arms outside it, as vanilla's humanoids; ``arms`` thick when the YAML sets it."""
    legs = round(height * 3 / 8)
    body = round(height * 3 / 8)
    head = height - legs - body
    body_width = max(4, round(across * 0.4) * 2)
    arm = arms or max(2, round(across * 0.3))
    depth = max(2, round(across * 0.45))
    head_width = min(body_width, max(4, head))
    leg_width = body_width // 2
    limbs, pivots = _limbs(legs, body, body_width, (arm, body, arm), (leg_width, legs, depth), 0)
    cubes = [
        Cube("body", (-body_width / 2, legs, -depth / 2), (body_width, body, depth)),
        Cube("head", (-head_width / 2, legs + body, -head_width / 2), (head_width, head, head_width)),
        *limbs,
    ]
    return cubes, pivots


def _slim(height: int, across: float, mob: str, arms: int | None) -> tuple[list[Cube], Pivots]:
    """An enderman's proportions: long thin legs (over half the height), a short body, a tall head, and thin arms
    (``arms`` thick when the YAML sets it) that hang below the hips; no wider than the hitbox."""
    legs = round(height * 0.54)
    body = round(height * 0.25)
    head = height - legs - body
    body_width = max(4, round(across * 0.35) * 2)
    depth = max(2, round(across * 0.35))
    arm = arms or 2
    limbs, pivots = _limbs(
        legs, body, body_width, (arm, round(height * 0.5), arm), (max(2, body_width // 2 - 1), legs, 3), 1
    )
    cubes = [
        Cube("body", (-body_width / 2, legs, -depth / 2), (body_width, body, depth)),
        Cube("head", (-body_width / 2, legs + body, -body_width / 2), (body_width, head, body_width)),
        *limbs,
    ]
    return cubes, pivots


def _golem(height: int, across: float, mob: str, arms: int | None) -> tuple[list[Cube], Pivots]:
    """An iron golem's proportions: short thick legs, a broad body with a shell on its front and its back, a big head,
    and long arms as thick as a player's (``arms`` thick when the YAML sets it) that hang almost to the ground; moss
    hangs off the shells' lower edges."""
    legs = round(height * 0.33)
    body = round(height * 0.40)
    head = height - legs - body
    body_width = max(6, round(across * 0.42) * 2)
    depth = max(4, round(across * 0.42))
    head_width = min(body_width - 2, max(6, round(head * 0.9)))
    shell = (max(1, body_width - 2), max(1, body - 4), 2)
    shell_y = legs + (body - shell[1]) // 2
    arm = arms or 4
    limbs, pivots = _limbs(
        legs, body, body_width, (arm, round(height * 0.65), arm), (max(2, body_width // 2 - 2), legs, 6), 2
    )
    cubes = [
        Cube("body", (-body_width / 2, legs, -depth / 2), (body_width, body, depth)),
        Cube("body", (-shell[0] / 2, shell_y, -depth / 2 - shell[2]), shell, material="shell"),
        Cube("body", (-shell[0] / 2, shell_y, depth / 2), shell, material="shell"),
        Cube("head", (-head_width / 2, legs + body, -head_width / 2), (head_width, head, head_width - 1)),
        *limbs,
    ]
    # Strands of moss under each shell, where the hash of the mob's id puts them: 2 to 4 pixels long, never below the
    # ground. A shell too narrow for them has none.
    if shell[0] >= 3 and shell_y >= 1:
        for side, z in (("front", -depth / 2 - shell[2]), ("back", depth / 2 + shell[2] - 1)):
            for strand in range(4 if side == "front" else 3):
                digest = hashlib.sha256(f"{mob}/moss/{side}/{strand}".encode()).digest()
                x = -shell[0] / 2 + 1 + (digest[0] % (shell[0] - 2))
                length = min(shell_y, 2 + digest[1] % 3)
                cubes.append(Cube("body", (x, shell_y - length, z), (1, length, 1), material="moss"))
    return cubes, pivots


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
        packed.append(replace(cube, uv=(u, v)))
        u += unwrapped[0]
        row_height = max(row_height, unwrapped[1])
    return packed


def _rgb(color: str) -> tuple[int, int, int]:
    value = int(color.removeprefix("#"), 16)
    return (value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF


def _shade(rgb: tuple[int, int, int], factor: float) -> Rgba:
    r, g, b = (min(255, max(0, round(channel * factor))) for channel in rgb)
    return r, g, b, 255


def _hash(*parts: object) -> int:
    """A byte from a hash of ``parts``, so a texture is the same on every machine."""
    return hashlib.sha256("/".join(str(part) for part in parts).encode()).digest()[0]


WHITE = (242, 242, 236)
# Vanilla stone bricks' layout: bricks 8 pixels long and 4 tall with their mortar (7 x 3 of brick), every other row
# shifted by half a brick (Matěj's playtest, 2026-10-24: rectangular and bigger, like the block).
BRICK_LENGTH = 8
BRICK_HEIGHT = 4
# Mortar: grey, darker than the bricks but never black.
MORTAR = 0.78
# Moss grows in clumps of at least this many pixels, as on mossy stone bricks: never a lone green pixel.
MOSS_CLUMP = 5
# Matěj's drawing of a sapphire crack (2026-10-24), from the top-left: "A" a dark grey rim, "C" a lighter grey, "D" the
# gem's light pixels and "B" its dark ones; "." leaves the brick as it is.
CRACK = (
    "AA.....",
    "ABC....",
    "CDBA...",
    ".CDBA..",
    ".ADBA..",
    "..ADBC.",
    "...CDBC",
    "....AA.",
)
# The rim is darker than the mortar, as the cracks of cracked stone bricks, and never black.
CRACK_SHADES = {"A": 0.5, "C": 0.64}
CRACK_GEM = {"D": 1.0, "B": 0.8}
# Cracks with a gem on each shell's outer face, where they fit without touching each other.
CRACKS_PER_FACE = 2


def _surface(mob: MobDef, face: str, x: int, y: int, size: tuple[int, int], px: int, py: int) -> Rgba:
    """The colour of the pixel at ``(x, y)`` from the corner of a face of ``size`` that sits at texture pixel
    ``(px, py)``; mossy bricks get their moss afterwards (``_paint_moss``)."""
    base = _rgb(mob.model.color)
    cell = _hash(mob.id, px, py)
    if mob.model.surface == "smooth":
        # Smooth stone: barely any flecks, and a faint edge around each face, as on vanilla's smooth stone.
        edge = x in (0, size[0] - 1) or y in (0, size[1] - 1)
        return _shade(base, (0.9 if edge else 1.0) + ((cell % 5) - 2) * 0.012)
    if mob.model.surface == "mossy_bricks":
        return _shade(base, _brick_shade(mob, face, x, y, px - x, py - y, cell))
    # Speckled stone: the base colour with lighter and darker flecks, as the block placeholders.
    factor = 1.0 + ((cell % 9) - 4) * 0.035 + (0.18 if cell > 236 else 0.0) - (0.22 if cell < 16 else 0.0)
    return _shade(base, factor)


def _brick_shade(mob: MobDef, face: str, x: int, y: int, u: int, v: int, cell: int) -> float:
    """How light a pixel of a brick face is, against the mob's colour: grey mortar, and bricks of a dozen shades as
    vanilla's stone bricks have (Matěj's playtest, 2026-10-24): each brick a little lighter or darker than the next, lit
    on its top and left edges and shaded on its bottom and right ones, with soft flecks inside."""
    row = y // BRICK_HEIGHT
    shifted = x + (BRICK_LENGTH // 2 if row % 2 else 0)
    column = shifted // BRICK_LENGTH
    bx, by = shifted % BRICK_LENGTH, y % BRICK_HEIGHT
    if by == BRICK_HEIGHT - 1 or bx == BRICK_LENGTH - 1:
        return MORTAR + ((cell % 3) - 1) * 0.02
    brick = _hash(mob.id, face, u, v, row, column)
    shade = 0.94 + (brick % 5) * 0.03
    if by == 0:
        shade += 0.07
    elif by == BRICK_HEIGHT - 2:
        shade -= 0.06
    if bx == 0:
        shade += 0.04
    elif bx == BRICK_LENGTH - 2:
        shade -= 0.04
    return shade + ((cell % 7) - 3) * 0.015


def _blob(
    seed: tuple[object, ...], start: tuple[int, int], length: int, inside: set[tuple[int, int]]
) -> set[tuple[int, int]]:
    """A clump of up to ``length`` pixels of ``inside``, grown from ``start`` one side neighbour at a time (never
    corner to corner, which would read as separate pixels), mostly down and sideways as moss hangs, the choices from a
    hash of ``seed``."""
    clump = {start} if start in inside else set()
    step = 0
    while clump and len(clump) < length and step < length * 6:
        step += 1
        ordered = sorted(clump)
        x, y = ordered[_hash(*seed, step, "at") % len(ordered)]
        dx, dy = ((1, 0), (-1, 0), (0, 1), (0, 1), (1, 0), (-1, 0), (0, -1))[_hash(*seed, step, "to") % 7]
        if (x + dx, y + dy) in inside:
            clump.add((x + dx, y + dy))
    return clump


def _paint_moss(
    pixels: dict[tuple[int, int], Rgba],
    mob: MobDef,
    cube: Cube,
    under: dict[tuple[int, int], tuple[Rgba, Rgba]],
) -> None:
    """Moss on a box of mossy bricks, in clumps of at least ``MOSS_CLUMP`` pixels as on vanilla's mossy stone
    bricks: thick on the top face, growing down from each side face's upper edge, and a few clumps lower down. Each
    mossy pixel's brick and moss go into ``under``, for ``_clear_lone_moss``."""
    moss = _rgb(mob.model.moss)
    for face, u, v, width, height in cube.faces():
        if face == "bottom" or width * height < MOSS_CLUMP * 2:
            continue
        inside = {(u + x, v + y) for x in range(width) for y in range(height)}
        starts = [(u + x, v) for x in range(0, width, 3)] if face != "top" else []
        clumps = max(1, width * height // 30) if face != "top" else max(2, width * height // 12)
        for n in range(clumps):
            digest = hashlib.sha256(f"{mob.id}/moss/{cube.origin}/{face}/{n}".encode()).digest()
            starts.append((u + digest[0] % width, v + digest[1] % max(1, height * 2 // 3)))
        for n, start in enumerate(starts):
            if _hash(mob.id, "moss-start", cube.origin, face, n) % 3 == 0:
                continue
            length = MOSS_CLUMP + _hash(mob.id, "moss-size", cube.origin, face, n) % 8
            clump = _blob((mob.id, cube.origin, face, n), start, length, inside)
            if len(clump) < MOSS_CLUMP:
                continue
            for px, py in clump:
                lit = 1.08 if (px, py - 1) not in clump else 0.92 if (px, py + 1) not in clump else 1.0
                brick = under[(px, py)][0] if (px, py) in under else pixels[(px, py)]
                pixels[(px, py)] = _shade(moss, lit * (0.9 + _hash(mob.id, "moss", px, py) % 4 * 0.05))
                under[(px, py)] = (brick, pixels[(px, py)])


def _clear_lone_moss(
    pixels: dict[tuple[int, int], Rgba], under: dict[tuple[int, int], tuple[Rgba, Rgba]]
) -> None:
    """Gives back its brick to each bit of moss that a crack or an eye cut to fewer than ``MOSS_CLUMP`` pixels."""
    left = {at for at, (_, moss) in under.items() if pixels[at] == moss}
    while left:
        clump, edge = set(), [left.pop()]
        while edge:
            x, y = edge.pop()
            clump.add((x, y))
            for near in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if near in left:
                    left.remove(near)
                    edge.append(near)
        if len(clump) < MOSS_CLUMP:
            for at in clump:
                pixels[at] = under[at][0]


def _paint_gems(
    pixels: dict[tuple[int, int], Rgba], mob: MobDef, cube: Cube, accent: tuple[int, int, int]
) -> None:
    """Cracks in a shell's outer face (the front of the front shell, the back of the back one), each Matěj's drawn
    crack: the gem's pixels along its middle, a dark grey rim around them (never black), drawn as he drew it or
    mirrored."""
    base = _rgb(mob.model.color)
    outer = "front" if cube.origin[2] < 0 else "back"
    for face, u, v, width, height in cube.faces():
        tall, wide = len(CRACK), len(CRACK[0])
        if face != outer or width < wide + 2 or height < tall + 2:
            continue
        taken: list[tuple[int, int]] = []
        for n in range(CRACKS_PER_FACE * 4):
            if len(taken) == CRACKS_PER_FACE:
                break
            gx = 1 + _hash(mob.id, face, cube.origin, n, "x") % (width - wide - 1)
            gy = 1 + _hash(mob.id, face, cube.origin, n, "y") % (height - tall - 1)
            if any(abs(gx - x) < wide and abs(gy - y) < tall for x, y in taken):
                continue
            taken.append((gx, gy))
            mirrored = _hash(mob.id, face, cube.origin, n, "mirror") % 2 == 1
            for dy, line in enumerate(CRACK):
                for dx, mark in enumerate(line):
                    at = (u + gx + (wide - 1 - dx if mirrored else dx), v + gy + dy)
                    if mark in CRACK_SHADES:
                        pixels[at] = _shade(base, CRACK_SHADES[mark])
                    elif mark in CRACK_GEM:
                        pixels[at] = _shade(accent, CRACK_GEM[mark])


def _paint_eyes(
    pixels: dict[tuple[int, int], Rgba],
    mob: MobDef,
    model: PlaceholderModel,
    accent: tuple[int, int, int] | None,
) -> None:
    """dot: one bright pixel each; tall: two above each other; gem (Matěj's edit, 2026-10-24): each eye 2x3, a column
    of white on its outer side and a column of the variant's accent on its inner side, two lighter pixels above a
    darker one."""
    base = _rgb(mob.model.color)
    if mob.model.eyes == "gem" and accent is not None:
        for n, (x, y) in enumerate(model.eye_corners(2, 3)):
            white, pupil = (x, x + 1) if n == 0 else (x + 1, x)
            for dy in range(3):
                pixels[(white, y + dy)] = (*WHITE, 255)
            pixels[(pupil, y)] = _shade(accent, 1.25)
            pixels[(pupil, y + 1)] = _shade(accent, 1.25)
            pixels[(pupil, y + 2)] = _shade(accent, 0.7)
        return
    size = (1, 2) if mob.model.eyes == "tall" else (1, 1)
    for x, y in model.eye_corners(*size):
        for dy in range(size[1]):
            pixels[(x, y + dy)] = _shade(base, 1.9)


def placeholder_texture(mob: MobDef, model: PlaceholderModel, variant: MobVariantDef | None) -> bytes:
    """The texture of ``mob`` (of ``variant``), until real art exists: each box painted by its material, mossy bricks
    with their moss, then the gems in the shells' cracks and the eyes. Pixels no box uses stay clear."""
    accent = _rgb(variant.accent) if variant is not None else None
    pixels: dict[tuple[int, int], Rgba] = {}
    under: dict[tuple[int, int], tuple[Rgba, Rgba]] = {}
    for cube in model.cubes:
        for face, u, v, width, height in cube.faces():
            for y in range(height):
                for x in range(width):
                    px, py = u + x, v + y
                    if cube.material == "moss":
                        moss = _rgb(mob.model.moss)
                        tip = 0.75 if face != "top" and y == height - 1 else 1.0
                        pixels[(px, py)] = _shade(moss, (0.85 + _hash(mob.id, px, py) % 7 * 0.05) * tip)
                    else:
                        pixels[(px, py)] = _surface(mob, face, x, y, (width, height), px, py)
        if mob.model.surface == "mossy_bricks" and cube.material != "moss":
            _paint_moss(pixels, mob, cube, under)
        if cube.material == "shell" and accent is not None:
            _paint_gems(pixels, mob, cube, accent)
    _paint_eyes(pixels, mob, model, accent)
    _clear_lone_moss(pixels, under)
    size = model.texture_size
    clear: Rgba = (0, 0, 0, 0)
    return encode_rgba(size, size, [pixels.get((x, y), clear) for y in range(size) for x in range(size)])


def _keyframes(*frames: tuple[float, tuple[float, float, float]]) -> dict[str, list[float]]:
    return {f"{time:g}" if time % 1 else f"{time:.1f}": list(rotation) for time, rotation in frames}


def _swing(amplitude: float) -> dict[str, list[float]]:
    return _keyframes((0.0, (amplitude, 0, 0)), (0.5, (-amplitude, 0, 0)), (1.0, (amplitude, 0, 0)))


# The head follows the mob's look in every animation that always plays (idle or walk): GeckoLib's molang queries give
# its pitch and its yaw from the body, and GeckoLib negates x and y rotations as Bedrock's format does.
_LOOK = {"rotation": ["query.head_x_rotation", "query.head_y_rotation", 0]}


def _cast_arm(side: int) -> dict[str, object]:
    """An arm raised forward and up toward the head (30 to 37 degrees above level: Matěj's playtests, straight up
    looked bad and level too far in front), turned in toward the other hand (``side`` 1 for the left arm, -1 for the
    right), and swaying while the stone forms."""
    return {
        "rotation": _keyframes(
            (0.0, (-CAST_RAISE, 0, -12 * side)),
            (0.5, (-CAST_RAISE - 7, 0, -16 * side)),
            (1.0, (-CAST_RAISE, 0, -12 * side)),
        )
    }


def _slam_arm() -> dict[str, object]:
    """An arm beating up and down, a slam every ``SLAM_SECONDS`` (Matěj's playtest of Astone's quake, 2026-10-29: at a
    medium speed, unlike a throw): from just in front of the legs up over the head, then down hard, the fist striking
    as the slam's time ends."""
    return {
        "rotation": _keyframes(
            (0.0, (-15, 0, 0)), (round(SLAM_SECONDS * 0.55, 4), (-165, 0, 0)), (SLAM_SECONDS, (-15, 0, 0))
        )
    }


def _swing_time(mob: MobDef) -> float:
    """How long the melee swing takes, in seconds: half the time between two hits, so a slow hitter raises and drops
    its arms slowly (Matěj's playtest, 2026-10-24: a stone golem's 0.5 s swing looked flashy), in whole ticks."""
    return seconds_to_ticks(melee_of(mob).attack_interval_seconds / 2) / 20


def animations(mob: MobDef) -> dict[str, object]:
    """The placeholder animations, by the names the archetypes play (GeckoLib's DefaultAnimations): a slow sway while
    it stands, a stiff walk (the long arms of a golem swing less), both fists hammering down for a melee hit over half
    the time between two hits, both arms raised toward the forehead while it casts, and both fists slamming up and down
    while it casts a spell whose animation is slam."""
    arm_swing = 10 if mob.model.build == "golem" else 15
    swing = _swing_time(mob)
    return {
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
                    "left_arm": {"rotation": _swing(-arm_swing)},
                    "right_arm": {"rotation": _swing(arm_swing)},
                },
            },
            "attack.swing": {
                "animation_length": swing,
                "bones": {
                    arm: {
                        "rotation": _keyframes(
                            (0.0, (0, 0, 0)),
                            (round(swing * 0.4, 4), (-150, 0, 0)),
                            (round(swing * 0.7, 4), (10, 0, 0)),
                            (swing, (0, 0, 0)),
                        )
                    }
                    for arm in ("left_arm", "right_arm")
                },
            },
            "attack.cast": {
                "loop": True,
                "animation_length": 1.0,
                "bones": {"head": _LOOK, "left_arm": _cast_arm(1), "right_arm": _cast_arm(-1)},
            },
            "attack.slam": {
                "loop": True,
                "animation_length": SLAM_SECONDS,
                "bones": {"head": _LOOK, "left_arm": _slam_arm(), "right_arm": _slam_arm()},
            },
        },
    }


def _float(value: float) -> str:
    """A Java float literal; repr() gives the shortest text that reads back as the same double."""
    return f"{float(value)!r}F"


def _double(value: float) -> str:
    return repr(float(value))


def _comment(mob: MobDef) -> str:
    english = mob.name.en_us.replace("*/", "*&#47;").replace("@", "&#64;")
    return f'{mob.archetype}, "{english}"'


def _id(value: str | None) -> str:
    return "Optional.empty()" if value is None else f"CustomMobSpec.id({string_literal(value)})"


def _spec(mob: MobDef) -> str:
    caster = mob.caster
    spells = (
        ", ".join(f"GeneratedSpells.{constant_name(spell.split(':', 1)[1])}" for spell in caster.spells)
        if caster is not None
        else ""
    )
    kiting = (
        f"Optional.of(new KiteRules({_double(caster.kiting.step_blocks)}, "
        f"{_double(percent_to_fraction(caster.kiting.speed_percent))}))"
        if caster is not None and caster.kiting is not None
        else "Optional.empty()"
    )
    sounds = mob.sounds
    step = None if sounds.step == "silent" else sounds.step
    melee = melee_of(mob)
    variants = ", ".join(
        f"new CustomMobSpec.Variant({string_literal(variant.id)}, {variant.weight})"
        for variant in mob.variants
    )
    ahead, up = placeholder_model(mob).hands()
    return (
        f"new CustomMobSpec(CustomMobSpec.Idle.{_IDLE[idle_of(mob)]},\n"
        f"            new CustomMobSpec.Sounds({_id(sounds.ambient)},\n"
        f"                    {_id(sounds.hurt)},\n"
        f"                    {_id(sounds.death)},\n"
        f"                    {_id(step)}, {'true' if sounds.step == 'silent' else 'false'}),\n"
        f"            {_id(mob.death_particles)},\n"
        f"            new CustomMobSpec.Melee({seconds_to_ticks(melee.attack_interval_seconds)}, "
        f"{_double(melee.knock_up_blocks)}),\n"
        f"            List.of({spells}), {kiting},\n"
        f"            List.of({variants}),\n"
        f"            new CustomMobSpec.Hands({_double(ahead)}, {_double(up)}))"
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


def _entity_class(mob: MobDef) -> str:
    """The Java class of the mob's entity type: its archetype's, or ``CustomMob`` for one made through
    ``Archetypes``."""
    return ARCHETYPE_CLASSES.get(mob.archetype, "CustomMob")


def _factory(mob: MobDef) -> str:
    """The entity factory of the mob's registration, as Java."""
    spec = f"{constant_name(mob.id)}_SPEC"
    if mob.archetype in ARCHETYPE_CLASSES:
        return f"(type, level) -> new {ARCHETYPE_CLASSES[mob.archetype]}(type, level, {spec})"
    return f"(type, level) -> Archetypes.create({string_literal(mob.archetype)}, type, level, {spec})"


def _tracking_range(mob: MobDef) -> int:
    return BOSS_TRACKING_RANGE if mob.archetype == BOSS else CLIENT_TRACKING_RANGE


def _registrations(content: Content) -> bytes:
    mobs = [entry.model for entry in content.mobs]
    fields = "".join(
        f"    /** {{@code {mob_id(entry.model.id)}}} from {{@code {entry.source}}}: {_comment(entry.model)}. */\n"
        f"    private static final CustomMobSpec {constant_name(entry.model.id)}_SPEC = {_spec(entry.model)};\n"
        "\n"
        f"    public static final DeferredHolder<EntityType<?>, EntityType<{_entity_class(entry.model)}>> "
        f"{constant_name(entry.model.id)} =\n"
        f"            ENTITY_TYPES.registerEntityType({string_literal(entry.model.id)},\n"
        f"                    {_factory(entry.model)},\n"
        f"                    MobCategory.MONSTER, builder -> builder.sized({_float(entry.model.size.width)}, "
        f"{_float(entry.model.size.height)})\n"
        f"                            .clientTrackingRange({_tracking_range(entry.model)}));\n"
        "\n"
        for entry in content.mobs
    )
    every = ", ".join(constant_name(mob.id) for mob in mobs)
    puts = "".join(f"        event.put({constant_name(mob.id)}.get(), {_attributes(mob)});\n" for mob in mobs)
    custom_imports = "".join(
        f"import {JAVA_PACKAGE}.mob.custom.{name};\n"
        for name in sorted(
            {_entity_class(mob) for mob in mobs}
            | {"CustomMob", "CustomMobSpec"}
            | ({"Archetypes"} if any(mob.archetype not in ARCHETYPE_CLASSES for mob in mobs) else set())
        )
    )
    source = (
        header(Path(__file__), SOURCES) + f"package {GENERATED_PACKAGE};\n"
        "\n"
        f"import {JAVA_PACKAGE}.Realmforge;\n"
        f"import {JAVA_PACKAGE}.mob.ai.KiteRules;\n" + custom_imports + "import java.util.List;\n"
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
        " * {@code realmforge:entities/<id>}, or its variant's, {@code realmforge:entities/<id>/<variant>}.\n"
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


def drop_expectation(item: str, drop: DropDef) -> str:
    """The QA mod's {@code DropExpectation} of one drop of ``item``, as Java."""
    return (
        f"new DropExpectation({string_literal(item)}, {drop.count.min}, {drop.count.max}, "
        f"{_double(percent_to_fraction(drop.chance_percent))}, {drop.looting_count}, "
        f"{_double(percent_to_fraction(drop.looting_chance_percent))})"
    )


def _drops(drops: list[MobDropDef], indent: str) -> str:
    return "List.of(" + f",\n{indent}".join(drop_expectation(drop.item, drop) for drop in drops) + ")"


def _string(value: str | None) -> str:
    return string_literal(value if value is not None else "")


def _expectation(mob: MobDef) -> str:
    values = mob.attributes
    caster = mob.caster
    spells = ", ".join(string_literal(spell) for spell in (caster.spells if caster is not None else []))
    kiting = caster.kiting if caster is not None else None
    melee = melee_of(mob)
    sounds = mob.sounds
    variants = ",\n".join(
        f"                            new VariantExpectation({string_literal(variant.id)}, {variant.weight}, "
        f"{string_literal(mob_loot_table(mob.id, variant.id))},\n"
        f"                                    {_drops(mob.loot + variant.loot, '                                    ')})"
        for variant in mob.variants
    )
    tags = ", ".join(string_literal(tag) for tag in tags_of(mob))
    ahead, up = placeholder_model(mob).hands()
    return (
        "            new MobExpectation(\n"
        f"                    {string_literal(mob_id(mob.id))},\n"
        f"                    {string_literal(mob_description_id(mob.id))},\n"
        f"                    {string_literal(mob.archetype)},\n"
        f"                    {_float(mob.size.width)}, {_float(mob.size.height)},\n"
        f"                    {_double(values.max_health)}, {_double(values.attack_damage)}, {_double(values.armor)},\n"
        f"                    {_double(values.movement_speed)}, {_double(values.follow_range)}, "
        f"{_double(percent_to_fraction(values.knockback_resistance_percent))},\n"
        f"                    {string_literal(idle_of(mob))},\n"
        f"                    {seconds_to_ticks(melee.attack_interval_seconds)}, {_double(melee.knock_up_blocks)},\n"
        f"                    List.of({spells}),\n"
        f"                    {_double(kiting.step_blocks if kiting else 0)}, "
        f"{_double(percent_to_fraction(kiting.speed_percent) if kiting else 0)},\n"
        f"                    {_string(sounds.ambient)}, {_string(sounds.hurt)}, {_string(sounds.death)},\n"
        f"                    {_string(None if sounds.step == 'silent' else sounds.step)}, "
        f"{'true' if sounds.step == 'silent' else 'false'},\n"
        f"                    {_string(mob.death_particles)},\n"
        f"                    List.of({tags}),\n"
        f"                    {string_literal(mob_loot_table(mob.id))},\n"
        f"                    {_drops(mob.loot, '                            ')},\n"
        f"                    List.of({chr(10) + variants if variants else ''}),\n"
        f"                    {_double(ahead)}, {_double(up)})"
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
        " * full resource locations, e.g. {@code realmforge:hewn_sentinel}; numbers are in game units (ticks, fractions);\n"
        ' * a sound or block the YAML leaves out is {@code ""}.\n'
        " */\n"
        "public final class GeneratedMobExpectations {\n"
        "    /**\n"
        "     * One drop of a loot table: how many of the item (from min to max), the chance it drops at all, and what each\n"
        "     * level of Looting adds: up to {@code lootingCount} more, {@code lootingChance} to the chance.\n"
        "     */\n"
        "    public record DropExpectation(String item, int min, int max, double chance, int lootingCount,\n"
        "            double lootingChance) {\n"
        "    }\n"
        "\n"
        "    /** One variant: its weight at spawn and its loot table, the mob's own drops and its own. */\n"
        "    public record VariantExpectation(String id, int weight, String lootTable, List<DropExpectation> loot) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * Expected state of one mob: its entity type, attributes at level 1, behaviour, sounds and loot, and where its\n"
        "     * hands hold a projectile its spell forms (blocks in front of its middle and above its feet).\n"
        "     */\n"
        "    public record MobExpectation(String id, String descriptionId, String archetype, float width, float height,\n"
        "            double maxHealth, double attackDamage, double armor, double movementSpeed, double followRange,\n"
        "            double knockbackResistance, String idle, int attackIntervalTicks, double knockUpBlocks,\n"
        "            List<String> spells, double kitingStepBlocks, double kitingSpeed, String ambientSound,\n"
        "            String hurtSound, String deathSound, String stepSound, boolean silentSteps, String deathParticles,\n"
        "            List<String> tags, String lootTable, List<DropExpectation> loot, List<VariantExpectation> variants,\n"
        "            double handsAhead, double handsUp) {\n"
        "    }\n"
        "\n"
        f"    public static final List<MobExpectation> MOBS = {mobs};\n"
        "\n"
        "    private GeneratedMobExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
