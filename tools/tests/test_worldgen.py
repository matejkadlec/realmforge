"""content/worldgen/: validated and copied unchanged (architecture 15.1)."""

from __future__ import annotations

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.worldgen import WORLDGEN_REGISTRIES
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

DATA = "mod/src/generated/resources/data/realmforge"
DIMENSION = '{\n  "type": "minecraft:overworld",\n  "generator": {"type": "minecraft:debug"}\n}\n'


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def test_files_are_copied_byte_for_byte_into_their_registry_folders(repo: RepoPaths) -> None:
    write(repo.content / "worldgen" / "dimension" / "shadowlands.json", DIMENSION)
    biome = '{"has_precipitation": false}  \n'
    write(repo.content / "worldgen" / "biome" / "ashen_waste.json", biome)
    files = generate(repo)
    assert files[f"{DATA}/dimension/shadowlands.json"] == DIMENSION.encode()
    assert files[f"{DATA}/worldgen/biome/ashen_waste.json"] == biome.encode()


def test_every_vanilla_datapack_worldgen_registry_has_a_directory(repo: RepoPaths) -> None:
    # The data-driven worldgen registries of 1.21.1 (RegistryDataLoader.WORLDGEN_REGISTRIES); the rest of the
    # minecraft:worldgen/* registries in the ID universe are static type registries (feature, carver, ...).
    datapack = {
        "biome",
        "configured_carver",
        "configured_feature",
        "placed_feature",
        "structure",
        "structure_set",
        "processor_list",
        "template_pool",
        "noise_settings",
        "noise",
        "density_function",
        "world_preset",
        "flat_level_generator_preset",
        "multi_noise_biome_source_parameter_list",
    }
    assert {d for d, r in WORLDGEN_REGISTRIES.items() if r.startswith("minecraft:worldgen/")} == datapack
    write(repo.content / "worldgen" / "world_preset" / "realms.json", "{}")
    assert f"{DATA}/worldgen/world_preset/realms.json" in generate(repo)


def test_worldgen_ids_join_the_universe(repo: RepoPaths) -> None:
    write(repo.content / "worldgen" / "dimension_type" / "dusk.json", "{}")
    write(
        repo.content / "worldgen" / "dimension" / "shadowlands.json",
        DIMENSION.replace("minecraft:overworld", "realmforge:dusk"),
    )
    content, universe = load_and_validate(repo)
    assert [f.id for f in content.worldgen] == ["shadowlands", "dusk"]
    assert universe.contains("minecraft:dimension", "realmforge:shadowlands")
    assert universe.contains("minecraft:dimension_type", "realmforge:dusk")


def test_dimension_may_use_the_core_mods_dimension_type(repo: RepoPaths) -> None:
    write(repo.mod_resources / "data" / "realmforge" / "dimension_type" / "instances.json", "{}")
    write(
        repo.content / "worldgen" / "dimension" / "a.json",
        DIMENSION.replace("minecraft:overworld", "realmforge:instances"),
    )
    load_and_validate(repo)


def test_unknown_dimension_type_is_reported_with_suggestion(repo: RepoPaths) -> None:
    write(repo.content / "worldgen" / "dimension" / "a.json", DIMENSION.replace("overworld", "overwold"))
    (problem,) = problems_for(repo)
    assert "worldgen/dimension/a.json: type: unknown id 'minecraft:overwold'" in problem
    assert "did you mean: minecraft:overworld" in problem


def test_dimension_needs_a_type(repo: RepoPaths) -> None:
    write(repo.content / "worldgen" / "dimension" / "a.json", '{"generator": {}}')
    assert any("worldgen/dimension/a.json: type: a dimension needs a type" in p for p in problems_for(repo))


def test_clash_with_the_core_mods_resources_is_reported(repo: RepoPaths) -> None:
    write(repo.mod_resources / "data" / "realmforge" / "dimension" / "instances.json", "{}")
    write(repo.content / "worldgen" / "dimension" / "instances.json", DIMENSION)
    assert any(
        "realmforge:instances already exists in the core mod's resources" in p for p in problems_for(repo)
    )


@pytest.mark.parametrize(
    ("rel", "text", "expected"),
    [
        ("worldgen/biomes/a.json", "{}", "unknown worldgen location"),
        ("worldgen/a.json", "{}", "unknown worldgen location"),
        ("worldgen/biome/nested/a.json", "{}", "unknown worldgen location"),
        ("worldgen/biome/a.yaml", "{}", "only .json files are allowed"),
        ("worldgen/biome/Bad.json", "{}", "is not a lowercase snake_case id"),
        ("worldgen/biome/a.json", "{", "invalid JSON"),
        ("worldgen/biome/a.json", "[]", "expected a JSON object"),
    ],
)
def test_bad_worldgen_files_are_reported(repo: RepoPaths, rel: str, text: str, expected: str) -> None:
    write(repo.content / rel, text)
    assert any(expected in p for p in problems_for(repo)), problems_for(repo)
