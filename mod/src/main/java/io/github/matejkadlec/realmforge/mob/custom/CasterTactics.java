package io.github.matejkadlec.realmforge.mob.custom;

/**
 * Whether a caster holds its ground or goes after its target (architecture 14.2), as a Skeleton does: it casts from
 * where it stands while its target is in reach and in sight, and closes in when it is not. Pure: the distance to the
 * target, its spells' reach, its line of sight and whether it is already closing in go in; the move comes out.
 * {@link CasterFightGoal} applies it, and steps between its casts with its kiting.
 */
public final class CasterTactics {
    /**
     * A caster that closes in goes on until its target is this share of its reach away, so a target that steps back a
     * little stays in range.
     */
    static final double CLOSE_IN = 0.8;

    private CasterTactics() {
    }

    /** Where a caster moves. */
    public enum Move {
        /** Towards its target, which is out of reach or out of sight. */
        CLOSE_IN,
        /** Nowhere: its target is in reach and in sight, so it stands and casts. */
        HOLD
    }

    /**
     * Where a caster {@code distance} blocks from its target moves, when its spells reach {@code reach} blocks, it
     * {@code seesTarget} or not, and it is {@code closingIn} already or not.
     */
    public static Move move(double distance, double reach, boolean seesTarget, boolean closingIn) {
        if (!seesTarget) {
            return Move.CLOSE_IN;
        }
        return distance > (closingIn ? reach * CLOSE_IN : reach) ? Move.CLOSE_IN : Move.HOLD;
    }
}
