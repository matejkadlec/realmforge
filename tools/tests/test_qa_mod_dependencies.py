"""The QA mod's Modrinth dependencies are the builds the modpack ships (RLM-83).

`qa-mod/build.gradle` names Player Animation Library, Better Combat, Cloth Config, Yeetus Experimentus, Placebo,
Apothic Attributes, Apothic Enchanting, Apotheosis, Enchantment Descriptions and Prickle (RLM-271) by Modrinth version
id (`maven.modrinth:<slug>:<id>`); each id must equal `version` under `[update.modrinth]` in `pack/mods/<slug>.pw.toml`,
or the dev runs would test our code against other jars than players get.
"""

from __future__ import annotations

import re
import tomllib
from pathlib import Path

from realmforge_tools.project import REPO_ROOT
from tests.conftest import write

COORDINATE = re.compile(r"""['"]maven\.modrinth:([a-z0-9_-]+):([^'"$]+)['"]""")
PACK_SLUGS = {
    "player-animation-library",
    "better-combat",
    "cloth-config",
    "yeetus-experimentus",
    "placebo",
    "apothic-attributes",
    "apothic-enchanting",
    "apotheosis",
    "enchantment-descriptions",
    "prickle",
}


def modrinth_coordinates(build_gradle: str) -> dict[str, str]:
    """`{slug: version}` for every `maven.modrinth:<slug>:<version>` literal in a Gradle script."""
    return {slug: version for slug, version in COORDINATE.findall(build_gradle)}


def pack_mismatches(coordinates: dict[str, str], mods_dir: Path) -> list[str]:
    problems = []
    for slug, version in sorted(coordinates.items()):
        pw_toml = mods_dir / f"{slug}.pw.toml"
        if not pw_toml.is_file():
            problems.append(f"{slug}: no {pw_toml.name} in the pack")
            continue
        pack_version = tomllib.loads(pw_toml.read_text(encoding="utf-8"))["update"]["modrinth"]["version"]
        if version != pack_version:
            problems.append(f"{slug}: qa-mod/build.gradle has {version}, {pw_toml.name} has {pack_version}")
    return problems


def test_qa_mod_uses_the_pack_builds() -> None:
    coordinates = modrinth_coordinates((REPO_ROOT / "qa-mod" / "build.gradle").read_text(encoding="utf-8"))
    assert set(coordinates) == PACK_SLUGS
    assert pack_mismatches(coordinates, REPO_ROOT / "pack" / "mods") == []


def test_coordinates_are_read_from_literals() -> None:
    script = """
def playerAnimationLib = 'maven.modrinth:player-animation-library:QmAdOGjK' // 1.2.8+26.1
runtimeOnly "maven.modrinth:better-combat:YPa9bdAg"
implementation "maven.modrinth:cloth-config:${clothConfigVersion}"
"""
    assert modrinth_coordinates(script) == {
        "player-animation-library": "QmAdOGjK",
        "better-combat": "YPa9bdAg",
    }


def test_a_different_build_is_reported(tmp_path: Path) -> None:
    write(
        tmp_path / "better-combat.pw.toml",
        '[update]\n[update.modrinth]\nmod-id = "5sy6g3kz"\nversion = "NEWER123"\n',
    )
    assert pack_mismatches({"better-combat": "YPa9bdAg"}, tmp_path) == [
        "better-combat: qa-mod/build.gradle has YPa9bdAg, better-combat.pw.toml has NEWER123"
    ]


def test_a_mod_missing_from_the_pack_is_reported(tmp_path: Path) -> None:
    assert pack_mismatches({"cloth-config": "TimoYzse"}, tmp_path) == [
        "cloth-config: no cloth-config.pw.toml in the pack"
    ]
