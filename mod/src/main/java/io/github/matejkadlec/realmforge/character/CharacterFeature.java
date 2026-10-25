package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.core.Requirement;
import io.github.matejkadlec.realmforge.core.RequirementTypes;
import io.github.matejkadlec.realmforge.spell.Rage;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Races, classes, character data, leveling, level caps and stat points (architecture 10). */
public final class CharacterFeature {
    private static final DeferredRegister<MapCodec<? extends Requirement>> REQUIREMENT_TYPES =
            RequirementTypes.deferredRegister();

    static {
        // realmforge:level_at_least (architecture 5.3).
        REQUIREMENT_TYPES.register("level_at_least", () -> LevelAtLeast.MAP_CODEC);
    }

    private CharacterFeature() {
    }

    /**
     * Registers the feature's registries and event listeners (leveling's and the stat points' among them), hands
     * {@code stats} the race and class modifiers and those of spent stat points, and {@code spell} a rage class's rage
     * numbers. At common setup, once every attribute is registered, it makes the attributes the character sheet shows
     * sync to the client. Called once from {@code Realmforge}.
     */
    public static void register(IEventBus modBus) {
        CharacterAttachments.register(modBus);
        REQUIREMENT_TYPES.register(modBus);
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, CharacterRegistries::registerDataPackRegistries);
        CharacterStats.addSource(CharacterBonuses.SOURCE);
        CharacterStats.addSource(StatPoints.SOURCE);
        Rage.addSource(holder -> holder instanceof Player player
                ? Characters.mainClass(player).flatMap(playerClass -> playerClass.value().rage())
                : Optional.empty());
        Leveling.register();
        Scrolls.register();
        StatPoints.register();
        modBus.addListener(FMLCommonSetupEvent.class,
                event -> event.enqueueWork(CharacterSheetView::syncShownAttributes));
    }
}
