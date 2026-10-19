"""Content validation, in order: schema (content.py), our own ids, references against the ID universe."""

from __future__ import annotations

import copy
import difflib
import math
import re
from itertools import pairwise
from pathlib import PurePosixPath

from pydantic import BaseModel

from realmforge_tools.content import Content, RoomEntry, load_content
from realmforge_tools.id_universe import IdUniverse, load_vanilla
from realmforge_tools.problems import Problem, ValidationFailed
from realmforge_tools.project import MOD_ID, RepoPaths
from realmforge_tools.rooms.cache import universe_digest
from realmforge_tools.rooms.check import (
    ENTRANCE_MARKER,
    TEMPLATE_POOL_REGISTRY,
    check_structure,
    fluid_edges,
    is_solid,
    markers,
)
from realmforge_tools.rooms.connectors import connectors, could_attach
from realmforge_tools.rooms.generator import GENERATED_DIR, theme_blocks, unknown_blocks
from realmforge_tools.rooms.walk import walk_problems
from realmforge_tools.schema import CONTENT_TYPES, DATAPACK_TYPES, WORLDGEN_DIR
from realmforge_tools.schema.block import SINGLE_BLOCK_KINDS
from realmforge_tools.schema.chapter import advancement_id as chapter_advancement
from realmforge_tools.schema.character import STARTING_BREAD
from realmforge_tools.schema.dungeon import DEAD_END_ROLES, PATH_ROLES, ROOM_ROLES, room_role
from realmforge_tools.schema.item import USE_TEXT_GROUP
from realmforge_tools.schema.kill_xp_multiplier import SIZED_ENTITY_TYPES
from realmforge_tools.schema.leveling import LEVELING_CURVES
from realmforge_tools.schema.realm import unlock_advancement
from realmforge_tools.schema.requirement import ChapterCompletedDef, HasAdvancementDef
from realmforge_tools.schema.room_spec import FULL, LANTERN, SLAB, STAIRS, THEME_ROLES, WALL_TORCH
from realmforge_tools.schema.spell import (
    ALLY,
    APPLY_EFFECT,
    AREA,
    DAMAGE,
    DASH,
    DIRECTION,
    ENEMY,
    GROUND_POINT,
    HEAL,
    KNOCKBACK,
    PROJECTILE,
    REMOVE_EFFECT,
    SCALING_OF_KIND,
    SELF,
    TELEPORT,
    SpellDef,
)
from realmforge_tools.schema.text import text_constant
from realmforge_tools.schema.worldgen import WORLDGEN_REGISTRIES, data_path

ITEM_REGISTRY = "minecraft:item"
BLOCK_REGISTRY = "minecraft:block"
ADVANCEMENT_REGISTRY = "minecraft:advancement"
ENTITY_TYPE_REGISTRY = "minecraft:entity_type"
ATTRIBUTE_REGISTRY = "minecraft:attribute"
CREATIVE_TAB_REGISTRY = "minecraft:creative_mode_tab"
DAMAGE_TYPE_REGISTRY = "minecraft:damage_type"
DIMENSION_REGISTRY = "minecraft:dimension"
DIMENSION_TYPE_REGISTRY = "minecraft:dimension_type"
SPELL_SCHOOL_REGISTRY = f"{MOD_ID}:spell_school"
DUNGEON_TYPE_REGISTRY = f"{MOD_ID}:dungeon_type"
CHAPTER_REGISTRY = f"{MOD_ID}:chapter"
STRUCTURE_REGISTRY = "minecraft:structure"

# Vanilla's dimensions (the level stems of its world presets). The registry report behind the vanilla ID universe
# lists no minecraft:dimension registry, as a world's dimensions come from its preset, so they are named here.
VANILLA_DIMENSIONS = ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")

# YAML content type -> the registry its ids live in.
REGISTRY_OF_TYPE: dict[str, str] = {
    "items": ITEM_REGISTRY,
    "blocks": BLOCK_REGISTRY,
    "stats": ATTRIBUTE_REGISTRY,
    "spell_schools": SPELL_SCHOOL_REGISTRY,
    "dungeons": DUNGEON_TYPE_REGISTRY,
    **{t.directory: f"{MOD_ID}:{t.registry}" for t in DATAPACK_TYPES},
}

# Translation key prefixes other content types generate (`item.realmforge.<id>`, `curve.realmforge.<id>.name`,
# `attribute.realmforge.<id>`, a school's `death.attack.realmforge.<id>`): a text group with one of these ids could
# produce the same key as an entry of that type.
RESERVED_TEXT_GROUPS: dict[str, str] = {
    "item": "content/items/",
    "block": "content/blocks/",
    "attribute": "content/stats/",
    "spell_school": "content/spell_schools/",
    "death.attack": "content/spell_schools/",
    "dungeon_type": "content/dungeons/",
    **{t.registry: f"content/{t.directory}/" for t in DATAPACK_TYPES},
}


def load_and_validate(
    paths: RepoPaths, *, broken_kept_seeds: dict[tuple[str, int], list[str]] | None = None
) -> tuple[Content, IdUniverse]:
    """Load content and run every validation step. Raises ValidationFailed with all problems found.

    Given ``broken_kept_seeds``, a kept seed of a room spec that the generator cannot build, or whose room fails
    validation, is no problem: its problems go there instead, under (spec id, seed), and ``rf rooms candidates
    --kept`` refuses the seed with them, so the review can reject it (RLM-263). A generated room's problems name its
    seed (``_room_location``); a seed the generator cannot build is missing from the content and refused anyway."""
    content = load_content(paths, skip_broken_kept_seeds=broken_kept_seeds is not None)
    universe = universe_with_content(load_vanilla(paths), content)
    _add_core_mod_worldgen(universe, paths)
    problems = validate_content(content, universe) + _check_worldgen_clashes(content, paths)
    if broken_kept_seeds is not None:
        specs = {entry.source: entry.model.id for entry in content.room_specs}
        kept = []
        for problem in problems:
            seed = re.match(r"seed (\d+)", problem.location) if problem.file in specs else None
            if seed is None:
                kept.append(problem)
                continue
            where = problem.location[seed.end() :].removeprefix(": ")
            reason = f"{where}: {problem.message}" if where else problem.message
            broken_kept_seeds.setdefault((specs[problem.file], int(seed[1])), []).append(reason)
        problems = kept
    if problems:
        raise ValidationFailed(problems)
    return content, universe


