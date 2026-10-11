"""Project-wide constants and well-known repository paths."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

MOD_ID = "realmforge"
QA_MOD_ID = "realmforge_qa"
JAVA_PACKAGE = "io.github.matejkadlec.realmforge"
QA_JAVA_PACKAGE = f"{JAVA_PACKAGE}.qa"

MINECRAFT_VERSION = "1.21.1"
# World data version of MINECRAFT_VERSION (written into generated NBT). Checked against the vanilla snapshot.
DATA_VERSION = 3955

# Locales we ship translations for. en_us is mandatory for every translatable text.
SUPPORTED_LOCALES = ("en_us", "cs_cz")

REPO_ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class RepoPaths:
    """Paths inside a Realmforge checkout. Tests point ``root`` at a temporary copy."""

    root: Path = REPO_ROOT

    @property
    def content(self) -> Path:
        return self.root / "content"

    @property
    def mod_generated(self) -> Path:
        return self.root / "mod" / "src" / "generated"

    @property
    def qa_generated(self) -> Path:
        return self.root / "qa-mod" / "src" / "generated"

    @property
    def schemas(self) -> Path:
        return self.root / "tools" / "schemas"

    @property
    def id_universe(self) -> Path:
        return self.root / "tools" / "id_universe"

    @property
    def cache(self) -> Path:
        return self.root / ".cache"

    def generated_roots(self) -> tuple[Path, ...]:
        """Directories fully owned by the generators: anything in them not produced by ``rf gen`` is deleted."""
        return (self.mod_generated, self.qa_generated, self.schemas)

    def rel(self, path: Path) -> str:
        """Repository-relative POSIX path, for messages."""
        try:
            return path.resolve().relative_to(self.root.resolve()).as_posix()
        except ValueError:
            return path.as_posix()
