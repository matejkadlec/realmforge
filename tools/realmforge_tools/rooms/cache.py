"""Parsed room templates and what is read from them, cached in ``.cache/rooms/parsed/`` (RLM-165).

Decoding the NBT of every room is most of what ``rf validate`` and ``rf gen`` cost (122 rooms, about half a million
blocks, pure Python), and the Claude Code hook runs ``rf validate`` after every edit. The cache keeps each decoded
template under a key made of the sha256 of its bytes and the code version, so an unchanged room is never decoded
again and any change to a template, or to the code that reads it, misses:

- ``<version>/structure/<key>.pickle``: the ``Structure`` in a packed form (a palette and flat block lists), rebuilt
  only when something asks for the room's blocks (``RoomEntry.structure``);
- ``<version>/<kind>/<key>.pickle``: a value computed from the structure (``RoomEntry.derived``): its size,
  connectors, fluid edges and markers, its normalized bytes for the jar, the problems ``check_structure`` finds
  against an ID universe (whose digest goes into the kind, ``check-<digest>``).

The code version hashes ``CACHE_VERSION``, the game's ``DATA_VERSION`` and the source of ``rooms/``, ``formats/``
and ``id_universe.py``, so an edit there invalidates every entry without a manual bump. A cached value is therefore
computed only by functions in that code (or the standard library) and reads no other constant than
``DATA_VERSION``; bump ``CACHE_VERSION`` for any other change that alters what is cached. Old entries do not pile
up: the first write of a run deletes every other version's folder, and the first write of a ``check-<digest>``
kind deletes the check results of other ID universes (RLM-238). Entries are written atomically (a temporary file,
then a rename), so parallel runs (the hook while pytest runs) never read half a file, and an entry that cannot be
read is computed again. The cache is a local convenience: ``.cache/`` is gitignored, and CI starts without one.
"""

from __future__ import annotations

import hashlib
import os
import pickle
import shutil
import tempfile
from collections.abc import Callable
from functools import cache
from pathlib import Path
from typing import Any

from realmforge_tools import project
from realmforge_tools.id_universe import IdUniverse
from realmforge_tools.rooms.structure import Structure, StructureBlock

CACHE_VERSION = 1
_TOOLS = Path(__file__).resolve().parent.parent


@cache
def code_version() -> str:
    """A digest of ``CACHE_VERSION``, ``DATA_VERSION`` and the code that reads templates and computes cached
    values."""
    digest = hashlib.sha256(f"realmforge room cache {CACHE_VERSION} {project.DATA_VERSION}\n".encode())
    sources = [
        *(_TOOLS / "rooms").glob("*.py"),
        *(_TOOLS / "formats").glob("*.py"),
        _TOOLS / "id_universe.py",
    ]
    for source in sorted(sources):
        digest.update(f"{source.parent.name}/{source.name}".encode() + b"\0" + source.read_bytes())
    return digest.hexdigest()


def template_key(data: bytes) -> str:
    """The cache key of a template: its bytes and the code version."""
    return hashlib.sha256(code_version().encode() + data).hexdigest()


def universe_digest(universe: IdUniverse) -> str:
    """A short digest of every id and tag in ``universe``, for values that depend on it."""
    digest = hashlib.sha256()
    for kind in (universe.registries, universe.tags):
        for registry in sorted(kind):
            digest.update(registry.encode() + b"\0" + "\0".join(sorted(kind[registry])).encode() + b"\1")
        digest.update(b"\2")
    return digest.hexdigest()[:16]


class RoomCache:
    """The cache in ``root``, its entries in the current code version's folder; ``None`` keeps nothing (every value
    is computed)."""

    def __init__(self, root: Path | None) -> None:
        self.root = root
        self.folder = None if root is None else root / code_version()[:16]
        self._evicted: set[str] = (
            set()
        )  # what this cache already cleaned up: "" for the versions, else a family

    def structure(self, key: str) -> Structure | None:
        """The cached structure under ``key``, or None."""
        packed = self._read("structure", key)
        return None if packed is None else _unpack(packed)

    def has_structure(self, key: str) -> bool:
        return self.folder is not None and self._path("structure", key).is_file()

    def put_structure(self, key: str, structure: Structure) -> None:
        self._write("structure", key, _pack(structure))

    def derived(self, kind: str, key: str, compute: Callable[[], Any]) -> Any:
        """The value of ``kind`` for the template under ``key``: cached, or computed and stored."""
        found = self._read(kind, key)
        if found is not None:
            return found[0]
        value = compute()
        self._write(kind, key, (value,))
        return value

    def _path(self, kind: str, key: str) -> Path:
        assert self.folder is not None
        return self.folder / kind / f"{key}.pickle"

    def _read(self, kind: str, key: str) -> Any:
        if self.folder is None:
            return None
        try:
            return pickle.loads(self._path(kind, key).read_bytes())
        except (
            OSError,
            pickle.UnpicklingError,
            EOFError,
            AttributeError,
            ImportError,
            IndexError,
            TypeError,
        ):
            return None

    def _write(self, kind: str, key: str, value: Any) -> None:
        if self.folder is None:
            return
        path = self._path(kind, key)
        self._evict(kind)
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.NamedTemporaryFile(dir=path.parent, suffix=".tmp", delete=False) as out:
                out.write(pickle.dumps(value, protocol=pickle.HIGHEST_PROTOCOL))
            os.replace(out.name, path)
        except OSError:
            pass  # a read-only checkout or a full disk only costs speed

    def _evict(self, kind: str) -> None:
        """Before the first write: delete the other code versions' folders, and for a ``<family>-<digest>`` kind
        the folders of the family's other digests. A run that still reads them only computes its values again."""
        assert self.root is not None and self.folder is not None
        family = kind.partition("-")[0] + "-" if "-" in kind else None
        for scope, parent, keep, prefix in (
            ("", self.root, self.folder.name, ""),
            (family, self.folder, kind, family),
        ):
            if scope is None or scope in self._evicted:
                continue
            self._evicted.add(scope)
            try:
                stale = [
                    p for p in parent.iterdir() if p.is_dir() and p.name.startswith(prefix) and p.name != keep
                ]
            except OSError:
                continue
            for folder in stale:
                shutil.rmtree(folder, ignore_errors=True)


def _pack(structure: Structure) -> tuple[Any, ...]:
    """A structure as plain values: a palette and, per block, x, y, z and its palette index in one flat list."""
    palette = sorted({block.state for block in structure.blocks})
    index = {state: i for i, state in enumerate(palette)}
    flat: list[int] = []
    for block in structure.blocks:
        flat += (*block.pos, index[block.state])
    nbt = {i: block.nbt for i, block in enumerate(structure.blocks) if block.nbt is not None}
    return structure.size, structure.data_version, tuple(palette), flat, nbt, structure.entities


def _unpack(packed: tuple[Any, ...]) -> Structure:
    size, data_version, palette, flat, nbt, entities = packed
    new = object.__new__
    blocks = []
    for n, i in enumerate(range(0, len(flat), 4)):
        # The frozen dataclass's __init__ sets each field through object.__setattr__; filling __dict__ directly
        # builds the same object in a third of the time, which matters for half a million blocks.
        block = new(StructureBlock)
        block.__dict__.update(
            pos=(flat[i], flat[i + 1], flat[i + 2]), state=palette[flat[i + 3]], nbt=nbt.get(n)
        )
        blocks.append(block)
    return Structure(size, data_version, tuple(blocks), entities)
