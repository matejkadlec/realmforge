"""Pydantic models for every content type in ``content/``.

Each content type maps a directory under ``content/`` to a model. Models are strict: unknown keys and
wrong types are errors. JSON Schemas for editor completion are generated from them into ``tools/schemas/``.
"""

from dataclasses import dataclass

from realmforge_tools.schema.curve import CurveDef
from realmforge_tools.schema.item import ItemDef
from realmforge_tools.schema.text import TextGroupDef

# content/<directory> -> model. One YAML file per entry; the file name (without .yaml) must equal the id.
CONTENT_TYPES: dict[str, type] = {
    "items": ItemDef,
    "curves": CurveDef,
    "texts": TextGroupDef,
}

# content/worldgen/ holds vanilla worldgen JSON instead of YAML; it is validated and copied (content.py, validate.py).
WORLDGEN_DIR = "worldgen"


@dataclass(frozen=True)
class DatapackType:
    """A content type that becomes entries of our datapack registry ``realmforge:<registry>`` (architecture 5.2)."""

    directory: str
    registry: str


# Content types generated into data/realmforge/realmforge/<registry>/<id>.json by generate/datapack.py.
DATAPACK_TYPES: tuple[DatapackType, ...] = (DatapackType("curves", "curve"),)

__all__ = [
    "CONTENT_TYPES",
    "DATAPACK_TYPES",
    "WORLDGEN_DIR",
    "CurveDef",
    "DatapackType",
    "ItemDef",
    "TextGroupDef",
]
