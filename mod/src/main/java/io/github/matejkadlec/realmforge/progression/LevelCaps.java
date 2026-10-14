package io.github.matejkadlec.realmforge.progression;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The level cap rule (architecture 10.2, decision D2): a player's cap is the highest {@code completedLevelCap} among
 * the chapters they completed, never below the first chapter's {@code levelCap}, the cap of a new player. The pipeline
 * computes the same ({@code level_cap} in {@code tools/realmforge_tools/schema/chapter.py}), and a GameTest compares
 * the two.
 */
public final class LevelCaps {
    /**
     * The highest level anything may reach: a guard against broken numbers, not a balance number (like the raised
     * attribute caps, architecture 8.4). It is the cap when no chapter exists at all, such as after a data pack
     * removed them.
     */
    public static final int MAX_LEVEL = 1000;

    private static final Comparator<Chapter> BY_ORDER = Comparator.comparingInt(Chapter::order);

    private LevelCaps() {
    }

    /** The level cap of a player who completed the chapters {@code completed} accepts, out of {@code chapters}. */
    public static int cap(Collection<Chapter> chapters, Predicate<Chapter> completed) {
        Optional<Chapter> first = chapters.stream().min(BY_ORDER);
        if (first.isEmpty()) {
            return MAX_LEVEL;
        }
        int cap = first.get().levelCap();
        for (Chapter chapter : chapters) {
            if (completed.test(chapter)) {
                cap = Math.max(cap, chapter.completedLevelCap());
            }
        }
        return Math.min(cap, MAX_LEVEL);
    }

    /** The first chapter, by order, that {@code completed} does not accept: the one that raises the cap next. */
    public static Optional<Chapter> next(Collection<Chapter> chapters, Predicate<Chapter> completed) {
        return chapters.stream().filter(completed.negate()).min(BY_ORDER);
    }
}
