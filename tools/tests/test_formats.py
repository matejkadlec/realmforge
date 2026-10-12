from __future__ import annotations

import gzip
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


def test_nbt_rejects_plain_ints() -> None:
    with pytest.raises(TypeError):
        nbt.encode({"x": 1})


def test_nbt_list_element_type_is_enforced() -> None:
    with pytest.raises(TypeError):
        nbt.encode({"x": nbt.NbtList(nbt.TAG_INT, (nbt.Short(1),))})
