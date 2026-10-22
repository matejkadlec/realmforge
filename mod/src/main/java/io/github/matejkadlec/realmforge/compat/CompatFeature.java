package io.github.matejkadlec.realmforge.compat;

import io.github.matejkadlec.realmforge.compat.apothic_attributes.ApothicAttributesCompat;
import io.github.matejkadlec.realmforge.compat.bettercombat.TwoHandedWeapons;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/** Optional integrations, one subpackage per mod; their client parts live in {@code client/compat/} (architecture 19.4). */
public final class CompatFeature {
    private CompatFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        if (ModList.get().isLoaded(ApothicAttributesCompat.MOD_ID)) {
            ApothicAttributesCompat.register(modBus);
        }
        if (ModList.get().isLoaded(TwoHandedWeapons.MOD_ID)) {
            TwoHandedWeapons.register();
        }
    }
}
