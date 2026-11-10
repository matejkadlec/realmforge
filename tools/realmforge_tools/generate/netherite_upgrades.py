"""Netherite upgrades (RLM-566): each ``content/netherite_upgrades/<id>.yaml`` -> the smithing recipe that puts it on a
piece, ``data/realmforge/recipe/netherite_upgrade/<id>.json`` of our recipe type ``realmforge:netherite_upgrade``
(``item/NetheriteUpgradeRecipe``): its template, any piece of ``#realmforge:netherite_upgradable`` and its item make
the same piece carrying the upgrade. The registry entry itself is ``generate/datapack.py``'s, and the item tags
``generate/items.py``'s."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.project import MOD_ID, RepoPaths
from realmforge_tools.schema.netherite_upgrade import (
    NETHERITE_UPGRADABLE_TAG,
    NETHERITE_UPGRADE_RECIPE_TYPE,
    NetheriteUpgradeDef,
)


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    folder = data_dir(paths) / "recipe" / "netherite_upgrade"
    return {
        folder / f"{entry.model.id}.json": dump_json(recipe_data(entry.model))
        for entry in content.netherite_upgrades
    }


def recipe_data(upgrade: NetheriteUpgradeDef) -> dict[str, Any]:
    """The smithing recipe of ``upgrade``, in the fields of vanilla's smithing recipes plus the upgrade it puts on."""
    return {
        "type": NETHERITE_UPGRADE_RECIPE_TYPE,
        "addition": upgrade.item,
        "base": f"#{NETHERITE_UPGRADABLE_TAG}",
        "template": upgrade.template,
        "upgrade": f"{MOD_ID}:{upgrade.id}",
    }
