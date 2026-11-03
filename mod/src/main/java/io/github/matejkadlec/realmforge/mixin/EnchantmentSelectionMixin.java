package io.github.matejkadlec.realmforge.mixin;

import io.github.matejkadlec.realmforge.item.ProtectionLimit;
import java.util.List;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * At most two protections from vanilla's enchantment selection (RLM-537): loot's {@code enchant_with_levels}, mobs'
 * and traders' gear, and the vanilla table where Apothic Enchanting does not replace it. {@code selectEnchantment}
 * picks one enchantment at a time and, after each pick, drops the candidates that do not combine with it
 * ({@code filterCompatibleEnchantments}); vanilla can only exclude pairs, and no event exists there, hence a mixin
 * (architecture 19.3). After that step, {@link ProtectionLimit#afterPick} also drops the protections once two are
 * picked. Tested by the QA mod's {@code MagicProtectionGameTests}.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentSelectionMixin {
    @Inject(method = "filterCompatibleEnchantments", at = @At("TAIL"))
    private static void realmforge$limitProtections(List<EnchantmentInstance> candidates, EnchantmentInstance picked,
            CallbackInfo ci) {
        ProtectionLimit.afterPick(candidates, picked);
    }
}
