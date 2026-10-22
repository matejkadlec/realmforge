package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * RLM-310 (ADR 0044): vanilla strongholds are off, through Structurify and the pack's {@code config/structurify.json},
 * which the dev runs copy from the pack. The GameTest world is vanilla's default flat world, whose structure sets
 * hold villages and strongholds, so a village nearby shows the search works while no stronghold is found.
 */
public final class StrongholdGameTests {
    /** How far the search looks, in chunks: vanilla's {@code /locate} radius. */
    private static final int RADIUS = 100;

    private StrongholdGameTests() {
    }

    @QaGameTest(timeoutTicks = 400)
    public static void strongholdsAreOffAndVillagesStay(GameTestHelper helper) {
        helper.assertTrue(nearest(helper, BuiltinStructures.STRONGHOLD) == null,
                "no stronghold within " + RADIUS + " chunks");
        helper.assertTrue(nearest(helper, BuiltinStructures.VILLAGE_PLAINS) != null,
                "a plains village within " + RADIUS + " chunks, as before");
        helper.succeed();
    }

    /** The nearest {@code structure} to the test, as {@code /locate structure} finds it. */
    private static Pair<BlockPos, Holder<Structure>> nearest(GameTestHelper helper, ResourceKey<Structure> structure) {
        ServerLevel level = helper.getLevel();
        Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(structure);
        return level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder),
                helper.absolutePos(BlockPos.ZERO), RADIUS, false);
    }
}
