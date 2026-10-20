"""Blocks: ``content/blocks/<id>.yaml`` (architecture 6.2).

A block with its block item: hardness, blast resistance, the tool that mines it, its sound and map colour, and an
optional crafting recipe. It drops itself when mined with the right tool. Generates its registration, blockstate,
models, loot table, mining tags, lang entry, recipe and (for now) placeholder textures. A kind without an item (the realm
portal's portal block) has no block item, no loot table and no recipe.

Its kind says what it does: a simple full block, or a block whose behavior is game code in the feature that owns the
kind (architecture 6.2). The generated registration asks ``world/BlockKinds`` for a kind's block, which that feature
provides; the kind also decides the blockstate and model.
"""

from __future__ import annotations

from typing import Literal, get_args

from pydantic import Field, model_validator

from realmforge_tools.schema.common import ContentId, LocalizedText, StrictModel, UniqueResourceIds
from realmforge_tools.schema.recipe import ShapedRecipeDef

# The tool that mines a block at full speed; each is vanilla's block tag minecraft:mineable/<tool>.
Tool = Literal["pickaxe", "axe", "shovel", "hoe"]
# The lowest tool tier that gets the block's drops, beyond any tool of its kind; each is vanilla's block tag
# minecraft:needs_<tier>_tool.
ToolTier = Literal["stone", "iron", "diamond"]
# Vanilla's SoundType constants (net.minecraft.world.level.block.SoundType) a block may sound like.
Sound = Literal[
    "stone", "deepslate", "tuff", "calcite", "basalt", "dripstone_block", "polished_deepslate", "glass"
]
SOUNDS: tuple[str, ...] = get_args(Sound)
# Vanilla's MapColor constants (net.minecraft.world.level.material.MapColor) a block may show on maps.
MapColor = Literal[
    "stone",
    "deepslate",
    "color_gray",
    "color_light_gray",
    "color_black",
    "terracotta_white",
    "sand",
    "none",
    "quartz",
    "dirt",
]
MAP_COLORS: tuple[str, ...] = get_args(MapColor)
# What a block does. simple: a full block and nothing more. dungeon_portal: the dungeon portal (W4d, architecture 15.5),
# a full block that a dungeon key opens, lit while open (blockstate property open). dungeon_exit: the way out of a
# dungeon, a thin plate without collision that the builder puts where players arrive (blockstate without properties).
# The realm portal (W2b, docs/design/realm-portal.md) has three: realmforge_bricks, the frame's block; realmforge_sealstone, the
# frame's bottom middle, which a signet opens onto a realm (a block entity); both carry the blockstate property
# generated, which a trip sets on the frames it builds, and drop only without it. realm_portal: the portal block that
# fills a lit frame's opening (blockstate property axis), with no item, no loot and no collision.
BlockKind = Literal[
    "simple", "dungeon_portal", "dungeon_exit", "realmforge_bricks", "realmforge_sealstone", "realm_portal"
]
BLOCK_KINDS: tuple[str, ...] = get_args(BlockKind)
# Kinds the game uses one block of, so at most one block may have them.
SINGLE_BLOCK_KINDS = ("dungeon_exit", "realmforge_bricks", "realmforge_sealstone", "realm_portal")
# The realm portal's kinds: a portal needs all three, so either every one has its block or none does.
REALM_PORTAL_KINDS = ("realmforge_bricks", "realmforge_sealstone", "realm_portal")
# Kinds whose blocks have the blockstate property generated, and drop only without it (realm portal spec, "Building a
# portal": a frame a trip built drops nothing).
GENERATED_FLAG_KINDS = ("realmforge_bricks", "realmforge_sealstone")
# Kinds without a block item: no item, no loot table, no recipe and no creative tab.
ITEMLESS_KINDS = ("realm_portal",)
# The hardness of a block nothing can break, as vanilla's bedrock and portals.
UNBREAKABLE = -1.0


class BlockDef(StrictModel):
    """A block that drops itself, with its block item."""

    id: ContentId
    name: LocalizedText = Field(description="Display name.")
    kind: BlockKind = Field(
        default="simple",
        description=(
            "What it does: simple (a full block), dungeon_portal (a dungeon key opens it, W4d), dungeon_exit (the "
            "way out the builder puts where players arrive in a dungeon), or the realm portal's realmforge_bricks (its "
            "frame), realmforge_sealstone (the frame's bottom middle) and realm_portal (the portal block, without an item). "
            "One block at most of each kind but simple and dungeon_portal."
        ),
    )
    hardness: float = Field(
        ge=UNBREAKABLE,
        le=1000,
        allow_inf_nan=False,
        description="How long the block takes to mine: stone 1.5, deepslate 3, obsidian 50; -1: never, as bedrock.",
    )
    blast_resistance: float = Field(
        default=6.0,
        ge=0,
        le=3_600_000,
        allow_inf_nan=False,
        description="How well the block withstands explosions: stone 6, obsidian 1200.",
    )
    tool: Tool | None = Field(
        default=None,
        description="The tool that mines it at full speed (minecraft:mineable/<tool>); none: by hand.",
    )
    requires_tool: bool = Field(
        default=False,
        description="Drops nothing unless mined with the right tool, as stone needs a pickaxe. Needs a tool.",
    )
    tool_tier: ToolTier | None = Field(
        default=None,
        description="Optional: the lowest tier of that tool that gets the drops (minecraft:needs_<tier>_tool).",
    )
    sound: Sound = Field(default="stone", description="What breaking, placing and walking on it sound like.")
    map_color: MapColor = Field(default="stone", description="Its colour on maps.")
    creative_tabs: UniqueResourceIds = Field(
        default_factory=list,
        description="Creative mode tabs to list the block's item in, e.g. minecraft:natural_blocks. No duplicates.",
    )
    recipe: ShapedRecipeDef | None = Field(
        default=None, description="Optional: the shaped crafting recipe that makes the block's item."
    )

    @model_validator(mode="after")
    def _tool(self) -> BlockDef:
        if self.hardness < 0 and self.hardness != UNBREAKABLE:
            raise ValueError("hardness is 0 or more, or -1 for a block nothing breaks")
        if self.hardness == UNBREAKABLE and self.tool is not None:
            raise ValueError("a block nothing breaks needs no tool")
        if self.kind in ITEMLESS_KINDS and (self.creative_tabs or self.recipe is not None):
            raise ValueError(f"a {self.kind} block has no item, so no creative_tabs and no recipe")
        if self.tool is None and (self.requires_tool or self.tool_tier is not None):
            raise ValueError("requires_tool and tool_tier need a tool")
        if self.tool_tier is not None and not self.requires_tool:
            raise ValueError("a tool_tier only matters when requires_tool is true")
        return self
