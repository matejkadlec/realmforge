"""Boot the whole modpack with the core mod and fail on errors (``rf pack-boot``, the CI jobs ``pack-boot`` and
``pack-boot-client``; ADR 0018).

1. Install the game side: the NeoForge server from its sha256-pinned installer into ``.cache/pack-boot/server/``,
   or a NeoForge client through the pinned portablemc launcher into ``.cache/pack-boot/client/`` (game directory)
   and ``.cache/pack-boot/launcher/`` (versions, libraries, assets).
2. Install the pack's mods for that side with packwiz-installer (``-g -s server`` or ``-s client``), served from
   ``pack/`` by a local HTTP server, the way a server owner or a player installs it.
3. Copy the core mod jar from ``mod/build/libs/`` into ``mods/``.
4. Start the game with NeoForge's self test (``NEOFORGE_DEDICATED_SERVER_SELFTEST`` or ``NEOFORGE_CLIENT_SELFTEST``),
   which writes a report file and stops the game at the server's first tick or once the client's title screen
   ticks. The client runs under Xvfb when ``xvfb-run`` exists. Each side boots twice: with the optional mods, which
   packwiz-installer's command line always installs, and without them, the default choice for players
   (pack/README.md, "Optional mods").
5. Fail on a non-zero exit, a missing self-test report, a crash report, a core mod that was not loaded, or an
   ERROR or FATAL line in ``logs/latest.log`` that the side's allowlist does not cover. A game that keeps running
   after a fatal error (the client then waits on FML's error screen) is stopped instead of waiting for the timeout.
"""

from __future__ import annotations

import hashlib
import http.server
import os
import shutil
import signal
import socket
import subprocess
import threading
import time
import tomllib
import urllib.request
from collections.abc import Iterator
from contextlib import contextmanager, suppress
from dataclasses import dataclass, field
from functools import partial
from pathlib import Path

from realmforge_tools.gamelog import parse_log, unexpected_errors
from realmforge_tools.project import RepoPaths

NEOFORGE_VERSION = "21.1.259"
SIDES = ("server", "client")
VARIANTS = ("with-optional", "without-optional")


@dataclass(frozen=True)
class PinnedDownload:
    name: str
    url: str
    sha256: str


NEOFORGE_INSTALLER = PinnedDownload(
    name=f"neoforge-{NEOFORGE_VERSION}-installer.jar",
    url=(
        "https://maven.neoforged.net/releases/net/neoforged/neoforge/"
        f"{NEOFORGE_VERSION}/neoforge-{NEOFORGE_VERSION}-installer.jar"
    ),
    sha256="bb971287ab44fc43f27da77ac34e263a3885d73fb842ceeb20ee5c517ff4378c",
)
# packwiz-installer only runs through its bootstrap; --bootstrap-no-update keeps it on the pinned jar.
PACKWIZ_INSTALLER_BOOTSTRAP = PinnedDownload(
    name="packwiz-installer-bootstrap-0.0.3.jar",
    url="https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/packwiz-installer-bootstrap.jar",
    sha256="a8fbb24dc604278e97f4688e82d3d91a318b98efc08d5dbfcbcbcab6443d116c",
)
PACKWIZ_INSTALLER = PinnedDownload(
    name="packwiz-installer-0.5.14.jar",
    url="https://github.com/packwiz/packwiz-installer/releases/download/v0.5.14/packwiz-installer.jar",
    sha256="c9f646908d340d84773948a9a7d98bc1dae250d35e1016dc6e2b8459760b5598",
)
# A command-line Minecraft launcher (GPL-3.0-only), run through uvx as a separate tool, never imported or shipped.
# It installs the vanilla client, NeoForge and the assets, and starts the game with an offline profile.
PORTABLEMC = "portablemc==4.4.1"

