package io.github.matejkadlec.realmforge.client.screen;

/**
 * The horizontal centre lock of the spell bar's drag mode (RLM-404, {@link SpellBarPositionScreen}), without world
 * access so JUnit covers it: a dragged group that comes within {@value #DISTANCE} px of the screen's middle snaps to it
 * and holds there while the mouse pulls it sideways, until the pull has lasted {@value #RELEASE_MILLIS} ms; then it
 * follows the mouse again, and snaps once more when it comes back. Vertical moves are free throughout.
 */
final class CentreSnap {
    /** How near the middle a group snaps to it, in GUI pixels. */
    static final int DISTANCE = 6;
    /** How long a sideways pull holds before the snap lets go. */
    static final long RELEASE_MILLIS = 1000;
    private static final long NO_PULL = Long.MIN_VALUE;

    private boolean snapped;
    /** When the current sideways pull on the snapped group began. */
    private long pullSince = NO_PULL;

    /**
     * The left edge for a group the mouse would put at {@code wanted}, the middle being {@code middle}, at
     * {@code nowMillis}.
     */
    int x(int wanted, int middle, long nowMillis) {
        boolean near = Math.abs(wanted - middle) <= DISTANCE;
        if (near) {
            snapped = true;
            pullSince = NO_PULL;
            return middle;
        }
        if (!snapped) {
            return wanted;
        }
        if (pullSince == NO_PULL) {
            pullSince = nowMillis;
        }
        if (nowMillis - pullSince >= RELEASE_MILLIS) {
            snapped = false;
            pullSince = NO_PULL;
            return wanted;
        }
        return middle;
    }

    boolean snapped() {
        return snapped;
    }

    /** A new drag: nothing held. */
    void reset() {
        snapped = false;
        pullSince = NO_PULL;
    }
}
