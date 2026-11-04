package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RLM-232, RLM-273, RLM-546 and RLM-557: the affix set of Matěj's review, our wands' and robes' loot categories and
 * the robes' affixes, as Apotheosis loads them with the pack (ADR 0042). The tests that read Apotheosis's registries and loot code delegate to {@link ApotheosisLoot},
 * so this class names no Apotheosis type (the lean dev client runs without it). Rolling items there also checks that
 * no category meets a slot it has nothing left for: Apotheosis logs "Failed to execute AffixLootRule (no affixes
 * available)" at ERROR then, which {@code rf gametest-log} refuses.
 */
public final class ApotheosisLootGameTests {
    /** Giant Slaying at Rare: 5 % to 10 % of the target's current health; level 1.0 is the top of that range. */
    private static final double GIANT_SLAYING_RARE_MAX = 0.10;
    private static final double EPSILON = 1e-4;

    private ApotheosisLootGameTests() {
    }

    /** Done when of RLM-232: the review's affixes are off, the ones it kept are not. */
    @QaGameTest
    public static void theReviewsAffixesAreOff(GameTestHelper helper) {
        ApotheosisLoot.theReviewsAffixesAreOff(helper);
    }

    /** RLM-232's value table: these affixes apply at Rare; Magical stays Epic and Legendary (Matěj, 2026-10-29). */
    @QaGameTest
    public static void theReviewOpensAffixesToRare(GameTestHelper helper) {
        ApotheosisLoot.theReviewOpensAffixesToRare(helper);
    }

