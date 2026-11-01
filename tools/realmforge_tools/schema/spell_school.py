"""Spell schools: ``content/spell_schools/<id>.yaml``, the datapack registry ``realmforge:spell_school``
(architecture 5.2, 9.1)."""

from __future__ import annotations

import re
from typing import Annotated, Literal

from pydantic import Field, StringConstraints, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.common import ContentId, LocalizedText, ResourceId, StrictModel

HexColor = Annotated[
    str,
    StringConstraints(pattern=r"^#[0-9A-Fa-f]{6}$"),
    Field(description='An RGB colour, "#RRGGBB".'),
]

# The damage type tag of physical hits (RLM-233): what Physical Resistance lowers (content/stats/). Melee hits (a mob's,
# a player's, a bee's sting, a spear's or a mace's), every projectile, and the damage types of every physical school,
# which generate/spell_schools.py adds; vanilla's own damage types, so other mods' mobs count too. NeoForge's
# #neoforge:is_physical is broader (falls, cactus, suffocation, /damage's generic), so it is not used.
PHYSICAL_TAG = f"{MOD_ID}:is_physical"
PHYSICAL_DAMAGE_TYPES: tuple[str, ...] = (
    "minecraft:mob_attack",
    "minecraft:mob_attack_no_aggro",
    "minecraft:sting",
    "#minecraft:is_player_attack",
    "#minecraft:is_projectile",
)

# Every format sequence in a translated text: %s, %1$s, %d, %%, ...
_FORMAT = re.compile(r"%(?:\d+\$)?[a-zA-Z%]")


class SpellSchoolDef(StrictModel):
    """A spell school: the kind of damage a spell, an ability or a boss attack deals (architecture 9.1). Each school
    is a vanilla damage type ``realmforge:<id>``, in ``#minecraft:bypasses_cooldown`` so its hits are never
    swallowed by the hurt cooldown.

    ``physical`` damage meets armor (our formula) and can be dodged; ``magic`` damage bypasses armor, spell power
    raises it and the school's resistance lowers it. ``power`` and ``resistance`` name secondary stats (shares in
    ``content/stats/``) that raise the school's damage dealt and lower its damage taken."""

    id: ContentId
    name: LocalizedText = Field(
        description="The school's name, shown in tooltips and on the character sheet."
    )
    kind: Literal["physical", "magic"] = Field(
        description="physical: armor reduces it, it can be dodged; magic: bypasses armor, spell power raises it."
    )
    color: HexColor = Field(
        description='Colour of its spells: spell bar frames, the cast bar, damage numbers in tooltips, "#RRGGBB".'
    )
    combat_text_color: HexColor | None = Field(
        default=None,
        description='Colour of its floating combat text, "#RRGGBB"; the school\'s color when left out.',
    )
    power: ResourceId | None = Field(
        default=None,
        description="Secondary stat (a share) that raises this school's damage, e.g. realmforge:fire_power.",
    )
    resistance: ResourceId | None = Field(
        default=None,
        description="Secondary stat (a share) that lowers the damage taken from this school.",
    )
    death_message: LocalizedText = Field(
        description='Death message when this school kills: "%1$s burned to ashes". %1$s is the victim; the game '
        "passes the killer only sometimes, so no other placeholder is allowed."
    )

    @model_validator(mode="after")
    def _shape(self) -> SpellSchoolDef:
        if self.power is not None and self.power == self.resistance:
            raise ValueError("power and resistance must be different stats")
        for locale, text in self.death_message.translations().items():
            found = _FORMAT.findall(text)
            if "%1$s" not in found:
                raise ValueError(f"death_message.{locale} must name the victim with %1$s")
            others = sorted({sequence for sequence in found if sequence not in ("%1$s", "%%")})
            if others:
                raise ValueError(
                    f"death_message.{locale} may use only %1$s (and %%), not {', '.join(others)}"
                )
        return self

    @property
    def damage_type(self) -> str:
        """The school's vanilla damage type, ``realmforge:<id>``."""
        return f"{MOD_ID}:{self.id}"

    @property
    def message_id(self) -> str:
        """The damage type's ``message_id``: death messages are ``death.attack.<message_id>``."""
        return f"{MOD_ID}.{self.id}"

    @property
    def bypasses_armor(self) -> bool:
        return self.kind == "magic"


def death_message_keys(school: SpellSchoolDef) -> tuple[str, str, str]:
    """The translation keys vanilla's ``DamageSource#getLocalizedDeathMessage`` reads for the school's damage type:
    the base key (with or without a killer), the one for a killer who only got kill credit (``.player``) and the one
    for a killer holding a named item (``.item``). All three get the same text."""
    base = f"death.attack.{school.message_id}"
    return base, f"{base}.player", f"{base}.item"
