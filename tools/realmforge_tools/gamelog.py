"""NeoForge ``latest.log`` checks: shared by ``rf pack-boot`` (packboot.py) and ``rf gametest-log``.

``rf gametest-log`` runs after ``./gradlew runGameTestServer`` (CI's gradle job, and the gate before a pull request).
Vanilla skips a broken loot table, recipe, advancement or tag with an ERROR line and carries on, so the GameTests
alone would pass without it (architecture 6.1); a broken entry of our datapack registries stops the server instead.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from realmforge_tools.project import RepoPaths

_LOG_LINE = re.compile(
    r"^\[(?P<time>[^\]]*)\] \[(?P<thread>[^\]]*)/(?P<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\] "
    r"\[(?P<logger>[^\]]*)\]: (?P<message>.*)$"
)
_TRACE_LINES_SHOWN = 6


@dataclass(frozen=True)
class LogEntry:
    """One log event: its first line and the lines that follow it (stack traces)."""

    line_number: int
    level: str
    logger: str
    message: str
    continuation: tuple[str, ...] = ()

    @property
    def subject(self) -> str:
        """What the allowlists match: the logger without its marker, and the message."""
        return f"{self.logger.split('/', 1)[0]}: {self.message}"

    def describe(self) -> str:
        lines = [f"latest.log:{self.line_number}: [{self.level}] {self.subject}"]
        lines += [f"    {line}" for line in self.continuation[:_TRACE_LINES_SHOWN]]
        if len(self.continuation) > _TRACE_LINES_SHOWN:
            lines.append(f"    ... {len(self.continuation) - _TRACE_LINES_SHOWN} more line(s)")
        return "\n".join(lines)


def parse_log(text: str) -> list[LogEntry]:
    """Split a NeoForge ``latest.log`` into entries; lines that do not start an entry continue the previous one."""
    entries: list[LogEntry] = []
    current: dict | None = None
    for number, line in enumerate(text.splitlines(), start=1):
        match = _LOG_LINE.match(line)
        if match:
            if current is not None:
                entries.append(_entry(current))
            current = {
                "line_number": number,
                "level": match["level"],
                "logger": match["logger"],
                "message": match["message"],
                "continuation": [],
            }
        elif current is not None and line.strip():
            current["continuation"].append(line)
    if current is not None:
        entries.append(_entry(current))
    return entries


def _entry(data: dict) -> LogEntry:
    return LogEntry(**{**data, "continuation": tuple(data["continuation"])})


def unexpected_errors(entries: list[LogEntry], allowed: tuple[tuple[str, str], ...]) -> list[LogEntry]:
    """ERROR and FATAL entries that no allowlist pattern matches."""
    patterns = [re.compile(pattern) for pattern, _reason in allowed]
    return [
        entry
        for entry in entries
        if entry.level in ("ERROR", "FATAL") and not any(p.search(entry.subject) for p in patterns)
    ]


def matching(entries: list[LogEntry], level: str, patterns: tuple[tuple[str, str], ...]) -> list[LogEntry]:
    """``level`` entries that a pattern matches."""
    compiled = [re.compile(pattern) for pattern, _reason in patterns]
    return [
        entry for entry in entries if entry.level == level and any(p.search(entry.subject) for p in compiled)
    ]


# Known harmless ERROR lines of the GameTest server, matched like packboot.ALLOWED_ERRORS. Every entry needs a reason.
GAMETEST_ALLOWED_ERRORS: tuple[tuple[str, str], ...] = (
    (
        r"^net\.minecraft\.server\.dedicated\.Settings: Failed to load properties from file: server\.properties$",
        "the GameTest server reads server.properties before writing it, so a fresh run directory (CI) has none yet",
    ),
    (
        r"^com\.mojang\.authlib\.yggdrasil\.YggdrasilServicesKeyInfo: Failed to request yggdrasil public key",
        "the server fetches Mojang's public keys over the network; sandboxes and some CI runners have no route",
    ),
    (
        r"^com\.mojang\.authlib\.minecraft\.client\.MinecraftClient: Got an error with a html body connecting to"
        r" https://api\.minecraftservices\.com/publickeys:",
        "the same key fetch when Mojang's service answers with an error page instead of the keys (as in"
        " packboot.ALLOWED_ERRORS); an error page from any other Mojang URL stays an error",
    ),
    (
        r"^io\.github\.matejkadlec\.realmforge\.dungeon\.instance\.InstanceManager: Instance plot \d+ failed to "
        r"build \(.*\): unknown room template realmforge_qa:gametest/(portal_)?gone; releasing it$",
        "InstanceGameTests.aBuildWhoseTemplateIsGoneIsReleased and DungeonPortalGameTests"
        ".aKeyComesBackWhenItsDungeonCannotBeBuilt remove those room templates on purpose; any other failed build "
        "stays an error",
    ),
    (
        r"^snownee\.jade\.Jade: Failed to collect shearable blocks$",
        "the GameTest server loads the pack's server-side mods (RLM-279), and Jade 26.1.10 hits this on every dedicated"
        " server, as packboot.ALLOWED_ERRORS records",
    ),
    (
        r"^net\.neoforged\.neoforge\.common\.OnlyInWarningsHandler: (The mod (?!realmforge)\S+ uses the @OnlyIn "
        r"annotation|@OnlyIn used on \w+ (for )?(?!io\.github\.matejkadlec\.)\S+$)",
        "NeoForge lists, only outside production, every pack mod that still uses @OnlyIn (Jade Addons); a use in our"
        " own mods stays an error",
    ),
)

# WARN lines that are data errors: vanilla reports these only as warnings, but they mean broken content.
GAMETEST_FAILING_WARNINGS: tuple[tuple[str, str], ...] = (
    (
        r"^net\.minecraft\.server\.ReloadableServerRegistries: Found loot table element validation problem",
        "a loot table that names a missing table, item or function loads, but drops nothing where the name is",
    ),
)

_TESTS_PASSED = re.compile(r"All \d+ required tests passed")


def gametest_log(paths: RepoPaths) -> Path:
    """Where ``./gradlew runGameTestServer`` writes its log (``qa-mod/build.gradle``, run ``gameTestServer``)."""
    return paths.root / "run" / "gametest" / "logs" / "latest.log"


def gametest_problems(text: str) -> list[str]:
    """Everything in a GameTest server log that fails the run, as messages in log order; empty for a clean run."""
    entries = parse_log(text)
    failing = {
        entry.line_number
        for entry in unexpected_errors(entries, GAMETEST_ALLOWED_ERRORS)
        + matching(entries, "WARN", GAMETEST_FAILING_WARNINGS)
    }
    problems = [entry.describe() for entry in entries if entry.line_number in failing]
    if not _TESTS_PASSED.search(text):
        problems.append(
            "no 'All N required tests passed' line: the log is not from a finished, passing GameTest run"
        )
    return problems
