package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.combat.CombatEvent;
import io.github.matejkadlec.realmforge.combat.WeaponBlocks;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/**
 * Spells, effect and targeting types, casting, cooldowns, resources, procs, passive spells and spell entities
 * (architecture 11).
 */
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
        CharacterStats.addSource(Passives.SOURCE);
        WeaponBlocks.cooldownReductionBy(Passives::blockCooldownReductionTicks);
        Casting.register();
        AimAssist.register();
        TargetHighlight.register();
        NeoForge.EVENT_BUS.addListener(CombatEvent.Hit.class, Rage::onHit);
        NeoForge.EVENT_BUS.addListener(CombatEvent.Hit.class, Procs::onHit);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDeathEvent.class, Procs::onDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Added.class, Procs::onEffectAdded);
        Procs.addSource(Passives::procs);
        NeoForge.EVENT_BUS.addListener(CombatEvent.Block.class, Windows::onBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDeathEvent.class, Windows::onDeath);
        // After the stats recompute of the same tick (StatsFeature registers first), so a pool sees its maximum.
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, Resources::onPlayerTick);
    }
}
