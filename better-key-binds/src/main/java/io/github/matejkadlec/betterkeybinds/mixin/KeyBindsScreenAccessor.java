package io.github.matejkadlec.betterkeybinds.mixin;

import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The Key Binds screen's list, which it keeps private: a bound secondary key relabels every row through it, as a bound
 * primary key does. Controlling puts its own list into the same field.
 */
@Mixin(KeyBindsScreen.class)
public interface KeyBindsScreenAccessor {
    @Accessor("keyBindsList")
    KeyBindsList betterKeyBinds$keyBindsList();
}
