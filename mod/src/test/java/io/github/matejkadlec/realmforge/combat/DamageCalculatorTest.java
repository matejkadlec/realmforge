package io.github.matejkadlec.realmforge.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.combat.DamageCalculator.Attacker;
import io.github.matejkadlec.realmforge.combat.DamageCalculator.SchoolHit;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import org.junit.jupiter.api.Test;

class DamageCalculatorTest {
    private static final double EPSILON = 1e-12;
    // Crit chance 0: no roll, so the school formula alone decides.
    private static final Attacker NO_CRIT = new Attacker(0, 0.5, 0.2, 0.1);

    @Test
    void armorMatchesVanillasFormula() {
        DamageSource source = new DamageSource(Holder.direct(new DamageType("generic", 0)));
        for (float damage : new float[] {0.5F, 1, 4, 10, 33, 90, 500}) {
            for (int armor = 0; armor <= 40; armor += 5) {
                for (float toughness : new float[] {0, 2, 8, 12, 18, 30}) {
                    // Without a weapon vanilla never reads the victim, so none is needed.
                    float left = CombatRules.getDamageAfterAbsorb(null, damage, source, armor, toughness);
                    assertEquals(1 - left / damage, DamageCalculator.armorReduction(damage, armor, toughness), 1e-6,
                            damage + " damage against " + armor + " armor and " + toughness + " toughness");
                }
            }
        }
    }

    @Test
    void armorAndToughnessKeepTheirOwnRoles() {
        assertEquals(0.4, DamageCalculator.armorReduction(10, 15, 0), EPSILON,
                "15 − 10 / 2 effective armor, 4 % each");
        assertTrue(DamageCalculator.armorReduction(33, 20, 0) < DamageCalculator.armorReduction(4, 20, 0),
                "armor blocks less of a bigger hit");
        assertTrue(DamageCalculator.armorReduction(33, 20, 12) > DamageCalculator.armorReduction(33, 20, 0),
                "toughness keeps armor working against big hits");
        assertEquals(DamageCalculator.armorReduction(1, 20, 0), DamageCalculator.armorReduction(1, 20, 18), 0.02,
                "toughness barely matters against small hits");
        assertEquals(0, DamageCalculator.armorReduction(10, 0, 18), "toughness alone blocks nothing");
    }

    @Test
    void armorNeverBlocksMoreThan80Percent() {
        assertEquals(0.8, DamageCalculator.armorReduction(1, 10_000, 0), EPSILON);
        assertEquals(0.8, DamageCalculator.armorReduction(33, 30, 18), EPSILON, "netherite × 1.5 at a level 20 hit");
    }

    @Test
    void armorKeepsAFifthOfItselfAgainstHugeHits() {
        assertEquals(15 * 0.2 / 25, DamageCalculator.armorReduction(1_000, 15, 0), EPSILON);
    }

    @Test
    void negativeArmorOrToughnessCountsAsNone() {
        assertEquals(0, DamageCalculator.armorReduction(10, -5, 0));
        assertEquals(DamageCalculator.armorReduction(10, 15, 0), DamageCalculator.armorReduction(10, 15, -4), EPSILON);
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
    void armorPierceTakesTheFlatPointsFirstThenTheShred() {
        assertEquals(15, DamageCalculator.piercedArmor(15, 0, 0), EPSILON, "no pierce");
        assertEquals(10, DamageCalculator.piercedArmor(15, 5, 0), EPSILON);
        assertEquals(5, DamageCalculator.piercedArmor(15, 5, 0.5), EPSILON, "half of the 10 left");
        assertEquals(7.5, DamageCalculator.piercedArmor(15, 0, 0.5), EPSILON);
    }

    @Test
    void armorPierceNeverLeavesNegativeArmor() {
        assertEquals(0, DamageCalculator.piercedArmor(15, 40, 0), "more pierce than armor");
        assertEquals(0, DamageCalculator.piercedArmor(15, 0, 2), "a share above 1 counts as 1");
        assertEquals(15, DamageCalculator.piercedArmor(15, -5, -1), EPSILON, "negative pierce counts as none");
        assertEquals(0, DamageCalculator.piercedArmor(-5, 0, 0), "negative armor counts as none");
    }

    @Test
    void piercedArmorBlocksLessAndToughnessIsNeverPierced() {
        assertEquals(DamageCalculator.armorReduction(10, 10, 8),
                DamageCalculator.armorReduction(10, 15, 8, 5, 0), EPSILON, "5 pierce against 15 armor, 8 toughness");
        assertEquals(DamageCalculator.armorReduction(10, 5, 8),
                DamageCalculator.armorReduction(10, 15, 8, 5, 0.5), EPSILON, "then 50 % shred of the 10 left");
        assertEquals(0, DamageCalculator.armorReduction(10, 15, 8, 20, 0), "all armor pierced: toughness alone");
        assertEquals(DamageCalculator.armorReduction(10, 15, 8),
                DamageCalculator.armorReduction(10, 15, 8, 0, 0), EPSILON, "no pierce changes nothing");
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