def universe_with_content(vanilla: IdUniverse, content: Content) -> IdUniverse:
    """The vanilla universe plus every id our content defines."""
    universe = copy.deepcopy(vanilla)
    for dimension in VANILLA_DIMENSIONS:
        universe.add(DIMENSION_REGISTRY, dimension)
    for type_dir, registry in REGISTRY_OF_TYPE.items():
        for entry in content.entries(type_dir):
            universe.add(registry, f"{MOD_ID}:{entry.model.id}")
    # A block registers its block item under the same id (generate/blocks.py).
    for entry in content.blocks:
        universe.add(ITEM_REGISTRY, f"{MOD_ID}:{entry.model.id}")
    for entry in content.spell_schools:
        universe.add(DAMAGE_TYPE_REGISTRY, entry.model.damage_type)
    for entry in content.chapters:
        universe.add(ADVANCEMENT_REGISTRY, f"{MOD_ID}:{chapter_advancement(entry.model.id)}")
    for entry in content.realms:
        universe.add(ADVANCEMENT_REGISTRY, f"{MOD_ID}:{unlock_advancement(entry.model.id)}")
    for file in content.worldgen:
        universe.add(WORLDGEN_REGISTRIES[file.directory], f"{MOD_ID}:{file.id}")
    for room in content.rooms:
        universe.add(STRUCTURE_REGISTRY, room_structure_id(room.id))
        # A room pool is a template pool too, for the jigsaws of world structures (M4), so a jigsaw may name one.
        for pool in room.model.pools:
            universe.add(TEMPLATE_POOL_REGISTRY, pool)
    return universe


def room_structure_id(room_id: str) -> str:
    """The structure id the jar gives a room (generate/rooms.py)."""
    return f"{MOD_ID}:rooms/{room_id}"


def validate_content(content: Content, universe: IdUniverse) -> list[Problem]:
    problems: list[Problem] = []
    problems += _check_ids(content)
    problems += _check_references(content, universe)
    problems += _check_stat_rules(content, universe)
    problems += _check_spell_schools(content, universe)
    problems += _check_spells(content, universe)
    problems += _check_characters(content, universe)
    problems += _check_worldgen_references(content, universe)
    problems += _check_dimension_rules(content, universe)
    problems += _check_blocks(content, universe)
    problems += _check_realms(content, universe)
    problems += _check_text_groups(content)
    problems += _check_room_themes(content, universe)
    problems += _check_room_specs(content)
    problems += _check_rooms(content, universe)
    problems += _check_dungeons(content)
    problems += _check_dungeon_keys(content, universe)
    problems += _check_recipes(content, universe)
    problems += _check_chapters(content)
    problems += _check_leveling_curves(content)
    problems += _check_kill_xp_multipliers(content, universe)
    return problems


def _check_rooms(content: Content, universe: IdUniverse) -> list[Problem]:
    # The problems depend on the ids the room may name, so the universe's digest is part of the cache kind.
    kind = f"check-{universe_digest(universe)}"
    problems = [
        Problem(room.template, _room_location(room, location), message)
        for room in content.rooms
        for location, message in room.derived(kind, lambda structure: check_structure(structure, universe))
    ]
    problems += _check_generated_rooms(content)
    for room in content.rooms:
        source = room.model.source
        third_party = room.id.startswith("third_party/")
        if source is not None and not source.ours and not third_party:
            problems.append(
                Problem(
                    room.source,
                    "source",
                    "an imported room lives in content/rooms/third_party/<source>/ with its LICENSE and NOTICE; "
                    "set `ours: true` only for a room we built ourselves",
                )
            )
        if source is not None and source.ours and third_party:
            problems.append(Problem(room.source, "source.ours", "a room in third_party/ is not ours"))
    # A rejected room is dropped, and its decision stays, so the next import of its source skips it.
    room_ids = {room.id for room in content.rooms}
    for review in content.room_reviews:
        prefix = f"{review.folder}/" if review.folder else ""
        for key, decision in sorted(review.decisions.items()):
            if decision == "keep" and f"{prefix}{key}" not in room_ids:
                problems.append(
                    Problem(review.source, key, f"kept, but there is no room content/rooms/{prefix}{key}.nbt")
                )
    return problems


def _room_location(room: RoomEntry, location: str) -> str:
    """Where a problem in a room is: a generated room's problems are reported on its spec, so they name the seed."""
    if room.id.startswith(f"{GENERATED_DIR}/"):
        seed = room.id.rsplit("_", 1)[1]
        return f"seed {seed}: {location}" if location else f"seed {seed}"
    return location


