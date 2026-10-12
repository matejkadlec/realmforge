"""``rf gametest-log``: data loading errors in the GameTest server's log fail the run."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.cli import main
from realmforge_tools.gamelog import GAMETEST_ALLOWED_ERRORS, gametest_problems
from tests.conftest import write

PASSED = (
    "[12Oct2026 09:54:15.072] [Server thread/INFO] [net.minecraft.gametest.framework.GameTestServer/]: "
    "All 15 required tests passed :)\n"
)
HARMLESS = """\
[12Oct2026 09:49:40.203] [main/ERROR] [net.minecraft.server.dedicated.Settings/]: Failed to load properties from file: server.properties
java.nio.file.NoSuchFileException: server.properties
[12Oct2026 09:49:40.439] [Yggdrasil Key Fetcher/ERROR] [com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo/]: Failed to request yggdrasil public key
[12Oct2026 09:49:40.500] [main/WARN] [net.minecraft.commands.Commands/]: Ambiguity between arguments [teleport, location] and [teleport, destination]
"""
BROKEN_RECIPE = (
    "[12Oct2026 09:50:00.000] [Worker-Main-1/ERROR] [net.minecraft.world.item.crafting.RecipeManager/]: "
    "Parsing error loading recipe realmforge:sword\n"
    "com.google.gson.JsonParseException: No key ingredients\n"
)
LOOT_WARNING = (
    "[12Oct2026 09:50:01.000] [Server thread/WARN] [net.minecraft.server.ReloadableServerRegistries/]: "
    "Found loot table element validation problem in realmforge:chests/x: Missing element\n"
)


def test_clean_log_with_known_harmless_errors_passes() -> None:
    assert gametest_problems(HARMLESS + PASSED) == []


def test_data_loading_error_fails_with_its_line_and_trace() -> None:
    (problem,) = gametest_problems(HARMLESS + BROKEN_RECIPE + PASSED)
    assert problem.startswith(
        "latest.log:5: [ERROR] net.minecraft.world.item.crafting.RecipeManager: Parsing error"
    )
    assert "No key ingredients" in problem


def test_loot_validation_warning_fails() -> None:
    (problem,) = gametest_problems(LOOT_WARNING + PASSED)
    assert "[WARN] net.minecraft.server.ReloadableServerRegistries: Found loot table element" in problem


def test_problems_come_in_log_order() -> None:
    problems = gametest_problems(LOOT_WARNING + BROKEN_RECIPE + PASSED)
    assert [p.split(":")[1] for p in problems] == ["1", "2"]


def test_log_of_an_unfinished_run_fails() -> None:
    (problem,) = gametest_problems(HARMLESS)
    assert "no 'All N required tests passed' line" in problem


def test_every_allowlist_entry_has_a_reason() -> None:
    assert all(reason.strip() for _pattern, reason in GAMETEST_ALLOWED_ERRORS)


def test_cli_exit_codes(tmp_path: Path, capsys) -> None:
    clean = write(tmp_path / "clean.log", HARMLESS + PASSED)
    broken = write(tmp_path / "broken.log", BROKEN_RECIPE + PASSED)
    assert main(["gametest-log", "--log", str(clean)]) == 0
    assert main(["gametest-log", "--log", str(broken)]) == 1
    assert main(["gametest-log", "--log", str(tmp_path / "missing.log")]) == 1
    output = capsys.readouterr().out
    assert "OK" in output and "FAIL" in output and "does not exist" in output
