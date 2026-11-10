package io.github.matejkadlec.realmforge.effect;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Immunities (RLM-615, architecture 11.4): while an effect of ours with {@code immune_to} lasts on its holder (Stun
 * Immunity, Frozen Immunity), the effect it names never lands there. {@code MobEffectEvent.Applicable} refuses it, so
 * {@code LivingEntity#addEffect} returns false and changes nothing, as for a boss's own {@code immune_to}.
 */
public final class Immunities {
    private Immunities() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Applicable.class, Immunities::onEffectApplicable);
    }

    /** Whether one of {@code entity}'s effects makes it immune to {@code effect} now. */
    public static boolean isImmune(LivingEntity entity, Holder<MobEffect> effect) {
        for (MobEffectInstance active : entity.getActiveEffects()) {
            if (active.getEffect().value() instanceof StatusEffect status && status.blocks(effect)) {
                return true;
            }
        }
        return false;
    }

    private static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!event.getEntity().level().isClientSide()
                && isImmune(event.getEntity(), event.getEffectInstance().getEffect())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
