package io.github.matejkadlec.realmforge.qa.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RLM-232 and RLM-273: the affix set of Matěj's review and our wands' loot category, as Apotheosis loads them with
 * the pack (ADR 0042). The tests that read Apotheosis's registries and loot code delegate to {@link ApotheosisLoot},
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
     * affixes. The wand's own affixes are the caster affixes (RLM-557).
     */
    @QaGameTest
    public static void aWandNeverRollsAMeleeAffix(GameTestHelper helper) {
        ApotheosisLoot.aWandNeverRollsAMeleeAffix(helper);
    }
}
