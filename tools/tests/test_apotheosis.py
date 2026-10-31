"""Apotheosis's data, fitted (content/apotheosis_*/, content/recipe_removals/): the snapshot, validation and the
generated files (RLM-271, RLM-272, ADR 0063)."""

from __future__ import annotations

import json
import tomllib
import zipfile
from pathlib import Path

import pytest
from pydantic import ValidationError

from realmforge_tools.apotheosis import DISABLED, build_snapshot, load_snapshot, pack_jar
from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import REPO_ROOT, RepoPaths
from realmforge_tools.schema.apotheosis import (
    ApotheosisAffixDef,
    ApotheosisRarityDef,
    ApotheosisRarityOverrideDef,
    ApotheosisStatRemapDef,
    RecipeRemovalDef,
)
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

DATA = "mod/src/generated/resources/data/"
AFFIXES = DATA + "apotheosis/apotheosis/affixes/"
GEMS = DATA + "apotheosis/apotheosis/gems/"
AUGMENTS = DATA + "apotheosis/apotheosis/tier_augments/"
SCHEMA = "# yaml-language-server: $schema=../../tools/schemas/{}.schema.json\n"


def generated(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def generated_json(files: dict[str, bytes], path: str) -> dict:
    return json.loads(files[path])


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def assert_problem(repo: RepoPaths, text: str) -> None:
    problems = problems_for(repo)
    assert any(text in problem for problem in problems), problems


@pytest.fixture(scope="module")
def files() -> dict[str, bytes]:
    return generated(RepoPaths())


# The snapshot


def test_the_snapshot_is_of_the_jar_the_pack_installs() -> None:
    """``pack/mods/apotheosis.pw.toml`` and the snapshot name the same jar: an Apotheosis update in the pack needs
    ``uv run rf ids apotheosis``."""
    repo = RepoPaths()
    snapshot = load_snapshot(repo)
    jar = pack_jar(repo)
    assert snapshot.sha512 == jar["sha512"], (
        "run `uv run rf ids apotheosis` after updating Apotheosis in the pack"
    )
    assert jar["file"].endswith(f"-{snapshot.version}.jar")
    assert len(snapshot.entries["affixes"]) == 99
    assert "apotheosis:potion_charm" in snapshot.recipes
    # The jar carries no license file: its text comes from the release's commit (RLM-524).
    assert snapshot.license_text.startswith("MIT License\n")
    assert "Copyright (c) 2018-2025 Stormraven Studios, LLC" in snapshot.license_text


def test_a_snapshot_holds_the_jar_s_entries_by_kind_and_its_recipe_ids(tmp_path: Path) -> None:
    jar = tmp_path / "apotheosis.jar"
    with zipfile.ZipFile(jar, "w") as archive:
        archive.writestr("META-INF/neoforge.mods.toml", '[[mods]]\nmodId = "apotheosis"\nversion = "9.9.9"\n')
        archive.writestr("data/apotheosis/apotheosis/affixes/melee/attribute/test.json", '{"type": "a"}')
        archive.writestr("data/apotheosis/apotheosis/gems/core/test.json", '{"bonuses": []}')
        archive.writestr("data/apotheosis/apotheosis/apothic_elites/test.json", "{}")
        archive.writestr(
            "data/apotheosis/apotheosis/rarity_override/apotheosis/shears.json", '{"category": "s"}'
        )
        archive.writestr("data/apotheosis/recipe/infusion/test.json", "{}")
        archive.writestr("assets/apotheosis/lang/en_us.json", "{}")
    snapshot = build_snapshot(jar, {"file": "apotheosis.jar", "url": "", "sha512": "abc"}, "MIT License\n")
    assert snapshot["version"] == "9.9.9"
    assert snapshot["jar"] == {"file": "apotheosis.jar", "sha512": "abc"}
    assert snapshot["affixes"] == {"apotheosis:melee/attribute/test": {"type": "a"}}
    assert snapshot["gems"] == {"apotheosis:core/test": {"bonuses": []}}
    assert snapshot["tier_augments"] == {} and snapshot["rarities"] == {}
    assert snapshot["rarity_override"] == {"apotheosis:apotheosis/shears": {"category": "s"}}
    assert snapshot["recipes"] == ["apotheosis:infusion/test"]
    assert snapshot["license"] == {
        "spdx": "MIT",
        "source": "https://raw.githubusercontent.com/Shadows-of-Fire/Apotheosis/f2e7686/LICENSE",
        "text": "MIT License\n",
    }


def test_the_pack_s_metafile_pins_a_sha512() -> None:
    meta = tomllib.loads((REPO_ROOT / "pack/mods/apotheosis.pw.toml").read_text(encoding="utf-8"))
    assert meta["download"]["hash-format"] == "sha512"


# What the repository's content generates


def test_lucky_replaces_apotheosis_s_affix_without_the_curios_category(files: dict[str, bytes]) -> None:
    lucky = generated_json(files, AFFIXES + "generic/attribute/lucky.json")
    # Apotheosis 9.1.0's generic/attribute/lucky.json, without apotheosis:charm.
    assert lucky == {
        "type": "apotheosis:attribute",
        "attribute": "minecraft:luck",
        "categories": [
            "apotheosis:bow",
            "apotheosis:breaker",
            "apotheosis:helmet",
            "apotheosis:chestplate",
            "apotheosis:leggings",
            "apotheosis:boots",
            "apotheosis:shield",
            "apotheosis:trident",
            "apotheosis:melee_weapon",
            "apotheosis:shears",
        ],
        "definition": {"affix_type": "stat", "exclusive_set": [], "weights": {"quality": 0.1, "weight": 25}},
        "operation": "add_value",
        "values": {
            "apotheosis:uncommon": {"min": 1.0, "max": 1.5, "step": 0.25},
            "apotheosis:rare": {"min": 2.0, "max": 3.0, "step": 0.25},
            "apotheosis:epic": {"min": 2.5, "max": 4.0, "step": 0.25},
            "apotheosis:mythic": {"min": 3.0, "max": 6.0, "step": 0.25},
        },
    }


@pytest.mark.parametrize(
    ("affix", "stat"),
    [
        ("melee/attribute/intricate", "realmforge:crit_chance"),
        ("melee/attribute/lacerating", "realmforge:crit_damage"),
        ("melee/attribute/piercing", "realmforge:armor_pierce"),
        ("weapon/attribute/shredding", "realmforge:armor_shred"),
        ("melee/attribute/vampiric", "realmforge:lifesteal"),
        ("melee/attribute/berserking", "realmforge:overheal"),
        ("armor/attribute/timekeeping", "realmforge:cooldown_reduction"),
    ],
)
def test_an_affix_grants_our_stat_with_apotheosis_s_values(
    files: dict[str, bytes], affix: str, stat: str
) -> None:
    original = load_snapshot(RepoPaths()).entry("affixes", f"apotheosis:{affix}")
    fitted = generated_json(files, AFFIXES + f"{affix}.json")
    assert fitted == {**original, "attribute": stat}


@pytest.mark.parametrize("affix", ["weapon/attribute/nullifying", "weapon/attribute/runebreaking"])
def test_protection_pierce_and_shred_affixes_are_off(files: dict[str, bytes], affix: str) -> None:
    assert generated_json(files, AFFIXES + f"{affix}.json") == DISABLED


def test_stats_without_an_equivalent_of_ours_stay_apothic_attributes(files: dict[str, bytes]) -> None:
    """Healing received (RLM-233), the ranged stats (RLM-235), vanilla XP (Matěj, 2026-10-29) and the rest keep their
    affixes untouched."""
    for affix in (
        "armor/attribute/spiritual",
        "ranged/attribute/agile",
        "ranged/attribute/elven",
        "breaker/attribute/experienced",
        "ranged/attribute/streamlined",
    ):
        assert AFFIXES + f"{affix}.json" not in files
    # Giant Slaying's values change (RLM-232), its stat does not.
    giant_slaying = generated_json(files, AFFIXES + "melee/attribute/giant_slaying.json")
    assert giant_slaying["attribute"] == "apothic_attributes:current_hp_damage"


def test_an_add_multiplied_base_crit_damage_bonus_adds_what_it_added_to_apothic_attributes(
    files: dict[str, bytes],
) -> None:
    """Warlord's crit damage was a share of Apothic Attributes' base 1.5: it becomes an add_value of 1.5 times it."""
    warlord = generated_json(files, GEMS + "core/warlord.json")
    crit = next(bonus for bonus in warlord["bonuses"] if bonus.get("attribute") == "realmforge:crit_damage")
    assert crit["operation"] == "add_value"
    assert crit["values"] == {
        "cracked": 0.15,
        "chipped": 0.3,
        "flawed": 0.525,
        "normal": 0.675,
        "flawless": 0.825,
        "perfect": 1.05,
    }


def test_a_gem_loses_the_bonuses_of_a_stat_switched_off(files: dict[str, bytes]) -> None:
    breach = generated_json(files, GEMS + "core/breach.json")
    assert [bonus["attribute"] for bonus in breach["bonuses"]] == [
        "realmforge:armor_pierce",
        "minecraft:block_interaction_range",
    ]
    royalty = generated_json(files, GEMS + "overworld/royalty.json")
    # The bow's multi-attribute bonus paired protection shred with a draw speed penalty: it goes as a whole.
    assert [bonus["type"] for bonus in royalty["bonuses"]] == [
        "apotheosis:all_stats",
        "apotheosis:drop_transform",
        "apotheosis:multi_attribute",
    ]
    all_stats = royalty["bonuses"][0]["attributes"]
    assert "realmforge:crit_chance" in all_stats and "realmforge:lifesteal" in all_stats
    assert not any(
        stat in all_stats for stat in ("apothic_attributes:prot_pierce", "apothic_attributes:prot_shred")
    )
    assert "apothic_attributes:arrow_damage" in all_stats


def test_tier_augments_give_monsters_our_armor_pierce_and_no_protection_pierce(
    files: dict[str, bytes],
) -> None:
    for tier, value in (("ascent", 5.0), ("summit", 15.0), ("pinnacle", 25.0)):
        augment = generated_json(files, AUGMENTS + f"{tier}/armor_pierce.json")
        assert augment["modifier"]["attribute"] == "realmforge:armor_pierce"
        assert augment["modifier"]["value"] == value
        assert augment["target"] == "monsters"
    for tier in ("summit", "pinnacle"):
        assert generated_json(files, AUGMENTS + f"{tier}/prot_pierce.json") == DISABLED
    assert AUGMENTS + "summit/experience.json" not in files


def test_no_generated_file_grants_a_remapped_apothic_attributes_stat(files: dict[str, bytes]) -> None:
    remapped = (
        "crit_chance",
        "crit_damage",
        "armor_pierce",
        "armor_shred",
        "life_steal",
        "overheal",
        "dodge_chance",
        "cooldown_reduction",
        "prot_pierce",
        "prot_shred",
    )
    for path, data in files.items():
        if path.startswith(DATA + "apotheosis/"):
            text = data.decode("utf-8")
            for stat in remapped:
                assert f'"apothic_attributes:{stat}"' not in text, path


def test_potion_charms_cannot_be_made(files: dict[str, bytes]) -> None:
    for recipe in ("potion_charm", "infusion/potion_charm"):
        assert generated_json(files, DATA + f"apotheosis/recipe/{recipe}.json") == DISABLED


def test_mythic_shows_as_legendary() -> None:
    lang = json.loads(
        (REPO_ROOT / "mod/src/main/resources/assets/apotheosis/lang/en_us.json").read_text(encoding="utf-8")
    )
    assert lang["rarity.apotheosis:mythic"] == "Legendary"
    assert not any("ythic" in text for text in lang.values())


# Entry changes


def test_an_entry_can_be_switched_off_or_have_fields_replaced(repo: RepoPaths) -> None:
    write(
        repo.content / "apotheosis_affixes" / "cleaving.yaml",
        SCHEMA.format("apotheosis_affixes")
        + "id: cleaving\naffix: apotheosis:melee/cleaving\ndisabled: true\n",
    )
    write(
        repo.content / "apotheosis_tier_augments" / "luck.yaml",
        "id: luck\naugment: apotheosis:frontier/luck\ndisabled: true\n",
    )
    write(
        repo.content / "apotheosis_rarities" / "uncommon.yaml",
        "id: uncommon\nrarity: apotheosis:uncommon\nreplace:\n  color: '#00ff00'\n",
    )
    write(
        repo.content / "apotheosis_gems" / "breach.yaml",
        "id: breach\ngem: apotheosis:core/breach\nreplace:\n  weights: {quality: 0.5, weight: 10}\n",
    )
    files = generated(repo)
    assert generated_json(files, AFFIXES + "melee/cleaving.json") == DISABLED
    assert generated_json(files, AUGMENTS + "frontier/luck.json") == DISABLED
    uncommon = generated_json(files, DATA + "apotheosis/apotheosis/rarities/uncommon.json")
    assert uncommon == {**load_snapshot(repo).entry("rarities", "apotheosis:uncommon"), "color": "#00ff00"}
    # A replaced gem still meets the stat remaps.
    breach = generated_json(files, GEMS + "core/breach.json")
    assert breach["weights"] == {"quality": 0.5, "weight": 10}
    assert breach["bonuses"][0]["attribute"] == "realmforge:armor_pierce"


def test_an_unknown_entry_is_reported_with_close_matches(repo: RepoPaths) -> None:
    write(
        repo.content / "apotheosis_affixes" / "vampiric.yaml",
        "id: vampiric\naffix: apotheosis:melee/attribute/vampirc\ndisabled: true\n",
    )
    assert_problem(
        repo,
        "Apotheosis 9.1.0 has no affixes entry apotheosis:melee/attribute/vampirc "
        "(did you mean: apotheosis:melee/attribute/vampiric",
    )


def test_an_entry_is_changed_once(repo: RepoPaths) -> None:
    write(
        repo.content / "apotheosis_affixes" / "again.yaml",
        "id: again\naffix: apotheosis:generic/attribute/lucky\ndisabled: true\n",
    )
    assert_problem(
        repo, "apotheosis:generic/attribute/lucky is already changed by content/apotheosis_affixes/"
    )


@pytest.mark.parametrize(
    ("replace", "message"),
    [
        ("  colour: 1\n", "has no field colour"),
        (
            "  categories: apotheosis:bow\n",
            "categories is a list in apotheosis:melee/attribute/vampiric, not a string",
        ),
        ("  categories: [apotheosis:wand]\n", "unknown loot category 'apotheosis:wand'"),
        ("  categories: [apotheosis:charm]\n", "exists only with Curios"),
        ("  values: {apotheosis:legendary: 1.0}\n", "unknown rarities apotheosis:legendary"),
        ("  attribute: minecraft:lucky\n", "unknown attribute 'minecraft:lucky'"),
        (
            "  definition: {affix_type: stat, exclusive_set: [apotheosis:melee/attribute/vampiric], weights: {}}\n",
            "cannot exclude itself",
        ),
        (
            "  definition: {affix_type: stat, exclusive_set: [apotheosis:melee/nothing], weights: {}}\n",
            "has no affix apotheosis:melee/nothing",
        ),
    ],
)
def test_a_replaced_field_is_checked(repo: RepoPaths, replace: str, message: str) -> None:
    write(
        repo.content / "apotheosis_affixes" / "vampiric.yaml",
        "id: vampiric\naffix: apotheosis:melee/attribute/vampiric\nreplace:\n" + replace,
    )
    assert_problem(repo, message)


@pytest.mark.parametrize(
    ("data", "message"),
    [
        ({"disabled": True, "replace": {"weight": 1}}, "either disabled or has fields replaced"),
        ({}, "say what changes"),
        ({"affix": "melee/attribute/vampiric", "disabled": True}, "should match pattern"),
    ],
)
def test_invalid_entry_changes_are_refused(data: dict, message: str) -> None:
    with pytest.raises(ValidationError, match=message):
        ApotheosisAffixDef.model_validate({"id": "test", "affix": "apotheosis:melee/test", **data})


def test_a_rarity_cannot_be_switched_off() -> None:
    with pytest.raises(ValidationError, match="Apotheosis needs every one of its rarities"):
        ApotheosisRarityDef.model_validate({"id": "rare", "rarity": "apotheosis:rare", "disabled": True})


# Stat remaps


@pytest.mark.parametrize(
    ("data", "message"),
    [
        ({"target": "realmforge:crit_chance", "off": True}, "but not both"),
        ({}, "give a target stat, or off: true"),
        ({"off": True, "source_base": 1.5}, "a stat switched off has none"),
        ({"target": "realmforge:crit_chance", "source_base": 0}, "greater than 0"),
        ({"source": "realmforge:crit_chance", "off": True}, "should match pattern"),
    ],
)
def test_invalid_remaps_are_refused(data: dict, message: str) -> None:
    with pytest.raises(ValidationError, match=message):
        ApotheosisStatRemapDef.model_validate(
            {"id": "test", "source": "apothic_attributes:crit_chance", **data}
        )


def test_a_remap_names_a_stat_that_exists(repo: RepoPaths) -> None:
    write(
        repo.content / "apotheosis_stat_remaps" / "crit_chance.yaml",
        "id: crit_chance\nsource: apothic_attributes:crit_chance\ntarget: realmforge:crit_chanse\n",
    )
    assert_problem(repo, "unknown id 'realmforge:crit_chanse' in registry minecraft:attribute")


def test_a_stat_is_remapped_once(repo: RepoPaths) -> None:
    write(
        repo.content / "apotheosis_stat_remaps" / "again.yaml",
        "id: again\nsource: apothic_attributes:crit_chance\noff: true\n",
    )
    assert_problem(
        repo, "apothic_attributes:crit_chance is already remapped by content/apotheosis_stat_remaps/"
    )


def test_a_remapped_stat_left_where_no_remap_applies_is_reported(repo: RepoPaths) -> None:
    """Bloodletting's bleeding is an effect, not a granted attribute: a remap cannot reach it."""
    write(
        repo.content / "apotheosis_stat_remaps" / "bleeding.yaml",
        "id: bleeding\nsource: apothic_attributes:bleeding\noff: true\n",
    )
    problems = problems_for(repo)
    assert any(
        "affixes apotheosis:melee/mob_effect/bloodletting names apothic_attributes:bleeding at mob_effect"
        in p
        for p in problems
    ), problems
    assert any(
        "no Apotheosis affix, gem or tier augment grants apothic_attributes:bleeding" in p for p in problems
    )


def test_source_base_cannot_convert_one_stat_of_an_all_stats_bonus(repo: RepoPaths) -> None:
    write(
        repo.content / "apotheosis_gems" / "royalty.yaml",
        "id: royalty\ngem: apotheosis:overworld/royalty\nreplace:\n  bonuses:\n"
        "    - type: apotheosis:all_stats\n      gem_class: apotheosis:helmet\n"
        "      operation: add_multiplied_base\n      attributes: [apothic_attributes:crit_damage]\n"
        "      values: {flawed: 0.05}\n",
    )
    assert_problem(repo, "cannot become add_value")


# Recipe removals


def test_vanilla_and_apotheosis_recipes_can_be_removed(repo: RepoPaths) -> None:
    write(
        repo.content / "recipe_removals" / "test.yaml",
        "id: test\nrecipes: [minecraft:lodestone, apotheosis:gem_case]\n",
    )
    files = generated(repo)
    assert generated_json(files, DATA + "minecraft/recipe/lodestone.json") == DISABLED
    assert generated_json(files, DATA + "apotheosis/recipe/gem_case.json") == DISABLED


@pytest.mark.parametrize(
    ("recipes", "message"),
    [
        (
            "[apotheosis:potion_charms]",
            "neither vanilla nor Apotheosis 9.1.0 has a recipe apotheosis:potion_charms",
        ),
        ("[realmforge:copper_wand]", "realmforge:copper_wand is our own"),
        ("[apotheosis:potion_charm]", "apotheosis:potion_charm is already removed by"),
        (
            "[minecraft:netherite_upgrade_smithing_template]",
            "replaces minecraft:netherite_upgrade_smithing_template",
        ),
    ],
)
def test_a_removed_recipe_is_checked(repo: RepoPaths, recipes: str, message: str) -> None:
    write(repo.content / "recipe_removals" / "test.yaml", f"id: test\nrecipes: {recipes}\n")
    assert_problem(repo, message)


def test_a_removal_names_recipes() -> None:
    with pytest.raises(ValidationError, match="at least 1 item"):
        RecipeRemovalDef.model_validate({"id": "test", "recipes": []})
    with pytest.raises(ValidationError, match="duplicate entries"):
        RecipeRemovalDef.model_validate(
            {"id": "test", "recipes": ["minecraft:lodestone", "minecraft:lodestone"]}
        )


def test_the_snapshot_is_required(repo: RepoPaths) -> None:
    for snapshot in repo.id_universe.glob("apotheosis-*.json"):
        snapshot.unlink()
    assert_problem(repo, "run `uv run rf ids apotheosis`")


# The affix set from Matěj's review (RLM-232)

REVIEW_OFF = (
    "armor/attribute/elastic",
    "armor/attribute/fireproof",
    "armor/attribute/oxygenated",
    "armor/attribute/winged",
    "armor/dmg_reduction/grounded",
    "armor/dmg_reduction/runed",
    "armor/mob_effect/blinding",
    "boots/ability/steadfast",
    "boots/ability/surefooted",
    "breaker/ability/sandforming",
    "breaker/ability/stoneforming",
    "breaker/attribute/submerged",
    "breaker/effect/omnetic",
    "breaker/enchantment/prosperous",
    "breaker/mob_effect/spelunkers",
    "melee/attribute/glacial",
    "melee/attribute/infernal",
    "melee/cleaving",
    "melee/executing",
    "melee/festive",
    "melee/mob_effect/elusive",
    "melee/mob_effect/omniscient",
    "ranged/enchantment/prosperous",
    "ranged/mob_effect/deathbound",
    "ranged/mob_effect/shulkers",
    "shears/effect/gardening",
    "shears/effect/leafforming",
    "shield/mob_effect/galvanizing",
    "shield/mob_effect/withering",
    "weapon/attribute/nullifying",
    "weapon/attribute/runebreaking",
)
RARITY_OVERRIDES = DATA + "apotheosis/apotheosis/rarity_override/"
NOTICES = "mod/src/generated/resources/META-INF/third_party/apotheosis/"


def test_the_review_s_thirty_one_affixes_are_off(files: dict[str, bytes]) -> None:
    """The 29 of the review, Executing (2026-10-29) and Runed (2026-10-30), and no other affix."""
    off = {
        path.removeprefix(AFFIXES).removesuffix(".json")
        for path in files
        if path.startswith(AFFIXES) and generated_json(files, path) == DISABLED
    }
    assert off == set(REVIEW_OFF)


@pytest.mark.parametrize(
    ("affix", "rare"),
    [
        ("melee/attribute/giant_slaying", {"min": 0.05, "max": 0.1}),
        ("melee/thunderstruck", {"min": 2.0, "max": 4.0, "step": 1.0}),
        ("ranged/spectral", {"min": 0.1, "max": 0.3}),
        ("shield/catalyzing", {"min": 100.0, "max": 300.0, "step": 20.0}),
        ("shield/psychic", {"min": 0.4, "max": 0.6}),
        (
            "ranged/mob_effect/acidic",
            {"duration": {"min": 40.0, "max": 80.0, "step": 20.0}, "amplifier": 0.0, "cooldown": 40},
        ),
        (
            "ranged/mob_effect/blighted",
            {
                "duration": {"min": 160.0, "max": 200.0, "step": 20.0},
                "amplifier": {"min": 0.0, "max": 1.0, "step": 0.25},
                "cooldown": 300,
            },
        ),
    ],
)
def test_the_review_opens_affixes_to_rare(files: dict[str, bytes], affix: str, rare: dict) -> None:
    assert generated_json(files, AFFIXES + f"{affix}.json")["values"]["apotheosis:rare"] == rare


def test_retreating_rolls_from_rare_and_magical_stays_epic(files: dict[str, bytes]) -> None:
    retreating = generated_json(files, AFFIXES + "shield/retreating.json")
    assert retreating["rarities"] == ["apotheosis:rare", "apotheosis:epic", "apotheosis:mythic"]
    # Magical stays an ability of Epic and Legendary (Matěj, 2026-10-29).
    assert AFFIXES + "ranged/magical.json" not in files


@pytest.mark.parametrize("affix", ["melee/attribute/giant_slaying", "ranged/mob_effect/acidic"])
def test_the_pinnacle_only_weights_the_review_drops_become_everyone_s(
    files: dict[str, bytes], affix: str
) -> None:
    assert generated_json(files, AFFIXES + f"{affix}.json")["definition"]["weights"] == {
        "quality": 0.1,
        "weight": 25,
    }


def test_blighted_s_epic_reaches_wither_iii(files: dict[str, bytes]) -> None:
    blighted = generated_json(files, AFFIXES + "ranged/mob_effect/blighted.json")
    assert blighted["values"]["apotheosis:epic"]["amplifier"]["max"] == 2.0
    assert blighted["values"]["apotheosis:mythic"]["amplifier"]["max"] == 3.0


def test_rare_gets_a_chanced_ability_slot(files: dict[str, bytes]) -> None:
    rare = generated_json(files, DATA + "apotheosis/apotheosis/rarities/rare.json")
    original = load_snapshot(RepoPaths()).entry("rarities", "apotheosis:rare")
    chanced = {
        "type": "apotheosis:chanced",
        "chance": 0.25,
        "rule": {"type": "apotheosis:affix", "affix_type": "ability"},
    }
    assert rare["rules"] == [*original["rules"][:4], chanced, *original["rules"][4:]]
    assert {key: value for key, value in rare.items() if key != "rules"} == {
        key: value for key, value in original.items() if key != "rules"
    }


@pytest.mark.parametrize("tier", ["haven", "frontier", "ascent", "summit"])
def test_the_world_tiers_cap_no_eterna(files: dict[str, bytes], tier: str) -> None:
    assert generated_json(files, AUGMENTS + f"{tier}/max_eterna.json") == DISABLED


# Rarity overrides


def labels(rules: list[dict]) -> list[str]:
    """A rarity's rules as their kinds: ``stat``, ``basic_effect?`` for a chanced slot, ``socket`` and so on."""
    return [
        rule["rule"]["affix_type"] + "?"
        if rule["type"] == "apotheosis:chanced"
        else rule.get("affix_type", rule["type"].removeprefix("apotheosis:"))
        for rule in rules
    ]


def test_helmets_boots_and_shears_skip_the_slots_they_have_nothing_for(files: dict[str, bytes]) -> None:
    """Matěj, 2026-10-31 (RLM-232): with Runed and Blinding off, helmets have no ability and one basic effect; with
    Runed, Steadfast and Surefooted off, boots have no ability below Epic; with Gardening and Leaf-Forming off, shears
    have one basic effect."""
    helmet = generated_json(files, RARITY_OVERRIDES + "apotheosis/helmet.json")["overrides"]
    assert {rarity: labels(rules) for rarity, rules in helmet.items()} == {
        "apotheosis:rare": ["stat", "stat", "basic_effect", "socket", "durability"],
        "apotheosis:epic": ["stat", "stat", "stat", "basic_effect", "socket", "durability"],
        "apotheosis:mythic": ["stat", "stat", "stat", "stat", "basic_effect", "select", "select"],
    }
    boots = generated_json(files, RARITY_OVERRIDES + "apotheosis/boots.json")["overrides"]
    assert {rarity: labels(rules) for rarity, rules in boots.items()} == {
        "apotheosis:rare": ["stat", "stat", "basic_effect", "basic_effect?", "socket", "durability"],
    }
    # Shears start from Apotheosis's own override: its other rarities stay as it ships them.
    shears = generated_json(files, RARITY_OVERRIDES + "apotheosis/shears.json")
    own = load_snapshot(RepoPaths()).entry("rarity_override", "apotheosis:apotheosis/shears")
    assert shears["category"] == "apotheosis:shears"
    for rarity in ("apotheosis:common", "apotheosis:uncommon", "apotheosis:rare"):
        assert shears["overrides"][rarity] == own["overrides"][rarity]
    assert labels(shears["overrides"]["apotheosis:epic"]) == ["socket", "stat", "basic_effect", "durability"]
    assert labels(shears["overrides"]["apotheosis:mythic"]) == ["socket", "stat", "basic_effect", "select"]


def test_the_rest_of_a_capped_rarity_is_the_fitted_rarity_s(files: dict[str, bytes]) -> None:
    rare = generated_json(files, DATA + "apotheosis/apotheosis/rarities/rare.json")["rules"]
    helmet = generated_json(files, RARITY_OVERRIDES + "apotheosis/helmet.json")["overrides"][
        "apotheosis:rare"
    ]
    # Rare's chanced basic effect and chanced ability go; everything else is Rare's own, in its order.
    assert helmet == [rule for rule in rare if rule["type"] != "apotheosis:chanced"]


def overriding(repo: RepoPaths, body: str) -> None:
    write(repo.content / "apotheosis_rarity_overrides" / "test.yaml", "id: test\n" + body)


def test_chanced_slots_go_first(repo: RepoPaths) -> None:
    overriding(repo, "category: apotheosis:bow\nmax_slots:\n  apotheosis:rare: {basic_effect: 1, stat: 1}\n")
    files = generated(repo)
    bow = generated_json(files, RARITY_OVERRIDES + "apotheosis/bow.json")["overrides"]["apotheosis:rare"]
    assert labels(bow) == ["stat", "basic_effect", "ability?", "socket", "durability"]


@pytest.mark.parametrize(
    ("body", "message"),
    [
        (
            "category: apotheosis:wand\nmax_slots: {apotheosis:rare: {ability: 0}}\n",
            "unknown loot category 'apotheosis:wand'",
        ),
        (
            "category: apotheosis:charm\nmax_slots: {apotheosis:rare: {ability: 0}}\n",
            "exists only with Curios",
        ),
        (
            "category: apotheosis:bow\nmax_slots: {apotheosis:legendary: {ability: 0}}\n",
            "unknown rarity 'apotheosis:legendary'",
        ),
        (
            "category: apotheosis:bow\nmax_slots: {apotheosis:common: {ability: 0}}\n",
            "apotheosis:bow has 0 ability slot(s) at apotheosis:common: nothing to drop for at most 0",
        ),
        (
            "category: apotheosis:bow\nmax_slots: {apotheosis:rare: {stat: 2}}\n",
            "apotheosis:bow has 2 stat slot(s) at apotheosis:rare: nothing to drop for at most 2",
        ),
        (
            "category: apotheosis:helmet\nmax_slots: {apotheosis:rare: {stat: 1}}\n",
            "apotheosis:helmet already has a rarity override",
        ),
    ],
)
def test_a_rarity_override_is_checked(repo: RepoPaths, body: str, message: str) -> None:
    overriding(repo, body)
    assert_problem(repo, message)


def test_a_rarity_override_starts_from_apotheosis_s_own(repo: RepoPaths) -> None:
    """Apotheosis's override for shears has no ability slot: the override reads it, not Rare's rules."""
    write(
        repo.content / "apotheosis_rarity_overrides" / "shears.yaml",
        "id: shears\ncategory: apotheosis:shears\nmax_slots: {apotheosis:rare: {ability: 0}}\n",
    )
    assert_problem(repo, "apotheosis:shears has 0 ability slot(s) at apotheosis:rare")


@pytest.mark.parametrize(
    "max_slots",
    [{}, {"apotheosis:rare": {}}, {"apotheosis:rare": {"ability": -1}}, {"apotheosis:rare": {"socket": 0}}],
)
def test_a_rarity_override_names_rarities_kinds_and_counts(max_slots: dict) -> None:
    with pytest.raises(ValidationError):
        ApotheosisRarityOverrideDef.model_validate(
            {"id": "test", "category": "apotheosis:bow", "max_slots": max_slots}
        )


def test_unbound_no_longer_names_winged(files: dict[str, bytes]) -> None:
    unbound = generated_json(files, AFFIXES + "armor/attribute/unbound.json")
    assert unbound["definition"]["exclusive_set"] == []
    assert unbound["definition"]["weights"] == {"pinnacle": {"quality": 0.2, "weight": 1}}


def test_a_switched_off_affix_left_in_an_exclusive_set_is_reported(repo: RepoPaths) -> None:
    (repo.content / "apotheosis_affixes" / "unbound.yaml").unlink()
    assert_problem(
        repo,
        "apotheosis:armor/attribute/unbound names apotheosis:armor/attribute/winged, which is switched off, in its "
        "exclusive set",
    )


# Apotheosis's license (RLM-524)


def test_the_jar_carries_apotheosis_s_license_next_to_its_data(files: dict[str, bytes]) -> None:
    snapshot = load_snapshot(RepoPaths())
    assert files[NOTICES + "LICENSE"] == snapshot.license_text.encode()
    notice = files[NOTICES + "NOTICE"].decode()
    assert f"Apotheosis's own data, version {snapshot.version}" in notice
    assert "MIT-licensed: see LICENSE" in notice


def test_no_apotheosis_data_needs_no_license(repo: RepoPaths) -> None:
    for folder in repo.content.glob("apotheosis_*"):
        for path in folder.glob("*.yaml"):
            path.unlink()
    for path in (repo.content / "recipe_removals").glob("*.yaml"):
        path.unlink()
    files = generated(repo)
    assert not any(path.startswith(NOTICES) or "/data/apotheosis/" in path for path in files)
