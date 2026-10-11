"""Structure templates for GameTests in the QA mod (``data/realmforge_qa/structure/*.nbt``).

Every GameTest needs a structure template; tests then place whatever blocks they need themselves.
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats import nbt
from realmforge_tools.formats.deflate import gzip_stored
from realmforge_tools.project import DATA_VERSION, QA_MOD_ID, RepoPaths

# name -> (size x, size y, size z). Each template is a smooth stone floor (y = 0) with air above.
PLATFORMS: dict[str, tuple[int, int, int]] = {
    "platform_5x5": (5, 5, 5),
}


def platform(size_x: int, size_y: int, size_z: int) -> bytes:
    """Gzipped structure NBT: a ``size_x`` x ``size_z`` floor of smooth stone, empty space above it."""
    floor = tuple(
        {"pos": nbt.int_list(x, 0, z), "state": nbt.Int(0)} for x in range(size_x) for z in range(size_z)
    )
    air = tuple(
        {"pos": nbt.int_list(x, y, z), "state": nbt.Int(1)}
        for x in range(size_x)
        for y in range(1, size_y)
        for z in range(size_z)
    )
    root = {
        "DataVersion": nbt.Int(DATA_VERSION),
        "size": nbt.int_list(size_x, size_y, size_z),
        "palette": nbt.NbtList(
            nbt.TAG_COMPOUND, ({"Name": "minecraft:smooth_stone"}, {"Name": "minecraft:air"})
        ),
        "blocks": nbt.NbtList(nbt.TAG_COMPOUND, floor + air),
        "entities": nbt.NbtList(nbt.TAG_COMPOUND, ()),
    }
    return gzip_stored(nbt.encode(root))


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    structure_dir = paths.qa_generated / "resources" / "data" / QA_MOD_ID / "structure"
    return {structure_dir / f"{name}.nbt": platform(*size) for name, size in PLATFORMS.items()}
