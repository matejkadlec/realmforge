package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class CastResultTest {
    @Test
    void aRefusedCastNamesItsReasonAndHasNoTarget() {
        CastResult refused = CastResult.refused(CastFailure.OUT_OF_RANGE);
        assertFalse(refused.success());
        assertEquals(CastFailure.OUT_OF_RANGE, refused.failureOrNull());
        assertEquals(Optional.empty(), refused.target());
    }

    @Test
    void aCastEitherFailsOrHasATarget() {
        assertThrows(IllegalArgumentException.class, () -> new CastResult(Optional.empty(), Optional.empty()),
                "neither a reason nor a target");
    }

    /** C3b turns each reason into a text keyed by its name, so the names stay stable. */
    @Test
    void reasonsHaveStableNames() {
        assertEquals("not_enough_resource", CastFailure.NOT_ENOUGH_RESOURCE.getSerializedName());
        assertEquals("on_global_cooldown", CastFailure.ON_GLOBAL_COOLDOWN.getSerializedName());
        assertEquals("no_line_of_sight", CastFailure.NO_LINE_OF_SIGHT.getSerializedName());
    }
}
