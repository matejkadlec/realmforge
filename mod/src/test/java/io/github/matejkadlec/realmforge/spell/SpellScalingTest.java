package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The spell engine's numbers (architecture 11): per-level values, scaling, cooldowns and resource pools. */
class SpellScalingTest {
    private static final double EPSILON = 1e-12;

    @Test
    void amountsGrowPerLevelFromLevelOne() {
        assertEquals(6, SpellScaling.atLevel(6, 2, 1), EPSILON);
        assertEquals(10, SpellScaling.atLevel(6, 2, 3), EPSILON);
        assertEquals(6, SpellScaling.atLevel(6, 0, 5), EPSILON);
        assertThrows(IllegalArgumentException.class, () -> SpellScaling.atLevel(6, 2, 0));
    }

    @Test
    void anAttackDamageAbilityAddsItsShareOfAttackDamage() {
        assertEquals(2 + 8, SpellScaling.base(2, 0, 1, 1, 8), EPSILON, "100 % of 8 attack damage");
        assertEquals(2 + 4 + 2, SpellScaling.base(2, 2, 2, 0.5, 8), EPSILON, "level 2 and half the attack damage");
        assertEquals(12, SpellScaling.base(12, 0, 1, 0, 50), EPSILON,
                "attack damage does nothing for magic (Matěj, 2026-10-15)");
        assertEquals(2, SpellScaling.base(2, 0, 1, 1, -5), EPSILON, "negative attack damage counts as none");
    }

    @Test
    void magicHealsScaleWithSpellPowerAndTheSchoolsPower() {
        assertEquals(10 * 1.7, SpellScaling.heal(10, Scaling.SPELL_POWER, 0.5, 0.2), EPSILON);
        assertEquals(10, SpellScaling.heal(10, Scaling.ATTACK_DAMAGE, 0.5, 0.2), EPSILON,
                "spell power does nothing for a physical ability");
        assertEquals(0, SpellScaling.heal(10, Scaling.SPELL_POWER, -3, 0), EPSILON, "never negative");
    }

    @Test
    void cooldownReductionShortensCooldownsToTheNearestTick() {
        assertEquals(60, SpellScaling.cooldownTicks(60, 0));
        assertEquals(45, SpellScaling.cooldownTicks(60, 0.25));
        assertEquals(15, SpellScaling.cooldownTicks(60, 0.75), "the stat's cap");
        assertEquals(31, SpellScaling.cooldownTicks(61, 0.5), "rounded to the nearest tick");
        assertEquals(0, SpellScaling.cooldownTicks(60, 2), "a reduction counts at most 100 %");
        assertEquals(60, SpellScaling.cooldownTicks(60, -1), "a negative reduction lengthens nothing");
        assertEquals(0, SpellScaling.cooldownTicks(0, 0.5), "no cooldown stays none");
    }

    @Test
    void castTimeReductionShortensCastTimesToTheNearestTick() {
        assertEquals(40, SpellScaling.castTicks(40, 0));
        assertEquals(20, SpellScaling.castTicks(40, 0.5));
        assertEquals(10, SpellScaling.castTicks(40, 0.75), "the stat's cap");
        assertEquals(11, SpellScaling.castTicks(21, 0.5), "rounded to the nearest tick");
        assertEquals(1, SpellScaling.castTicks(40, 1), "a cast time never drops below a tick");
        assertEquals(1, SpellScaling.castTicks(1, 0.75), "nor does a one-tick cast");
        assertEquals(40, SpellScaling.castTicks(40, -1), "a negative reduction lengthens nothing");
        assertThrows(IllegalArgumentException.class, () -> SpellScaling.castTicks(0, 0.5), "an instant cast");
    }

    /** A channel keeps its tick count: Cast Time Reduction shortens its interval, and its duration follows. */
    @Test
    void aChannelKeepsItsTickCount() {
        SpellCast drain = SpellCast.channel(60, 20, false, false);
        assertEquals(3, drain.tickCount());
        int interval = SpellScaling.castTicks(drain.intervalTicks(), 0.5);
        assertEquals(10, interval);
        assertEquals(30, interval * drain.tickCount(), "half the duration, the same three ticks");
    }

    @Test
    void poolsRegainTheirRegenerationWithinTheirMaximum() {
        assertEquals(55, SpellScaling.regenerate(50, 5, 100), EPSILON);
        assertEquals(100, SpellScaling.regenerate(98, 5, 100), EPSILON, "never above the maximum");
        assertEquals(80, SpellScaling.regenerate(90, 5, 80), EPSILON, "a pool above a lowered maximum drops to it");
        assertEquals(0, SpellScaling.regenerate(3, -5, 100), EPSILON, "a drain stops at 0");
        assertEquals(40, SpellScaling.regenerate(40, 5, 0), EPSILON,
                "no maximum yet (stats not applied): the pool stays as it is");
    }
}
