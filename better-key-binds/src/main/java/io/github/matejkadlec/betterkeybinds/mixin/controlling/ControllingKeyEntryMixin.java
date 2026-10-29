package io.github.matejkadlec.betterkeybinds.mixin.controlling;

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
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Controlling's Key Binds row with the secondary key column, as {@code KeyBindsListKeyEntryMixin} does for vanilla's
 * row: Controlling replaces the screen's list with its own, whose rows lay out their buttons after the render event
 * its API offers, so only a mixin can move the primary button. The new conflict tooltip keeps Controlling's category
 * line first. Applied only when Controlling is loaded ({@code Pseudo}).
 *
 * <p>A Controlling update can move any hook's injection point, and the config then skips that hook (RLM-383). The
 * column's hooks give way together: the secondary button is shown and clickable only once the row has placed it, so a
 * row whose layout hook lost its {@code setPosition} call keeps Controlling's own layout instead of a stray button at
 * the screen's corner.
 */
@Pseudo
@Mixin(targets = "com.blamejared.controlling.client.NewKeyBindsList$KeyEntry")
public abstract class ControllingKeyEntryMixin {
    @Shadow
    @Final
    private KeyMapping key;
    @Shadow
    @Final
    private Component name;
    @Shadow
    @Final
    private Button btnChangeKeyBinding;
    @Shadow
    private boolean hasCollision;
    @Shadow
    @Final
    private Component categoryName;

    @Unique
    private Button betterKeyBinds$secondaryButton;
    @Unique
    private boolean betterKeyBinds$secondaryConflict;
    @Unique
    private boolean betterKeyBinds$placed;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void betterKeyBinds$createSecondaryButton(CallbackInfo ci) {
        betterKeyBinds$secondaryButton = SecondaryKeyColumn.createButton(key, name);
        // The constructor's own refresh ran before the button existed.
        betterKeyBinds$refresh();
    }

    @WrapOperation(method = "extractContent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/Button;setPosition(II)V", ordinal = 1))
    private void betterKeyBinds$moveChangeButton(Button changeButton, int x, int y, Operation<Void> original) {
        if (betterKeyBinds$secondaryButton == null) {
            original.call(changeButton, x, y);
            return;
        }
        original.call(changeButton, x - SecondaryKeyColumn.SHIFT, y);
        betterKeyBinds$secondaryButton.setPosition(x, y);
        betterKeyBinds$placed = true;
    }

    @Inject(method = "extractContent", at = @At("TAIL"))
    private void betterKeyBinds$renderSecondaryButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            boolean hovered, float partialTick, CallbackInfo ci) {
        if (!betterKeyBinds$placed) {
            return;
        }
        ContainerObjectSelectionList.Entry<?> row = (ContainerObjectSelectionList.Entry<?>) (Object) this;
        SecondaryKeyColumn.render(graphics, betterKeyBinds$secondaryButton, betterKeyBinds$secondaryConflict,
                row.getContentY(), row.getContentBottom(), mouseX, mouseY, partialTick);
    }

    @ModifyReturnValue(method = "children", at = @At("RETURN"))
    private List<?> betterKeyBinds$addSecondaryButton(List<?> children) {
        return betterKeyBinds$placed ? KeyBindsRows.withSecondaryButton(children, betterKeyBinds$secondaryButton)
                : children;
    }

    @ModifyReturnValue(method = "narratables", at = @At("RETURN"))
    private List<?> betterKeyBinds$narrateSecondaryButton(List<?> narratables) {
        return betterKeyBinds$placed ? KeyBindsRows.withSecondaryButton(narratables, betterKeyBinds$secondaryButton)
                : narratables;
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
        if (SecondaryKeyColumn.refreshPrimary(btnChangeKeyBinding, key, KeyBindsRows.isSelected(key), categoryName)
                != null) {
            hasCollision = true;
        }
    }
}
