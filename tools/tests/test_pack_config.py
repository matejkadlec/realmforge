"""The pack's config overrides that our content depends on (``pack/config/``, pack README "Configuration").

Traveler's Titles shows a dimension's name when a player enters it, and "???" for a dimension it has no name for. Our
realms show their own arrival title (W2b), so every realm's dimension, and the dungeon instances' (no name of its
own), is on its dimension blacklist (RLM-308): a realm never shows two titles. A new realm fails this test until its
dimension joins the list.
"""

from __future__ import annotations

import json
import tomllib

from ruamel.yaml import YAML

from realmforge_tools.project import REPO_ROOT

TRAVELERS_TITLES = REPO_ROOT / "pack" / "config" / "travelerstitles-neoforge-26_1.toml"
REALMS_DIR = REPO_ROOT / "content" / "realms"
MOD_DIMENSIONS_DIR = REPO_ROOT / "mod" / "src" / "main" / "resources" / "data" / "realmforge" / "dimension"


def blacklisted_dimensions() -> set[str]:
    """Traveler's Titles' dimension blacklist: a string that holds a list, ``"[a, b]"``."""
    config = tomllib.loads(TRAVELERS_TITLES.read_text(encoding="utf-8"))
    listed = config["Traveler's Titles"]["Dimension Titles"]["Blacklisted Dimensions"]
    return {entry.strip() for entry in listed.strip().removeprefix("[").removesuffix("]").split(",") if entry.strip()}


def realm_dimensions() -> set[str]:
    """The dimensions of our realms (Overworld's entry is no realm and shows no title of ours)."""
    yaml = YAML(typ="safe")
    dimensions = {yaml.load(path.read_text(encoding="utf-8"))["dimension"] for path in REALMS_DIR.glob("*.yaml")}
    return dimensions - {"minecraft:overworld"}


def test_the_blacklist_is_read() -> None:
    assert blacklisted_dimensions() == {"realmforge:stone", "realmforge:instances"}


def test_every_realm_shows_only_its_own_title() -> None:
    missing = realm_dimensions() - blacklisted_dimensions()
    assert not missing, f"add these realm dimensions to Traveler's Titles' blacklist: {sorted(missing)}"


def test_the_dungeon_instances_show_no_title() -> None:
    instances = {f"realmforge:{path.stem}" for path in MOD_DIMENSIONS_DIR.glob("*.json")}
    assert instances == {"realmforge:instances"}, "a dimension of the core mod's own data needs a decision here"
    assert instances <= blacklisted_dimensions()


def test_structurify_switches_strongholds_off() -> None:
    config = json.loads((REPO_ROOT / "pack" / "config" / "structurify.json").read_text(encoding="utf-8"))
    disabled = {entry["name"] for entry in config["structures"] if entry.get("is_disabled")}
    assert disabled == {"minecraft:stronghold"}
