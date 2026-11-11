"""The realm portal's animated textures (RLM-300; realm portal spec, "Look and sound"), drawn by the pipeline.

A portal block's texture is a strip of 16 x 16 frames under each other with a ``.png.mcmeta`` beside it, as vanilla's
nether portal has, and the client tints it with the colour of the realm the portal opens onto. So the frames are
greyscale (the palette's light greys) and see-through, and no generator paints them: a frame has to join its
neighbours on all four sides and the last frame has to lead into the first, which only arithmetic guarantees. One
animation per name in ``PORTAL_ANIMATIONS`` (``schema/realm.py``), which a realm's ``portal.animation`` picks:

- ``swirl``: the shared one (Overworld's return portals, and every realm until Matěj names its own): spirals that
  turn round the corners of the block, so a whole swirl forms wherever four portal blocks meet (Matěj, 2026-10-20:
  "the circle created by the edges of the 4 blocks"), with a calm middle.
- ``stream``: Undervault's: a stream of pixels that flows at 45 degrees from the bottom left to the top right, across
  the whole opening without a seam.

Every frame is a pure function of its number, so the output is the same on every machine.
"""

from __future__ import annotations

import hashlib
import math

from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.formats.png import Rgba, encode_rgba
from realmforge_tools.schema.realm import PORTAL_ANIMATIONS

SIZE = 16
# The palette's greys the frames are drawn in, from dim to bright (stone 3 to 7, iron 1 to 3, white): the tint
# multiplies them, so white shows the realm's colour itself and the darker ones its shadows, down to a deep shade of
# it that the bright streaks and arms stand out from.
GREYS: tuple[tuple[int, int, int], ...] = (
    (63, 63, 70),
    (85, 85, 92),
    (109, 109, 115),
    (135, 135, 140),
    (163, 163, 166),
    (190, 190, 192),
    (211, 211, 213),
    (230, 230, 231),
    (255, 255, 255),
)
# How opaque the dimmest and the brightest grey are: the whole opening reads as a surface (RLM-300: the old swirl was
# "mostly see-through"), and what lies behind still shows faintly, as through a nether portal.
ALPHA_DIM, ALPHA_BRIGHT = 204, 244
# Frames of a loop, and the ticks each one shows.
STREAM_FRAMES, STREAM_FRAME_TIME = 16, 2
SWIRL_FRAMES, SWIRL_FRAME_TIME = 16, 3
# The swirl: two arms that wind outwards, and how far from a corner they reach (half a block, so the swirls of
# neighbouring corners never meet and the middle stays calm).
SWIRL_ARMS = 2
SWIRL_WIND = 0.85
SWIRL_REACH = 8.0


def _pixel(level: float) -> Rgba:
    """A brightness of 0 to 1 as one of the greys, the brighter the more opaque."""
    level = min(max(level, 0.0), 1.0)
    index = round(level * (len(GREYS) - 1))
    alpha = round(ALPHA_DIM + (ALPHA_BRIGHT - ALPHA_DIM) * index / (len(GREYS) - 1))
    return (*GREYS[index], alpha)


def _noise(*parts: int) -> float:
    """A number from 0 to 1 that depends only on ``parts``."""
    digest = hashlib.sha256("/".join(str(part) for part in parts).encode()).digest()
    return digest[0] / 255


def _stream_base() -> list[list[float]]:
    """The stream's still picture, by column and by row counted upwards: on each of the 16 diagonals that run from the
    bottom left to the top right, one or two streaks, bright at the head (the upper right end) and fading down their
    tail. A diagonal wraps round the tile, so the picture joins itself on every side."""
    base = [[0.0] * SIZE for _ in range(SIZE)]
    for diagonal in range(SIZE):
        streaks = 1 + (_noise(1, diagonal) > 0.55)
        for streak in range(streaks):
            head = round(_noise(2, diagonal, streak) * (SIZE - 1))
            length = 3 + round(_noise(3, diagonal, streak) * 5)
            peak = 0.55 + 0.45 * _noise(4, diagonal, streak)
            for step in range(length):
                along = (head - step) % SIZE
                x, up = along, (along - diagonal) % SIZE
                level = peak * (1 - step / length)
                base[x][up] = max(base[x][up], level)
    return base


def stream_frame(frame: int) -> list[Rgba]:
    """Frame ``frame`` of the stream, row by row from the top: the still picture moved ``frame`` pixels up and to the
    right."""
    base = _stream_base()
    pixels: list[Rgba] = []
    for y in range(SIZE):
        up = SIZE - 1 - y
        for x in range(SIZE):
            level = base[(x - frame) % SIZE][(up - frame) % SIZE]
            pixels.append(_pixel(0.15 + 0.85 * level))
    return pixels


def swirl_frame(frame: int) -> list[Rgba]:
    """Frame ``frame`` of the swirl, row by row from the top: round the nearest corner of the block, two arms that
    wind outwards and turn with the frames, fading towards the middle of the block."""
    turn = 2 * math.pi * frame / SWIRL_FRAMES
    pixels: list[Rgba] = []
    for y in range(SIZE):
        for x in range(SIZE):
            # The middle of the pixel, measured from the nearest corner of the block.
            dx = x + 0.5 - (0 if x < SIZE // 2 else SIZE)
            dy = y + 0.5 - (0 if y < SIZE // 2 else SIZE)
            radius = math.hypot(dx, dy)
            reach = max(0.0, 1 - radius / SWIRL_REACH)
            arm = (math.cos(math.atan2(dy, dx) * SWIRL_ARMS + radius * SWIRL_WIND - turn) + 1) / 2
            pixels.append(_pixel(0.2 + 0.8 * arm * reach**0.6))
    return pixels


def strip(animation: str) -> bytes:
    """The texture of ``animation``: its frames under each other, 16 pixels wide."""
    draw, frames = (stream_frame, STREAM_FRAMES) if animation == "stream" else (swirl_frame, SWIRL_FRAMES)
    pixels: list[Rgba] = []
    for frame in range(frames):
        pixels.extend(draw(frame))
    return encode_rgba(SIZE, SIZE * frames, pixels)


def mcmeta(animation: str) -> bytes:
    """The ``.png.mcmeta`` of ``animation``: square frames in order, each shown for the animation's frame time."""
    frame_time = STREAM_FRAME_TIME if animation == "stream" else SWIRL_FRAME_TIME
    return dump_json({"animation": {"frametime": frame_time}})


def texture_name(block_id: str, animation: str) -> str:
    """The texture of ``animation`` on the portal block ``block_id``: the block's own name for the first animation
    (the shared swirl), ``<block>_<animation>`` for the others."""
    return block_id if animation == PORTAL_ANIMATIONS[0] else f"{block_id}_{animation}"
