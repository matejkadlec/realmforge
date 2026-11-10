package io.github.matejkadlec.realmforge.mixin;

import io.github.matejkadlec.realmforge.mob.merchant.Merchants;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides the level name and the XP bar in our merchants' trade screen ({@code mob/merchant/Merchants}, architecture
 * 14.4, RLM-285): they have no levels (ADR 0034). The screen draws both only while the server's offers packet says
 * so, and every such packet takes the flag from {@code showProgressBar}: the one that opens the screen and the one a
 * restock sends while it is open. For a villager whose profession is in {@code #realmforge:fixed_price} it is false.
 * No event carries that packet, hence a mixin (architecture 19.3). Tested by the QA mod's {@code MerchantGameTests}.
 */
@Mixin(AbstractVillager.class)
public abstract class MerchantProgressBarMixin {
    @Inject(method = "showProgressBar", at = @At("HEAD"), cancellable = true)
    private void realmforge$noLevelsForMerchants(CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof Villager villager && Merchants.isFixedPrice(villager)) {
            callback.setReturnValue(false);
        }
    }
}
