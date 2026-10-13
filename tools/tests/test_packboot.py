from __future__ import annotations

from pathlib import Path

import pytest

from realmforge_tools import packboot
from realmforge_tools.packboot import ALLOWED_ERRORS, BootOutcome, boot_problems, parse_log, unexpected_errors
from realmforge_tools.project import REPO_ROOT, RepoPaths
from tests.conftest import write

JAR = "realmforge-0.0.1+mc26.1.2.jar"
FOUND_JAR = (
    "[11Oct2026 21:20:29.596] [main/INFO] [net.neoforged.fml.loading.FMLLoader/]: Building game content classloader:\n"
    f" - realmforge (jar(mods/{JAR}))"
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
    assert [(e.line_number, e.level) for e in entries] == [(1, "INFO"), (3, "ERROR"), (7, "FATAL")]
    assert entries[0].continuation == (f" - realmforge (jar(mods/{JAR}))",)
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
    assert problems[0].startswith("latest.log:3: [ERROR] net.minecraft.server.packs.resources.")
    assert "Missing key cost" in problems[0]
    assert problems[1] == (
        "latest.log:7: [FATAL] net.neoforged.fml.ModLoader: Failed to complete lifecycle event CONSTRUCT"
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
        ({"exit_code": None, "stopped_after_fatal": True}, "the game kept running"),
        ({"report_written": False}, "no self-test report: the game never finished loading"),
        ({"crash_reports": ("crash-2026-10-11_21.20.41-server.txt",)}, "crash report: crash-2026"),
        ({"log": None}, "the game wrote no logs/latest.log"),
        ({"mod_jar_name": "realmforge-9.9.9+mc26.1.2.jar"}, "the core mod jar realmforge-9.9.9"),
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
[11Oct2026 19:43:01.000] [Render thread/ERROR] [com.mojang.text2speech.Narrator/]: Error while loading the narrator
com.mojang.text2speech.Narrator$InitializeException: Failed to load library flite
"""


def test_the_client_allowlist_covers_headless_noise_only_on_the_client() -> None:
    assert boot_problems(_outcome(log=CLIENT_LOG), ALLOWED_ERRORS["client"]) == []
    assert len(boot_problems(_outcome(log=CLIENT_LOG), ALLOWED_ERRORS["server"])) == 3


CLOUD_SERVER_LOG = f"""\
{FOUND_JAR}
[13Oct2026 10:42:53.186] [Yggdrasil Key Fetcher/ERROR] [com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo/]: Failed to request yggdrasil public key
com.mojang.authlib.exceptions.MinecraftClientException: Failed to read from https://api.minecraftservices.com/publickeys due to api.minecraftservices.com
Caused by: java.net.UnknownHostException: api.minecraftservices.com
"""


def test_the_server_allowlist_covers_the_key_fetch_a_cloud_session_cannot_make() -> None:
    assert boot_problems(_outcome(log=CLOUD_SERVER_LOG), ALLOWED_ERRORS["server"]) == []
    assert len(boot_problems(_outcome(log=CLOUD_SERVER_LOG), ALLOWED_ERRORS["client"])) == 1


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


def test_the_pack_has_no_optional_mods() -> None:
    # Quark left with the 26.1.2 port (RLM-124); a new optional mod brings the boot without it back.
    assert packboot.optional_mods(REPO_ROOT / "pack") == []


@pytest.mark.parametrize(
    ("output", "major"),
    [
        ('openjdk version "25.0.4.1" 2026-08-18 LTS\nOpenJDK Runtime Environment Temurin-25.0.4.1+1', 25),
        ('openjdk version "21.0.12" 2026-07-15 LTS', 21),
        ('java version "1.8.0_402"', 8),
        ("no java here", None),
    ],
)
def test_the_java_version_is_read_from_java_version(output: str, major: int | None) -> None:
    assert packboot.java_major_version(output) == major


def test_the_pinned_neoforge_matches_the_pack_and_the_build() -> None:
    assert packboot.pack_neoforge_version(REPO_ROOT / "pack") == packboot.NEOFORGE_VERSION
    assert packboot.gradle_properties(REPO_ROOT)["neo_version"] == packboot.NEOFORGE_VERSION
    assert packboot.NEOFORGE_VERSION in packboot.NEOFORGE_INSTALLER.url


def test_the_default_jar_is_the_one_gradle_builds() -> None:
    jar = packboot.default_mod_jar(RepoPaths())
    assert jar.parent == REPO_ROOT / "mod" / "build" / "libs"
    assert jar.name.startswith("realmforge-") and jar.name.endswith("+mc26.1.2.jar")


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


def test_a_fatal_line_or_a_crash_report_means_the_game_failed_to_load(tmp_path: Path) -> None:
    assert not packboot.failed_to_load(tmp_path)
    write(tmp_path / "logs" / "latest.log", CLEAN_LOG)
    assert not packboot.failed_to_load(tmp_path)
    write(tmp_path / "logs" / "latest.log", ERROR_LOG)
    assert packboot.failed_to_load(tmp_path)
    (tmp_path / "logs" / "latest.log").unlink()
    write(
        tmp_path / "crash-reports" / "crash-2026-10-11_19.43.23-fml.txt", "---- Minecraft Crash Report ----"
    )
    assert packboot.failed_to_load(tmp_path)


def test_a_game_that_hangs_after_a_fatal_error_is_stopped(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    import subprocess
    import sys
    import time

    monkeypatch.setattr(packboot, "FATAL_GRACE_S", 0)
    write(tmp_path / "logs" / "latest.log", ERROR_LOG)
    process = subprocess.Popen(
        [sys.executable, "-c", "import time; time.sleep(120)"], start_new_session=sys.platform != "win32"
    )
    started = time.monotonic()
    assert packboot._wait_for_game(process, tmp_path) == (None, True)
    assert time.monotonic() - started < 30
    assert process.poll() is not None


def test_a_game_that_exits_returns_its_exit_code(tmp_path: Path) -> None:
    import subprocess
    import sys

    process = subprocess.Popen([sys.executable, "-c", "raise SystemExit(3)"])
    assert packboot._wait_for_game(process, tmp_path) == (3, False)