def _check_room_themes(content: Content, universe: IdUniverse) -> list[Problem]:
    """Every block of a theme exists and is what its role needs: a full block, stairs, a slab, a wall torch, a
    lantern (``schema/room_spec.py``)."""
    needs = {
        FULL: (is_solid, "a full solid block"),
        STAIRS: (lambda name: name.endswith("_stairs"), "a stairs block"),
        SLAB: (lambda name: name.endswith("_slab"), "a slab"),
        WALL_TORCH: (lambda name: name.endswith("wall_torch"), "a wall torch"),
        LANTERN: (lambda name: name.endswith("lantern"), "a lantern"),
    }
    problems: list[Problem] = []
    for entry in content.room_themes:
        for role, need in THEME_ROLES.items():
            block = entry.model.block(role)
            check, what = needs[need]
            if not universe.contains(BLOCK_REGISTRY, block):
                problems.append(Problem(entry.source, f"blocks.{role}", f"unknown block {block!r}"))
            elif not check(block):
                problems.append(Problem(entry.source, f"blocks.{role}", f"{block} is not {what}"))
    return problems


def _check_room_specs(content: Content) -> list[Problem]:
    themes = {f"{MOD_ID}:{entry.model.id}" for entry in content.room_themes}
    return [
        Problem(entry.source, "theme", f"no theme {entry.model.theme!r} in content/room_themes/")
        for entry in content.room_specs
        if entry.model.theme not in themes
    ]


def _check_generated_rooms(content: Content) -> list[Problem]:
    """A generated room can be played (``rooms/walk.py``: its doorways reach each other with headroom, and no mob
    spawns in the dark) and is built only of its theme's blocks, so a theme swap reaches all of them."""
    specs = {entry.model.id: entry.model for entry in content.room_specs}
    themes = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.room_themes}
    problems: list[Problem] = []
    for room in content.rooms:
        if not room.id.startswith(f"{GENERATED_DIR}/"):
            continue
        for location, message in room.derived("walk", walk_problems):
            problems.append(Problem(room.source, _room_location(room, location), message))
        theme = themes[specs[room.id.split("/")[1]].theme]
        for name in room.derived(f"blocks-{theme.id}", lambda s, t=theme: unknown_blocks(s, theme_blocks(t))):
            problems.append(
                Problem(
                    room.source,
                    _room_location(room, ""),
                    f"holds {name}, which theme {theme.id} has no role for",
                )
            )
    return problems


def _check_dungeons(content: Content) -> list[Problem]:
    """A room in the pool of a dungeon type has exactly one role, and the pool holds what the type's layout needs: an
    entrance, rooms to pass through, the room that ends the main path, a dead end for the branches and for the
    connectors nothing else takes, rooms that take a branch when it must have one, and only rooms whose every
    connector meets some connector of the pool (architecture 15.3). A room in such a pool also holds no fluid at its
    edge, and an entrance marker only if it is an entrance (``_check_pooled_room``). Whether a layout fits a plot is
    the planner's to find; JUnit fuzzes it over the generated types."""
    problems: list[Problem] = []
    used_pools = {entry.model.pool for entry in content.dungeons}
    pooled: dict[str, list[RoomEntry]] = {}
    for room in content.rooms:
        if not used_pools & set(room.model.pools):
            continue
        if room_role(room.model.tags) is None:
            roles = [tag for tag in room.model.tags if tag in ROOM_ROLES]
            problems.append(
                Problem(
                    room.source,
                    _room_location(room, "tags"),
                    f"a room in a dungeon type's pool has exactly one role tag ({', '.join(ROOM_ROLES)}); "
                    f"this one has {', '.join(roles) if roles else 'none'}",
                )
            )
            continue
        problems += _check_pooled_room(room)
        for pool in room.model.pools:
            pooled.setdefault(pool, []).append(room)

    for entry in content.dungeons:
        dungeon = entry.model
        rooms = pooled.get(dungeon.pool, [])
        if not rooms:
            problems.append(Problem(entry.source, "pool", f"no room lists {dungeon.pool} in its pools"))
            continue
        shapes = [
            (room, room_role(room.model.tags), room.derived("connectors", connectors)) for room in rooms
        ]
        ending = dungeon.layout.ending
        needs = (
            (
                "pool",
                "entrance (role entrance, a connector)",
                lambda role, n: role == "entrance" and n >= 1,
            ),
            (
                "pool",
                f"room to pass through (role {', '.join(PATH_ROLES)}; two connectors)",
                lambda role, n: role in PATH_ROLES and n >= 2,
            ),
            (
                "layout.ending",
                f"room to end the main path (role {ending}, a connector)",
                lambda role, n, ending=ending: role == ending and n >= 1,
            ),
            (
                "pool",
                f"dead end for the branches and open connectors (role {', '.join(DEAD_END_ROLES)}, one connector)",
                lambda role, n: role in DEAD_END_ROLES and n == 1,
            ),
        )
        for location, what, fits in needs:
            if not any(fits(role, len(found)) for _, role, found in shapes):
                problems.append(Problem(entry.source, location, f"pool {dungeon.pool} has no {what}"))
        if dungeon.layout.branches[0] > 0 and not any(
            role in PATH_ROLES and len(found) >= 3 for _, role, found in shapes
        ):
            problems.append(
                Problem(
                    entry.source,
                    "layout.branches",
                    f"every layout has a branch, but pool {dungeon.pool} has no room to pass through with a third "
                    "connector to take it",
                )
            )
        everything = [connector for _, _, found in shapes for connector in found]
        for room, _, found in shapes:
            for connector in found:
                if not any(could_attach(connector, other) for other in everything):
                    position = " ".join(str(c) for c in connector.pos)
                    problems.append(
                        Problem(
                            room.source,
                            _room_location(room, "pools"),
                            f"its connector at {position} (facing {connector.front}, target {connector.target}) "
                            f"meets no connector in pool {dungeon.pool}, so {entry.source} can never place it",
                        )
                    )
    return problems


