package io.github.matejkadlec.realmforge.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.combat.DamageCalculator.Attacker;
import io.github.matejkadlec.realmforge.combat.DamageCalculator.SchoolHit;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class DamageCalculatorTest {
    private static final double EPSILON = 1e-12;
    // Crit chance 0: no roll, so the school formula alone decides.
    private static final Attacker NO_CRIT = new Attacker(0, 0.5, 0.2, 0.1);

    @Test
    void armorThatEqualsTheHalvingValueBlocksHalf() {
        assertEquals(0.5, DamageCalculator.armorReduction(15, 0, 15, 0.75), EPSILON);
    }

    @Test
    void toughnessCountsAsArmor() {
        assertEquals(DamageCalculator.armorReduction(28, 0, 15, 0.75),
                DamageCalculator.armorReduction(20, 8, 15, 0.75), EPSILON);
        assertEquals(28.0 / 43, DamageCalculator.armorReduction(20, 8, 15, 0.75), EPSILON);
    }

    @Test
    void theCapHoldsForHugeArmor() {
        assertEquals(0.75, DamageCalculator.armorReduction(10_000, 0, 15, 0.75), EPSILON);
    }

    @Test
    void aStrongerAttackerMeetsLessReduction() {
        assertTrue(DamageCalculator.armorReduction(20, 0, 50, 0.75) < DamageCalculator.armorReduction(20, 0, 15, 0.75),
                "a bigger halving value (a higher attacker level) lets armor block less");
    }

    @Test
    void noArmorOrNoHalvingValueBlocksNothing() {
        assertEquals(0, DamageCalculator.armorReduction(0, 0, 15, 0.75));
        assertEquals(0, DamageCalculator.armorReduction(-5, 0, 15, 0.75), "negative armor counts as none");
        assertEquals(0, DamageCalculator.armorReduction(20, 0, 0, 0.75));
        assertEquals(0, DamageCalculator.armorReduction(20, 0, -1, 0.75));
    }

    @Test
    void theCapStaysAShare() {
        assertEquals(10_000.0 / 10_001, DamageCalculator.armorReduction(10_000, 0, 1, 5), EPSILON,
                "a cap above 1 counts as 1");
        assertEquals(0, DamageCalculator.armorReduction(20, 0, 15, -1), "a negative cap blocks nothing");
    }

    @Test
    void magicAddsSpellPowerAndSchoolPower() {
        SchoolHit hit = DamageCalculator.schoolHit(10, true, NO_CRIT, 0, random());
        assertEquals(10 * 1.3, hit.damage(), EPSILON);
        assertFalse(hit.crit());
    }

    @Test
    void physicalIgnoresSpellPower() {
        assertEquals(10 * 1.1, DamageCalculator.schoolHit(10, false, NO_CRIT, 0, random()).damage(), EPSILON);
    }

    @Test
    void resistanceLowersTheHit() {
        assertEquals(10 * 1.3 * 0.75, DamageCalculator.schoolHit(10, true, NO_CRIT, 0.25, random()).damage(), EPSILON);
        assertEquals(0, DamageCalculator.afterResistance(10, 2), "a resistance above 1 counts as 1");
        assertEquals(10, DamageCalculator.afterResistance(10, -1), EPSILON, "a negative resistance counts as 0");
    }

    @Test
    void aSureCritMultipliesByOnePlusCritDamage() {
        Attacker sure = new Attacker(1, 0.5, 0, 0);
        SchoolHit hit = DamageCalculator.schoolHit(10, true, sure, 0, random());
        assertTrue(hit.crit());
        assertEquals(15, hit.damage(), EPSILON);
    }

    @Test
    void negativeBonusesNeverMakeDamageNegative() {
        assertEquals(0, DamageCalculator.schoolHit(10, true, new Attacker(0, 0, -3, 0), 0, random()).damage());
    }

    @Test
    void critMultiplierStartsAtOne() {
        assertEquals(1.5, DamageCalculator.critMultiplier(0.5), EPSILON);
        assertEquals(1, DamageCalculator.critMultiplier(-0.5), EPSILON);
    }

    @Test
    void rollsFollowTheirChance() {
        RandomSource random = random();
        for (int i = 0; i < 100; i++) {
            assertFalse(DamageCalculator.dodges(0, random), "a chance of 0 never succeeds");
            assertTrue(DamageCalculator.dodges(1, random), "a chance of 1 always succeeds");
            assertFalse(DamageCalculator.crits(-1, random), "a negative chance never succeeds");
        }
        int crits = 0;
        for (int i = 0; i < 20_000; i++) {
            crits += DamageCalculator.crits(0.25, random) ? 1 : 0;
        }
        assertEquals(0.25, crits / 20_000.0, 0.02, "about a quarter of the rolls at 25 %");
    }

    @Test
    void armorPierceTakesTheFlatPointsFirstThenThePercent() {
        assertEquals(20, DamageCalculator.piercedArmor(15, 5, 0, 0), EPSILON, "no pierce: armor plus toughness");
        assertEquals(15, DamageCalculator.piercedArmor(15, 5, 5, 0), EPSILON);
        assertEquals(7.5, DamageCalculator.piercedArmor(15, 5, 5, 0.5), EPSILON, "half of the 15 left");
        assertEquals(10, DamageCalculator.piercedArmor(15, 5, 0, 0.5), EPSILON);
    }

    @Test
    void armorPierceNeverLeavesNegativeArmor() {
        assertEquals(0, DamageCalculator.piercedArmor(15, 0, 40, 0), "more pierce than armor");
        assertEquals(0, DamageCalculator.piercedArmor(15, 0, 0, 2), "a percent above 1 counts as 1");
        assertEquals(15, DamageCalculator.piercedArmor(15, 0, -5, -1), EPSILON, "negative pierce counts as none");
    }

    @Test
    void piercedArmorBlocksLess() {
        assertEquals(DamageCalculator.armorReduction(10, 0, 15, 0.75),
                DamageCalculator.armorReduction(15, 0, 5, 0, 15, 0.75), EPSILON, "5 pierce against 15 armor");
        assertEquals(0, DamageCalculator.armorReduction(15, 5, 20, 0, 15, 0.75), "all armor pierced");
        assertEquals(DamageCalculator.armorReduction(15, 5, 15, 0.75),
                DamageCalculator.armorReduction(15, 5, 0, 0, 15, 0.75), EPSILON, "no pierce changes nothing");
    }

    @Test
    void spellResistanceAddsToAMagicSchoolsResistanceUpToTheCap() {
        assertEquals(0.45, DamageCalculator.resistance(0.25, 0.2, true), EPSILON);
        assertEquals(0.75, DamageCalculator.resistance(0.5, 0.5, true), EPSILON, "the sum stops at 75 %");
        assertEquals(DamageCalculator.RESISTANCE_CAP, DamageCalculator.resistance(0, 0.9, true), EPSILON);
        assertEquals(0.25, DamageCalculator.resistance(0.25, 0.5, false), EPSILON, "physical: no spell resistance");
        assertEquals(0.2, DamageCalculator.resistance(-0.5, 0.2, true), EPSILON, "a negative resistance counts as 0");
    }

    @Test
    void lifestealHealsItsShareOfTheDamageDealt() {
        assertEquals(2.5, DamageCalculator.lifesteal(10, 0.25), EPSILON);
        assertEquals(0, DamageCalculator.lifesteal(10, 0));
        assertEquals(10, DamageCalculator.lifesteal(10, 3), EPSILON, "a share above 1 counts as 1");
        assertEquals(0, DamageCalculator.lifesteal(-4, 0.5), "no damage, no heal");
    }

    @Test
    void overhealKeepsWhatHealingLeavesBeyondMaxHealthUpToTheCap() {
        assertEquals(2, DamageCalculator.overheal(4, 18, 20, 0, 0.25), EPSILON, "2 of the 4 go past 20");
        assertEquals(5, DamageCalculator.overheal(10, 20, 20, 2, 0.25), EPSILON, "the cap: 25 % of 20");
        assertEquals(0, DamageCalculator.overheal(2, 17, 20, 0, 0.25), "a heal within max health overheals nothing");
        assertEquals(1, DamageCalculator.overheal(4, 20, 20, 1, 0), EPSILON, "no overheal stat: nothing added");
        assertEquals(6, DamageCalculator.overheal(4, 20, 20, 6, 0.25), EPSILON,
                "absorption above a lowered cap stays as it is");
    }

    @Test
    void regenerationHealsItsPointsUpToMaxHealth() {
        assertEquals(2, DamageCalculator.regeneration(2, 10, 20), EPSILON);
        assertEquals(1, DamageCalculator.regeneration(2, 19, 20), EPSILON, "never beyond max health");
        assertEquals(0, DamageCalculator.regeneration(2, 20, 20), "a full health bar heals nothing");
        assertEquals(0, DamageCalculator.regeneration(-1, 10, 20), "a negative rate heals nothing");
    }

    @Test
    void theMagicShieldTakesWhatIsLeftUpToItsPoints() {
        assertEquals(6, DamageCalculator.shieldAbsorbs(6, 10), EPSILON);
        assertEquals(10, DamageCalculator.shieldAbsorbs(14, 10), EPSILON, "the rest goes to health");
        assertEquals(0, DamageCalculator.shieldAbsorbs(6, 0), "an empty shield takes nothing");
        assertEquals(0, DamageCalculator.shieldAbsorbs(0, 10), "nothing left after absorption hearts");
    }

    private static RandomSource random() {
        return RandomSource.create(42);
    }
}
