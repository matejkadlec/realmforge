from __future__ import annotations

import shutil
from pathlib import Path

import pytest

from realmforge_tools.project import REPO_ROOT, RepoPaths


@pytest.fixture
def repo(tmp_path: Path) -> RepoPaths:
    """A scratch checkout containing a copy of content/ and the vanilla ID universe."""
    for rel in ("content", "tools/id_universe"):
        shutil.copytree(REPO_ROOT / rel, tmp_path / rel)
    return RepoPaths(tmp_path)


def write(path: Path, text: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path