def _check_pooled_room(room: RoomEntry) -> list[Problem]:
    """What a room needs once a dungeon builds it into a plot: no fluid that can reach its sides or floor, from where
    it would flow out of its box past what cleanup clears (architecture 15.5), and an entrance marker only in the entrance, where players
    arrive (the builder reads only the entrance room's)."""
    problems: list[Problem] = []
    edges = room.derived("fluid_edges", fluid_edges)
    if edges:
        count = f" ({len(edges)} blocks)" if len(edges) > 1 else ""
        problems.append(
            Problem(
                room.template,
                _room_location(room, f"fluid at {' '.join(str(c) for c in edges[0])}"),
                f"a fluid that can reach the room's sides or floor{count} flows out of its box, where cleanup does "
                "not reach; a room in a dungeon type's pool keeps its fluids inside",
            )
        )
    role = room_role(room.model.tags)
    for name, pos in room.derived("markers", markers):
        if name == ENTRANCE_MARKER and role != "entrance":
            problems.append(
                Problem(
                    room.template,
                    _room_location(room, f"data marker {' '.join(str(c) for c in pos)}"),
                    f"an entrance marker in a room whose role is {role}: players arrive only in the entrance room",
                )
            )
    return problems


def _check_ids(content: Content) -> list[Problem]:
    problems: list[Problem] = []
    for type_dir in CONTENT_TYPES:
        kind = type_dir.removesuffix("s")
        seen: dict[str, str] = {}
        for entry in content.entries(type_dir):
            entry_id = entry.model.id
            stem = PurePosixPath(entry.source).stem
            if entry_id != stem:
                problems.append(
                    Problem(entry.source, "id", f"{entry_id!r} must equal the file name {stem!r}")
                )
            if entry_id in seen:
                problems.append(
                    Problem(
                        entry.source, "id", f"duplicate {kind} id {entry_id!r} (also in {seen[entry_id]})"
                    )
                )
            seen.setdefault(entry_id, entry.source)
    return problems


def _check_references(content: Content, universe: IdUniverse) -> list[Problem]:
    problems: list[Problem] = []
    use_texts = {
        name for entry in content.texts if entry.model.id == USE_TEXT_GROUP for name in entry.model.texts
    }
    for entry in content.items:
        for i, tab in enumerate(entry.model.creative_tabs):
            if not universe.contains(CREATIVE_TAB_REGISTRY, tab):
                problems.append(
                    Problem(
                        entry.source, f"creative_tabs.{i}", _unknown(tab, CREATIVE_TAB_REGISTRY, universe)
                    )
                )
        use = entry.model.use
        if use is not None and use not in use_texts:
            problems.append(
                Problem(
                    entry.source,
                    "use",
                    f"{use} needs its tooltip line: {use} in content/texts/{USE_TEXT_GROUP}.yaml",
                )
            )
    return problems


def _check_stat_rules(content: Content, universe: IdUniverse) -> list[Problem]:
    """A rule reads one primary stat, which no other rule reads, and writes only secondary stats and vanilla
    attributes (architecture 8.3). An add_value amount on one of our stats takes its unit, percent for a share; a
    vanilla attribute takes either, as its unit is not catalogued (knockback resistance is a fraction)."""
    problems: list[Problem] = []
    stats = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.stats}
    rule_of_stat: dict[str, str] = {}
    for entry in content.stat_rules:
        rule = entry.model
        stat = stats.get(rule.stat)
        if not universe.contains(ATTRIBUTE_REGISTRY, rule.stat):
            problems.append(Problem(entry.source, "stat", _unknown(rule.stat, ATTRIBUTE_REGISTRY, universe)))
        elif stat is None or stat.kind != "primary":
            problems.append(
                Problem(
                    entry.source, "stat", f"{rule.stat} is not a primary stat (content/stats/, kind: primary)"
                )
            )
        elif rule.stat in rule_of_stat:
            problems.append(
                Problem(
                    entry.source, "stat", f"{rule.stat} already has a stat rule ({rule_of_stat[rule.stat]})"
                )
            )
        rule_of_stat.setdefault(rule.stat, entry.source)
        if stat is not None and stat.kind == "primary":
            # A stat never exceeds its maximum, so an uncapped total stays finite up to there or never.
            for n, per_point in enumerate(rule.per_point):
                if per_point.game_max is None and not math.isfinite(per_point.game_amount * stat.game_max):
                    problems.append(
                        Problem(
                            entry.source,
                            f"per_point.{n}",
                            f"the amount overflows a double at {rule.stat}'s maximum ({stat.game_max:g} points); "
                            "lower it or give it a cap",
                        )
                    )

        for path, modifier in rule.modifiers():
            location = f"{path}.attribute"
            target = stats.get(modifier.attribute)
            if not universe.contains(ATTRIBUTE_REGISTRY, modifier.attribute):
                problems.append(
                    Problem(
                        entry.source, location, _unknown(modifier.attribute, ATTRIBUTE_REGISTRY, universe)
                    )
                )
            elif target is not None and target.kind == "primary":
                problems.append(
                    Problem(
                        entry.source,
                        location,
                        f"stat rules write secondary stats and vanilla attributes, not the primary stat "
                        f"{modifier.attribute}",
                    )
                )
            elif modifier.operation == "add_value" and target is not None:
                share = target.percent
                if share and not modifier.is_percent:
                    problems.append(
                        Problem(
                            entry.source,
                            f"{path}.amount",
                            f"{modifier.attribute} is a share: write the amount as amount_percent",
                        )
                    )
                elif not share and modifier.is_percent:
                    problems.append(
                        Problem(
                            entry.source,
                            f"{path}.amount_percent",
                            f"{modifier.attribute} counts points: add_value takes amount, not amount_percent",
                        )
                    )
    return problems


