"""Apotheosis data: ``content/apotheosis_affixes/<id>.yaml`` -> ``data/apotheosis/apotheosis/affixes/<path>.json``,
which replaces Apotheosis's own file at that path (``schema/apotheosis_affix.py``, architecture 19.4)."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.apotheosis_affix import RARITIES, ApotheosisAffixDef


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    data = paths.mod_generated / "resources" / "data"
    return {
        data / entry.model.data_path: dump_json(affix_data(entry.model))
        for entry in content.apotheosis_affixes
    }


def affix_data(affix: ApotheosisAffixDef) -> dict[str, Any]:
    """The affix as Apotheosis writes its own (``data/apotheosis/apotheosis/affixes/generic/attribute/lucky.json``),
    with the rarities in Apotheosis's order."""
    return {
        "type": affix.type,
        "attribute": affix.attribute,
        "categories": list(affix.categories),
        "definition": {
            "affix_type": affix.affix_type,
            "exclusive_set": list(affix.exclusive_set),
            "weights": {"quality": affix.quality, "weight": affix.weight},
        },
        "operation": affix.operation,
        "values": {
            rarity: affix.values[rarity].model_dump() for rarity in RARITIES if rarity in affix.values
        },
    }
