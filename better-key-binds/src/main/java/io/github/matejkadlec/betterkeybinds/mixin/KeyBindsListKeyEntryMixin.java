package io.github.matejkadlec.betterkeybinds.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.matejkadlec.betterkeybinds.KeyBindsRows;
import io.github.matejkadlec.betterkeybinds.SecondaryKeyColumn;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's Key Binds row (without Controlling) with the secondary key column: the button is created with the row,
 * placed where the primary button was while the primary moves one column left, takes clicks and narration as one of
 * the row's children, and is relabelled whenever the row is. The row's conflict marker and its primary key's label
 * include the conflicts with other key binds' secondary keys. The row builds and lays out its buttons itself, with no
 * event to add one, hence a mixin; {@code ControllingKeyEntryMixin} does the same for Controlling's row.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.options.controls.KeyBindsList$KeyEntry")
public abstract class KeyBindsListKeyEntryMixin {
    @Shadow
    @Final
    private KeyMapping key;
    @Shadow
    @Final
    private Component name;
    @Shadow
    @Final
    private Button changeButton;
    @Shadow
    private boolean hasCollision;

    @Unique
    private Button betterKeyBinds$secondaryButton;
    @Unique
    private boolean betterKeyBinds$secondaryConflict;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void betterKeyBinds$createSecondaryButton(CallbackInfo ci) {
        betterKeyBinds$secondaryButton = SecondaryKeyColumn.createButton(key, name);
        // The constructor's own refresh ran before the button existed.
        betterKeyBinds$refresh();
    }

    @WrapOperation(method = "extractContent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/Button;setPosition(II)V", ordinal = 1))
    private void betterKeyBinds$moveChangeButton(Button changeButton, int x, int y, Operation<Void> original) {
        original.call(changeButton, x - SecondaryKeyColumn.SHIFT, y);
        betterKeyBinds$secondaryButton.setPosition(x, y);
    }

    @Inject(method = "extractContent", at = @At("TAIL"))
    private void betterKeyBinds$renderSecondaryButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            boolean hovered, float partialTick, CallbackInfo ci) {
        ContainerObjectSelectionList.Entry<?> row = (ContainerObjectSelectionList.Entry<?>) (Object) this;
        SecondaryKeyColumn.render(graphics, betterKeyBinds$secondaryButton, betterKeyBinds$secondaryConflict,
                row.getContentY(), row.getContentBottom(), mouseX, mouseY, partialTick);
    }

    @ModifyReturnValue(method = "children", at = @At("RETURN"))
    private List<?> betterKeyBinds$addSecondaryButton(List<?> children) {
        return KeyBindsRows.withSecondaryButton(children, betterKeyBinds$secondaryButton);
    }

    @ModifyReturnValue(method = "narratables", at = @At("RETURN"))
    private List<?> betterKeyBinds$narrateSecondaryButton(List<?> narratables) {
        return KeyBindsRows.withSecondaryButton(narratables, betterKeyBinds$secondaryButton);
    }

    @Inject(method = "refreshEntry", at = @At("TAIL"))
    private void betterKeyBinds$refreshSecondaryButton(CallbackInfo ci) {
        betterKeyBinds$refresh();
    }

    @Unique
    private void betterKeyBinds$refresh() {
        if (betterKeyBinds$secondaryButton == null) {
            return;
        }
        betterKeyBinds$secondaryConflict = SecondaryKeyColumn.refresh(betterKeyBinds$secondaryButton, key);
        if (SecondaryKeyColumn.refreshPrimary(changeButton, key, KeyBindsRows.isSelected(key), null) != null) {
            hasCollision = true;
        }
    }
}
