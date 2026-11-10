"""The spell visuals kit (RLM-439, ADR 0073, architecture 11.3): the reusable parts a spell's look is combined from.

- ``content/spell_looks/<id>.yaml``: an element's look (frost, fire, arcane, stone): its colours, the particles its
  projectiles trail and burst into, and its impact sound. Its material, the one tileable 16 x 16 texture every model of
  the look is drawn with, is ``textures/spell/material/<id>.png``.
- ``content/spell_models/<id>.yaml``: a projectile's shape. The ``orb`` is the only shape until the GeckoLib models
  (RLM-640).
- ``content/spell_trajectories/<id>.yaml``: how a projectile flies. Only ``straight`` (ADR 0052); the registry keeps
  room for a later kind.
- ``content/particles/<id>.yaml``: a particle shape of our own, greyscale frames that each spawn tints with one of a
  look's colours, and how it moves.

A spell names them in its ``visuals`` and on its projectile (``schema/spell.py``).
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field, StringConstraints, model_validator

from realmforge_tools.schema.common import ContentId, ResourceId, StrictModel
from realmforge_tools.schema.spell_school import HexColor

# A look's palette: Matěj's frost range holds 12 to 16 colours (2026-10-29).
MAX_LOOK_COLORS = 16
# The particle budget of the design (docs/design/spell-visuals.md): a trail spawns at most 3 a block flown, an impact
# bursts into at most 24 at once.
MAX_TRAIL_PER_BLOCK = 3
MAX_BURST_COUNT = 24
# A particle's frames are 8 x 8 pixels, as vanilla's are.
PARTICLE_SIZE = 8
MAX_PARTICLE_FRAMES = 8
# A particle lives at most 2 seconds: the budget counts on short lives.
MAX_PARTICLE_LIFETIME_TICKS = 40
MAX_PARTICLE_QUAD_SIZE = 0.5
MAX_MODEL_SCALE = 4
MAX_SPIN_DEGREES_PER_SECOND = 1440

# A frame's row: "." is transparent, a hex digit from 1 to f a grey from dark to white, which a spawn's colour tints.
ParticleRow = Annotated[str, StringConstraints(pattern=r"^[.1-9a-f]{8}$")]


class TrailDef(StrictModel):
    """The particles a projectile of the look leaves behind as it flies, spawned by its clients."""

    particle: ResourceId = Field(
        description="One of our particles, e.g. realmforge:mote (content/particles/)."
    )
    per_block: float = Field(
        gt=0,
        le=MAX_TRAIL_PER_BLOCK,
        allow_inf_nan=False,
        description="How many a block flown, at most 3.",
    )


class BurstDef(StrictModel):
    """The particles a projectile of the look bursts into where it hits, spread over its splash."""

    particle: ResourceId = Field(
        description="One of our particles, e.g. realmforge:shard (content/particles/)."
    )
    count: int = Field(ge=1, le=MAX_BURST_COUNT, description="How many at once, at most 24.")


class SpellLookDef(StrictModel):
    """An element's look, shared by every spell of it: frost reads as frost on Frostbolt and on Ice Lance."""

    id: ContentId
    colors: list[HexColor] = Field(
        min_length=1,
        max_length=MAX_LOOK_COLORS,
        description='Its colours, lightest first, "#RRGGBB": each particle takes one at random, and its placeholder '
        "material is noise in them. At most 16.",
    )
    trail: TrailDef | None = Field(default=None, description="What its projectiles leave behind in flight.")
    burst: BurstDef | None = Field(
        default=None, description="What its projectiles burst into where they hit."
    )
    impact_sound: ResourceId | None = Field(
        default=None,
        description="The sound event of its projectiles' hit on a block or a creature, e.g. "
        "minecraft:block.glass.break. A spell may name its own (visuals.impact_sound).",
    )

    @model_validator(mode="after")
    def _distinct_colors(self) -> SpellLookDef:
        if len(set(self.colors)) != len(self.colors):
            raise ValueError("colors: each colour once")
        return self


