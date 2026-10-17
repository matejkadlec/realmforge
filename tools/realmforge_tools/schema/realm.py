"""Realms: ``content/realms/<id>.yaml``, the datapack registry ``realmforge:realm`` (architecture 5.2, 15.1; the
realm portal spec, docs/design/realm-portal.md).

A realm is a dimension of its own, sealed until the player unlocks it: its unlock is the advancement
``realmforge:realm/<id>``, whose only criterion is ``minecraft:impossible``, so only the realm's unlock quest (Q2)
or a command grants it. The realm's dimension rules (``content/dimensions/``) name that advancement in their access
requirements. W2b adds the portal's fields (sigil, colour, particle, sound, arrival).
"""

from __future__ import annotations

from pydantic import Field

from realmforge_tools.schema.common import ContentId, LocalizedText, ResourceId, StrictModel


def unlock_advancement(realm_id: str) -> str:
    """The advancement that unlocks a realm: ``realm/<id>`` in our namespace (realm portal spec)."""
    return f"realm/{realm_id}"


class RealmDef(StrictModel):
    """A realm: its dimension, what players read about it, and the icon of its unlock."""

    id: ContentId
    dimension: ResourceId = Field(
        description="The realm's dimension, e.g. realmforge:stone (content/worldgen/)."
    )
    name: LocalizedText = Field(description="The realm's name, without an article: Undervault (RLM-188).")
    subtitle: LocalizedText = Field(
        description="Its element, shown with the name where a player first meets it: Stone Realm (RLM-139)."
    )
    locked_hint: LocalizedText = Field(
        description="How to unlock it, shown when a player without the unlock is refused: one sentence."
    )
    flavour: LocalizedText = Field(
        description="A flavour line, shown on the first arrival and with the unlock: one sentence, no mechanics."
    )
    icon: ResourceId = Field(
        description="The item the unlock advancement shows, e.g. one of the realm's blocks."
    )
