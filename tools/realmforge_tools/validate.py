"""Content validation, in order: schema (content.py), our own ids, references against the ID universe."""

from __future__ import annotations

import copy
import difflib
import math
import re
from collections.abc import Callable
from itertools import pairwise
from pathlib import PurePosixPath

from pydantic import BaseModel

from realmforge_tools.apotheosis import Snapshot, fit, load_snapshot
from realmforge_tools.content import Content, Entry, RoomEntry, SetPieceEntry, load_content
from realmforge_tools.id_universe import NEOFORGE_ATTRIBUTES, IdUniverse, load_vanilla
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
from realmforge_tools.rooms.connectors import Connector, connectors, could_attach, doorway_gap
from realmforge_tools.rooms.containers import KINDS, candidates
from realmforge_tools.rooms.generator import GENERATED_DIR, theme_blocks, unknown_blocks
from realmforge_tools.rooms.openings import digest as opening_digest
from realmforge_tools.rooms.openings import problems as opening_problems
from realmforge_tools.rooms.set_pieces import piece_problems
from realmforge_tools.rooms.structure import BlockState, StructureError, count_spawners
from realmforge_tools.rooms.walk import walk_problems
from realmforge_tools.schema import CONTENT_TYPES, DATAPACK_TYPES, WORLDGEN_DIR
from realmforge_tools.schema.apotheosis import (
    CURIOS_LOOT_CATEGORIES,
    LOOT_CATEGORIES,
    RARITIES,
    ApotheosisEntryDef,
)
from realmforge_tools.schema.block import ITEMLESS_KINDS, REALM_PORTAL_KINDS, SINGLE_BLOCK_KINDS
from realmforge_tools.schema.chapter import advancement_id as chapter_advancement
from realmforge_tools.schema.character import STARTING_BREAD, CharacterModifierDef
from realmforge_tools.schema.dimension_rules import WORLDGEN_STRUCTURE_REGISTRY
from realmforge_tools.schema.dungeon import (
    CORRIDOR_ROLES,
    DEAD_END_ROLES,
    PATH_ROLES,
    ROOM_ROLES,
    WALL_HEIGHT,
    WALL_WIDTH,
    DungeonTypeDef,
    room_role,
)
from realmforge_tools.schema.effect import PeriodicDef, StacksDef, effect_id
from realmforge_tools.schema.item import (
    FIXED_ITEM_TAGS,
    GEAR_FORBIDDEN_ATTRIBUTES,
    USE_TEXT_GROUP,
    ItemDef,
    entity_loot_table,
)
from realmforge_tools.schema.kill_xp_multiplier import SIZED_ENTITY_TYPES
from realmforge_tools.schema.leveling import LEVELING_CURVES
from realmforge_tools.schema.mob import BOSS, TRASH_LOOT, mob_description_id, mob_id
from realmforge_tools.schema.quest import (
    CUSTOM_STATS,
    AdvancementRewardDef,
    AdvancementTaskDef,
    DimensionTaskDef,
    DungeonTaskDef,
    ItemRewardDef,
    ItemTaskDef,
    KillTaskDef,
    QuestDef,
    RewardDef,
    SpellTaskDef,
    StatTaskDef,
    TaskDef,
    chapter_key,
    code_string,
    object_id,
    quest_dependencies,
    quest_object_keys,
)
from realmforge_tools.schema.realm import FRAME_HEIGHT, PORTAL_PARTICLES, unlock_advancement
from realmforge_tools.schema.recipe import is_tag
from realmforge_tools.schema.requirement import ChapterCompletedDef, HasAdvancementDef
from realmforge_tools.schema.room_spec import FULL, LANTERN, SLAB, STAIRS, THEME_ROLES, WALL_TORCH
from realmforge_tools.schema.sound import sound_id
from realmforge_tools.schema.spell import (
    ALLY,
    APPLY_EFFECT,
    AREA,
    CLEANSE,
    CURRENT_TARGET,
    DAMAGE,
    DAMAGE_OVER_TIME,
    DASH,
    DIRECTION,
    ENEMY,
    FARTHEST_PLAYER,
    GROUND_POINT,
    HEAL,
    KNOCKBACK,
    MELEE,
    PERIODIC_PLACEHOLDERS,
    PROJECTILE,
    RANDOM_PLAYER,
    REMOVE_EFFECT,
    SCALING_OF_KIND,
    SELF,
    TELEPORT,
    SpellDef,
)
from realmforge_tools.schema.spell_school import SpellSchoolDef
from realmforge_tools.schema.stat import StatDef
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
PARTICLE_TYPE_REGISTRY = "minecraft:particle_type"
MOB_EFFECT_REGISTRY = "minecraft:mob_effect"
SOUND_EVENT_REGISTRY = "minecraft:sound_event"
SPELL_REGISTRY = f"{MOD_ID}:spell"
CUSTOM_STAT_REGISTRY = "minecraft:custom_stat"
LOOT_TABLE_REGISTRY = "minecraft:loot_table"
RECIPE_REGISTRY = "minecraft:recipe"
BIOME_REGISTRY = "minecraft:worldgen/biome"
# Vanilla Overworld's height (net.minecraft.world.level.dimension.DimensionType, minecraft:overworld): its lowest y and
# the number of blocks above it, for Overworld's arrival heights.
OVERWORLD_MIN_Y = -64
OVERWORLD_HEIGHT = 384

# Vanilla's dimensions (the level stems of its world presets). The registry report behind the vanilla ID universe
# lists no minecraft:dimension registry, as a world's dimensions come from its preset, so they are named here.
VANILLA_DIMENSIONS = ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")

