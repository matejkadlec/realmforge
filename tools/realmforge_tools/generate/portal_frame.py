"""The textures of a realm portal's frame in each of its looks (RLM-301; realm portal spec, "Look and sound").

The frame's blocks have a ``look`` state: a dark frame is ``washed``, washed-out bricks, and a lit one takes the look
of the realm its portal opens onto (a realm's ``portal.frame``), ``stone`` for Undervault. Every look's textures are
made from the Gatewright Bricks' approved tile (``art/frame_looks.py`` has the colours and the drawing): the bricks in
the look, which the sealstone shows on its sides and its bottom too, and the sealstone's top with the signet's socket.
The first look's textures carry the block's own name, the others ``<block>_<look>``. Until the bricks have their art,
every one of them is a placeholder.
"""

from __future__ import annotations

from realmforge_tools.art import approved, frame_looks
from realmforge_tools.generate.textures import placeholder_block_texture
from realmforge_tools.project import MOD_ID, RepoPaths
from realmforge_tools.schema.realm import FRAME_LOOKS


def texture_name(block_id: str, look: str) -> str:
    """The texture (and the model) of ``block_id`` in ``look``: the block's own name for the first look."""
    return block_id if look == FRAME_LOOKS[0] else f"{block_id}_{look}"


def top_name(block_id: str, look: str) -> str:
    """The texture of the sealstone's top in ``look``."""
    return f"{block_id}_top" if look == FRAME_LOOKS[0] else f"{block_id}_top_{look}"


def textures(paths: RepoPaths, bricks_id: str, sealstone_id: str | None) -> dict[str, bytes]:
    """Every texture of the frame's looks, by name under ``textures/block/``: the bricks in each look and, with a
    sealstone, its top in each look. Placeholders until the bricks have approved art."""
    tile = approved.image(paths, approved.BLOCK, bricks_id)
    palette = approved.palette(paths) if tile is not None else None
    files: dict[str, bytes] = {}
    for look in FRAME_LOOKS:
        names = [texture_name(bricks_id, look)]
        if sealstone_id is not None:
            names.append(top_name(sealstone_id, look))
        if tile is None or palette is None:
            for name in names:
                files[name] = placeholder_block_texture(f"{MOD_ID}:{name}")
            continue
        look_bricks = frame_looks.bricks(tile, look, palette)
        files[names[0]] = look_bricks.png()
        if sealstone_id is not None:
            files[names[1]] = frame_looks.sealstone_top(look_bricks, look, palette).png()
    return files
