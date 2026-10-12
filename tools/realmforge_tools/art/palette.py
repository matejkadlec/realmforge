"""The project palette: a GIMP ``.gpl`` file in ``art/palette/`` and nearest-color matching against it.

Colors are compared in OKLab, a perceptual color space, so a generated pixel snaps to the palette color that
looks closest rather than the one with the closest RGB numbers. Ties go to the color listed first, so the result
never depends on dict or set order.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

Rgb = tuple[int, int, int]

DEFAULT_PALETTE = Path("art") / "palette" / "realmforge.gpl"


class PaletteError(ValueError):
    """A palette file that cannot be read."""


@dataclass(frozen=True)
class PaletteColor:
    name: str
    rgb: Rgb

    @property
    def hex(self) -> str:
        return "#{:02x}{:02x}{:02x}".format(*self.rgb)


@dataclass
class Palette:
    name: str
    colors: tuple[PaletteColor, ...]
    _lab: tuple[tuple[float, float, float], ...] = field(init=False, repr=False)
    _cache: dict[Rgb, PaletteColor] = field(default_factory=dict, init=False, repr=False)

    def __post_init__(self) -> None:
        if not self.colors:
            raise PaletteError(f"palette {self.name!r} has no colors")
        self._lab = tuple(oklab(c.rgb) for c in self.colors)

    def nearest(self, rgb: Rgb) -> PaletteColor:
        """The palette color perceptually closest to ``rgb`` (the first one listed on a tie)."""
        hit = self._cache.get(rgb)
        if hit is None:
            lab = oklab(rgb)
            distances = [_distance2(lab, other) for other in self._lab]
            hit = self.colors[distances.index(min(distances))]
            self._cache[rgb] = hit
        return hit


def load_gpl(path: Path) -> Palette:
    """Read a GIMP palette: a ``GIMP Palette`` header, optional ``Name:``/``Columns:`` lines, ``#`` comments,
    then one ``R G B name`` line per color. Every name must be unique."""
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except UnicodeDecodeError as e:
        raise PaletteError(f"{path}: not UTF-8 text ({e.reason} at byte {e.start})") from e
    if not lines or lines[0].strip() != "GIMP Palette":
        raise PaletteError(f"{path}: not a GIMP palette (first line must be 'GIMP Palette')")
    name = path.stem
    colors: list[PaletteColor] = []
    seen: set[str] = set()
    for number, line in enumerate(lines[1:], start=2):
        text = line.strip()
        if not text or text.startswith("#") or text.startswith("Columns:"):
            continue
        if text.startswith("Name:"):
            name = text.removeprefix("Name:").strip() or name
            continue
        parts = text.split(None, 3)
        if len(parts) < 3:
            raise PaletteError(f"{path}:{number}: expected 'R G B name', got {text!r}")
        try:
            rgb = (int(parts[0]), int(parts[1]), int(parts[2]))
        except ValueError:
            raise PaletteError(f"{path}:{number}: R, G and B must be integers, got {text!r}") from None
        if any(not 0 <= channel <= 255 for channel in rgb):
            raise PaletteError(f"{path}:{number}: R, G and B must be between 0 and 255, got {text!r}")
        color_name = parts[3].strip() if len(parts) == 4 else f"color {len(colors) + 1}"
        if color_name in seen:
            raise PaletteError(f"{path}:{number}: duplicate color name {color_name!r}")
        seen.add(color_name)
        colors.append(PaletteColor(color_name, rgb))
    return Palette(name, tuple(colors))


def oklab(rgb: Rgb) -> tuple[float, float, float]:
    """sRGB (0-255) to OKLab (Björn Ottosson, 2020)."""
    r, g, b = (_linear(channel / 255) for channel in rgb)
    long = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    medium = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    short = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    long, medium, short = (value ** (1 / 3) for value in (long, medium, short))
    return (
        0.2104542553 * long + 0.7936177850 * medium - 0.0040720468 * short,
        1.9779984951 * long - 2.4285922050 * medium + 0.4505937099 * short,
        0.0259040371 * long + 0.7827717662 * medium - 0.8086757660 * short,
    )


def _linear(value: float) -> float:
    return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4


def _distance2(a: tuple[float, float, float], b: tuple[float, float, float]) -> float:
    return (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2
