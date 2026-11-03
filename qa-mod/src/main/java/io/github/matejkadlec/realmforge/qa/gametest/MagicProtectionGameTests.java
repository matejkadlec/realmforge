package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.generated.GeneratedEnchantments;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.item.ProtectionLimit;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * RLM-537: Magic Protection (0.5 protection point per level in vanilla's shared pool, Matěj's pick of 2026-11-01) and
 * at most two different protections on one piece, at the anvil, the enchanting table and in loot
 * ({@code item/ProtectionLimit}). The pack's protection formula is vanilla's: a point lowers a hit by 4 %, at most
 * 20 points (80 %). Apothic Enchanting's table and max levels are read through {@link ApothicEnchanting}, which holds
 * its types out of this class (the lean dev client runs without it).
 */
public final class MagicProtectionGameTests {
    private static final float HIT = 10;
    /** Rolls per selection test, with a fixed seed each. */
    private static final int ROLLS = 300;
    /** The enchanting power of the vanilla rolls: a level 30 table's best, and loot's high end. */
    private static final int VANILLA_POWER = 30;

    private MagicProtectionGameTests() {
    }

    /**
     * Done when of RLM-537: Magic Protection VIII on one piece is 4 points, 16 % off a frost hit, and leaves a physical
     * hit as it was.
     */
    @QaGameTest
    public static void magicProtectionLowersAFrostHitAndNotAPhysicalOne(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "qa-magic-protection");
        Zombie caster = CombatGameTests.attacker(helper);
        DamageSource frost = CombatGameTests.school(helper, GeneratedSpellSchools.FROST, caster);
        DamageSource physical = CombatGameTests.school(helper, GeneratedSpellSchools.PHYSICAL, caster);
        double plainPhysical = dealt(player, physical);
        helper.assertTrue(CombatGameTests.close(dealt(player, frost), HIT), "a frost hit lands in full without it");

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        chest.enchant(enchantment(helper, GeneratedEnchantments.MAGIC_PROTECTION), 8);
        double frostDealt = dealt(player, frost);
        helper.assertTrue(CombatGameTests.close(frostDealt, HIT * (1 - 4 * 0.04)),
                "Magic Protection VIII takes 16 % off the frost hit of " + HIT + ", dealt " + frostDealt);
        double physicalDealt = dealt(player, physical);
        helper.assertTrue(CombatGameTests.close(physicalDealt, plainPhysical),
                "Magic Protection leaves the physical hit of " + plainPhysical + " alone, dealt " + physicalDealt);
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-537: the shared pool's cap holds. Protection IV and Magic Protection VIII on all four pieces are
     * 16 + 16 points, capped at 20, so a frost hit loses 80 %; Protection and Magic Protection on one piece is two
     * protections, which the rule allows.
     */
    @QaGameTest
    public static void theProtectionCapHolds(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "qa-magic-protection-cap");
        Holder<Enchantment> protection = enchantment(helper, Enchantments.PROTECTION);
        Holder<Enchantment> magic = enchantment(helper, GeneratedEnchantments.MAGIC_PROTECTION);
        List<ItemStack> pieces = List.of(new ItemStack(Items.LEATHER_HELMET), new ItemStack(Items.LEATHER_CHESTPLATE),
                new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.LEATHER_BOOTS));
        List<EquipmentSlot> slots = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET);
        for (int i = 0; i < pieces.size(); i++) {
            ItemStack piece = pieces.get(i);
            piece.enchant(protection, 4);
            piece.enchant(magic, 8);
            helper.assertValueEqual(ProtectionLimit.count(piece), 2, "two protections on " + piece);
            player.setItemSlot(slots.get(i), piece);
        }
        ApotheosisGameTests.ticks(player, 2);
        Zombie caster = CombatGameTests.attacker(helper);
        double dealt = dealt(player, CombatGameTests.school(helper, GeneratedSpellSchools.FROST, caster));
        helper.assertTrue(CombatGameTests.close(dealt, HIT * 0.2),
                "32 protection points stop at 20: the frost hit of " + HIT + " loses 80 %, dealt " + dealt);
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-537: the anvil puts Magic Protection next to Protection, Fire and Blast Protection combine (the
     * exclusive set no longer holds them), and a book of Projectile Protection onto a piece with two protections gives
     * no result.
     */
    @QaGameTest
    public static void theAnvilRefusesAThirdProtection(GameTestHelper helper) {
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-magic-protection-anvil");
        ItemStack protected4 = enchanted(helper, Items.IRON_CHESTPLATE, Enchantments.PROTECTION, 4);
        ItemStack two = anvil(player, protected4, book(helper, GeneratedEnchantments.MAGIC_PROTECTION, 4));
        helper.assertValueEqual(ProtectionLimit.count(two), 2, "Protection and Magic Protection combine: " + two);

        ItemStack fire = enchanted(helper, Items.IRON_CHESTPLATE, Enchantments.FIRE_PROTECTION, 4);
        ItemStack fireBlast = anvil(player, fire, book(helper, Enchantments.BLAST_PROTECTION, 4));
        helper.assertValueEqual(ProtectionLimit.count(fireBlast), 2, "Fire and Blast Protection combine: " + fireBlast);
        ItemStack third = anvil(player, fireBlast, book(helper, Enchantments.PROJECTILE_PROTECTION, 4));
        helper.assertTrue(third.isEmpty(), "a third protection gives no result: " + third);
        ItemStack other = anvil(player, fireBlast, book(helper, Enchantments.UNBREAKING, 3));
        helper.assertValueEqual(other.getEnchantmentLevel(enchantment(helper, Enchantments.UNBREAKING)), 3,
                "two protections still take another enchantment");
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-537: vanilla's selection (loot's enchant_with_levels, mobs' and traders' gear) picks at most two
     * protections, and sometimes two, now that any two combine.
     */
    @QaGameTest(timeoutTicks = 200)
    public static void lootPicksAtMostTwoProtections(GameTestHelper helper) {
        HolderLookup.RegistryLookup<Enchantment> enchantments = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);
        RandomSource random = RandomSource.create(537);
        int withTwo = 0;
        for (int i = 0; i < ROLLS; i++) {
            List<EnchantmentInstance> picks = EnchantmentHelper.selectEnchantment(random,
                    new ItemStack(Items.DIAMOND_CHESTPLATE), VANILLA_POWER,
                    enchantments.getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE).stream());
            int protections = protections(picks);
            helper.assertTrue(protections <= ProtectionLimit.MAX, "at most two protections: " + picks);
            withTwo += protections == ProtectionLimit.MAX ? 1 : 0;
        }
        helper.assertTrue(withTwo > 0, "some rolls pick two protections");
        helper.succeed();
    }

    /** Done when of RLM-537: Apothic Enchanting's table picks at most two protections, and sometimes two. */
    @QaGameTest(timeoutTicks = 200)
    public static void theTablePicksAtMostTwoProtections(GameTestHelper helper) {
        int withTwo = 0;
        for (List<EnchantmentInstance> picks : ApothicEnchanting.tableRolls(helper, ROLLS)) {
            int protections = protections(picks);
            helper.assertTrue(protections <= ProtectionLimit.MAX, "at most two protections: " + picks);
            withTwo += protections == ProtectionLimit.MAX ? 1 : 0;
        }
        helper.assertTrue(withTwo > 0, "some table rolls pick two protections");
        helper.succeed();
    }

    /** RLM-537: Magic Protection goes up to VIII with Apothic Enchanting, IV without. */
    @QaGameTest
    public static void magicProtectionReachesEight(GameTestHelper helper) {
        Holder<Enchantment> magic = enchantment(helper, GeneratedEnchantments.MAGIC_PROTECTION);
        helper.assertValueEqual(magic.value().definition().maxLevel(), 4, "vanilla's max level");
        helper.assertValueEqual(ApothicEnchanting.maxLevel(magic), 8, "Apothic Enchanting's max level");
        helper.assertTrue(magic.is(ProtectionLimit.PROTECTIONS), "it counts as a protection");
        helper.assertTrue(magic.is(EnchantmentTags.IN_ENCHANTING_TABLE), "the enchanting table offers it");
        helper.succeed();
    }

    /** A survival player who never dodges, in a leather chestplate. */
    private static ServerPlayer wearer(GameTestHelper helper, String name) {
        ServerPlayer player = ApotheosisGameTests.player(helper, name);
        StatsCommands.setBase(player, GeneratedStats.DODGE_CHANCE, 0);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        ApotheosisGameTests.ticks(player, 2);
        return player;
    }

    /** What a {@value #HIT} hit of {@code source} takes off the player at full health, out of the hurt cooldown. */
    private static double dealt(ServerPlayer player, DamageSource source) {
        player.setHealth(player.getMaxHealth());
        CombatGameTests.settle(player);
        float before = player.getHealth();
        player.hurtServer(player.level(), source, HIT);
        return before - player.getHealth();
    }

    /** The anvil's result of the two inputs for {@code player}, or empty. */
    private static ItemStack anvil(ServerPlayer player, ItemStack left, ItemStack right) {
        AnvilMenu menu = new AnvilMenu(0, player.getInventory());
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(left.copy());
        menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(right.copy());
        menu.createResult();
        return menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
    }

    private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(enchantment(helper, key), level);
        return stack;
    }

    private static ItemStack book(GameTestHelper helper, ResourceKey<Enchantment> key, int level) {
        return EnchantmentHelper.createBook(new EnchantmentInstance(enchantment(helper, key), level));
    }

    private static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static int protections(List<EnchantmentInstance> picks) {
        return ProtectionLimit.count(picks.stream().map(EnchantmentInstance::enchantment).toList());
    }
}