# Known harmless ERROR or FATAL lines, matched against "<logger>: <message>" of the line. Keep these short: every
# entry needs a reason, and an entry that no longer matches anything should go.
ALLOWED_ERRORS: dict[str, tuple[tuple[str, str], ...]] = {
    "server": (),
    "client": (
        (
            r"^xaero\.map\.WorldMap: io exception while checking versions",
            "Xaero's World Map reads version data from its website; the check depends on the network",
        ),
        (
            r"^net\.minecraft\.client\.sounds\.SoundEngine: Error starting SoundSystem",
            "a headless machine has no audio device; the game turns sound off and carries on",
        ),
        (
            r"^com\.mojang\.text2speech\.Narrator: Error while loading the narrator",
            "the narrator needs the flite text-to-speech library, which CI runners lack; the game runs without it",
        ),
    ),
}

SERVER_HEAP = "-Xmx3G"
CLIENT_HEAP = "-Xmx4G"
BOOT_TIMEOUT_S = 600
# After a loading error the client waits on FML's error screen forever, so a boot that logged a FATAL line or wrote
# a crash report is stopped this many seconds later instead of at the timeout.
FATAL_GRACE_S = 10
INSTALL_TIMEOUT_S = 1800
# The fixed seed keeps the generated spawn area, and so the worldgen code that runs, the same on every boot.
SERVER_PROPERTIES = """\
# Written by `rf pack-boot` before every boot.
level-seed=realmforge
server-ip=127.0.0.1
server-port={port}
"""
XVFB_SCREEN = "-screen 0 1280x720x24"


@dataclass(frozen=True)
class BootOutcome:
    """What one boot left behind, gathered after the game process ended."""

    exit_code: int | None  # None: killed, after a fatal error or the timeout
    report_written: bool
    crash_reports: tuple[str, ...]
    log: str | None  # None: the game wrote no latest.log
    mod_jar_name: str
    stopped_after_fatal: bool = False


def boot_problems(outcome: BootOutcome, allowed: tuple[tuple[str, str], ...]) -> list[str]:
    """Everything that makes a boot fail, as messages; an empty list is a clean boot."""
    problems: list[str] = []
    if outcome.exit_code is None and outcome.stopped_after_fatal:
        problems.append(f"the game kept running {FATAL_GRACE_S} s after a fatal error and was stopped")
    elif outcome.exit_code is None:
        problems.append(f"the game did not stop within {BOOT_TIMEOUT_S} s and was killed")
    elif outcome.exit_code != 0:
        problems.append(f"the game exited with code {outcome.exit_code}")
    if not outcome.report_written:
        problems.append("no self-test report: the game never finished loading")
    problems += [f"crash report: {name}" for name in outcome.crash_reports]
    if outcome.log is None:
        problems.append("the game wrote no logs/latest.log")
        return problems
    if f'Found mod file "{outcome.mod_jar_name}"' not in outcome.log:
        problems.append(f"the core mod jar {outcome.mod_jar_name} was not loaded")
    problems += [entry.describe() for entry in unexpected_errors(parse_log(outcome.log), allowed)]
    return problems


@dataclass(frozen=True)
class OptionalMod:
    name: str
    filename: str


def optional_mods(pack_dir: Path) -> list[OptionalMod]:
    """Mods marked ``[option] optional = true`` in the pack's metafiles."""
    mods = []
    for metafile in sorted((pack_dir / "mods").glob("*.pw.toml")):
        data = tomllib.loads(metafile.read_text(encoding="utf-8"))
        if data.get("option", {}).get("optional", False):
            mods.append(OptionalMod(name=data["name"], filename=data["filename"]))
    return mods


def pack_neoforge_version(pack_dir: Path) -> str:
    return tomllib.loads((pack_dir / "pack.toml").read_text(encoding="utf-8"))["versions"]["neoforge"]


def gradle_properties(root: Path) -> dict[str, str]:
    properties = {}
    for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    return properties


def default_mod_jar(paths: RepoPaths) -> Path:
    """The jar ``./gradlew :mod:jar`` builds for the version in gradle.properties."""
    properties = gradle_properties(paths.root)
    name = f"realmforge-{properties['mod_version']}+mc{properties['minecraft_version']}.jar"
    return paths.root / "mod" / "build" / "libs" / name


@dataclass
class BootReport:
    label: str
    seconds: float
    problems: list[str] = field(default_factory=list)


class PackBootError(RuntimeError):
    """A step before the boot itself failed (a download, the game install, the pack install)."""


