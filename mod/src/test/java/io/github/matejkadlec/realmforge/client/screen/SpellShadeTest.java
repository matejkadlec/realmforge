package io.github.matejkadlec.realmforge.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.character.SpellTreeView.State;
import org.junit.jupiter.api.Test;

/** The Spell Tree tab's three shades and its pulse (RLM-333). */
class SpellShadeTest {
    @Test
    void aKnownSpellIsAtFullLightWhateverItsState() {
        assertEquals(SpellShade.KNOWN, SpellShade.of(1, State.LEARNABLE));
        assertEquals(SpellShade.KNOWN, SpellShade.of(3, State.MAX_RANK));
        assertEquals(SpellShade.KNOWN, SpellShade.of(2, State.NO_POINTS));
        assertEquals(0, SpellShade.KNOWN.overlayAlpha(500));
    }

    @Test
    void onlyASpellAClickWouldTeachPulses() {
        assertEquals(SpellShade.LEARNABLE, SpellShade.of(0, State.LEARNABLE));
        for (State state : new State[] {State.NO_POINTS, State.NEEDS_LEVEL, State.NEEDS_SPELL, State.NEEDS_PICK,
                State.FIRST_FREE, State.OTHER_TREE}) {
            assertEquals(SpellShade.LOCKED, SpellShade.of(0, state), state.name());
        }
    }

    @Test
    void lockedIsDarkerThanTheDarkestPulse() {
        assertTrue(SpellShade.LOCKED.overlayAlpha(0) > SpellShade.LEARNABLE.overlayAlpha(0));
        assertEquals(SpellShade.LOCKED.overlayAlpha(0), SpellShade.LOCKED.overlayAlpha(777), "it does not pulse");
    }

    /** One second dark to light, one second back, in a loop. */
    @Test
    void thePulseTakesASecondEachWay() {
        int dark = SpellShade.LEARNABLE.overlayAlpha(0);
        int light = SpellShade.LEARNABLE.overlayAlpha(SpellShade.PULSE_MILLIS);
        assertTrue(light < dark, "lighter after a second");
        assertEquals(dark, SpellShade.LEARNABLE.overlayAlpha(2L * SpellShade.PULSE_MILLIS), "and dark again after two");
        int between = SpellShade.LEARNABLE.overlayAlpha(SpellShade.PULSE_MILLIS / 2);
        assertTrue(between < dark && between > light, "it fades between them");
        assertEquals(between, SpellShade.LEARNABLE.overlayAlpha(3L * SpellShade.PULSE_MILLIS / 2), "the same way back");
    }
}
