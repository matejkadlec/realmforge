"""Apotheosis data: every Apotheosis affix, gem, tier augment and rarity that ``content/`` changes (an entry's own
change in ``content/apotheosis_<kind>/``, the stat remaps of ``content/apotheosis_stat_remaps/``) and every rarity
override of ``content/apotheosis_rarity_overrides/`` -> ``data/<namespace>/apotheosis/<kind>/<path>.json``, which
replaces Apotheosis's own file at that path; every affix of ours in ``content/apotheosis_new_affixes/`` ->
``data/realmforge/apotheosis/affixes/<id>.json``, with its name and suffix in our lang; every gem's extra bonuses in
``content/apotheosis_gem_bonuses/`` -> ``data/realmforge/apotheosis/extra_gem_bonuses/<id>.json``, with the names of
their gem classes of one category of ours in our lang (several categories: ``content/texts/gem_class.yaml``); every
recipe
``content/recipe_removals/`` names ->
``data/<namespace>/recipe/<path>.json``, a file NeoForge skips; and, with them, Apotheosis's MIT ``LICENSE`` and our
``NOTICE`` -> ``META-INF/third_party/apotheosis/`` (``realmforge_tools/apotheosis.py``, architecture 19.4, RLM-524)."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.apotheosis import gem_class_name, generated_files, load_snapshot, new_affix_id, notices
from realmforge_tools.content import Content
from realmforge_tools.project import MOD_ID, RepoPaths
from realmforge_tools.schema.apotheosis import LOOT_CATEGORY_TEXTS
from realmforge_tools.schema.common import LocalizedText


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    snapshot = load_snapshot(paths)
    resources = paths.mod_generated / "resources"
    files = {resources / "data" / path: file for path, file in generated_files(content, snapshot).items()}
    if files:
        third_party = resources / "META-INF" / "third_party" / "apotheosis"
        files.update({third_party / name: text for name, text in notices(snapshot).items()})
    return files


def translations(content: Content) -> dict[str, LocalizedText]:
    """Apotheosis names an affix by ``affix.<id>`` before an item's name and ``affix.<id>.suffix`` after it
    (``Affix#getName``), and a gem bonus's gem class by ``gem_class.<key>`` (``Gem#addInformation``): an extra bonus for
    one loot category of ours is keyed by the category's path, so it is named by the category's plural
    (``content/texts/loot_category.yaml``, ``GEM_CLASS_TEXTS``)."""
    texts: dict[str, LocalizedText] = {}
    for entry in content.apotheosis_new_affixes:
        key = f"affix.{new_affix_id(entry.model)}"
        texts[key] = entry.model.name
        texts[f"{key}.suffix"] = entry.model.suffix
    plurals = {
        name.removesuffix(".plural"): text
        for group in content.texts
        if group.model.id == LOOT_CATEGORY_TEXTS
        for name, text in group.model.texts.items()
        if name.endswith(".plural")
    }
    for entry in content.apotheosis_gem_bonuses:
        for bonus in entry.model.bonuses:
            if isinstance(bonus.gem_class, str) and bonus.gem_class.startswith(f"{MOD_ID}:"):
                plural = plurals.get(bonus.class_key)
                if plural is not None:
                    texts[gem_class_name(bonus)] = plural
    return texts
