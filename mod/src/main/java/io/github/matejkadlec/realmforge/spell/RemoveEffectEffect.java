package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code realmforge:remove_effect}: takes a status effect off the target, if it has it (architecture 11.4). An effect
 * that keeps itself (by cancelling {@code MobEffectEvent.Remove}) stays.
 *
 * @param effect the status effect
 */
public record RemoveEffectEffect(Holder<MobEffect> effect) implements EntityEffect {
    public static final MapCodec<RemoveEffectEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(MobEffect.CODEC.fieldOf("effect").forGetter(RemoveEffectEffect::effect))
            .apply(instance, RemoveEffectEffect::new));

    @Override
    public MapCodec<RemoveEffectEffect> codec() {
        return MAP_CODEC;
    }

    @Override
    public void applyTo(EffectContext context, LivingEntity target, SpellTarget spellTarget) {
        target.removeEffect(effect);
    }
}
