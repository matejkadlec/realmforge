"""zlib and gzip containers holding *stored* (uncompressed) deflate blocks.

Real compression output depends on the zlib implementation (zlib vs zlib-ng) and version, which would make
generated binaries differ between machines. Stored blocks are fully specified by RFC 1951, so these bytes are
identical everywhere. Our generated binaries are tiny, so the missing compression does not matter.
"""

from __future__ import annotations

import struct
import zlib

_MAX_STORED_BLOCK = 0xFFFF


def deflate_stored(data: bytes) -> bytes:
    """Raw deflate stream (RFC 1951) made only of stored blocks."""
    out = bytearray()
    offsets = range(0, len(data), _MAX_STORED_BLOCK) if data else [0]
    for offset in offsets:
        block = data[offset : offset + _MAX_STORED_BLOCK]
        final = offset + _MAX_STORED_BLOCK >= len(data)
        # Header bits: BFINAL (bit 0), BTYPE=00 (stored); the rest of the byte is padding.
        out.append(1 if final else 0)
        out += struct.pack("<HH", len(block), len(block) ^ 0xFFFF)
        out += block
    return bytes(out)


def zlib_stored(data: bytes) -> bytes:
    """zlib stream (RFC 1950): header 0x78 0x01 (no compression level, no dictionary), deflate, Adler-32."""
    return b"\x78\x01" + deflate_stored(data) + struct.pack(">I", zlib.adler32(data))


def gzip_stored(data: bytes) -> bytes:
    """gzip member (RFC 1952) with mtime 0 and OS 'unknown', so the header is constant."""
    header = b"\x1f\x8b\x08\x00" + b"\x00\x00\x00\x00" + b"\x00\xff"
    trailer = struct.pack("<II", zlib.crc32(data), len(data) & 0xFFFFFFFF)
    return header + deflate_stored(data) + trailer
