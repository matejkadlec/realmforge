package io.github.matejkadlec.realmforge.boss;

import io.github.matejkadlec.realmforge.mob.custom.Archetypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/** Boss definitions, the phase machine, the boss entity and its boss bar ({@link BossBar}) (architecture 14.3). */
public final class BossFeature {
    /** The mob archetype of a boss's body in {@code content/mobs/}. */
    public static final String ARCHETYPE = "boss";

    private BossFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(NewRegistryEvent.class, BossRegistries::registerTypeRegistries);
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, BossRegistries::registerDataPackRegistries);
        PhaseTriggerTypes.register(modBus);
        // A boss's body is a custom mob of archetype boss: GeneratedMobs creates it through mob's Archetypes.
        Archetypes.provide(ARCHETYPE, BossMob::create);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Applicable.class, BossFeature::onEffectApplicable);
    }

    /** A boss never takes the status effects it is immune to (RLM-539: Frozen, Stunned, boss by boss). */
    private static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!event.getEntity().level().isClientSide()
                && Bosses.of(event.getEntity().registryAccess(), event.getEntity().getType())
                        .filter(boss -> boss.value().isImmuneTo(event.getEffectInstance().getEffect())).isPresent()) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
