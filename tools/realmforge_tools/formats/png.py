"""Minimal deterministic PNG encoder (8-bit RGBA, no filtering)."""

from __future__ import annotations

import struct
import zlib

from realmforge_tools.formats.deflate import zlib_stored

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"

Rgba = tuple[int, int, int, int]


def encode_rgba(width: int, height: int, pixels: list[Rgba]) -> bytes:
    """Encode ``pixels`` (row-major, ``width * height`` RGBA tuples) as a PNG file."""
    if len(pixels) != width * height:
        raise ValueError(f"expected {width * height} pixels, got {len(pixels)}")
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter type: none
        for r, g, b, a in pixels[y * width : (y + 1) * width]:
            raw += bytes((r, g, b, a))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)  # 8-bit depth, color type 6 = RGBA
    return (
        PNG_SIGNATURE
        + _chunk(b"IHDR", ihdr)
        + _chunk(b"IDAT", zlib_stored(bytes(raw)))
        + _chunk(b"IEND", b"")
    )


def _chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
