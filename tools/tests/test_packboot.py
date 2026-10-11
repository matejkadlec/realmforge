from __future__ import annotations

from pathlib import Path

import pytest

from realmforge_tools import packboot
from realmforge_tools.packboot import ALLOWED_ERRORS, BootOutcome, boot_problems, parse_log, unexpected_errors
from realmforge_tools.project import REPO_ROOT, RepoPaths
from tests.conftest import write

JAR = "realmforge-0.0.1+mc1.21.1.jar"
FOUND_JAR = (
    f'[11Oct2026 21:20:29.596] [main/INFO] [net.neoforged.fml.loading.moddiscovery.ModDiscoverer/SCAN]: Found mod file "{JAR}"'
    " [locator: {mods folder locator at /srv/mods}, reader: mod manifest]"
)
CLEAN_LOG = f"""\
[11Oct2026 21:20:29.023] [main/INFO] [cpw.mods.modlauncher.Launcher/MODLAUNCHER]: ModLauncher running
{FOUND_JAR}
[11Oct2026 21:20:32.067] [main/WARN] [mixin/]: Error loading class: net/minecraft/client/gui/screens/X (java.lang.ClassNotFoundException: X)
[11Oct2026 21:20:58.073] [Server thread/INFO] [net.neoforged.neoforge.common.util.SelfTest/]: Wrote self-test report
"""
ERROR_LOG = f"""\
{FOUND_JAR}
[11Oct2026 21:20:40.898] [Worker-Main-1/ERROR] [net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener/]: Couldn't parse data file realmforge:spell/firebolt
java.lang.IllegalStateException: Missing key cost
\tat com.mojang.serialization.DataResult.getOrThrow(DataResult.java:1)

[11Oct2026 21:20:41.000] [main/FATAL] [net.neoforged.fml.ModLoader/LOADING]: Failed to complete lifecycle event CONSTRUCT
"""


def _outcome(**overrides) -> BootOutcome:
    values = {
        "exit_code": 0,
        "report_written": True,
        "crash_reports": (),
        "log": CLEAN_LOG,
        "mod_jar_name": JAR,
    }
    return BootOutcome(**{**values, **overrides})


def test_parse_log_groups_stack_traces_with_their_entry() -> None:
    entries = parse_log(ERROR_LOG)
    assert [(e.line_number, e.level) for e in entries] == [(1, "INFO"), (2, "ERROR"), (6, "FATAL")]
    assert entries[1].logger == "net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener/"
    assert entries[1].continuation == (
        "java.lang.IllegalStateException: Missing key cost",
        "\tat com.mojang.serialization.DataResult.getOrThrow(DataResult.java:1)",
    )
    assert entries[2].subject == "net.neoforged.fml.ModLoader: Failed to complete lifecycle event CONSTRUCT"


def test_a_clean_boot_has_no_problems() -> None:
    assert boot_problems(_outcome(), ()) == []


def test_warnings_do_not_fail_a_boot() -> None:
    assert unexpected_errors(parse_log(CLEAN_LOG), ()) == []


def test_error_and_fatal_lines_fail_a_boot_with_their_trace() -> None:
    problems = boot_problems(_outcome(log=ERROR_LOG), ())
    assert len(problems) == 2
    assert problems[0].startswith("latest.log:2: [ERROR] net.minecraft.server.packs.resources.")
    assert "Missing key cost" in problems[0]
    assert problems[1] == (
        "latest.log:6: [FATAL] net.neoforged.fml.ModLoader: Failed to complete lifecycle event CONSTRUCT"
    )


def test_allowlisted_errors_pass() -> None:
    allowed = (
        (r"^net\.minecraft\.server\.packs\.resources\.\w+: Couldn't parse data file realmforge:", "test"),
        (r"lifecycle event CONSTRUCT$", "test"),
    )
    assert boot_problems(_outcome(log=ERROR_LOG), allowed) == []


@pytest.mark.parametrize(
    ("overrides", "expected"),
    [
        ({"exit_code": 1}, "the game exited with code 1"),
        ({"exit_code": None}, f"the game did not stop within {packboot.BOOT_TIMEOUT_S} s and was killed"),
        ({"report_written": False}, "no self-test report: the game never finished loading"),
        ({"crash_reports": ("crash-2026-10-11_21.20.41-server.txt",)}, "crash report: crash-2026"),
        ({"log": None}, "the game wrote no logs/latest.log"),
        ({"mod_jar_name": "realmforge-9.9.9+mc1.21.1.jar"}, "the core mod jar realmforge-9.9.9"),
    ],
)
def test_a_broken_boot_is_reported(overrides: dict, expected: str) -> None:
    problems = boot_problems(_outcome(**overrides), ())
    assert any(problem.startswith(expected) for problem in problems), problems


