package io.github.matejkadlec.realmforge.dungeon.plan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The chests and barrels of a room that its dungeon's chance tables decide (RLM-365), resolved by {@code rf gen}:
 * the candidates (each with the block it leaves behind when the draw drops it) and, per kind, the chance of each
 * count as weights by count (index 0: none stays). A kind without a table lists no candidates and keeps all it has.
 *
 * @param spots the candidates, in template order
 * @param chests the chance table of the single chests, empty for none
 * @param barrels the chance table of the barrels, empty for none
 */
public record RoomContainers(List<Spot> spots, List<Integer> chests, List<Integer> barrels) {
    /** A room whose containers all stay. */
    public static final RoomContainers NONE = new RoomContainers(List.of(), List.of(), List.of());

    public static final Codec<RoomContainers> CODEC = RecordCodecBuilder.<RoomContainers>create(instance -> instance
            .group(Spot.CODEC.listOf().fieldOf("spots").forGetter(RoomContainers::spots),
                    Codec.intRange(0, 100).listOf().optionalFieldOf("chests", List.of())
                            .forGetter(RoomContainers::chests),
                    Codec.intRange(0, 100).listOf().optionalFieldOf("barrels", List.of())
                            .forGetter(RoomContainers::barrels))
            .apply(instance, RoomContainers::new)).validate(RoomContainers::validate);

    public RoomContainers {
        spots = List.copyOf(spots);
        chests = List.copyOf(chests);
        barrels = List.copyOf(barrels);
    }

    /** The chance table of {@code kind}, empty for none. */
    public List<Integer> table(Kind kind) {
        return kind == Kind.CHEST ? chests : barrels;
    }

    /**
     * Which containers the dungeon drops when it places the room: per kind with a table, a count drawn from the table
     * (at most as many as the room holds) stays, picked at random among the candidates, and every other candidate
     * becomes the block it leaves behind. Positions are the template's.
     */
    public List<RoomPlacement.Swap> draw(RandomSource random) {
        List<RoomPlacement.Swap> swaps = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            List<Integer> table = table(kind);
            List<Spot> candidates = new ArrayList<>(spots.stream().filter(spot -> spot.kind() == kind).toList());
            if (table.isEmpty() || candidates.isEmpty()) {
                continue;
            }
            int kept = Math.min(count(table, random), candidates.size());
            Util.shuffle(candidates, random);
            for (Spot dropped : candidates.subList(kept, candidates.size())) {
                swaps.add(new RoomPlacement.Swap(dropped.pos(), dropped.instead()));
            }
        }
        return swaps;
    }

    /** A count drawn from {@code table}, the weight at each index being that count's chance. */
    static int count(List<Integer> table, RandomSource random) {
        int total = table.stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(total);
        for (int count = 0; count < table.size(); count++) {
            roll -= table.get(count);
            if (roll < 0) {
                return count;
            }
        }
        throw new IllegalStateException("a roll past the table " + table);
    }

    private static DataResult<RoomContainers> validate(RoomContainers containers) {
        for (Kind kind : Kind.values()) {
            List<Integer> table = containers.table(kind);
            if (!table.isEmpty() && table.stream().mapToInt(Integer::intValue).sum() <= 0) {
                return DataResult.error(() -> "the " + kind.getSerializedName() + " table " + table + " has no chance");
            }
        }
        return DataResult.success(containers);
    }

    /** The kinds of container a table decides: single chests and barrels (trapped and double chests always stay). */
    public enum Kind implements StringRepresentable {
        CHEST("chest"),
        BARREL("barrel");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** A candidate: where it is in the template, its kind, and the block it leaves behind when dropped. */
    public record Spot(BlockPos pos, Kind kind, BlockState instead) {
        public static final Codec<Spot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Spot::pos),
                Kind.CODEC.fieldOf("kind").forGetter(Spot::kind),
                BlockState.CODEC.fieldOf("instead").forGetter(Spot::instead)
        ).apply(instance, Spot::new));
    }
}
