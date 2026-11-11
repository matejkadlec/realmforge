package io.github.matejkadlec.realmforge.dungeon.plan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.dungeon.instance.PiecePlacement;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The set pieces a dungeon type's runs place (RLM-282, ADR 0050, architecture 15.4), resolved by {@code rf gen}: small
 * hand-built dressing (a barrel against a wall, a niche with hay bales) that fills the spots {@code rf gen} found in
 * the type's rooms ({@link DungeonRoom#setPieceSpots}). A run fills some of a room's spot patterns, each with one
 * piece on every spot of the pattern, and leaves the others empty; everything is drawn from the layout's seed when the
 * run is planned ({@link DungeonPlan#toInstanceLayout}), so the same seed always gives the same pieces, and a new run
 * new ones, wherever its plot lies (ADR 0077).
 *
 * @param density how many patterns a room of each role fills: the chance of each count as weights by count (index 0:
 *     none); a role without a table fills none
 * @param containerLoot the loot table a piece's chests and barrels get (the type's default container loot, RLM-290)
 * @param pieces the pieces, by id
 */
public record SetPieces(Map<RoomRole, List<Integer>> density, Optional<ResourceKey<LootTable>> containerLoot,
        Map<Identifier, Piece> pieces) {
    /** A dungeon type that places no set pieces. */
    public static final SetPieces NONE = new SetPieces(Map.of(), Optional.empty(), Map.of());

    public static final Codec<SetPieces> CODEC = RecordCodecBuilder.<SetPieces>create(instance -> instance.group(
                    Codec.unboundedMap(RoomRole.CODEC, Codec.intRange(0, 100).listOf())
                            .optionalFieldOf("density", Map.of()).forGetter(SetPieces::density),
                    LootTable.KEY_CODEC.optionalFieldOf("container_loot").forGetter(SetPieces::containerLoot),
                    Codec.unboundedMap(Identifier.CODEC, Piece.CODEC).fieldOf("pieces").forGetter(SetPieces::pieces))
            .apply(instance, SetPieces::new)).validate(SetPieces::validate);

    public SetPieces {
        Map<RoomRole, List<Integer>> tables = new EnumMap<>(RoomRole.class);
        density.forEach((role, table) -> tables.put(role, List.copyOf(table)));
        density = Collections.unmodifiableMap(tables);
        // In id order whatever the map it came from, so a draw never depends on a map's order.
        Map<Identifier, Piece> sorted = new LinkedHashMap<>();
        pieces.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        pieces = Collections.unmodifiableMap(sorted);
    }

    /**
     * The pieces a run places in {@code room}, which stands at {@code offset} from the plot's origin turned by
     * {@code rotation}: a count of its patterns drawn from the density of its role (at most as many as it has), those
     * patterns picked at random, each filled on every spot with one piece picked by weight among the pieces that fit
     * it, mirrored where the spot is and the piece allows it. A piece's variants are drawn once per pattern, so the
     * pieces of a pattern look alike (a mirrored pair is a pair, ADR 0036).
     */
    public List<PiecePlacement> draw(DungeonRoom room, BlockPos offset, Rotation rotation, RandomSource random) {
        List<Pattern> patterns = room.setPieceSpots().stream()
                .filter(pattern -> pattern.pieces().stream().anyMatch(pieces::containsKey))
                .toList();
        List<Integer> table = density.getOrDefault(room.role(), List.of());
        if (patterns.isEmpty() || table.isEmpty()) {
            return List.of();
        }
        int count = Math.min(RoomContainers.count(table, random), patterns.size());
        List<Pattern> shuffled = new ArrayList<>(patterns);
        Util.shuffle(shuffled, random);
        List<PiecePlacement> placed = new ArrayList<>();
        for (Pattern pattern : shuffled.subList(0, count)) {
            Identifier id = pick(pattern.pieces().stream().filter(pieces::containsKey).toList(), random);
            Piece piece = pieces.get(id);
            List<RoomPlacement.Swap> swaps = piece.drawVariants(random);
            for (Spot spot : pattern.spots()) {
                placed.add(place(piece, spot, offset, rotation, swaps));
            }
        }
        return placed;
    }

    /**
     * Where and how the builder places {@code piece} on {@code spot} of a room at {@code offset} turned by
     * {@code rotation}. The spot names the lowest corner of the piece's turned and mirrored box in the room's own
     * template ({@code rooms/set_pieces.py}); vanilla places a template from its origin corner, mirrored and then
     * turned about it ({@link StructureTemplate#transform}), so the origin moves back by where that corner ends up,
     * and the whole follows the room's own turn.
     */
    public PiecePlacement place(Piece piece, Spot spot, BlockPos offset, Rotation rotation,
            List<RoomPlacement.Swap> swaps) {
        Mirror mirror = spot.mirror() && piece.mirror() ? Mirror.FRONT_BACK : Mirror.NONE;
        Rotation turn = turn(spot.facing());
        BlockPos far = StructureTemplate.transform(
                new BlockPos(piece.size().getX() - 1, 0, piece.size().getZ() - 1), mirror, turn, BlockPos.ZERO);
        BlockPos origin = spot.pos().offset(-Math.min(0, far.getX()), 0, -Math.min(0, far.getZ()));
        BlockPos placed = offset.offset(StructureTemplate.transform(origin, Mirror.NONE, rotation, BlockPos.ZERO));
        return new PiecePlacement(piece.template(), placed, turn.getRotated(rotation), mirror, swaps, containerLoot);
    }

    /**
     * The quarter turns that make a piece's front face {@code facing}: a piece is built with its wall side to the
     * north and its front to the south, and a clockwise turn sends south to west.
     */
    public static Rotation turn(Direction facing) {
        return switch (facing) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private Identifier pick(List<Identifier> candidates, RandomSource random) {
        int total = candidates.stream().mapToInt(id -> pieces.get(id).weight()).sum();
        int roll = random.nextInt(total);
        for (Identifier id : candidates) {
            roll -= pieces.get(id).weight();
            if (roll < 0) {
                return id;
            }
        }
        throw new IllegalStateException("a roll past the pieces " + candidates);
    }

    private static DataResult<SetPieces> validate(SetPieces pieces) {
        for (Map.Entry<RoomRole, List<Integer>> table : pieces.density.entrySet()) {
            if (table.getValue().stream().mapToInt(Integer::intValue).sum() <= 0) {
                return DataResult.error(() -> "the set-piece density of " + table.getKey().getSerializedName()
                        + " rooms, " + table.getValue() + ", has no chance");
            }
        }
        return DataResult.success(pieces);
    }

    /**
     * A piece: its template, the template's size, its weight among the pieces that fit a pattern, whether a mirrored
     * spot takes it mirrored, and the blocks of it that vary.
     */
    public record Piece(Identifier template, Vec3i size, int weight, boolean mirror, List<Variant> variants) {
        public static final Codec<Piece> CODEC = RecordCodecBuilder.<Piece>create(instance -> instance.group(
                        Identifier.CODEC.fieldOf("template").forGetter(Piece::template),
                        Vec3i.CODEC.fieldOf("size").forGetter(Piece::size),
                        Codec.intRange(1, 150).optionalFieldOf("weight", 1).forGetter(Piece::weight),
                        Codec.BOOL.optionalFieldOf("mirror", false).forGetter(Piece::mirror),
                        Variant.CODEC.listOf().optionalFieldOf("variants", List.of()).forGetter(Piece::variants))
                .apply(instance, Piece::new)).validate(Piece::validate);

        public Piece {
            variants = List.copyOf(variants);
        }

        /**
         * The blocks of the piece that a run changes: each variant on its own, at its chance, in the order written.
         */
        public List<RoomPlacement.Swap> drawVariants(RandomSource random) {
            List<RoomPlacement.Swap> swaps = new ArrayList<>();
            for (Variant variant : variants) {
                if (random.nextFloat() < variant.chance()) {
                    swaps.add(new RoomPlacement.Swap(variant.pos(), variant.state()));
                }
            }
            return swaps;
        }

        private static DataResult<Piece> validate(Piece piece) {
            Vec3i size = piece.size;
            if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1) {
                return DataResult.error(() -> piece.template + ": size " + size.toShortString() + " is empty");
            }
            for (Variant variant : piece.variants) {
                BlockPos pos = variant.pos();
                if (pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0 || pos.getX() >= size.getX()
                        || pos.getY() >= size.getY() || pos.getZ() >= size.getZ()) {
                    return DataResult.error(() -> piece.template + ": a variant at " + pos.toShortString()
                            + " lies outside the piece");
                }
            }
            return DataResult.success(piece);
        }
    }

    /**
     * A block of a piece that varies ({@code generate/set_pieces.py}): at {@code chance} the block at {@code pos} of
     * the template becomes {@code state}, air for a block that is there only by chance, or the block a swap names.
     * The state is the template's own, unturned: the game turns it with the piece.
     */
    public record Variant(BlockPos pos, float chance, BlockState state) {
        public static final Codec<Variant> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Variant::pos),
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(Variant::chance),
                BlockState.CODEC.fieldOf("state").forGetter(Variant::state)
        ).apply(instance, Variant::new));
    }

    /**
     * Spots of a room that a run fills together or not at all (ADR 0036): a pair mirrored across the room, one in
     * each corner, or one centred on a wall, with the pieces that fit them.
     */
    public record Pattern(List<Identifier> pieces, List<Spot> spots) {
        public static final Codec<Pattern> CODEC = RecordCodecBuilder.<Pattern>create(instance -> instance.group(
                Identifier.CODEC.listOf().fieldOf("pieces").forGetter(Pattern::pieces),
                Spot.CODEC.listOf().fieldOf("spots").forGetter(Pattern::spots)
        ).apply(instance, Pattern::new)).validate(pattern -> pattern.pieces.isEmpty() || pattern.spots.isEmpty()
                ? DataResult.error(() -> "a set-piece pattern needs a piece and a spot")
                : DataResult.success(pattern));

        public Pattern {
            pieces = List.copyOf(pieces);
            spots = List.copyOf(spots);
        }
    }

    /**
     * Where a piece goes in a room's template: the lowest corner of its turned and mirrored box, at the height it
     * stands on, the way its front faces, and whether it is mirrored there.
     */
    public record Spot(BlockPos pos, Direction facing, boolean mirror) {
        public static final Codec<Spot> CODEC = RecordCodecBuilder.<Spot>create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Spot::pos),
                Direction.CODEC.fieldOf("facing").forGetter(Spot::facing),
                Codec.BOOL.optionalFieldOf("mirror", false).forGetter(Spot::mirror)
        ).apply(instance, Spot::new)).validate(spot -> spot.facing.getAxis().isHorizontal()
                ? DataResult.success(spot)
                : DataResult.error(() -> "a set piece faces a side, not " + spot.facing.getSerializedName()));
    }
}
