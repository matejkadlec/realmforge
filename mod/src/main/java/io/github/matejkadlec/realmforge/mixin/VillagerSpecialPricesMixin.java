package io.github.matejkadlec.realmforge.mixin;

import io.github.matejkadlec.realmforge.mob.merchant.Merchants;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps our merchants' prices fixed ({@code mob/merchant/Merchants}, architecture 14.4, RLM-285): vanilla's
 * {@code Villager#startTrading} always calls {@code updateSpecialPrices}, which lowers every first cost for a player
 * with Hero of the Village (30 % and more, at least 1) and by reputation. For a profession in
 * {@code #realmforge:fixed_price} the method does nothing, and the offers are put into their written order instead,
 * right before the trade screen opens. The method is private and NeoForge has no event before a villager's trading,
 * hence a mixin (architecture 19.3); the rest of vanilla's interaction (name tags, spawn eggs, a sleeping or baby
 * villager) stays untouched. Tested by the QA mod's {@code MerchantGameTests}.
 */
@Mixin(Villager.class)
public abstract class VillagerSpecialPricesMixin {
    @Inject(method = "updateSpecialPrices", at = @At("HEAD"), cancellable = true)
    private void realmforge$keepFixedPrices(Player player, CallbackInfo callback) {
        Villager villager = (Villager) (Object) this;
        if (Merchants.isFixedPrice(villager)) {
            Merchants.beforeTrading(villager);
            callback.cancel();
        }
    }
}
