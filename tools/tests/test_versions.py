"""The release version and phase that the welcome message shows (RLM-520).

The mod's version (``mod_version`` in ``gradle.properties``) is the one the game shows, "Welcome to Realmforge v0.0.1
(Alpha)"; the pack's version (``pack/pack.toml``) names the same release, so the two change together, only on a release
(Matěj, 2026-10-29). The phase (``mod_phase``) is Alpha until the game is feature-complete, then Beta, then empty from
v1.0.0, and ``neoforge.mods.toml`` hands it to the mod as the property ``phase``.
"""

from __future__ import annotations

import tomllib

from realmforge_tools.project import REPO_ROOT

PHASES = {"Alpha", "Beta", ""}


def gradle_properties() -> dict[str, str]:
    properties: dict[str, str] = {}
    for line in (REPO_ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    return properties


def test_the_pack_and_the_mod_name_the_same_release() -> None:
    pack = tomllib.loads((REPO_ROOT / "pack" / "pack.toml").read_text(encoding="utf-8"))
    assert pack["version"] == gradle_properties()["mod_version"]


def test_the_phase_is_alpha_beta_or_none() -> None:
    phase = gradle_properties()["mod_phase"]
    assert phase in PHASES
    if gradle_properties()["mod_version"].split(".")[0] != "0":
        assert phase == "", "from v1.0.0 the welcome line names no phase"


def test_the_mod_metadata_hands_the_phase_to_the_mod() -> None:
    template = (
        REPO_ROOT / "mod" / "src" / "main" / "templates" / "META-INF" / "neoforge.mods.toml"
    ).read_text(encoding="utf-8")
    assert '[modproperties.realmforge]\nphase = "${mod_phase}"' in template
    assert "mod_phase              : mod_phase," in (REPO_ROOT / "mod" / "build.gradle").read_text(
        encoding="utf-8"
    )
