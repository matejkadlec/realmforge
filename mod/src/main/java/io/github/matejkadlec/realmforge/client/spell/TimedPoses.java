package io.github.matejkadlec.realmforge.client.spell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The casters whose arms play an animation that ends by itself (RLM-641, architecture 11.6): an instant spell's push
 * or the release as a formed projectile leaves, each until a game time. No payload ends one, so the client lowers the
 * arms when its time is over ({@link #due}), unless a cast or another such animation took the arms meanwhile. Without
 * world access, so JUnit covers it. Main thread only.
 */
public final class TimedPoses {
    private final Map<Integer, Long> ends = new HashMap<>();

    /** {@code casterId}'s arms play an animation until game time {@code endGameTime}, replacing one that played. */
    public void start(int casterId, long endGameTime) {
        ends.put(casterId, endGameTime);
    }

    /** {@code casterId}'s arms were taken by a cast: nothing is left to lower for the animation that played. */
    public void stop(int casterId) {
        ends.remove(casterId);
    }

    /** Whether {@code casterId}'s arms still play such an animation: its time is not marked as over yet. */
    public boolean playing(int casterId) {
        return ends.containsKey(casterId);
    }

    /** The casters whose animation is over at game time {@code gameTime}, each returned once and then forgotten. */
    public List<Integer> due(long gameTime) {
        List<Integer> due = new ArrayList<>();
        for (Iterator<Map.Entry<Integer, Long>> each = ends.entrySet().iterator(); each.hasNext();) {
            Map.Entry<Integer, Long> pose = each.next();
            if (gameTime >= pose.getValue()) {
                due.add(pose.getKey());
                each.remove();
            }
        }
        return due;
    }

    public void clear() {
        ends.clear();
    }
}
