"""JSON Schemas for editor completion in content YAML files (``tools/schemas/<type>.schema.json``).

Content files opt in with a first line like:
``# yaml-language-server: $schema=../../tools/schemas/items.schema.json``
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema import CONTENT_TYPES


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    for type_dir, model in CONTENT_TYPES.items():
        schema = {"$schema": "https://json-schema.org/draft/2020-12/schema", **model.model_json_schema()}
        files[paths.schemas / f"{type_dir}.schema.json"] = dump_json(schema, sort_keys=True)
    return files
