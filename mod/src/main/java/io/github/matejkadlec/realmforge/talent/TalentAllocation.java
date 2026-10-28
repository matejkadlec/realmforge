package io.github.matejkadlec.realmforge.talent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

/**
 * The value of a player's {@code realmforge:talents} attachment (architecture 7 and 12): the ranks allocated in each
 * talent tree and the points not spent yet. Trees are named by id until their registry comes with talents (postponed,
 * RLM-28).
 *
 * @param ranks         tree to node id to rank (1 or more); unallocated nodes and trees are absent (an empty tree is
 *                      dropped)
 * @param unspentPoints talent points granted but not spent yet
 */
public record TalentAllocation(Map<Identifier, Map<String, Integer>> ranks, int unspentPoints) {
    public static final TalentAllocation NONE = new TalentAllocation(Map.of(), 0);

    /** A node id inside its tree, the same shape as the path of our content ids. */
    private static final Pattern NODE_ID = Pattern.compile("[a-z0-9_]+");

    private static final Codec<String> NODE_ID_CODEC = Codec.STRING.validate(id -> NODE_ID.matcher(id).matches()
            ? DataResult.success(id)
            : DataResult.error(() -> "Not a talent node id: " + id));

    public static final Codec<TalentAllocation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, Codec.unboundedMap(NODE_ID_CODEC, ExtraCodecs.POSITIVE_INT))
                    .optionalFieldOf("ranks", Map.of()).forGetter(TalentAllocation::ranks),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("unspent_points", 0).forGetter(TalentAllocation::unspentPoints)
    ).apply(instance, TalentAllocation::new));

    private static final StreamCodec<ByteBuf, Map<String, Integer>> NODES_STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

    public static final StreamCodec<ByteBuf, TalentAllocation> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, NODES_STREAM_CODEC), TalentAllocation::ranks,
            ByteBufCodecs.VAR_INT, TalentAllocation::unspentPoints,
            TalentAllocation::new);

    public TalentAllocation {
        Map<Identifier, Map<String, Integer>> copy = new LinkedHashMap<>();
        ranks.forEach((tree, nodes) -> {
            nodes.forEach((node, rank) -> {
                if (!NODE_ID.matcher(node).matches()) {
                    throw new IllegalArgumentException("Not a talent node id: " + tree + " / " + node);
                }
                if (rank < 1) {
                    throw new IllegalArgumentException("An allocated rank is 1 or more: " + tree + " / " + node);
                }
            });
            if (!nodes.isEmpty()) {
                copy.put(tree, Map.copyOf(nodes));
            }
        });
        ranks = Map.copyOf(copy);
        if (unspentPoints < 0) {
            throw new IllegalArgumentException("Unspent talent points are never negative: " + unspentPoints);
        }
    }

    /** The rank allocated in a node, 0 when none. */
    public int rank(Identifier tree, String node) {
        return ranks.getOrDefault(tree, Map.of()).getOrDefault(node, 0);
    }

    /** Points spent in a tree, which its tiers count (architecture 12). */
    public int pointsSpent(Identifier tree) {
        return ranks.getOrDefault(tree, Map.of()).values().stream().mapToInt(Integer::intValue).sum();
    }
}
