package io.github.matejkadlec.realmforge.effect;

import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Our status effects (architecture 11.4): the generated registrations from {@code content/effects/}, their damage and
 * healing over time, and who applied each one.
 */
public final class EffectFeature {
    private EffectFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        GeneratedEffects.register(modBus);
        EffectAttachments.register(modBus);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Added.class, Effects::onEffectAdded);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Remove.class, Effects::onEffectRemoved);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Expired.class, Effects::onEffectExpired);
    }
}
