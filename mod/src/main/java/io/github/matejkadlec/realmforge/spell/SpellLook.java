package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

/**
 * An element's look (frost, fire, arcane, stone), an entry of the datapack registry {@code realmforge:spell_look}
 * from {@code content/spell_looks/} (architecture 11.3, ADR 0073): what makes every spell of it read as that element.
 * Synced, as its clients draw it.
 *
 * <ul>
 *   <li><b>Its material</b> is one tileable 16 x 16 texture that every model of the look is drawn with
 *   ({@link #material}): {@code textures/spell/material/<look>.png}.</li>
 *   <li><b>Its colours</b> tint its particles: each spawn takes one at random ({@link #color}), from the colours of
 *   its own emitter when that names some (fire's smoke is black, its embers orange).</li>
 *   <li><b>Its particles</b> are spawned by the clients, from what they already know: a trail from a projectile's own
 *   flight, a burst from the entity event of its hit, a swirl from a cast in progress. No particle crosses the
 *   network. Each of the three is a few emitters (RLM-440: shards and a puff of snow, embers and smoke), which share
 *   the particle budget.</li>
 *   <li><b>Its light</b> says how its models are lit ({@link Light}).</li>
 * </ul>
 *
 * @param colors its colours as RGB, lightest first
 * @param trail what its projectiles leave behind in flight, emitter by emitter
 * @param burst what its projectiles burst into where they hit
 * @param swirl what swirls in toward its projectile while it forms in a caster's hands
 * @param light how its models are lit
 * @param impactSound the sound event of its projectiles' hit, which the server plays there
 */