def _check_spell_schools(content: Content, universe: IdUniverse) -> list[Problem]:
    """A school's power and resistance are secondary stats that are shares (architecture 9.1), and no stat serves
    two schools or two roles: the damage pipeline reads each as one school's bonus."""
    problems: list[Problem] = []
    stats = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.stats}
    used: dict[str, str] = {}
    for entry in content.spell_schools:
        school = entry.model
        for field in ("power", "resistance"):
            stat_id = getattr(school, field)
            if stat_id is None:
                continue
            stat = stats.get(stat_id)
            if not universe.contains(ATTRIBUTE_REGISTRY, stat_id):
                problems.append(Problem(entry.source, field, _unknown(stat_id, ATTRIBUTE_REGISTRY, universe)))
            elif stat is None:
                problems.append(
                    Problem(entry.source, field, f"{stat_id} is not one of our stats (content/stats/)")
                )
            elif stat.kind != "secondary" or not stat.percent:
                problems.append(
                    Problem(
                        entry.source,
                        field,
                        f"{stat_id} must be a secondary stat that is a share (kind: secondary, max_percent)",
                    )
                )
            if stat_id in used:
                problems.append(Problem(entry.source, field, f"{stat_id} already serves {used[stat_id]}"))
            used.setdefault(stat_id, f"{entry.source} ({field})")
    return problems


# Which targeting types each effect may use: damage and knockback hit enemies, heals mend the caster or its allies,
# status effects go on anyone; a projectile flies at an enemy, a ground point or along a direction, an area lies at the
# caster's feet, an enemy's or a ground point, a teleport needs a point to go to, and a dash rushes at an enemy, a
# point or along a direction.
_EFFECT_TARGETING: dict[str, tuple[str, ...]] = {
    DAMAGE: (ENEMY,),
    HEAL: (SELF, ALLY),
    APPLY_EFFECT: (SELF, ENEMY, ALLY),
    REMOVE_EFFECT: (SELF, ENEMY, ALLY),
    KNOCKBACK: (ENEMY,),
    PROJECTILE: (ENEMY, GROUND_POINT, DIRECTION),
    AREA: (SELF, ENEMY, GROUND_POINT),
    TELEPORT: (GROUND_POINT, DIRECTION),
    DASH: (ENEMY, GROUND_POINT, DIRECTION),
}
MOB_EFFECT_REGISTRY = "minecraft:mob_effect"


def _check_spells(content: Content, universe: IdUniverse) -> list[Problem]:
    """A spell's school exists, and the stat it scales with fits the school's kind: off-class stats do nothing for a
    class's abilities (Matěj, 2026-10-15), so magic scales with spell power and physical abilities with attack damage.
    An attack damage ability names its share of attack damage on every damage effect, and a spell power one names
    none. Every effect fits the spell's targeting (damage hits enemies, heals mend the caster or its allies), and the
    effects a projectile or an area runs fit whom it reaches. Status effects exist."""
    problems: list[Problem] = []
    schools = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.spell_schools}
    for entry in content.spells:
        spell = entry.model
        school = schools.get(spell.school)
        if not universe.contains(SPELL_SCHOOL_REGISTRY, spell.school):
            problems.append(
                Problem(entry.source, "school", _unknown(spell.school, SPELL_SCHOOL_REGISTRY, universe))
            )
        elif school is not None and SCALING_OF_KIND[school.kind] != spell.scales_with:
            problems.append(
                Problem(
                    entry.source,
                    "scales_with",
                    f"{spell.school} is a {school.kind} school, so the spell scales with "
                    f"{SCALING_OF_KIND[school.kind]}, not {spell.scales_with}",
                )
            )
        for n, effect in enumerate(spell.effects):
            path = f"effects.{n}"
            problems += _check_effect(entry.source, path, effect, spell, universe)
            allowed = _EFFECT_TARGETING[effect.type]
            if spell.targeting.type not in allowed:
                problems.append(
                    Problem(
                        entry.source,
                        f"{path}.type",
                        f"{effect.type} acts on the spell's targets, so the targeting is {' or '.join(allowed)}, "
                        f"not {spell.targeting.type}",
                    )
                )
            problems += _check_inner_effects(entry.source, path, effect, spell, universe)
    return problems


def _check_inner_effects(
    source: str, path: str, effect: BaseModel, spell: SpellDef, universe: IdUniverse
) -> list[Problem]:
    """The effects a projectile runs on the enemy it hits, or an area on the enemies or allies inside it, fit them as
    the spell's targeting would."""
    if effect.type == PROJECTILE:
        reached, what = ENEMY, "the enemy a projectile hits"
    elif effect.type == AREA:
        reached, what = (
            (ENEMY, "an area's enemies") if effect.affects == "enemies" else (ALLY, "an area's allies")
        )
    else:
        return []
    problems: list[Problem] = []
    for m, inner in enumerate(effect.effects):
        inner_path = f"{path}.effects.{m}"
        problems += _check_effect(source, inner_path, inner, spell, universe)
        if reached not in _EFFECT_TARGETING[inner.type]:
            problems.append(Problem(source, f"{inner_path}.type", f"{inner.type} cannot act on {what}"))
    return problems


