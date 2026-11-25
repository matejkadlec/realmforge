package io.github.matejkadlec.realmforge.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/**
 * The spell point rules (C5, RLM-333, RLM-631): what a level earns, what ranks cost, rows that open by points spent in
 * the tree, arrows, and every refusal.
 */
class SpellPointsTest {
    private static final ResourceKey<Spell> FIRST = spell("frostbolt");
    private static final ResourceKey<Spell> OTHER = spell("cone_of_cold");
    private static final ResourceKey<Spell> OUTSIDE = spell("will_to_survive");
    private static final List<ResourceKey<Spell>> TREE = List.of(FIRST, OTHER);
    private static final ResourceKey<Spell> ROW_TWO = spell("frost_nova");
    private static final ResourceKey<Spell> BESIDE = spell("shatter");
    private static final ResourceKey<Spell> ROW_THREE = spell("ice_lance");
    private static final ResourceKey<Spell> BOTH = spell("bloodthirst");
    /** A small tree: the free first spell and another in row 1, two in row 2 (one needing the other), two in row 3. */
    private static final List<SpellPoints.Place> PLACES = List.of(
            new SpellPoints.Place(FIRST, 1, List.of()),
            new SpellPoints.Place(OTHER, 1, List.of()),
            new SpellPoints.Place(ROW_TWO, 2, List.of()),
            new SpellPoints.Place(BESIDE, 2, List.of(ROW_TWO)),
            new SpellPoints.Place(ROW_THREE, 3, List.of()),
            new SpellPoints.Place(BOTH, 3, List.of(ROW_THREE, ROW_TWO)));

    private static ResourceKey<Spell> spell(String path) {
        return ResourceKey.create(SpellRegistries.SPELL, Realmforge.id(path));
    }

    /** Exactly one point per level from level 2 on (Matěj, 2026-11-03 and 2026-11-04): 17 at level 18. */
    @Test
    void aLevelEarnsOnePointFromLevelTwoOn() {
        assertEquals(0, SpellPoints.earned(1));
        assertEquals(1, SpellPoints.earned(2));
        assertEquals(17, SpellPoints.earned(18));
    }

    @Test
    void theFirstSpellsFirstRankIsFree() {
        assertEquals(0, SpellPoints.spent(Map.of(FIRST, 1), TREE, Optional.of(FIRST)));
        assertEquals(1, SpellPoints.spent(Map.of(FIRST, 2), TREE, Optional.of(FIRST)));
    }

    @Test
    void everyOtherRankCostsAPointRankOneIncluded() {
        assertEquals(1, SpellPoints.spent(Map.of(FIRST, 1, OTHER, 1), TREE, Optional.of(FIRST)));
        assertEquals(5, SpellPoints.spent(Map.of(FIRST, 3, OTHER, 3), TREE, Optional.of(FIRST)));
    }

    /** With the first spell unknown nothing is free, and the count never goes below 0. */
    @Test
    void nothingIsFreeWhileTheFirstSpellIsUnknown() {
        assertEquals(2, SpellPoints.spent(Map.of(OTHER, 2), TREE, Optional.of(FIRST)));
        assertEquals(0, SpellPoints.spent(Map.of(), TREE, Optional.of(FIRST)));
    }

    @Test
    void spellsOutsideTheTreeCostNothing() {
        assertEquals(0, SpellPoints.spent(Map.of(FIRST, 1, OUTSIDE, 1), TREE, Optional.of(FIRST)));
    }

    @Test
    void aPointGoesThroughWhenEveryRuleHolds() {
        assertEquals(SpellPoints.Result.SPENT, SpellPoints.check(0, 1, 0, 5, 1, true));
        assertEquals(SpellPoints.Result.SPENT, SpellPoints.check(12, 1, 4, 5, 3, true));
    }

    @Test
    void theHighestRankRefuses() {
        assertEquals(SpellPoints.Result.MAX_RANK, SpellPoints.check(17, 5, 5, 5, 1, true));
    }

    /** Row N opens after 5 x (N - 1) points spent in the tree (Matěj, 2026-11-04, RLM-631): 0, 5, 10, 15. */
    @Test
    void aRowOpensAfterFivePointsPerRowAboveIt() {
        assertEquals(List.of(0, 5, 10, 15), List.of(SpellPoints.rowPoints(1), SpellPoints.rowPoints(2),
                SpellPoints.rowPoints(3), SpellPoints.rowPoints(4)));
        assertEquals(List.of(1, 1, 2, 2, 3, 4), List.of(SpellPoints.openRows(0), SpellPoints.openRows(4),
                SpellPoints.openRows(5), SpellPoints.openRows(9), SpellPoints.openRows(10), SpellPoints.openRows(17)));
    }

