from __future__ import annotations

import gzip
import struct
import zlib

import pytest

from realmforge_tools.formats import nbt
from realmforge_tools.formats.deflate import deflate_stored, gzip_stored, zlib_stored


@pytest.mark.parametrize("size", [0, 1, 1000, 0xFFFF, 0xFFFF + 1, 200_000])
def test_stored_streams_round_trip(size: int) -> None:
    data = bytes(i % 251 for i in range(size))
    assert zlib.decompress(zlib_stored(data)) == data
    assert gzip.decompress(gzip_stored(data)) == data
    assert zlib.decompress(deflate_stored(data), wbits=-15) == data


def test_gzip_header_is_constant() -> None:
    assert gzip_stored(b"abc")[:10] == b"\x1f\x8b\x08\x00\x00\x00\x00\x00\x00\xff"


def test_nbt_round_trip() -> None:
    root = {
        "byte": nbt.Byte(-1),
        "short": nbt.Short(300),
        "int": nbt.Int(4790),
        "long": nbt.Long(2**40),
        "float": nbt.Float(0.5),
        "double": nbt.Double(1.25),
        "text": "Testovací předmět",
        "ints": nbt.int_list(1, 2, 3),
        "empty": nbt.NbtList(nbt.TAG_COMPOUND, ()),
        "nested": {"name": "minecraft:stone"},
    }
    name, decoded = nbt.decode(nbt.encode(root, "root"))
    assert name == "root"
    assert decoded["empty"].items == ()
    decoded["empty"] = root["empty"]  # empty lists are written with element type TAG_END, like vanilla
    assert decoded == root


def test_nbt_array_tags_round_trip() -> None:
    root = {
        "bytes": nbt.ByteArray((-128, -1, 0, 127)),
        "ints": nbt.IntArray((-(2**31), 0, 2**31 - 1)),
        "longs": nbt.LongArray((-(2**63), 0, 2**63 - 1)),
        "empty": nbt.LongArray(()),
        "uuids": nbt.NbtList(nbt.TAG_INT_ARRAY, (nbt.IntArray((1, 2, 3, 4)),)),
    }
    encoded = nbt.encode(root)
    assert nbt.decode(encoded)[1] == root
    # Tag 11 (int array), name "ints", length 3, then the big-endian elements.
    assert b"\x0b\x00\x04ints\x00\x00\x00\x03\x80\x00\x00\x00" in encoded


def test_nbt_array_out_of_range_is_rejected() -> None:
    with pytest.raises(struct.error):
        nbt.encode({"x": nbt.ByteArray((128,))})


def test_nbt_truncated_array_is_rejected() -> None:
    encoded = nbt.encode({"x": nbt.IntArray((1, 2))})
    with pytest.raises(ValueError, match="truncated"):
        nbt.decode(encoded[:-5])


@pytest.mark.parametrize(
    ("text", "java"),
    [
        ("plain", b"plain"),
        ("p\u0159edm\u011bt", "p\u0159edm\u011bt".encode()),
        ("a\0b", b"a\xc0\x80b"),
        ("sign \U0001f600", b"sign \xed\xa0\xbd\xed\xb8\x80"),
    ],
)
def test_nbt_strings_use_java_modified_utf8(text: str, java: bytes) -> None:
    # Java writes NBT strings with DataOutput#writeUTF: NUL as C0 80, characters outside the BMP as surrogates.
    encoded = nbt.encode({"text": text})
    assert java in encoded
    assert nbt.decode(encoded)[1] == {"text": text}


def test_nbt_string_longer_than_65535_bytes_is_refused() -> None:
    with pytest.raises(ValueError, match="65535"):
        nbt.encode({"text": "x" * 65536})


def test_nbt_rejects_plain_ints() -> None:
    with pytest.raises(TypeError):
        nbt.encode({"x": 1})


def test_nbt_list_element_type_is_enforced() -> None:
    with pytest.raises(TypeError):
        nbt.encode({"x": nbt.NbtList(nbt.TAG_INT, (nbt.Short(1),))})