# YAML content type -> the registry its ids live in.
REGISTRY_OF_TYPE: dict[str, str] = {
    "items": ITEM_REGISTRY,
    "blocks": BLOCK_REGISTRY,
    "stats": ATTRIBUTE_REGISTRY,
    "effects": MOB_EFFECT_REGISTRY,
    "mobs": ENTITY_TYPE_REGISTRY,
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
    "effect": "content/effects/",
    "spell_school": "content/spell_schools/",
    "death.attack": "content/spell_schools/",
    "dungeon_type": "content/dungeons/",
    "subtitles": "content/sounds/",
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
    problems = (
        validate_content(content, universe)
        + _check_worldgen_clashes(content, paths)
        + _check_apotheosis(content, universe, paths)
    )
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
    """The vanilla universe plus NeoForge's attributes and every id our content defines."""
    universe = copy.deepcopy(vanilla)
    for dimension in VANILLA_DIMENSIONS:
        universe.add(DIMENSION_REGISTRY, dimension)
    for attribute in NEOFORGE_ATTRIBUTES:
        universe.add(ATTRIBUTE_REGISTRY, attribute)
    for type_dir, registry in REGISTRY_OF_TYPE.items():
        for entry in content.entries(type_dir):
            universe.add(registry, f"{MOD_ID}:{entry.model.id}")
    # A block registers its block item under the same id (generate/blocks.py), unless its kind has none.
    for entry in content.blocks:
        if entry.model.kind not in ITEMLESS_KINDS:
            universe.add(ITEM_REGISTRY, f"{MOD_ID}:{entry.model.id}")
    for entry in content.spell_schools:
        universe.add(DAMAGE_TYPE_REGISTRY, entry.model.damage_type)
    # The item tags generate/items.py writes with fixed members (melee weapons, sword-like weapons).
    universe.tags.setdefault(ITEM_REGISTRY, set()).update(FIXED_ITEM_TAGS)
    for entry in content.sounds:
        for name in entry.model.events:
            universe.add(SOUND_EVENT_REGISTRY, sound_id(entry.model, name))
    for entry in content.chapters:
        universe.add(ADVANCEMENT_REGISTRY, f"{MOD_ID}:{chapter_advancement(entry.model.id)}")
    for stat in CUSTOM_STATS:
        universe.add(CUSTOM_STAT_REGISTRY, f"{MOD_ID}:{stat}")
    for entry in content.realms:
        if not entry.model.is_overworld:
            universe.add(ADVANCEMENT_REGISTRY, f"{MOD_ID}:{unlock_advancement(entry.model.id)}")
    for file in content.worldgen:
        universe.add(WORLDGEN_REGISTRIES[file.directory], f"{MOD_ID}:{file.id}")
    for room in content.rooms:
        universe.add(STRUCTURE_REGISTRY, room_structure_id(room.id))
        # A room pool is a template pool too, for the jigsaws of world structures (M2, ADR 0044), so a jigsaw may
        # name one.
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
    problems += _check_effects(content, universe)
    problems += _check_spells(content, universe)
    problems += _check_mobs(content, universe)
    problems += _check_bosses(content, universe)
    problems += _check_sounds(content, universe)
    problems += _check_characters(content, universe)
    problems += _check_worldgen_references(content, universe)
    problems += _check_dimension_rules(content, universe)
    problems += _check_blocks(content, universe)
    problems += _check_realms(content, universe)
    problems += _check_text_groups(content)
    problems += _check_room_themes(content, universe)
    problems += _check_room_specs(content)
    problems += _check_rooms(content, universe)
    problems += _check_set_pieces(content, universe)
    problems += _check_dungeons(content, universe)
    problems += _check_dungeon_keys(content, universe)
    problems += _check_recipes(content, universe)
    problems += _check_gear(content, universe)
    problems += _check_item_drops(content, universe)
    problems += _check_chapters(content)
    problems += _check_quests(content, universe)
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
        for index, block in enumerate(room.model.explosion_breaks):
            if not universe.contains(BLOCK_REGISTRY, block):
                problems.append(
                    Problem(room.source, f"explosion_breaks[{index}]", f"unknown block {block!r}")
                )
        problems += _check_room_entry(room)
        problems += _check_room_containers(room)
        problems += _check_room_openings(room)
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


def _check_room_containers(room: RoomEntry) -> list[Problem]:
    """A sidecar's chance table asks for no more chests or barrels than the room holds (RLM-365): such a count is a
    slip, which the dungeon would quietly cap. The dungeon type's tables are capped per room."""
    tables = room.model.containers
    if tables is None:
        return []
    found = room.derived("container_candidates", candidates)
    problems = []
    for kind in KINDS:
        table = getattr(tables, f"{kind}s")
        held = sum(candidate.kind == kind for candidate in found)
        if table is not None and max(table) > held:
            problems.append(
                Problem(
                    room.source,
                    f"containers.{kind}s",
                    f"the table asks for up to {max(table)} {kind}s, but the room holds {held}",
                )
            )
    return problems


def _check_room_openings(room: RoomEntry) -> list[Problem]:
    """Each block a sidecar's opening clears is a plain block inside the room (RLM-412, ``rooms/openings.py``)."""
    openings = room.model.openings
    if not openings:
        return []
    found = room.derived(f"openings_{opening_digest(openings)}", lambda s: opening_problems(s, openings))
    return [Problem(room.source, where, message) for where, message in found]


def _check_set_pieces(content: Content, universe: IdUniverse) -> list[Problem]:
    """A set piece's template is a valid template (``rooms/check.py``) and the piece its fit rule describes
    (``rooms/set_pieces.py``), and its fit rule names blocks the template holds and the universe knows."""
    kind = f"check-{universe_digest(universe)}"
    problems: list[Problem] = []
    for piece in content.set_pieces:
        model = piece.model
        stem = PurePosixPath(piece.source).stem
        if model.id != stem:
            problems.append(Problem(piece.source, "id", f"id {model.id!r} must equal the file name {stem!r}"))
        found = piece.derived(kind, lambda structure: check_structure(structure, universe))
        found += piece.derived(
            f"set_piece_{model.fit().digest}", lambda structure, m=model: piece_problems(m.fit(), structure)
        )
        problems += [Problem(piece.template, location, message) for location, message in found]
        problems += _set_piece_rule_problems(piece, universe)
    return problems


def _set_piece_rule_problems(piece: SetPieceEntry, universe: IdUniverse) -> list[Problem]:
    model, problems = piece.model, []
    states: dict[str, set[BlockState]] = {}
    for block in piece.structure.blocks:
        states.setdefault(block.state.name, set()).add(block.state)
    for index, wall in enumerate(model.walls):
        if ":" in wall and not universe.contains(BLOCK_REGISTRY, wall):
            problems.append(Problem(piece.source, f"walls[{index}]", f"unknown block {wall!r}"))
    variants = model.variants
    for block in (*variants.chance, *variants.swaps):
        group = "chance" if block in variants.chance else "swaps"
        if not universe.contains(BLOCK_REGISTRY, block):
            problems.append(Problem(piece.source, f"variants.{group}.{block}", f"unknown block {block!r}"))
        elif block not in states:
            problems.append(
                Problem(piece.source, f"variants.{group}.{block}", "the template holds no such block")
            )
    for block, swap in variants.swaps.items():
        where = f"variants.swaps.{block}"
        try:
            target = BlockState.parse(swap.to)
        except StructureError as e:
            problems.append(Problem(piece.source, f"{where}.to", str(e)))
            continue
        if not universe.contains(BLOCK_REGISTRY, target.name):
            problems.append(Problem(piece.source, f"{where}.to", f"unknown block {target.name!r}"))
        for name in swap.keep:
            if any(state.get(name) is None for state in states.get(block, ())):
                problems.append(
                    Problem(
                        piece.source, f"{where}.keep", f"{block} in the template has no property {name!r}"
                    )
                )
            if target.get(name) is not None:
                problems.append(
                    Problem(
                        piece.source,
                        f"{where}.keep",
                        f"{swap.to} sets {name} itself; keep it or set it, not both",
                    )
                )
    return problems


def _room_location(room: RoomEntry, location: str) -> str:
    """Where a problem in a room is: a generated room's problems are reported on its spec, so they name the seed."""
    if room.id.startswith(f"{GENERATED_DIR}/"):
        seed = room.id.rsplit("_", 1)[1]
        return f"seed {seed}: {location}" if location else f"seed {seed}"
    return location


# Told with every refused full block: a vanilla full block is only missing from the list.
_NOT_FULL = "a full solid block (a full block missing from SOLID_BLOCKS in rooms/check.py belongs there)"


def _full_block(content: Content) -> Callable[[str], bool]:
    """Whether a block is a full solid block: ``is_solid`` by its name, or one of our own simple content blocks, such
    as Undervault's stones (RLM-473). The structure checks keep ``is_solid`` alone."""
    ours = {f"{MOD_ID}:{entry.model.id}" for entry in content.blocks if entry.model.kind == "simple"}
    return lambda name: name in ours or is_solid(name)


def _check_room_themes(content: Content, universe: IdUniverse) -> list[Problem]:
    """Every block of a theme exists and is what its role needs: a full block, stairs, a slab, a wall torch, a
    lantern (``schema/room_spec.py``)."""
    needs = {
        FULL: (_full_block(content), _NOT_FULL),
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


def _check_room_entry(room: RoomEntry) -> list[Problem]:
    """A room's entry side holds exactly one of its connectors, and an entrance has none: players arrive in it
    (RLM-362)."""
    side = room.model.entry
    if side is None:
        return []
    location = _room_location(room, "entry")
    if room_role(room.model.tags) == "entrance":
        return [Problem(room.source, location, "an entrance is where players arrive; it has no entry side")]
    found = [c for c in room.derived("connectors", connectors) if c.front == side]
    if len(found) != 1:
        return [
            Problem(
                room.source,
                location,
                f"the entry side {side} holds {len(found)} connectors; it names the side of exactly one",
            )
        ]
    return []


def _check_dungeons(content: Content, universe: IdUniverse) -> list[Problem]:
    """A room in the pool of a dungeon type has exactly one role, and the pool holds what the type's layout needs: an
    entrance, rooms to pass through, the room that ends the main path, a dead end for the branches and for the
    connectors nothing else takes, rooms that take a branch when it must have one, and only rooms whose every
    connector meets some connector of the pool (architecture 15.3). With a ``pacing`` the rooms to pass through and
    the junctions that take branches are corridors, the pool has a fight, and a type without walls a corridor's dead
    end (RLM-364). A type's wall is a block, and closes every
    doorway of its pool. A room in such a pool also holds no fluid at its edge, and an entrance marker only if it is an
    entrance (``_check_pooled_room``). Whether a layout fits a plot is the planner's to find; JUnit fuzzes it over the
    generated types."""
    problems: list[Problem] = []
    full = _full_block(content)
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
        paced = dungeon.layout.pacing is not None
        passing = CORRIDOR_ROLES if paced else PATH_ROLES
        needs = (
            (
                "pool",
                "entrance (role entrance, a connector)",
                lambda room, role, n: role == "entrance" and n >= 1,
            ),
            (
                "pool",
                f"room to pass through (role {', '.join(passing)}; two connectors)",
                lambda room, role, n, passing=passing: role in passing and n >= 2,
            ),
            (
                "layout.ending",
                f"room to end the main path (role {ending}, a connector)",
                lambda room, role, n, ending=ending: role == ending and n >= 1,
            ),
            (
                "pool",
                f"dead end for the branches and open connectors (role {', '.join(DEAD_END_ROLES)}, one connector)",
                lambda room, role, n: role in DEAD_END_ROLES and n == 1,
            ),
        )
        if paced:
            needs += (
                (
                    "layout.pacing",
                    "fight (role combat with a mob spawner, two connectors)",
                    lambda room, role, n: (
                        role == "combat" and n >= 2 and room.derived("spawners", count_spawners) > 0
                    ),
                ),
            )
        if paced and dungeon.layout.walls is None:
            needs += (
                (
                    "layout.pacing",
                    f"corridor's dead end to close open connectors without walls (role {', '.join(CORRIDOR_ROLES)}, "
                    "one connector)",
                    lambda room, role, n: role in CORRIDOR_ROLES and n == 1,
                ),
            )
        for location, what, fits in needs:
            if not any(fits(room, role, len(found)) for room, role, found in shapes):
                problems.append(Problem(entry.source, location, f"pool {dungeon.pool} has no {what}"))
        if dungeon.layout.branches[0] > 0 and not any(
            role in passing and len(found) >= 3 for _, role, found in shapes
        ):
            problems.append(
                Problem(
                    entry.source,
                    "layout.branches",
                    f"every layout has a branch, but pool {dungeon.pool} has no room to pass through with a third "
                    f"connector to take it{' (a corridor, as the pacing hangs branches off them)' if paced else ''}",
                )
            )
        problems += _check_walls(entry, shapes, universe, full)
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


def _check_walls(
    entry: Entry[DungeonTypeDef],
    shapes: list[tuple[RoomEntry, str | None, list[Connector]]],
    universe: IdUniverse,
    full: Callable[[str], bool],
) -> list[Problem]:
    """A dungeon type's wall is a full solid block, and closes every doorway of its pool: around the middle three by
    three blocks of each side connector's face, the room is closed (RLM-364). A pane, a fence or air would leave
    every closed doorway open to walk or see through (RLM-419)."""
    walls = entry.model.layout.walls
    if walls is None:
        return []
    problems = []
    if not universe.contains(BLOCK_REGISTRY, walls.block):
        problems.append(Problem(entry.source, "layout.walls.block", f"unknown block {walls.block!r}"))
    elif not full(walls.block):
        problems.append(Problem(entry.source, "layout.walls.block", f"{walls.block} is not {_NOT_FULL}"))
    kind = f"doorway_gaps_{WALL_WIDTH}x{WALL_HEIGHT}"
    for room, _, found in shapes:
        gaps = room.derived(
            kind,
            lambda structure: [
                doorway_gap(structure, c, WALL_WIDTH, WALL_HEIGHT) for c in connectors(structure)
            ],
        )
        for connector, gap in zip(found, gaps, strict=True):
            if gap is not None:
                problems.append(
                    Problem(
                        room.source,
                        _room_location(room, "pools"),
                        f"the doorway of its connector at {' '.join(map(str, connector.pos))} is open at "
                        f"{' '.join(map(str, gap))}, wider than the {WALL_WIDTH} x {WALL_HEIGHT} wall of "
                        f"{entry.source}",
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
        kind = type_dir.removesuffix("es") if type_dir.endswith("sses") else type_dir.removesuffix("s")
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
    attributes (architecture 8.3), adding its percents to the base (architecture 8.2). An add_value amount on one of
    our stats takes its unit, percent for a share; a vanilla attribute takes either, as its unit is not catalogued
    (knockback resistance is a fraction)."""
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
            problems += _check_adds_percents(entry.source, path, modifier.operation)
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


# Which targeting types each effect may use: damage, damage over time and knockback hit enemies, heals and cleanses help
# the caster or its allies, status effects go on anyone; a melee shape lies in front of the caster itself; a projectile flies at an enemy, a ground point or along a direction, an area lies at the
# caster's feet, an enemy's or a ground point, a teleport needs a point to go to, and a dash rushes at an enemy, a
# point or along a direction. The NPC targeting types choose an enemy, so they go wherever enemy does.
_NPC = (CURRENT_TARGET, RANDOM_PLAYER, FARTHEST_PLAYER)
_EFFECT_TARGETING: dict[str, tuple[str, ...]] = {
    DAMAGE: (ENEMY, *_NPC),
    DAMAGE_OVER_TIME: (ENEMY, *_NPC),
    HEAL: (SELF, ALLY),
    APPLY_EFFECT: (SELF, ENEMY, ALLY, *_NPC),
    REMOVE_EFFECT: (SELF, ENEMY, ALLY, *_NPC),
    CLEANSE: (SELF, ALLY),
    KNOCKBACK: (ENEMY, *_NPC),
    PROJECTILE: (ENEMY, GROUND_POINT, DIRECTION, *_NPC),
    AREA: (SELF, ENEMY, GROUND_POINT, *_NPC),
    MELEE: (SELF,),
    TELEPORT: (GROUND_POINT, DIRECTION),
    DASH: (ENEMY, GROUND_POINT, DIRECTION, *_NPC),
}
# Vanilla's instant status effects (InstantenousMobEffect) apply on every tick of their duration, so
# realmforge:apply_effect with one would heal, hurt or feed every tick (RLM-260): what a spell uses instead.
INSTANT_MOB_EFFECTS: dict[str, str] = {
    "minecraft:instant_health": f"use {HEAL} instead",
    "minecraft:instant_damage": f"use {DAMAGE} instead",
    "minecraft:saturation": "a spell does not feed",
}


def _check_effects(content: Content, universe: IdUniverse) -> list[Problem]:
    """A status effect's periodic school exists, and its modifiers name attributes that exist, in their unit."""
    problems: list[Problem] = []
    stats = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.stats}
    schools = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.spell_schools}
    for entry in content.effects:
        effect = entry.model
        if effect.stacks is not None and not universe.contains(SPELL_SCHOOL_REGISTRY, effect.stacks.school):
            problems.append(
                Problem(
                    entry.source,
                    "stacks.school",
                    _unknown(effect.stacks.school, SPELL_SCHOOL_REGISTRY, universe),
                )
            )
        if effect.periodic is not None and not universe.contains(
            SPELL_SCHOOL_REGISTRY, effect.periodic.school
        ):
            problems.append(
                Problem(
                    entry.source,
                    "periodic.school",
                    _unknown(effect.periodic.school, SPELL_SCHOOL_REGISTRY, universe),
                )
            )
        school = schools.get(effect.periodic.school) if effect.periodic is not None else None
        if school is not None and school.kind != "magic" and effect.periodic.spell_power_percent is not None:
            problems.append(
                Problem(
                    entry.source,
                    "periodic.spell_power_percent",
                    f"{effect.periodic.school} is a {school.kind} school, and only magic scales with spell power",
                )
            )
        for n, modifier in enumerate(effect.modifiers):
            problems += _check_modifier(entry.source, f"modifiers.{n}", modifier, stats, universe)
    return problems


def _check_spells(content: Content, universe: IdUniverse) -> list[Problem]:
    """A spell's school exists, and the stat it scales with fits the school's kind: off-class stats do nothing for a
    class's abilities (Matěj, 2026-10-15), so magic scales with spell power and physical abilities with attack damage.
    An attack damage ability names its share of attack damage on every damage effect, and a spell power one names
    none. Every effect fits the spell's targeting (damage hits enemies, heals mend the caster or its allies), and the
    effects a projectile or an area runs fit whom it reaches. Status effects exist."""
    problems: list[Problem] = []
    schools = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.spell_schools}
    effects = {
        effect_id(entry.model.id): entry.model.periodic
        for entry in content.effects
        if entry.model.periodic is not None
    }
    mob_tags = {tag for entry in content.mobs for tag in entry.model.tags}
    for entry in content.spells:
        spell = entry.model
        school = schools.get(spell.school) if spell.school is not None else None
        periodic_names = sorted(PERIODIC_PLACEHOLDERS & set(spell.placeholders()))
        applied = spell.first_effect(APPLY_EFFECT)
        if periodic_names and applied is not None and applied.effect not in effects:
            problems.append(
                Problem(
                    entry.source,
                    "description",
                    f"{{{periodic_names[0]}}} reads the first apply_effect's damage or healing over time, and "
                    f"{applied.effect} has none (only our effects with periodic do)",
                )
            )
        if spell.school is not None and not universe.contains(SPELL_SCHOOL_REGISTRY, spell.school):
            problems.append(
                Problem(entry.source, "school", _unknown(spell.school, SPELL_SCHOOL_REGISTRY, universe))
            )
        elif (
            school is not None and not spell.is_passive and SCALING_OF_KIND[school.kind] != spell.scales_with
        ):
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
            problems += _check_effect(entry.source, path, effect, spell, universe, effects)
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
            problems += _check_inner_effects(entry.source, path, effect, spell, universe, effects)
            if effect.type == PROJECTILE:
                problems += _check_projectile(entry.source, path, effect, mob_tags, universe)
            if spell.cast.type == "channel":
                problems += _check_channel_reapply(
                    entry.source, path, effect, spell.cast.interval_seconds, effects
                )
        cast_sound = getattr(spell.cast, "sound", None)
        if cast_sound is not None and not universe.contains(SOUND_EVENT_REGISTRY, cast_sound):
            problems.append(
                Problem(entry.source, "cast.sound", _unknown(cast_sound, SOUND_EVENT_REGISTRY, universe))
            )
        if school is not None:
            problems += _check_hit_bonuses(entry.source, spell, school)
            problems += _check_melee_requirement(entry.source, spell, school)
        problems += _check_bonus_placeholder(entry.source, spell, content, universe)
        problems += _check_damage_over_time(entry.source, spell, content, universe)
        problems += _check_conditions(entry.source, spell, content, universe)
        if spell.passive is not None:
            problems += _check_passive(entry.source, spell, content, universe)
    return problems


def _check_damage_over_time(
    source: str, spell: SpellDef, content: Content, universe: IdUniverse
) -> list[Problem]:
    """A damage over time (RLM-539) names one of our effects with stacks, lasts a whole number of its intervals at
    every rank, and grows with the power of its ticks' school; a damage effect's bonus against a status effect names
    one that exists (RLM-540)."""
    problems: list[Problem] = []
    stacked: dict[str, StacksDef] = {
        effect_id(entry.model.id): entry.model.stacks
        for entry in content.effects
        if entry.model.stacks is not None
    }
    schools = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.spell_schools}
    for path, effect in spell.all_effects():
        if effect.type == DAMAGE and effect.against_effect is not None:
            against = effect.against_effect.effect
            if not universe.contains(MOB_EFFECT_REGISTRY, against):
                problems.append(
                    Problem(
                        source,
                        f"{path}.against_effect.effect",
                        _unknown(against, MOB_EFFECT_REGISTRY, universe),
                    )
                )
        if effect.type != DAMAGE_OVER_TIME:
            continue
        stacks = stacked.get(effect.effect)
        if stacks is None:
            problems.append(
                Problem(
                    source,
                    f"{path}.effect",
                    f"{effect.effect} is no effect of content/effects/ with stacks: a damage over time keeps its "
                    "damage in one",
                )
            )
            continue
        for field in ("duration_seconds", "duration_per_level_seconds"):
            seconds = getattr(effect, field)
            if seconds is None:
                continue
            ticks = seconds / stacks.interval_seconds
            if abs(ticks - round(ticks)) > 1e-9:
                problems.append(
                    Problem(
                        source,
                        f"{path}.{field}",
                        f"{effect.effect} ticks every {stacks.interval_seconds:g} s, so it lasts a whole number of "
                        f"its intervals at every level, and {field} {seconds:g} s is not one",
                    )
                )
        school = schools.get(stacks.school)
        if school is not None and SCALING_OF_KIND[school.kind] != spell.scales_with:
            problems.append(
                Problem(
                    source,
                    f"{path}.effect",
                    f"{effect.effect} deals {stacks.school} damage, a {school.kind} school, so the spell scales with "
                    f"{SCALING_OF_KIND[school.kind]}, not {spell.scales_with}",
                )
            )
    return problems


