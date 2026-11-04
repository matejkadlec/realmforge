"""Apotheosis's data, fitted (content/apotheosis_*/, content/recipe_removals/): the snapshot, validation and the
generated files (RLM-271, RLM-272, ADR 0063)."""

from __future__ import annotations

import json
import tomllib
import zipfile
from pathlib import Path

import pytest
from pydantic import ValidationError

from realmforge_tools import apotheosis
from realmforge_tools.apotheosis import DISABLED, build_snapshot, load_snapshot, pack_jar
from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import REPO_ROOT, RepoPaths
from realmforge_tools.schema.apotheosis import (
    RARITIES,
    ROBE_LOOT_CATEGORIES,
    ApotheosisAffixDef,
    ApotheosisNewAffixDef,
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
ARMOR_TO_ROBE = {
    "apotheosis:helmet": "realmforge:robe_head",
    "apotheosis:chestplate": "realmforge:robe_chest",
    "apotheosis:leggings": "realmforge:robe_legs",
    "apotheosis:boots": "realmforge:robe_feet",
}


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


def test_both_downloads_give_up_after_a_timeout(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    """RLM-584: the jar and the license text are fetched with a timeout, so a host that accepts the connection and then
    stalls fails ``rf ids apotheosis`` instead of holding CI's required job until its own timeout."""
    jar = b"a jar"
    monkeypatch.setattr(
        apotheosis,
        "pack_jar",
        lambda _: {
            "file": "apotheosis.jar",
            "url": "https://jar.example/apotheosis.jar",
            "sha512": apotheosis._sha512(jar),
        },
    )
    calls: list[tuple[str, float | None]] = []

    class Stalled(Exception):
        pass

    class Response:
        def __enter__(self) -> Response:
            return self

        def __exit__(self, *_: object) -> None:
            return None

        def read(self) -> bytes:
            return jar

    def urlopen(url: str, timeout: float | None = None) -> Response:
        calls.append((url, timeout))
        if url == apotheosis.LICENSE_URL:
            raise Stalled  # stops before the snapshot is built from the fake jar
        return Response()

    monkeypatch.setattr(apotheosis.urllib.request, "urlopen", urlopen)
    with pytest.raises(Stalled):
        apotheosis.extract(RepoPaths(tmp_path))
    assert calls == [
        ("https://jar.example/apotheosis.jar", apotheosis.DOWNLOAD_TIMEOUT_S),
        (apotheosis.LICENSE_URL, apotheosis.DOWNLOAD_TIMEOUT_S),
    ]
    assert apotheosis.DOWNLOAD_TIMEOUT_S == 120


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
    # Apotheosis 9.1.0's generic/attribute/lucky.json, without apotheosis:charm, with the robes (RLM-557).
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
            "realmforge:robe_head",
            "realmforge:robe_chest",
            "realmforge:robe_legs",
            "realmforge:robe_feet",
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
    ],
)
def test_an_affix_grants_our_stat_with_apotheosis_s_values(
    files: dict[str, bytes], affix: str, stat: str
) -> None:
    original = load_snapshot(RepoPaths()).entry("affixes", f"apotheosis:{affix}")
    fitted = generated_json(files, AFFIXES + f"{affix}.json")
    assert fitted == {**original, "attribute": stat}


@pytest.mark.parametrize(
    ("affix", "stat"),
    [
        ("blast_forged", "realmforge:explosion_resistance"),
        ("blockading", "realmforge:physical_resistance"),
        ("deflective", "realmforge:projectile_resistance"),
        ("feathery", "realmforge:fall_resistance"),
    ],
)
def test_a_damage_reduction_affix_becomes_our_resistance_with_apotheosis_s_values(
    files: dict[str, bytes], affix: str, stat: str
) -> None:
    """RLM-233: the reduction share per rarity becomes an add_value of our stat, a share too; the items it rolls on and
    its slot kind stay Apotheosis's, and the robe of each armor category it lists joins them (RLM-557)."""
    original = load_snapshot(RepoPaths()).entry("affixes", f"apotheosis:armor/dmg_reduction/{affix}")
    assert original is not None and original["type"] == "apotheosis:damage_reduction"
    fitted = generated_json(files, AFFIXES + f"armor/dmg_reduction/{affix}.json")
    robes = [ARMOR_TO_ROBE[category] for category in original["categories"]]
    assert fitted == {
        "type": "apotheosis:attribute",
        "attribute": stat,
        "categories": original["categories"] + robes,
        "definition": original["definition"],
        "operation": "add_value",
        "values": original["values"],
    }


