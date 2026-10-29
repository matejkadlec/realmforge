package io.github.matejkadlec.realmforge.client.spell;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * The casters whose hands go down on the next client tick (RLM-454, architecture 11.6). A cast that replaces another
 * ends the old one before it starts, in the same server tick, so {@code CastEndedPayload} arrives before the new cast's
 * {@code CastStartedPayload}: lowering the hands at the end would dip them and raise them again from the animation's
 * first frame. An end therefore only marks its caster, and the next client tick lowers the hands of each marked caster
 * that has no cast by then. Without world access, so JUnit covers it. Main thread only.
 */
public final class PendingLowerings {
    private final Set<Integer> casters = new LinkedHashSet<>();

    /** {@code casterId}'s cast ended: its hands go down at the next {@link #due}, unless it casts again by then. */
    public void ended(int casterId) {
        casters.add(casterId);
    }

    /**
     * The casters marked since the last call that have no cast now ({@code casting} says which do), in the order their
     * casts ended; every mark clears.
     */
    public List<Integer> due(IntPredicate casting) {
        List<Integer> due = casters.stream().filter(id -> !casting.test(id)).toList();
        casters.clear();
        return due;
    }

    public void clear() {
        casters.clear();
    }
}
