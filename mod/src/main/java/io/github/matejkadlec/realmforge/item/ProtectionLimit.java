package io.github.matejkadlec.realmforge.item;

import com.google.common.collect.MapMaker;
import io.github.matejkadlec.realmforge.Realmforge;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * At most two different protection enchantments on one piece of gear (RLM-537; Matěj, 2026-10-30): any two of
 * Protection, Fire, Blast, Projectile and Magic Protection, never a third. Vanilla can only say which pairs exclude
 * each other, so the data lets every pair combine ({@code #minecraft:exclusive_set/armor} loses them,
 * {@code generate/enchantments.py}) and this counts the protections in {@link #PROTECTIONS}:
 * <ul>
 *   <li><b>The anvil:</b> NeoForge's {@link AnvilUpdateEvent} carries vanilla's result; one that would hold a third
 *   protection is refused, no result, as for enchantments that do not combine ({@link #onAnvilUpdate}).</li>
 *   <li><b>The enchanting table and loot</b> pick enchantments one at a time, and each pick drops the candidates that
 *   do not combine with it: vanilla's {@code EnchantmentHelper#selectEnchantment} (loot's {@code enchant_with_levels},
 *   mobs' and traders' gear) and Apothic Enchanting's table. Once two protections are picked, the mixins drop every
 *   protection left among the candidates too ({@link #afterPick}, {@link #afterTablePick}), so the next picks take
 *   other enchantments and the roll keeps its size.</li>
 * </ul>
 * Apothic Enchanting's library hands out books of one enchantment, which the anvil then applies, and a book is no
 * piece of gear. An item that already has more (from before this rule, or a command) keeps them; only a third added
 * is refused.
 */
public final class ProtectionLimit {
    /** The most different protections a piece holds. */
    public static final int MAX = 2;
    /** Vanilla's four protections and ours ({@code content/enchantments/}). */
    public static final TagKey<Enchantment> PROTECTIONS =
            TagKey.create(Registries.ENCHANTMENT, Realmforge.id("protections"));

    /**
     * The protections picked against each candidate list of vanilla's selection so far: its filter step sees only
     * the candidates and the last pick. Weak identity keys, so a list is counted while its selection runs and forgotten
     * after it.
     */
    private static final Map<List<?>, Integer> PICKED = new MapMaker().weakKeys().makeMap();

    private ProtectionLimit() {
    }

    /** How many protections the enchantments hold. */
    public static int count(Collection<Holder<Enchantment>> enchantments) {
        return (int) enchantments.stream().filter(enchantment -> enchantment.is(PROTECTIONS)).count();
    }

    /** How many protections the item holds, an enchanted book's stored ones included. */
    public static int count(ItemStack stack) {
        return count(EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet());
    }

    /**
     * Vanilla's selection, after it dropped the candidates that do not combine with {@code picked}: from the second
     * protection picked against these candidates on, no protection stays among them.
     */
    public static void afterPick(List<EnchantmentInstance> candidates, EnchantmentInstance picked) {
        if (picked.enchantment().is(PROTECTIONS) && PICKED.merge(candidates, 1, Integer::sum) >= MAX) {
            candidates.removeIf(candidate -> candidate.enchantment().is(PROTECTIONS));
        }
    }

    /**
     * Apothic Enchanting's table, after a pick: once {@code picks} hold two protections, no protection stays among the
     * candidates, whose enchantment {@code enchantment} reads.
     */
    public static <T> void afterTablePick(List<EnchantmentInstance> picks, List<T> candidates,
            Function<T, Holder<Enchantment>> enchantment) {
        if (count(picks.stream().map(EnchantmentInstance::enchantment).toList()) >= MAX) {
            candidates.removeIf(candidate -> enchantment.apply(candidate).is(PROTECTIONS));
        }
    }

    /** Refuses an anvil result that adds a third protection. */
    static void onAnvilUpdate(AnvilUpdateEvent event) {
        int protections = count(event.getOutput());
        if (protections > MAX && protections > count(event.getLeft())) {
            event.setCanceled(true);
        }
    }
}
