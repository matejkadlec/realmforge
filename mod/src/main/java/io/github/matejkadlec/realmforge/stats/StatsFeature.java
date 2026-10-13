package io.github.matejkadlec.realmforge.stats;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Attributes, entity levels, stat sources, derived stats and caps (architecture 8). */
public final class StatsFeature {
    private StatsFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        // Attributes generated from content/stats/ (mod/src/generated, never edit by hand).
        GeneratedStats.register(modBus);
        StatsAttachments.register(modBus);
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, StatsRegistries::registerDataPackRegistries);
        modBus.addListener(EntityAttributeModificationEvent.class, StatsFeature::addToPlayers);

        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, CharacterStats::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.Clone.class, CharacterStats::onClone);
        NeoForge.EVENT_BUS.addListener(LivingEquipmentChangeEvent.class, CharacterStats::onEquipmentChange);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Added.class, CharacterStats::onEffectChange);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Remove.class, CharacterStats::onEffectChange);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Expired.class, CharacterStats::onEffectChange);
    }

    /** Every stat goes on players; mobs get the ones they need with the features that use them. */
    private static void addToPlayers(EntityAttributeModificationEvent event) {
        for (Holder<Attribute> stat : GeneratedStats.ALL) {
            event.add(EntityType.PLAYER, stat);
        }
    }
}