def _check_effect(
    source: str, path: str, effect: BaseModel, spell: SpellDef, universe: IdUniverse
) -> list[Problem]:
    """An effect's share of attack damage fits what the spell scales with, and its status effect exists."""
    problems: list[Problem] = []
    percent = getattr(effect, "attack_damage_percent", None)
    if spell.scales_with == "spell_power" and percent is not None:
        problems.append(
            Problem(
                source,
                f"{path}.attack_damage_percent",
                "the spell scales with spell_power, so attack damage adds nothing to it",
            )
        )
    if spell.scales_with == "attack_damage" and effect.type == DAMAGE and percent is None:
        problems.append(
            Problem(
                source,
                f"{path}.attack_damage_percent",
                "the spell scales with attack_damage: name the share of attack damage its damage adds",
            )
        )
    status = getattr(effect, "effect", None)
    if status is not None and not universe.contains(MOB_EFFECT_REGISTRY, status):
        problems.append(Problem(source, f"{path}.effect", _unknown(status, MOB_EFFECT_REGISTRY, universe)))
    return problems


def _check_characters(content: Content, universe: IdUniverse) -> list[Problem]:
    """Races name classes that exist, and every class is open to some race, or no player could take it. Modifiers
    name attributes that exist, in their unit as for stat rules (architecture 8.3); kit items exist, and every kit
    holds the starting bread."""
    problems: list[Problem] = []
    stats = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.stats}
    class_registry = f"{MOD_ID}:class"
    taken: set[str] = set()
    for entry in content.races:
        for n, class_id in enumerate(entry.model.classes):
            taken.add(class_id)
            if not universe.contains(class_registry, class_id):
                problems.append(
                    Problem(entry.source, f"classes.{n}", _unknown(class_id, class_registry, universe))
                )
    for entry in (*content.races, *content.classes):
        for n, modifier in enumerate(entry.model.modifiers):
            path = f"modifiers.{n}"
            target = stats.get(modifier.attribute)
            if not universe.contains(ATTRIBUTE_REGISTRY, modifier.attribute):
                problems.append(
                    Problem(
                        entry.source,
                        f"{path}.attribute",
                        _unknown(modifier.attribute, ATTRIBUTE_REGISTRY, universe),
                    )
                )
            elif modifier.operation == "add_value" and target is not None:
                if target.percent and not modifier.is_percent:
                    problems.append(
                        Problem(
                            entry.source,
                            f"{path}.amount",
                            f"{modifier.attribute} is a share: write the amount as amount_percent",
                        )
                    )
                elif not target.percent and modifier.is_percent:
                    problems.append(
                        Problem(
                            entry.source,
                            f"{path}.amount_percent",
                            f"{modifier.attribute} counts points: add_value takes amount, not amount_percent",
                        )
                    )
    bread, bread_count = STARTING_BREAD
    for entry in content.classes:
        class_id = f"{MOD_ID}:{entry.model.id}"
        kit_bread = sum(item.count for item in entry.model.starting_kit if item.item == bread)
        if kit_bread != bread_count:
            problems.append(
                Problem(
                    entry.source,
                    "starting_kit",
                    f"every class starts with {bread} x{bread_count} (Matěj, 2026-10-14); this kit has {kit_bread}",
                )
            )
        if class_id not in taken:
            problems.append(
                Problem(
                    entry.source, "id", f"no race lists {class_id} in its classes, so no player can take it"
                )
            )
        for n, kit_item in enumerate(entry.model.starting_kit):
            if not universe.contains(ITEM_REGISTRY, kit_item.item):
                problems.append(
                    Problem(
                        entry.source,
                        f"starting_kit.{n}.item",
                        _unknown(kit_item.item, ITEM_REGISTRY, universe),
                    )
                )
    return problems


def _check_text_groups(content: Content) -> list[Problem]:
    """Text groups must not reuse another type's key prefix, and their Java constants must be unique."""
    problems: list[Problem] = []
    constants: dict[str, str] = {}
    for entry in content.texts:
        group = entry.model.id
        if group in RESERVED_TEXT_GROUPS:
            problems.append(
                Problem(
                    entry.source,
                    "id",
                    f"{group!r} is the translation key prefix of {RESERVED_TEXT_GROUPS[group]}; "
                    "name the group after what the texts are (key, message, ...)",
                )
            )
        for name in entry.model.texts:
            constant = text_constant(group, name)
            if constant in constants:
                problems.append(
                    Problem(
                        entry.source,
                        f"texts.{name}",
                        f"Java constant {constant} is also generated for {constants[constant]}",
                    )
                )
            constants.setdefault(constant, f"{entry.source} (texts.{name})")
    return problems


def _check_worldgen_references(content: Content, universe: IdUniverse) -> list[Problem]:
    """The references Python can check cheaply; the game decodes the rest when a world loads."""
    problems: list[Problem] = []
    for file in content.worldgen:
        if file.directory != "dimension":
            continue
        dimension_type = file.data.get("type")
        if dimension_type is None:
            problems.append(Problem(file.source, "type", "a dimension needs a type (a dimension_type id)"))
        elif isinstance(dimension_type, str) and not universe.contains(
            DIMENSION_TYPE_REGISTRY, dimension_type
        ):
            problems.append(
                Problem(file.source, "type", _unknown(dimension_type, DIMENSION_TYPE_REGISTRY, universe))
            )
    return problems


def _check_dimension_rules(content: Content, universe: IdUniverse) -> list[Problem]:
    """Each entry names a dimension that exists, and no two name the same one: the server indexes the rules by
    dimension (architecture 5.2)."""
    problems: list[Problem] = []
    seen: dict[str, str] = {}
    for entry in content.dimensions:
        dimension = entry.model.dimension
        if not universe.contains(DIMENSION_REGISTRY, dimension):
            problems.append(
                Problem(entry.source, "dimension", _unknown(dimension, DIMENSION_REGISTRY, universe))
            )
        if dimension in seen:
            problems.append(
                Problem(entry.source, "dimension", f"{dimension} already has rules in {seen[dimension]}")
            )
        seen.setdefault(dimension, entry.source)
        for n, requirement in enumerate(entry.model.access or []):
            location = f"access.{n}"
            if isinstance(requirement, HasAdvancementDef) and not universe.contains(
                ADVANCEMENT_REGISTRY, requirement.advancement
            ):
                problems.append(
                    Problem(
                        entry.source,
                        f"{location}.advancement",
                        _unknown(requirement.advancement, ADVANCEMENT_REGISTRY, universe),
                    )
                )
            if isinstance(requirement, ChapterCompletedDef) and not universe.contains(
                CHAPTER_REGISTRY, requirement.chapter
            ):
                problems.append(
                    Problem(
                        entry.source,
                        f"{location}.chapter",
                        _unknown(requirement.chapter, CHAPTER_REGISTRY, universe),
                    )
                )
    return problems


