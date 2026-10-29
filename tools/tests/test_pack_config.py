"""The pack's config overrides that our content depends on (``pack/config/``, pack README "Configuration").

Traveler's Titles shows a dimension's name when a player enters it, and "???" for a dimension it has no name for. Our
realms show their own arrival title (W2b), so every realm's dimension, and the dungeon instances' (no name of its
own), is on its dimension blacklist (RLM-308): a realm never shows two titles. A new realm fails this test until its
dimension joins the list. A dungeon run shows its own arrival title too (RLM-409), named after its dungeon type's
biome, so every dungeon type's biome is on the biome blacklist, and a new dungeon type fails until its biome joins.
"""

from __future__ import annotations

import json
import tomllib

from ruamel.yaml import YAML

from realmforge_tools.generate.instance_biomes import biome_id
from realmforge_tools.project import REPO_ROOT

TRAVELERS_TITLES = REPO_ROOT / "pack" / "config" / "travelerstitles-neoforge-26_1.toml"
REALMS_DIR = REPO_ROOT / "content" / "realms"
DUNGEONS_DIR = REPO_ROOT / "content" / "dungeons"
MOD_DIMENSIONS_DIR = REPO_ROOT / "mod" / "src" / "main" / "resources" / "data" / "realmforge" / "dimension"


def blacklisted_dimensions() -> set[str]:
    """Traveler's Titles' dimension blacklist: a string that holds a list, ``"[a, b]"``."""
    return _blacklist("Dimension Titles", "Blacklisted Dimensions")


def blacklisted_biomes() -> set[str]:
    """Traveler's Titles' biome blacklist, written like the dimension blacklist."""
    return _blacklist("Biome Titles", "Blacklisted Biomes")


def _blacklist(section: str, key: str) -> set[str]:
    config = tomllib.loads(TRAVELERS_TITLES.read_text(encoding="utf-8"))
    listed = config["Traveler's Titles"][section][key]
    return {
        entry.strip()
        for entry in listed.strip().removeprefix("[").removesuffix("]").split(",")
        if entry.strip()
    }


def realm_dimensions() -> set[str]:
    """The dimensions of our realms (Overworld's entry is no realm and shows no title of ours)."""
    yaml = YAML(typ="safe")
    dimensions = {
        yaml.load(path.read_text(encoding="utf-8"))["dimension"] for path in REALMS_DIR.glob("*.yaml")
    }
    return dimensions - {"minecraft:overworld"}


def test_the_blacklist_is_read() -> None:
    assert blacklisted_dimensions() == {"realmforge:stone", "realmforge:instances"}


def test_every_realm_shows_only_its_own_title() -> None:
    missing = realm_dimensions() - blacklisted_dimensions()
    assert not missing, f"add these realm dimensions to Traveler's Titles' blacklist: {sorted(missing)}"


def test_the_dungeon_instances_show_no_title() -> None:
    instances = {f"realmforge:{path.stem}" for path in MOD_DIMENSIONS_DIR.glob("*.json")}
    assert instances == {"realmforge:instances"}, (
        "a dimension of the core mod's own data needs a decision here"
    )
    assert instances <= blacklisted_dimensions()


def test_a_dungeon_run_shows_only_its_own_title() -> None:
    yaml = YAML(typ="safe")
    biomes = {
        f"realmforge:{biome_id(yaml.load(path.read_text(encoding='utf-8'))['id'])}"
        for path in DUNGEONS_DIR.glob("*.yaml")
    }
    assert biomes, "no dungeon type found"
    missing = biomes - blacklisted_biomes()
    assert not missing, f"add these dungeon biomes to Traveler's Titles' biome blacklist: {sorted(missing)}"


def test_structurify_switches_strongholds_off() -> None:
    config = json.loads((REPO_ROOT / "pack" / "config" / "structurify.json").read_text(encoding="utf-8"))
    disabled = {entry["name"] for entry in config["structures"] if entry.get("is_disabled")}
    assert disabled == {"minecraft:stronghold"}


def test_pick_up_notifier_lists_pickups_from_the_middle_of_the_right_edge() -> None:
    """The core mod moves the list's top to mid-screen only while its corner is the top right (RLM-308)."""
    config = tomllib.loads(
        (REPO_ROOT / "pack" / "config" / "pickupnotifier-client.toml").read_text(encoding="utf-8")
    )
    display = config["display"]
    assert display["screen_corner"] == "TOP_RIGHT", "client/compat/pickupnotifier expects the top right"
    assert display["display_scale"] == 6, "150 % of the mod's default size, 4"
    assert display["max_height"] <= 0.5, "a list that starts mid-screen must end above the bottom edge"


def test_the_target_dummy_shows_only_our_combat_text() -> None:
    """MmmMmmMmmMmm's own floating numbers stay off: our combat text shows every hit (RLM-393, Matěj's playtest)."""
    config = tomllib.loads(
        (REPO_ROOT / "pack" / "config" / "dummmmmmy-client.toml").read_text(encoding="utf-8")
    )
    assert config["damage_numbers"]["enabled"] is False


def test_no_monster_spawns_wearing_a_backpack() -> None:
    """Sophisticated Backpacks' monsters with backpacks stay off (Matěj, 2026-10-29, RLM-494): about 1 in 100 by
    default, each with a flat health bonus, potion buffs, armor and a backpack of chest loot."""
    config = tomllib.loads(
        (REPO_ROOT / "pack" / "config" / "sophisticatedbackpacks-server.toml").read_text(encoding="utf-8")
    )
    assert config["entityBackpackAdditions"]["chance"] == 0.0


def default_keys() -> dict[str, str]:
    """Default Options' ``key_<key mapping>:<key>[:<modifier>]`` lines, as mapping to ``<key>[:<modifier>]``."""
    lines = (REPO_ROOT / "pack" / "config" / "defaultoptions" / "keybindings.txt").read_text(encoding="utf-8")
    keys = {}
    for line in lines.splitlines():
        if line.startswith("key_"):
            mapping, key = line.removeprefix("key_").split(":", 1)
            keys[mapping] = key
    return keys


def test_the_backpack_keys_are_on_b_and_semicolon() -> None:
    """Open Backpack on B, alone, and Sort Storage/Backpack on ; beside Open Quests and Inventory Sorting's Sort
    (Matěj, 2026-10-29, RLM-501, ADR 0062); every other Sophisticated key stays unbound."""
    keys = default_keys()
    assert [mapping for mapping, key in keys.items() if key == "key.keyboard.b"] == [
        "key.sophisticatedbackpacks.open_backpack"
    ]
    assert {mapping for mapping, key in keys.items() if key == "key.keyboard.grave.accent"} == {
        "key.ftbquests.keys.quests",
        "inventorysorter.key.sort",
        "key.sophisticatedcore.sort",
    }
    other_sophisticated = {
        key
        for mapping, key in keys.items()
        if mapping.startswith("key.sophisticated")
        and mapping not in {"key.sophisticatedbackpacks.open_backpack", "key.sophisticatedcore.sort"}
    }
    assert other_sophisticated == {"key.keyboard.unknown"}


def test_apotheosis_keys_leave_ctrl_free_for_sprinting() -> None:
    """Holding Ctrl to sprint must not turn T or K into Apotheosis's Ctrl bindings (ADR 0046, RLM-347, ADR 0062)."""
    keys = default_keys()
    assert keys["key.apotheosis.open_world_tier_select"] == "key.keyboard.t:ALT"
    assert keys["key.apotheosis.toggle_attribute_bonuses"] == "key.keyboard.k:ALT"
