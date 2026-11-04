package io.github.matejkadlec.realmforge.qa.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * RLM-600 (Matěj, 2026-11-03: the Apotheosis bleed leaves the pack, so Rend and Deep Wounds are the only bleeds):
 * Bloodletting and Devilish are off, and the Tyrannical gem has no bow bonus. The bodies live in
 * {@link ApotheosisBleeds}, so this class names no Apotheosis type (the lean dev client runs without it).
 */
public final class ApotheosisBleedGameTests {
    private ApotheosisBleedGameTests() {
    }

    /** No affix or gem bonus gives Apothic Attributes' Bleeding, and Bloodletting and Devilish are off. */
    @QaGameTest
    public static void noAffixOrGemGivesApothicBleeding(GameTestHelper helper) {
        ApotheosisBleeds.noAffixOrGemGivesApothicBleeding(helper);
    }

    /** A Flawless or Perfect Tyrannical gem fits no bow, so no arrow of one bleeds. */
    @QaGameTest
    public static void aTyrannicalGemGivesABowNoBleeding(GameTestHelper helper) {
        ApotheosisBleeds.aTyrannicalGemGivesABowNoBleeding(helper);
    }

    /** Reforging a sword and a shield at Legendary never rolls Bloodletting or Devilish. */
    @QaGameTest(timeoutTicks = 200)
    public static void reforgingRollsNoBleedAffix(GameTestHelper helper) {
        ApotheosisBleeds.reforgingRollsNoBleedAffix(helper);
    }

    /** A sword that rolled Bloodletting before loads, and its hit gives no bleed. */
    @QaGameTest
    public static void aSwordThatRolledBloodlettingLoadsAndBleedsNothing(GameTestHelper helper) {
        ApotheosisBleeds.aSwordThatRolledBloodlettingLoadsAndBleedsNothing(helper);
    }
}
