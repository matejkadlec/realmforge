package io.github.matejkadlec.realmforge.dungeon.plan;

import java.util.Locale;

/** The place a room takes in a planned layout (architecture 15.3). */
public enum Slot {
    /** The first room, where players arrive. */
    ENTRANCE,
    /** A room of the main path, between the entrance and the ending. */
    PATH,
    /** The room that ends the main path: the boss's lair or a treasury. */
    ENDING,
    /** A room of a side branch before its dead end. */
    BRANCH,
    /** The dead end of a side branch. */
    BRANCH_END,
    /** A dead end that closes a connector no other room takes. */
    CAP;

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
