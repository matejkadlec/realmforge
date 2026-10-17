package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code realmforge:apply_effect}: gives the target a status effect, from the caster (architecture 11.4). Vanilla's
 * until C3c (RLM-26) adds ours. Combat effects keep their own short time scale (research SP-8), so at most a minute.
 *
 * @param effect the status effect
 * @param durationTicks how long it lasts
 * @param amplifier its strength above the first (0 is Slowness I)
 */
public record ApplyEffectEffect(Holder<MobEffect> effect, int durationTicks, int amplifier) implements EntityEffect {
    /** The longest a spell's status effect lasts: a minute. */
    public static final int MAX_DURATION_TICKS = 1200;
    public static final int MAX_AMPLIFIER = 9;

    public static final MapCodec<ApplyEffectEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    MobEffect.CODEC.fieldOf("effect").forGetter(ApplyEffectEffect::effect),
                    Codec.intRange(1, MAX_DURATION_TICKS).fieldOf("duration_ticks")
                            .forGetter(ApplyEffectEffect::durationTicks),
                    Codec.intRange(0, MAX_AMPLIFIER).optionalFieldOf("amplifier", 0)
                            .forGetter(ApplyEffectEffect::amplifier))
            .apply(instance, ApplyEffectEffect::new));

    @Override
    public MapCodec<ApplyEffectEffect> codec() {
        return MAP_CODEC;
    }

    @Override
    public void applyTo(EffectContext context, LivingEntity target, SpellTarget spellTarget) {
        target.addEffect(new MobEffectInstance(effect, durationTicks, amplifier), context.caster());
    }
}
