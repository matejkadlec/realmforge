"""Texture cleanup, the first step of the texture pipeline (ADR 0007): generator output in, game texture out.

``clean`` turns a generated image into a texture Minecraft can use as is:

1. **Grid:** an image larger than the target is reduced by a whole factor (a 64x64 image of 4x4-pixel blocks
   becomes 16x16). Each block takes its most common color, which removes the soft edges an upscaler adds.
2. **Alpha:** every pixel becomes fully opaque or fully transparent (item textures use cutout rendering, so
   half-transparent pixels would look different in the inventory and in the hand).
3. **Palette:** every opaque pixel snaps to the nearest project palette color.

The step is deterministic: the same input and palette always give the same bytes.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass

from realmforge_tools.art.palette import Palette
from realmforge_tools.formats.png import Rgba, encode_rgba

TRANSPARENT: Rgba = (0, 0, 0, 0)
DEFAULT_SIZE = 16
DEFAULT_ALPHA_THRESHOLD = 128


class TextureError(ValueError):
    """An image the cleanup step cannot turn into a texture of the requested size."""


@dataclass(frozen=True)
class Image:
    width: int
    height: int
    pixels: tuple[Rgba, ...]

    def __post_init__(self) -> None:
        if len(self.pixels) != self.width * self.height:
            raise ValueError(f"expected {self.width * self.height} pixels, got {len(self.pixels)}")

    def png(self) -> bytes:
        return encode_rgba(self.width, self.height, list(self.pixels))

    def colors(self) -> int:
        """Distinct opaque colors."""
        return len({p for p in self.pixels if p[3] > 0})

    def is_opaque(self) -> bool:
        return all(p[3] == 255 for p in self.pixels)

    def tiled(self, times: int) -> Image:
        """The image repeated ``times`` x ``times``, to check that a block texture tiles without seams."""
        pixels = [
            self.pixels[(y % self.height) * self.width + (x % self.width)]
            for y in range(self.height * times)
            for x in range(self.width * times)
        ]
        return Image(self.width * times, self.height * times, tuple(pixels))


@dataclass(frozen=True)
class CleanReport:
    scale: int
    colors_before: int
    colors_after: int
    changed_pixels: int


def clean(
    image: Image,
    palette: Palette,
    size: int = DEFAULT_SIZE,
    alpha_threshold: int = DEFAULT_ALPHA_THRESHOLD,
) -> tuple[Image, CleanReport]:
    """Reduce ``image`` to ``size`` pixels wide, harden its alpha and snap it to ``palette``.

    The height follows the aspect ratio, so a 16x32 animation strip stays one. Raises ``TextureError`` when the
    image is not a whole multiple of the target size.
    """
    if size <= 0:
        raise TextureError(f"size must be positive, got {size}")
    if image.width == 0 or image.height == 0:
        raise TextureError(f"a {image.width}x{image.height} image has no pixels")
    if not 1 <= alpha_threshold <= 255:
        raise TextureError(f"alpha threshold must be between 1 and 255, got {alpha_threshold}")
    if image.width % size:
        raise TextureError(
            f"a {image.width}x{image.height} image cannot be reduced to {size} pixels wide: "
            f"its width is not a multiple of {size}"
        )
    scale = image.width // size
    if image.height % scale:
        raise TextureError(
            f"a {image.width}x{image.height} image cannot be reduced by {scale}: "
            f"its height is not a multiple of {scale}"
        )
    reduced = _reduce(image, scale, alpha_threshold)
    pixels: list[Rgba] = []
    for r, g, b, a in reduced.pixels:
        if a < alpha_threshold:
            pixels.append(TRANSPARENT)
        else:
            pixels.append((*palette.nearest((r, g, b)).rgb, 255))
    result = Image(reduced.width, reduced.height, tuple(pixels))
    changed = sum(
        1 for before, after in zip(reduced.pixels, result.pixels, strict=True) if _visible(before) != after
    )
    return result, CleanReport(scale, image.colors(), result.colors(), changed)


def _visible(pixel: Rgba) -> Rgba:
    """Fully transparent pixels all look the same, whatever color values they carry."""
    return TRANSPARENT if pixel[3] == 0 else pixel


def _reduce(image: Image, scale: int, alpha_threshold: int) -> Image:
    if scale == 1:
        return image
    width, height = image.width // scale, image.height // scale
    pixels: list[Rgba] = []
    for by in range(height):
        for bx in range(width):
            block = [
                image.pixels[(by * scale + dy) * image.width + bx * scale + dx]
                for dy in range(scale)
                for dx in range(scale)
            ]
            opaque = [p for p in block if p[3] >= alpha_threshold]
            if len(opaque) * 2 <= len(block):
                pixels.append(TRANSPARENT)
            else:
                # most_common keeps first-seen order on ties, so the result is deterministic.
                pixels.append(Counter(opaque).most_common(1)[0][0])
    return Image(width, height, tuple(pixels))
