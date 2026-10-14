package io.github.matejkadlec.realmforge.dungeon.plan;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.RandomSource;

/**
 * Step 1 of planning (architecture 15.3): which rooms a layout has and how they hang together, before any room is
 * chosen. A tree in placement order, so every node's parent comes before it: the entrance, the main path, the
 * ending, then the branches, each hanging off a room of the main path and ending in a dead end.
 */
final class RoomGraph {
    /** One room to be: its slot and the node it hangs off ({@code -1} for the entrance). */
    record Node(Slot slot, int parent) {
    }

    private final List<Node> nodes;
    private final int[] children;

    private RoomGraph(List<Node> nodes) {
        this.nodes = List.copyOf(nodes);
        this.children = new int[nodes.size()];
        for (Node node : nodes) {
            if (node.parent() >= 0) {
                children[node.parent()]++;
            }
        }
    }

    /**
     * A graph drawn from the rules: a main path and a number of branches, each with a length, within their ranges.
     * Branches hang off different rooms of the main path while there are enough, so no room has to take several.
     */
    static RoomGraph draw(LayoutRules rules, RandomSource random) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(Slot.ENTRANCE, -1));
        int mainPath = roll(rules.mainPath(), random);
        for (int i = 0; i < mainPath; i++) {
            nodes.add(new Node(Slot.PATH, nodes.size() - 1));
        }
        nodes.add(new Node(Slot.ENDING, nodes.size() - 1));
        List<Integer> hosts = new ArrayList<>();
        for (int branch = roll(rules.branches(), random); branch > 0; branch--) {
            if (hosts.isEmpty()) {
                for (int path = 1; path <= mainPath; path++) {
                    hosts.add(path);
                }
            }
            int host = hosts.remove(random.nextInt(hosts.size()));
            int length = roll(rules.branchRooms(), random);
            int parent = host;
            for (int i = 1; i <= length; i++) {
                nodes.add(new Node(i == length ? Slot.BRANCH_END : Slot.BRANCH, parent));
                parent = nodes.size() - 1;
            }
        }
        return new RoomGraph(nodes);
    }

    int size() {
        return nodes.size();
    }

    Node node(int index) {
        return nodes.get(index);
    }

    /** The connectors the node's room needs: one to its parent, one for each node hanging off it. */
    int connectorsNeeded(int index) {
        return children[index] + (nodes.get(index).parent() < 0 ? 0 : 1);
    }

    private static int roll(InclusiveRange<Integer> range, RandomSource random) {
        return random.nextIntBetweenInclusive(range.minInclusive(), range.maxInclusive());
    }
}
