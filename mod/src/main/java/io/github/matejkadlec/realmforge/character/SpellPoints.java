package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.spell.Spell;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;

/**
 * The spell point rules (C5, RLM-333, architecture 10.1), pure so JUnit can walk them. {@link SpellTrees} applies them
 * to players.
 *
 * <ul>
 *   <li><b>Earned:</b> exactly 1 point per level from level 2 on, so 17 at level 18 (Matěj, 2026-11-03 and 2026-11-04).
 *   They are never saved.</li>
 *   <li><b>Spent:</b> every rank the player knows in their tree, less the first spell's first rank, which is free
 *   (decision D4; Matěj, 2026-11-04). The ranks in the spellbook are the only record, so points and ranks can never
 *   disagree.</li>
 *   <li><b>A spell's next rank</b> needs its row's level and its arrow's spell at any rank; there is no rule on points
 *   spent per row (Matěj, 2026-11-04).</li>
 * </ul>
 */
public final class SpellPoints {
    /** What a request to spend a point led to. Only {@link #SPENT} changes anything. */
    public enum Result {
        SPENT,
        /** The player has not picked a specialization, or has no character. */
        NO_SPECIALIZATION,
        /** The spell is not in the tree of the player's specialization. */
        NOT_IN_TREE,
        /** The spell already has its highest rank. */
        MAX_RANK,
        /** The character's level has not opened the spell's row. */
        LEVEL_TOO_LOW,
        /** The spell its arrow names is not known yet. */
        NEEDS_SPELL,
        NO_POINTS
    }

    private SpellPoints() {
    }

    /** The points a character of {@code level} has earned. */
    public static int earned(int level) {
        return Math.max(0, level - 1);
    }

    /**
     * The points spent in a tree: the ranks known of its spells, less the free first rank of {@code first} when known.
     * Spells outside the tree (a racial spell, another tree's) cost nothing here.
     */
    public static int spent(Map<ResourceKey<Spell>, Integer> known, Collection<ResourceKey<Spell>> tree,
            Optional<ResourceKey<Spell>> first) {
        int ranks = tree.stream().mapToInt(spell -> known.getOrDefault(spell, 0)).sum();
        boolean free = first.filter(known::containsKey).isPresent();
        return Math.max(0, free ? ranks - 1 : ranks);
    }

    /**
     * The rules for a spell of the player's tree, without changing anything: its highest rank, its row's level, its
     * arrow and the points, checked in that order, so a refusal names what a point could not fix first.
     *
     * @param level        the character's level
     * @param unspent      the points the character has left
     * @param rank         the rank they know the spell at, 0 when they do not
     * @param maxRank      the spell's highest rank
     * @param rowLevel     the level that opens the spell's row
     * @param requirementMet whether they know the spell its arrow names, true when it has no arrow
     */
    public static Result check(int level, int unspent, int rank, int maxRank, int rowLevel, boolean requirementMet) {
        if (rank >= maxRank) {
            return Result.MAX_RANK;
        }
        if (level < rowLevel) {
            return Result.LEVEL_TOO_LOW;
        }
        if (!requirementMet) {
            return Result.NEEDS_SPELL;
        }
        return unspent > 0 ? Result.SPENT : Result.NO_POINTS;
    }
}
