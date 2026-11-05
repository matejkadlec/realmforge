package io.github.matejkadlec.realmforge.client.input;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LonePressTest {
    private static final boolean DOWN = true;
    private static final boolean UP = false;
    private static final boolean KEY_PRESSED = true;
    private static final boolean NO_KEY_PRESSED = false;
    private static final boolean COUNTS = true;

    /** The mapping goes down on its own key's press and comes up on its release. */
    private static boolean tap(LonePress alt) {
        alt.input(DOWN, KEY_PRESSED, COUNTS);
        return alt.input(UP, NO_KEY_PRESSED, COUNTS);
    }

    @Test
    void aLonePressCountsOnItsRelease() {
        LonePress alt = new LonePress();
        assertFalse(alt.input(DOWN, KEY_PRESSED, COUNTS), "nothing counts on the press");
        assertTrue(alt.input(UP, NO_KEY_PRESSED, COUNTS));
    }

    @Test
    void itCountsOncePerPress() {
        LonePress alt = new LonePress();
        assertTrue(tap(alt));
        assertFalse(alt.input(UP, NO_KEY_PRESSED, COUNTS), "another event while it stays up");
        assertTrue(tap(alt), "the next press counts again");
    }

    @Test
    void aKeyPressedInBetweenMakesItAModifier() {
        LonePress alt = new LonePress();
        alt.input(DOWN, KEY_PRESSED, COUNTS);
        alt.input(DOWN, KEY_PRESSED, COUNTS);
        assertFalse(alt.input(UP, NO_KEY_PRESSED, COUNTS), "Alt+T or Alt+K");
        assertTrue(tap(alt), "the next lone press still counts");
    }

    @Test
    void repeatsAndMouseButtonsInBetweenAreNoOtherKey() {
        LonePress alt = new LonePress();
        alt.input(DOWN, KEY_PRESSED, COUNTS);
        alt.input(DOWN, NO_KEY_PRESSED, COUNTS);
        alt.input(DOWN, NO_KEY_PRESSED, COUNTS);
        assertTrue(alt.input(UP, NO_KEY_PRESSED, COUNTS));
    }

    @Test
    void aKeyPressedBeforeItDoesNotCount() {
        LonePress alt = new LonePress();
        alt.input(UP, KEY_PRESSED, COUNTS);
        assertTrue(tap(alt), "tapping Alt while W is held");
    }

    @Test
    void aMouseButtonBindingCounts() {
        LonePress button = new LonePress();
        button.input(DOWN, NO_KEY_PRESSED, COUNTS);
        assertTrue(button.input(UP, NO_KEY_PRESSED, COUNTS), "a side button: mouse events, no key pressed");
    }

    @Test
    void aReleaseWithoutFocusOrUnderAScreenDoesNotCount() {
        LonePress alt = new LonePress();
        alt.input(DOWN, KEY_PRESSED, COUNTS);
        assertFalse(alt.input(UP, NO_KEY_PRESSED, false), "Alt+Tab: GLFW releases Alt after the focus is gone");
        assertTrue(tap(alt), "back in the game, a lone press counts again");
    }

    @Test
    void aReleaseNoEventAnnouncedIsNoPress() {
        LonePress alt = new LonePress();
        alt.input(DOWN, KEY_PRESSED, COUNTS);
        alt.tick(UP);
        assertFalse(alt.input(UP, KEY_PRESSED, COUNTS), "a screen released Alt; the next key event is no release");
        alt.tick(DOWN);
        assertFalse(alt.input(UP, NO_KEY_PRESSED, COUNTS), "held through a screen closing: not a fresh press");
        assertTrue(tap(alt));
    }

    @Test
    void aTickWithoutChangeKeepsAPress() {
        LonePress alt = new LonePress();
        alt.input(DOWN, KEY_PRESSED, COUNTS);
        alt.tick(DOWN);
        assertTrue(alt.input(UP, NO_KEY_PRESSED, COUNTS));
    }

    @Test
    void resetForgetsAPendingPress() {
        LonePress alt = new LonePress();
        alt.input(DOWN, KEY_PRESSED, COUNTS);
        alt.reset();
        assertFalse(alt.input(UP, NO_KEY_PRESSED, COUNTS));
    }
}
