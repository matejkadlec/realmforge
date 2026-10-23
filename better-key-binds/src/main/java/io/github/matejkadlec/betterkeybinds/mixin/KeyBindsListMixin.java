package io.github.matejkadlec.betterkeybinds.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.matejkadlec.betterkeybinds.SecondaryKeyColumn;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Widens the Key Binds rows by the secondary key column, vanilla's and Controlling's (which extends this list). */
@Mixin(KeyBindsList.class)
public abstract class KeyBindsListMixin {
    @ModifyReturnValue(method = "getRowWidth", at = @At("RETURN"))
    private int betterKeyBinds$widenRow(int rowWidth) {
        return SecondaryKeyColumn.widenRow(rowWidth, ((AbstractWidget) (Object) this).getWidth());
    }
}
