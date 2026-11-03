package io.github.matejkadlec.realmforge.mixin;

import io.github.matejkadlec.realmforge.item.ProtectionLimit;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * At most two protections from Apothic Enchanting's enchanting table (RLM-537). Its {@code selectEnchantment} picks
 * one enchantment at a time ({@code pickEnchantment}), adds it to the picks and drops the candidates that do not
 * combine with it; vanilla can only exclude pairs, and Apothic Enchanting has no event there, hence a mixin
 * (architecture 19.3). After each pick, {@link ProtectionLimit#afterTablePick} also drops the protections once two are
 * picked. It is {@code @Pseudo}, so the core mod loads without Apothic Enchanting (the lean dev client); with it, its
 * injector is required, so a changed {@code pickEnchantment} fails at load. Tested by the QA mod's
 * {@code MagicProtectionGameTests}.
 */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentHelper")
public abstract class ApothicTableSelectionMixin {
    @Inject(method = "pickEnchantment", at = @At("TAIL"))
    private static void realmforge$limitProtections(RandomSource random, List<EnchantmentInstance> picks,
            List<ArcanaEnchantmentDataAccessor> candidates, CallbackInfo ci) {
        ProtectionLimit.afterTablePick(picks, candidates, candidate -> candidate.realmforge$data().enchantment());
    }
}
