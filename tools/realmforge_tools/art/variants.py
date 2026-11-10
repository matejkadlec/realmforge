"""Variants scripted from one base (ADR 0007): a texture recoloured into another, ramp by ramp.

ADR 0007's texture pipeline makes the variants of one texture (rarities, gem colours, armor tiers) by script from one
base instead of generating each: it keeps a set's shapes the same and costs nothing. ``art/textures/variants.yaml``
names them (``art/approved.py`` reads it), per kind and id::

    item:
      spellweave_cap:
        base: arcane_silk_cap
        recolor:
          - {from: [arcane, shadow], to: royal}
          - {from: [gold, wood], to: [stone 6, stone 7, iron 1, iron 2, iron 3]}

A variant is its base's approved texture with each ``recolor`` group mapped: the base's pixels in the ``from`` ramps of
the project palette (a role of the picture: its cloth, its trim, its gem) take the ``to`` colours, a ramp's name or a
list of palette colour names from dark to light, **by lightness**: the darkest pixel of the group takes the first
colour, the lightest the last, the others the colour at their share of the way (OKLab lightness, as the palette
compares colours). Pixels in no group stay. The result holds only palette colours, and it follows the base and the
palette: nothing is stored, ``rf gen`` computes it.
"""

from __future__ import annotations

from typing import Annotated

from pydantic import Field, StringConstraints

from realmforge_tools.art.palette import Palette, Rgb, oklab, ramps
from realmforge_tools.art.texture import Image
from realmforge_tools.schema.common import ContentId, StrictModel

VARIANTS_FILE = "variants.yaml"
PaletteName = Annotated[str, StringConstraints(min_length=1, max_length=64)]


class VariantError(ValueError):
    """A variant that names a ramp or a colour the palette does not have."""


class RecolorDef(StrictModel):
    """One group: the base's pixels in these ramps take those colours, by lightness."""

    source: list[PaletteName] = Field(
        alias="from", min_length=1, description="Palette ramps of the base, e.g. [arcane, shadow]."
    )
    to: PaletteName | list[PaletteName] = Field(
        description="A palette ramp, or palette colour names from dark to light, e.g. [stone 7, iron 1, iron 2]."
    )


class VariantDef(StrictModel):
    """A texture scripted from another one of its kind."""

    base: ContentId = Field(
        description="The id whose approved texture it is made from; not a variant itself."
    )
    recolor: list[RecolorDef] = Field(min_length=1, description="The groups to recolour, each on its own.")


class VariantsFile(StrictModel):
    """``art/textures/variants.yaml``: the variants, by kind and id."""

    item: dict[ContentId, VariantDef] = Field(default_factory=dict, description="Item textures.")


Group = tuple[frozenset[Rgb], tuple[Rgb, ...]]


def groups(variant: VariantDef, palette: Palette) -> list[Group]:
    """Each recolour group as colours: those it takes from the base and those it gives. Raises ``VariantError`` for a
    ramp or a colour name the palette does not have, and for a ramp two groups both take."""
    by_ramp = ramps(palette)
    by_name = {color.name: color.rgb for color in palette.colors}
    taken: dict[str, int] = {}
    resolved: list[Group] = []
    for number, group in enumerate(variant.recolor, start=1):
        sources: set[Rgb] = set()
        for ramp in group.source:
            if ramp not in by_ramp:
                raise VariantError(f"recolor {number}: the palette has no ramp {ramp!r}")
            if ramp in taken:
                raise VariantError(f"recolor {number}: recolor {taken[ramp]} already takes the ramp {ramp!r}")
            taken[ramp] = number
            sources.update(color.rgb for color in by_ramp[ramp])
        if isinstance(group.to, str):
            if group.to not in by_ramp:
                raise VariantError(f"recolor {number}: the palette has no ramp {group.to!r}")
            targets = tuple(color.rgb for color in by_ramp[group.to])
        else:
            missing = [name for name in group.to if name not in by_name]
            if missing:
                raise VariantError(f"recolor {number}: the palette has no colour {missing[0]!r}")
            targets = tuple(by_name[name] for name in group.to)
        resolved.append((frozenset(sources), targets))
    return resolved


def recolor(base: Image, recolors: list[Group]) -> Image:
    """``base`` with each group's pixels mapped onto its colours by lightness; every other pixel as it is."""
    mapping: dict[Rgb, Rgb] = {}
    present = {pixel[:3] for pixel in base.pixels if pixel[3] > 0}
    for sources, targets in recolors:
        used = sorted(present & sources, key=lambda rgb: (oklab(rgb)[0], rgb))
        if not used:
            continue
        darkest, lightest = oklab(used[0])[0], oklab(used[-1])[0]
        span = lightest - darkest
        for rgb in used:
            # One colour of the group in the picture: it takes the middle of the target colours.
            share = (oklab(rgb)[0] - darkest) / span if span > 0 else 0.5
            mapping[rgb] = targets[round(share * (len(targets) - 1))]
    pixels = tuple(
        (*mapping[pixel[:3]], pixel[3]) if pixel[3] > 0 and pixel[:3] in mapping else pixel
        for pixel in base.pixels
    )
    return Image(base.width, base.height, pixels)
