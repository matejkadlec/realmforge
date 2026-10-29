"""Generators: validated content -> every derived file.

``generate_all`` computes all outputs in memory; ``sync`` makes the generated directories match them exactly
(writes new/changed files, deletes stale ones). The generated directories are owned by this package: never edit
files in them by hand, change ``content/`` or the generators instead.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.generate import (
    apotheosis,
    blocks,
    bosses,
    characters,
    curves,
    datapack,
    dimension_rules,
    dungeons,
    effects,
    fonts,
    gametest,
    instance_biomes,
    items,
    lang,
    leveling,
    mobs,
    quests,
    realms,
    recipes,
    rooms,
    schemas,
    set_pieces,
    sounds,
    spell_schools,
    spells,
    stats,
    texts,
    worldgen,
)
from realmforge_tools.project import RepoPaths


def generate_all(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    generators = (
        items.generate,
        blocks.generate,
        recipes.generate,
        datapack.generate,
        curves.generate,
        dimension_rules.generate,
        characters.generate,
        leveling.generate,
        realms.generate,
        quests.generate,
        apotheosis.generate,
        stats.generate,
        effects.generate,
        spell_schools.generate,
        spells.generate,
        mobs.generate,
        bosses.generate,
        sounds.generate,
        texts.generate,
        fonts.generate,
        worldgen.generate,
        rooms.generate,
        set_pieces.generate,
        dungeons.generate,
        instance_biomes.generate,
        lang.generate,
        gametest.generate,
        schemas.generate,
    )
    for generator in generators:
        for path, data in generator(content, paths).items():
            if path in files:
                raise RuntimeError(f"two generators produced {paths.rel(path)}")
            files[path] = data
    roots = paths.generated_roots()
    for path in files:
        if not any(path.is_relative_to(root) for root in roots):
            raise RuntimeError(f"generator output {paths.rel(path)} is outside the generated directories")
    return files


@dataclass
class SyncResult:
    added: list[str] = field(default_factory=list)
    changed: list[str] = field(default_factory=list)
    removed: list[str] = field(default_factory=list)

    @property
    def clean(self) -> bool:
        return not (self.added or self.changed or self.removed)

    def lines(self) -> list[str]:
        return (
            [f"A {p}" for p in self.added]
            + [f"M {p}" for p in self.changed]
            + [f"D {p}" for p in self.removed]
        )


def sync(files: dict[Path, bytes], paths: RepoPaths, *, check: bool = False) -> SyncResult:
    """Make the generated directories match ``files``. With ``check=True`` only report the differences."""
    result = SyncResult()
    existing = {
        p for root in paths.generated_roots() if root.exists() for p in root.rglob("*") if p.is_file()
    }

    for path in sorted(files):
        data = files[path]
        if path not in existing:
            result.added.append(paths.rel(path))
        elif path.read_bytes() != data:
            result.changed.append(paths.rel(path))
        else:
            continue
        if not check:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)

    for path in sorted(existing - files.keys()):
        result.removed.append(paths.rel(path))
        if not check:
            path.unlink()

    if not check:
        _remove_empty_dirs(paths)
    return result


def _remove_empty_dirs(paths: RepoPaths) -> None:
    for root in paths.generated_roots():
        if not root.exists():
            continue
        for directory in sorted(
            (d for d in root.rglob("*") if d.is_dir()), key=lambda d: len(d.parts), reverse=True
        ):
            if not any(directory.iterdir()):
                directory.rmdir()
