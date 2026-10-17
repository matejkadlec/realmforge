from __future__ import annotations

import shutil
from pathlib import Path

import pytest

from realmforge_tools.project import REPO_ROOT, RepoPaths


@pytest.fixture
def repo(tmp_path: Path) -> RepoPaths:
    """A scratch checkout containing a copy of content/ and the vanilla ID universe. It leaves out content/rooms/,
    megabytes of imported templates no test reads, content/room_specs/, whose kept seeds the generator would build
    into rooms, and content/dungeons/, whose pools need those rooms: a test that needs rooms, room specs or dungeon
    types adds its own (the room themes stay)."""
    shutil.copytree(REPO_ROOT / "content", tmp_path / "content", ignore=_rooms_and_dungeons)
    shutil.copytree(REPO_ROOT / "tools/id_universe", tmp_path / "tools/id_universe")
    return RepoPaths(tmp_path)


def _rooms_and_dungeons(folder: str, names: list[str]) -> set[str]:
    return {"rooms", "room_specs", "dungeons"} if Path(folder) == REPO_ROOT / "content" else set()


def write(path: Path, text: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path
