"""Minimal binary NBT encoder/decoder (Java Edition, big-endian), e.g. for structure templates.

Python values map to tags as follows: ``dict`` -> Compound, ``str`` -> String, ``NbtList`` -> List, and the
wrapper classes below for numbers (NBT has several integer widths, so plain ``int`` is ambiguous and rejected) and
for the byte, int and long arrays (entity UUIDs, Sponge ``.schem`` block data, Litematica block states).
"""

from __future__ import annotations

import functools
import struct
from dataclasses import dataclass
from typing import Any

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = range(7)
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = range(7, 13)


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
class ByteArray:
    """Signed bytes (-128..127), as Java stores them."""

    values: tuple[int, ...]


@dataclass(frozen=True)
class IntArray:
    values: tuple[int, ...]


@dataclass(frozen=True)
class LongArray:
    values: tuple[int, ...]


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
_STRUCTS = {tag: struct.Struct(fmt) for tag, fmt in _FORMATS.items()}
_WRAPPERS = {tag: cls for cls, (tag, _) in _SCALARS.items()}
_U16 = struct.Struct(">H")
_I32 = struct.Struct(">i")
# Array wrapper -> (tag, element code for struct). The payload is an int length, then the elements.
_ARRAYS: dict[type, tuple[int, str]] = {
    ByteArray: (TAG_BYTE_ARRAY, "b"),
    IntArray: (TAG_INT_ARRAY, "i"),
    LongArray: (TAG_LONG_ARRAY, "q"),
}
_ARRAY_CODES = {tag: code for tag, code in _ARRAYS.values()}
_ARRAY_WRAPPERS = {tag: cls for cls, (tag, _) in _ARRAYS.items()}


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
    if type(value) in _ARRAYS:
        return _ARRAYS[type(value)][0]
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
    encoded = encode_modified_utf8(value)
    if len(encoded) > 0xFFFF:
        raise ValueError(f"an NBT string holds at most 65535 bytes, this one {len(encoded)}")
    return struct.pack(">H", len(encoded)) + encoded


def encode_modified_utf8(value: str) -> bytes:
    """Java's "modified UTF-8" (``DataOutput#writeUTF``), which NBT strings use: UTF-8, except that NUL is two bytes
    (``C0 80``) and a character outside the BMP is its UTF-16 surrogate pair, three bytes per surrogate."""
    if "\0" not in value and all(ord(c) <= 0xFFFF for c in value):
        return value.encode("utf-8")  # the same bytes as plain UTF-8
    surrogates = "".join(c if ord(c) <= 0xFFFF else _surrogate_pair(ord(c)) for c in value)
    return surrogates.encode("utf-8", "surrogatepass").replace(b"\x00", b"\xc0\x80")


def _surrogate_pair(code: int) -> str:
    code -= 0x10000
    return chr(0xD800 + (code >> 10)) + chr(0xDC00 + (code & 0x3FF))


def decode_modified_utf8(data: bytes) -> str:
    """The inverse of ``encode_modified_utf8``; plain UTF-8 is accepted too."""
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError:
        pass
    text = data.replace(b"\xc0\x80", b"\x00").decode("utf-8", "surrogatepass")
    return text.encode("utf-16-be", "surrogatepass").decode("utf-16-be", "surrogatepass")


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
    if kind in _ARRAY_CODES:
        count = len(value.values)
        return struct.pack(f">i{count}{_ARRAY_CODES[kind]}", count, *value.values)
    return struct.pack(_FORMATS[kind], value.value)


@functools.lru_cache(maxsize=256)
def _run(code: str, count: int) -> struct.Struct:
    """A big-endian run of ``count`` values of the struct ``code``, cached: most lists are three ints long."""
    return struct.Struct(f">{count}{code}")


class _Reader:
    """Reads tags from ``data``. Large templates hold millions of tags, so scalars are read with ``unpack_from`` and
    a list of scalars (every block's ``pos``) in one call."""

    def __init__(self, data: bytes) -> None:
        self.data = data
        self.pos = 0

    def _unpack(self, fmt: struct.Struct) -> tuple[Any, ...]:
        if self.pos + fmt.size > len(self.data):
            raise ValueError("truncated NBT data")
        values = fmt.unpack_from(self.data, self.pos)
        self.pos += fmt.size
        return values

    def take(self, n: int) -> bytes:
        chunk = self.data[self.pos : self.pos + n]
        if len(chunk) != n:
            raise ValueError("truncated NBT data")
        self.pos += n
        return chunk

    def u8(self) -> int:
        if self.pos >= len(self.data):
            raise ValueError("truncated NBT data")
        self.pos += 1
        return self.data[self.pos - 1]

    def string(self) -> str:
        (length,) = self._unpack(_U16)
        return decode_modified_utf8(self.take(length))

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
            (count,) = self._unpack(_I32)
            if element_type in _STRUCTS and count > 0:
                wrapper = _WRAPPERS[element_type]
                values = self._unpack(_run(_FORMATS[element_type][1:], count))
                return NbtList(element_type, tuple(map(wrapper, values)))
            return NbtList(element_type, tuple(self.payload(element_type) for _ in range(count)))
        if kind in _STRUCTS:
            (value,) = self._unpack(_STRUCTS[kind])
            return _WRAPPERS[kind](value)
        if kind in _ARRAY_CODES:
            (count,) = self._unpack(_I32)
            if count < 0:
                raise ValueError("negative NBT array length")
            values = self._unpack(_run(_ARRAY_CODES[kind], count))
            return _ARRAY_WRAPPERS[kind](values)
        raise ValueError(f"unsupported NBT tag type {kind}")
