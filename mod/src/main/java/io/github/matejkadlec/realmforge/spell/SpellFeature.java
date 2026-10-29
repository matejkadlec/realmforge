package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.combat.CombatEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/** Spells, effect and targeting types, casting, cooldowns, resources, procs and spell entities (architecture 11). */
public final class SpellFeature {
    private SpellFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(NewRegistryEvent.class, SpellRegistries::registerTypeRegistries);
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, SpellRegistries::registerDataPackRegistries);
        ResourceTypes.register(modBus);
        SpellEffectTypes.register(modBus);
        TargetingTypes.register(modBus);
        SpellEntities.register(modBus);
        SpellAttachments.register(modBus);
        Casting.register();
        AimAssist.register();
        TargetHighlight.register();
        NeoForge.EVENT_BUS.addListener(CombatEvent.Hit.class, Rage::onHit);
        NeoForge.EVENT_BUS.addListener(CombatEvent.Hit.class, Procs::onHit);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDeathEvent.class, Procs::onDeath);
        // After the stats recompute of the same tick (StatsFeature registers first), so a pool sees its maximum.
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, Resources::onPlayerTick);
    }
}
