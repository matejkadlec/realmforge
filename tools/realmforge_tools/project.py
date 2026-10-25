"""Project-wide constants and well-known repository paths."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

MOD_ID = "realmforge"
QA_MOD_ID = "realmforge_qa"
JAVA_PACKAGE = "io.github.matejkadlec.realmforge"
QA_JAVA_PACKAGE = f"{JAVA_PACKAGE}.qa"

MINECRAFT_VERSION = "26.1.2"
# World data version of MINECRAFT_VERSION (written into generated NBT). Checked against the vanilla snapshot.
DATA_VERSION = 4790

# Locales we ship translations for: English only (Matěj, 2026-10-13). A community translation plugs in as a resource
# pack keyed by the same translation keys (content/README.md); the pipeline needs nothing for it.
SUPPORTED_LOCALES = ("en_us",)

REPO_ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class RepoPaths:
    """Paths inside a Realmforge checkout. Tests point ``root`` at a temporary copy."""

    root: Path = REPO_ROOT

    @property
    def content(self) -> Path:
        return self.root / "content"

    @property
    def mod_resources(self) -> Path:
        """The core mod's hand-written resources (not generated)."""
        return self.root / "mod" / "src" / "main" / "resources"

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
    def pack_quests(self) -> Path:
        """FTB Quests' quest book among the pack's config overrides, written by ``generate/quests.py``."""
        return self.root / "pack" / "config" / "ftbquests" / "quests"

    @property
    def id_universe(self) -> Path:
        return self.root / "tools" / "id_universe"

    @property
    def cache(self) -> Path:
        return self.root / ".cache"

    def generated_roots(self) -> tuple[Path, ...]:
        """Directories fully owned by the generators: anything in them not produced by ``rf gen`` is deleted."""
        return (self.mod_generated, self.qa_generated, self.schemas, self.pack_quests)

    def rel(self, path: Path) -> str:
        """Repository-relative POSIX path, for messages."""
        try:
            return path.resolve().relative_to(self.root.resolve()).as_posix()
        except ValueError:
            return path.as_posix()
