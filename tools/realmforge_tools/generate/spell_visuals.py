"""The spell visuals kit (RLM-439, ADR 0073, architecture 11.3): what the game needs beyond the datapack entries of
looks, models and trajectories (datapack.py).

- Each look's placeholder material, ``assets/realmforge/textures/spell/material/<look>.png``, until its art exists.
- Each ``cubes`` model's GeckoLib model, ``assets/realmforge/geckolib/models/spell/<id>.geo.json``, from its source
  in ``art/models/spell/`` with every face mapped onto the material (``art/spell_models.py``, RLM-640).
- Each particle of ``content/particles/``: its ``ParticleType`` registration with how it moves
  (``GeneratedParticles``), its sprite set ``assets/realmforge/particles/<id>.json`` and its frames
  ``assets/realmforge/textures/particle/<id>_<n>.png``.
- The QA mod's expectations of all four content types.
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.art import spell_models
from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.generate.textures import particle_frame, placeholder_material_texture
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.spell_visuals import ParticleDef, SpellLookDef, material_texture

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
SOURCES = "content/particles/"
EXPECTATION_SOURCES = (
    "content/spell_looks/, content/spell_models/, content/spell_trajectories/, content/particles/"
)


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    assets = paths.mod_generated / "resources" / "assets" / MOD_ID
    files = {
        java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedParticles"): _registrations(
            content
        ),
        java_file_path(
            paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedSpellVisualsExpectations"
        ): _expectations(content),
    }
    for entry in content.spell_looks:
        look = entry.model
        files[assets / material_texture(look.id)] = placeholder_material_texture(
            f"{MOD_ID}:{look.id}", [_rgb(color) for color in look.colors]
        )
    for entry in content.spell_models:
        model = entry.model
        if model.shape == spell_models.CUBES:
            # Validation read the source already (spell_models.problems), so it is there and sound.
            source = spell_models.read(spell_models.source_path(paths, model.id))
            files[assets / spell_models.asset_path(model.id)] = dump_json(
                spell_models.geometry(model.id, source)
            )
    for entry in content.particles:
        particle = entry.model
        files[assets / "particles" / f"{particle.id}.json"] = dump_json(
            {"textures": [f"{MOD_ID}:{name}" for name in frame_names(particle)]}
        )
        for name, frame in zip(frame_names(particle), particle.frames, strict=True):
            files[assets / "textures" / "particle" / f"{name}.png"] = particle_frame(frame)
    return files


def frame_names(particle: ParticleDef) -> list[str]:
    """The sprites of a particle's frames, first to last: ``<id>_0``, ``<id>_1``, ..."""
    return [f"{particle.id}_{n}" for n in range(len(particle.frames))]


def _rgb(color: str) -> int:
    return int(color.removeprefix("#"), 16)


def _float(value: float) -> str:
    """A Java float literal; repr() gives the shortest text that reads back as the same double."""
    return f"{float(value)!r}F"


def _motion(particle: ParticleDef) -> str:
    shortest, longest = particle.lifetime_ticks
    glows = "true" if particle.glows else "false"
    return (
        f"new TintedParticles.Motion({shortest}, {longest}, {_float(particle.size)}, {_float(particle.gravity)}, "
        f"{_float(particle.friction)}, {glows})"
    )


