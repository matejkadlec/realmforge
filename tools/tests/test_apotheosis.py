"""Apotheosis's data, fitted (content/apotheosis_*/, content/recipe_removals/): the snapshot, validation and the
generated files (RLM-271, RLM-272, ADR 0062)."""

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


def test_a_snapshot_holds_the_jar_s_entries_by_kind_and_its_recipe_ids(tmp_path: Path) -> None:
    jar = tmp_path / "apotheosis.jar"
    with zipfile.ZipFile(jar, "w") as archive:
        archive.writestr("META-INF/neoforge.mods.toml", '[[mods]]\nmodId = "apotheosis"\nversion = "9.9.9"\n')
        archive.writestr("data/apotheosis/apotheosis/affixes/melee/attribute/test.json", '{"type": "a"}')
        archive.writestr("data/apotheosis/apotheosis/gems/core/test.json", '{"bonuses": []}')
        archive.writestr("data/apotheosis/apotheosis/apothic_elites/test.json", "{}")
        archive.writestr("data/apotheosis/recipe/infusion/test.json", "{}")
        archive.writestr("assets/apotheosis/lang/en_us.json", "{}")
    snapshot = build_snapshot(jar, {"file": "apotheosis.jar", "url": "", "sha512": "abc"})
    assert snapshot["version"] == "9.9.9"
    assert snapshot["jar"] == {"file": "apotheosis.jar", "sha512": "abc"}
    assert snapshot["affixes"] == {"apotheosis:melee/attribute/test": {"type": "a"}}
    assert snapshot["gems"] == {"apotheosis:core/test": {"bonuses": []}}
    assert snapshot["tier_augments"] == {} and snapshot["rarities"] == {}
    assert snapshot["recipes"] == ["apotheosis:infusion/test"]


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
        "melee/attribute/giant_slaying",
    ):
        assert AFFIXES + f"{affix}.json" not in files


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
        repo.content / "apotheosis_tier_augments" / "max_eterna.yaml",
        "id: max_eterna\naugment: apotheosis:haven/max_eterna\ndisabled: true\n",
    )
    write(
        repo.content / "apotheosis_rarities" / "rare.yaml",
        "id: rare\nrarity: apotheosis:rare\nreplace:\n  color: '#00ff00'\n",
    )
    write(
        repo.content / "apotheosis_gems" / "breach.yaml",
        "id: breach\ngem: apotheosis:core/breach\nreplace:\n  weights: {quality: 0.5, weight: 10}\n",
    )
    files = generated(repo)
    assert generated_json(files, AFFIXES + "melee/cleaving.json") == DISABLED
    assert generated_json(files, AUGMENTS + "haven/max_eterna.json") == DISABLED
    rare = generated_json(files, DATA + "apotheosis/apotheosis/rarities/rare.json")
    assert rare == {**load_snapshot(repo).entry("rarities", "apotheosis:rare"), "color": "#00ff00"}
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
