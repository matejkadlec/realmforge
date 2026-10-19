package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

/** Rage's numbers (architecture 11.5): gain from hits dealt and damage taken, and decay out of combat. */
class RageRulesTest {
    private static final double EPSILON = 1e-12;
    private static final RageRules WARRIOR = new RageRules(10, 50, 2, 100);

    @Test
    void aHitDealtGivesItsShareOfAFullStrengthHit() {
        assertEquals(10, WARRIOR.forHitDealt(1), EPSILON);
        assertEquals(5, WARRIOR.forHitDealt(0.5), EPSILON, "a half-strength swing");
        assertEquals(10, WARRIOR.forHitDealt(1.5), EPSILON, "never more than a full-strength hit");
        assertEquals(0, WARRIOR.forHitDealt(-1), EPSILON, "never negative");
    }

    @Test
    void damageTakenGivesRageByTheShareOfMaxHealth() {
        assertEquals(25, WARRIOR.forDamageTaken(10, 20), EPSILON, "half the max health");
        assertEquals(5, WARRIOR.forDamageTaken(2, 20), EPSILON, "a tenth");
        assertEquals(0, WARRIOR.forDamageTaken(0, 20), EPSILON, "no damage");
        assertEquals(0, WARRIOR.forDamageTaken(5, 0), EPSILON, "no health to lose");
    }

    @Test
    void ragePoolsDecayOnlyOutOfCombat() {
        assertEquals(0, WARRIOR.regeneration(true), EPSILON);
        assertEquals(-2, WARRIOR.regeneration(false), EPSILON);
    }

    @Test
    void theClassDataDecodes() {
        RageRules rules = RageRules.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"per_hit_dealt\": 10.0, \"per_max_health_taken\": 50.0, \"decay_per_second\": 2.0,"
                        + " \"decay_after_ticks\": 100}")).getOrThrow();
        assertEquals(WARRIOR, rules);
    }
}