def _check_blocks(content: Content, universe: IdUniverse) -> list[Problem]:
    """A block's item takes the block's id, so no item may have it too; its creative tabs exist; a kind the game uses
    one block of (the dungeon exit) has one block at most."""
    problems: list[Problem] = []
    items = {entry.model.id: entry.source for entry in content.items}
    kind_blocks: dict[str, str] = {}
    for entry in content.blocks:
        block = entry.model
        if block.kind in SINGLE_BLOCK_KINDS:
            if block.kind in kind_blocks:
                problems.append(
                    Problem(
                        entry.source,
                        "kind",
                        f"the game uses one {block.kind} block, and {kind_blocks[block.kind]} is one already",
                    )
                )
            kind_blocks.setdefault(block.kind, entry.source)
        if block.id in items:
            problems.append(
                Problem(
                    entry.source, "id", f"the block's item would clash with the item in {items[block.id]}"
                )
            )
        for i, tab in enumerate(block.creative_tabs):
            if not universe.contains(CREATIVE_TAB_REGISTRY, tab):
                problems.append(
                    Problem(
                        entry.source, f"creative_tabs.{i}", _unknown(tab, CREATIVE_TAB_REGISTRY, universe)
                    )
                )
    return problems


def _check_dungeon_keys(content: Content, universe: IdUniverse) -> list[Problem]:
    """A dungeon type's key is an item, and no other dungeon type has the same key: a key opens a portal to one type
    (W4d, architecture 15.5)."""
    problems: list[Problem] = []
    types_of_key: dict[str, str] = {}
    for entry in content.dungeons:
        key = entry.model.key
        if not universe.contains(ITEM_REGISTRY, key):
            problems.append(Problem(entry.source, "key", _unknown(key, ITEM_REGISTRY, universe)))
        elif key in types_of_key:
            problems.append(
                Problem(entry.source, "key", f"{key} already opens the dungeon type in {types_of_key[key]}")
            )
        types_of_key.setdefault(key, entry.source)
    return problems


def _check_recipes(content: Content, universe: IdUniverse) -> list[Problem]:
    """Every ingredient of an item's or block's recipe is an item."""
    problems: list[Problem] = []
    for entry in [*content.items, *content.blocks]:
        recipe = entry.model.recipe
        if recipe is None:
            continue
        for char, ingredient in sorted(recipe.key.items()):
            if not universe.contains(ITEM_REGISTRY, ingredient):
                problems.append(
                    Problem(entry.source, f"recipe.key.{char}", _unknown(ingredient, ITEM_REGISTRY, universe))
                )
    return problems


def _check_realms(content: Content, universe: IdUniverse) -> list[Problem]:
    """A realm names a dimension of ours that exists, one realm per dimension; the dimension's rules name the realm's
    unlock advancement in their access requirements; its dimension type links by a coordinate scale above 1 and has
    beds that set no spawn and do not explode (realm portal spec, "Data")."""
    problems: list[Problem] = []
    rules = {entry.model.dimension: entry for entry in content.dimensions}
    dimension_files = {
        f"{MOD_ID}:{file.id}": file for file in content.worldgen if file.directory == "dimension"
    }
    types = {f"{MOD_ID}:{file.id}": file for file in content.worldgen if file.directory == "dimension_type"}
    seen: dict[str, str] = {}
    for entry in content.realms:
        realm = entry.model
        if realm.dimension in seen:
            problems.append(
                Problem(
                    entry.source,
                    "dimension",
                    f"{realm.dimension} is already the realm of {seen[realm.dimension]}",
                )
            )
        seen.setdefault(realm.dimension, entry.source)
        if not universe.contains(ITEM_REGISTRY, realm.icon):
            problems.append(Problem(entry.source, "icon", _unknown(realm.icon, ITEM_REGISTRY, universe)))
        dimension = dimension_files.get(realm.dimension)
        if dimension is None:
            problems.append(
                Problem(
                    entry.source,
                    "dimension",
                    f"{realm.dimension} is not one of our dimensions (content/{WORLDGEN_DIR}/dimension/)",
                )
            )
            continue
        unlock = f"{MOD_ID}:{unlock_advancement(realm.id)}"
        rule = rules.get(realm.dimension)
        if rule is None or not any(
            isinstance(requirement, HasAdvancementDef) and requirement.advancement == unlock
            for requirement in rule.model.access or []
        ):
            problems.append(
                Problem(
                    rule.source if rule is not None else entry.source,
                    "access",
                    f"the rules of {realm.dimension} (content/dimensions/) need its unlock in their access: "
                    f"{{type: realmforge:has_advancement, advancement: {unlock}}}",
                )
            )
        dimension_type = types.get(str(dimension.data.get("type")))
        if dimension_type is None:
            problems.append(
                Problem(
                    dimension.source, "type", "a realm's dimension type is one of ours (content/worldgen/)"
                )
            )
            continue
        problems += _realm_dimension_type_problems(dimension_type.source, dimension_type.data)
    return problems


