"""Client isolation check (architecture 18): only code under a mod's ``client/`` package may use client classes.

A dedicated server has no client classes, so a reference to one anywhere else crashes it as soon as that code runs.
NeoForge marks few of its client classes client-only, so the GameTest server alone would miss most of them. The
check scans Java sources for the banned packages, in imports, static imports and fully qualified names alike, after
blanking comments, string literals and character literals.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from realmforge_tools.project import JAVA_PACKAGE, QA_JAVA_PACKAGE, RepoPaths

BANNED_PACKAGES = ("net.minecraft.client", "com.mojang.blaze3d", "net.neoforged.neoforge.client")

_BANNED = re.compile(
    r"(?<![\w.])(" + "|".join(re.escape(p) for p in BANNED_PACKAGES) + r")(?![\w])",
)


@dataclass(frozen=True)
class SourceRoot:
    """A Java source directory and the one package below it whose code may use client classes."""

    path: Path
    client_package: str

    def allows_client_code(self, source: Path) -> bool:
        client_dir = self.path.joinpath(*self.client_package.split("."))
        return source.is_relative_to(client_dir)


@dataclass(frozen=True)
class Violation:
    path: Path
    line: int
    reference: str

    def describe(self, paths: RepoPaths) -> str:
        return f"{paths.rel(self.path)}:{self.line}: uses {self.reference} outside client/"


def source_roots(paths: RepoPaths) -> list[SourceRoot]:
    """Every source set that ends up in a mod jar, with its mod's client package."""
    roots = []
    for project, package in (("mod", JAVA_PACKAGE), ("qa-mod", QA_JAVA_PACKAGE)):
        for source_set in ("main", "generated"):
            roots.append(SourceRoot(paths.root / project / "src" / source_set / "java", f"{package}.client"))
    return roots


def find_violations(roots: list[SourceRoot]) -> list[Violation]:
    violations = []
    for root in roots:
        if not root.path.is_dir():
            continue
        for source in sorted(root.path.rglob("*.java")):
            if root.allows_client_code(source):
                continue
            for line, reference in banned_references(source.read_text(encoding="utf-8")):
                violations.append(Violation(source, line, reference))
    return violations


def banned_references(java_source: str) -> list[tuple[int, str]]:
    """``(line, package)`` for every use of a banned package in the code (not in comments or literals)."""
    code = strip_comments_and_literals(java_source)
    return [
        (number, match.group(1))
        for number, line in enumerate(code.splitlines(), start=1)
        for match in _BANNED.finditer(line)
    ]


def strip_comments_and_literals(java_source: str) -> str:
    """Replace comments, string literals, text blocks and char literals by spaces, keeping every line break."""
    out: list[str] = []
    i, n = 0, len(java_source)

    def blank(text: str) -> None:
        out.append("".join(c if c == "\n" else " " for c in text))

    while i < n:
        if java_source.startswith("//", i):
            end = java_source.find("\n", i)
            end = n if end == -1 else end
        elif java_source.startswith("/*", i):
            end = java_source.find("*/", i + 2)
            end = n if end == -1 else end + 2
        elif java_source.startswith('"""', i):
            end = java_source.find('"""', i + 3)
            while end != -1 and _escaped(java_source, end):
                end = java_source.find('"""', end + 1)
            end = n if end == -1 else end + 3
        elif java_source[i] in "\"'":
            end = _literal_end(java_source, i)
        else:
            out.append(java_source[i])
            i += 1
            continue
        blank(java_source[i:end])
        i = end
    return "".join(out)


def _literal_end(text: str, start: int) -> int:
    quote = text[start]
    i = start + 1
    while i < len(text) and text[i] not in (quote, "\n"):
        i += 2 if text[i] == "\\" else 1
    return min(i + 1, len(text))


def _escaped(text: str, index: int) -> bool:
    backslashes = 0
    while index - backslashes - 1 >= 0 and text[index - backslashes - 1] == "\\":
        backslashes += 1
    return backslashes % 2 == 1
