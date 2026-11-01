package io.github.matejkadlec.realmforge.mixin;

import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the enchantment of a candidate of Apothic Enchanting's table, whose field is package-private, for
 * {@link ApothicTableSelectionMixin} (RLM-537). {@code @Pseudo}, as that mixin is.
 */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentHelper$ArcanaEnchantmentData")
public interface ArcanaEnchantmentDataAccessor {
    @Accessor("data")
    EnchantmentInstance realmforge$data();
}
