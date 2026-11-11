package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
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
 *   <li><b>Its colours</b> tint its particles: each spawn takes one at random ({@link #color}).</li>
 *   <li><b>Its particles</b> are spawned by the clients, from what they already know: a trail from a projectile's own
 *   flight, a burst from the entity event of its hit, a swirl from a cast in progress. No particle crosses the
 *   network.</li>
 *   <li><b>Its light</b> says how its models are lit ({@link Light}).</li>
 * </ul>
 *
 * @param colors its colours as RGB, lightest first
 * @param trail what its projectiles leave behind in flight
 * @param burst what its projectiles burst into where they hit
 * @param swirl what swirls in toward its projectile while it forms in a caster's hands
 * @param light how its models are lit
 * @param impactSound the sound event of its projectiles' hit, which the server plays there
 */
public record SpellLook(List<Integer> colors, Optional<Trail> trail, Optional<Burst> burst, Optional<Swirl> swirl,
        Light light, Optional<Identifier> impactSound) {
    public static final int MAX_COLORS = 16;
    /** The particle budget (docs/design/spell-visuals.md): a trail spawns at most this many a block flown. */
    public static final float MAX_TRAIL_PER_BLOCK = 3;
    /** The particle budget: an impact bursts into at most this many at once. */
    public static final int MAX_BURST_COUNT = 24;
    /** The particle budget: a forming swirl spawns at most this many a tick. */
    public static final float MAX_SWIRL_PER_TICK = 8;

    public static final Codec<SpellLook> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ExtraCodecs.nonEmptyList(ExtraCodecs.STRING_RGB_COLOR.sizeLimitedListOf(MAX_COLORS))
                            .fieldOf("colors").forGetter(SpellLook::colors),
                    Trail.CODEC.optionalFieldOf("trail").forGetter(SpellLook::trail),
                    Burst.CODEC.optionalFieldOf("burst").forGetter(SpellLook::burst),
                    Swirl.CODEC.optionalFieldOf("swirl").forGetter(SpellLook::swirl),
                    Light.CODEC.optionalFieldOf("light", Light.WORLD).forGetter(SpellLook::light),
                    Identifier.CODEC.optionalFieldOf("impact_sound").forGetter(SpellLook::impactSound))
            .apply(instance, SpellLook::new));

    public SpellLook {
        colors = List.copyOf(colors);
    }

    /**
     * The particles a projectile leaves behind as it flies.
     *
     * @param particle one of our particle types ({@link TintedParticles})
     * @param perBlock how many a block flown
     */
    public record Trail(Identifier particle, float perBlock) {
        public static final Codec<Trail> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Identifier.CODEC.fieldOf("particle").forGetter(Trail::particle),
                        Codec.floatRange(0, MAX_TRAIL_PER_BLOCK).fieldOf("per_block").forGetter(Trail::perBlock))
                .apply(instance, Trail::new));
    }

    /**
     * The particles a projectile bursts into where it hits.
     *
     * @param particle one of our particle types ({@link TintedParticles})
     * @param count how many at once
     */
    public record Burst(Identifier particle, int count) {
        public static final Codec<Burst> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Identifier.CODEC.fieldOf("particle").forGetter(Burst::particle),
                        Codec.intRange(1, MAX_BURST_COUNT).fieldOf("count").forGetter(Burst::count))
                .apply(instance, Burst::new));
    }

    /**
     * The particles that swirl in toward a projectile while it forms in a caster's hands; a {@code hands} forming
     * shows them alone.
     *
     * @param particle one of our particle types ({@link TintedParticles})
     * @param perTick how many a tick while the cast runs
     */
    public record Swirl(Identifier particle, float perTick) {
        public static final Codec<Swirl> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Identifier.CODEC.fieldOf("particle").forGetter(Swirl::particle),
                        Codec.floatRange(0, MAX_SWIRL_PER_TICK).fieldOf("per_tick").forGetter(Swirl::perTick))
                .apply(instance, Swirl::new));
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
        return ARGB.opaque(colors.get(random.nextInt(colors.size())));
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