def _check_conditions(source: str, spell: SpellDef, content: Content, universe: IdUniverse) -> list[Problem]:
    """A spell's conditions (RLM-540): a target health condition needs an enemy target; extra resource spent raises
    damage, so the spell deals some; a hit moment belongs to a player's swing."""
    problems: list[Problem] = []
    if spell.target_health_below_percent is not None and spell.targeting.type not in (ENEMY, *_NPC):
        problems.append(
            Problem(
                source,
                "target_health_below_percent",
                f"a target's health is read from an enemy target, and the spell targets {spell.targeting.type}",
            )
        )
    if spell.cost is not None and spell.cost.extra is not None and spell.first_effect(DAMAGE) is None:
        problems.append(
            Problem(source, "cost.extra", "extra resource spent raises the spell's damage, and it deals none")
        )
    if getattr(spell.cast, "animation_seconds", None) is not None and spell.npc_only:
        problems.append(
            Problem(
                source,
                "cast.animation_seconds",
                "a swing's animation is a player's; mobs land their spells at once",
            )
        )
    return problems


def _check_passive(source: str, spell: SpellDef, content: Content, universe: IdUniverse) -> list[Problem]:
    """A passive spell (RLM-541): its weapon tag exists; its modifiers name attributes that exist, in their unit, and add
    their percents (architecture 8.2); the spells it raises exist, are active and deal damage; its crit chance names a
    status effect that exists."""
    passive = spell.passive
    assert passive is not None
    problems: list[Problem] = []
    if passive.while_wielding is not None and not universe.contains_tag(
        ITEM_REGISTRY, passive.while_wielding
    ):
        problems.append(
            Problem(source, "passive.while_wielding", f"unknown item tag {passive.while_wielding!r}")
        )
    stats = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.stats}
    for n, modifier in enumerate(passive.modifiers):
        path = f"passive.modifiers.{n}"
        problems += _check_modifier(source, path, modifier, stats, universe)
        problems += _check_adds_percents(source, path, modifier.operation)
    spells = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.spells}
    for n, bonus in enumerate(passive.spell_damage):
        for m, named in enumerate(bonus.spells):
            path = f"passive.spell_damage.{n}.spells.{m}"
            target = spells.get(named)
            if target is None:
                problems.append(Problem(source, path, _unknown(named, SPELL_REGISTRY, universe)))
            elif target.is_passive or target.first_effect(DAMAGE) is None:
                problems.append(
                    Problem(source, path, f"{named} deals no damage, so more damage does nothing")
                )
    critical = passive.crit_chance
    if critical is not None and not universe.contains(MOB_EFFECT_REGISTRY, critical.against):
        problems.append(
            Problem(
                source,
                "passive.crit_chance.against",
                _unknown(critical.against, MOB_EFFECT_REGISTRY, universe),
            )
        )
    for n, proc in enumerate(passive.procs):
        problems += _check_proc(source, f"passive.procs.{n}", proc, spell, spells, universe)
    first = passive.modifiers[0] if passive.modifiers else None
    stat = stats.get(first.attribute) if first is not None else None
    if (
        "{bonus}" in spell.description.en_us
        and first is not None
        and first.operation == "add_value"
        and (stat is not None and stat.percent)
    ):
        problems.append(
            Problem(
                source,
                "description",
                f"{{bonus}} reads the first modifier, add_value on the share {first.attribute}: give it a multiplied "
                "operation or describe the number another way",
            )
        )
    return problems