@pytest.mark.parametrize("affix", ["weapon/attribute/nullifying", "weapon/attribute/runebreaking"])
def test_protection_pierce_and_shred_affixes_are_off(files: dict[str, bytes], affix: str) -> None:
    assert generated_json(files, AFFIXES + f"{affix}.json") == DISABLED


def test_stats_without_an_equivalent_of_ours_stay_apothic_attributes(files: dict[str, bytes]) -> None:
    """Healing received (RLM-233), the ranged stats (RLM-235), vanilla XP (Matěj, 2026-10-29) and the rest keep their
    affixes untouched."""
    spiritual = generated_json(files, AFFIXES + "armor/attribute/spiritual.json")
    assert (
        spiritual["attribute"] == "apothic_attributes:healing_received"
    )  # it only joins the robes (RLM-557)
    for affix in (
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


def test_no_affix_or_gem_of_the_pack_gives_apothic_attributes_bleeding(files: dict[str, bytes]) -> None:
    """RLM-600 (Matěj, 2026-11-03: the Apotheosis bleed leaves the pack): every affix and gem Apotheosis loads, its own
    or the one our file replaces it with, gives no ``apothic_attributes:bleeding``; Bloodletting and Devilish are off,
    and Tyrannical keeps every bonus but its bow's."""
    snapshot = load_snapshot(RepoPaths())
    loaded = 0
    for kind, folder in (("affixes", AFFIXES), ("gems", GEMS)):
        for entry_id, entry in snapshot.entries[kind].items():
            path = folder + entry_id.removeprefix("apotheosis:") + ".json"
            data = generated_json(files, path) if path in files else entry
            if data == DISABLED:
                continue
            loaded += 1
            assert "apothic_attributes:bleeding" not in json.dumps(data), entry_id
    assert loaded > 50, loaded
    for affix in ("melee/mob_effect/bloodletting", "shield/mob_effect/devilish"):
        assert generated_json(files, AFFIXES + affix + ".json") == DISABLED, affix
    tyrannical = generated_json(files, GEMS + "core/tyrannical.json")
    own = snapshot.entry("gems", "apotheosis:core/tyrannical")
    assert own is not None
    assert [bonus["gem_class"] for bonus in tyrannical["bonuses"]] == [
        bonus["gem_class"] for bonus in own["bonuses"] if bonus["gem_class"] != "apotheosis:bow"
    ]
    assert {key: value for key, value in tyrannical.items() if key != "bonuses"} == {
        key: value for key, value in own.items() if key != "bonuses"
    }


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
        (
            {"disabled": True, "to_attribute": "realmforge:fall_resistance"},
            "a switched-off affix becomes no attribute affix",
        ),
    ],
)
def test_invalid_entry_changes_are_refused(data: dict, message: str) -> None:
    with pytest.raises(ValidationError, match=message):
        ApotheosisAffixDef.model_validate({"id": "test", "affix": "apotheosis:melee/test", **data})


def test_to_attribute_needs_a_damage_reduction_affix(repo: RepoPaths) -> None:
    write(
        repo.content / "apotheosis_affixes" / "vampiric.yaml",
        "id: vampiric\naffix: apotheosis:melee/attribute/vampiric\nto_attribute: realmforge:fall_resistance\n",
    )
    assert_problem(
        repo,
        "to_attribute turns a damage reduction affix (apotheosis:damage_reduction) into an attribute affix; "
        "apotheosis:melee/attribute/vampiric is apotheosis:attribute",
    )


@pytest.mark.parametrize(
    ("target", "message"),
    [
        (
            "realmforge:fall_resistanc",
            "unknown id 'realmforge:fall_resistanc' in registry minecraft:attribute",
        ),
        ("realmforge:stamina", "realmforge:stamina must be one of our stats that is a share"),
        ("minecraft:armor", "minecraft:armor must be one of our stats that is a share"),
    ],
)
def test_to_attribute_names_a_share_of_ours(repo: RepoPaths, target: str, message: str) -> None:
    write(
        repo.content / "apotheosis_affixes" / "feathery.yaml",
        f"id: feathery\naffix: apotheosis:armor/dmg_reduction/feathery\nto_attribute: {target}\n",
    )
    assert_problem(repo, message)


