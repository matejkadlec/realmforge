package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * {@code realmforge:dash}: throws the caster toward the target (an enemy, a ground point, or along a direction) with a
 * burst of speed, which friction then slows as for any push: about 2 blocks on the ground for a strength of 1. A
 * player's client moves the player once the server sends the burst.
 *
 * @param strength the burst's speed in blocks per tick
 */
public record DashEffect(double strength) implements SpellEffect {
    public static final double MAX_STRENGTH = 5;

    public static final MapCodec<DashEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.doubleRange(0, MAX_STRENGTH).fieldOf("strength").forGetter(DashEffect::strength))
            .apply(instance, DashEffect::new));

    @Override
    public MapCodec<DashEffect> codec() {
        return MAP_CODEC;
    }

    @Override
    public void apply(EffectContext context, SpellTarget target) {
        LivingEntity caster = context.caster();
        Vec3 direction = target.kind() == SpellTarget.Kind.DIRECTION
                ? target.direction()
                : target.position().subtract(caster.position());
        if (direction.lengthSqr() < 1e-8) {
            return;
        }
        caster.setDeltaMovement(direction.normalize().scale(strength));
        caster.hurtMarked = true;
    }
}
