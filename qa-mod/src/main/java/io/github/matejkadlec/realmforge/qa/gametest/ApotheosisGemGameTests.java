package io.github.matejkadlec.realmforge.qa.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * RLM-549: Apotheosis's gems against our school hits, and RLM-547 and RLM-548: our extra gem bonuses in wands, robes
 * and helmets, as the game loads them with the pack (ADR 0042). The bodies
 * live in {@link ApotheosisGems}, so this class names no Apotheosis type (the lean dev client runs without it).
 */
public final class ApotheosisGemGameTests {
    private ApotheosisGemGameTests() {
    }

    /** Done when of RLM-549: a Combatant gem in the chestplate lowers a {@code realmforge:physical} hit. */
    @QaGameTest
    public static void aCombatantGemLowersAPhysicalSchoolHit(GameTestHelper helper) {
        ApotheosisGems.aCombatantGemLowersAPhysicalSchoolHit(helper);
    }

    /** Done when of RLM-549: a Verdant Ruin gem in the chestplate does not lower a frost hit. */
    @QaGameTest
    public static void aVerdantRuinGemLeavesAFrostHitAlone(GameTestHelper helper) {
        ApotheosisGems.aVerdantRuinGemLeavesAFrostHitAlone(helper);
    }

    /** Done when of RLM-547: a Ballast in a wand gives Spell Power and no Attack Damage. */
    @QaGameTest
    public static void aBallastInAWandGivesSpellPowerAndNoAttackDamage(GameTestHelper helper) {
        ApotheosisGems.aBallastInAWandGivesSpellPowerAndNoAttackDamage(helper);
    }

    /** Done when of RLM-547: a wand refuses an Earth gem and a Guardian. */
    @QaGameTest
    public static void aWandRefusesAnEarthGemAndAGuardian(GameTestHelper helper) {
        ApotheosisGems.aWandRefusesAnEarthGemAndAGuardian(helper);
    }

    /** Done when of RLM-547: a Samurai in a wand raises our Crit Chance. */
    @QaGameTest
    public static void aSamuraiInAWandRaisesOurCritChance(GameTestHelper helper) {
        ApotheosisGems.aSamuraiInAWandRaisesOurCritChance(helper);
    }

    /** Done when of RLM-548: a Guardian in a robe chest gives Magic Shield and no Armor. */
    @QaGameTest
    public static void aGuardianInARobeChestGivesMagicShieldAndNoArmor(GameTestHelper helper) {
        ApotheosisGems.aGuardianInARobeChestGivesMagicShieldAndNoArmor(helper);
    }

    /** Done when of RLM-548: a Warlord in a robe head gives Spell Power %. */
    @QaGameTest
    public static void aWarlordInARobeHeadGivesSpellPowerPercent(GameTestHelper helper) {
        ApotheosisGems.aWarlordInARobeHeadGivesSpellPowerPercent(helper);
    }

    /** Done when of RLM-548: a Slipstream in a helmet (and in a robe head) gives Dodge Chance. */
    @QaGameTest
    public static void aSlipstreamInAHelmetGivesDodgeChance(GameTestHelper helper) {
        ApotheosisGems.aSlipstreamInAHelmetGivesDodgeChance(helper);
    }

    /** The deferred playtest's commands give a Perfect gem and a wand's socket as written (RLM-673). */
    @QaGameTest
    public static void thePlaytestsCommandsGiveAPerfectGemAndASocket(GameTestHelper helper) {
        ApotheosisGems.thePlaytestsCommandsGiveAPerfectGemAndASocket(helper);
    }

    /** Done when of RLM-548: Royalty's all-stats raise Spell Power. */
    @QaGameTest
    public static void royaltysAllStatsRaiseSpellPower(GameTestHelper helper) {
        ApotheosisGems.royaltysAllStatsRaiseSpellPower(helper);
    }
}
