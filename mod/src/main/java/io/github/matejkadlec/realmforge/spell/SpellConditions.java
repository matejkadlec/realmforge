package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;

/**
 * When a spell may be cast beyond its cooldowns and cost (RLM-540; architecture 11.2): only while its window is open
 * ({@link SpellWindow}), only on a target below a share of its health (Execute). One field of the spell's codec for
 * both, as a record codec takes at most 16.
 *
 * @param window the window it is usable in, if it has one
 * @param targetHealthBelow the share of its health a target must be below, if any (Execute's 0.2)
 */
public record SpellConditions(Optional<SpellWindow> window, Optional<Double> targetHealthBelow) {
    public static final SpellConditions NONE = new SpellConditions(Optional.empty(), Optional.empty());

    /** The fields at the top of a spell's JSON. */
    public static final MapCodec<SpellConditions> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    SpellWindow.CODEC.optionalFieldOf("window").forGetter(SpellConditions::window),
                    Codec.doubleRange(0, 1).optionalFieldOf("target_health_below")
                            .forGetter(SpellConditions::targetHealthBelow))
            .apply(instance, SpellConditions::new));

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
}