def _check_proc(
    source: str,
    path: str,
    proc: BaseModel,
    spell: SpellDef,
    spells: dict[str, SpellDef],
    universe: IdUniverse,
) -> list[Problem]:
    """A passive's proc (RLM-540): the effects it watches or gives exist; the spell whose ticks it watches deals damage
    over time; the spell it casts is active and a player's, at the passive's ranks; the spell whose window it opens has
    one."""
    problems: list[Problem] = []
    for field, effect in (
        ("effect", proc.effect),
        ("apply_effect.effect", getattr(proc.apply_effect, "effect", None)),
    ):
        if effect is not None and not universe.contains(MOB_EFFECT_REGISTRY, effect):
            problems.append(
                Problem(source, f"{path}.{field}", _unknown(effect, MOB_EFFECT_REGISTRY, universe))
            )
    if proc.spell is not None:
        watched = spells.get(proc.spell)
        if watched is None:
            problems.append(Problem(source, f"{path}.spell", _unknown(proc.spell, SPELL_REGISTRY, universe)))
        elif watched.first_effect(DAMAGE_OVER_TIME) is None and watched.first_effect(APPLY_EFFECT) is None:
            problems.append(
                Problem(source, f"{path}.spell", f"{proc.spell} applies no damage over time to tick")
            )
    if proc.cast is not None:
        cast = spells.get(proc.cast)
        if cast is None:
            problems.append(Problem(source, f"{path}.cast", _unknown(proc.cast, SPELL_REGISTRY, universe)))
        elif cast.is_passive or cast.npc_only:
            problems.append(Problem(source, f"{path}.cast", f"{proc.cast} is no spell a player casts"))
        elif cast.max_level < spell.max_level:
            problems.append(
                Problem(
                    source,
                    f"{path}.cast",
                    f"{proc.cast} has {cast.max_level} ranks, fewer than the passive's {spell.max_level}: it is cast at "
                    "the passive's rank",
                )
            )
    if proc.open_window is not None:
        opened = spells.get(proc.open_window)
        if opened is None:
            problems.append(
                Problem(source, f"{path}.open_window", _unknown(proc.open_window, SPELL_REGISTRY, universe))
            )
        elif opened.window is None:
            problems.append(
                Problem(source, f"{path}.open_window", f"{proc.open_window} has no window to open")
            )
    return problems


def _check_hit_bonuses(source: str, spell: SpellDef, school: SpellSchoolDef) -> list[Problem]:
    """A hit's extra armor shred needs a school that armor stops (physical), and its spell penetration a school with
    a resistance to ignore (a magic one); either would do nothing otherwise."""
    problems: list[Problem] = []
    for path, effect in spell.all_effects():
        if effect.type != DAMAGE:
            continue
        if effect.armor_shred_percent is not None and school.bypasses_armor:
            problems.append(
                Problem(
                    source,
                    f"{path}.armor_shred_percent",
                    f"{spell.school} hits skip armor, so armor shred does nothing for them",
                )
            )
        if effect.penetration_percent is not None and school.resistance is None:
            problems.append(
                Problem(
                    source,
                    f"{path}.penetration_percent",
                    f"{spell.school} has no resistance for spell penetration to ignore",
                )
            )
    return problems


def _check_melee_requirement(source: str, spell: SpellDef, school: SpellSchoolDef) -> list[Problem]:
    """A player's physical spell that deals damage in melee (a damage effect of its own or of an area, not a
    projectile's) names what it strikes with: a melee weapon in the main hand, or a shield (RLM-499). Mobs and bosses
    cast without it, so their spells need none."""
    if spell.npc_only or school.kind != "physical" or spell.requires:
        return []
    melee = [
        path
        for path, effect in spell.all_effects()
        if effect.type == DAMAGE and not _inside_projectile(spell, path)
    ]
    if not melee:
        return []
    return [
        Problem(
            source,
            "requires",
            f"a player's physical spell that deals damage in melee ({melee[0]}) needs a melee weapon in the main "
            "hand: requires: [melee_weapon] (or [shield] for a shield strike), RLM-499",
        )
    ]


def _inside_projectile(spell: SpellDef, path: str) -> bool:
    """Whether the effect at ``path`` is one a projectile runs on the enemy it hits."""
    parts = path.split(".")
    return len(parts) == 4 and spell.effects[int(parts[1])].type == PROJECTILE


def _check_bonus_placeholder(
    source: str, spell: SpellDef, content: Content, universe: IdUniverse
) -> list[Problem]:
    """{bonus} reads the attribute modifier of the first applied status effect: one of ours must have exactly one (the
    game keeps an effect's modifiers unordered), and it must read as a number, in percent for a multiplied operation or
    in the attribute's points (not add_value on one of our shares, which the description could not tell from
    points)."""
    if spell.is_passive or "bonus" not in spell.placeholders():
        return []
    applied = spell.first_effect(APPLY_EFFECT)
    ours = {effect_id(entry.model.id): entry.model for entry in content.effects}
    effect = ours.get(applied.effect)
    if effect is None:
        return []
    if len(effect.modifiers) != 1:
        return [
            Problem(
                source,
                "description",
                f"{{bonus}} reads {applied.effect}'s one modifier, and it has {len(effect.modifiers)}",
            )
        ]
    first = effect.modifiers[0]
    stats = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.stats}
    stat = stats.get(first.attribute)
    if first.operation == "add_value" and stat is not None and stat.percent:
        return [
            Problem(
                source,
                "description",
                f"{{bonus}} reads {applied.effect}'s first modifier, add_value on the share {first.attribute}: "
                "give it a multiplied operation or describe the number another way",
            )
        ]
    return []


def _check_projectile(
    source: str, path: str, projectile: BaseModel, mob_tags: set[str], universe: IdUniverse
) -> list[Problem]:
    """A projectile spares a tag that our mobs fill, and its impact's sound and particle block exist."""
    problems: list[Problem] = []
    if projectile.spares is not None and projectile.spares not in mob_tags:
        known = ", ".join(sorted(mob_tags)) or "none"
        problems.append(
            Problem(
                source,
                f"{path}.spares",
                f"no mob in content/mobs/ is tagged {projectile.spares}; tags: {known}",
            )
        )
    impact = projectile.impact
    if (
        impact is not None
        and impact.sound is not None
        and not universe.contains(SOUND_EVENT_REGISTRY, impact.sound)
    ):
        problems.append(
            Problem(source, f"{path}.impact.sound", _unknown(impact.sound, SOUND_EVENT_REGISTRY, universe))
        )
    if (
        impact is not None
        and impact.particles is not None
        and not universe.contains(BLOCK_REGISTRY, impact.particles)
    ):
        problems.append(
            Problem(source, f"{path}.impact.particles", _unknown(impact.particles, BLOCK_REGISTRY, universe))
        )
    return problems


# The text group of entity type names, entity.realmforge.<name>, which content/mobs/ shares.
ENTITY_TEXT_GROUP = "entity"

# The targeting types a mob's AI casts with: at its target, or at a player it picks itself (architecture 11.2).
MOB_SPELL_TARGETING = (ENEMY, CURRENT_TARGET, RANDOM_PLAYER, FARTHEST_PLAYER)

# A melee spell's reach (``melee: true``, RLM-399) when no caster is at hand: vanilla's default melee reach, a mob's.
DEFAULT_MELEE_REACH = 3.0


def _reach(targeting: object) -> float:
    """How far a mob's spell of ``targeting`` reaches: its range, or a melee spell's default melee reach."""
    reach = getattr(targeting, "range", None)
    return DEFAULT_MELEE_REACH if reach is None else reach


def _check_mobs(content: Content, universe: IdUniverse) -> list[Problem]:
    """A mob's loot (its own and its variants') names items that exist and are worth having (no trash loot, principle
    10), its sounds and death particles exist, and a caster's spells exist, target an enemy and reach farther than one
    step of its kiting."""
    problems: list[Problem] = []
    spells = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.spells}
    # content/texts/entity.yaml names the hand-written entity types (the spell entities) with the same keys.
    entity_texts = {
        name for entry in content.texts if entry.model.id == ENTITY_TEXT_GROUP for name in entry.model.texts
    }
    for entry in content.mobs:
        mob = entry.model
        if mob.id in entity_texts:
            problems.append(
                Problem(
                    entry.source,
                    "id",
                    f"content/texts/{ENTITY_TEXT_GROUP}.yaml already names an entity type {mob.id}: both would be "
                    f"{mob_description_id(mob.id)}",
                )
            )
        drops = [(f"loot.{n}", drop) for n, drop in enumerate(mob.loot)] + [
            (f"variants.{v}.loot.{n}", drop)
            for v, variant in enumerate(mob.variants)
            for n, drop in enumerate(variant.loot)
        ]
        for path, drop in drops:
            if not universe.contains(ITEM_REGISTRY, drop.item):
                problems.append(
                    Problem(entry.source, f"{path}.item", _unknown(drop.item, ITEM_REGISTRY, universe))
                )
            elif drop.item in TRASH_LOOT:
                problems.append(
                    Problem(
                        entry.source,
                        f"{path}.item",
                        f"{drop.item} is trash loot: every drop is worth having (vision); drop something useful",
                    )
                )
        for name, sound in mob.sounds.model_dump().items():
            if sound not in (None, "silent") and not universe.contains(SOUND_EVENT_REGISTRY, sound):
                problems.append(
                    Problem(entry.source, f"sounds.{name}", _unknown(sound, SOUND_EVENT_REGISTRY, universe))
                )
        for n, tag in enumerate(mob.tags):
            if not tag.startswith(f"{MOD_ID}:"):
                problems.append(
                    Problem(
                        entry.source, f"tags.{n}", f"{tag}: a mob joins our own entity type tags ({MOD_ID}:)"
                    )
                )
        if mob.death_particles is not None and not universe.contains(BLOCK_REGISTRY, mob.death_particles):
            problems.append(
                Problem(
                    entry.source, "death_particles", _unknown(mob.death_particles, BLOCK_REGISTRY, universe)
                )
            )
        if mob.caster is None:
            continue
        for n, spell_id in enumerate(mob.caster.spells):
            path = f"caster.spells.{n}"
            spell = spells.get(spell_id)
            if spell is None:
                problems.append(Problem(entry.source, path, _unknown(spell_id, SPELL_REGISTRY, universe)))
                continue
            if spell.targeting.type not in MOB_SPELL_TARGETING:
                problems.append(
                    Problem(
                        entry.source,
                        path,
                        f"{spell_id} targets {spell.targeting.type}; a mob casts at an enemy: "
                        f"{', '.join(MOB_SPELL_TARGETING)}",
                    )
                )
            elif mob.caster.kiting is not None and _reach(spell.targeting) <= mob.caster.kiting.step_blocks:
                problems.append(
                    Problem(
                        entry.source,
                        path,
                        f"{spell_id} reaches {_reach(spell.targeting):g} blocks, no farther than one "
                        f"{mob.caster.kiting.step_blocks:g}-block step of its kiting, so a step could never keep its "
                        "target in range",
                    )
                )
    return problems


