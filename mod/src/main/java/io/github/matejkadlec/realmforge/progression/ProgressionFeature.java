package io.github.matejkadlec.realmforge.progression;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.core.Requirement;
import io.github.matejkadlec.realmforge.core.RequirementTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Chapters, criteria triggers and statistics: the bridge to quests (architecture 16). */
public final class ProgressionFeature {
    private static final DeferredRegister<MapCodec<? extends Requirement>> REQUIREMENT_TYPES =
            RequirementTypes.deferredRegister();

    static {
        // realmforge:chapter_completed: content a chapter unlocks names the chapter (architecture 5.3, 16).
        REQUIREMENT_TYPES.register("chapter_completed", () -> ChapterCompleted.MAP_CODEC);
    }

    private ProgressionFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, ProgressionRegistries::registerDataPackRegistries);
        ProgressionTriggers.register(modBus);
        REQUIREMENT_TYPES.register(modBus);
    }
}
