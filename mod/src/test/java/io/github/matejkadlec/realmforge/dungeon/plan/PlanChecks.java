package io.github.matejkadlec.realmforge.dungeon.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Every rule a planned layout must keep, checked from the plan alone (architecture 15.3). */
final class PlanChecks {
    private PlanChecks() {
    }

    static void assertValid(DungeonPlan plan, List<DungeonRoom> pool, LayoutRules rules, BoundingBox limits) {
        String seed = "seed " + plan.seed() + ": ";
        List<PlannedRoom> rooms = plan.rooms();
        PlannedRoom entrance = plan.entrance();
        assertEquals(Slot.ENTRANCE, entrance.slot(), seed + "the first room is the entrance");
        assertEquals(BlockPos.ZERO, entrance.offset(), seed + "the entrance's origin corner is the plot's origin");
        assertEquals(1, plan.count(Slot.ENTRANCE), seed + "one entrance");
        assertEquals(1, plan.count(Slot.ENDING), seed + "one ending");
        assertWithin(rules.mainPath(), plan.count(Slot.PATH), seed + "rooms on the main path");
        assertWithin(rules.branches(), plan.count(Slot.BRANCH_END), seed + "branches");

        for (int i = 0; i < rooms.size(); i++) {
            PlannedRoom room = rooms.get(i);
            String where = seed + "room " + i + " (" + room.slot().label() + ", " + room.room().template() + ")";
            assertTrue(pool.contains(room.room()), where + " comes from the pool");
            assertTrue(fitsSlot(room, rules), where + " fits its slot");
            assertEquals(room.room().box(room.offset(), room.rotation()), room.box(), where + " box");
            assertTrue(inside(limits, room.box()), where + " lies inside the limits: " + room.box());
            for (int j = i + 1; j < rooms.size(); j++) {
                assertFalse(room.box().intersects(rooms.get(j).box()), where + " overlaps room " + j);
            }
        }

        boolean[][] linked = new boolean[rooms.size()][];
        for (int i = 0; i < rooms.size(); i++) {
            linked[i] = new boolean[rooms.get(i).room().connectors().size()];
        }
        List<List<Integer>> neighbours = new ArrayList<>();
        rooms.forEach(room -> neighbours.add(new ArrayList<>()));
        int[] parent = new int[rooms.size()];
        java.util.Arrays.fill(parent, -1);
        for (RoomLink link : plan.links()) {
            Connector source = rooms.get(link.room()).connector(link.connector());
            Connector target = rooms.get(link.otherRoom()).connector(link.otherConnector());
            String where = seed + "link " + link;
            assertTrue(source.takes(target), where + ": the connectors meet by the jigsaw rules");
            assertEquals(source.pos().relative(source.front()), target.pos(), where + ": the connectors touch");
            assertFalse(linked[link.room()][link.connector()], where + ": a connector meets one connector");
            assertFalse(linked[link.otherRoom()][link.otherConnector()], where + ": a connector meets one connector");
            linked[link.room()][link.connector()] = true;
            linked[link.otherRoom()][link.otherConnector()] = true;
            neighbours.get(link.room()).add(link.otherRoom());
            neighbours.get(link.otherRoom()).add(link.room());
            if (parent[link.otherRoom()] < 0 && link.otherRoom() > link.room()) {
                parent[link.otherRoom()] = link.room();
            }
        }
        for (int i = 0; i < rooms.size(); i++) {
            for (int c = 0; c < linked[i].length; c++) {
                assertTrue(linked[i][c], seed + "connector " + c + " of room " + i + " stays open");
            }
        }

        // RLM-256: a room's limit per layout, and a room that belongs at the start hangs off the entrance or off the
        // first room of the main path.
        for (DungeonRoom room : pool) {
            long count = rooms.stream().filter(placed -> placed.room().equals(room)).count();
            room.maxPerLayout().ifPresent(max -> assertTrue(count <= max,
                    seed + room.template() + " appears " + count + " times, at most " + max + " allowed"));
        }
        // With a pacing, the start is the main path's approach (RLM-364).
        List<Integer> start = new ArrayList<>(List.of(0));
        for (int room : mainPath(rooms, parent)) {
            if (start.size() <= (rules.pacing().isPresent() ? RoomGraph.APPROACH : 1)) {
                start.add(room);
            }
        }
        for (int i = 1; i < rooms.size(); i++) {
            if (rooms.get(i).room().nearStart()) {
                assertTrue(start.contains(parent[i]), seed + "room " + i + " ("
                        + rooms.get(i).room().template() + ") hangs off room " + parent[i] + ", not at the start");
            }
        }

        boolean[] reached = reached(neighbours, -1);
        for (int i = 0; i < rooms.size(); i++) {
            assertTrue(reached[i], seed + "room " + i + " cannot be reached from the entrance");
        }

        // RLM-362: a room with an entry hangs off the room before it through that connector, and without it nothing
        // beyond its other connectors can be reached from the entrance: no loop leads in from another side.
        for (int i = 1; i < rooms.size(); i++) {
            Optional<Integer> entry = rooms.get(i).room().entry();
            if (entry.isEmpty()) {
                continue;
            }
            String where = seed + "room " + i + " (" + rooms.get(i).room().template() + ")";
            boolean[] without = reached(neighbours, i);
            boolean enteredThroughEntry = false;
            for (RoomLink link : plan.links()) {
                boolean first = link.room() == i;
                if (!first && link.otherRoom() != i) {
                    continue;
                }
                int connector = first ? link.connector() : link.otherConnector();
                int other = first ? link.otherRoom() : link.room();
                if (connector == entry.get()) {
                    enteredThroughEntry = other == parent[i];
                } else {
                    assertFalse(without[other], where + " can be entered through its connector " + connector
                            + " from room " + other + ", not only through its entry " + entry.get());
                }
            }
            assertTrue(enteredThroughEntry, where + " hangs off room " + parent[i] + " through its entry");
        }

        // RLM-364: with a pacing, fights on the main path keep at least the smallest gap from the entrance and from
        // each other, a branch is corridors up to its end, caps are walls, corridors' dead ends or the room meant for
        // the start, and a loop leads around few rooms of the tree and past no fight.
        rules.pacing().ifPresent(pacing -> {
            int since = 0;
            for (int room : mainPath(rooms, parent)) {
                if (paced(rooms.get(room), Pace.FIGHT)) {
                    assertTrue(since >= pacing.fights().minInclusive(), seed + "the fight in room " + room
                            + " follows only " + since + " rooms after the last fight or the entrance");
                    since = 0;
                } else {
                    since++;
                }
            }
            for (int i = 1; i < rooms.size(); i++) {
                PlannedRoom room = rooms.get(i);
                String where = seed + "room " + i + " (" + room.slot().label() + ", " + room.room().template() + ")";
                if (room.slot() == Slot.BRANCH) {
                    assertTrue(paced(room, Pace.CORRIDOR), where + " is a branch's corridor");
                } else if (room.slot() == Slot.CAP) {
                    assertTrue(room.room().role() == RoomRole.WALL || room.room().nearStart()
                            || paced(room, Pace.CORRIDOR), where + " closes a connector as a wall or a corridor");
                }
            }
            for (RoomLink link : plan.links()) {
                int a = link.room();
                int b = link.otherRoom();
                if (parent[a] == b || parent[b] == a) {
                    continue;
                }
                List<Integer> way = way(parent, a, b);
                assertTrue(way.size() - 1 <= DungeonPlanner.LOOP_REACH, seed + "the loop " + link + " leads around "
                        + (way.size() - 1) + " rooms of the tree");
                for (int room : way.subList(1, way.size() - 1)) {
                    assertFalse(paced(rooms.get(room), Pace.FIGHT), seed + "the loop " + link
                            + " leads around the fight in room " + room);
                }
            }
        });

        for (int i = 0; i < rooms.size(); i++) {
            Slot slot = rooms.get(i).slot();
            if (slot == Slot.ENDING) {
                assertEquals(plan.count(Slot.PATH), chain(rooms, parent, i, Slot.PATH),
                        seed + "the ending closes a chain of every main path room");
            } else if (slot == Slot.BRANCH_END) {
                int length = 1 + chain(rooms, parent, i, Slot.BRANCH);
                assertWithin(rules.branchRooms(), length, seed + "rooms in the branch ending in room " + i);
                int host = i;
                for (int step = 0; step < length; step++) {
                    host = parent[host];
                }
                assertEquals(Slot.PATH, rooms.get(host).slot(), seed + "branch " + i + " hangs off the main path");
            }
        }
    }