def test_a_converted_affix_may_have_fields_replaced_first(repo: RepoPaths) -> None:
    write(
        repo.content / "apotheosis_affixes" / "feathery.yaml",
        "id: feathery\naffix: apotheosis:armor/dmg_reduction/feathery\nto_attribute: realmforge:fall_resistance\n"
        "replace:\n  categories: [apotheosis:boots, apotheosis:leggings]\n",
    )
    feathery = generated_json(generated(repo), AFFIXES + "armor/dmg_reduction/feathery.json")
    assert feathery["type"] == "apotheosis:attribute"
    assert feathery["categories"] == ["apotheosis:boots", "apotheosis:leggings"]


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
    """Acidic's Sundering is an effect, not a granted attribute: a remap cannot reach it (Bloodletting's Bleeding was
    the example until RLM-600 turned it off)."""
    write(
        repo.content / "apotheosis_stat_remaps" / "sundering.yaml",
        "id: sundering\nsource: apothic_attributes:sundering\noff: true\n",
    )
    problems = problems_for(repo)
    assert any(
        "affixes apotheosis:ranged/mob_effect/acidic names apothic_attributes:sundering at mob_effect" in p
        for p in problems
    ), problems
    assert any(
        "no Apotheosis affix, gem or tier augment grants apothic_attributes:sundering" in p for p in problems
    )


def test_a_switched_off_entry_counts_for_no_remap(repo: RepoPaths) -> None:
    """RLM-600 turned Bloodletting and Devilish off and took Tyrannical's bow bonus, the pack's only sources of
    Apothic Attributes' Bleeding, so nothing is left for a remap of it to reach."""
    write(
        repo.content / "apotheosis_stat_remaps" / "bleeding.yaml",
        "id: bleeding\nsource: apothic_attributes:bleeding\noff: true\n",
    )
    problems = problems_for(repo)
    assert problems == [
        "content/apotheosis_stat_remaps/bleeding.yaml: source: no Apotheosis affix, gem or tier augment grants "
        "apothic_attributes:bleeding"
    ], problems


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


# RLM-600: the Apotheosis bleed leaves the pack (Matěj, 2026-11-03).
BLEEDS_OFF = ("melee/mob_effect/bloodletting", "shield/mob_effect/devilish")


def test_the_review_s_thirty_one_affixes_and_the_two_bleeds_are_off(files: dict[str, bytes]) -> None:
    """The 29 of the review, Executing (2026-10-29) and Runed (2026-10-30), Bloodletting and Devilish (RLM-600), and
    no other affix."""
    off = {
        path.removeprefix(AFFIXES).removesuffix(".json")
        for path in files
        if path.startswith(AFFIXES) and generated_json(files, path) == DISABLED
    }
    assert off == set(REVIEW_OFF) | set(BLEEDS_OFF)


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


