"""Generated rooms (A4d, architecture 15.4): room specs in ``content/room_specs/<id>.yaml`` and the themes they are
built with in ``content/room_themes/<id>.yaml``.

A spec describes a room (its kind, footprint, height, connector sides and features); the room generator
(``rooms/generator.py``) turns it and a seed into a structure template. Each seed in ``seeds`` is a room the gallery
review kept, so it ships as ``realmforge:rooms/generated/<spec>/seed_<n>``; ``rejected_seeds`` records the ones it
rejected, so the next candidates are new ones (``rooms/candidates.py``).

A theme names the block of every neutral role the generator builds with (``floor``, ``wall_body``, ``stairs``,
...). Each role has its own block, so a theme swap (W4, architecture 15.2) can tell the roles apart in a built room
and give each the block another theme names for it.
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import AfterValidator, Field, model_validator

from realmforge_tools.schema.common import ContentId, ResourceId, StrictModel, UniqueResourceIds

SIDES = ("north", "east", "south", "west")
_OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}

# kind -> the room's role tag (schema/dungeon.py ROOM_ROLES).
KIND_ROLES = {
    "entrance": "entrance",
    "corridor": "hallway",
    "curve": "hallway",
    "t_junction": "hallway",
    "crossing": "hallway",
    "dead_end": "hallway",
    "combat": "combat",
    "treasure": "treasure",
    "arena": "boss",
}
Kind = Literal[
    "entrance", "corridor", "curve", "t_junction", "crossing", "dead_end", "combat", "treasure", "arena"
]
Side = Literal["north", "east", "south", "west"]
Feature = Literal["pillars", "alcoves", "pit", "balcony", "rubble"]

# The smallest and largest footprint per axis: a wall, a doorway three blocks wide and a wall; at most 16 x 16
# (DU-3's room kits).
MIN_FOOTPRINT = 5
MAX_FOOTPRINT = 16
# The template's height, its floor and ceiling included: a floor, three blocks of headroom, a ceiling and a row for
# the wall's cornice above the doorways at the least.
MIN_HEIGHT = 5
MAX_HEIGHT = 16
# What a feature needs (rooms/generator.py builds it to these measures).
PIT_MIN_FOOTPRINT = 9
PIT_FLOOR = 2  # a room with a pit has a floor two blocks thick: the pit is one block deep
BALCONY_MIN_HEIGHT = 9  # a floor, three blocks below the deck, the deck, three blocks above it, the ceiling
PILLARS_MIN_FOOTPRINT = 7
ALCOVES_MIN_FOOTPRINT = 7


def _unique[T](values: list[T]) -> list[T]:
    duplicates = sorted({str(value) for value in values if values.count(value) > 1})
    if duplicates:
        raise ValueError(f"duplicate entries: {', '.join(duplicates)}")
    return values


Seed = Annotated[int, Field(ge=1, le=2**31 - 1)]
Seeds = Annotated[list[Seed], AfterValidator(_unique), Field(json_schema_extra={"uniqueItems": True})]


class RoomSpecDef(StrictModel):
    """A room for the room generator. Every kept seed becomes a room that dungeons can use like an imported one."""

    id: ContentId
    kind: Kind = Field(
        description="entrance; the hallways corridor (two opposite connectors), curve (two adjacent), t_junction "
        "(three), crossing (four) and dead_end (one); combat, treasure and arena (the boss's room)."
    )
    theme: ResourceId = Field(
        description="The theme it is built with: realmforge:<id> of content/room_themes/."
    )
    footprint: list[Annotated[int, Field(ge=MIN_FOOTPRINT, le=MAX_FOOTPRINT)]] = Field(
        min_length=2,
        max_length=2,
        description=f"[x, z]: width (west to east) and depth (north to south), walls included, "
        f"{MIN_FOOTPRINT} to {MAX_FOOTPRINT} blocks.",
    )
    height: int = Field(
        ge=MIN_HEIGHT,
        le=MAX_HEIGHT,
        description=f"Blocks from the bottom of the floor to the top of the ceiling, {MIN_HEIGHT} to {MAX_HEIGHT}.",
    )
    connectors: Annotated[list[Side], AfterValidator(_unique)] = Field(
        min_length=1,
        description="The sides with a doorway (three blocks wide and high, in the middle of the side), each with a "
        "connector named realmforge:door, like the imported rooms'.",
    )
    features: Annotated[list[Feature], AfterValidator(_unique)] = Field(
        default_factory=list,
        description="pillars, alcoves (niches in the walls without a doorway), pit (a sunken middle one block "
        "deep), balcony (a deck along a wall without a doorway, reached by stairs), rubble.",
    )
    pools: UniqueResourceIds = Field(
        default_factory=list, description="The room pools its rooms join (e.g. realmforge:stone_vault)."
    )
    weight: int = Field(default=1, ge=1, le=150, description="Weight of each of its rooms in their pools.")
    seeds: Seeds = Field(
        default_factory=list,
        description="The seeds the gallery review kept, each a room: realmforge:rooms/generated/<id>/seed_<n>. "
        "rf rooms review writes them.",
    )
    rejected_seeds: Seeds = Field(
        default_factory=list,
        description="The seeds the gallery review rejected, so rf rooms candidates shows new ones.",
    )

    @property
    def role(self) -> str:
        return KIND_ROLES[self.kind]

    @property
    def floor(self) -> int:
        """How thick the floor is: the y of the level players walk on."""
        return PIT_FLOOR if "pit" in self.features else 1

    def free_sides(self) -> list[str]:
        """The sides without a connector, in ``SIDES`` order."""
        return [side for side in SIDES if side not in self.connectors]

    @model_validator(mode="after")
    def _fits(self) -> RoomSpecDef:
        sides = self.connectors
        expected = {"corridor": 2, "curve": 2, "t_junction": 3, "crossing": 4, "dead_end": 1}.get(self.kind)
        if expected is not None and len(sides) != expected:
            raise ValueError(f"a {self.kind} has {expected} connector(s), not {len(sides)}")
        if self.kind == "corridor" and _OPPOSITE[sides[0]] != sides[1]:
            raise ValueError("a corridor's two connectors are on opposite sides (a curve's are adjacent)")
        if self.kind == "curve" and _OPPOSITE[sides[0]] == sides[1]:
            raise ValueError("a curve's two connectors are on adjacent sides (a corridor's are opposite)")
        if overlap := sorted(set(self.seeds) & set(self.rejected_seeds)):
            raise ValueError(f"seeds both kept and rejected: {', '.join(map(str, overlap))}")
        width, depth = self.footprint
        features = self.features
        min_height = MIN_HEIGHT + (self.floor - 1)
        if "pit" in features and min(width, depth) < PIT_MIN_FOOTPRINT:
            raise ValueError(f"a pit needs a footprint of at least {PIT_MIN_FOOTPRINT} x {PIT_MIN_FOOTPRINT}")
        if "balcony" in features:
            min_height = max(min_height, BALCONY_MIN_HEIGHT + (self.floor - 1))
            if not self.free_sides():
                raise ValueError("a balcony stands along a side without a connector, and this room has none")
        if "alcoves" in features:
            if not self.free_sides():
                raise ValueError("alcoves are niches in a side without a connector, and this room has none")
            if min(width, depth) < ALCOVES_MIN_FOOTPRINT:
                raise ValueError(f"alcoves need a footprint of at least {ALCOVES_MIN_FOOTPRINT} on both axes")
        if "pillars" in features and min(width, depth) < PILLARS_MIN_FOOTPRINT:
            raise ValueError(f"pillars need a footprint of at least {PILLARS_MIN_FOOTPRINT} on both axes")
        if self.height < min_height:
            raise ValueError(f"this room needs a height of at least {min_height} for its floor and features")
        return self


# The neutral roles a theme names a block for, with what each block must be (rooms/generator.py builds with them).
FULL = "full"  # a full solid block
STAIRS = "stairs"
SLAB = "slab"
WALL_TORCH = "wall_torch"
LANTERN = "lantern"
THEME_ROLES: dict[str, str] = {
    "floor": FULL,
    "floor_variant": FULL,
    "floor_border": FULL,
    "wall_base": FULL,
    "wall_body": FULL,
    "wall_body_cracked": FULL,
    "wall_body_mossy": FULL,
    "wall_cornice": FULL,
    "ceiling": FULL,
    "pillar": FULL,
    "stairs": STAIRS,
    "slab": SLAB,
    "rubble": FULL,
    "rubble_slab": SLAB,
    "wall_light": WALL_TORCH,
    "ceiling_light": LANTERN,
}


class ThemeBlocks(StrictModel):
    """The block of each role. Each role has a block of its own."""

    floor: ResourceId = Field(description="The floor (a full block).")
    floor_variant: ResourceId = Field(description="Patches and patterns in the floor (a full block).")
    floor_border: ResourceId = Field(
        description="The floor along the walls and in the doorways (a full block)."
    )
    wall_base: ResourceId = Field(description="The bottom row of the walls and the pillars (a full block).")
    wall_body: ResourceId = Field(description="The walls between base and cornice (a full block).")
    wall_body_cracked: ResourceId = Field(description="Cracked patches in the wall body (a full block).")
    wall_body_mossy: ResourceId = Field(description="Mossy patches in the wall body (a full block).")
    wall_cornice: ResourceId = Field(
        description="The top row of the walls, under the ceiling (a full block)."
    )
    ceiling: ResourceId = Field(description="The ceiling (a full block).")
    pillar: ResourceId = Field(description="The shafts of pillars (a full block).")
    stairs: ResourceId = Field(description="Profiles: mouldings, corbels, steps (a stairs block).")
    slab: ResourceId = Field(description="Profiles: ledges and the balcony's deck (a slab).")
    rubble: ResourceId = Field(description="Rubble piles (a full block).")
    rubble_slab: ResourceId = Field(description="Low rubble (a slab).")
    wall_light: ResourceId = Field(
        description="Lights on the walls (a wall torch, e.g. minecraft:wall_torch)."
    )
    ceiling_light: ResourceId = Field(description="Lights hanging from the ceiling (a lantern).")


class RoomThemeDef(StrictModel):
    """A theme: the block of every role the room generator builds with. W4 turns themes into processor lists that
    swap one theme's blocks for another's (architecture 15.2)."""

    id: ContentId
    blocks: ThemeBlocks

    def block(self, role: str) -> str:
        return getattr(self.blocks, role)

    @model_validator(mode="after")
    def _distinct(self) -> RoomThemeDef:
        by_block: dict[str, list[str]] = {}
        for role in THEME_ROLES:
            by_block.setdefault(self.block(role), []).append(role)
        shared = [f"{block} ({', '.join(roles)})" for block, roles in by_block.items() if len(roles) > 1]
        if shared:
            raise ValueError(
                f"each role needs a block of its own, so a theme swap can tell them apart: {'; '.join(shared)}"
            )
        return self
