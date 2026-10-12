package io.github.matejkadlec.realmforge.core;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Keys of the registries {@code core} owns (architecture 5.4). */
public final class CoreRegistries {
    /** {@code realmforge:curve}: curves from {@code content/curves/}, synced to clients. */
    public static final ResourceKey<Registry<Curve>> CURVE = ResourceKey.createRegistryKey(Realmforge.id("curve"));

    private CoreRegistries() {
    }

    static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(CURVE, Curve.CODEC, Curve.CODEC);
    }
}