def test_the_allowlists_cover_both_sides_and_every_entry_has_a_reason() -> None:
    assert set(ALLOWED_ERRORS) == set(packboot.SIDES)
    for allowed in ALLOWED_ERRORS.values():
        for pattern, reason in allowed:
            assert reason.strip(), pattern
            unexpected_errors(parse_log(CLEAN_LOG), ((pattern, reason),))


CLIENT_LOG = f"""\
{FOUND_JAR}
[11Oct2026 21:33:38.671] [Render thread/ERROR] [xaero.map.WorldMap/]: io exception while checking versions: Online mod data expired! Date: Wed Oct 07 12:36:48 CEST 2026
[11Oct2026 21:33:43.428] [Render thread/ERROR] [net.minecraft.client.sounds.SoundEngine/SOUNDS]: Error starting SoundSystem. Turning off sounds & music
"""


def test_the_client_allowlist_covers_headless_noise_only_on_the_client() -> None:
    assert boot_problems(_outcome(log=CLIENT_LOG), ALLOWED_ERRORS["client"]) == []
    assert len(boot_problems(_outcome(log=CLIENT_LOG), ALLOWED_ERRORS["server"])) == 2


def test_the_client_runs_under_xvfb_when_it_exists(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(packboot.shutil, "which", lambda name: f"/usr/bin/{name}")
    assert packboot._headless(["game"]) == ["/usr/bin/xvfb-run", "-a", "-s", packboot.XVFB_SCREEN, "game"]


def test_the_client_needs_xvfb_or_a_display(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(packboot.shutil, "which", lambda name: None)
    monkeypatch.delenv("DISPLAY", raising=False)
    monkeypatch.delenv("WAYLAND_DISPLAY", raising=False)
    with pytest.raises(packboot.PackBootError, match="xvfb-run"):
        packboot._headless(["game"])
    monkeypatch.setenv("DISPLAY", ":0")
    assert packboot._headless(["game"]) == ["game"]


def test_optional_mods_come_from_the_metafiles(tmp_path: Path) -> None:
    write(tmp_path / "mods" / "a.pw.toml", 'name = "A"\nfilename = "a.jar"\n[option]\noptional = true\n')
    write(tmp_path / "mods" / "b.pw.toml", 'name = "B"\nfilename = "b.jar"\n')
    write(tmp_path / "mods" / "c.pw.toml", 'name = "C"\nfilename = "c.jar"\n[option]\noptional = false\n')
    assert packboot.optional_mods(tmp_path) == [packboot.OptionalMod("A", "a.jar")]


def test_quark_is_the_packs_optional_mod() -> None:
    assert [m.name for m in packboot.optional_mods(REPO_ROOT / "pack")] == ["Quark"]


def test_the_pinned_neoforge_matches_the_pack_and_the_build() -> None:
    assert packboot.pack_neoforge_version(REPO_ROOT / "pack") == packboot.NEOFORGE_VERSION
    assert packboot.gradle_properties(REPO_ROOT)["neo_version"] == packboot.NEOFORGE_VERSION
    assert packboot.NEOFORGE_VERSION in packboot.NEOFORGE_INSTALLER.url


def test_the_default_jar_is_the_one_gradle_builds() -> None:
    jar = packboot.default_mod_jar(RepoPaths())
    assert jar.parent == REPO_ROOT / "mod" / "build" / "libs"
    assert jar.name.startswith("realmforge-") and jar.name.endswith("+mc1.21.1.jar")


def test_optional_mods_are_set_aside_and_restored(tmp_path: Path) -> None:
    jar = write(tmp_path / "mods" / "Quark.jar", "jar")
    with packboot.optional_mods_set_aside(tmp_path, [packboot.OptionalMod("Quark", "Quark.jar")]):
        assert not jar.exists()
    assert jar.read_text() == "jar"
    assert not (tmp_path / packboot._ASIDE).exists()


def test_serve_directory_serves_the_pack(tmp_path: Path) -> None:
    import urllib.request

    write(tmp_path / "pack.toml", 'name = "Test"\n')
    with (
        packboot.serve_directory(tmp_path) as url,
        urllib.request.urlopen(f"{url}/pack.toml", timeout=5) as response,
    ):
        assert response.read() == b'name = "Test"\n'


def test_a_download_with_the_wrong_hash_is_refused(tmp_path: Path) -> None:
    source = write(tmp_path / "src" / "file.jar", "payload")
    download = packboot.PinnedDownload("file.jar", source.as_uri(), "0" * 64)
    with pytest.raises(packboot.PackBootError, match="does not match the pinned"):
        packboot.fetch(download, tmp_path / "cache")
    assert not (tmp_path / "cache" / "file.jar").exists()
