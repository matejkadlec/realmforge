"""Colours in content (``HexColor``): either case validates, and every generated file gets them in uppercase
(RLM-507), a realm's portal colour included (RLM-599)."""

from __future__ import annotations

import json

import pydantic
import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.spell_school import HexColor
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

SPELL_DATA = "mod/src/generated/resources/data/realmforge/realmforge/spell"
REALM_DATA = "mod/src/generated/resources/data/realmforge/realmforge/realm"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedSpellExpectations.java"
)

COLOURS = pydantic.TypeAdapter(HexColor)


@pytest.mark.parametrize("colour", ["#8c8c8c", "#8C8c8C", "#8C8C8C"])
def test_a_colour_validates_in_either_case_and_is_held_uppercase(colour: str) -> None:
    assert COLOURS.validate_python(colour) == "#8C8C8C"


@pytest.mark.parametrize("colour", ["#8C8C8G", "8C8C8C", "#8C8C8", "#8C8C8C8C"])
def test_a_colour_is_hash_and_six_hex_digits(colour: str) -> None:
    with pytest.raises(pydantic.ValidationError):
        COLOURS.validate_python(colour)


def test_a_lowercase_ring_color_reaches_the_spell_data_and_the_expectations_uppercased(
    repo: RepoPaths,
) -> None:
    # The content GameTests format the colour the game loads as "#%06X", so a lowercase expectation would fail on
    # content that works in the game.
    write(
        repo.content / "spells" / "dust_ring.yaml",
        "id: dust_ring\n"
        "name: {en_us: Dust Ring}\n"
        "description: {en_us: A ring of dust.}\n"
        "school: realmforge:physical\n"
        "scales_with: attack_damage\n"
        "targeting: {type: realmforge:self}\n"
        "cast: {type: instant}\n"
        "npc_only: true\n"
        "effects:\n"
        "  - {type: realmforge:area, radius: 5, duration_seconds: 0.5, interval_seconds: 0.5, ring_color: '#8c8c8c', "
        "effects: [{type: realmforge:damage, amount: 1, attack_damage_percent: 30}]}\n",
    )
    content, _ = load_and_validate(repo)
    files = {repo.rel(path): data for path, data in generate_all(content, repo).items()}
    data = json.loads(files[f"{SPELL_DATA}/dust_ring.json"])
    assert data["effects"][0]["ring_color"] == "#8C8C8C"
    expectations = files[EXPECTATIONS].decode()
    dust_ring = expectations[expectations.index('"realmforge:dust_ring"') :]
    assert 'Map.of("affects", "enemies", "ring_color", "#8C8C8C")' in dust_ring
    assert "#8c8c8c" not in expectations


def test_a_lowercase_realm_colour_reaches_the_realm_data_uppercased(repo: RepoPaths) -> None:
    # A realm's colour uses the same HexColor as every other colour (RLM-599), so content/README's rule holds for it.
    stone = repo.content / "realms" / "stone.yaml"
    text = stone.read_text(encoding="utf-8")
    assert 'colour: "#7A8FB0"' in text
    stone.write_text(text.replace('colour: "#7A8FB0"', 'colour: "#7a8fb0"'), encoding="utf-8")
    content, _ = load_and_validate(repo)
    files = {repo.rel(path): data for path, data in generate_all(content, repo).items()}
    assert json.loads(files[f"{REALM_DATA}/stone.json"])["colour"] == "#7A8FB0"
    assert b"#7a8fb0" not in files[f"{REALM_DATA}/stone.json"]