def _realm_dimension_type_problems(source: str, data: dict) -> list[Problem]:
    problems: list[Problem] = []
    scale = data.get("coordinate_scale")
    if not isinstance(scale, int | float) or isinstance(scale, bool) or scale <= 1:
        problems.append(
            Problem(
                source, "coordinate_scale", "a realm links to Overworld by a coordinate scale above 1 (1:16)"
            )
        )
    attributes = data.get("attributes")
    bed_rule = attributes.get("minecraft:gameplay/bed_rule") if isinstance(attributes, dict) else None
    if not isinstance(bed_rule, dict) or bed_rule.get("can_set_spawn") != "never" or bed_rule.get("explodes"):
        problems.append(
            Problem(
                source,
                "attributes.minecraft:gameplay/bed_rule",
                "a realm's beds set no spawn and do not explode: can_set_spawn never, explodes false",
            )
        )
    return problems


def _check_chapters(content: Content) -> list[Problem]:
    """There is a chapter, chapters are numbered 1, 2, 3 without gaps, and each one starts at the cap the one before
    it ends with, so no level band is skipped or played twice (research PR-16; architecture 10.2)."""
    problems: list[Problem] = []
    if not content.chapters:
        problems.append(
            Problem(
                "content/chapters/",
                "",
                "leveling needs a chapter: the first one's level_cap is a new player's cap",
            )
        )
    ordered = sorted(content.chapters, key=lambda entry: (entry.model.order, entry.source))
    for expected, entry in enumerate(ordered, start=1):
        if entry.model.order != expected:
            problems.append(
                Problem(
                    entry.source,
                    "order",
                    f"chapters are numbered 1, 2, 3 without gaps or repeats; expected {expected}, "
                    f"found {entry.model.order}",
                )
            )
    for before, after in pairwise(ordered):
        if after.model.level_cap != before.model.completed_level_cap:
            problems.append(
                Problem(
                    after.source,
                    "level_cap",
                    f"{after.model.level_cap} must equal the completed_level_cap of the chapter before it "
                    f"({before.model.completed_level_cap} in {before.source}), so no level band is skipped",
                )
            )
    return problems


def _check_leveling_curves(content: Content) -> list[Problem]:
    """The curves leveling reads hold no value below their minimum: a level always costs XP, and no kill takes XP
    away (schema/leveling.py). The game needs them all: Java names them through GeneratedCurves."""
    problems: list[Problem] = []
    curves = {entry.model.id: entry for entry in content.curves}
    for curve_id, minimum in LEVELING_CURVES.items():
        entry = curves.get(curve_id)
        if entry is None:
            problems.append(
                Problem(
                    f"content/curves/{curve_id}.yaml", "", "leveling needs this curve (architecture 10.2)"
                )
            )
            continue
        for n, point in enumerate(entry.model.points):
            if point.value < minimum:
                problems.append(
                    Problem(
                        entry.source,
                        f"points.{n}.value",
                        f"{point.value} is below {minimum:g}, the least this curve may hold for leveling",
                    )
                )
    return problems


def _check_kill_xp_multipliers(content: Content, universe: IdUniverse) -> list[Problem]:
    """Each entry names an entity type that exists, no two name the same one, and only slimes and magma cubes give
    by size (architecture 10.2)."""
    problems: list[Problem] = []
    seen: dict[str, str] = {}
    for entry in content.kill_xp_multipliers:
        entity = entry.model.entity
        if not universe.contains(ENTITY_TYPE_REGISTRY, entity):
            problems.append(Problem(entry.source, "entity", _unknown(entity, ENTITY_TYPE_REGISTRY, universe)))
        if entity in seen:
            problems.append(
                Problem(
                    entry.source, "entity", f"{entity} already has a kill XP multiplier in {seen[entity]}"
                )
            )
        seen.setdefault(entity, entry.source)
        if entry.model.by_size and entity not in SIZED_ENTITY_TYPES:
            problems.append(
                Problem(
                    entry.source,
                    "by_size",
                    f"only {' and '.join(SIZED_ENTITY_TYPES)} have a size; {entity} gives by multiplier alone",
                )
            )
    return problems


def _core_mod_worldgen(paths: RepoPaths) -> dict[str, set[str]]:
    """Worldgen entries the core mod ships in its own resources (realmforge:instances, ADR 0019), by directory."""
    found: dict[str, set[str]] = {}
    data_root = paths.mod_resources / "data" / MOD_ID
    for directory in WORLDGEN_REGISTRIES:
        folder = data_root / data_path(directory)
        if folder.is_dir():
            found[directory] = {file.stem for file in folder.glob("*.json")}
    return found


def _add_core_mod_worldgen(universe: IdUniverse, paths: RepoPaths) -> None:
    for directory, ids in _core_mod_worldgen(paths).items():
        for id_ in ids:
            universe.add(WORLDGEN_REGISTRIES[directory], f"{MOD_ID}:{id_}")


def _check_worldgen_clashes(content: Content, paths: RepoPaths) -> list[Problem]:
    core = _core_mod_worldgen(paths)
    return [
        Problem(
            file.source,
            "",
            f"{MOD_ID}:{file.id} already exists in the core mod's resources "
            f"(mod/src/main/resources/data/{MOD_ID}/{data_path(file.directory)}/{file.id}.json)",
        )
        for file in content.worldgen
        if file.id in core.get(file.directory, set())
    ]


def _unknown(id_: str, registry: str, universe: IdUniverse) -> str:
    message = f"unknown id {id_!r} in registry {registry}"
    close = difflib.get_close_matches(id_, universe.registries.get(registry, set()), n=3)
    if close:
        message += f" (did you mean: {', '.join(close)}?)"
    return message
