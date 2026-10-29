package io.github.matejkadlec.realmforge.combat;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * The damage pipeline: spell schools, crit, dodge, resistances, our armor formula, armor pierce, lifesteal (and those
 * stats on mobs), overheal, health regeneration, the magic shield, combat text, damage over time's appliers, and
 * blocking with a two-handed weapon (architecture 9).
 */
public final class CombatFeature {
    private CombatFeature() {
    }

    /** Registers the feature's registries, config and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus, ModContainer container) {
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, CombatRegistries::registerDataPackRegistries);
        CombatConfig.register(container);
        CombatAttachments.register(modBus);
        modBus.addListener(EntityAttributeModificationEvent.class, MobCombatStats::addToMobs);
        DamagePipeline.register();
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, Overheal::onServerTick);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, Overheal::onLogin);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Added.class, Overheal::onEffectAdded);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Added.class, DamageOverTime::onEffectAdded);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Remove.class,
                DamageOverTime::onEffectRemoved);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Expired.class,
                DamageOverTime::onEffectExpired);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, HealthRegeneration::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, MagicShields::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Added.class, MagicShields::onEffectAdded);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, WeaponBlocks::onServerTick);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, WeaponBlocks::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingEntityUseItemEvent.Start.class,
                WeaponBlocks::onUseStart);
        NeoForge.EVENT_BUS.addListener(LivingEntityUseItemEvent.Stop.class, WeaponBlocks::onUseStop);
    }
}