def _registrations(content: Content) -> bytes:
    fields = "".join(
        f"    /** {{@code {MOD_ID}:{entry.model.id}}} from {{@code {entry.source}}}. */\n"
        f"    public static final TintedParticles.Entry {constant_name(entry.model.id)} =\n"
        f"            register({string_literal(entry.model.id)},\n"
        f"                    {_motion(entry.model)});\n"
        "\n"
        for entry in content.particles
    )
    every = ", ".join(constant_name(entry.model.id) for entry in content.particles)
    source = (
        header(Path(__file__), SOURCES) + f"package {GENERATED_PACKAGE};\n"
        "\n"
        f"import {JAVA_PACKAGE}.Realmforge;\n"
        f"import {JAVA_PACKAGE}.spell.TintedParticles;\n"
        "import java.util.List;\n"
        "import net.minecraft.core.particles.ParticleType;\n"
        "import net.minecraft.core.registries.Registries;\n"
        "import net.neoforged.bus.api.IEventBus;\n"
        "import net.neoforged.neoforge.registries.DeferredRegister;\n"
        "\n"
        "/**\n"
        " * Our particle types, generated from {@code content/particles/} (architecture 11.3): each spawn carries its\n"
        " * colour, and each type moves as its data says. Their sprites are\n"
        " * {@code assets/realmforge/particles/<id>.json}; the client's provider is {@code client/particle}.\n"
        " */\n"
        "public final class GeneratedParticles {\n"
        "    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =\n"
        "            DeferredRegister.create(Registries.PARTICLE_TYPE, Realmforge.MOD_ID);\n"
        "\n" + fields + "    /** Every particle above, in id order. */\n"
        f"    public static final List<TintedParticles.Entry> ALL = List.of({every});\n"
        "\n"
        "    private GeneratedParticles() {\n"
        "    }\n"
        "\n"
        "    /** Registers the particle types. Call once from the mod constructor. */\n"
        "    public static void register(IEventBus modEventBus) {\n"
        "        PARTICLE_TYPES.register(modEventBus);\n"
        "    }\n"
        "\n"
        "    private static TintedParticles.Entry register(String path, TintedParticles.Motion motion) {\n"
        "        return new TintedParticles.Entry(PARTICLE_TYPES.register(path, TintedParticles::type), motion);\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _optional_string(value: str | None) -> str:
    return f"Optional.of({string_literal(value)})" if value is not None else "Optional.empty()"


def _look(look: SpellLookDef) -> str:
    colors = ", ".join(string_literal(color) for color in look.colors)
    trail = look.trail
    burst = look.burst
    swirl = look.swirl
    return (
        "            new LookExpectation(\n"
        f"                    {string_literal(f'{MOD_ID}:{look.id}')},\n"
        f"                    List.of({colors}),\n"
        f"                    {_optional_string(trail.particle if trail is not None else None)},\n"
        f"                    {float(trail.per_block) if trail is not None else 0.0!r},\n"
        f"                    {_optional_string(burst.particle if burst is not None else None)},\n"
        f"                    {burst.count if burst is not None else 0},\n"
        f"                    {_optional_string(swirl.particle if swirl is not None else None)},\n"
        f"                    {float(swirl.per_tick) if swirl is not None else 0.0!r},\n"
        f"                    {string_literal(look.light)},\n"
        f"                    {_optional_string(look.impact_sound)})"
    )


def _list(rows: list[str]) -> str:
    return "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"


def _expectations(content: Content) -> bytes:
    looks = [_look(entry.model) for entry in content.spell_looks]
    models = [
        f"            new ModelExpectation({string_literal(f'{MOD_ID}:{entry.model.id}')}, "
        f"{string_literal(entry.model.shape)}, {float(entry.model.scale)!r}, "
        f"{string_literal(entry.model.orientation)}, {float(entry.model.spin_degrees_per_second)!r})"
        for entry in content.spell_models
    ]
    trajectories = [
        f"            new TrajectoryExpectation({string_literal(f'{MOD_ID}:{entry.model.id}')}, "
        f"{string_literal(entry.model.kind)})"
        for entry in content.spell_trajectories
    ]
    particles = [
        f"            new ParticleExpectation({string_literal(f'{MOD_ID}:{entry.model.id}')}, "
        f"{len(entry.model.frames)}, {entry.model.lifetime_ticks[0]}, {entry.model.lifetime_ticks[1]})"
        for entry in content.particles
    ]
    source = (
        header(Path(__file__), EXPECTATION_SOURCES) + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "import java.util.Optional;\n"
        "\n"
        "/**\n"
        " * What the spell visuals kit's content says (looks, models, trajectories, particles), so GameTests can check\n"
        " * that the game agrees. Ids are full resource locations.\n"
        " */\n"
        "public final class GeneratedSpellVisualsExpectations {\n"
        "    /**\n"
        '     * A look: its colours as {@code "#RRGGBB"}, lightest first; its trail\'s particle and how many a block\n'
        "     * flown, its burst's particle and how many at once, its swirl's particle and how many a tick (0 without\n"
        "     * one); how its models are lit; its impact sound.\n"
        "     */\n"
        "    public record LookExpectation(String id, List<String> colors, Optional<String> trailParticle,\n"
        "            double trailPerBlock, Optional<String> burstParticle, int burstCount, Optional<String> swirlParticle,\n"
        "            double swirlPerTick, String light, Optional<String> impactSound) {\n"
        "    }\n"
        "\n"
        "    /** A model: its shape, its scale, how it turns in flight and its spin in degrees per second. */\n"
        "    public record ModelExpectation(String id, String shape, double scale, String orientation,\n"
        "            double spinDegreesPerSecond) {\n"
        "    }\n"
        "\n"
        "    /** A trajectory: its kind. */\n"
        "    public record TrajectoryExpectation(String id, String kind) {\n"
        "    }\n"
        "\n"
        "    /** A particle: how many frames its sprite set has, and its shortest and longest life in ticks. */\n"
        "    public record ParticleExpectation(String id, int frames, int shortestLifetimeTicks,\n"
        "            int longestLifetimeTicks) {\n"
        "    }\n"
        "\n"
        f"    public static final List<LookExpectation> LOOKS = {_list(looks)};\n"
        "\n"
        f"    public static final List<ModelExpectation> MODELS = {_list(models)};\n"
        "\n"
        f"    public static final List<TrajectoryExpectation> TRAJECTORIES = {_list(trajectories)};\n"
        "\n"
        f"    public static final List<ParticleExpectation> PARTICLES = {_list(particles)};\n"
        "\n"
        "    private GeneratedSpellVisualsExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
