"""Items: ``content/items/<id>.yaml``."""

from __future__ import annotations

from pydantic import Field

from realmforge_tools.schema.common import ContentId, LocalizedText, ResourceId, StrictModel


class ItemDef(StrictModel):
    """A simple item. Generates its registration, model, lang entries and (for now) a placeholder texture."""

    id: ContentId
    name: LocalizedText = Field(description="Display name.")
    max_stack_size: int = Field(default=64, ge=1, le=99, description="Maximum stack size (1-99).")
    creative_tabs: list[ResourceId] = Field(
        default_factory=list,
        description="Creative mode tabs to list the item in, e.g. minecraft:ingredients.",
    )
