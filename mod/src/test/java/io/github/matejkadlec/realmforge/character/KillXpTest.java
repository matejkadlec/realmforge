package io.github.matejkadlec.realmforge.character;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.core.Curve.Point;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KillXpTest {
    /** 10 XP for a level 1 victim, 100 for level 10. */
    private static final Curve BASE = new Curve(Optional.empty(), List.of(new Point(1, 10), new Point(10, 100)));
    /** Full XP up to 2 levels below, nothing from 6 below. */
    private static final Curve BELOW = new Curve(Optional.empty(),
            List.of(new Point(0, 1), new Point(2, 1), new Point(6, 0)));
    /** 10 % more per level above, up to 4 levels. */
    private static final Curve ABOVE = new Curve(Optional.empty(), List.of(new Point(0, 1), new Point(4, 1.4)));

    @Test
    void aKillAtThePlayersLevelGivesTheBaseXp() {
        assertEquals(10, KillXp.of(BASE, BELOW, ABOVE, 1, 1, 1.0, 1.0));
        assertEquals(100, KillXp.of(BASE, BELOW, ABOVE, 10, 10, 1.0, 1.0));
    }

    @Test
    void victimsBelowThePlayerGiveLessAndFarBelowNothing() {
        assertEquals(100, KillXp.of(BASE, BELOW, ABOVE, 10, 12, 1.0, 1.0));
        assertEquals(50, KillXp.of(BASE, BELOW, ABOVE, 10, 14, 1.0, 1.0));
        assertEquals(0, KillXp.of(BASE, BELOW, ABOVE, 10, 16, 1.0, 1.0));
        assertEquals(0, KillXp.of(BASE, BELOW, ABOVE, 1, 20, 1.0, 1.0));
    }

    @Test
    void victimsAboveThePlayerGiveMoreUpToTheCurvesEnd() {
        assertEquals(120, KillXp.of(BASE, BELOW, ABOVE, 10, 8, 1.0, 1.0));
        assertEquals(140, KillXp.of(BASE, BELOW, ABOVE, 10, 6, 1.0, 1.0));
        assertEquals(140, KillXp.of(BASE, BELOW, ABOVE, 10, 1, 1.0, 1.0));
    }

    @Test
    void theXpMultiplierScalesTheKillAndRoundsHalfUp() {
        assertEquals(15, KillXp.of(BASE, BELOW, ABOVE, 1, 1, 1.0, 1.5));
        assertEquals(0, KillXp.of(BASE, BELOW, ABOVE, 1, 1, 1.0, 0));
        assertEquals(13, KillXp.of(BASE, BELOW, ABOVE, 1, 1, 1.0, 1.25));
    }

    @Test
    void theVictimsRankScalesTheKillBeforeTheLevelDifference() {
        assertEquals(15, KillXp.of(BASE, BELOW, ABOVE, 1, 1, 1.5, 1.0));
        assertEquals(1000, KillXp.of(BASE, BELOW, ABOVE, 10, 10, 10, 1.0));
        assertEquals(0, KillXp.of(BASE, BELOW, ABOVE, 10, 10, 0, 1.0));
        assertEquals(25, KillXp.of(BASE, BELOW, ABOVE, 10, 14, 0.5, 1.0));
        assertEquals(30, KillXp.of(BASE, BELOW, ABOVE, 1, 1, 2, 1.5));
    }

    @Test
    void aNegativeCurveGivesNoXpRatherThanTakingIt() {
        Curve negative = new Curve(Optional.empty(), List.of(new Point(1, -50)));
        assertEquals(0, KillXp.of(negative, BELOW, ABOVE, 1, 1, 1.0, 1.0));
    }
}