# The targeting types a boss casts its phases' spells with: at its target or a player it picks, at a point or along
# a direction aimed at its target, or on itself (architecture 14.3). An ally spell has no ally to find.
BOSS_SPELL_TARGETING = (SELF, ENEMY, CURRENT_TARGET, RANDOM_PLAYER, FARTHEST_PLAYER, GROUND_POINT, DIRECTION)


def _check_bosses(content: Content, universe: IdUniverse) -> list[Problem]:
    """A boss runs on a mob of archetype boss, one boss per such mob and every such mob a boss's, and its phases cast
    spells that exist and that a boss can aim (architecture 14.3)."""
    problems: list[Problem] = []
    spells = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.spells}
    mobs = {mob_id(entry.model.id): entry.model for entry in content.mobs}
    bodies: dict[str, str] = {}
    for entry in content.bosses:
        boss = entry.model
        mob = mobs.get(boss.entity)
        if mob is None:
            problems.append(
                Problem(
                    entry.source,
                    "entity",
                    f"{boss.entity} is no mob of content/mobs/: a boss runs on a mob of archetype {BOSS}",
                )
            )
        elif mob.archetype != BOSS:
            problems.append(
                Problem(
                    entry.source,
                    "entity",
                    f"{boss.entity} is a {mob.archetype} mob: a boss runs on a mob of archetype {BOSS}",
                )
            )
        elif boss.entity in bodies:
            problems.append(
                Problem(entry.source, "entity", f"{boss.entity} is already the body of {bodies[boss.entity]}")
            )
        else:
            bodies[boss.entity] = entry.source
        for n, immune in enumerate(boss.immune_to):
            if not universe.contains(MOB_EFFECT_REGISTRY, immune):
                problems.append(
                    Problem(entry.source, f"immune_to.{n}", _unknown(immune, MOB_EFFECT_REGISTRY, universe))
                )
        for p, phase in enumerate(boss.phases):
            casts = [(f"phases.{p}.on_enter.{n}", spell) for n, spell in enumerate(phase.on_enter)] + [
                (f"phases.{p}.abilities.{n}.spell", ability.spell)
                for n, ability in enumerate(phase.abilities)
            ]
            for path, spell_id in casts:
                spell = spells.get(spell_id)
                if spell is None:
                    problems.append(Problem(entry.source, path, _unknown(spell_id, SPELL_REGISTRY, universe)))
                elif spell.targeting.type not in BOSS_SPELL_TARGETING:
                    problems.append(
                        Problem(
                            entry.source,
                            path,
                            f"{spell_id} targets {spell.targeting.type}; a boss casts with "
                            f"{', '.join(BOSS_SPELL_TARGETING)}",
                        )
                    )
    for entry in content.mobs:
        mob = entry.model
        if mob.archetype == BOSS and mob_id(mob.id) not in bodies:
            problems.append(
                Problem(
                    entry.source,
                    "archetype",
                    f"no boss in content/bosses/ runs on {mob_id(mob.id)}: a boss's body needs its fight",
                )
            )
    return problems


def _check_sounds(content: Content, universe: IdUniverse) -> list[Problem]:
    """An event plays vanilla sound events that exist, never one of ours: a reference to one of ours could loop."""
    problems: list[Problem] = []
    for entry in content.sounds:
        for name, event in entry.model.events.items():
            for n, ref in enumerate(event.sounds):
                path = f"events.{name}.sounds.{n}.event"
                if not ref.event.startswith("minecraft:"):
                    problems.append(
                        Problem(
                            entry.source,
                            path,
                            f"{ref.event}: an event plays vanilla sound events (minecraft:)",
                        )
                    )
                elif not universe.contains(SOUND_EVENT_REGISTRY, ref.event):
                    problems.append(
                        Problem(entry.source, path, _unknown(ref.event, SOUND_EVENT_REGISTRY, universe))
                    )
    return problems


def _check_inner_effects(
    source: str,
    path: str,
    effect: BaseModel,
    spell: SpellDef,
    universe: IdUniverse,
    effects: dict[str, PeriodicDef],
) -> list[Problem]:
    """The effects a projectile runs on the enemy it hits, or an area on the enemies or allies inside it, fit them as
    the spell's targeting would."""
    if effect.type == PROJECTILE:
        reached, what = ENEMY, "the enemy a projectile hits"
    elif effect.type == AREA:
        reached, what = (
            (ENEMY, "an area's enemies") if effect.affects == "enemies" else (ALLY, "an area's allies")
        )
    elif effect.type == MELEE:
        reached, what = ENEMY, "the enemies in a melee shape"
    else:
        return []
    problems: list[Problem] = []
    for m, inner in enumerate(effect.effects):
        inner_path = f"{path}.effects.{m}"
        problems += _check_effect(source, inner_path, inner, spell, universe, effects)
        if reached not in _EFFECT_TARGETING[inner.type]:
            problems.append(Problem(source, f"{inner_path}.type", f"{inner.type} cannot act on {what}"))
        if effect.type == AREA:
            problems += _check_reapply(
                source, inner_path, inner, effect.interval_seconds, "the area", effects
            )
    return problems


def _check_channel_reapply(
    source: str, path: str, effect: BaseModel, interval: float, effects: dict[str, PeriodicDef]
) -> list[Problem]:
    """A channel runs its effects at every interval: what it applies, and what the projectile or area it sends at
    every interval applies, is applied again then."""
    problems = _check_reapply(source, path, effect, interval, "the channel", effects)
    if effect.type in (PROJECTILE, AREA, MELEE):
        for m, inner in enumerate(effect.effects):
            problems += _check_reapply(source, f"{path}.effects.{m}", inner, interval, "the channel", effects)
    return problems


def _check_reapply(
    source: str, path: str, effect: BaseModel, interval: float, what: str, effects: dict[str, PeriodicDef]
) -> list[Problem]:
    """An area or a channel applies one of our effects with damage or healing over time again no sooner than the
    effect ticks. Its ticks count the remaining duration, which every application resets, so an effect applied again
    sooner never ticks while it is (RLM-376); as often as it ticks loses only the partial interval, as in Classic."""
    status = getattr(effect, "effect", None)
    periodic = effects.get(status) if effect.type == APPLY_EFFECT else None
    if periodic is None or periodic.interval_seconds <= interval + 1e-9:
        return []
    return [
        Problem(
            source,
            f"{path}.effect",
            f"{status} ticks every {periodic.interval_seconds:g} s and {what} applies it again every "
            f"{interval:g} s: each application resets its duration before it ticks, so it never ticks while "
            f"{what} keeps applying it; give {what} an interval of at least {periodic.interval_seconds:g} s",
        )
    ]


def _check_effect(
    source: str,
    path: str,
    effect: BaseModel,
    spell: SpellDef,
    universe: IdUniverse,
    effects: dict[str, PeriodicDef],
) -> list[Problem]:
    """An effect's share of attack damage fits what the spell scales with, and its status effect exists; a status
    effect a spell applies is not an instant one, and one of ours with damage or healing over time lasts a whole
    number of its intervals (``effects``: our periodic effects by id)."""
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
    if spell.scales_with == "attack_damage" and getattr(effect, "spell_power_percent", None) is not None:
        problems.append(
            Problem(
                source,
                f"{path}.spell_power_percent",
                "the spell scales with attack_damage, so spell power adds nothing to it",
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
    periodic = effects.get(status) if effect.type == APPLY_EFFECT else None
    if periodic is not None:
        for field in ("duration_seconds", "duration_per_level_seconds"):
            seconds = getattr(effect, field)
            if seconds is None:
                continue
            ticks = seconds / periodic.interval_seconds
            if abs(ticks - round(ticks)) > 1e-9:
                problems.append(
                    Problem(
                        source,
                        f"{path}.{field}",
                        f"{status} ticks every {periodic.interval_seconds:g} s, so it lasts a whole number of its "
                        f"intervals at every level, and {field} {seconds:g} s is not one",
                    )
                )
    if status is not None and not universe.contains(MOB_EFFECT_REGISTRY, status):
        problems.append(Problem(source, f"{path}.effect", _unknown(status, MOB_EFFECT_REGISTRY, universe)))
    elif effect.type == APPLY_EFFECT and status in INSTANT_MOB_EFFECTS:
        problems.append(
            Problem(
                source,
                f"{path}.effect",
                f"{status} is instant and would apply on every tick of its duration: {INSTANT_MOB_EFFECTS[status]}",
            )
        )
    return problems


def _check_characters(content: Content, universe: IdUniverse) -> list[Problem]:
    """Races name classes that exist, and every class is open to some race, or no player could take it. Modifiers
    name attributes that exist, in their unit as for stat rules (architecture 8.3), and add their percents to the
    base (architecture 8.2); kit items exist, and every kit holds the starting bread. A race's racial spell and a
    class's spells are players' spells; a racial spell is free, as a race spans classes of different resources, and a
    class's spells cost its own resource, at most 4 x N of them by the end of chapter N (decision D4). A racial passive
    names an item tag that exists, and its modifiers add their percents to the base too."""
    problems: list[Problem] = []
    stats = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.stats}
    spells = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.spells}
    class_registry = f"{MOD_ID}:class"
    taken: set[str] = set()
    for entry in content.races:
        for n, class_id in enumerate(entry.model.classes):
            taken.add(class_id)
            if not universe.contains(class_registry, class_id):
                problems.append(
                    Problem(entry.source, f"classes.{n}", _unknown(class_id, class_registry, universe))
                )
        problems += _check_racials(entry, spells, stats, universe)
    for entry in (*content.races, *content.classes):
        for n, modifier in enumerate(entry.model.modifiers):
            problems += _check_adds_percents(entry.source, f"modifiers.{n}", modifier.operation)
            problems += _check_modifier(entry.source, f"modifiers.{n}", modifier, stats, universe)
    for entry in content.classes:
        problems += _check_class_spells(entry, spells, content)
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


def _check_adds_percents(source: str, path: str, operation: str) -> list[Problem]:
    """A stat bonus (a stat rule's, a race's, a class's, gear's) adds its percents to the base: every value is
    ``(default + flat) * (1 + sum of percents)`` (Matěj, RLM-170, architecture 8.2), so no bonus multiplies the
    total on its own. Status effects may (a slow, a boss phase's Cracked Stone)."""
    if operation != "add_multiplied_total":
        return []
    return [
        Problem(
            source,
            f"{path}.operation",
            "stat bonuses add their percents, (default + flat) * (1 + sum of percents): use add_multiplied_base, "
            "not add_multiplied_total (architecture 8.2)",
        )
    ]


