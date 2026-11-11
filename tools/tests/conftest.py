from __future__ import annotations

import re
import shutil
from pathlib import Path

import pytest

from realmforge_tools.project import REPO_ROOT, RepoPaths


@pytest.fixture
def repo(tmp_path: Path) -> RepoPaths:
    """A scratch checkout containing a copy of content/ and the vanilla ID universe. It leaves out content/rooms/,
    megabytes of imported templates no test reads, content/room_specs/, whose kept seeds the generator would build
    into rooms, content/dungeons/, whose pools need those rooms, and content/set_pieces/, templates too: a test that
    needs rooms, room specs, dungeon types or set pieces adds its own (the room themes stay). The spell models'
    sources come along (art/models/), which the content's models of cubes are read from."""
    shutil.copytree(REPO_ROOT / "content", tmp_path / "content", ignore=_rooms_and_dungeons)
    shutil.copytree(REPO_ROOT / "tools/id_universe", tmp_path / "tools/id_universe")
    shutil.copytree(REPO_ROOT / "art/models", tmp_path / "art/models")
    return RepoPaths(tmp_path)


def _rooms_and_dungeons(folder: str, names: list[str]) -> set[str]:
    return (
        {"rooms", "room_specs", "dungeons", "set_pieces"} if Path(folder) == REPO_ROOT / "content" else set()
    )


def write(path: Path, text: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path


def drop_class_kits(content: Path) -> None:
    """Takes the class kits out of ``content`` (C4, C5): every class's ``specializations`` and every race's
    ``racial_spell``, for a test that deletes the spells they name."""
    for file in (content / "classes").glob("*.yaml"):
        file.write_text(
            re.sub(r"^specializations:\n(?:  .*\n)*", "", file.read_text(encoding="utf-8"), flags=re.M),
            "utf-8",
        )
    for file in (content / "races").glob("*.yaml"):
        file.write_text(
            re.sub(r"^racial_spell: .*\n", "", file.read_text(encoding="utf-8"), flags=re.M), "utf-8"
        )
