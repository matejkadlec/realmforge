package io.github.matejkadlec.realmforge.dungeon.instance;

import java.util.List;
import java.util.Optional;
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
 * the type (RLM-409, architecture 15.5): Traveler's Titles shows the biome's name when a player arrives, and Xaero's
 * Minimap shows it as the biome. The instance dimension's generator fills it with {@code minecraft:the_void}; placing a
 * room gives the room the run's biome and clearing gives the void back, one unit's slice (one chunk column) at a time,
 * the way {@code /fillbiome} does: every 4 x 4 x 4 biome cell the slice touches, then the chunk's biomes go to the
 * players who see it.
 */
public final class InstanceBiomes {
    private static final String PREFIX = "dungeon_";

    private InstanceBiomes() {
    }

    /** The biome of a dungeon type's runs; {@code tools/realmforge_tools/generate/instance_biomes.py} writes it. */
    public static ResourceKey<Biome> biome(Identifier dungeonType) {
        return ResourceKey.create(Registries.BIOME,
                Identifier.fromNamespaceAndPath(dungeonType.getNamespace(), PREFIX + dungeonType.getPath()));
    }

    /**
     * Gives the unit's slice its biome after the builder ran it: a placed room the run's biome, when the run has a
     * dungeon type whose biome is loaded, and a cleared box the void.
     */
    static void apply(ServerLevel level, Plot plot, PlotWork.Unit unit) {
        switch (unit) {
            case PlotWork.Place place -> {
                Holder<Biome> biome = runBiome(level, plot.dungeonType().orElse(null));
                if (biome != null) {
                    fill(level, place.slice(), biome);
                }
            }
            case PlotWork.Clear clear ->
                    fill(level, clear.slice(), level.registryAccess().lookupOrThrow(Registries.BIOME)
                            .getOrThrow(Biomes.THE_VOID));
            case PlotWork.DiscardEntities discard -> {
            }
        }
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

    /** Sets {@code biome} in every biome cell of {@code slice}, which lies in one chunk column. */
    private static void fill(ServerLevel level, BoundingBox slice, Holder<Biome> biome) {
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
        level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
    }
}
