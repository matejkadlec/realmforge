package io.github.matejkadlec.realmforge.dungeon.instance;

import io.github.matejkadlec.realmforge.Realmforge;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A run's rooms stand in its dungeon type's biome, {@code realmforge:dungeon_<type>}, which {@code rf gen} names after
 * the type (RLM-409, architecture 15.5): Xaero's Minimap shows it as the biome, and the client's arrival title names
 * it ({@code client/hud/InstanceArrivalTitle}). The instance dimension's generator fills it with
 * {@code minecraft:the_void}. Once a run's rooms stand, every 4 x 4 x 4 biome cell within {@link #REACH} blocks of a
 * room gets the run's biome, the way {@code /fillbiome} sets cells; each slice cleanup clears gets the void back.
 */
public final class InstanceBiomes {
    /**
     * How far past a room its biome reaches, in blocks: vanilla's biome lookup at a block reads a cell up to two
     * blocks away ({@code BiomeManager#getBiome}), so a player beside a wall still reads the run's biome. Within the
     * margin the plot holds and cleanup clears ({@link PlotWork#MARGIN}).
     */
    static final int REACH = 2;
    private static final String PREFIX = "dungeon_";

    private InstanceBiomes() {
    }

    /** The biome of a dungeon type's runs; {@code tools/realmforge_tools/generate/instance_biomes.py} writes it. */
    public static ResourceKey<Biome> biome(Identifier dungeonType) {
        return ResourceKey.create(Registries.BIOME,
                Identifier.fromNamespaceAndPath(dungeonType.getNamespace(), PREFIX + dungeonType.getPath()));
    }

    /** Whether {@code biome} is a dungeon type's biome, {@code realmforge:dungeon_<type>}. */
    public static boolean isRunBiome(ResourceKey<Biome> biome) {
        return biome.identifier().getPath().startsWith(PREFIX)
                && biome.identifier().getNamespace().equals(Realmforge.MOD_ID);
    }

    /** Gives a slice cleanup cleared the void back; the builder calls it after every unit it ran. */
    static void afterUnit(ServerLevel level, PlotWork.Unit unit) {
        if (unit instanceof PlotWork.Clear clear) {
            Holder<Biome> theVoid = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
            LevelChunk chunk = fill(level, clear.slice(), theVoid);
            level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
        }
    }

    /**
     * Gives the run's rooms and {@link #REACH} blocks around them the run's biome, when the run has a dungeon type
     * whose biome is loaded; called when the rooms stand, before anyone enters. The plot holds every chunk this
     * touches.
     */
    static void fillRun(ServerLevel level, Plot plot) {
        Holder<Biome> biome = runBiome(level, plot.dungeonType().orElse(null));
        if (biome == null) {
            return;
        }
        Set<LevelChunk> changed = new LinkedHashSet<>();
        for (BoundingBox room : plot.rooms()) {
            for (BoundingBox slice : PlotWork.chunkSlices(room.inflatedBy(REACH))) {
                changed.add(fill(level, slice, biome));
            }
        }
        level.getChunkSource().chunkMap.resendBiomesForChunks(List.copyOf(changed));
    }

    @Nullable
    private static Holder<Biome> runBiome(ServerLevel level, @Nullable Identifier dungeonType) {
        if (dungeonType == null) {
            return null;
        }
        Optional<Holder.Reference<Biome>> biome =
                level.registryAccess().lookupOrThrow(Registries.BIOME).get(biome(dungeonType));
        return biome.orElse(null);
    }

    /** Sets {@code biome} in every biome cell {@code slice} touches; the slice lies in one chunk column. */
    private static LevelChunk fill(ServerLevel level, BoundingBox slice, Holder<Biome> biome) {
        int minX = QuartPos.fromBlock(slice.minX());
        int minY = QuartPos.fromBlock(slice.minY());
        int minZ = QuartPos.fromBlock(slice.minZ());
        int maxX = QuartPos.fromBlock(slice.maxX());
        int maxY = QuartPos.fromBlock(slice.maxY());
        int maxZ = QuartPos.fromBlock(slice.maxZ());
        LevelChunk chunk = level.getChunk(slice.minX() >> 4, slice.minZ() >> 4);
        chunk.fillBiomesFromNoise((x, y, z, sampler) -> x >= minX && x <= maxX && y >= minY && y <= maxY
                        && z >= minZ && z <= maxZ ? biome : chunk.getNoiseBiome(x, y, z),
                level.getChunkSource().randomState().sampler());
        chunk.markUnsaved();
        return chunk;
    }
}