def test_the_wand_drops_every_affix_slot_and_keeps_the_rest(files: dict[str, bytes]) -> None:
    """RLM-546: the wand has no affix to roll yet (RLM-562), so every rarity keeps its sockets and durability and no
    slot is left for Apotheosis to log as empty."""
    override = generated_json(files, RARITY_OVERRIDES + "realmforge/wand.json")
    assert override["category"] == "realmforge:wand"
    rules = override["overrides"]
    assert list(rules) == list(RARITIES)
    assert rules["apotheosis:common"] == []
    assert labels(rules["apotheosis:uncommon"]) == ["socket"]
    assert labels(rules["apotheosis:rare"]) == ["socket", "durability"]
    assert labels(rules["apotheosis:epic"]) == ["socket", "durability"]
    assert labels(rules["apotheosis:mythic"]) == ["select", "select"]


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
        ("category: apotheosis:bow\nno_affixes: true\n", "apotheosis:bow is Apotheosis's and has affixes"),
        ("category: realmforge:wand\nno_affixes: true\n", "realmforge:wand already has a rarity override"),
        (
            "category: realmforge:robe_hands\nno_affixes: true\n",
            "unknown loot category 'realmforge:robe_hands'",
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


def test_a_rarity_override_caps_slots_or_drops_them_all_not_both() -> None:
    with pytest.raises(ValidationError, match="max_slots, or no_affixes"):
        ApotheosisRarityOverrideDef.model_validate(
            {
                "id": "test",
                "category": "realmforge:wand",
                "max_slots": {"apotheosis:rare": {"stat": 0}},
                "no_affixes": True,
            }
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
    # The robes' loot category data map is ours, written from our items (RLM-546), not Apotheosis's data.
    ours = DATA + "apotheosis/data_maps/item/loot_category_overrides.json"
    assert not any(
        path.startswith(NOTICES) or ("/data/apotheosis/" in path and path != ours) for path in files
    )


# Affixes of our own and the robes' affixes (RLM-557, the gear design's table).
NEW_AFFIXES = DATA + "realmforge/apotheosis/affixes/"
GIVES_ARMOR = {"minecraft:armor", "minecraft:armor_toughness"}


def spread(*ranges: tuple[float, float, float] | float) -> list[dict | float]:
    return [
        value if isinstance(value, int | float) else {"min": value[0], "max": value[1], "step": value[2]}
        for value in ranges
    ]


def fitted_affixes(files: dict[str, bytes]) -> dict[str, dict]:
    """Every affix Apotheosis loads, by id: ours, and its own as fitted, without the ones switched off."""
    affixes: dict[str, dict] = {}
    for id_, original in load_snapshot(RepoPaths()).entries["affixes"].items():
        data = (
            json.loads(files[path])
            if (path := AFFIXES + id_.split(":", 1)[1] + ".json") in files
            else original
        )
        if data != DISABLED:
            affixes[id_] = data
    for path, data in files.items():
        if path.startswith(NEW_AFFIXES):
            affixes[f"realmforge:{path.removeprefix(NEW_AFFIXES).removesuffix('.json')}"] = json.loads(data)
    return affixes


def listed_categories(affix: dict) -> list[str]:
    return affix.get("categories") or affix.get("types") or []


@pytest.mark.parametrize(
    ("affix", "stat", "rarities", "values"),
    [
        (
            "robe_sorcerous",
            "realmforge:spell_power",
            RARITIES,
            spread((1, 1.5, 0.25), (1, 1.5, 0.25), (1.5, 2.5, 0.25), (2, 3, 0.25), (2.5, 4, 0.25)),
        ),
        (
            "robe_deep",
            "realmforge:max_mana",
            RARITIES,
            spread((5, 10, 1), (8, 12, 1), (10, 18, 1), (15, 25, 1), (20, 35, 1)),
        ),
        (
            "robe_flowing",
            "realmforge:mana_regeneration",
            RARITIES,
            spread((0.2, 0.4, 0.05), (0.3, 0.5, 0.05), (0.4, 0.8, 0.05), (0.6, 1, 0.05), (0.8, 1.5, 0.05)),
        ),
        (
            "robe_warded",
            "realmforge:magic_shield",
            RARITIES,
            spread(1, (1, 2, 0.5), (1, 3, 0.5), (2, 4, 0.5), (3, 5, 0.5)),
        ),
        *(
            (
                f"robe_{name}",
                f"realmforge:{school}_power",
                RARITIES[1:],
                spread((0.03, 0.05, 0.01), (0.04, 0.07, 0.01), (0.06, 0.1, 0.01), (0.08, 0.14, 0.01)),
            )
            for name, school in (("fiery", "fire"), ("frozen", "frost"), ("arcane_touched", "arcane"))
        ),
    ],
)
def test_a_robe_affix_has_the_gear_design_s_values(
    files: dict[str, bytes], affix: str, stat: str, rarities: tuple[str, ...], values: list
) -> None:
    assert generated_json(files, NEW_AFFIXES + f"{affix}.json") == {
        "type": "apotheosis:attribute",
        "attribute": stat,
        "categories": list(ROBE_LOOT_CATEGORIES.values()),
        "definition": {"affix_type": "stat", "exclusive_set": [], "weights": {"quality": 0.1, "weight": 25}},
        "operation": "add_value",
        "values": dict(zip(rarities, values, strict=True)),
    }


def test_an_affix_of_ours_has_its_name_and_suffix_in_our_lang(files: dict[str, bytes]) -> None:
    lang = generated_json(files, "mod/src/generated/resources/assets/realmforge/lang/en_us.json")
    assert lang["affix.realmforge:robe_sorcerous"] == "Sorcerous"
    assert lang["affix.realmforge:robe_sorcerous.suffix"] == "of Sorcery"
    for path in files:
        if path.startswith(NEW_AFFIXES):
            key = f"affix.realmforge:{path.removeprefix(NEW_AFFIXES).removesuffix('.json')}"
            assert key in lang and f"{key}.suffix" in lang


def test_timekeeping_rolls_on_the_robe_head_and_chest_from_rare(files: dict[str, bytes]) -> None:
    timekeeping = generated_json(files, AFFIXES + "armor/attribute/timekeeping.json")
    assert timekeeping["attribute"] == "realmforge:cooldown_reduction"
    assert timekeeping["categories"] == [
        "apotheosis:helmet",
        "apotheosis:chestplate",
        "apotheosis:shield",
        "realmforge:robe_head",
        "realmforge:robe_chest",
    ]
    assert timekeeping["values"] == {
        "apotheosis:rare": {"min": 0.01, "max": 0.05, "step": 0.005},
        "apotheosis:epic": {"min": 0.05, "max": 0.075, "step": 0.005},
        "apotheosis:mythic": {"min": 0.05, "max": 0.1, "step": 0.005},
    }


def test_no_robe_rolls_armor_or_toughness(files: dict[str, bytes]) -> None:
    for id_, affix in fitted_affixes(files).items():
        if affix.get("attribute") in GIVES_ARMOR:
            assert not set(listed_categories(affix)) & set(ROBE_LOOT_CATEGORIES.values()), id_


def test_every_kept_armor_affix_that_gives_no_armor_joins_the_robe_of_its_slot(
    files: dict[str, bytes],
) -> None:
    """Task 3: the rule of the gear design, so a later review's change to the affix set cannot leave one behind."""
    checked = 0
    for id_, affix in fitted_affixes(files).items():
        listed = listed_categories(affix)
        armor = [category for category in listed if category in ARMOR_TO_ROBE]
        if armor and affix.get("attribute") not in GIVES_ARMOR:
            assert {ARMOR_TO_ROBE[category] for category in armor} <= set(listed), id_
            checked += 1
    assert checked == 18


def test_a_robe_has_an_affix_for_every_slot_it_keeps(files: dict[str, bytes]) -> None:
    """Apotheosis logs an error and rolls nothing for a slot that finds no affix of its kind left for the item."""
    snapshot = load_snapshot(RepoPaths())
    affixes = fitted_affixes(files)
    for category in ROBE_LOOT_CATEGORIES.values():
        override_file = RARITY_OVERRIDES + category.replace(":", "/") + ".json"
        overrides = generated_json(files, override_file)["overrides"] if override_file in files else {}
        for rarity in RARITIES:
            rarity_file = DATA + "apotheosis/apotheosis/rarities/" + rarity.split(":")[1] + ".json"
            fitted = (
                generated_json(files, rarity_file)
                if rarity_file in files
                else snapshot.entry("rarities", rarity)
            )
            rules = overrides.get(rarity, fitted["rules"])
            for kind in ("stat", "basic_effect", "ability"):
                slots = sum(_slot_kind_of(rule) == kind for rule in rules)
                rolled = [
                    id_
                    for id_, affix in affixes.items()
                    if category in listed_categories(affix)
                    and affix["definition"]["affix_type"] == kind
                    and rarity in affix.get("values", {})
                ]
                assert len(rolled) >= slots, (category, rarity, kind, rolled)


def _slot_kind_of(rule: dict) -> str | None:
    if rule["type"] == "apotheosis:chanced":
        return _slot_kind_of(rule["rule"])
    return rule.get("affix_type") if rule["type"] == "apotheosis:affix" else None


def test_only_the_robe_head_and_feet_skip_slots(files: dict[str, bytes]) -> None:
    assert RARITY_OVERRIDES + "realmforge/robe_chest.json" not in files
    assert RARITY_OVERRIDES + "realmforge/robe_legs.json" not in files
    head = generated_json(files, RARITY_OVERRIDES + "realmforge/robe_head.json")["overrides"]
    feet = generated_json(files, RARITY_OVERRIDES + "realmforge/robe_feet.json")["overrides"]
    for rarity in ("apotheosis:rare", "apotheosis:epic", "apotheosis:mythic"):
        assert not [rule for rule in head[rarity] if _slot_kind_of(rule) == "ability"]
        assert len([rule for rule in head[rarity] if _slot_kind_of(rule) == "basic_effect"]) == 1
        assert not [rule for rule in feet[rarity] if _slot_kind_of(rule) == "ability"]
    assert list(head) == list(feet) == ["apotheosis:rare", "apotheosis:epic", "apotheosis:mythic"]


def new_affix(repo: RepoPaths, body: str) -> None:
    write(
        repo.content / "apotheosis_new_affixes" / "test_affix.yaml",
        "id: test_affix\nname: {en_us: Testing}\nsuffix: {en_us: of Tests}\n" + body,
    )


def test_a_new_affix_needs_no_range_for_a_fixed_value(repo: RepoPaths) -> None:
    new_affix(
        repo,
        "stat: realmforge:magic_shield\ncategories: [realmforge:robe_chest]\nvalues: {apotheosis:mythic: 2}\nweight: 5\n",
    )
    affix = generated_json(generated(repo), NEW_AFFIXES + "test_affix.json")
    assert affix["values"] == {"apotheosis:mythic": 2.0}
    assert affix["definition"]["weights"] == {"quality": 0.1, "weight": 5}


@pytest.mark.parametrize(
    ("body", "message"),
    [
        (
            "stat: minecraft:armor\ncategories: [realmforge:robe_chest]\nvalues: {apotheosis:rare: 1}\n",
            "must be one of our stats",
        ),
        (
            "stat: realmforge:spell_power\ncategories: [apotheosis:chestplate]\nvalues: {apotheosis:rare: 1}\n",
            "not a loot category of ours",
        ),
        (
            "stat: realmforge:fire_power\ncategories: [realmforge:robe_chest]\nvalues: {apotheosis:rare: 3}\n",
            "its values are fractions",
        ),
        (
            "stat: realmforge:fire_power\ncategories: [realmforge:robe_chest]\n"
            "values: {apotheosis:rare: {min: 3, max: 5, step: 1}}\n",
            "its values are fractions",
        ),
        (
            "stat: realmforge:spell_power\ncategories: [realmforge:wand]\nvalues: {apotheosis:rare: 1}\n",
            "says no_affixes",
        ),
    ],
)
def test_a_new_affix_is_checked(repo: RepoPaths, body: str, message: str) -> None:
    new_affix(repo, body)
    assert_problem(repo, message)


def test_a_replaced_list_cannot_name_a_category_without_slots(repo: RepoPaths) -> None:
    write(
        repo.content / "apotheosis_affixes" / "vampiric.yaml",
        "id: vampiric\naffix: apotheosis:melee/attribute/vampiric\nreplace:\n  categories: [realmforge:wand]\n",
    )
    assert_problem(repo, "says no_affixes")


@pytest.mark.parametrize(
    ("values", "message"),
    [
        ({"apotheosis:legendary": 1}, "unknown rarities apotheosis:legendary"),
        ({}, "at least 1 item"),
        ({"apotheosis:rare": {"min": 2, "max": 2, "step": 1}}, "a fixed value is a plain number"),
        ({"apotheosis:rare": {"min": 3, "max": 2, "step": 1}}, "min must be below max"),
        ({"apotheosis:rare": {"min": 1, "max": 2, "step": 0.3}}, "not a whole number of steps"),
        ({"apotheosis:rare": {"min": 1, "max": 2, "step": 0}}, "greater than 0"),
    ],
)
def test_a_new_affix_s_values_are_refused_by_its_schema(values: dict, message: str) -> None:
    data = {
        "id": "test",
        "name": {"en_us": "Testing"},
        "suffix": {"en_us": "of Tests"},
        "stat": "realmforge:spell_power",
        "categories": ["realmforge:robe_chest"],
        "values": values,
    }
    with pytest.raises(ValidationError, match=message):
        ApotheosisNewAffixDef.model_validate(data)


def test_a_new_affix_names_a_category() -> None:
    with pytest.raises(ValidationError, match="at least 1 item"):
        ApotheosisNewAffixDef.model_validate(
            {
                "id": "test",
                "name": {"en_us": "Testing"},
                "suffix": {"en_us": "of Tests"},
                "stat": "realmforge:spell_power",
                "categories": [],
                "values": {"apotheosis:rare": 1},
            }
        )
