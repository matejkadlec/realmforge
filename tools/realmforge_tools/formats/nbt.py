"""Minimal binary NBT encoder/decoder (Java Edition, big-endian), e.g. for structure templates.

Python values map to tags as follows: ``dict`` -> Compound, ``str`` -> String, ``NbtList`` -> List and the
wrapper classes below for numbers (NBT has several integer widths, so plain ``int`` is ambiguous and rejected).
"""

from __future__ import annotations

import struct
from dataclasses import dataclass
from typing import Any

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = range(7)
TAG_STRING, TAG_LIST, TAG_COMPOUND = 8, 9, 10


@dataclass(frozen=True)
class Byte:
    value: int


@dataclass(frozen=True)
class Short:
    value: int


@dataclass(frozen=True)
class Int:
    value: int


@dataclass(frozen=True)
class Long:
    value: int


@dataclass(frozen=True)
class Float:
    value: float


@dataclass(frozen=True)
class Double:
    value: float


@dataclass(frozen=True)
class NbtList:
    """A list tag. ``element_type`` is needed because an empty list still records its element type."""

    element_type: int
    items: tuple[Any, ...]


_SCALARS: dict[type, tuple[int, str]] = {
    Byte: (TAG_BYTE, ">b"),
    Short: (TAG_SHORT, ">h"),
    Int: (TAG_INT, ">i"),
    Long: (TAG_LONG, ">q"),
    Float: (TAG_FLOAT, ">f"),
    Double: (TAG_DOUBLE, ">d"),
}
_FORMATS = {tag: fmt for tag, fmt in _SCALARS.values()}
_WRAPPERS = {tag: cls for cls, (tag, _) in _SCALARS.items()}


def int_list(*values: int) -> NbtList:
    return NbtList(TAG_INT, tuple(Int(v) for v in values))


def tag_type(value: Any) -> int:
    if isinstance(value, dict):
        return TAG_COMPOUND
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, NbtList):
        return TAG_LIST
    if type(value) in _SCALARS:
        return _SCALARS[type(value)][0]
    raise TypeError(f"cannot encode {type(value).__name__} as NBT; use the wrapper classes")


def encode(root: dict[str, Any], name: str = "") -> bytes:
    """Encode a root compound (uncompressed). Compress with ``gzip_stored`` for structure files."""
    return bytes([TAG_COMPOUND]) + _string(name) + _payload(root)


def decode(data: bytes) -> tuple[str, dict[str, Any]]:
    """Decode an uncompressed root compound. Returns ``(name, compound)``."""
    reader = _Reader(data)
    if reader.u8() != TAG_COMPOUND:
        raise ValueError("root tag is not a compound")
    name = reader.string()
    return name, reader.payload(TAG_COMPOUND)


def _string(value: str) -> bytes:
    # Java's "modified UTF-8" equals UTF-8 for text without NUL and supplementary characters.
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def _payload(value: Any) -> bytes:
    kind = tag_type(value)
    if kind == TAG_COMPOUND:
        out = bytearray()
        for key, child in value.items():
            out += bytes([tag_type(child)]) + _string(key) + _payload(child)
        return bytes(out + bytes([TAG_END]))
    if kind == TAG_STRING:
        return _string(value)
    if kind == TAG_LIST:
        element_type = value.element_type if value.items else TAG_END
        for item in value.items:
            if tag_type(item) != value.element_type:
                raise TypeError("all elements of an NBT list must have the declared element type")
        return (
            bytes([element_type]) + struct.pack(">i", len(value.items)) + b"".join(map(_payload, value.items))
        )
    return struct.pack(_FORMATS[kind], value.value)


class _Reader:
    def __init__(self, data: bytes) -> None:
        self.data = data
        self.pos = 0

    def take(self, n: int) -> bytes:
        chunk = self.data[self.pos : self.pos + n]
        if len(chunk) != n:
            raise ValueError("truncated NBT data")
        self.pos += n
        return chunk

    def u8(self) -> int:
        return self.take(1)[0]

    def string(self) -> str:
        (length,) = struct.unpack(">H", self.take(2))
        return self.take(length).decode("utf-8")

    def payload(self, kind: int) -> Any:
        if kind == TAG_COMPOUND:
            result: dict[str, Any] = {}
            while (child_kind := self.u8()) != TAG_END:
                key = self.string()
                result[key] = self.payload(child_kind)
            return result
        if kind == TAG_STRING:
            return self.string()
        if kind == TAG_LIST:
            element_type = self.u8()
            (count,) = struct.unpack(">i", self.take(4))
            return NbtList(element_type, tuple(self.payload(element_type) for _ in range(count)))
        if kind in _FORMATS:
            fmt = _FORMATS[kind]
            (value,) = struct.unpack(fmt, self.take(struct.calcsize(fmt)))
            return _WRAPPERS[kind](value)
        raise ValueError(f"unsupported NBT tag type {kind}")