    /** Which rooms the entrance reaches over the links, without passing room {@code skipped} ({@code -1}: none). */
    private static boolean[] reached(List<List<Integer>> neighbours, int skipped) {
        boolean[] reached = new boolean[neighbours.size()];
        Deque<Integer> queue = new ArrayDeque<>(List.of(0));
        reached[0] = true;
        while (!queue.isEmpty()) {
            for (int next : neighbours.get(queue.poll())) {
                if (!reached[next] && next != skipped) {
                    reached[next] = true;
                    queue.add(next);
                }
            }
        }
        return reached;
    }

    private static boolean paced(PlannedRoom room, Pace pace) {
        return room.room().pace().equals(Optional.of(pace));
    }

    /** The main path's rooms from the entrance to the ending, both left out. */
    private static List<Integer> mainPath(List<PlannedRoom> rooms, int[] parent) {
        List<Integer> path = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).slot() == Slot.ENDING) {
                for (int room = parent[i]; room > 0; room = parent[room]) {
                    path.addFirst(room);
                }
            }
        }
        return path;
    }

    /** The rooms on the tree's way from room {@code a} to room {@code b}, both included. */
    private static List<Integer> way(int[] parent, int a, int b) {
        List<Integer> up = new ArrayList<>();
        for (int room = a; room >= 0; room = parent[room]) {
            up.add(room);
        }
        List<Integer> down = new ArrayList<>();
        int common = b;
        while (!up.contains(common)) {
            down.addFirst(common);
            common = parent[common];
        }
        List<Integer> way = new ArrayList<>(up.subList(0, up.indexOf(common) + 1));
        way.addAll(down);
        return way;
    }

    /** How many rooms of {@code slot} lead from room {@code from} back towards the entrance without a break. */
    private static int chain(List<PlannedRoom> rooms, int[] parent, int from, Slot slot) {
        int count = 0;
        for (int room = parent[from]; room >= 0 && rooms.get(room).slot() == slot; room = parent[room]) {
            count++;
        }
        return count;
    }

    private static boolean fitsSlot(PlannedRoom planned, LayoutRules rules) {
        DungeonRoom room = planned.room();
        return switch (planned.slot()) {
            case ENTRANCE -> room.role() == RoomRole.ENTRANCE;
            case PATH, BRANCH -> room.role().passesThrough();
            case ENDING -> room.role() == rules.ending();
            case BRANCH_END -> room.role().closesBranches() && (room.deadEnd() || rules.pacing().isPresent()
                    && room.role().passesThrough() && room.chests() >= DungeonRoom.TREASURE_CHESTS);
            case CAP -> (room.role().closesBranches() || room.role() == RoomRole.WALL) && room.deadEnd();
        };
    }

    private static void assertWithin(InclusiveRange<Integer> range, long value, String what) {
        assertTrue(value >= range.minInclusive() && value <= range.maxInclusive(),
                what + ": " + value + " is outside " + range.minInclusive() + ".." + range.maxInclusive());
    }

    private static boolean inside(BoundingBox limits, BoundingBox box) {
        return box.minX() >= limits.minX() && box.minY() >= limits.minY() && box.minZ() >= limits.minZ()
                && box.maxX() <= limits.maxX() && box.maxY() <= limits.maxY() && box.maxZ() <= limits.maxZ();
    }
}
