package io.github.matejkadlec.realmforge.core;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.Realmforge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The requirement types {@code core} registers (architecture 5.3): {@code has_advancement}. The features that know
 * the other facts register theirs ({@code character/LevelAtLeast}, {@code progression/ChapterCompleted}).
 */
public final class RequirementTypes {
    private static final DeferredRegister<MapCodec<? extends Requirement>> TYPES =
            DeferredRegister.create(CoreRegistries.REQUIREMENT_TYPES, Realmforge.MOD_ID);

    /** {@code realmforge:has_advancement} ({@link HasAdvancement}). */
    public static final DeferredHolder<MapCodec<? extends Requirement>, MapCodec<HasAdvancement>> HAS_ADVANCEMENT =
            TYPES.register("has_advancement", () -> HasAdvancement.MAP_CODEC);

    private RequirementTypes() {
    }

    /** A {@code DeferredRegister} for a feature's own requirement types, in our namespace. */
    public static DeferredRegister<MapCodec<? extends Requirement>> deferredRegister() {
        return DeferredRegister.create(CoreRegistries.REQUIREMENT_TYPES, Realmforge.MOD_ID);
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
