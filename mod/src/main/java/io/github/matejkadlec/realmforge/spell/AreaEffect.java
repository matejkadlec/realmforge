package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

/**
 * {@code realmforge:area}: places a {@link SpellArea} at the target's position (the caster's feet, an enemy's, a
 * ground point), which runs its own {@code effects} on every enemy (or ally) inside it as it appears and then every
 * interval, once per interval of its duration (architecture 11.3).
 *
 * @param radius in blocks
 * @param durationTicks how long it lasts, a whole number of intervals
 * @param intervalTicks the ticks between two runs of its effects
 * @param affects whom its effects act on
 * @param effects what it does to each entity inside each time it acts, in order
 */
public record AreaEffect(double radius, int durationTicks, int intervalTicks, Affects affects,
        List<SpellEffect> effects) implements SpellEffect {
    public static final double MAX_RADIUS = 16;
    public static final int MAX_DURATION_TICKS = 1200;

    public static final MapCodec<AreaEffect> MAP_CODEC = RecordCodecBuilder.<AreaEffect>mapCodec(instance -> instance
                    .group(Codec.doubleRange(0, MAX_RADIUS).fieldOf("radius").forGetter(AreaEffect::radius),
                            Codec.intRange(1, MAX_DURATION_TICKS).fieldOf("duration_ticks")
                                    .forGetter(AreaEffect::durationTicks),
                            Codec.intRange(1, MAX_DURATION_TICKS).fieldOf("interval_ticks")
                                    .forGetter(AreaEffect::intervalTicks),
                            Affects.CODEC.optionalFieldOf("affects", Affects.ENEMIES).forGetter(AreaEffect::affects),
                            ExtraCodecs.nonEmptyList(SpellEffect.CODEC.listOf()).fieldOf("effects")
                                    .forGetter(AreaEffect::effects))
                    .apply(instance, AreaEffect::new))
            .validate(area -> area.durationTicks() % area.intervalTicks() == 0
                    ? DataResult.success(area)
                    : DataResult.error(() -> "An area lasts a whole number of intervals: "
                            + area.durationTicks() + " ticks, every " + area.intervalTicks()));

    /** Whom an area's effects act on. */
    public enum Affects implements StringRepresentable {
        ENEMIES("enemies"),
        ALLIES("allies");

        public static final Codec<Affects> CODEC = StringRepresentable.fromEnum(Affects::values);

        private final String serializedName;

        Affects(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    public AreaEffect {
        effects = List.copyOf(effects);
    }

    @Override
    public MapCodec<AreaEffect> codec() {
        return MAP_CODEC;
    }

    @Override
    public void apply(EffectContext context, SpellTarget target) {
        SpellArea.place(context, this, target.position());
    }
}