@dataclass(frozen=True)
class _Workspace:
    root: Path

    @property
    def downloads(self) -> Path:
        return self.root / "downloads"

    def game(self, side: str) -> Path:
        return self.root / side

    @property
    def launcher(self) -> Path:
        return self.root / "launcher"

    def reports(self, side: str) -> Path:
        return self.root / "reports" / side


def run(
    paths: RepoPaths,
    *,
    side: str = "server",
    mod_jar: Path | None = None,
    pack_dir: Path | None = None,
    variants: tuple[str, ...] = VARIANTS,
    log=None,
) -> list[BootReport]:
    """Install one side, boot it once per variant and return a report per boot. Raises ``PackBootError``."""
    log = log or partial(print, flush=True)
    if side not in SIDES:
        raise ValueError(f"unknown side {side}")
    pack_dir = pack_dir or paths.root / "pack"
    mod_jar = mod_jar or default_mod_jar(paths)
    if not mod_jar.is_file():
        raise PackBootError(f"core mod jar {paths.rel(mod_jar)} not found; build it with ./gradlew :mod:jar")
    pack_version = pack_neoforge_version(pack_dir)
    if pack_version != NEOFORGE_VERSION:
        raise PackBootError(
            f"pack.toml wants NeoForge {pack_version}, but packboot.py pins {NEOFORGE_VERSION}; update the pin"
        )

    work = _Workspace(paths.cache / "pack-boot")
    game = work.game(side)
    command = _server_command(work) if side == "server" else _headless(_client_command(work, dry=False))
    game.mkdir(parents=True, exist_ok=True)
    shutil.rmtree(work.reports(side), ignore_errors=True)
    work.reports(side).mkdir(parents=True)

    if side == "server":
        _install_server(work, log)
    else:
        log(f"Installing the NeoForge {NEOFORGE_VERSION} client with portablemc ...")
        _run_step(
            _client_command(work, dry=True),
            cwd=game,
            log_file=work.reports(side) / "launcher.log",
            what="portablemc",
        )

    log(f"Installing the pack's {side} side with packwiz-installer ...")
    optional = optional_mods(pack_dir)
    _restore_optional_mods(game)  # a run that died during a "without" boot left them aside
    _install_pack(work, side, pack_dir)
    for old in (game / "mods").glob("realmforge-*.jar"):
        old.unlink()
    shutil.copy2(mod_jar, game / "mods" / mod_jar.name)

    results = []
    for variant in variants:
        if variant == "with-optional":
            label = f"{side} with optional mods" + (
                f" ({', '.join(m.name for m in optional)})" if optional else ""
            )
            aside: list[OptionalMod] = []
        elif variant == "without-optional":
            label, aside = f"{side} without optional mods", optional
        else:
            raise ValueError(f"unknown variant {variant}")
        log(f"Booting the {label} ...")
        with optional_mods_set_aside(game, aside):
            results.append(
                boot(
                    command,
                    side=side,
                    game_dir=game,
                    label=label,
                    mod_jar_name=mod_jar.name,
                    reports=work.reports(side) / variant,
                )
            )
    return results


