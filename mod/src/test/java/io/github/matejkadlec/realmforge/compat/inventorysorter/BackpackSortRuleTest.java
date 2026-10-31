package io.github.matejkadlec.realmforge.compat.inventorysorter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** RLM-519: the backpack rule joins a player's Inventory Sorting rules once, beside their own. */
class BackpackSortRuleTest {
    @Test
    void theRuleIsMissingFromNoRulesOrOtherRules() {
        assertTrue(BackpackSortRule.isMissing(List.of()));
        assertTrue(BackpackSortRule.isMissing(List.of("minecraft:torch", "#minecraft:logs")));
    }

    @Test
    void theRuleIsNotAddedTwice() {
        assertFalse(BackpackSortRule.isMissing(List.of("minecraft:torch", BackpackSortRule.MATCH)));
        assertFalse(BackpackSortRule.isMissing(List.of(" " + BackpackSortRule.MATCH + " ")));
    }

    @Test
    void everyTierIsMatched() {
        for (String tier : List.of("", "copper_", "iron_", "gold_", "diamond_", "netherite_")) {
            assertTrue(BackpackSortRule.MATCH.contains("sophisticatedbackpacks:" + tier + "backpack"), tier);
        }
    }
}
