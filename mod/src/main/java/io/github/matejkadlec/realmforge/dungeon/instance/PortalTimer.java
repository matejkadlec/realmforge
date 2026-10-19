package io.github.matejkadlec.realmforge.dungeon.instance;

import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.Optional;

/**
 * The timer of a run opened through a dungeon portal (W4d, architecture 15.5): the warnings before it runs out and the
 * time a completed run has left.
 */
public final class PortalTimer {
    /** A completed run closes this long after its goal is done, however much time it had left: 5 minutes. */
    public static final long COMPLETED_TICKS = 5L * 60L * 20L;

    private PortalTimer() {
    }

    /**
     * The warning to send when a run has {@code ticksLeft} ticks before it closes: 5 minutes, 1 minute and 10 seconds
     * before; none at any other time.
     */
    public static Optional<String> warning(long ticksLeft) {
        if (ticksLeft == 5L * 60L * 20L) {
            return Optional.of(GeneratedTexts.MESSAGE_PORTAL_CLOSES_IN_5_MINUTES);
        }
        if (ticksLeft == 60L * 20L) {
            return Optional.of(GeneratedTexts.MESSAGE_PORTAL_CLOSES_IN_1_MINUTE);
        }
        if (ticksLeft == 10L * 20L) {
            return Optional.of(GeneratedTexts.MESSAGE_PORTAL_CLOSES_IN_10_SECONDS);
        }
        return Optional.empty();
    }
}
