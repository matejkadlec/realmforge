"""Pydantic models for every content type in ``content/``.

Each content type maps a directory under ``content/`` to a model. Models are strict: unknown keys and
wrong types are errors. JSON Schemas for editor completion are generated from them into ``tools/schemas/``.
"""

from realmforge_tools.schema.item import ItemDef

# content/<directory> -> model. One YAML file per entry; the file name (without .yaml) must equal the id.
CONTENT_TYPES: dict[str, type] = {
    "items": ItemDef,
}

__all__ = ["CONTENT_TYPES", "ItemDef"]
