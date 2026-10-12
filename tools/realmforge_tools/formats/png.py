"""Minimal deterministic PNG encoder (8-bit RGBA, no filtering) and a decoder for the PNGs generators return.

The decoder reads what texture generators and image editors write: 8-bit grayscale, RGB, palette and RGBA images
(palette and grayscale also at 1, 2 and 4 bits), with or without ``tRNS`` transparency. 16-bit and interlaced
images are refused with a ``PngError``; re-export them as 8-bit, non-interlaced PNGs.
"""

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


class PngError(ValueError):
    """A PNG file that is broken or uses a feature the decoder does not support."""


# Channels per color type: 0 grayscale, 2 RGB, 3 palette index, 4 grayscale + alpha, 6 RGBA.
_CHANNELS = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}
_BIT_DEPTHS = {0: (1, 2, 4, 8), 2: (8,), 3: (1, 2, 4, 8), 4: (8,), 6: (8,)}


def decode_rgba(data: bytes) -> tuple[int, int, list[Rgba]]:
    """Decode a PNG file into ``(width, height, pixels)``, pixels row-major as RGBA tuples."""
    if not data.startswith(PNG_SIGNATURE):
        raise PngError("not a PNG file (bad signature)")
    header: tuple[int, ...] | None = None
    palette: list[tuple[int, int, int]] = []
    transparency = b""
    idat = bytearray()
    offset = len(PNG_SIGNATURE)
    while offset + 8 <= len(data):
        (length,) = struct.unpack_from(">I", data, offset)
        kind = data[offset + 4 : offset + 8]
        body = data[offset + 8 : offset + 8 + length]
        name = kind.decode("latin-1")
        if len(body) != length or offset + 12 + length > len(data):
            raise PngError(f"truncated {name} chunk")
        (crc,) = struct.unpack_from(">I", data, offset + 8 + length)
        if crc != zlib.crc32(kind + body):
            raise PngError(f"corrupt {name} chunk (CRC mismatch)")
        offset += 12 + length
        if kind == b"IHDR":
            if length != 13:
                raise PngError(f"IHDR chunk has {length} bytes instead of 13")
            header = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE":
            palette = [tuple(body[i : i + 3]) for i in range(0, length - length % 3, 3)]  # type: ignore[misc]
        elif kind == b"tRNS":
            transparency = body
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            break
    if header is None:
        raise PngError("missing IHDR chunk")
    width, height, depth, color_type, _compression, _filter, interlace = header
    if color_type not in _CHANNELS:
        raise PngError(f"unknown color type {color_type}")
    if depth not in _BIT_DEPTHS[color_type]:
        raise PngError(f"unsupported bit depth {depth} for color type {color_type}; re-export as 8-bit")
    if interlace:
        raise PngError("interlaced PNGs are not supported; re-export without interlacing")
    if color_type == 3 and not palette:
        raise PngError("palette image without a PLTE chunk")
    try:
        raw = zlib.decompress(bytes(idat))
    except zlib.error as e:
        raise PngError(f"corrupt image data: {e}") from e

    bits_per_pixel = _CHANNELS[color_type] * depth
    stride = (width * bits_per_pixel + 7) // 8
    if len(raw) < height * (stride + 1):
        raise PngError("image data is shorter than the header promises")
    rows = _unfilter(raw, height, stride, max(1, bits_per_pixel // 8))

    pixels: list[Rgba] = []
    for row in rows:
        samples = _samples(row, width * _CHANNELS[color_type], depth)
        pixels += _to_rgba(samples, color_type, depth, palette, transparency)
    return width, height, pixels


def _unfilter(raw: bytes, height: int, stride: int, bpp: int) -> list[bytes]:
    rows: list[bytes] = []
    previous = bytearray(stride)
    for y in range(height):
        start = y * (stride + 1)
        kind = raw[start]
        line = bytearray(raw[start + 1 : start + 1 + stride])
        for i in range(stride):
            left = line[i - bpp] if i >= bpp else 0
            up = previous[i]
            if kind == 0:
                continue
            if kind == 1:
                line[i] = (line[i] + left) & 0xFF
            elif kind == 2:
                line[i] = (line[i] + up) & 0xFF
            elif kind == 3:
                line[i] = (line[i] + (left + up) // 2) & 0xFF
            elif kind == 4:
                up_left = previous[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + _paeth(left, up, up_left)) & 0xFF
            else:
                raise PngError(f"unknown filter type {kind} in row {y}")
        rows.append(bytes(line))
        previous = line
    return rows


def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    return b if pb <= pc else c


def _samples(row: bytes, count: int, depth: int) -> list[int]:
    if depth == 8:
        return list(row[:count])
    per_byte = 8 // depth
    mask = (1 << depth) - 1
    out: list[int] = []
    for byte in row:
        for k in range(per_byte):
            out.append((byte >> (8 - depth * (k + 1))) & mask)
    return out[:count]


def _to_rgba(
    samples: list[int],
    color_type: int,
    depth: int,
    palette: list[tuple[int, int, int]],
    transparency: bytes,
) -> list[Rgba]:
    if color_type == 3:
        out: list[Rgba] = []
        for index in samples:
            if index >= len(palette):
                raise PngError(f"palette index {index} is outside the {len(palette)}-color palette")
            alpha = transparency[index] if index < len(transparency) else 255
            out.append((*palette[index], alpha))
        return out
    if color_type in (0, 4):
        scale = 255 // ((1 << depth) - 1)
        if color_type == 4:
            return [(g, g, g, a) for g, a in zip(samples[0::2], samples[1::2], strict=True)]
        key = struct.unpack(">H", transparency[:2])[0] if len(transparency) >= 2 else None
        return [(g * scale, g * scale, g * scale, 0 if g == key else 255) for g in samples]
    if color_type == 2:
        key3 = struct.unpack(">HHH", transparency[:6]) if len(transparency) >= 6 else None
        return [
            (r, g, b, 0 if (r, g, b) == key3 else 255)
            for r, g, b in zip(samples[0::3], samples[1::3], samples[2::3], strict=True)
        ]
    return list(zip(samples[0::4], samples[1::4], samples[2::4], samples[3::4], strict=True))
