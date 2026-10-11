package io.github.matejkadlec.realmforge.talent;

import net.neoforged.bus.api.IEventBus;

/** Talent trees, points and spell modifiers; glyphs later (architecture 12). */
public final class TalentFeature {
    private TalentFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
    }
}
