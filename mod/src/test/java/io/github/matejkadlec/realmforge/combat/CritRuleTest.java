package io.github.matejkadlec.realmforge.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.combat.DamageCalculator.Attacker;
import io.github.matejkadlec.realmforge.testing.RoundTrip;
import java.util.Optional;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** Spell crits (Matěj, 2026-10-15): every spell can crit, heals too, and a spell may change its crit in data. */
class CritRuleTest {
    private static final double EPSILON = 1e-12;
    private static final CritRule DOUBLE_ON_BONUS = new CritRule(true, Optional.of(2.0), 1);
    private static final CritRule NEVER = new CritRule(false, Optional.empty(), 0);

    @Test
    void theDefaultCritsOnTheCastersChanceForOnePlusCritDamage() {
        RandomSource random = random();
        assertEquals(new DamageCalculator.Crit(true, 1.5), DamageCalculator.crit(CritRule.DEFAULT, 1, 0.5, random),
                "150 % with the base 50 % crit damage");
        assertEquals(new DamageCalculator.Crit(true, 2.1), DamageCalculator.crit(CritRule.DEFAULT, 1, 1.1, random),
                "crit damage is shared with melee, so a bonus raises spell crits too");
        assertEquals(DamageCalculator.Crit.NONE, DamageCalculator.crit(CritRule.DEFAULT, 0, 0.5, random));
    }

    @Test
    void aRuleThatCannotCritNeverDoes() {
        RandomSource random = random();
        for (int i = 0; i < 100; i++) {
            assertEquals(DamageCalculator.Crit.NONE, DamageCalculator.crit(NEVER, 1, 0.5, random));
        }
        assertEquals(0, NEVER.chance(0.8), EPSILON);
    }

    @Test
    void aBonusChanceAddsToTheCastersAndAMultiplierReplacesCritDamage() {
        assertEquals(0.35, new CritRule(true, Optional.empty(), 0.3).chance(0.05), EPSILON);
        assertEquals(new DamageCalculator.Crit(true, 2), DamageCalculator.crit(DOUBLE_ON_BONUS, 0, 0.5, random()),
                "a bonus of 100 % always crits, for the rule's own 200 %");
    }

    @Test
    void schoolHitsCritByTheRule() {
        Attacker sureCrit = new Attacker(1, 0.5, 0, 0);
        assertEquals(15, DamageCalculator.schoolHit(10, true, sureCrit, 0, random()).damage(), EPSILON,
                "a hit without a rule of its own crits by the default");
        assertEquals(10, DamageCalculator.schoolHit(10, true, sureCrit, 0, NEVER, random()).damage(), EPSILON);
        DamageCalculator.SchoolHit doubled = DamageCalculator.schoolHit(10, true, Attacker.NONE, 0.5, DOUBLE_ON_BONUS,
                random());
        assertTrue(doubled.crit());
        assertEquals(10, doubled.damage(), EPSILON, "doubled, then halved by the resistance");
    }

    @Test
    void codecReadsTheGeneratedShapeAndItsDefaults() {
        assertEquals(DOUBLE_ON_BONUS, CritRule.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"can_crit\": true, \"multiplier\": 2.0, \"bonus_chance\": 1.0}"))
                .getOrThrow());
        assertEquals(CritRule.DEFAULT, CritRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
                .getOrThrow());
        assertEquals(NEVER, RoundTrip.json(CritRule.CODEC, NEVER));
        assertTrue(CritRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"multiplier\": 0.5}")).isError(),
                "a crit never weakens a hit");
        assertTrue(CritRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"bonus_chance\": 1.5}")).isError(),
                "a chance is a share");
    }

    @Test
    void constructorEnforcesTheSameRules() {
        assertThrows(IllegalArgumentException.class, () -> new CritRule(true, Optional.of(0.9), 0));
        assertThrows(IllegalArgumentException.class, () -> new CritRule(true, Optional.empty(), -0.1));
        assertThrows(IllegalArgumentException.class, () -> new CritRule(true, Optional.empty(), Double.NaN));
        assertFalse(NEVER.canCrit());
    }

    private static RandomSource random() {
        return RandomSource.create(42);
    }
}
