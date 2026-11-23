package io.github.matejkadlec.realmforge.client.spell;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * The casters whose arms change on the next client tick because a cast ended (RLM-454, RLM-662, architecture 11.6):
 * they go down, or, after a cast time that formed a projectile, play the release. A cast that replaces another, or a
 * spell pressed in a cast's last quarter second, which waits for it to finish (RLM-517), starts in the same server
 * tick as the end, so {@code CastEndedPayload} arrives before the new cast's {@code CastStartedPayload}: lowering the
 * hands at the end, or playing the release then, would dip them and raise them again from the animation's first
 * frame. An end therefore only marks its caster, and the next client tick lowers the hands of, or plays the release
 * for, each marked caster that has no cast by then; one that cast again keeps its running cast animation as it is.
 * Without world access, so JUnit covers it. Main thread only.
 */
public final class PendingLowerings {
    /** A marked caster's arms at the next tick: {@code release} plays the release, else they go down. */
    public record Due(int casterId, boolean release) {
    }

    /** Caster to whether its last end releases, in the order the casters were first marked. */
    private final Map<Integer, Boolean> casters = new LinkedHashMap<>();

    /** {@code casterId}'s cast ended: its hands go down at the next {@link #due}, unless it casts again by then. */
    public void ended(int casterId) {
        casters.put(casterId, false);
    }

    /**
     * {@code casterId}'s cast time completed and formed a projectile: its arms play the release at the next
     * {@link #due}, unless it casts again by then, when the new cast keeps them up.
     */
    public void released(int casterId) {
        casters.put(casterId, true);
    }

    /**
     * The casters marked since the last call that have no cast now ({@code casting} says which do), in the order they
     * were first marked, each with what its latest end asked for; every mark clears.
     */
    public List<Due> due(IntPredicate casting) {
        List<Due> due = casters.entrySet().stream().filter(entry -> !casting.test(entry.getKey()))
                .map(entry -> new Due(entry.getKey(), entry.getValue())).toList();
        casters.clear();
        return due;
    }

    public void clear() {
        casters.clear();
    }
}
