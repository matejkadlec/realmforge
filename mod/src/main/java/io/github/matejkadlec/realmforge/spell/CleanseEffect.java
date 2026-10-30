package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code realmforge:cleanse}: takes every harmful status effect off the target (C4; the Human's Will To Survive,
 * RLM-219): debuffs, damage over time and loss of control such as a stun, ours and vanilla's alike. An effect that keeps
 * itself (by cancelling {@code MobEffectEvent.Remove}) stays.
 */
public record CleanseEffect() implements EntityEffect {
    public static final CleanseEffect INSTANCE = new CleanseEffect();
    public static final MapCodec<CleanseEffect> MAP_CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<CleanseEffect> codec() {
        return MAP_CODEC;
    }

    @Override
    public void applyTo(EffectContext context, LivingEntity target, SpellTarget spellTarget) {
        List<Holder<MobEffect>> harmful = target.getActiveEffects().stream()
                .map(MobEffectInstance::getEffect)
                .filter(effect -> effect.value().getCategory() == MobEffectCategory.HARMFUL)
                .toList();
        harmful.forEach(target::removeEffect);
    }
}
