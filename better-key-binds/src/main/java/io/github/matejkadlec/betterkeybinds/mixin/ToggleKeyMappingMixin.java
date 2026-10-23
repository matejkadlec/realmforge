package io.github.matejkadlec.betterkeybinds.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.matejkadlec.betterkeybinds.SecondaryKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sneak and Sprint are toggle key mappings, whose {@code isDown} replaces KeyMapping's, so {@link KeyMappingMixin}'s
 * merge does not reach them: held while either key is held. In toggle mode the secondary key flips the toggle itself
 * ({@code SecondaryKeyMapping#setDown}).
 */
@Mixin(ToggleKeyMapping.class)
public abstract class ToggleKeyMappingMixin {
    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean betterKeyBinds$isDown(boolean down) {
        return SecondaryKeys.isDown((KeyMapping) (Object) this, down);
    }
}
