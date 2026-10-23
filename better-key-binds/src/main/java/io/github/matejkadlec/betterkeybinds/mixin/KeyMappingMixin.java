package io.github.matejkadlec.betterkeybinds.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.matejkadlec.betterkeybinds.SecondaryKeys;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.neoforged.neoforge.client.extensions.IKeyMappingExtension;
import net.neoforged.neoforge.client.settings.KeyMappingLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Merges a key bind's secondary key into it, for every caller that asks the key bind: held while either key is held,
 * the clicks of both keys, a screen's key check matching either key. Reset and the reset state cover both keys, so
 * the Key Binds screen's Reset, "Reset Keys" and their enabled state include the secondary key. Where a key is one key
 * bind's primary key and another's secondary key, the primary key bind wins. No event or API changes how a key
 * mapping answers these, hence a mixin.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements IKeyMappingExtension {
    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean betterKeyBinds$isDown(boolean down) {
        return SecondaryKeys.isDown(self(), down);
    }

    @ModifyReturnValue(method = "consumeClick", at = @At("RETURN"))
    private boolean betterKeyBinds$consumeClick(boolean consumed) {
        return SecondaryKeys.consumeClick(self(), consumed);
    }

    @ModifyReturnValue(method = "matches", at = @At("RETURN"))
    private boolean betterKeyBinds$matches(boolean matches, KeyEvent event) {
        return SecondaryKeys.matches(self(), matches, secondary -> secondary.matches(event));
    }

    @ModifyReturnValue(method = "matchesMouse", at = @At("RETURN"))
    private boolean betterKeyBinds$matchesMouse(boolean matches, MouseButtonEvent event) {
        return SecondaryKeys.matches(self(), matches, secondary -> secondary.matchesMouse(event));
    }

    /**
     * NeoForge's key check, which its container screens use (Close Inventory, Drop, Pick Block, Swap Offhand, the
     * hotbar keys), merged like {@code matches}. KeyMapping inherits the interface's default method, so this override
     * is added to it.
     */
    @Override
    public boolean isActiveAndMatches(InputConstants.Key keyCode) {
        return SecondaryKeys.isActiveAndMatches(self(), keyCode);
    }

    /**
     * A pressed or clicked key reaches another key bind's secondary key only when no primary key bind takes it
     * ({@link SecondaryKeys#primaryKeysWin}): the one lookup vanilla's {@code click} and {@code set} share.
     */
    @WrapOperation(
            method = "forAllKeyMappings(Lcom/mojang/blaze3d/platform/InputConstants$Key;Ljava/util/function/Consumer;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/settings/KeyMappingLookup;"
                    + "getAll(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)Ljava/util/List;"))
    private static List<KeyMapping> betterKeyBinds$primaryKeysWin(KeyMappingLookup lookup, InputConstants.Key key,
            boolean releasing, Operation<List<KeyMapping>> original) {
        return SecondaryKeys.primaryKeysWin(original.call(lookup, key, releasing), releasing);
    }

    /** Default only while the secondary key is unbound too: its default. */
    @ModifyReturnValue(method = "isDefault", at = @At("RETURN"))
    private boolean betterKeyBinds$isDefault(boolean isDefault) {
        return isDefault && SecondaryKeys.isUnbound(self());
    }

    /**
     * {@link IKeyMappingExtension#setToDefault}'s reset, plus the secondary key's. KeyMapping inherits the interface's
     * default method, so this override is added to it; both Key Binds screens reset through it.
     */
    @Override
    public void setToDefault() {
        setKeyModifierAndCode(getDefaultKeyModifier(), self().getDefaultKey());
        SecondaryKeys.unbind(self());
    }

    private KeyMapping self() {
        return (KeyMapping) (Object) this;
    }
}
