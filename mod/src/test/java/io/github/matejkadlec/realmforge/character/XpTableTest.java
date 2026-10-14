package io.github.matejkadlec.realmforge.character;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.core.Curve.Point;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class XpTableTest {
    /** 100 XP from level 1, 200 from level 2, 300 from level 3 and on; 150 at level 1.5 is never read. */
    private static final Curve CURVE = new Curve(Optional.empty(),
            List.of(new Point(1, 100), new Point(3, 300)));

    @Test
    void toNextIsTheCurveRoundedHalfUp() {
        assertEquals(100, XpTable.toNext(CURVE, 1));
        assertEquals(200, XpTable.toNext(CURVE, 2));
        assertEquals(300, XpTable.toNext(CURVE, 7));
        Curve halves = new Curve(Optional.empty(), List.of(new Point(1, 0.5), new Point(2, 2.5)));
        assertEquals(1, XpTable.toNext(halves, 1));
        assertEquals(3, XpTable.toNext(halves, 2));
    }

    @Test
    void aLevelAlwaysCostsAtLeastOneXp() {
        Curve free = new Curve(Optional.empty(), List.of(new Point(1, 0), new Point(5, -10)));
        assertEquals(1, XpTable.toNext(free, 1));
        assertEquals(1, XpTable.toNext(free, 5));
        assertEquals(3, XpTable.levelFor(free, 2, 10));
    }

    @Test
    void toReachSumsTheLevelsBelow() {
        assertEquals(0, XpTable.toReach(CURVE, 1));
        assertEquals(100, XpTable.toReach(CURVE, 2));
        assertEquals(300, XpTable.toReach(CURVE, 3));
        assertEquals(600, XpTable.toReach(CURVE, 4));
    }

    @Test
    void levelIsTheHighestLevelWhoseXpThePlayerHas() {
        assertEquals(1, XpTable.levelFor(CURVE, 0, 10));
        assertEquals(1, XpTable.levelFor(CURVE, 99, 10));
        assertEquals(2, XpTable.levelFor(CURVE, 100, 10));
        assertEquals(2, XpTable.levelFor(CURVE, 299, 10));
        assertEquals(3, XpTable.levelFor(CURVE, 300, 10));
        assertEquals(4, XpTable.levelFor(CURVE, 600, 10));
    }

    @Test
    void theCapHoldsTheLevelWhileXpKeepsCounting() {
        assertEquals(3, XpTable.levelFor(CURVE, 1_000_000, 3));
        assertEquals(1, XpTable.levelFor(CURVE, 1_000_000, 1));
        assertEquals(10, XpTable.levelFor(CURVE, 1_000_000, 10));
    }

    @Test
    void hugeXpSaturatesInsteadOfOverflowing() {
        Curve huge = new Curve(Optional.empty(), List.of(new Point(1, 1e300)));
        assertEquals(Long.MAX_VALUE, XpTable.toReach(huge, 5));
        assertEquals(1, XpTable.levelFor(huge, Long.MAX_VALUE - 1, 10));
        assertEquals(Long.MAX_VALUE, XpTable.plus(Long.MAX_VALUE - 1, 5));
    }
}
