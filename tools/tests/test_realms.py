"""content/realms/: the datapack registry realmforge:realm, each realm's unlock advancement and the checks between a
realm, its dimension, its dimension type and its dimension rules; the portal's fields and Overworld's entry (realm
portal spec, "Data")."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

DATA = "mod/src/generated/resources/data/realmforge"
LANG = "mod/src/generated/resources/assets/realmforge/lang/en_us.json"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedRealmExpectations.java"
)
ITEMS = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedItems.java"


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def edit_json(repo: RepoPaths, rel: str, change: dict[str, object]) -> None:
    path = repo.content / "worldgen" / rel
    data = json.loads(path.read_text(encoding="utf-8"))
    data.update(change)
    path.write_text(json.dumps(data), encoding="utf-8")


def test_repository_realms_are_undervault_and_overworlds_entry() -> None:
    content, universe = load_and_validate(RepoPaths())
    assert [(e.model.id, e.model.dimension, e.model.name.en_us) for e in content.realms] == [
        ("overworld", "minecraft:overworld", "Overworld"),
        ("stone", "realmforge:stone", "Undervault"),
    ]
    assert universe.contains("minecraft:advancement", "realmforge:realm/stone")
    assert not universe.contains("minecraft:advancement", "realmforge:realm/overworld")
    assert universe.contains("realmforge:realm", "realmforge:stone")


def test_a_realm_becomes_its_entry_unlock_and_texts(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{DATA}/realmforge/realm/stone.json"]) == {
        "dimension": "realmforge:stone",
        "name": "realm.realmforge.stone.name",
        "subtitle": "realm.realmforge.stone.subtitle",
        "description": "realm.realmforge.stone.description",
        "locked_hint": "realm.realmforge.stone.locked_hint",
        "levels": [6, 18],
        "flavour": "realm.realmforge.stone.flavour",
        "icon": "realmforge:vaultstone",
        "signet": "realmforge:undervault_signet",
        "colour": "#7A8FB0",
        "particle": "minecraft:ash",
        "sound_pitch": 0.8,
        "arrival": {"y": [20, 90], "floor": "realmforge:vaultstone", "pocket_radius": 3},
    }
    unlock = json.loads(files[f"{DATA}/advancement/realm/stone.json"])
    assert unlock["criteria"] == {"unlocked": {"trigger": "minecraft:impossible"}}
    assert unlock["requirements"] == [["unlocked"]]
    display = unlock["display"]
    assert (display["hidden"], display["show_toast"], display["announce_to_chat"]) == (True, True, False)
    assert display["title"] == {
        "translate": "advancement.realmforge.realm_unlocked",
        "with": [{"translate": "realm.realmforge.stone.name"}],
    }
    assert display["icon"] == {"id": "realmforge:vaultstone"}
    lang = json.loads(files[LANG])
    assert lang["realm.realmforge.stone.name"] == "Undervault"
    assert lang["realm.realmforge.stone.subtitle"] == "Stone Realm"
    assert lang["advancement.realmforge.realm_unlocked"] == "Realm unlocked: %1$s"
    expectations = files[EXPECTATIONS].decode()
    assert (
        '"realmforge:realm/stone",\n                    "realmforge:undervault_signet",\n                    0x7A8FB0)'
        in (expectations)
    )


def test_a_realms_rules_must_require_its_unlock(repo: RepoPaths) -> None:
    rules = repo.content / "dimensions" / "stone.yaml"
    rules.write_text(rules.read_text(encoding="utf-8").split("access:")[0], encoding="utf-8")
    assert any(
        "dimensions/stone.yaml: access: the rules of realmforge:stone (content/dimensions/) need its unlock"
        in p
        for p in problems_for(repo)
    )


def test_a_realm_needs_rules(repo: RepoPaths) -> None:
    (repo.content / "dimensions" / "stone.yaml").unlink()
    assert any("realms/stone.yaml: access: the rules of realmforge:stone" in p for p in problems_for(repo))


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        ({"coordinate_scale": 1.0}, "a realm links to Overworld by a coordinate scale above 1"),
        (
            {
                "attributes": {
                    "minecraft:gameplay/bed_rule": {"can_set_spawn": "always", "can_sleep": "never"}
                }
            },
            "a realm's beds set no spawn and do not explode",
        ),
        (
            {
                "attributes": {
                    "minecraft:gameplay/bed_rule": {
                        "can_set_spawn": "never",
                        "can_sleep": "never",
                        "explodes": True,
                    }
                }
            },
            "a realm's beds set no spawn and do not explode",
        ),
    ],
)
def test_a_realms_dimension_type_links_by_scale_and_has_safe_beds(
    repo: RepoPaths, change: dict[str, object], expected: str
) -> None:
    edit_json(repo, "dimension_type/stone.json", change)
    assert any("dimension_type/stone.json: " in p and expected in p for p in problems_for(repo)), (
        problems_for(repo)
    )


def test_a_realm_names_one_of_our_dimensions_once(repo: RepoPaths) -> None:
    source = (repo.content / "realms" / "stone.yaml").read_text(encoding="utf-8")
    write(repo.content / "realms" / "twin.yaml", source.replace("id: stone", "id: twin"))
    write(
        repo.content / "realms" / "surface.yaml",
        source.replace("id: stone", "id: surface")
        .replace("dimension: realmforge:stone", "dimension: minecraft:the_nether")
        .replace("realmforge:undervault_signet", "realmforge:test_item"),
    )
    problems = problems_for(repo)
    assert any("realms/twin.yaml: dimension: realmforge:stone is already the realm of" in p for p in problems)
    assert any(
        "realms/twin.yaml: signet: realmforge:undervault_signet is already the signet of" in p
        for p in problems
    )
    assert any(
        "realms/surface.yaml: dimension: minecraft:the_nether is not one of our dimensions" in p
        for p in problems
    )


def test_a_realms_icon_is_an_item(repo: RepoPaths) -> None:
    path = repo.content / "realms" / "stone.yaml"
    path.write_text(path.read_text(encoding="utf-8").replace("realmforge:vaultstone", "realmforge:vaultstne"))
    assert any("realms/stone.yaml: icon: unknown id 'realmforge:vaultstne'" in p for p in problems_for(repo))


def test_overworlds_entry_is_no_realm(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{DATA}/realmforge/realm/overworld.json"]) == {
        "dimension": "minecraft:overworld",
        "name": "realm.realmforge.overworld.name",
        "colour": "#5DBB63",
        "sound_pitch": 1.0,
        "arrival": {"y": [64, 310], "floor": "minecraft:stone_bricks", "pocket_radius": 3},
    }
    assert f"{DATA}/advancement/realm/overworld.json" not in files
    assert (
        '"realmforge:overworld",\n                    "minecraft:overworld",' in files[EXPECTATIONS].decode()
    )


def test_overworld_has_no_realm_fields_and_a_realm_needs_them(repo: RepoPaths) -> None:
    overworld = repo.content / "realms" / "overworld.yaml"
    overworld.write_text(
        overworld.read_text(encoding="utf-8")
        + "signet: realmforge:undervault_signet\nlocked_hint: {en_us: Never.}\n",
        encoding="utf-8",
    )
    stone = repo.content / "realms" / "stone.yaml"
    stone.write_text(stone.read_text(encoding="utf-8").replace("signet: realmforge:undervault_signet\n", ""))
    problems = problems_for(repo)
    assert any(
        "realms/overworld.yaml" in p
        and "Overworld is always open and no realm, so it has no locked_hint, signet" in p
        for p in problems
    ), problems
    assert any("realms/stone.yaml" in p and "a realm needs signet" in p for p in problems), problems


def test_a_realms_levels_start_at_its_entry_level(repo: RepoPaths) -> None:
    stone = repo.content / "realms" / "stone.yaml"
    stone.write_text(stone.read_text(encoding="utf-8").replace("levels: [6, 18]", "levels: [5, 61]"))
    assert any(
        "realms/stone.yaml: levels: [5, 61] starts at the entry level of realmforge:stone's rules, 6, and ends at its "
        "max_level, 60, or below" in p
        for p in problems_for(repo)
    )
    stone.write_text(stone.read_text(encoding="utf-8").replace("levels: [5, 61]", "levels: [18, 6]"))
    assert any("levels is [lowest, highest]" in p for p in problems_for(repo))


def test_a_signet_is_one_of_our_items_that_stacks_to_one(repo: RepoPaths) -> None:
    stone = repo.content / "realms" / "stone.yaml"
    stone.write_text(
        stone.read_text(encoding="utf-8").replace("realmforge:undervault_signet", "realmforge:test_item")
    )
    assert any(
        "realms/stone.yaml: signet: a signet stacks to 1: realmforge:test_item stacks to" in p
        for p in problems_for(repo)
    )
    stone.write_text(stone.read_text(encoding="utf-8").replace("realmforge:test_item", "minecraft:compass"))
    assert any(
        "realms/stone.yaml: signet: minecraft:compass is not an item of ours" in p for p in problems_for(repo)
    )


@pytest.mark.parametrize(
    ("old", "new", "expected"),
    [
        ("particle: minecraft:ash", "particle: minecraft:dust", "particle: minecraft:dust is not a particle"),
        ("floor: realmforge:vaultstone", "floor: realmforge:vaultstne", "arrival.floor: unknown id"),
        ("y: [20, 90]", "y: [20, 125]", "arrival.y: a built frame's bottom row stands at y 1 to 122"),
        ("y: [20, 90]", "y: [90, 20]", "y is [lowest, highest]"),
        ('colour: "#7A8FB0"', "colour: slate", "colour"),
    ],
)
def test_invalid_portal_fields_are_rejected(repo: RepoPaths, old: str, new: str, expected: str) -> None:
    stone = repo.content / "realms" / "stone.yaml"
    text = stone.read_text(encoding="utf-8")
    assert old in text
    stone.write_text(text.replace(old, new), encoding="utf-8")
    problems = problems_for(repo)
    assert any("realms/stone.yaml" in p and expected in p for p in problems), problems


def test_a_signets_tooltip_says_it_is_not_consumed_and_it_resists_fire(repo: RepoPaths) -> None:
    java = generate(repo)[ITEMS].decode()
    assert (
        'ITEMS.registerSimpleItem("undervault_signet", properties -> properties.stacksTo(1).fireResistant()'
        in java
    )
    # Matěj, 2026-10-20: one line, no realm name or element.
    assert 'Component.translatable("tooltip.realmforge.signet_not_consumed")' in java
    assert "realm.realmforge.stone.subtitle" not in java
    lang = json.loads(generate(repo)[LANG])
    assert lang["tooltip.realmforge.signet_not_consumed"] == "Does not consume on use"


def test_a_realms_description_and_hint_fill_the_refusal_at_the_sealstone(repo: RepoPaths) -> None:
    files = generate(repo)
    lang = json.loads(files[LANG])
    message = lang["message.realmforge.realm_portal_not_eligible"]
    description = lang["realm.realmforge.stone.description"]
    hint = lang["realm.realmforge.stone.locked_hint"]
    # Matěj's wording, 2026-10-20.
    assert message.replace("%1$s", description).replace("%2$s", hint) == (
        "You are not eligible to open a portal to the stone realm. "
        "Unlocked by completing The Ominous Chief quest."
    )
