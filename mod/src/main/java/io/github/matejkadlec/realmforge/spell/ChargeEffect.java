package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code realmforge:charge} (RLM-543, the Arms Warrior's Charge): rushes the caster at its enemy target at {@code speed}
 * blocks a second until it stands in front of it, then runs {@code effects} on every enemy within {@code landingRadius}
 * blocks of where it lands (Charge's stun). {@link Charges} steers the rush on the server every tick, a player's
 * through the velocity its client receives, as a knockback reaches it.
 *
 * @param speed how fast the caster rushes, in blocks per second
 * @param landingRadius the blocks round the landing whose enemies the effects reach
 * @param effects what the landing does to each enemy round it
 */
public record ChargeEffect(double speed, double landingRadius, List<SpellEffect> effects) implements EntityEffect {
    /** The fastest a charge rushes: 3 blocks a tick. */
    public static final double MAX_SPEED = 60;

    public static final MapCodec<ChargeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.doubleRange(0, MAX_SPEED).fieldOf("speed").forGetter(ChargeEffect::speed),
                    Codec.doubleRange(0, AreaEffect.MAX_RADIUS).fieldOf("landing_radius")
                            .forGetter(ChargeEffect::landingRadius),
                    SpellEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(ChargeEffect::effects))
            .apply(instance, ChargeEffect::new));

    public ChargeEffect {
        effects = List.copyOf(effects);
    }

    @Override
    public MapCodec<ChargeEffect> codec() {
        return MAP_CODEC;
    }

    @Override
    public void applyTo(EffectContext context, LivingEntity target, SpellTarget spellTarget) {
        Charges.start(context, target, this);
    }
}
