package io.github.matejkadlec.realmforge.client.input;

/**
 * A lone press of one key (RLM-522), without world access so JUnit covers it: the key goes down and comes up again with
 * no other key going down in between, while the game window keeps its focus. A key used as a modifier (Left Alt in
 * Alt+T, Alt+K or Alt+Tab) is therefore no press of its own. Vanilla's F3 counts its debug overlay the same way
 * ({@code KeyboardHandler#usedDebugKeyAsModifier}).
 *
 * <ul>
 *   <li>Keys held before the key went down don't count: tapping Alt while walking on W is a lone press.</li>
 *   <li>Key repeats are no new presses, so callers pass only presses ({@code GLFW_PRESS}).</li>
 *   <li>A lost focus spoils it: GLFW releases every held key after telling the game the window lost its focus, so
 *       Alt+Tab, whose Tab the system takes, ends in a release while the window is no longer focused.</li>
 * </ul>
 */
public final class LonePress {
    private boolean armed;

    /** The key went down where it counts (in game, no screen open). */
    public void pressed() {
        armed = true;
    }

    /** Another key went down: the key is a modifier this time. */
    public void otherPressed() {
        armed = false;
    }

    /**
     * The key came up. True once for a lone press: armed, nothing pressed in between, and the window still focused and
     * free of screens ({@code counts}).
     */
    public boolean released(boolean counts) {
        boolean lone = armed && counts;
        armed = false;
        return lone;
    }

    /** Forgets a pending press (a logout). */
    public void reset() {
        armed = false;
    }
}
