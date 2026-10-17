package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * {@code realmforge:knockback}: pushes the target along the spell's direction (away from the caster, along a
 * projectile's flight, out from an area's middle) with vanilla's knockback, which Knockback Resistance weakens, and
 * interrupts its cast unless that may be cast while moving (architecture 11.1, 11.2). The only way a spell pushes: its
 * school damage never knocks back (architecture 9.1, RLM-214).
 *
 * @param strength as vanilla's knockback: a melee hit pushes with 0.4
 */
public record KnockbackEffect(double strength) implements EntityEffect {
    public static final double MAX_STRENGTH = 5;

    public static final MapCodec<KnockbackEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.doubleRange(0, MAX_STRENGTH).fieldOf("strength").forGetter(KnockbackEffect::strength))
            .apply(instance, KnockbackEffect::new));

    @Override
    public MapCodec<KnockbackEffect> codec() {
        return MAP_CODEC;
    }

    @Override
    public void applyTo(EffectContext context, LivingEntity target, SpellTarget spellTarget) {
        Vec3 before = target.getDeltaMovement();
        Vec3 push = spellTarget.direction();
        // Vanilla's knockback pushes away from the point (x, z) gives, so the push's opposite goes in.
        target.knockback(strength, -push.x, -push.z);
        if (!target.getDeltaMovement().equals(before)) {
            // Players move themselves: their client applies the push once the server sends it.
            target.hurtMarked = true;
            Casting.knockedBack(target);
        }
    }
}