class SpellModelDef(StrictModel):
    """A projectile's shape, drawn with its look's material."""

    id: ContentId
    shape: Literal["orb"] = Field(
        description="orb: three crossed slabs half a block across, the placeholder shape of every model until its "
        "GeckoLib model exists (RLM-640)."
    )
    scale: float = Field(
        default=1.0,
        gt=0,
        le=MAX_MODEL_SCALE,
        allow_inf_nan=False,
        description="Its width as a share of the projectile's hitbox: 1 fills it. At most 4.",
    )
    orientation: Literal["tumble", "forward"] = Field(
        default="tumble",
        description="tumble: it turns over on every axis as it flies. forward: it points along its flight and may "
        "spin about that line.",
    )
    spin_degrees_per_second: float = Field(
        default=0.0,
        ge=0,
        le=MAX_SPIN_DEGREES_PER_SECOND,
        allow_inf_nan=False,
        description="How fast a forward model spins about its flight, at most 1440 (4 turns a second).",
    )

    @model_validator(mode="after")
    def _spin_needs_forward(self) -> SpellModelDef:
        if self.spin_degrees_per_second and self.orientation != "forward":
            raise ValueError("spin_degrees_per_second: only a forward model spins about its flight")
        return self


class SpellTrajectoryDef(StrictModel):
    """How a projectile flies. A trajectory changes where a projectile is, so it is gameplay: the server moves the
    projectile along it and its clients compute the same path."""

    id: ContentId
    kind: Literal["straight"] = Field(
        description="straight: along its aim at its speed, without gravity or drag (ADR 0052). The only kind."
    )


class ParticleDef(StrictModel):
    """One of our particle shapes: ``realmforge:<id>``, a particle type whose spawns carry their colour."""

    id: ContentId
    frames: list[list[ParticleRow]] = Field(
        min_length=1,
        max_length=MAX_PARTICLE_FRAMES,
        description="Its frames, first to last over its life, each 8 rows of 8 pixels: '.' is transparent, 1 to f a "
        "grey from dark to white that the spawn's colour tints. At most 8 frames.",
    )
    lifetime_ticks: list[int] = Field(
        min_length=2,
        max_length=2,
        description="How long it lives, in ticks: the shortest and the longest, each spawn in between. At most 40.",
    )
    size: float = Field(
        gt=0,
        le=MAX_PARTICLE_QUAD_SIZE,
        allow_inf_nan=False,
        description="Half its width in blocks (vanilla's quad size), at most 0.5; vanilla's are about 0.1.",
    )
    gravity: float = Field(
        default=0.0,
        ge=-2,
        le=2,
        allow_inf_nan=False,
        description="How hard it falls: 1 falls like a block, 0 floats, less than 0 rises.",
    )
    friction: float = Field(
        default=0.98,
        gt=0,
        le=1,
        allow_inf_nan=False,
        description="The share of its speed it keeps every tick: 1 never slows, vanilla's default is 0.98.",
    )
    glows: bool = Field(
        default=True,
        description="Whether it is drawn at full brightness, as a spark is, instead of in the light where it is.",
    )

    @model_validator(mode="after")
    def _frames_and_lifetime(self) -> ParticleDef:
        for n, frame in enumerate(self.frames):
            if len(frame) != PARTICLE_SIZE:
                raise ValueError(f"frames.{n}: a frame has {PARTICLE_SIZE} rows, not {len(frame)}")
            if all(row == "." * PARTICLE_SIZE for row in frame):
                raise ValueError(f"frames.{n}: an empty frame draws nothing")
        shortest, longest = self.lifetime_ticks
        if not 1 <= shortest <= longest <= MAX_PARTICLE_LIFETIME_TICKS:
            raise ValueError(
                f"lifetime_ticks: [shortest, longest] with 1 <= shortest <= longest <= {MAX_PARTICLE_LIFETIME_TICKS}"
            )
        return self


def material_texture(look_id: str) -> str:
    """A look's material texture below ``assets/realmforge/``: ``textures/spell/material/<look>.png``."""
    return f"textures/spell/material/{look_id}.png"