def boot(
    command: list[str], *, side: str, game_dir: Path, label: str, mod_jar_name: str, reports: Path
) -> BootReport:
    """Start the installed game once with the self test, then judge what it left behind."""
    for leftover in ("world", "logs", "crash-reports"):
        shutil.rmtree(game_dir / leftover, ignore_errors=True)
    report_file = game_dir / "selftest-report"
    report_file.unlink(missing_ok=True)
    if side == "server":
        (game_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
        (game_dir / "server.properties").write_text(
            SERVER_PROPERTIES.format(port=_free_port()), encoding="utf-8"
        )
    report_env = "NEOFORGE_DEDICATED_SERVER_SELFTEST" if side == "server" else "NEOFORGE_CLIENT_SELFTEST"
    env = {**os.environ, report_env: str(report_file.resolve())}

    reports.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    with (reports / "console.log").open("wb") as console:
        # Its own session, so stopping it also stops what it started (xvfb-run starts Xvfb, uvx the launcher).
        process = subprocess.Popen(
            command,
            cwd=game_dir,
            env=env,
            stdin=subprocess.DEVNULL,
            stdout=console,
            stderr=subprocess.STDOUT,
            start_new_session=os.name == "posix",
        )
        exit_code, stopped_after_fatal = _wait_for_game(process, game_dir)
    seconds = time.monotonic() - started

    latest_log = game_dir / "logs" / "latest.log"
    crash_dir = game_dir / "crash-reports"
    crash_reports = tuple(sorted(p.name for p in crash_dir.glob("*"))) if crash_dir.is_dir() else ()
    for path in [latest_log, *(crash_dir / name for name in crash_reports)]:
        if path.is_file():
            shutil.copy2(path, reports / path.name)
    outcome = BootOutcome(
        exit_code=exit_code,
        report_written=report_file.is_file(),
        crash_reports=crash_reports,
        log=latest_log.read_text(encoding="utf-8", errors="replace") if latest_log.is_file() else None,
        mod_jar_name=mod_jar_name,
        stopped_after_fatal=stopped_after_fatal,
    )
    return BootReport(label=label, seconds=seconds, problems=boot_problems(outcome, ALLOWED_ERRORS[side]))


def _wait_for_game(process: subprocess.Popen, game_dir: Path) -> tuple[int | None, bool]:
    """Wait until the game exits; stop it after the timeout, or soon after it failed to load.

    Returns the exit code (None when it was stopped) and whether it was stopped after a fatal error.
    """
    deadline = time.monotonic() + BOOT_TIMEOUT_S
    failed_at: float | None = None
    while True:
        try:
            return process.wait(timeout=2), False
        except subprocess.TimeoutExpired:
            pass
        now = time.monotonic()
        if failed_at is None and failed_to_load(game_dir):
            failed_at = now
        if (failed_at is not None and now - failed_at >= FATAL_GRACE_S) or now >= deadline:
            _stop(process)
            return None, failed_at is not None


def failed_to_load(game_dir: Path) -> bool:
    """Whether the running game wrote a crash report or a FATAL log line, after which it will not load."""
    crash_dir = game_dir / "crash-reports"
    if crash_dir.is_dir() and any(crash_dir.iterdir()):
        return True
    latest_log = game_dir / "logs" / "latest.log"
    return latest_log.is_file() and b"/FATAL] [" in latest_log.read_bytes()


def _stop(process: subprocess.Popen) -> None:
    if os.name == "posix":
        with suppress(ProcessLookupError):
            os.killpg(process.pid, signal.SIGKILL)
    else:
        process.kill()
    process.wait()


def _install_server(work: _Workspace, log) -> None:
    server = work.game("server")
    if _neoforge_args_file(server).is_file():
        return
    log(f"Installing the NeoForge {NEOFORGE_VERSION} server ...")
    installer = fetch(NEOFORGE_INSTALLER, work.downloads)
    _run_step(
        [java_executable(), "-jar", str(installer.resolve()), "--installServer", str(server.resolve())],
        cwd=work.downloads,
        log_file=work.reports("server") / "neoforge-installer.log",
        what="the NeoForge server installer",
    )


def _install_pack(work: _Workspace, side: str, pack_dir: Path) -> None:
    bootstrap = fetch(PACKWIZ_INSTALLER_BOOTSTRAP, work.downloads)
    installer = fetch(PACKWIZ_INSTALLER, work.downloads)
    with serve_directory(pack_dir) as pack_url:
        _run_step(
            [
                java_executable(),
                "-jar",
                str(bootstrap.resolve()),
                "--bootstrap-no-update",
                "--bootstrap-main-jar",
                str(installer.resolve()),
                "-g",
                "-s",
                side,
                f"{pack_url}/pack.toml",
            ],
            cwd=work.game(side),
            log_file=work.reports(side) / "packwiz-installer.log",
            what="packwiz-installer",
        )


def _server_command(work: _Workspace) -> list[str]:
    server = work.game("server")
    return [java_executable(), SERVER_HEAP, f"@{_neoforge_args_file(server).relative_to(server)}", "nogui"]


def _client_command(work: _Workspace, *, dry: bool) -> list[str]:
    uvx = shutil.which("uvx")
    if uvx is None:
        raise PackBootError("the client boot runs portablemc through uvx, which is not on PATH")
    command = [
        uvx,
        PORTABLEMC,
        "--output",
        "human",
        "--main-dir",
        str(work.launcher.resolve()),
        "--work-dir",
        str(work.game("client").resolve()),
        "start",
        f"neoforge:{NEOFORGE_VERSION}",
        "--jvm",
        java_executable(),
        f"--jvm-args={CLIENT_HEAP}",
        "-u",
        "Dev",
    ]
    return [*command, "--dry"] if dry else command


def _headless(command: list[str]) -> list[str]:
    """Run under a virtual display when ``xvfb-run`` exists, otherwise on the current display."""
    xvfb_run = shutil.which("xvfb-run")
    if xvfb_run:
        return [xvfb_run, "-a", "-s", XVFB_SCREEN, *command]
    if os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY"):
        return command
    raise PackBootError("the client boot needs xvfb-run (apt install xvfb) or a display; try --side server")


_ASIDE = ".optional-mods-aside"


@contextmanager
def optional_mods_set_aside(game_dir: Path, mods: list[OptionalMod]) -> Iterator[None]:
    """Move the optional mods' jars out of ``mods/`` for one boot, and back afterwards."""
    aside = game_dir / _ASIDE
    aside.mkdir(exist_ok=True)
    for mod in mods:
        jar = game_dir / "mods" / mod.filename
        if jar.is_file():
            jar.rename(aside / mod.filename)
    try:
        yield
    finally:
        _restore_optional_mods(game_dir)


def _restore_optional_mods(game_dir: Path) -> None:
    aside = game_dir / _ASIDE
    if not aside.is_dir():
        return
    for jar in aside.iterdir():
        jar.rename(game_dir / "mods" / jar.name)
    aside.rmdir()


@contextmanager
def serve_directory(directory: Path) -> Iterator[str]:
    """Serve ``directory`` read-only over HTTP on a free local port; yields the base URL."""

    class QuietHandler(http.server.SimpleHTTPRequestHandler):
        def log_message(self, format: str, *args: object) -> None:
            pass

    handler = partial(QuietHandler, directory=str(directory.resolve()))
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_address[1]}"
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


