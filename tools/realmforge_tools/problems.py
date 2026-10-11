"""Validation problems reported to the user (and to agents via the Claude Code hook)."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, order=True)
class Problem:
    """One validation error, pointing at a file and (optionally) a field inside it."""

    file: str
    location: str
    message: str

    def __str__(self) -> str:
        where = f"{self.file}: {self.location}" if self.location else self.file
        return f"{where}: {self.message}"


class ValidationFailed(Exception):
    """Raised when content cannot be loaded or validated. Carries every problem found, not just the first."""

    def __init__(self, problems: list[Problem]) -> None:
        self.problems = sorted(problems)
        super().__init__("\n".join(str(p) for p in self.problems))
