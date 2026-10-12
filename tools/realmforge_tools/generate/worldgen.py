"""Worldgen: ``content/worldgen/<directory>/<id>.json`` copied unchanged to ``data/realmforge/<registry path>/``."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.worldgen import data_path


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    return {
        data_dir(paths) / data_path(file.directory) / f"{file.id}.json": file.raw for file in content.worldgen
    }
