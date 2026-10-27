package io.github.matejkadlec.realmforge.dungeon.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

/** RLM-365: a room's chance tables decide how many of its chests and barrels a dungeon places. */
class RoomContainersTest {
    /** Matěj's treasure table for chests: 2 at 25 %, 3 at 40 %, 4 at 25 %, 5 at 10 %. */
    private static final List<Integer> TREASURE_CHESTS = List.of(0, 0, 25, 40, 25, 10);
    private static final List<Integer> BARRELS = List.of(40, 40, 20);

    @Test
    void countsComeAsOftenAsTheirChances() {
        RandomSource random = RandomSource.create(1);
        int draws = 40_000;
        int[] seen = new int[TREASURE_CHESTS.size()];
        for (int i = 0; i < draws; i++) {
            seen[RoomContainers.count(TREASURE_CHESTS, random)]++;
        }
        for (int count = 0; count < seen.length; count++) {
            double share = seen[count] / (double) draws;
            assertEquals(TREASURE_CHESTS.get(count) / 100.0, share, 0.01, "the share of " + count + " chests");
        }
    }

    @Test
    void aDrawKeepsItsCountOfEachKindAndSwapsTheRest() {
        RoomContainers containers = new RoomContainers(spots(6, 3), TREASURE_CHESTS, BARRELS);
        Map<Integer, Integer> keptChests = new TreeMap<>();
        RandomSource random = RandomSource.create(7);
        int draws = 20_000;
        for (int i = 0; i < draws; i++) {
            List<RoomPlacement.Swap> swaps = containers.draw(random);
            long chestSwaps = swaps.stream().filter(swap -> swap.pos().getY() == 1).count();
            long barrelSwaps = swaps.stream().filter(swap -> swap.pos().getY() == 2).count();
            assertTrue(barrelSwaps >= 1 && barrelSwaps <= 3, "barrels kept: " + (3 - barrelSwaps));
            keptChests.merge(6 - (int) chestSwaps, 1, Integer::sum);
            assertTrue(swaps.stream().allMatch(swap -> swap.state() == (swap.pos().getY() == 1
                    ? Blocks.AIR.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState())),
                    "a dropped container leaves its own block");
        }
        assertEquals(List.of(2, 3, 4, 5), List.copyOf(keptChests.keySet()), "chests kept");
        assertEquals(0.40, keptChests.get(3) / (double) draws, 0.015, "3 chests kept");
    }

    @Test
    void aRoomWithFewerContainersKeepsAllItHas() {
        // The treasure table asks for 2 to 5 chests; the egg room holds 2 (RLM-361), so both always stay.
        RoomContainers egg = new RoomContainers(spots(2, 0), TREASURE_CHESTS, List.of());
        RandomSource random = RandomSource.create(3);
        for (int i = 0; i < 1_000; i++) {
            assertEquals(List.of(), egg.draw(random));
        }
    }

    @Test
    void aKindWithoutATableKeepsAllAndNoneMayKeepNothing() {
        RoomContainers onlyBarrels = new RoomContainers(spots(2, 3), List.of(), List.of(100));
        List<RoomPlacement.Swap> swaps = onlyBarrels.draw(RandomSource.create(5));
        assertEquals(3, swaps.size(), "every barrel goes");
        assertTrue(swaps.stream().allMatch(swap -> swap.pos().getY() == 2), "no chest goes");
        assertEquals(List.of(), RoomContainers.NONE.draw(RandomSource.create(5)));
    }

    @Test
    void theSameSeedDropsTheSameContainers() {
        RoomContainers containers = new RoomContainers(spots(6, 3), TREASURE_CHESTS, BARRELS);
        for (long seed = 0; seed < 50; seed++) {
            assertEquals(containers.draw(RandomSource.create(seed)), containers.draw(RandomSource.create(seed)));
        }
        long distinct = java.util.stream.LongStream.range(0, 50)
                .mapToObj(seed -> containers.draw(RandomSource.create(seed)).stream().map(RoomPlacement.Swap::pos)
                        .collect(Collectors.toSet()))
                .distinct().count();
        assertTrue(distinct > 10, "the draws vary between seeds: " + distinct);
    }

    @Test
    void theGeneratedEntryReadsAndAPlacementKeepsItsSwaps() {
        String json = """
                {"spots": [
                  {"pos": [6, 2, 1], "kind": "chest", "instead": {"Name": "minecraft:air"}},
                  {"pos": [7, 2, 6], "kind": "barrel", "instead": {"Name": "minecraft:stone_bricks"}}],
                 "chests": [0, 75, 20, 5], "barrels": [40, 40, 20]}""";
        RoomContainers containers = RoomContainers.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow();
        assertEquals(RoomContainers.Kind.BARREL, containers.spots().get(1).kind());
        assertEquals(Blocks.STONE_BRICKS.defaultBlockState(), containers.spots().get(1).instead());
        assertEquals(List.of(0, 75, 20, 5), containers.chests());

        RoomPlacement room = new RoomPlacement(Identifier.parse("realmforge:rooms/test"), new BlockPos(1, 2, 3),
                Rotation.CLOCKWISE_90, List.of(),
                List.of(new RoomPlacement.Swap(new BlockPos(6, 2, 1), Blocks.AIR.defaultBlockState())));
        RoomPlacement read = RoomPlacement.CODEC.parse(JsonOps.INSTANCE,
                RoomPlacement.CODEC.encodeStart(JsonOps.INSTANCE, room).getOrThrow()).getOrThrow();
        assertEquals(room, read);
    }

    /** {@code chests} chests in a row at y 1, each leaving air, and {@code barrels} barrels at y 2, leaving stone bricks. */
    private static List<RoomContainers.Spot> spots(int chests, int barrels) {
        List<RoomContainers.Spot> spots = new java.util.ArrayList<>();
        for (int x = 0; x < chests; x++) {
            spots.add(new RoomContainers.Spot(new BlockPos(x, 1, 0), RoomContainers.Kind.CHEST,
                    Blocks.AIR.defaultBlockState()));
        }
        for (int x = 0; x < barrels; x++) {
            spots.add(new RoomContainers.Spot(new BlockPos(x, 2, 0), RoomContainers.Kind.BARREL,
                    Blocks.STONE_BRICKS.defaultBlockState()));
        }
        return spots;
    }
}
