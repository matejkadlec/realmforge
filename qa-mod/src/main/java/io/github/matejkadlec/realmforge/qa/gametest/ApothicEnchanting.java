package io.github.matejkadlec.realmforge.qa.gametest;

import dev.shadowsoffire.apothic_enchanting.asm.EnchHooks;
import dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentHelper;
import dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/**
 * Apothic Enchanting's table and max levels for {@link MagicProtectionGameTests} (RLM-537). Every Apothic Enchanting
 * type lives here, out of the GameTest class, as {@link ApotheosisLoot} explains for Apotheosis.
 */
final class ApothicEnchanting {
    /** A strong table's stats: 50 Eterna (its cap), 15 % Quanta, 15 % Arcana. */
    private static final float ETERNA = 50;
    private static final float QUANTA = 15;
    private static final float ARCANA = 15;
    /** The enchanting power of the table's best slot at that Eterna. */
    private static final int POWER = 100;

    private ApothicEnchanting() {
    }

    /** {@code rolls} of the table's selection for a diamond chestplate, with a fixed seed. */
    static List<List<EnchantmentInstance>> tableRolls(GameTestHelper helper, int rolls) {
        EnchantmentTableStats.Builder builder = new EnchantmentTableStats.Builder();
        builder.addEterna(ETERNA, ETERNA);
        builder.addQuanta(QUANTA);
        builder.addArcana(ARCANA);
        EnchantmentTableStats stats = builder.build();
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        RandomSource random = RandomSource.create(537);
        List<List<EnchantmentInstance>> results = new ArrayList<>();
        for (int i = 0; i < rolls; i++) {
            results.add(ApothEnchantmentHelper.selectEnchantment(random, new ItemStack(Items.DIAMOND_CHESTPLATE),
                    POWER, stats, enchantments));
        }
        return results;
    }

    /** The enchantment's highest level with Apothic Enchanting, which its coremod gives every caller of vanilla's. */
    static int maxLevel(Holder<Enchantment> enchantment) {
        return EnchHooks.getMaxLevel(enchantment.value());
    }
}
