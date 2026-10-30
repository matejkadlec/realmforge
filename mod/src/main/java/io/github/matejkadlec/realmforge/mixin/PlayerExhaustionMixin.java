package io.github.matejkadlec.realmforge.mixin;

import io.github.matejkadlec.realmforge.stats.ExhaustionReduction;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lowers the food exhaustion a player gains by their Stamina ({@code stats/ExhaustionReduction}, architecture 8.1,
 * RLM-171). Vanilla adds every exhaustion from sprinting, jumping, swimming, attacking, taking damage, mining and the
 * Hunger effect through {@link Player#causeFoodExhaustion}, and neither NeoForge nor vanilla has an event or an
 * attribute that scales it, hence a mixin (architecture 19.3). Tested by the QA mod's {@code StaminaGameTests}.
 */
@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float realmforge$lowerByStamina(float amount) {
        return amount * (float) ExhaustionReduction.factor((Player) (Object) this);
    }
}
