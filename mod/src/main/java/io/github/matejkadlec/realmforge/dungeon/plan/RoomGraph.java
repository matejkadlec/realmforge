package io.github.matejkadlec.realmforge.dungeon.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.RandomSource;

/**
 * Step 1 of planning (architecture 15.3): which rooms a layout has and how they hang together, before any room is
 * chosen. A tree in placement order, so every node's parent comes before it: the entrance, the main path, the
 * ending, then the branches, each hanging off a room of the main path and ending in a dead end.
 *
 * <p>With a {@code special_gap} (RLM-364), each node also says whether a special room (a fight, treasure) or a plain
 * one (a hallway, stairs) takes it: on the way from the entrance, a special room follows as many plain rooms as a
 * gap drawn from the range, the last special room of the main path keeps at least the smallest gap before the
 * ending, a branch's dead end is special once the smallest gap lies before it, and branches hang off plain rooms of
 * the main path while it has any (the pool's junctions are mostly plain).
 */
final class RoomGraph {
    /** Which rooms may take a node: any that fits its slot, or only plain or special ones (RLM-364). */
    enum Kind {
        ANY,
        PLAIN,
        SPECIAL
    }

    /** One room to be: its slot, the node it hangs off ({@code -1} for the entrance) and which rooms may take it. */
    record Node(Slot slot, int parent, Kind kind) {
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
        Optional<InclusiveRange<Integer>> gap = rules.specialGap();
        List<Node> nodes = new ArrayList<>();
        // Per node, the plain rooms since the last special one on its way from the entrance, itself included.
        List<Integer> plainSince = new ArrayList<>();
        nodes.add(new Node(Slot.ENTRANCE, -1, Kind.ANY));
        plainSince.add(0);
        int mainPath = roll(rules.mainPath(), random);
        int since = 0;
        int target = gap.map(range -> roll(range, random)).orElse(0);
        for (int i = 1; i <= mainPath; i++) {
            Kind kind = Kind.ANY;
            if (gap.isPresent()) {
                kind = since >= target && mainPath - i >= gap.get().minInclusive() ? Kind.SPECIAL : Kind.PLAIN;
                since = kind == Kind.SPECIAL ? 0 : since + 1;
                if (kind == Kind.SPECIAL) {
                    target = roll(gap.get(), random);
                }
            }
            nodes.add(new Node(Slot.PATH, nodes.size() - 1, kind));
            plainSince.add(since);
        }
        nodes.add(new Node(Slot.ENDING, nodes.size() - 1, gap.isPresent() ? Kind.SPECIAL : Kind.ANY));
        plainSince.add(0);
        List<Integer> hosts = new ArrayList<>();
        for (int branch = roll(rules.branches(), random); branch > 0; branch--) {
            if (hosts.isEmpty()) {
                hosts.addAll(hosts(nodes, mainPath));
            }
            int host = hosts.remove(random.nextInt(hosts.size()));
            int length = roll(rules.branchRooms(), random);
            int parent = host;
            since = plainSince.get(host);
            target = gap.map(range -> roll(range, random)).orElse(0);
            for (int i = 1; i <= length; i++) {
                Slot slot = i == length ? Slot.BRANCH_END : Slot.BRANCH;
                Kind kind = Kind.ANY;
                if (gap.isPresent()) {
                    int needed = slot == Slot.BRANCH_END ? gap.get().minInclusive() : target;
                    kind = since >= needed ? Kind.SPECIAL : Kind.PLAIN;
                    since = kind == Kind.SPECIAL ? 0 : since + 1;
                    if (kind == Kind.SPECIAL) {
                        target = roll(gap.get(), random);
                    }
                }
                nodes.add(new Node(slot, parent, kind));
                plainSince.add(since);
                parent = nodes.size() - 1;
            }
        }
        return new RoomGraph(nodes);
    }

    /** The main path's rooms a branch may hang off: its plain ones when it has any, else all of them. */
    private static List<Integer> hosts(List<Node> nodes, int mainPath) {
        List<Integer> plain = new ArrayList<>();
        List<Integer> all = new ArrayList<>();
        for (int path = 1; path <= mainPath; path++) {
            all.add(path);
            if (nodes.get(path).kind() == Kind.PLAIN) {
                plain.add(path);
            }
        }
        return plain.isEmpty() ? all : plain;
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
