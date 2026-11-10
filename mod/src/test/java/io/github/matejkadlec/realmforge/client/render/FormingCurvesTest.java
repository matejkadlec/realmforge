package io.github.matejkadlec.realmforge.client.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Which cube of a forming model shows at which moment of the cast (RLM-640). */
class FormingCurvesTest {
    private static final float CLOSE = 1e-5F;

    @Test
    void theCubesAppearFromTheHeartOutward() {
        // Distances from the heart; equal ones keep their own order.
        assertArrayEquals(new int[] {2, 0, 3, 1}, FormingCurves.ranks(new double[] {1.0, 4.0, 0.5, 1.0}));
        assertArrayEquals(new int[] {0}, FormingCurves.ranks(new double[] {7.0}));
        assertArrayEquals(new int[0], FormingCurves.ranks(new double[0]));
    }

    @Test
    void nothingShowsAsTheCastBeginsAndTheModelIsWholeAsItEnds() {
        int count = 26;
        for (int rank = 0; rank < count; rank++) {
            assertEquals(0, FormingCurves.cube(rank, count, 0), CLOSE, "cube " + rank + " at the start");
            assertEquals(1, FormingCurves.cube(rank, count, 1), CLOSE, "cube " + rank + " at the end");
        }
    }

    @Test
    void aCubeGrowsOverItsRampAndTheOnesNearerTheHeartAreAhead() {
        int count = 11;
        // The middle cube's turn starts at half of what is left beside one ramp.
        float start = 5 / 10F * (1 - FormingCurves.RAMP);
        assertEquals(0, FormingCurves.cube(5, count, start), CLOSE, "nothing before its turn");
        assertEquals(0.5F, FormingCurves.cube(5, count, start + FormingCurves.RAMP / 2), CLOSE, "half way up its ramp");
        assertEquals(1, FormingCurves.cube(5, count, start + FormingCurves.RAMP), CLOSE, "whole after its ramp");
        for (float progress = 0; progress <= 1; progress += 0.05F) {
            float before = 2;
            for (int rank = 0; rank < count; rank++) {
                float size = FormingCurves.cube(rank, count, progress);
                assertTrue(size >= 0 && size <= 1, "a share of its size");
                assertTrue(size <= before + CLOSE, "a cube farther from the heart is never ahead at " + progress);
                before = size;
            }
        }
    }

    @Test
    void aCubeNeverShrinksWhileTheCastRuns() {
        for (int rank = 0; rank < 8; rank++) {
            float before = 0;
            for (float progress = 0; progress <= 1.0001F; progress += 0.01F) {
                float size = FormingCurves.cube(rank, 8, progress);
                assertTrue(size >= before - CLOSE, "cube " + rank + " at " + progress);
                before = size;
            }
        }
    }

    @Test
    void aSingleCubeGrowsFromTheStart() {
        assertEquals(0.5F, FormingCurves.cube(0, 1, FormingCurves.RAMP / 2), CLOSE);
        assertEquals(1, FormingCurves.cube(0, 1, FormingCurves.RAMP), CLOSE);
        assertEquals(0, FormingCurves.edge(1, 0.7F));
    }

    @Test
    void theGrowingEdgeIsTheNewestCubeThatShows() {
        int count = 11;
        assertEquals(0, FormingCurves.edge(count, 0));
        assertEquals(10, FormingCurves.edge(count, 1));
        for (float progress = 0.02F; progress < 1; progress += 0.07F) {
            int edge = FormingCurves.edge(count, progress);
            assertTrue(FormingCurves.cube(edge, count, progress) > 0 || edge == 0, "the edge shows at " + progress);
            if (edge + 1 < count) {
                assertEquals(0, FormingCurves.cube(edge + 1, count, progress), CLOSE,
                        "the cube after the edge does not show yet at " + progress);
            }
        }
    }

    @Test
    void aModelThatScalesAsAWholeStartsSmallAndEndsAtItsSize() {
        assertEquals(FormingCurves.WHOLE_START, FormingCurves.whole(0), CLOSE);
        assertEquals(1, FormingCurves.whole(1), CLOSE);
        assertEquals(1, FormingCurves.whole(1.5F), CLOSE, "never beyond its size");
        assertTrue(FormingCurves.whole(0.5F) > FormingCurves.whole(0.25F));
    }

    @Test
    void anInterruptedCastsModelFadesAway() {
        assertEquals(1, FormingCurves.fade(0), CLOSE);
        assertEquals(0.5F, FormingCurves.fade(FormingCurves.FADE_TICKS / 2), CLOSE);
        assertEquals(0, FormingCurves.fade(FormingCurves.FADE_TICKS), CLOSE);
        assertEquals(0, FormingCurves.fade(FormingCurves.FADE_TICKS * 3), CLOSE, "gone, not negative");
    }

    @Test
    void aProjectileLeavesTheHandsOverItsFirstTicks() {
        assertEquals(0, FormingCurves.left(0), CLOSE, "at the hands as it appears");
        assertEquals(0.5F, FormingCurves.left(FormingCurves.LEAVE_TICKS / 2), CLOSE, "half way at half the time");
        assertEquals(1, FormingCurves.left(FormingCurves.LEAVE_TICKS), CLOSE, "on its own path");
        assertEquals(1, FormingCurves.left(40), CLOSE, "and it stays there");
        float before = 0;
        for (float ticks = 0; ticks <= FormingCurves.LEAVE_TICKS; ticks += 0.25F) {
            assertTrue(FormingCurves.left(ticks) >= before - CLOSE, "it never comes back toward the hands");
            before = FormingCurves.left(ticks);
        }
    }
}
