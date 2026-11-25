package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.spell.Spell;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;

/**
 * The spell point rules (C5, RLM-333, RLM-631, architecture 10.1), pure so JUnit can walk them. {@link SpellTrees}
 * applies them to players.
 *
 * <ul>
 *   <li><b>Earned:</b> exactly 1 point per level from level 2 on, so 17 at level 18 (Matěj, 2026-11-03 and 2026-11-04).
 *   They are never saved.</li>
 *   <li><b>Spent:</b> every rank the player knows in their tree, less the first spell's first rank, which is free
 *   (decision D4; Matěj, 2026-11-04). The ranks in the spellbook are the only record, so points and ranks can never
 *   disagree.</li>
 *   <li><b>Rows open by points spent in the tree,</b> WotLK style (Matěj, 2026-11-04, RLM-631): row N once
 *   {@link #POINTS_PER_ROW} x (N - 1) are spent, so row 2 after 5, row 3 after 10 and row 4 after 15. A saved point
 *   opens nothing; spending is what opens the next row.</li>
 *   <li><b>A spell's next rank</b> needs its row open and every spell its arrows name at any rank.</li>
 * </ul>
 */
public final class SpellPoints {
    /** A row opens once this many points per row above it are spent in the tree (RLM-631). */
    public static final int POINTS_PER_ROW = 5;

    /** What a request to spend a point led to. Only {@link #SPENT} changes anything. */
    public enum Result {
        SPENT,
        /** The player has not picked a specialization, or has no character. */
        NO_SPECIALIZATION,
        /** The spell is not in the tree of the player's specialization. */
        NOT_IN_TREE,
        /** The spell already has its highest rank. */
        MAX_RANK,
        /** Too few points are spent in the tree to open the spell's row. */
        ROW_CLOSED,
        /** A spell one of its arrows names is not known yet. */
        NEEDS_SPELL,
        NO_POINTS
    }

    /**
     * A tree spell as the rules see it.
     *
     * @param spell    the spell
     * @param row      its row, from 1
     * @param requires the spells its arrows need
     */
    public record Place(ResourceKey<Spell> spell, int row, List<ResourceKey<Spell>> requires) {
        public Place {
            requires = List.copyOf(requires);
        }
    }

    private SpellPoints() {
    }

    /** The points a character of {@code level} has earned. */
    public static int earned(int level) {
        return Math.max(0, level - 1);
    }

    /** The points that must be spent in a tree before {@code row} (from 1) opens: 0, 5, 10, 15, ... */
    public static int rowPoints(int row) {
        return POINTS_PER_ROW * Math.max(0, row - 1);
    }

    /** How many rows {@code spent} points open: 1 with none, 2 from 5, 3 from 10. */
    public static int openRows(int spent) {
        return Math.max(0, spent) / POINTS_PER_ROW + 1;
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

    /** The points spent in the rows above {@code row}, the first spell's free rank aside. */
    public static int spentAbove(Map<ResourceKey<Spell>, Integer> known, Collection<Place> tree,
            Optional<ResourceKey<Spell>> first, int row) {
        return spent(known, tree.stream().filter(place -> place.row() < row).map(Place::spell).toList(),
                first.filter(spell -> tree.stream().anyMatch(place -> place.spell().equals(spell)
                        && place.row() < row)));
    }

    /** Whether the player knows every spell {@code place}'s arrows name, at any rank. */
    public static boolean requirementsMet(Map<ResourceKey<Spell>, Integer> known, Place place) {
        return place.requires().stream().allMatch(known::containsKey);
    }

    /**
     * The rules for a spell of the player's tree, without changing anything: its highest rank, its row, its arrows and
     * the points, checked in that order, so a refusal names what a point could not fix first.
     *
     * @param spent          the points spent in the tree
     * @param unspent        the points the character has left
     * @param rank           the rank they know the spell at, 0 when they do not
     * @param maxRank        the spell's highest rank
     * @param row            the spell's row, from 1
     * @param requirementMet whether they know every spell its arrows name, true when it has none
     */
    public static Result check(int spent, int unspent, int rank, int maxRank, int row, boolean requirementMet) {
        if (rank >= maxRank) {
            return Result.MAX_RANK;
        }
        if (spent < rowPoints(row)) {
            return Result.ROW_CLOSED;
        }
        if (!requirementMet) {
            return Result.NEEDS_SPELL;
        }
        return unspent > 0 ? Result.SPENT : Result.NO_POINTS;
    }

    /**
     * Whether the ranks the player knows could have been learned by the rules: every known spell's row was open by the
     * points spent in the rows above it, and its arrows' spells are known. Learning only adds ranks, so a legal history
     * always passes; a tree whose layout changed under a character can fail it.
     */
    public static boolean followsTheRules(Map<ResourceKey<Spell>, Integer> known, Collection<Place> tree,
            Optional<ResourceKey<Spell>> first) {
        return tree.stream().filter(place -> known.containsKey(place.spell())).allMatch(place ->
                spentAbove(known, tree, first, place.row()) >= rowPoints(place.row()) && requirementsMet(known, place));
    }
}