def fetch(download: PinnedDownload, cache_dir: Path) -> Path:
    """Download (or reuse) a pinned file, verifying its sha256."""
    target = cache_dir / download.name
    if target.is_file() and _sha256(target.read_bytes()) == download.sha256:
        return target
    with urllib.request.urlopen(download.url, timeout=120) as response:
        data = response.read()
    actual = _sha256(data)
    if actual != download.sha256:
        raise PackBootError(f"{download.url}: sha256 {actual} does not match the pinned {download.sha256}")
    cache_dir.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return target


def java_executable() -> str:
    java_home = os.environ.get("JAVA_HOME")
    return str(Path(java_home) / "bin" / "java") if java_home else "java"


def _neoforge_args_file(server: Path) -> Path:
    name = "win_args.txt" if os.name == "nt" else "unix_args.txt"
    return server / "libraries" / "net" / "neoforged" / "neoforge" / NEOFORGE_VERSION / name


def _run_step(command: list[str], *, cwd: Path, log_file: Path, what: str) -> None:
    log_file.parent.mkdir(parents=True, exist_ok=True)
    cwd.mkdir(parents=True, exist_ok=True)
    with log_file.open("wb") as output:
        try:
            result = subprocess.run(
                command,
                cwd=cwd,
                stdin=subprocess.DEVNULL,
                stdout=output,
                stderr=subprocess.STDOUT,
                timeout=INSTALL_TIMEOUT_S,
                check=False,
            )
        except subprocess.TimeoutExpired as e:
            raise PackBootError(f"{what} did not finish within {INSTALL_TIMEOUT_S} s; see {log_file}") from e
    if result.returncode != 0:
        tail = log_file.read_text(encoding="utf-8", errors="replace").splitlines()[-20:]
        raise PackBootError(f"{what} failed with exit code {result.returncode}:\n" + "\n".join(tail))


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()
