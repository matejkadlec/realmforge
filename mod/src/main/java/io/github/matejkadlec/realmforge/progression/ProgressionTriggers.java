package io.github.matejkadlec.realmforge.progression;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Our criteria triggers, which complete advancements generated from content (architecture 16). */
public final class ProgressionTriggers {
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, Realmforge.MOD_ID);

    /** {@code realmforge:level_reached}: a player's character level changed, or they logged in. */
    public static final DeferredHolder<CriterionTrigger<?>, LevelReachedTrigger> LEVEL_REACHED =
            TRIGGERS.register("level_reached", LevelReachedTrigger::new);

    private ProgressionTriggers() {
    }

    static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }
}
