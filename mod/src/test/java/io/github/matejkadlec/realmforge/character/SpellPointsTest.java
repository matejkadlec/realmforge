package io.github.matejkadlec.realmforge.character;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** The spell point rules (C5, RLM-333): what a level earns, what ranks cost, and every refusal. */
class SpellPointsTest {
    private static final ResourceKey<Spell> FIRST = spell("frostbolt");
    private static final ResourceKey<Spell> OTHER = spell("cone_of_cold");
    private static final ResourceKey<Spell> OUTSIDE = spell("will_to_survive");
    private static final List<ResourceKey<Spell>> TREE = List.of(FIRST, OTHER);

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
        assertEquals(SpellPoints.Result.SPENT, SpellPoints.check(6, 1, 0, 5, 6, true));
        assertEquals(SpellPoints.Result.SPENT, SpellPoints.check(18, 1, 4, 5, 6, true));
    }

    @Test
    void theHighestRankRefuses() {
        assertEquals(SpellPoints.Result.MAX_RANK, SpellPoints.check(18, 5, 5, 5, 1, true));
    }

    /** A row opens exactly at its level; RLM-631 replaces that with points spent in the tree (Matěj, 2026-11-04). */
    @Test
    void aRowOpensAtItsLevel() {
        assertEquals(SpellPoints.Result.LEVEL_TOO_LOW, SpellPoints.check(5, 4, 0, 5, 6, true));
        assertEquals(SpellPoints.Result.SPENT, SpellPoints.check(6, 4, 0, 5, 6, true));
    }

    @Test
    void anArrowRefusesUntilItsSpellIsKnown() {
        assertEquals(SpellPoints.Result.NEEDS_SPELL, SpellPoints.check(12, 3, 0, 2, 12, false));
    }

    @Test
    void noPointsRefuses() {
        assertEquals(SpellPoints.Result.NO_POINTS, SpellPoints.check(12, 0, 0, 2, 12, true));
    }

    /** The refusal names what a point could not fix first: the rank, then the level, then the arrow. */
    @Test
    void refusalsComeInOrder() {
        assertEquals(SpellPoints.Result.MAX_RANK, SpellPoints.check(1, 0, 2, 2, 12, false));
        assertEquals(SpellPoints.Result.LEVEL_TOO_LOW, SpellPoints.check(1, 0, 0, 2, 12, false));
        assertEquals(SpellPoints.Result.NEEDS_SPELL, SpellPoints.check(12, 0, 0, 2, 12, false));
    }
}