    /** One point short of a row's threshold refuses its spells; the threshold itself opens them. */
    @Test
    void onePointShortRefusesAndTheThresholdOpens() {
        assertEquals(SpellPoints.Result.ROW_CLOSED, SpellPoints.check(4, 4, 0, 3, 2, true));
        assertEquals(SpellPoints.Result.SPENT, SpellPoints.check(5, 4, 0, 3, 2, true));
        assertEquals(SpellPoints.Result.ROW_CLOSED, SpellPoints.check(9, 8, 0, 5, 3, true));
        assertEquals(SpellPoints.Result.SPENT, SpellPoints.check(10, 7, 0, 5, 3, true));
        assertEquals(SpellPoints.Result.ROW_CLOSED, SpellPoints.check(14, 3, 0, 3, 4, true));
        assertEquals(SpellPoints.Result.SPENT, SpellPoints.check(15, 2, 0, 3, 4, true));
    }

    /**
     * The free rank opens nothing: the first spell at its highest rank 5 is 4 points spent, one short of row 2, and a
     * point elsewhere in row 1 makes 5.
     */
    @Test
    void theFreeRankDoesNotCountTowardsARow() {
        Map<ResourceKey<Spell>, Integer> four = Map.of(FIRST, 5);
        assertEquals(4, SpellPoints.spent(four, TREE, Optional.of(FIRST)));
        assertEquals(SpellPoints.Result.ROW_CLOSED,
                SpellPoints.check(SpellPoints.spent(four, TREE, Optional.of(FIRST)), 5, 0, 3, 2, true));
        Map<ResourceKey<Spell>, Integer> five = Map.of(FIRST, 5, OTHER, 1);
        assertEquals(SpellPoints.Result.SPENT,
                SpellPoints.check(SpellPoints.spent(five, TREE, Optional.of(FIRST)), 4, 0, 3, 2, true));
        assertEquals(5, SpellPoints.spentAbove(five, PLACES, Optional.of(FIRST), 2));
        assertEquals(0, SpellPoints.spentAbove(five, PLACES, Optional.of(FIRST), 1));
    }

    /** A spell with several arrows needs every one of their spells, at any rank. */
    @Test
    void severalArrowsNeedEveryTheirSpell() {
        SpellPoints.Place both = PLACES.getLast();
        assertFalse(SpellPoints.requirementsMet(Map.of(FIRST, 1), both));
        assertFalse(SpellPoints.requirementsMet(Map.of(FIRST, 1, ROW_THREE, 1), both));
        assertFalse(SpellPoints.requirementsMet(Map.of(FIRST, 1, ROW_TWO, 3), both));
        assertTrue(SpellPoints.requirementsMet(Map.of(FIRST, 1, ROW_TWO, 1, ROW_THREE, 1), both));
        assertTrue(SpellPoints.requirementsMet(Map.of(), PLACES.getFirst()), "no arrow, nothing needed");
    }

    @Test
    void anArrowRefusesUntilItsSpellsAreKnown() {
        assertEquals(SpellPoints.Result.NEEDS_SPELL, SpellPoints.check(10, 3, 0, 2, 3, false));
    }

    @Test
    void noPointsRefuses() {
        assertEquals(SpellPoints.Result.NO_POINTS, SpellPoints.check(10, 0, 0, 2, 3, true));
    }

    /** The refusal names what a point could not fix first: the rank, then the row, then the arrow. */
    @Test
    void refusalsComeInOrder() {
        assertEquals(SpellPoints.Result.MAX_RANK, SpellPoints.check(0, 0, 2, 2, 3, false));
        assertEquals(SpellPoints.Result.ROW_CLOSED, SpellPoints.check(0, 0, 0, 2, 3, false));
        assertEquals(SpellPoints.Result.NEEDS_SPELL, SpellPoints.check(10, 0, 0, 2, 3, false));
    }

    /** Ranks a legal history leaves follow the rules, whatever order the points went in. */
    @Test
    void aLegalHistoryFollowsTheRules() {
        assertTrue(SpellPoints.followsTheRules(Map.of(FIRST, 1), PLACES, Optional.of(FIRST)));
        // 5 in row 1 (the free rank aside), then 5 in row 2, then row 3 with both its arrows' spells known.
        assertTrue(SpellPoints.followsTheRules(Map.of(FIRST, 5, OTHER, 1, ROW_TWO, 3, BESIDE, 2, ROW_THREE, 1,
                BOTH, 1), PLACES, Optional.of(FIRST)));
    }

    /**
     * A tree whose layout changed under a character: a spell in a row the points above it never opened, or a spell
     * whose arrow's spell is unknown.
     */
    @Test
    void ranksTheTreeNoLongerAllowsBreakTheRules() {
        assertFalse(SpellPoints.followsTheRules(Map.of(FIRST, 4, ROW_TWO, 1), PLACES, Optional.of(FIRST)),
                "row 2 with 3 points above it");
        // 8 points above row 3, though 10 are spent in all: the row 3 spell's own points do not open its row.
        assertFalse(SpellPoints.followsTheRules(Map.of(FIRST, 5, OTHER, 1, ROW_TWO, 3, ROW_THREE, 2), PLACES,
                Optional.of(FIRST)), "row 3 with 8 points above it");
        assertFalse(SpellPoints.followsTheRules(Map.of(FIRST, 5, OTHER, 1, BESIDE, 1), PLACES, Optional.of(FIRST)),
                "Shatter without Frost Nova");
    }
}
