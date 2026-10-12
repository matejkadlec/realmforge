package io.github.matejkadlec.realmforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.GameRules;
import org.junit.jupiter.api.Test;

class WorldDefaultsTest {
    @Test
    void newWorldRulesMatchTheVision() {
        GameRules rules = new GameRules();
        WorldDefaults.apply(rules);
        assertTrue(rules.getBoolean(GameRules.RULE_KEEPINVENTORY));
        assertTrue(rules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN));
        assertFalse(rules.getBoolean(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY));
        assertFalse(rules.getBoolean(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY));
        assertFalse(rules.getBoolean(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY));
    }

    /** Targeted protection replaces mobGriefing=false (D6), so the defaults leave it alone. */
    @Test
    void mobGriefingKeepsItsVanillaValue() {
        GameRules rules = new GameRules();
        WorldDefaults.apply(rules);
        assertTrue(rules.getBoolean(GameRules.RULE_MOBGRIEFING));
        assertEquals(5, WorldDefaults.RULES.size());
    }
}
