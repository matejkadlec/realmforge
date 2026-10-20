"""Realms: ``content/realms/<id>.yaml``, the datapack registry ``realmforge:realm`` (architecture 5.2, 15.1; the
realm portal spec, docs/design/realm-portal.md).

A realm is a dimension of its own, sealed until the player unlocks it: its unlock is the advancement
``realmforge:realm/<id>``, whose only criterion is ``minecraft:impossible``, so only the realm's unlock quest (Q2)
or a command grants it. The realm's dimension rules (``content/dimensions/``) name that advancement in their access
requirements. Its portal fields (W2b) say how the realm portal looks and sounds when it opens onto the realm, which
signet opens it, and where a trip may build a portal in the realm.

Overworld has an entry too (``dimension: minecraft:overworld``), but it is no realm: it is always open, so it has no
unlock, signet, hint or flavour line. Its colour is the return portals', and its arrival is where a way back from a realm
builds a portal (realm portal spec, "Building a portal").
"""

from __future__ import annotations

from typing import Annotated

from pydantic import Field, StringConstraints, model_validator

from realmforge_tools.schema.common import ContentId, LocalizedText, ResourceId, StrictModel

OVERWORLD = "minecraft:overworld"
# Vanilla particle types without options (SimpleParticleType in net.minecraft.core.particles.ParticleTypes, checked
# in the 26.1.2 sources) that suit a portal: a realm's particle is drawn next to the dust in its colour, and an entry
# carries no option data for types such as minecraft:dust or minecraft:block.
PORTAL_PARTICLES: tuple[str, ...] = (
    "minecraft:ash",
    "minecraft:white_ash",
    "minecraft:snowflake",
    "minecraft:small_flame",
    "minecraft:flame",
    "minecraft:soul_fire_flame",
    "minecraft:portal",
    "minecraft:reverse_portal",
    "minecraft:enchant",
    "minecraft:end_rod",
    "minecraft:crimson_spore",
    "minecraft:warped_spore",
    "minecraft:spore_blossom_air",
    "minecraft:glow",
    "minecraft:electric_spark",
    "minecraft:white_smoke",
    "minecraft:cloud",
)
# A colour as six hex digits after a #: #7A8FB0.
HexColour = Annotated[str, StringConstraints(pattern=r"^#[0-9A-Fa-f]{6}$")]
# The frame a trip builds: the bottom row and five more above it (the opening's four, then the top row).
FRAME_HEIGHT = 6


def unlock_advancement(realm_id: str) -> str:
    """The advancement that unlocks a realm: ``realm/<id>`` in our namespace (realm portal spec)."""
    return f"realm/{realm_id}"


class ArrivalDef(StrictModel):
    """Where a trip may build a portal in the dimension when none is near (realm portal spec, "Building a portal")."""

    y: list[int] = Field(
        min_length=2,
        max_length=2,
        description=(
            "[lowest, highest]: the heights a built frame's bottom row may stand at, which keep arrivals in the "
            "depth band of the realm's lowest levels; Overworld [64, 310], above sea level."
        ),
    )
    floor: ResourceId = Field(
        description=(
            "The block under and in front of a frame carved out where no spot fits, e.g. minecraft:stone_bricks. It "
            "drops as usual."
        )
    )
    pocket_radius: int = Field(
        default=3,
        ge=1,
        le=8,
        description="Blocks of safe space around a carved frame, free of fluids and falling blocks (3 by default).",
    )

    @model_validator(mode="after")
    def _range(self) -> ArrivalDef:
        if self.y[0] > self.y[1]:
            raise ValueError("y is [lowest, highest]: the first is not above the second")
        return self


class RealmDef(StrictModel):
    """A realm: its dimension, what players read about it, the icon of its unlock, and its portal."""

    id: ContentId
    dimension: ResourceId = Field(
        description=(
            "The realm's dimension, e.g. realmforge:stone (content/worldgen/); minecraft:overworld for Overworld's "
            "entry, which is no realm."
        )
    )
    name: LocalizedText = Field(description="The realm's name, without an article: Undervault (RLM-188).")
    subtitle: LocalizedText | None = Field(
        default=None,
        description=(
            "Its element, shown with the name where a player first meets it: Stone Realm (RLM-139). Every realm has "
            "one; Overworld none."
        ),
    )
    description: LocalizedText | None = Field(
        default=None,
        description=(
            "The realm as a plain description in running text, lowercase and without an article: stone realm. The "
            "refusal at the sealstone reads 'a portal to the stone realm' (Matěj, 2026-10-20). Every realm has one; "
            "Overworld none."
        ),
    )
    locked_hint: LocalizedText | None = Field(
        default=None,
        description=(
            "How to unlock it, shown when a player without the unlock is refused: one sentence naming the quest, "
            "'Unlocked by completing The Ominous Chief quest.' Every realm has one; Overworld none."
        ),
    )
    levels: list[int] | None = Field(
        default=None,
        min_length=2,
        max_length=2,
        description=(
            "[lowest, highest]: the realm's level band, which the arrival title shows (RLM-139); the lowest is the "
            "entry level its dimension rules floor mobs at, the highest at most their max_level. Every realm has one; "
            "Overworld none."
        ),
    )
    flavour: LocalizedText | None = Field(
        default=None,
        description=(
            "A flavour line, shown on the first arrival and with the unlock: one sentence, no mechanics. Every realm "
            "has one; Overworld none."
        ),
    )
    icon: ResourceId | None = Field(
        default=None,
        description=(
            "The item the unlock advancement shows, e.g. one of the realm's blocks. Every realm has one; Overworld "
            "none."
        ),
    )
    signet: ResourceId | None = Field(
        default=None,
        description=(
            "The item from content/items/ that opens the realm portal onto the realm, stacking to 1: "
            "realmforge:undervault_signet. Every realm has its own; Overworld none."
        ),
    )
    colour: HexColour = Field(
        description="The portal's colour while it opens onto the realm, e.g. #7A8FB0; Overworld's: return portals."
    )
    particle: ResourceId | None = Field(
        default=None,
        description=(
            "The vanilla particle the portal shows next to the dust in its colour, one without options: "
            "minecraft:ash. Every realm has one; Overworld none."
        ),
    )
    sound_pitch: float = Field(
        default=1.0,
        ge=0.5,
        le=2.0,
        allow_inf_nan=False,
        description="The pitch of the vanilla portal sounds for this realm until our own sounds exist (A3): 0.5-2.",
    )
    arrival: ArrivalDef = Field(description="Where a trip may build a portal in the dimension.")

    @property
    def is_overworld(self) -> bool:
        return self.dimension == OVERWORLD

    @model_validator(mode="after")
    def _realm_fields(self) -> RealmDef:
        realm_only = {
            "subtitle": self.subtitle,
            "description": self.description,
            "locked_hint": self.locked_hint,
            "levels": self.levels,
            "flavour": self.flavour,
            "icon": self.icon,
            "signet": self.signet,
            "particle": self.particle,
        }
        if self.is_overworld:
            present = [name for name, value in realm_only.items() if value is not None]
            if present:
                raise ValueError(f"Overworld is always open and no realm, so it has no {', '.join(present)}")
        else:
            missing = [name for name, value in realm_only.items() if value is None]
            if missing:
                raise ValueError(f"a realm needs {', '.join(missing)}")
            if self.levels is not None and not 1 <= self.levels[0] <= self.levels[1]:
                raise ValueError("levels is [lowest, highest], from 1: the first is not above the second")
        return self
