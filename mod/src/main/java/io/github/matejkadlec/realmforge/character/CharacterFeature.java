package io.github.matejkadlec.realmforge.character;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Races, classes, character data, leveling and level caps (architecture 10). */
public final class CharacterFeature {
    private CharacterFeature() {
    }

    /**
     * Registers the feature's registries and event listeners. At common setup, once every attribute is registered,
     * it makes the attributes the character sheet shows sync to the client. Called once from {@code Realmforge}.
     */
    public static void register(IEventBus modBus) {
        CharacterAttachments.register(modBus);
        modBus.addListener(FMLCommonSetupEvent.class,
                event -> event.enqueueWork(CharacterSheetView::syncShownAttributes));
    }
}
