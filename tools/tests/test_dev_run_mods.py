"""The dev runs load the pack's mods, read from ``pack/mods/*.pw.toml`` (RLM-279, ADR 0042).

``qa-mod/build.gradle`` reads every ``.pw.toml`` with a small line reader of its own (Gradle has no TOML parser):
``[section]`` headers and ``key = value`` lines. A ``side = "client"`` mod joins the client, ``"server"`` the dev
server and the GameTest server, ``"both"`` every run; a Modrinth mod comes from Modrinth's Maven by project and
version id, a CurseForge one from CurseMaven by project and file id. These tests fail when a ``.pw.toml`` has
something that reader cannot take, or when ``qa-mod/build.gradle`` names a mod by hand instead of from the pack, so
the dev runs' mod list never differs from ``pack/mods/``.
"""

from __future__ import annotations

import re
import tomllib
from pathlib import Path

import pytest

from realmforge_tools.project import REPO_ROOT
from tests.conftest import write

MODS_DIR = REPO_ROOT / "pack" / "mods"
BUILD_GRADLE = REPO_ROOT / "qa-mod" / "build.gradle"

# The lines qa-mod/build.gradle's readPackwizToml understands; anything else could be read wrong there.
SIMPLE_LINE = re.compile(r'^\s*$|^\[[A-Za-z0-9_.-]+\]$|^[A-Za-z0-9_-]+ = ("[^"\\]*"|[0-9]+|true|false)$')
HASH_FORMATS = {"sha1", "sha256", "sha512"}


def dev_run_coordinate(pw_toml: Path) -> str:
    """The Maven coordinate qa-mod/build.gradle builds for a pack mod; ValueError where it would stop the build."""
    text = pw_toml.read_text(encoding="utf-8")
    odd = [line for line in text.splitlines() if not SIMPLE_LINE.match(line)]
    if odd:
        raise ValueError(f"{pw_toml.name}: lines qa-mod/build.gradle cannot read: {odd}")
    data = tomllib.loads(text)
    slug = pw_toml.name.removesuffix(".pw.toml")
    if data.get("side") not in ("client", "server", "both"):
        raise ValueError(f"{pw_toml.name}: side is {data.get('side')!r}, not client, server or both")
    download = data.get("download", {})
    if download.get("hash-format") not in HASH_FORMATS or not download.get("hash"):
        raise ValueError(f"{pw_toml.name}: no sha1, sha256 or sha512 hash under [download]")
    update = data.get("update", {})
    modrinth, curseforge = update.get("modrinth", {}), update.get("curseforge", {})
    if modrinth.get("mod-id") and modrinth.get("version"):
        return f"maven.modrinth:{modrinth['mod-id']}:{modrinth['version']}"
    if curseforge.get("project-id") and curseforge.get("file-id"):
        return f"curse.maven:{slug}-{curseforge['project-id']}:{curseforge['file-id']}"
    raise ValueError(f"{pw_toml.name}: neither [update.modrinth] nor [update.curseforge]")


def dev_run_mods(mods_dir: Path) -> dict[str, set[str]]:
    """``{"client": slugs, "server": slugs}``: the pack mods each kind of dev run loads."""
    runs: dict[str, set[str]] = {"client": set(), "server": set()}
    for pw_toml in sorted(mods_dir.glob("*.pw.toml")):
        dev_run_coordinate(pw_toml)
        side = tomllib.loads(pw_toml.read_text(encoding="utf-8"))["side"]
        slug = pw_toml.name.removesuffix(".pw.toml")
        if side != "server":
            runs["client"].add(slug)
        if side != "client":
            runs["server"].add(slug)
    return runs


def test_every_pack_mod_is_one_the_dev_runs_can_load() -> None:
    problems = []
    for pw_toml in sorted(MODS_DIR.glob("*.pw.toml")):
        try:
            dev_run_coordinate(pw_toml)
        except ValueError as error:
            problems.append(str(error))
    assert problems == []


