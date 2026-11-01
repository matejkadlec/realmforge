package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

/**
 * When a spell may be cast beyond its cooldowns and cost (RLM-540; architecture 11.2): only while its window is open
 * ({@link SpellWindow}), only on a target below a share of its health (Execute), only on a target at a distance that
 * grows with the rank (RLM-543: Charge). One field of the spell's codec for all, as a record codec takes at most 16.
 *
 * @param window the window it is usable in, if it has one
 * @param targetHealthBelow the share of its health a target must be below, if any (Execute's 0.2)
 * @param targetDistance how near and how far its target must be, if that is limited (Charge's 3 to 10 blocks)
 */
public record SpellConditions(Optional<SpellWindow> window, Optional<Double> targetHealthBelow,
        Optional<TargetDistance> targetDistance) {
    public static final SpellConditions NONE = new SpellConditions(Optional.empty(), Optional.empty(),
            Optional.empty());

    /** The fields at the top of a spell's JSON. */
    public static final MapCodec<SpellConditions> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    SpellWindow.CODEC.optionalFieldOf("window").forGetter(SpellConditions::window),
                    Codec.doubleRange(0, 1).optionalFieldOf("target_health_below")
                            .forGetter(SpellConditions::targetHealthBelow),
                    TargetDistance.CODEC.optionalFieldOf("target_distance").forGetter(SpellConditions::targetDistance))
            .apply(instance, SpellConditions::new));

    /** A window and a health condition, without a target distance. */
    public SpellConditions(Optional<SpellWindow> window, Optional<Double> targetHealthBelow) {
        this(window, targetHealthBelow, Optional.empty());
    }

    /** Why {@code target} fails the health condition, if it does: no target, or one at or above the share. */
    public Optional<CastFailure> targetFailure(LivingEntity target) {
        if (targetHealthBelow.isEmpty()) {
            return Optional.empty();
        }
        if (target == null || target.getMaxHealth() <= 0) {
            return Optional.of(CastFailure.NO_TARGET);
        }
        return target.getHealth() / target.getMaxHealth() < targetHealthBelow.get() ? Optional.empty()
                : Optional.of(CastFailure.TARGET_HEALTH_TOO_HIGH);
    }

    /**
     * Why {@code target} fails the target distance of a cast at rank {@code level}, if it does: nearer than its least,
     * or farther than its most at the rank (from {@code caster}'s eyes to the nearest point of the target's box, as a
     * range is measured, {@link Targets#distance}).
     */
    public Optional<CastFailure> distanceFailure(LivingEntity caster, LivingEntity target, int level) {
        if (targetDistance.isEmpty() || target == null) {
            return Optional.empty();
        }
        double distance = Targets.distance(caster, target);
        if (distance < targetDistance.get().min()) {
            return Optional.of(CastFailure.TOO_CLOSE);
        }
        return distance > targetDistance.get().maxAt(level) ? Optional.of(CastFailure.OUT_OF_RANGE)
                : Optional.empty();
    }

    /**
     * How near and how far a spell's target must be (RLM-543: Charge reaches 3 to 10 blocks, 12 at rank 2), in blocks:
     * at least {@code min}, at most {@code max} at rank 1 and {@code maxPerLevel} more for every rank above it. The
     * spell's targeting range is the farthest at its highest rank.
     */
    public record TargetDistance(double min, double max, double maxPerLevel) {
        public static final Codec<TargetDistance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.doubleRange(0, Targets.MAX_RANGE).optionalFieldOf("min", 0.0)
                                .forGetter(TargetDistance::min),
                        Codec.doubleRange(0, Targets.MAX_RANGE).fieldOf("max").forGetter(TargetDistance::max),
                        Codec.doubleRange(0, Targets.MAX_RANGE).optionalFieldOf("max_per_level", 0.0)
                                .forGetter(TargetDistance::maxPerLevel))
                .apply(instance, TargetDistance::new));

        /** The farthest at rank {@code level}, in blocks. */
        public double maxAt(int level) {
            return SpellScaling.atLevel(max, maxPerLevel, level);
        }
    }
}
