package io.github.matejkadlec.realmforge.dungeon.plan;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * The part a room plays in a dungeon layout: the role tag of its sidecar in {@code content/rooms/} (architecture
 * 15.3, 15.4), or {@link #WALL} for the plug that {@code rf gen} makes from a dungeon type's {@code walls}.
 */
public enum RoomRole implements StringRepresentable {
    /** Where players arrive; the first room of every layout. */
    ENTRANCE,
    HALLWAY,
    COMBAT,
    STAIRS,
    TREASURE,
    /** The boss's lair. */
    BOSS,
    /** A wall that closes a doorway nothing else takes, instead of a dead end (RLM-364): no room at all. */
    WALL;

    public static final Codec<RoomRole> CODEC = StringRepresentable.fromEnum(RoomRole::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Rooms a player passes through: they make up the main path and the branches. */
    public boolean passesThrough() {
        return this == HALLWAY || this == COMBAT || this == STAIRS;
    }

    /** Dead ends that may end a branch or close a connector: neither the entrance, the boss's lair nor a wall. */
    public boolean closesBranches() {
        return this != ENTRANCE && this != BOSS && this != WALL;
    }

    /** Rooms that may end the main path: the boss's lair or a treasury (architecture 15.2). */
    public boolean endsDungeons() {
        return this == BOSS || this == TREASURE;
    }
}
