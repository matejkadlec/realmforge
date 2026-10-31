"""Apotheosis data: every Apotheosis affix, gem, tier augment and rarity that ``content/`` changes (an entry's own
change in ``content/apotheosis_<kind>/``, the stat remaps of ``content/apotheosis_stat_remaps/``) and every rarity
override of ``content/apotheosis_rarity_overrides/`` -> ``data/<namespace>/apotheosis/<kind>/<path>.json``, which
replaces Apotheosis's own file at that path; every recipe ``content/recipe_removals/`` names ->
``data/<namespace>/recipe/<path>.json``, a file NeoForge skips; and, with them, Apotheosis's MIT ``LICENSE`` and our
``NOTICE`` -> ``META-INF/third_party/apotheosis/`` (``realmforge_tools/apotheosis.py``, architecture 19.4, RLM-524)."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.apotheosis import generated_files, load_snapshot, notices
from realmforge_tools.content import Content
from realmforge_tools.project import RepoPaths


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    snapshot = load_snapshot(paths)
    resources = paths.mod_generated / "resources"
    files = {resources / "data" / path: file for path, file in generated_files(content, snapshot).items()}
    if files:
        third_party = resources / "META-INF" / "third_party" / "apotheosis"
        files.update({third_party / name: text for name, text in notices(snapshot).items()})
    return files
