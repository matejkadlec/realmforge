package io.github.matejkadlec.realmforge.client.input;

/**
 * A lone press of one key mapping (RLM-522), without world access so JUnit covers it: the mapping goes down and comes
 * up again with no other key going down in between, while the game window keeps its focus. A key used as a modifier
 * (Left Alt in Alt+T, Alt+K or Alt+Tab) is therefore no press of its own. Vanilla's F3 counts its debug overlay the
 * same way ({@code KeyboardHandler#usedDebugKeyAsModifier}).
 *
 * <p>It follows the mapping's own {@code isDown()} after every key and mouse event, not a key code, so whatever the
 * mapping is bound to counts: a key, a mouse button, a modifier combination or Better Key Binds' secondary key.
 *
 * <ul>
 *   <li>Keys held before the mapping went down don't count: tapping Alt while walking on W is a lone press.</li>
 *   <li>Key repeats and mouse buttons are no other key.</li>
 *   <li>A lost focus spoils it: GLFW releases every held key after telling the game the window lost its focus, so
 *       Alt+Tab, whose Tab the system takes, ends in a release while the window is no longer focused.</li>
 *   <li>A release without an event (a screen opening releases every mapping) is no press: {@link #tick} sees it.</li>
 * </ul>
 */
public final class LonePress {
    /** Whether the mapping was down after the last event or tick. */
    private boolean down;
    /** Whether the current press is still lone. */
    private boolean armed;

    /**
     * After a key or mouse event, once vanilla updated the mapping: {@code nowDown} is its {@code isDown()},
     * {@code keyPressed} whether the event was a key going down (not a repeat, not a mouse button), {@code counts}
     * whether a release may count now (the window focused, no screen open). True once, on a lone press's release.
     */
    public boolean input(boolean nowDown, boolean keyPressed, boolean counts) {
        boolean lone = false;
        if (nowDown && !down) {
            armed = true;
        } else if (!nowDown && down) {
            lone = armed && counts;
            armed = false;
        } else if (nowDown && keyPressed) {
            armed = false;
        }
        down = nowDown;
        return lone;
    }

    /**
     * Each client tick: a change no event announced (a screen opened, or closed over the held key) counts for nothing.
     */
    public void tick(boolean nowDown) {
        if (nowDown != down) {
            armed = false;
            down = nowDown;
        }
    }

    /** Forgets everything (a logout). */
    public void reset() {
        down = false;
        armed = false;
    }
}
