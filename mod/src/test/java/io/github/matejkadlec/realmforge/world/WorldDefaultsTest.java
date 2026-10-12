package io.github.matejkadlec.realmforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.gamerules.GameRules;
import org.junit.jupiter.api.Test;

class WorldDefaultsTest {
    @Test
    void newWorldRulesMatchTheVision() {
        GameRules rules = new GameRules(FeatureFlags.DEFAULT_FLAGS);
        WorldDefaults.apply(rules);
        assertTrue(rules.get(GameRules.KEEP_INVENTORY));
        assertTrue(rules.get(GameRules.IMMEDIATE_RESPAWN));
        assertFalse(rules.get(GameRules.BLOCK_EXPLOSION_DROP_DECAY));
        assertFalse(rules.get(GameRules.MOB_EXPLOSION_DROP_DECAY));
        assertFalse(rules.get(GameRules.TNT_EXPLOSION_DROP_DECAY));
    }

    /** Targeted protection replaces mob_griefing=false (D6), so the defaults leave it alone. */
    @Test
    void mobGriefingKeepsItsVanillaValue() {
        GameRules rules = new GameRules(FeatureFlags.DEFAULT_FLAGS);
        WorldDefaults.apply(rules);
        assertTrue(rules.get(GameRules.MOB_GRIEFING));
        assertEquals(5, WorldDefaults.RULES.size());
    }
}