def _check_racials(
    entry: Entry, spells: dict[str, SpellDef], stats: dict[str, StatDef], universe: IdUniverse
) -> list[Problem]:
    """A race's racial spell is one of our player spells, free; its racial passive names an item tag that exists and
    attributes that exist, and its {bonus} reads as a number."""
    problems: list[Problem] = []
    race = entry.model
    if race.racial_spell is not None:
        spell = spells.get(race.racial_spell)
        if spell is None:
            problems.append(
                Problem(entry.source, "racial_spell", _unknown(race.racial_spell, SPELL_REGISTRY, universe))
            )
        elif spell.npc_only:
            problems.append(
                Problem(entry.source, "racial_spell", f"{race.racial_spell} is a mob's spell (npc_only)")
            )
        elif spell.cost is not None:
            problems.append(
                Problem(
                    entry.source,
                    "racial_spell",
                    f"{race.racial_spell} costs {spell.cost.resource}: a racial spell is free, as the race's classes "
                    "use different resources",
                )
            )
    passive = race.racial_passive
    if passive is None:
        return problems
    if not universe.contains_tag(ITEM_REGISTRY, passive.weapons):
        problems.append(
            Problem(entry.source, "racial_passive.weapons", f"unknown item tag {passive.weapons!r}")
        )
    for n, modifier in enumerate(passive.modifiers):
        path = f"racial_passive.modifiers.{n}"
        problems += _check_modifier(entry.source, path, modifier, stats, universe)
        problems += _check_adds_percents(entry.source, path, modifier.operation)
    first = passive.modifiers[0]
    stat = stats.get(first.attribute)
    if (
        "{bonus}" in passive.description.en_us
        and first.operation == "add_value"
        and stat is not None
        and stat.percent
    ):
        problems.append(
            Problem(
                entry.source,
                "racial_passive.description",
                f"{{bonus}} reads the first modifier, add_value on the share {first.attribute}: give it a multiplied "
                "operation or describe the number another way",
            )
        )
    return problems


def _check_class_spells(entry: Entry, spells: dict[str, SpellDef], content: Content) -> list[Problem]:
    """A class's spells are our player spells that cost the class's resource (or nothing), and the class teaches at
    most 4 x N of them by the last level of chapter N (decision D4: chapter 1 allows 4 spells, chapter 2 eight)."""
    problems: list[Problem] = []
    player_class = entry.model
    for n, taught in enumerate(player_class.spells):
        spell = spells.get(taught.spell)
        if spell is None:
            problems.append(
                Problem(
                    entry.source, f"spells.{n}.spell", f"{taught.spell} is not a spell of content/spells/"
                )
            )
        elif spell.npc_only:
            problems.append(
                Problem(entry.source, f"spells.{n}.spell", f"{taught.spell} is a mob's spell (npc_only)")
            )
        elif spell.cost is not None and spell.cost.resource != player_class.resource:
            problems.append(
                Problem(
                    entry.source,
                    f"spells.{n}.spell",
                    f"{taught.spell} costs {spell.cost.resource}, and the class's resource is {player_class.resource}",
                )
            )
    for chapter in sorted((entry.model for entry in content.chapters), key=lambda chapter: chapter.order):
        known = sum(1 for taught in player_class.spells if taught.level <= chapter.level_cap)
        allowed = 4 * chapter.order
        if known > allowed:
            problems.append(
                Problem(
                    entry.source,
                    "spells",
                    f"{known} spells by level {chapter.level_cap}, the end of chapter {chapter.order}, which allows "
                    f"{allowed} (decision D4: 4 x N)",
                )
            )
    return problems


def _check_modifier(
    source: str, path: str, modifier: CharacterModifierDef, stats: dict[str, StatDef], universe: IdUniverse
) -> list[Problem]:
    """A race's, class's or status effect's attribute modifier names an attribute that exists, in its unit as for
    stat rules (architecture 8.3): add_value on one of our shares takes amount_percent, on points amount."""
    if not universe.contains(ATTRIBUTE_REGISTRY, modifier.attribute):
        return [
            Problem(source, f"{path}.attribute", _unknown(modifier.attribute, ATTRIBUTE_REGISTRY, universe))
        ]
    target = stats.get(modifier.attribute)
    if modifier.operation != "add_value" or target is None:
        return []
    if target.percent and not modifier.is_percent:
        return [
            Problem(
                source,
                f"{path}.amount",
                f"{modifier.attribute} is a share: write the amount as amount_percent",
            )
        ]
    if not target.percent and modifier.is_percent:
        return [
            Problem(
                source,
                f"{path}.amount_percent",
                f"{modifier.attribute} counts points: add_value takes amount, not amount_percent",
            )
        ]
    return []


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
    dimension (architecture 5.2). Each structure floor names a structure or a structure tag that exists."""
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
        rule = entry.model.mob_levels
        for n, floor in enumerate(rule.structure_floors or [] if rule is not None else []):
            problem = _unknown_structure(floor.structure, universe)
            if problem is not None:
                problems.append(Problem(entry.source, f"mob_levels.structure_floors.{n}.structure", problem))
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
    one block of (the dungeon exit, the realm portal's) has one block at most; the realm portal's three kinds come
    together, since a portal needs its frame, its sealstone and its portal block."""
    problems: list[Problem] = []
    portal_kinds = {
        entry.model.kind: entry.source for entry in content.blocks if entry.model.kind in REALM_PORTAL_KINDS
    }
    if portal_kinds:
        missing = [kind for kind in REALM_PORTAL_KINDS if kind not in portal_kinds]
        if missing:
            source = next(iter(portal_kinds.values()))
            problems.append(
                Problem(
                    source,
                    "kind",
                    f"the realm portal needs a block of each of {', '.join(REALM_PORTAL_KINDS)}; "
                    f"none is {', '.join(missing)}",
                )
            )
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
    """Every ingredient of an item's or block's recipe is an item or a vanilla item tag, and a smithing upgrade
    starts from another item than the one it makes. A replaced vanilla recipe exists and is replaced once, and makes an
    item."""
    problems: list[Problem] = []
    for entry in [*content.items, *content.blocks, *content.recipes]:
        recipe = entry.model.recipe
        if recipe is None:
            continue
        for path, ingredient in recipe.ingredients_by_path():
            unknown = _unknown_ingredient(ingredient, universe)
            if unknown is not None:
                problems.append(Problem(entry.source, f"recipe.{path}", unknown))
        if getattr(recipe, "base", None) == f"{MOD_ID}:{entry.model.id}":
            problems.append(
                Problem(entry.source, "recipe.base", "a smithing upgrade makes the item from another one")
            )
    replaced: dict[str, str] = {}
    for entry in content.recipes:
        override = entry.model
        if not universe.contains(RECIPE_REGISTRY, override.replaces):
            problems.append(
                Problem(entry.source, "replaces", _unknown(override.replaces, RECIPE_REGISTRY, universe))
            )
        if override.replaces in replaced:
            problems.append(
                Problem(
                    entry.source,
                    "replaces",
                    f"{override.replaces} is already replaced by {replaced[override.replaces]}",
                )
            )
        replaced.setdefault(override.replaces, entry.source)
        if not universe.contains(ITEM_REGISTRY, override.result):
            problems.append(
                Problem(entry.source, "result", _unknown(override.result, ITEM_REGISTRY, universe))
            )
    return problems


def _unknown_ingredient(ingredient: str, universe: IdUniverse) -> str | None:
    """Why a recipe ingredient or repair material, an item or an item tag (``#`` first), is not in the ID universe,
    or None when it is."""
    if not is_tag(ingredient):
        return (
            None
            if universe.contains(ITEM_REGISTRY, ingredient)
            else _unknown(ingredient, ITEM_REGISTRY, universe)
        )
    tag = ingredient.removeprefix("#")
    if universe.contains_tag(ITEM_REGISTRY, tag):
        return None
    message = f"unknown tag {ingredient!r} in registry {ITEM_REGISTRY}"
    close = difflib.get_close_matches(tag, universe.tags.get(ITEM_REGISTRY, set()), n=3)
    if close:
        message += f" (did you mean: {', '.join('#' + match for match in close)}?)"
    return message


def _check_gear(content: Content, universe: IdUniverse) -> list[Problem]:
    """Gear's fixed stats name attributes that exist, in their unit as for stat rules (architecture 8.3), add their
    percents to the base (architecture 8.2), and never max health, attack damage or attack speed; its repair material and equip sound exist; the items that share a
    look are armor of one colour, so its placeholder texture has one."""
    problems: list[Problem] = []
    stats = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.stats}
    colors: dict[str, tuple[str | None, str]] = {}
    for entry in content.items:
        gear = entry.model.gear
        if gear is None:
            continue
        for n, modifier in enumerate(gear.modifiers):
            path = f"gear.modifiers.{n}"
            problems += _check_adds_percents(entry.source, path, modifier.operation)
            forbidden = GEAR_FORBIDDEN_ATTRIBUTES.get(modifier.attribute)
            if forbidden is not None:
                problems.append(Problem(entry.source, f"{path}.attribute", forbidden))
            else:
                problems += _check_modifier(entry.source, path, modifier, stats, universe)
        if gear.repair_with is not None:
            unknown = _unknown_ingredient(gear.repair_with, universe)
            if unknown is not None:
                problems.append(Problem(entry.source, "gear.repair_with", unknown))
        if not universe.contains(SOUND_EVENT_REGISTRY, gear.equip_sound):
            problems.append(
                Problem(
                    entry.source,
                    "gear.equip_sound",
                    _unknown(gear.equip_sound, SOUND_EVENT_REGISTRY, universe),
                )
            )
        if gear.look is not None:
            seen = colors.setdefault(gear.look, (entry.model.color, entry.source))
            if seen[0] != entry.model.color:
                problems.append(
                    Problem(
                        entry.source,
                        "color",
                        f"the items of look {gear.look} share one colour, its placeholder's: {seen[1]} has "
                        f"{seen[0] or 'none'}, this one {entry.model.color or 'none'}",
                    )
                )
    return problems


def _check_item_drops(content: Content, universe: IdUniverse) -> list[Problem]:
    """An item's ``dropped_by`` names mobs that are not ours and have a loot table, which its loot modifier adds to;
    our own mobs list the item in their ``loot`` (content/mobs/)."""
    problems: list[Problem] = []
    for entry in content.items:
        for n, drop in enumerate(entry.model.dropped_by):
            path = f"dropped_by.{n}.entity"
            namespace, entity_path = drop.entity.split(":", 1)
            if namespace == MOD_ID:
                problems.append(
                    Problem(
                        entry.source,
                        path,
                        f"{drop.entity} is ours: list the item in its loot (content/mobs/{entity_path}.yaml)",
                    )
                )
            elif not universe.contains(ENTITY_TYPE_REGISTRY, drop.entity):
                problems.append(
                    Problem(entry.source, path, _unknown(drop.entity, ENTITY_TYPE_REGISTRY, universe))
                )
            elif not universe.contains(LOOT_TABLE_REGISTRY, entity_loot_table(drop.entity)):
                problems.append(
                    Problem(
                        entry.source,
                        path,
                        f"{drop.entity} has no loot table {entity_loot_table(drop.entity)}",
                    )
                )
    return problems