def test_the_dev_client_gets_every_client_and_shared_mod() -> None:
    runs = dev_run_mods(MODS_DIR)
    pack = {p.name.removesuffix(".pw.toml") for p in MODS_DIR.glob("*.pw.toml")}
    sides = {
        p.name.removesuffix(".pw.toml"): tomllib.loads(p.read_text("utf-8"))["side"]
        for p in MODS_DIR.glob("*.pw.toml")
    }
    assert runs["client"] == {slug for slug in pack if sides[slug] != "server"}
    assert runs["server"] == {slug for slug in pack if sides[slug] != "client"}
    assert {"jei", "jade", "better-combat", "ftb-quests-forge"} <= runs["client"]
    assert "sodium" not in runs["server"]


def test_build_gradle_reads_every_mod_from_the_pack() -> None:
    script = BUILD_GRADLE.read_text(encoding="utf-8")
    assert "rootProject.file('pack/mods')" in script
    # A coordinate written by hand would be a second list that can drift from the pack.
    # The reader's own templates ("maven.modrinth:${...}") are fine.
    assert re.findall(r"""['"](?:maven\.modrinth|curse\.maven):[^$'"]+""", script) == []
    # The mods it names: packMod('<slug>') and the lean client's lists (leanClientMods = [...] and += [...]).
    named = set(re.findall(r"packMod\('([a-z0-9-]+)'\)", script))
    for listed in re.findall(r"leanClientMods \+?= \[([^\]]*)\]", script):
        named |= set(re.findall(r"'([a-z0-9-]+)'", listed))
    assert {"player-animation-library", "yeetus-experimentus", "better-combat"} <= named
    missing = sorted(slug for slug in named if not (MODS_DIR / f"{slug}.pw.toml").is_file())
    assert missing == []


MODRINTH = """\
name = "JEI"
filename = "jei.jar"
side = "both"

[download]
url = "https://cdn.modrinth.com/data/u6dRKJwZ/versions/AisPQmQz/jei.jar"
hash-format = "sha512"
hash = "abc"

[update]
[update.modrinth]
mod-id = "u6dRKJwZ"
version = "AisPQmQz"
"""

CURSEFORGE = """\
name = "FTB Quests (NeoForge)"
filename = "ftb-quests-neoforge-26.1.2.9.jar"
side = "both"

[download]
hash-format = "sha1"
hash = "b85b"
mode = "metadata:curseforge"

[update]
[update.curseforge]
file-id = 9088796
project-id = 289412
"""


def test_coordinates_for_modrinth_and_curseforge(tmp_path: Path) -> None:
    assert dev_run_coordinate(write(tmp_path / "jei.pw.toml", MODRINTH)) == "maven.modrinth:u6dRKJwZ:AisPQmQz"
    assert (
        dev_run_coordinate(write(tmp_path / "ftb-quests-forge.pw.toml", CURSEFORGE))
        == "curse.maven:ftb-quests-forge-289412:9088796"
    )


@pytest.mark.parametrize(
    ("text", "message"),
    [
        (MODRINTH.replace('side = "both"', 'side = "neither"'), "side is 'neither'"),
        (MODRINTH.replace('hash-format = "sha512"', 'hash-format = "md5"'), "no sha1, sha256 or sha512 hash"),
        (MODRINTH.split("[update]")[0], "neither [update.modrinth] nor [update.curseforge]"),
        (MODRINTH.replace('name = "JEI"', "name = 'JEI'"), "lines qa-mod/build.gradle cannot read"),
    ],
)
def test_a_pack_mod_the_dev_runs_cannot_load_is_reported(tmp_path: Path, text: str, message: str) -> None:
    with pytest.raises(ValueError, match=re.escape(message)):
        dev_run_coordinate(write(tmp_path / "jei.pw.toml", text))


def test_a_server_only_mod_stays_off_the_client(tmp_path: Path) -> None:
    write(tmp_path / "jei.pw.toml", MODRINTH)
    write(tmp_path / "spark.pw.toml", MODRINTH.replace('side = "both"', 'side = "server"'))
    write(tmp_path / "sodium.pw.toml", MODRINTH.replace('side = "both"', 'side = "client"'))
    assert dev_run_mods(tmp_path) == {"client": {"jei", "sodium"}, "server": {"jei", "spark"}}
