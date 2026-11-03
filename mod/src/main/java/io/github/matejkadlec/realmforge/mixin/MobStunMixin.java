package io.github.matejkadlec.realmforge.mixin;

import io.github.matejkadlec.realmforge.effect.Stuns;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips a stunned mob's AI step ({@code effect/Stuns}, architecture 11.4, RLM-532), as vanilla skips an immobile
 * one's (a ravager's own stun): its goals, its brain (a piglin's crossbow, a breeze's wind charges), its navigation
 * and its move, look and jump controls all wait. Goals without control flags (an evoker's spells, a ghast's
 * fireballs) and brain-driven mobs cannot be stopped from outside otherwise, and NeoForge has no event before the
 * step, hence a mixin (architecture 19.3). Tested by the QA mod's {@code StunGameTests}.
 */
@Mixin(Mob.class)
public abstract class MobStunMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void realmforge$waitWhileStunned(CallbackInfo callback) {
        if (Stuns.holdStill((Mob) (Object) this)) {
            callback.cancel();
        }
    }
}