public record SpellLook(List<Integer> colors, List<Trail> trail, List<Burst> burst, List<Swirl> swirl, Light light,
        Optional<Identifier> impactSound) {
    public static final int MAX_COLORS = 16;
    /** A trail, a burst or a swirl is at most this many emitters. */
    public static final int MAX_EMITTERS = 3;
    /**
     * The particle budget (docs/design/spell-visuals.md): a trail spawns at most this many a block flown, all its
     * emitters together.
     */
    public static final float MAX_TRAIL_PER_BLOCK = 3;
    /** The particle budget: an impact bursts into at most this many at once, all its emitters together. */
    public static final int MAX_BURST_COUNT = 24;
    /** The particle budget: a forming swirl spawns at most this many a tick, all its emitters together. */
    public static final float MAX_SWIRL_PER_TICK = 8;

    /** A list of colours as {@code "#RRGGBB"}: a look's, or an emitter's own. */
    private static final Codec<List<Integer>> COLORS =
            ExtraCodecs.nonEmptyList(ExtraCodecs.STRING_RGB_COLOR.sizeLimitedListOf(MAX_COLORS));

    public static final Codec<SpellLook> CODEC = RecordCodecBuilder.<SpellLook>create(instance -> instance.group(
                    COLORS.fieldOf("colors").forGetter(SpellLook::colors),
                    Trail.CODEC.sizeLimitedListOf(MAX_EMITTERS).optionalFieldOf("trail", List.of())
                            .forGetter(SpellLook::trail),
                    Burst.CODEC.sizeLimitedListOf(MAX_EMITTERS).optionalFieldOf("burst", List.of())
                            .forGetter(SpellLook::burst),
                    Swirl.CODEC.sizeLimitedListOf(MAX_EMITTERS).optionalFieldOf("swirl", List.of())
                            .forGetter(SpellLook::swirl),
                    Light.CODEC.optionalFieldOf("light", Light.WORLD).forGetter(SpellLook::light),
                    Identifier.CODEC.optionalFieldOf("impact_sound").forGetter(SpellLook::impactSound))
            .apply(instance, SpellLook::new)).validate(SpellLook::validate);

    public SpellLook {
        colors = List.copyOf(colors);
        trail = List.copyOf(trail);
        burst = List.copyOf(burst);
        swirl = List.copyOf(swirl);
    }

    /** The emitters of a trail, a burst or a swirl share its budget. */
    private static DataResult<SpellLook> validate(SpellLook look) {
        double perBlock = look.trail.stream().mapToDouble(Trail::perBlock).sum();
        if (perBlock > MAX_TRAIL_PER_BLOCK) {
            return DataResult.error(() -> "A look's trail spawns at most " + MAX_TRAIL_PER_BLOCK
                    + " particles a block flown, not " + perBlock);
        }
        int count = look.burst.stream().mapToInt(Burst::count).sum();
        if (count > MAX_BURST_COUNT) {
            return DataResult.error(() -> "A look's burst is at most " + MAX_BURST_COUNT + " particles, not " + count);
        }
        double perTick = look.swirl.stream().mapToDouble(Swirl::perTick).sum();
        if (perTick > MAX_SWIRL_PER_TICK) {
            return DataResult.error(() -> "A look's swirl spawns at most " + MAX_SWIRL_PER_TICK
                    + " particles a tick, not " + perTick);
        }
        return DataResult.success(look);
    }

    /**
     * Particles a projectile leaves behind as it flies.
     *
     * @param particle one of our particle types ({@link TintedParticles})
     * @param perBlock how many a block flown
     * @param colors the colours its particles take instead of the look's, if it names some
     */
    public record Trail(Identifier particle, float perBlock, Optional<List<Integer>> colors) {
        public static final Codec<Trail> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Identifier.CODEC.fieldOf("particle").forGetter(Trail::particle),
                        Codec.floatRange(0, MAX_TRAIL_PER_BLOCK).fieldOf("per_block").forGetter(Trail::perBlock),
                        COLORS.optionalFieldOf("colors").forGetter(Trail::colors))
                .apply(instance, Trail::new));

        public Trail {
            colors = colors.map(List::copyOf);
        }
    }

    /**
     * Particles a projectile bursts into where it hits.
     *
     * @param particle one of our particle types ({@link TintedParticles})
     * @param count how many at once
     * @param spread how far from the middle of the hit they appear and how fast they fly out, as a share of the
     *     burst's own: 1 fills it, 0 leaves them still at its middle (a ring that widens where a missile hit)
     * @param colors the colours its particles take instead of the look's, if it names some
     */
    public record Burst(Identifier particle, int count, float spread, Optional<List<Integer>> colors) {
        public static final Codec<Burst> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Identifier.CODEC.fieldOf("particle").forGetter(Burst::particle),
                        Codec.intRange(1, MAX_BURST_COUNT).fieldOf("count").forGetter(Burst::count),
                        Codec.floatRange(0, 1).optionalFieldOf("spread", 1.0F).forGetter(Burst::spread),
                        COLORS.optionalFieldOf("colors").forGetter(Burst::colors))
                .apply(instance, Burst::new));

        public Burst {
            colors = colors.map(List::copyOf);
        }
    }

    /**
     * Particles that swirl in toward a projectile while it forms in a caster's hands; a {@code hands} forming shows
     * them alone.
     *
     * @param particle one of our particle types ({@link TintedParticles})
     * @param perTick how many a tick while the cast runs
     * @param colors the colours its particles take instead of the look's, if it names some
     */
    public record Swirl(Identifier particle, float perTick, Optional<List<Integer>> colors) {
        public static final Codec<Swirl> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Identifier.CODEC.fieldOf("particle").forGetter(Swirl::particle),
                        Codec.floatRange(0, MAX_SWIRL_PER_TICK).fieldOf("per_tick").forGetter(Swirl::perTick),
                        COLORS.optionalFieldOf("colors").forGetter(Swirl::colors))
                .apply(instance, Swirl::new));

        public Swirl {
            colors = colors.map(List::copyOf);
        }
    }

    /** How a look's models are lit (Matěj, 2026-10-29, RLM-463's answer 7). */
    public enum Light implements StringRepresentable {
        /** By the light where they are, like an arrow. */
        WORLD("world"),
        /** A few light levels above the light where they are; it lights nothing (frost). */
        GLOW("glow"),
        /**
         * At full brightness (fire). Its light on the blocks round it comes with dynamic lights in the pack
         * (ADR 0075).
         */
        EMIT("emit");

        public static final Codec<Light> CODEC = StringRepresentable.fromEnum(Light::values);

        private final String serializedName;

        Light(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /** One of its colours at random, opaque, for a particle's spawn. */
    public int color(RandomSource random) {
        return color(random, Optional.empty());
    }

    /**
     * A colour at random, opaque, for the spawn of a particle of an emitter with {@code own} colours: one of those,
     * or one of the look's without any.
     */
    public int color(RandomSource random, Optional<List<Integer>> own) {
        List<Integer> from = own.orElse(colors);
        return ARGB.opaque(from.get(random.nextInt(from.size())));
    }

    /**
     * The material texture of {@code look}: {@code <ns>:textures/spell/material/<path>.png}; none for a look that is
     * no registry entry.
     */
    public static Optional<Identifier> material(Holder<SpellLook> look) {
        return look.unwrapKey().map(key -> key.identifier()
                .withPath(path -> "textures/spell/material/" + path + ".png"));
    }
}
