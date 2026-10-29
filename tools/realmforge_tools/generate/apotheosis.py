"""Apotheosis data: every Apotheosis affix, gem, tier augment and rarity that ``content/`` changes (an entry's own
change in ``content/apotheosis_<kind>/``, the stat remaps of ``content/apotheosis_stat_remaps/``) ->
``data/<namespace>/apotheosis/<kind>/<path>.json``, which replaces Apotheosis's own file at that path; every recipe
``content/recipe_removals/`` names -> ``data/<namespace>/recipe/<path>.json``, a file NeoForge skips
(``realmforge_tools/apotheosis.py``, architecture 19.4)."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.apotheosis import generated_files, load_snapshot
from realmforge_tools.content import Content
from realmforge_tools.project import RepoPaths


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    data = paths.mod_generated / "resources" / "data"
    return {data / path: file for path, file in generated_files(content, load_snapshot(paths)).items()}
