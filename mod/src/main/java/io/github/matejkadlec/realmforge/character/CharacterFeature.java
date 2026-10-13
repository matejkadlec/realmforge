package io.github.matejkadlec.realmforge.character;

import net.neoforged.bus.api.IEventBus;

/** Races, classes, character data, leveling and level caps (architecture 10). */
public final class CharacterFeature {
    private CharacterFeature() {
    }

    /**
     * Registers the feature's registries and event listeners, and makes the attributes the character sheet shows sync
     * to the client. Called once from {@code Realmforge}.
     */
    public static void register(IEventBus modBus) {
        CharacterAttachments.register(modBus);
        CharacterSheetView.syncShownAttributes();
    }
}
