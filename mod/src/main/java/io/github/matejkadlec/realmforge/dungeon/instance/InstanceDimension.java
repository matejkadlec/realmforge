package io.github.matejkadlec.realmforge.dungeon.instance;

import io.github.matejkadlec.realmforge.Realmforge;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The technical dimension {@code realmforge:instances} that hosts every dungeon instance in its own plot
 * (architecture 15.5). It is a void: no terrain, no natural spawning, fixed time. Defined by
 * {@code data/realmforge/dimension/instances.json} and {@code dimension_type/instances.json}.
 */
public final class InstanceDimension {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Realmforge.id("instances"));

    private InstanceDimension() {
    }

    /** The instance level, or {@code null} when the world has no such dimension (its data pack is missing). */
    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    public static boolean isInstance(Level level) {
        return level.dimension() == LEVEL;
    }
}
