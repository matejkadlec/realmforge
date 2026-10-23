package io.github.matejkadlec.realmforge.mob;

import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.mob.custom.CasterMob;
import io.github.matejkadlec.realmforge.spell.CastEndedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;

/** Mob levels and scaling, the AI goal library, custom mobs (architecture 14.1, 14.2). */
public final class MobFeature {
    private MobFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        MobAttachments.register(modBus);
        // Custom mobs generated from content/mobs/ (mod/src/generated, never edit by hand), architecture 14.2.
        GeneratedMobs.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, GeneratedMobs::createAttributes);
        NeoForge.EVENT_BUS.addListener(CastEndedEvent.class, CasterMob::onCastEnded);

        NeoForge.EVENT_BUS.addListener(FinalizeSpawnEvent.class, MobLevels::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, MobLevels::onJoinLevel);
        NeoForge.EVENT_BUS.addListener(MobSplitEvent.class, MobLevels::onMobSplit);
        // After NeoForge's own listener has copied the old mob's attachments, the level among them.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, LivingConversionEvent.Post.class, MobLevels::onConversion);
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, MobLevels::onIncomingDamage);
    }
}
