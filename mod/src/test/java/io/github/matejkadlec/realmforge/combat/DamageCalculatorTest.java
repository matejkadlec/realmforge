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

    private static RandomSource random() {
        return RandomSource.create(42);
    }
}
