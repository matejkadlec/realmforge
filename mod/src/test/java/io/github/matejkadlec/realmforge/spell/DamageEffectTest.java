package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.combat.HitBonus;
import org.junit.jupiter.api.Test;

/**
 * A damage effect's per-rank armor shred and spell penetration (C4): Slam's 5 % a rank, Arcane Missiles' too; and its
 * coefficient of the caster's power (RLM-534).
 */
class DamageEffectTest {
    private static final double EPSILON = 1e-12;

    @Test
    void theBonusGrowsWithTheLevelUpToAll() {
        DamageEffect slam = new DamageEffect(4, 1.5, new Coefficient(1.2, 0.2, 0, 0), 0.05, 0.05, 0, 0);
        assertEquals(new HitBonus(0.05, 0), slam.bonusAt(1));
        assertEquals(0.25, slam.bonusAt(5).armorShred(), EPSILON);
        DamageEffect missiles = new DamageEffect(5, 1, Coefficient.NONE, 0, 0, 0.5, 0.25);
        assertEquals(1, missiles.bonusAt(5).penetration(), EPSILON, "at most the whole resistance");
        assertEquals(HitBonus.NONE, new DamageEffect(1, 0, Coefficient.NONE).bonusAt(9), "a plain hit adds nothing");
    }

    @Test
    void codecReadsTheGeneratedShapeAndItsDefaults() {
        DamageEffect read = DamageEffect.MAP_CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"amount\": 4.0, \"per_level\": 1.5, \"attack_damage\": 1.2, \"attack_damage_per_level\": 0.2, "
                        + "\"armor_shred\": 0.05, \"armor_shred_per_level\": 0.05}")).getOrThrow();
        assertEquals(new DamageEffect(4, 1.5, new Coefficient(1.2, 0.2, 0, 0), 0.05, 0.05, 0, 0), read);
        assertTrue(DamageEffect.MAP_CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"amount\": 1.0, \"armor_shred\": 1.5}")).isError(), "a share is at most 1");
        DamageEffect frostbolt = DamageEffect.MAP_CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"amount\": 8.0, \"per_level\": 1.0, \"spell_power\": 1.0, \"spell_power_per_level\": 0.25}"))
                .getOrThrow();
        assertEquals(new DamageEffect(8, 1, Coefficient.spellPower(1, 0.25)), frostbolt);
        assertEquals(50, frostbolt.baseAt(5, 0, 19), EPSILON, "rank 5 with 19 Spell Power (RLM-534)");
    }
}
