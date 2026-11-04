package io.github.matejkadlec.realmforge.client.input;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LonePressTest {
    @Test
    void aLonePressCountsOnItsRelease() {
        LonePress alt = new LonePress();
        alt.pressed();
        assertTrue(alt.released(true));
    }

    @Test
    void itCountsOncePerPress() {
        LonePress alt = new LonePress();
        alt.pressed();
        assertTrue(alt.released(true));
        assertFalse(alt.released(true), "a second release without a press");
        alt.pressed();
        assertTrue(alt.released(true), "the next press counts again");
    }

    @Test
    void aKeyPressedInBetweenMakesItAModifier() {
        LonePress alt = new LonePress();
        alt.pressed();
        alt.otherPressed();
        assertFalse(alt.released(true), "Alt+T or Alt+K");
        alt.pressed();
        assertTrue(alt.released(true), "the next lone press still counts");
    }

    @Test
    void aKeyPressedBeforeItDoesNotCount() {
        LonePress alt = new LonePress();
        alt.otherPressed();
        alt.pressed();
        assertTrue(alt.released(true), "tapping Alt while W is held");
    }

    @Test
    void aReleaseWithoutFocusOrUnderAScreenDoesNotCount() {
        LonePress alt = new LonePress();
        alt.pressed();
        assertFalse(alt.released(false), "Alt+Tab: GLFW releases Alt after the focus is gone");
        alt.pressed();
        assertTrue(alt.released(true), "back in the game, a lone press counts again");
    }

    @Test
    void aReleaseWithoutAPressDoesNotCount() {
        assertFalse(new LonePress().released(true), "Alt went down in a screen or before the game had focus");
    }

    @Test
    void resetForgetsAPendingPress() {
        LonePress alt = new LonePress();
        alt.pressed();
        alt.reset();
        assertFalse(alt.released(true));
    }
}
