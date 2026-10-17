package io.github.matejkadlec.realmforge.core;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Keys of the registries {@code core} owns (architecture 5.4). */
public final class CoreRegistries {
    /** {@code realmforge:curve}: curves from {@code content/curves/}, synced to clients. */
    public static final ResourceKey<Registry<Curve>> CURVE = ResourceKey.createRegistryKey(Realmforge.id("curve"));

    /** {@code realmforge:requirement_type}: what a player needs for something ({@link Requirement}). */
    public static final ResourceKey<Registry<MapCodec<? extends Requirement>>> REQUIREMENT_TYPE =
            ResourceKey.createRegistryKey(Realmforge.id("requirement_type"));

    /**
     * The registry behind {@link #REQUIREMENT_TYPE}, filled from Java at game start. Synced, so a synced registry may
     * hold requirements (a talent's, later).
     */
    public static final Registry<MapCodec<? extends Requirement>> REQUIREMENT_TYPES =
            new RegistryBuilder<>(REQUIREMENT_TYPE).sync(true).create();

    private CoreRegistries() {
    }

    static void registerTypeRegistries(NewRegistryEvent event) {
        event.register(REQUIREMENT_TYPES);
    }

    static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(CURVE, Curve.CODEC, Curve.CODEC);
    }
}