    /** Giant Slaying at Rare adds its Rare value, which Apotheosis 9.1.0 did not have. */
    @QaGameTest
    public static void giantSlayingAtRareAddsItsRareValue(GameTestHelper helper) {
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-apoth-giant-slaying");
        Identifier currentHpDamage = Identifier.parse("apothic_attributes:current_hp_damage");
        double before = player.getAttributeValue(ApotheosisGameTests.attribute(currentHpDamage));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        ApotheosisGameTests.run(player, "apoth set_rarity apotheosis:rare");
        ApotheosisGameTests.run(player, "apoth affix apply apotheosis:melee/attribute/giant_slaying 1.0");
        ApotheosisGameTests.ticks(player, 2);

        double gained = player.getAttributeValue(ApotheosisGameTests.attribute(currentHpDamage)) - before;
        helper.assertTrue(Math.abs(gained - GIANT_SLAYING_RARE_MAX) < EPSILON,
                "a Rare Giant Slaying at its top adds " + GIANT_SLAYING_RARE_MAX + ", added " + gained);
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Rare's chanced ability slot (Matěj, 2026-10-29): 25 %, so a Rare sword sometimes carries Thunderstruck, never
     * two abilities.
     */
    @QaGameTest
    public static void aRareSwordSometimesRollsAnAbility(GameTestHelper helper) {
        ApotheosisLoot.aRareSwordSometimesRollsAnAbility(helper);
    }

    /**
     * Matěj, 2026-10-31 (RLM-232): a category skips the slots it has nothing left for. Helmets have no ability and one
     * basic effect (Runed and Blinding are off), boots no ability below Epic, shears one basic effect (Gardening and
     * Leaf-Forming are off). Every category rolls at every rarity and tier without an empty slot (the log check).
     */
    @QaGameTest(timeoutTicks = 200)
    public static void everyCategoryRollsWithoutAnEmptySlot(GameTestHelper helper) {
        ApotheosisLoot.everyCategoryRollsWithoutAnEmptySlot(helper);
    }

    /** RLM-232: the world tiers no longer cap Apothic Enchanting's max Eterna; their other player augments stay. */
    @QaGameTest
    public static void theWorldTiersCapNoEterna(GameTestHelper helper) {
        ApotheosisLoot.theWorldTiersCapNoEterna(helper);
    }

    /** Done when of RLM-273: every wand is {@code realmforge:wand}, never Apotheosis's melee weapon. */
    @QaGameTest
    public static void aWandIsAWandNotAMeleeWeapon(GameTestHelper helper) {
        ApotheosisLoot.aWandIsAWandNotAMeleeWeapon(helper);
    }

    /**
     * Done when of RLM-273: no affix can roll on a wand at any rarity, so never a melee one; a sword still has its
     * affixes. The wand's own affixes are RLM-562's.
     */
    @QaGameTest
    public static void aWandNeverRollsAMeleeAffix(GameTestHelper helper) {
        ApotheosisLoot.aWandNeverRollsAMeleeAffix(helper);
    }

    /** Done when of RLM-546: every robe piece is the robe category of its slot, never Apotheosis's armor. */
    @QaGameTest
    public static void aRobeIsARobeNotArmor(GameTestHelper helper) {
        ApotheosisLoot.aRobeIsARobeNotArmor(helper);
    }

    /** Done when of RLM-546: a Guardian gem, armor bonuses only, cannot be socketed into a robe. */
    @QaGameTest
    public static void aGuardianGemFitsNoRobe(GameTestHelper helper) {
        ApotheosisLoot.aGuardianGemFitsNoRobe(helper);
    }

    /** RLM-546: a wand rolled at every rarity gets the rarity and no affix, without an empty slot's error. */
    @QaGameTest(timeoutTicks = 200)
    public static void aWandRollsARarityWithoutAffixes(GameTestHelper helper) {
        ApotheosisLoot.aWandRollsARarityWithoutAffixes(helper);
    }

    /** Done when of RLM-557: what can roll on each robe piece, at each rarity, is the gear design's list, no armor. */
    @QaGameTest
    public static void eachRobePieceOffersTheGearDesignsAffixes(GameTestHelper helper) {
        ApotheosisLoot.eachRobePieceOffersTheGearDesignsAffixes(helper);
    }

    /**
     * Done when of RLM-557: a robe rolls caster affixes and no armor affix, at every rarity and tier, with a stat
     * affix for every stat slot and without an empty slot's error (the log check).
     */
    @QaGameTest(timeoutTicks = 200)
    public static void aRobeRollsCasterAffixesAndNoArmorAffix(GameTestHelper helper) {
        ApotheosisLoot.aRobeRollsCasterAffixesAndNoArmorAffix(helper);
    }

    /** RLM-557: a worn robe's Sorcerous, at its top Legendary roll, adds its 4 Spell Power; held, nothing. */
    @QaGameTest
    public static void sorcerousAddsItsSpellPowerWhenTheRobeIsWorn(GameTestHelper helper) {
        robeAffixAddsItsStat(helper, "qa-apoth-sorcerous", "robe_sorcerous", GeneratedStats.SPELL_POWER, 4.0);
    }

    /** RLM-557: a worn robe's Fiery, at its top Legendary roll, adds its 14 % Fire Power, as a share. */
    @QaGameTest
    public static void fieryAddsItsFirePowerWhenTheRobeIsWorn(GameTestHelper helper) {
        robeAffixAddsItsStat(helper, "qa-apoth-fiery", "robe_fiery", GeneratedStats.FIRE_POWER, 0.14);
    }

    /** RLM-557: a worn robe's Warded, at its top Legendary roll, adds its 5 Magic Shield. */
    @QaGameTest
    public static void wardedAddsItsMagicShieldWhenTheRobeIsWorn(GameTestHelper helper) {
        robeAffixAddsItsStat(helper, "qa-apoth-warded", "robe_warded", GeneratedStats.MAGIC_SHIELD, 5.0);
    }

    /** RLM-557 and the gear design: a worn robe's Bolstering fires on being hurt, as on any armor. */
    @QaGameTest
    public static void bolsteringFiresOnAWornRobeWhenItsWearerIsHit(GameTestHelper helper) {
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-apoth-bolstering");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GeneratedItems.NETHERITE_ROBES.get()));
        ApotheosisGameTests.run(player, "apoth set_rarity apotheosis:epic");
        ApotheosisGameTests.run(player, "apoth affix apply apotheosis:armor/mob_effect/bolstering 1.0");
        player.setItemSlot(EquipmentSlot.CHEST, player.getMainHandItem().copy());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ApotheosisGameTests.ticks(player, 2);
        helper.assertFalse(player.hasEffect(MobEffects.RESISTANCE), "no Resistance before the hit");

        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        husk.doHurtTarget(helper.getLevel(), player);
        helper.assertTrue(player.hasEffect(MobEffects.RESISTANCE), "Bolstering gives Resistance when the wearer is hit");
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /** The two commands a player reaches a Legendary affix with, then the difference it makes held and worn. */
    private static void robeAffixAddsItsStat(GameTestHelper helper, String name, String affix,
            Holder<Attribute> stat, double expected) {
        ServerPlayer player = ApotheosisGameTests.player(helper, name);
        // Against plain robes worn, as the robes' own gear stats count too.
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(GeneratedItems.NETHERITE_ROBES.get()));
        ApotheosisGameTests.ticks(player, 2);
        double plain = player.getAttributeValue(stat);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GeneratedItems.NETHERITE_ROBES.get()));
        ApotheosisGameTests.run(player, "apoth set_rarity apotheosis:mythic");
        ApotheosisGameTests.run(player, "apoth affix apply realmforge:" + affix + " 1.0");
        ApotheosisGameTests.ticks(player, 2);
        helper.assertTrue(Math.abs(player.getAttributeValue(stat) - plain) < EPSILON,
                affix + " adds nothing while the robes are held");

        player.setItemSlot(EquipmentSlot.CHEST, player.getMainHandItem().copy());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ApotheosisGameTests.ticks(player, 2);
        double gained = player.getAttributeValue(stat) - plain;
        helper.assertTrue(Math.abs(gained - expected) < EPSILON,
                affix + " at its top adds " + expected + ", added " + gained);
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }
}
