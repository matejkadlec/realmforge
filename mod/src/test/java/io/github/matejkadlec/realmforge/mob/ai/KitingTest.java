package io.github.matejkadlec.realmforge.mob.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class KitingTest {
    private static final Vec3 TARGET = new Vec3(0, 64, 0);
    private static final double RANGE = 8;
    private static final double STEP = 2;
    private static final double LIMIT = RANGE - Kiting.RANGE_MARGIN;
    private static final double CLOSE = 1e-6;

    private static double distance(Vec3 place) {
        return new Vec3(place.x - TARGET.x, 0, place.z - TARGET.z).horizontalDistance();
    }

    @Test
    void aMobWellInRangeStepsStraightAwayFirst() {
        Vec3 self = new Vec3(3, 64, 0);
        List<Vec3> steps = Kiting.steps(self, TARGET, RANGE, STEP, true);
        assertEquals(new Vec3(5, 64, 0).distanceTo(steps.getFirst()), 0, CLOSE);
        // Then away at a turn to either side, the clockwise one (seen from above: east turns toward south) first.
        assertTrue(steps.get(1).z > 0 && distance(steps.get(1)) > 3, "clockwise away: " + steps.get(1));
        assertTrue(steps.get(2).z < 0 && distance(steps.get(2)) > 3, "counter-clockwise away: " + steps.get(2));
    }

    @Test
    void theOtherSideComesFirstCounterClockwise() {
        List<Vec3> steps = Kiting.steps(new Vec3(3, 64, 0), TARGET, RANGE, STEP, false);
        assertTrue(steps.get(1).z < 0, "counter-clockwise away first: " + steps.get(1));
    }

    @Test
    void everyStepIsAStepLongAndKeepsTheTargetInRange() {
        for (Vec3 self : List.of(new Vec3(3, 64, 0), new Vec3(5, 64, 4), new Vec3(-7, 64, 1), new Vec3(0, 64, 7))) {
            for (Vec3 step : Kiting.steps(self, TARGET, RANGE, STEP, true)) {
                assertTrue(distance(step) <= LIMIT + CLOSE, step + " leaves the range from " + self);
                assertTrue(Math.abs(step.distanceTo(self) - STEP) < 0.3, step + " is no step from " + self);
                assertEquals(self.y, step.y, CLOSE, "a step keeps the mob's height");
            }
        }
    }

    @Test
    void atTheEdgeOfItsRangeAMobCirclesItsTarget() {
        Vec3 self = new Vec3(7, 64, 0);
        List<Vec3> steps = Kiting.steps(self, TARGET, RANGE, STEP, true);
        // Away would leave the range, so the circle comes first: the same distance, clockwise.
        assertEquals(7, distance(steps.getFirst()), CLOSE);
        assertTrue(steps.getFirst().z > 0, "clockwise round: " + steps.getFirst());
        assertEquals(7, distance(steps.get(1)), CLOSE);
        assertTrue(steps.get(1).z < 0, "counter-clockwise round: " + steps.get(1));
        // Then a little toward the target.
        assertTrue(distance(steps.get(2)) < 7, "toward: " + steps.get(2));
    }

    @Test
    void beyondItsRangeAMobCirclesInsideIt() {
        Vec3 self = new Vec3(7.9, 64, 0);
        List<Vec3> steps = Kiting.steps(self, TARGET, RANGE, STEP, true);
        assertEquals(LIMIT, distance(steps.getFirst()), CLOSE);
    }

    @Test
    void aMobOnItsTargetsSpotStillSteps() {
        List<Vec3> steps = Kiting.steps(TARGET, TARGET, RANGE, STEP, true);
        assertEquals(STEP, steps.getFirst().distanceTo(TARGET), CLOSE);
    }

    @Test
    void aRangeShorterThanAStepLeavesOnlyTheCircleAndInward() {
        List<Vec3> steps = Kiting.steps(new Vec3(1, 64, 0), TARGET, 2, STEP, true);
        assertTrue(steps.stream().allMatch(step -> distance(step) <= 2 - Kiting.RANGE_MARGIN + CLOSE), "" + steps);
    }
}
