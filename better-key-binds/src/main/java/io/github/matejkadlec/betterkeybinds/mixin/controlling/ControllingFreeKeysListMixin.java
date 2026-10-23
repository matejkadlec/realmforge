package io.github.matejkadlec.betterkeybinds.mixin.controlling;

import io.github.matejkadlec.betterkeybinds.SecondaryKeys;
import java.util.List;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Controlling's "Toggle Free" lists the keys no key bind uses; a key used as a secondary key is not free. The list
 * collects the key binds to compare with once, before it first fills itself, and offers no way to add any, hence a
 * mixin. Applied only when Controlling is loaded.
 */
@Pseudo
@Mixin(targets = "com.blamejared.controlling.client.FreeKeysList")
public abstract class ControllingFreeKeysListMixin {
    @Shadow
    @Final
    private List<KeyMapping> keyBindings;

    @Inject(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/blamejared/controlling/client/FreeKeysList;recalculate()V"))
    private void betterKeyBinds$secondaryKeysAreNotFree(CallbackInfo ci) {
        keyBindings.addAll(SecondaryKeys.bound());
    }
}
