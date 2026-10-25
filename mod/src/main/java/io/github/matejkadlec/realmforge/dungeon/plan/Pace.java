package io.github.matejkadlec.realmforge.dungeon.plan;

/**
 * The part a room plays in a layout's rhythm (RLM-364, architecture 15.3): what a player does there. A room's pace
 * follows from its role and its spawners ({@link DungeonRoom#pace}); a node of a {@link RoomGraph} drawn with a
 * {@link LayoutRules.Pacing} asks for one.
 */
public enum Pace {
    /** A hallway or stairs: a way from one room to the next. */
    CORRIDOR,
    /** A room with something to solve, dodge, see or take, but no spawner: a lock, a trap, a sight. */
    FEATURE,
    /** A room with a spawner: mobs keep coming until the player breaks it. */
    FIGHT,
    /** A treasure room. */
    TREASURE
}
