"""Pydantic models for every content type in ``content/``.

Each content type maps a directory under ``content/`` to a model. Models are strict: unknown keys and
wrong types are errors. JSON Schemas for editor completion are generated from them into ``tools/schemas/``.
"""

from dataclasses import dataclass

from realmforge_tools.schema.apotheosis_affix import ApotheosisAffixDef
from realmforge_tools.schema.block import BlockDef
from realmforge_tools.schema.chapter import ChapterDef
from realmforge_tools.schema.character import ClassDef, RaceDef
from realmforge_tools.schema.curve import CurveDef
from realmforge_tools.schema.dimension_rules import DimensionRulesDef
from realmforge_tools.schema.dungeon import DungeonTypeDef
from realmforge_tools.schema.effect import EffectDef
from realmforge_tools.schema.item import ItemDef
from realmforge_tools.schema.kill_xp_multiplier import KillXpMultiplierDef
from realmforge_tools.schema.realm import RealmDef
from realmforge_tools.schema.room_spec import RoomSpecDef, RoomThemeDef
from realmforge_tools.schema.spell import SpellDef
from realmforge_tools.schema.spell_school import SpellSchoolDef
from realmforge_tools.schema.stat import StatDef
from realmforge_tools.schema.stat_rule import StatRuleDef
from realmforge_tools.schema.text import TextGroupDef

# content/<directory> -> model. One YAML file per entry; the file name (without .yaml) must equal the id.
CONTENT_TYPES: dict[str, type] = {
    "items": ItemDef,
    "blocks": BlockDef,
    "curves": CurveDef,
    "stats": StatDef,
    "stat_rules": StatRuleDef,
    "spell_schools": SpellSchoolDef,
    "dimensions": DimensionRulesDef,
    "races": RaceDef,
    "classes": ClassDef,
    "texts": TextGroupDef,
    "dungeons": DungeonTypeDef,
    "chapters": ChapterDef,
    "kill_xp_multipliers": KillXpMultiplierDef,
    "effects": EffectDef,
    "spells": SpellDef,
    "room_themes": RoomThemeDef,
    "room_specs": RoomSpecDef,
    "realms": RealmDef,
    "apotheosis_affixes": ApotheosisAffixDef,
}

# content/worldgen/ holds vanilla worldgen JSON instead of YAML; it is validated and copied (content.py, validate.py).
WORLDGEN_DIR = "worldgen"


@dataclass(frozen=True)
class DatapackType:
    """A content type that becomes entries of our datapack registry ``realmforge:<registry>`` (architecture 5.2)."""

    directory: str
    registry: str


# Content types generated into data/realmforge/realmforge/<registry>/<id>.json by generate/datapack.py. Spell schools
# and dungeon types are datapack registries too (realmforge:spell_school, realmforge:dungeon_type), but their own
# generators write them: spell_schools.py with their damage types, dungeons.py with their pools' rooms resolved
# (architecture 6.2).
DATAPACK_TYPES: tuple[DatapackType, ...] = (
    DatapackType("curves", "curve"),
    DatapackType("stat_rules", "stat_rule"),
    DatapackType("dimensions", "dimension_rules"),
    DatapackType("races", "race"),
    DatapackType("classes", "class"),
    DatapackType("chapters", "chapter"),
    DatapackType("kill_xp_multipliers", "kill_xp_multiplier"),
    DatapackType("spells", "spell"),
    DatapackType("realms", "realm"),
)

__all__ = [
    "CONTENT_TYPES",
    "DATAPACK_TYPES",
    "WORLDGEN_DIR",
    "ApotheosisAffixDef",
    "BlockDef",
    "ChapterDef",
    "ClassDef",
    "CurveDef",
    "DatapackType",
    "DimensionRulesDef",
    "DungeonTypeDef",
    "EffectDef",
    "ItemDef",
    "KillXpMultiplierDef",
    "RaceDef",
    "RealmDef",
    "RoomSpecDef",
    "RoomThemeDef",
    "SpellDef",
    "SpellSchoolDef",
    "StatDef",
    "StatRuleDef",
    "TextGroupDef",
]
