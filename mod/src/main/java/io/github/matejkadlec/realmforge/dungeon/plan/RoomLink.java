package io.github.matejkadlec.realmforge.dungeon.plan;

/**
 * Two connectors of a planned layout that meet: connector {@code connector} of room {@code room} and connector
 * {@code otherConnector} of room {@code otherRoom}, indices into {@link DungeonPlan#rooms()} and each room's
 * connectors.
 */
public record RoomLink(int room, int connector, int otherRoom, int otherConnector) {
}
