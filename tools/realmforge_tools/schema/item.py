"""Items: ``content/items/<id>.yaml``."""

from __future__ import annotations

from typing import Literal, get_args

from pydantic import Field

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.common import ContentId, LocalizedText, StrictModel, UniqueResourceIds
from realmforge_tools.schema.recipe import ShapedRecipeDef
from realmforge_tools.schema.spell_school import HexColor

# What right-clicking an item can do (architecture 13). Each use is game code in the feature it belongs to, which finds
# its items through the item tag realmforge:use/<use> that the pipeline writes; each has its tooltip line, the text
# <USE_TEXT_GROUP>.realmforge.<use> in content/texts/.
ItemUse = Literal["unlearn_stat_points"]
ITEM_USES: tuple[str, ...] = get_args(ItemUse)
USE_TEXT_GROUP = "item_use"


def use_tag(use: str) -> str:
    """The item tag of the items with ``use``: ``realmforge:use/<use>``."""
    return f"{MOD_ID}:use/{use}"


class ItemDef(StrictModel):
    """A simple item. Generates its registration, model, lang entries and (for now) a placeholder texture; a use and a
    flavor line become its tooltip, and a recipe its crafting recipe."""

    id: ContentId
    name: LocalizedText = Field(description="Display name.")
    max_stack_size: int = Field(default=64, ge=1, le=99, description="Maximum stack size (1-99).")
    fire_resistant: bool = Field(
        default=False, description="Fire and lava do not destroy the dropped item, as netherite's."
    )
    creative_tabs: UniqueResourceIds = Field(
        default_factory=list,
        description="Creative mode tabs to list the item in, e.g. minecraft:ingredients. No duplicates.",
    )
    use: ItemUse | None = Field(
        default=None,
        description=(
            "What right-clicking the item does; one item is used up each time. unlearn_stat_points: every spent "
            "stat point is unspent again (RLM-196)."
        ),
    )
    flavor: LocalizedText | None = Field(
        default=None,
        description="A flavor line in the tooltip: one sentence, no mechanics, at most 12 words (lore style guide).",
    )
    recipe: ShapedRecipeDef | None = Field(
        default=None, description="Optional: the shaped crafting recipe that makes the item."
    )
    color: HexColor | None = Field(
        default=None,
        description='Main colour of its placeholder texture, "#RRGGBB"; by default a colour from a hash of its id.',
    )
