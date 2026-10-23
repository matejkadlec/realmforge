package io.github.matejkadlec.realmforge.mob.custom;

/**
 * How a caster moves around its target (architecture 14.2, research BM-22: a caster that does more than run at you).
 * Pure: the distance to the target, the distance the caster keeps, its spells' reach and its line of sight go in;
 * where it moves comes out. {@link CasterFightGoal} applies it.
 */
public final class CasterTactics {
    /**
     * A caster closes in until its target is this share of its reach away, so a target that steps back a little
     * stays in range.
     */
    static final double CLOSE_IN = 0.8;

    private CasterTactics() {
    }

    /** Where a caster moves. */
    public enum Move {
        /** Away from its target, which came nearer than the distance it keeps. */
        BACK_AWAY,
        /** Towards its target, which is out of reach or out of sight. */
        CLOSE_IN,
        /** Nowhere: its target is in reach and in sight, and not too near. */
        HOLD
    }

    /**
     * Where a caster {@code distance} blocks from its target moves, when it keeps {@code keepDistance} blocks (0
     * never backs away), its spells reach {@code reach} blocks and it {@code seesTarget} or not. Backing away comes
     * first: a caster that cannot see a target right next to it still steps back.
     */
    public static Move move(double distance, double keepDistance, double reach, boolean seesTarget) {
        if (keepDistance > 0 && distance < keepDistance) {
            return Move.BACK_AWAY;
        }
        if (!seesTarget || distance > reach * CLOSE_IN) {
            return Move.CLOSE_IN;
        }
        return Move.HOLD;
    }
}
