"""Pydantic models for every content type in ``content/``.

Each content type maps a directory under ``content/`` to a model. Models are strict: unknown keys and
wrong types are errors. JSON Schemas for editor completion are generated from them into ``tools/schemas/``.
"""

from dataclasses import dataclass

from realmforge_tools.schema.curve import CurveDef
from realmforge_tools.schema.item import ItemDef
from realmforge_tools.schema.spell_school import SpellSchoolDef
from realmforge_tools.schema.stat import StatDef
from realmforge_tools.schema.stat_rule import StatRuleDef
from realmforge_tools.schema.text import TextGroupDef

# content/<directory> -> model. One YAML file per entry; the file name (without .yaml) must equal the id.
CONTENT_TYPES: dict[str, type] = {
    "items": ItemDef,
    "curves": CurveDef,
    "stats": StatDef,
    "stat_rules": StatRuleDef,
    "spell_schools": SpellSchoolDef,
    "texts": TextGroupDef,
}

# content/worldgen/ holds vanilla worldgen JSON instead of YAML; it is validated and copied (content.py, validate.py).
WORLDGEN_DIR = "worldgen"


@dataclass(frozen=True)
class DatapackType:
    """A content type that becomes entries of our datapack registry ``realmforge:<registry>`` (architecture 5.2)."""

    directory: str
    registry: str


# Content types generated into data/realmforge/realmforge/<registry>/<id>.json by generate/datapack.py. Spell schools
# are a datapack registry too (realmforge:spell_school), but generate/spell_schools.py writes them with their damage
# types (architecture 6.2).
DATAPACK_TYPES: tuple[DatapackType, ...] = (
    DatapackType("curves", "curve"),
    DatapackType("stat_rules", "stat_rule"),
)

__all__ = [
    "CONTENT_TYPES",
    "DATAPACK_TYPES",
    "WORLDGEN_DIR",
    "CurveDef",
    "DatapackType",
    "ItemDef",
    "SpellSchoolDef",
    "StatDef",
    "StatRuleDef",
    "TextGroupDef",
]
