package io.github.matejkadlec.betterkeybinds.mixin.controlling;

import com.blamejared.controlling.api.DisplayMode;
import com.blamejared.controlling.api.entries.IKeyEntry;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.matejkadlec.betterkeybinds.Conflicts;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Controlling's "Show Conflicts" filter also lists the rows whose conflict involves a secondary key: a primary key
 * against another key bind's secondary key, or a secondary key against any other key. Its filter compares primary
 * keys only and offers no way to extend it, hence a mixin. Applied only when Controlling is loaded.
 */
@Pseudo
@Mixin(DisplayMode.class)
public abstract class ControllingDisplayModeMixin {
    @ModifyReturnValue(method = "getPredicate", at = @At("RETURN"))
    private Predicate<KeyBindsList.Entry> betterKeyBinds$secondaryConflicts(Predicate<KeyBindsList.Entry> predicate) {
        if ((Object) this != DisplayMode.CONFLICTING) {
            return predicate;
        }
        return predicate.or(entry -> entry instanceof IKeyEntry keyEntry
                && Conflicts.involveSecondaryKey(keyEntry.getKey(), Minecraft.getInstance().options.keyMappings));
    }
}
