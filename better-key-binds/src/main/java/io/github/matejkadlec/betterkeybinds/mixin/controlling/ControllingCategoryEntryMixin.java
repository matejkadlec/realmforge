package io.github.matejkadlec.betterkeybinds.mixin.controlling;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.FocusableTextWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Controlling's category headings sit inside their row, as vanilla's do: Controlling places the heading 10 px above
 * the row's bottom, the height of a plain line of text, but the heading is a padded widget (17 px), so it reached into
 * the first key bind's row below it, and so did its focus border. Placed by its own height like vanilla's
 * ({@code KeyBindsList.CategoryEntry}). Applied only when Controlling is loaded.
 */
@Pseudo
@Mixin(targets = "com.blamejared.controlling.client.NewKeyBindsList$CategoryEntry")
public abstract class ControllingCategoryEntryMixin {
    @WrapOperation(method = "extractContent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/FocusableTextWidget;setPosition(II)V"))
    private void betterKeyBinds$keepHeadingInRow(FocusableTextWidget heading, int x, int y,
            Operation<Void> original) {
        ContainerObjectSelectionList.Entry<?> row = (ContainerObjectSelectionList.Entry<?>) (Object) this;
        original.call(heading, x, row.getContentBottom() - heading.getHeight());
    }
}
