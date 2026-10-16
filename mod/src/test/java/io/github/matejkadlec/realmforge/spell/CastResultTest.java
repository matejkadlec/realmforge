package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class CastResultTest {
    @Test
    void aRefusedCastNamesItsReasonAndHasNoTarget() {
        CastResult refused = CastResult.refused(CastFailure.OUT_OF_RANGE);
        assertFalse(refused.success());
        assertEquals(CastFailure.OUT_OF_RANGE, refused.failureOrNull());
        assertEquals(Optional.empty(), refused.target());
        assertFalse(refused.begun());
    }

    @Test
    void aCastEitherFailsOrHasATarget() {
        assertThrows(IllegalArgumentException.class,
                () -> new CastResult(Optional.empty(), Optional.empty(), false), "neither a reason nor a target");
        assertThrows(IllegalArgumentException.class,
                () -> new CastResult(Optional.of(CastFailure.ALREADY_CASTING), Optional.empty(), true),
                "a refused cast that began");
    }

    /** C3b turns each reason into a text keyed by its name, so the names stay stable. */
    @Test
    void reasonsHaveStableNames() {
        assertEquals("not_enough_resource", CastFailure.NOT_ENOUGH_RESOURCE.getSerializedName());
        assertEquals("on_global_cooldown", CastFailure.ON_GLOBAL_COOLDOWN.getSerializedName());
        assertEquals("no_line_of_sight", CastFailure.NO_LINE_OF_SIGHT.getSerializedName());
        assertEquals("already_casting", CastFailure.ALREADY_CASTING.getSerializedName());
    }

    @Test
    void aFailedCheckBeforeTheEffectsEndsTheCastWithItsReason() {
        assertEquals(CastEnd.OUT_OF_RANGE, CastEnd.of(CastFailure.OUT_OF_RANGE));
        assertEquals(CastEnd.NO_LINE_OF_SIGHT, CastEnd.of(CastFailure.NO_LINE_OF_SIGHT));
        assertEquals(CastEnd.NOT_ENOUGH_RESOURCE, CastEnd.of(CastFailure.NOT_ENOUGH_RESOURCE));
        assertEquals(CastEnd.TARGET_LOST, CastEnd.of(CastFailure.NO_TARGET));
        assertEquals(CastEnd.INVALID_TARGET, CastEnd.of(CastFailure.INVALID_TARGET));
        assertFalse(CastEnd.OUT_OF_RANGE.interrupt());
        assertTrue(CastEnd.MOVED.interrupt());
        assertTrue(CastEnd.COMPLETED.completed());
        assertFalse(CastEnd.COMPLETED.interrupt());
    }
}
