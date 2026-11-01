package io.github.matejkadlec.realmforge.qa.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * RLM-549: Apotheosis's gems against our school hits, as the game loads them with the pack (ADR 0042). The bodies
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
}
