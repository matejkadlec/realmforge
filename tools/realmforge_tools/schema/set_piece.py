"""Set pieces: ``content/set_pieces/<id>.nbt`` (a structure template) with its fit rule ``<id>.yaml``
([design](../../../docs/design/set-pieces.md), [ADR 0050](../../../docs/decisions/0050-set-pieces-hand-built-nbt-placed-at-dungeon-generation.md)).

A set piece is small hand-built dressing (a niche with hay bales, a barrel stack) that the dungeon fits into rooms:
``rf gen`` finds each room's spots for it (``rooms/set_pieces.py``) and writes them into the dungeon type entries,
and the dungeon builder fills them. Set pieces are the third exception to YAML-only content, after worldgen JSON and
rooms: the template is the piece, and the fit rule says where it fits. The jar holds the template as the structure
``realmforge:set_pieces/<id>``.
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import AfterValidator, Field, StringConstraints, field_validator, model_validator

from realmforge_tools.rooms.set_pieces import (
    DEFAULT_ROLES,
    MAX_HEIGHT,
    WALL,
    WALL_GROUP,
    Fit,
    Mask,
    MaskError,
)
from realmforge_tools.schema.common import ContentId, ResourceId, StrictModel
from realmforge_tools.schema.dungeon import ROOM_ROLES
from realmforge_tools.schema.room import Tag
from realmforge_tools.schema.room_spec import THEME_ROLES

SET_PIECES_DIR = "set_pieces"

RoomRole = Literal["entrance", "hallway", "combat", "stairs", "treasure", "boss"]
assert set(RoomRole.__args__) == set(ROOM_ROLES)  # type: ignore[attr-defined]

# A chance strictly between 0 and 1: 1 would keep a block always and 0 leave it out, which needs no rule.
Chance = Annotated[float, Field(gt=0, lt=1, allow_inf_nan=False)]
BlockStateText = Annotated[
    str,
    StringConstraints(
        pattern=r"^[a-z0-9_.-]+:[a-z0-9_./-]+(\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*\])?$"
    ),
    Field(description="A block state as commands write it: minecraft:chest[facing=south]."),
]


def _unique[T](values: list[T]) -> list[T]:
    duplicates = sorted({str(value) for value in values if values.count(value) > 1})
    if duplicates:
        raise ValueError(f"duplicate entries: {', '.join(duplicates)}")
    return values


class SwapDef(StrictModel):
    """What a block of the piece may become (a barrel a chest): a rule per state of the block in the template."""

    to: BlockStateText = Field(
        description="The block state it becomes, as the template has it (unturned): the game turns it with the piece."
    )
    chance: Chance = Field(default=0.5, description="How often it becomes that block (0.5: half the time).")
    keep: Annotated[
        list[Annotated[str, StringConstraints(pattern=r"^[a-z0-9_]+$")]], AfterValidator(_unique)
    ] = Field(
        default_factory=list,
        description="Properties the new block takes over from the block it replaces (facing, waterlogged); the "
        "new block must have them.",
    )


class VariantsDef(StrictModel):
    """How one build gives several pieces: mirroring, blocks that appear only by chance, and swaps."""

    mirror: bool = Field(
        default=False,
        description="True: the builder may mirror the piece (a pair of spots mirrored across the room gets the piece "
        "mirrored). Leave it false for a piece with writing or an asymmetric story.",
    )
    chance: dict[ResourceId, Chance] = Field(
        default_factory=dict,
        description="Blocks that are there only by chance (minecraft:skeleton_skull: 0.5, half the time).",
    )
    swaps: dict[ResourceId, SwapDef] = Field(
        default_factory=dict,
        description="Blocks that may become another (minecraft:barrel: {to: ..., chance: 0.3}).",
    )

    @model_validator(mode="after")
    def _apart(self) -> VariantsDef:
        both = sorted(set(self.chance) & set(self.swaps))
        if both:
            raise ValueError(f"a block is either left out by chance or swapped, not both: {', '.join(both)}")
        return self


class SetPieceDef(StrictModel):
    """Where a set piece fits and where it may appear. The template next to it holds the blocks, built as the mask
    shows them (the wall side to the north)."""

    id: ContentId
    mask: str = Field(
        description="Top view, one character per cell separated by single spaces, the first row the wall side: W "
        "wall, . the piece, _ clearance that stays walkable, ? anything."
    )
    height: int = Field(
        default=MAX_HEIGHT,
        ge=1,
        le=MAX_HEIGHT,
        description="The piece's height above the floor, its template's too.",
    )
    builds_walls: bool = Field(
        default=False,
        description="True: the piece brings its own W cells (its template holds them), so it needs free space in "
        "front of a flat wall there, not an existing niche.",
    )
    walls: Annotated[list[str], AfterValidator(_unique)] = Field(
        default_factory=lambda: [WALL_GROUP],
        min_length=1,
        description="What the W cells may be in a generated room: theme roles (wall: the wall's base, body, patches "
        "and cornice; pillar, ...) or block ids. An imported room's blocks have no roles: any full block there "
        "counts for a role.",
    )
    tags: Annotated[list[Tag], AfterValidator(_unique)] = Field(
        min_length=1,
        description="What the piece is (storage, prison, crypt, shrine, ...); dungeon types pick by tag.",
    )
    roles: Annotated[list[RoomRole], AfterValidator(_unique)] = Field(
        default_factory=lambda: list(DEFAULT_ROLES),
        min_length=1,
        description="The room roles it may appear in (default: hallway and combat; a treasure room, a boss's room "
        "and the entrance only take pieces that name them).",
    )
    weight: int = Field(
        default=1, ge=1, le=150, description="How often it is picked among the pieces that fit."
    )
    variants: VariantsDef = Field(default_factory=VariantsDef)
    source: Annotated[str, StringConstraints(pattern=r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")] | None = Field(
        default=None,
        description="The workshop jig's save it was imported from (realmforge_qa:set_piece/<mask>_<n>), written by "
        "rf set-pieces import, so --from-world skips it next time.",
    )

    @field_validator("mask")
    @classmethod
    def _mask(cls, value: str) -> str:
        try:
            Mask.parse(value)
        except MaskError as e:
            raise ValueError(str(e)) from e
        return value

    @field_validator("walls")
    @classmethod
    def _walls(cls, values: list[str]) -> list[str]:
        known = {WALL_GROUP, *THEME_ROLES}
        for value in values:
            if ":" not in value and value not in known:
                raise ValueError(
                    f"{value!r} is neither a theme role ({', '.join(sorted(known))}) nor a block id"
                )
        return values

    @model_validator(mode="after")
    def _walls_to_build(self) -> SetPieceDef:
        if self.builds_walls and WALL not in Mask.parse(self.mask).rows[0]:
            raise ValueError("builds_walls needs W cells in the first row: the wall side it stands against")
        return self

    def fit(self) -> Fit:
        return Fit(Mask.parse(self.mask), self.height, self.builds_walls, tuple(self.walls))