def _check_realms(content: Content, universe: IdUniverse) -> list[Problem]:
    """A realm names a dimension of ours that exists, one realm per dimension; the dimension's rules name the realm's
    unlock advancement in their access requirements, and the realm's level band starts at their entry level and ends
    at their max level or below; its dimension type links by a coordinate scale above 1 and has beds that set no spawn
    and do not explode. Its signet is an item of ours that stacks to 1, the signet of no other realm; its particle is
    one without options; its arrival floor is a block, and a built frame fits the dimension at its arrival heights.
    Overworld's entry, which is no realm, needs only the arrival (realm portal spec, "Data")."""
    problems: list[Problem] = []
    rules = {entry.model.dimension: entry for entry in content.dimensions}
    dimension_files = {
        f"{MOD_ID}:{file.id}": file for file in content.worldgen if file.directory == "dimension"
    }
    types = {f"{MOD_ID}:{file.id}": file for file in content.worldgen if file.directory == "dimension_type"}
    items = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.items}
    seen: dict[str, str] = {}
    signets: dict[str, str] = {}
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
        if not universe.contains(BLOCK_REGISTRY, realm.arrival.floor):
            problems.append(
                Problem(
                    entry.source, "arrival.floor", _unknown(realm.arrival.floor, BLOCK_REGISTRY, universe)
                )
            )
        if realm.is_overworld:
            problems += _arrival_height_problems(
                entry.source, realm.arrival.y, OVERWORLD_MIN_Y, OVERWORLD_HEIGHT
            )
            continue
        if realm.icon is not None and not universe.contains(ITEM_REGISTRY, realm.icon):
            problems.append(Problem(entry.source, "icon", _unknown(realm.icon, ITEM_REGISTRY, universe)))
        problems += _realm_signet_problems(entry.source, realm.signet, items, signets)
        if realm.particle is not None and realm.particle not in PORTAL_PARTICLES:
            problems.append(
                Problem(
                    entry.source,
                    "particle",
                    f"{realm.particle} is not a particle without options that suits a portal: "
                    f"one of {', '.join(PORTAL_PARTICLES)}",
                )
            )
        elif realm.particle is not None and not universe.contains(PARTICLE_TYPE_REGISTRY, realm.particle):
            problems.append(
                Problem(entry.source, "particle", _unknown(realm.particle, PARTICLE_TYPE_REGISTRY, universe))
            )
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
        if rule is not None and rule.model.mob_levels is not None and realm.levels is not None:
            band = rule.model.mob_levels
            if realm.levels[0] != band.entry_level or realm.levels[1] > band.max_level:
                problems.append(
                    Problem(
                        entry.source,
                        "levels",
                        f"{realm.levels} starts at the entry level of {realm.dimension}'s rules, "
                        f"{band.entry_level}, and ends at its max_level, {band.max_level}, or below",
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
        min_y, height = dimension_type.data.get("min_y"), dimension_type.data.get("height")
        if isinstance(min_y, int) and isinstance(height, int):
            problems += _arrival_height_problems(entry.source, realm.arrival.y, min_y, height)
    return problems


def _realm_signet_problems(
    source: str, signet: str | None, items: dict[str, ItemDef], signets: dict[str, str]
) -> list[Problem]:
    """A realm's signet is an item of ours that stacks to 1, and no other realm's: the sealstone needs one destination
    for each signet."""
    if signet is None:
        return []
    problems: list[Problem] = []
    item = items.get(signet)
    if item is None:
        problems.append(Problem(source, "signet", f"{signet} is not an item of ours (content/items/)"))
    elif item.max_stack_size != 1:
        problems.append(
            Problem(source, "signet", f"a signet stacks to 1: {signet} stacks to {item.max_stack_size}")
        )
    if signet in signets:
        problems.append(Problem(source, "signet", f"{signet} is already the signet of {signets[signet]}"))
    signets.setdefault(signet, source)
    return problems


def _arrival_height_problems(source: str, heights: list[int], min_y: int, height: int) -> list[Problem]:
    """A built frame's bottom row is set into the floor, so it is the lowest block the frame needs, and the frame
    reaches FRAME_HEIGHT blocks up from it, all inside the dimension."""
    lowest, highest = min_y, min_y + height - FRAME_HEIGHT
    if heights[0] < lowest or heights[1] > highest:
        return [
            Problem(
                source,
                "arrival.y",
                f"a built frame's bottom row stands at y {lowest} to {highest} in this dimension, "
                f"set into the floor with its top row inside: not {heights[0]} to {heights[1]}",
            )
        ]
    return []


def _realm_dimension_type_problems(source: str, data: dict) -> list[Problem]:
    problems: list[Problem] = []
    scale = data.get("coordinate_scale")
    if not isinstance(scale, int | float) or isinstance(scale, bool) or scale <= 1:
        problems.append(
            Problem(
                source, "coordinate_scale", "a realm links to Overworld by a coordinate scale above 1 (1:8)"
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


def _check_quests(content: Content, universe: IdUniverse) -> list[Problem]:
    """A quest's chapter, dependencies, icon, tasks and rewards exist; dependencies form no cycle; the quests of a
    chapter stand apart; a chapter with quests has exactly one quest that completes it; and no two objects of the quest
    book share an id, which would cost a player's progress (architecture 16)."""
    problems: list[Problem] = []
    chapters = {entry.model.id: entry.model for entry in content.chapters}
    realms = {entry.model.id: entry.model for entry in content.realms}
    quests = {entry.model.id: entry for entry in content.quests}
    npc_spells = {f"{MOD_ID}:{entry.model.id}" for entry in content.spells if entry.model.npc_only}
    for entry in content.quests:
        quest = entry.model
        if quest.chapter not in chapters:
            problems.append(
                Problem(entry.source, "chapter", f"unknown chapter {quest.chapter!r} (content/chapters/)")
            )
        if quest.realm not in realms:
            problems.append(
                Problem(entry.source, "realm", f"unknown realm {quest.realm!r} (content/realms/)")
            )
        problems += _quest_where_and_with_what(entry.source, quest, content, universe)
        for i, dependency in enumerate(quest.dependencies):
            if dependency not in quests:
                problems.append(
                    Problem(
                        entry.source, f"dependencies.{i}", f"unknown quest {dependency!r} (content/quests/)"
                    )
                )
        if quest.icon is not None and not universe.contains(ITEM_REGISTRY, quest.icon):
            problems.append(Problem(entry.source, "icon", _unknown(quest.icon, ITEM_REGISTRY, universe)))
        for i, task in enumerate(quest.tasks):
            problems += _quest_task_problems(entry.source, f"tasks.{i}", task, universe)
            if isinstance(task, SpellTaskDef) and task.spell is not None and task.spell in npc_spells:
                problems.append(
                    Problem(entry.source, f"tasks.{i}.spell", f"{task.spell} is npc_only: no player casts it")
                )
        for i, reward in enumerate(quest.rewards):
            problems += _quest_reward_problems(entry.source, f"rewards.{i}", reward, universe)
    problems += _quest_cycles(content)

    by_chapter: dict[str, list] = {}
    for entry in content.quests:
        by_chapter.setdefault(entry.model.chapter, []).append(entry)
    for chapter, entries in sorted(by_chapter.items()):
        placed: dict[tuple[float, float], str] = {}
        for entry in entries:
            position = (entry.model.position.x, entry.model.position.y)
            if position in placed:
                problems.append(
                    Problem(
                        entry.source,
                        "position",
                        f"stands on {placed[position]}'s place in chapter {chapter!r}: quests of a chapter stand apart",
                    )
                )
            placed.setdefault(position, entry.model.id)
        completing = [entry.model.id for entry in entries if entry.model.completes_chapter]
        if len(completing) != 1:
            found = ", ".join(completing) if completing else "none"
            problems.append(
                Problem(
                    "content/quests/",
                    "",
                    f"chapter {chapter!r} needs exactly one quest with completes_chapter: true, its last one, which "
                    f"grants realmforge:{chapter_advancement(chapter)} (found: {found})",
                )
            )

    owners: dict[int, str] = {}
    keys = [chapter_key(chapter) for chapter in sorted(by_chapter)]
    keys += [key for entry in content.quests for key in quest_object_keys(entry.model)]
    for key in keys:
        object_id_ = object_id(key)
        if object_id_ in owners:
            problems.append(
                Problem(
                    "content/quests/",
                    "",
                    f"{key} and {owners[object_id_]} share the quest book id {code_string(object_id_)}: give one of "
                    "them another key",
                )
            )
        owners.setdefault(object_id_, key)
    return problems


def _quest_where_and_with_what(
    source: str, quest: QuestDef, content: Content, universe: IdUniverse
) -> list[Problem]:
    """Where and with what (Q1-3): the place and the items exist, every item to craft has a recipe, and the
    recommended level lies in the realm's level band and below the cap the quest's chapter leaves."""
    problems: list[Problem] = []
    if quest.place is not None:
        for field, id_, registry in (
            ("structure", quest.place.structure, WORLDGEN_STRUCTURE_REGISTRY),
            ("biome", quest.place.biome, BIOME_REGISTRY),
        ):
            if id_ is not None and not universe.contains(registry, id_):
                problems.append(Problem(source, f"place.{field}", _unknown(id_, registry, universe)))
    for field, items in (("bring", quest.bring), ("craft", quest.craft)):
        for i, item in enumerate(items):
            if not universe.contains(ITEM_REGISTRY, item):
                problems.append(Problem(source, f"{field}.{i}", _unknown(item, ITEM_REGISTRY, universe)))
            elif field == "craft" and not _has_recipe(item, content, universe):
                problems.append(
                    Problem(
                        source,
                        f"craft.{i}",
                        f"no recipe makes {item}: name an item with a recipe (content/items/, content/blocks/, "
                        "content/recipes/ or vanilla's)",
                    )
                )
    if quest.level is not None:
        realm = next((entry.model for entry in content.realms if entry.model.id == quest.realm), None)
        if (
            realm is not None
            and realm.levels is not None
            and not realm.levels[0] <= quest.level <= realm.levels[1]
        ):
            problems.append(
                Problem(
                    source,
                    "level",
                    f"level {quest.level} lies outside {quest.realm}'s level band {realm.levels[0]}-{realm.levels[1]}",
                )
            )
        chapter = next((entry.model for entry in content.chapters if entry.model.id == quest.chapter), None)
        if chapter is not None and quest.level > chapter.completed_level_cap:
            problems.append(
                Problem(
                    source,
                    "level",
                    f"level {quest.level} lies above {quest.chapter}'s cap once completed, "
                    f"{chapter.completed_level_cap}: a player on its page never gets there",
                )
            )
    return problems


def _has_recipe(item: str, content: Content, universe: IdUniverse) -> bool:
    """Whether a recipe makes the item: our item's or block's own recipe, a content/recipes/ override's result, or a
    vanilla recipe named after it (``minecraft:iron_pickaxe``, ``minecraft:iron_ingot_from_smelting_iron_ore``)."""
    namespace, path = item.split(":", 1)
    if namespace == MOD_ID:
        return any(
            entry.model.id == path and entry.model.recipe is not None
            for entry in (*content.items, *content.blocks)
        )
    if any(entry.model.result == item for entry in content.recipes):
        return True
    recipes = universe.registries.get(RECIPE_REGISTRY, set())
    return item in recipes or any(recipe.startswith(f"{item}_from_") for recipe in recipes)


def _quest_task_problems(source: str, path: str, task: TaskDef, universe: IdUniverse) -> list[Problem]:
    references: list[tuple[str, str, str]] = []  # field, id, registry
    match task:
        case ItemTaskDef():
            references.append(("item", task.item, ITEM_REGISTRY))
        case AdvancementTaskDef():
            references.append(("advancement", task.advancement, ADVANCEMENT_REGISTRY))
        case StatTaskDef():
            references.append(("stat", task.stat, CUSTOM_STAT_REGISTRY))
        case KillTaskDef():
            references.append(("entity", task.entity, ENTITY_TYPE_REGISTRY))
        case DimensionTaskDef():
            references.append(("dimension", task.dimension, DIMENSION_REGISTRY))
        case DungeonTaskDef() if task.dungeon is not None:
            references.append(("dungeon", task.dungeon, DUNGEON_TYPE_REGISTRY))
        case SpellTaskDef() if task.spell is not None:
            references.append(("spell", task.spell, SPELL_REGISTRY))
    return [
        Problem(source, f"{path}.{field}", _unknown(id_, registry, universe))
        for field, id_, registry in references
        if not universe.contains(registry, id_)
    ]


def _quest_reward_problems(source: str, path: str, reward: RewardDef, universe: IdUniverse) -> list[Problem]:
    match reward:
        case ItemRewardDef() if not universe.contains(ITEM_REGISTRY, reward.item):
            return [Problem(source, f"{path}.item", _unknown(reward.item, ITEM_REGISTRY, universe))]
        case AdvancementRewardDef():
            advancement = reward.advancement
            if advancement.startswith(f"{MOD_ID}:{chapter_advancement('')}"):
                return [
                    Problem(
                        source,
                        f"{path}.advancement",
                        "a chapter's advancement comes from completes_chapter: true on the chapter's last quest",
                    )
                ]
            if not advancement.startswith(f"{MOD_ID}:"):
                return [
                    Problem(
                        source,
                        f"{path}.advancement",
                        f"{advancement!r} is not ours: a quest grants only our own advancements (realmforge:...)",
                    )
                ]
            if not universe.contains(ADVANCEMENT_REGISTRY, advancement):
                return [
                    Problem(
                        source, f"{path}.advancement", _unknown(advancement, ADVANCEMENT_REGISTRY, universe)
                    )
                ]
    return []


def _quest_cycles(content: Content) -> list[Problem]:
    """Quests whose dependencies, a final boss quest's mandatory quests included, lead back to themselves: FTB Quests
    could never open them."""
    entries = {entry.model.id: entry for entry in content.quests}
    models = [entry.model for entry in content.quests]
    dependencies = {quest.id: quest_dependencies(quest, models) for quest in models}
    done: set[str] = set()
    cycles: set[tuple[str, ...]] = set()
    # A depth-first walk with its own stack (a campaign's chains can be longer than Python's recursion limit): the
    # path, and for each quest on it the dependencies still to follow.
    for start_id in sorted(entries):
        if start_id in done:
            continue
        path: list[str] = [start_id]
        pending: list[list[str]] = [list(dependencies[start_id])]
        while path:
            if not pending[-1]:
                done.add(path.pop())
                pending.pop()
                continue
            dependency = pending[-1].pop(0)
            if dependency not in entries or dependency in done:
                continue
            if dependency in path:
                cycle = path[path.index(dependency) :]
                first = cycle.index(min(cycle))
                cycles.add(tuple(cycle[first:] + cycle[:first]))
                continue
            path.append(dependency)
            pending.append(list(dependencies[dependency]))
    return [
        Problem(
            entries[cycle[0]].source,
            "dependencies",
            f"the dependencies form a cycle, so no quest of it can open (each waits for the next): "
            f"{' -> '.join((*cycle, cycle[0]))}",
        )
        for cycle in sorted(cycles)
    ]


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


def _check_apotheosis(content: Content, universe: IdUniverse, paths: RepoPaths) -> list[Problem]:
    """Apotheosis's data, fitted (ADR 0063, architecture 19.4): every changed entry, remap and removed recipe names
    something Apotheosis or vanilla has, a replaced field keeps Apotheosis's loot categories, rarities and affixes,
    and no remapped stat is left where no remap applies (``realmforge_tools/apotheosis.py``)."""
    try:
        snapshot = load_snapshot(paths)
    except FileNotFoundError as e:
        return [Problem(paths.rel(paths.id_universe), "", str(e))]
    problems = list(fit(content, snapshot).problems)
    attributes = universe.registries.get(ATTRIBUTE_REGISTRY, set()) | _snapshot_attributes(snapshot)
    for entry in [*content.apotheosis_affixes, *content.apotheosis_gems, *content.apotheosis_tier_augments]:
        problems += _check_replaced_fields(entry, snapshot, attributes)
    seen: dict[str, str] = {}
    for entry in content.apotheosis_stat_remaps:
        remap = entry.model
        if remap.source in seen:
            problems.append(
                Problem(entry.source, "source", f"{remap.source} is already remapped by {seen[remap.source]}")
            )
        seen.setdefault(remap.source, entry.source)
        if remap.target is not None and not universe.contains(ATTRIBUTE_REGISTRY, remap.target):
            problems.append(
                Problem(entry.source, "target", _unknown(remap.target, ATTRIBUTE_REGISTRY, universe))
            )
    replaced = {entry.model.replaces: entry.source for entry in content.recipes}
    removed: dict[str, str] = {}
    for entry in content.recipe_removals:
        for n, recipe in enumerate(entry.model.recipes):
            location = f"recipes.{n}"
            if recipe.startswith(f"{MOD_ID}:"):
                problems.append(
                    Problem(
                        entry.source, location, f"{recipe} is our own: change the item's or block's recipe"
                    )
                )
            elif recipe not in snapshot.recipes and not universe.contains(RECIPE_REGISTRY, recipe):
                problems.append(
                    Problem(
                        entry.source,
                        location,
                        f"neither vanilla nor Apotheosis {snapshot.version} has a recipe {recipe}",
                    )
                )
            if recipe in removed:
                problems.append(
                    Problem(entry.source, location, f"{recipe} is already removed by {removed[recipe]}")
                )
            removed.setdefault(recipe, entry.source)
            if recipe in replaced:
                problems.append(Problem(entry.source, location, f"{replaced[recipe]} replaces {recipe}"))
    return problems


def _snapshot_attributes(snapshot: Snapshot) -> set[str]:
    """Every attribute Apotheosis's own data names: Apothic Attributes' are not in the ID universe."""
    found: set[str] = set()

    def visit(value: object, name: str = "") -> None:
        if isinstance(value, dict):
            for key, item in value.items():
                visit(item, key)
        elif isinstance(value, list):
            for item in value:
                visit(item, name)
        elif isinstance(value, str) and name in ("attribute", "attributes"):
            found.add(value)

    for kind in snapshot.entries.values():
        visit(kind)
    return found


def _check_replaced_fields(
    entry: Entry[ApotheosisEntryDef], snapshot: Snapshot, attributes: set[str]
) -> list[Problem]:
    """The fields an entry replaces that name loot categories, rarities, affixes or an attribute name ones that
    exist."""
    problems: list[Problem] = []
    model = entry.model
    for name, value in model.replace.items():
        location = f"replace.{name}"
        if name in ("categories", "types") and isinstance(value, list):
            for n, category in enumerate(value):
                if category in CURIOS_LOOT_CATEGORIES:
                    problems.append(
                        Problem(
                            entry.source,
                            f"{location}.{n}",
                            f"{category} exists only with Curios, which the pack lacks: Apotheosis would skip the "
                            "whole entry",
                        )
                    )
                elif category not in LOOT_CATEGORIES:
                    problems.append(
                        Problem(
                            entry.source,
                            f"{location}.{n}",
                            f"unknown loot category {category!r}; Apotheosis has {', '.join(LOOT_CATEGORIES)}",
                        )
                    )
        elif name == "values" and model.KIND == "affixes" and isinstance(value, dict):
            unknown = sorted(set(value) - set(RARITIES))
            if unknown:
                problems.append(
                    Problem(
                        entry.source,
                        location,
                        f"unknown rarities {', '.join(unknown)}; Apotheosis has {', '.join(RARITIES)}",
                    )
                )
        elif name == "definition" and model.KIND == "affixes" and isinstance(value, dict):
            exclusive = value.get("exclusive_set", [])
            for n, affix in enumerate(exclusive if isinstance(exclusive, list) else []):
                if affix == model.target:
                    problems.append(
                        Problem(
                            entry.source, f"{location}.exclusive_set.{n}", f"{affix} cannot exclude itself"
                        )
                    )
                elif snapshot.entry("affixes", str(affix)) is None:
                    problems.append(
                        Problem(
                            entry.source,
                            f"{location}.exclusive_set.{n}",
                            f"Apotheosis {snapshot.version} has no affix {affix}",
                        )
                    )
        elif name == "attribute" and isinstance(value, str) and value not in attributes:
            problems.append(Problem(entry.source, location, f"unknown attribute {value!r}"))
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


def _unknown_structure(reference: str, universe: IdUniverse) -> str | None:
    """Why a structure or a structure tag (``#`` first) is not in the ID universe, or None when it is."""
    if not reference.startswith("#"):
        return (
            None
            if universe.contains(WORLDGEN_STRUCTURE_REGISTRY, reference)
            else _unknown(reference, WORLDGEN_STRUCTURE_REGISTRY, universe)
        )
    tag = reference.removeprefix("#")
    if universe.contains_tag(WORLDGEN_STRUCTURE_REGISTRY, tag):
        return None
    message = f"unknown tag {reference!r} in registry {WORLDGEN_STRUCTURE_REGISTRY}"
    close = difflib.get_close_matches(tag, universe.tags.get(WORLDGEN_STRUCTURE_REGISTRY, set()), n=3)
    if close:
        message += f" (did you mean: {', '.join('#' + match for match in close)}?)"
    return message


def _unknown(id_: str, registry: str, universe: IdUniverse) -> str:
    message = f"unknown id {id_!r} in registry {registry}"
    close = difflib.get_close_matches(id_, universe.registries.get(registry, set()), n=3)
    if close:
        message += f" (did you mean: {', '.join(close)}?)"
    return message
