package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.stats.CharacterStats;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Races, classes, character data, leveling, level caps and stat points (architecture 10). */
public final class CharacterFeature {
    private CharacterFeature() {
    }

    /**
     * Registers the feature's registries and event listeners (leveling's and the stat points' among them), and hands
     * {@code stats} the race and class modifiers and those of spent stat points. At common setup, once every attribute
     * is registered, it makes the attributes the character sheet shows sync to the client. Called once from
     * {@code Realmforge}.
     */
    public static void register(IEventBus modBus) {
        CharacterAttachments.register(modBus);
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, CharacterRegistries::registerDataPackRegistries);
        CharacterStats.addSource(CharacterBonuses.SOURCE);
        CharacterStats.addSource(StatPoints.SOURCE);
        Leveling.register();
        StatPoints.register();
        modBus.addListener(FMLCommonSetupEvent.class,
                event -> event.enqueueWork(CharacterSheetView::syncShownAttributes));
    }
}
