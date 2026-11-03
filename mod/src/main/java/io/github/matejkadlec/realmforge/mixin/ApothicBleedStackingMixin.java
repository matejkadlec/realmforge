package io.github.matejkadlec.realmforge.mixin;

import dev.shadowsoffire.apotheosis.mixin.LivingEntityInvoker;
import io.github.matejkadlec.realmforge.compat.apothic_attributes.ApothicBleeds;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * RLM-579: Apotheosis's affixes and gems that stack their effect on reapply (Bloodletting, Devilish, the Tyrannical
 * gem) read the target's current effect in {@code applyEffect} and raise it in place. For Apothic Attributes' Bleeding,
 * which becomes our Bleeding ({@link ApothicBleeds}), this hands them a stand-in at our stack's level and remaining time,
 * sends their raise to the stack, and keeps the stand-in out of the target's effect updates (it never had it, and a
 * player would be told of it). Every other effect goes through as before. Neither mod has an event for the read, hence a
 * mixin (architecture 19.3); {@code @Pseudo}, so the core mod loads without Apotheosis, and with it a changed
 * {@code applyEffect} fails at load (the injectors are required). Tested by the QA mod's {@code ApothicBleedGameTests}.
 */
@Pseudo
@Mixin(targets = {"dev.shadowsoffire.apotheosis.affix.effect.MobEffectAffix",
        "dev.shadowsoffire.apotheosis.socket.gem.bonus.MobEffectBonus"})
public abstract class ApothicBleedStackingMixin {
    @Redirect(method = "applyEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;"
            + "getEffect(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/effect/MobEffectInstance;"))
    private MobEffectInstance realmforge$current(LivingEntity target, Holder<MobEffect> effect) {
        return ApothicBleeds.current(target, effect);
    }

    @Redirect(method = "applyEffect", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffectInstance;update(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private boolean realmforge$update(MobEffectInstance existing, MobEffectInstance next) {
        return ApothicBleeds.update(existing, next);
    }

    @Redirect(method = "applyEffect", at = @At(value = "INVOKE", target = "Ldev/shadowsoffire/apotheosis/mixin/"
            + "LivingEntityInvoker;callOnEffectUpdated(Lnet/minecraft/world/effect/MobEffectInstance;Z"
            + "Lnet/minecraft/world/entity/Entity;)V"))
    private void realmforge$updated(LivingEntityInvoker entity, MobEffectInstance instance, boolean refresh,
            Entity source) {
        if (!ApothicBleeds.isStandIn(instance)) {
            entity.callOnEffectUpdated(instance, refresh, source);
        }
    }

    @Redirect(method = "applyEffect", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffectInstance;onEffectStarted(Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void realmforge$started(MobEffectInstance instance, LivingEntity entity) {
        if (ApothicBleeds.isStandIn(instance)) {
            ApothicBleeds.release(instance);
        } else {
            instance.onEffectStarted(entity);
        }
    }
}
