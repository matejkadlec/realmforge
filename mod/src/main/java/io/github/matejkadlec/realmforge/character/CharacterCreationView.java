package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/**
 * What the character creation screen offers (RLM-22; architecture 18: a thin screen draws a view model computed in
 * common code): every race in id order, each with the classes it allows in its own order. Each option carries its
 * name, description and the bonuses it gives at level 1 ("+5 Strength (+1 per Level)"); a class also its resource and
 * starting kit. Built from the synced registries, so the client screen and the GameTests read the same options.
 *
 * @param races the races to choose from; empty only when the data has none
 */
public record CharacterCreationView(List<RaceOption> races) {
    /** A race to choose, with the classes it allows. */
    public record RaceOption(ResourceKey<Race> key, Component name, Component description, List<Component> bonuses,
            List<ClassOption> classes) {
    }

    /** A class to choose. */
    public record ClassOption(ResourceKey<PlayerClass> key, Component name, Component description, Component resource,
            List<Component> bonuses, List<Component> startingKit) {
    }

    /** The options from the registries (the client's synced ones, or the server's). */
    public static CharacterCreationView of(HolderLookup.Provider registries) {
        List<RaceOption> races = new ArrayList<>();
        registries.lookupOrThrow(CharacterRegistries.RACE).listElements()
                .sorted(Comparator.comparing(race -> race.key().identifier().toString()))
                .forEach(race -> races.add(race(race.key(), race.value())));
        return new CharacterCreationView(List.copyOf(races));
    }

    private static RaceOption race(ResourceKey<Race> key, Race race) {
        List<ClassOption> classes = new ArrayList<>();
        for (Holder<PlayerClass> playerClass : race.classes()) {
            playerClass.unwrapKey().ifPresent(classKey -> classes.add(playerClass(classKey, playerClass.value())));
        }
        return new RaceOption(key, Component.translatable(race.name()), Component.translatable(race.description()),
                bonuses(race.modifiers()), List.copyOf(classes));
    }

    private static ClassOption playerClass(ResourceKey<PlayerClass> key, PlayerClass playerClass) {
        List<Component> kit = new ArrayList<>();
        for (KitItem item : playerClass.startingKit()) {
            Component name = new ItemStack(item.item()).getHoverName();
            kit.add(item.count() == 1 ? name : Component.translatable(GeneratedTexts.GUI_KIT_ITEM, name, item.count()));
        }
        Component resource = Component.translatable(GeneratedTexts.GUI_CLASS_RESOURCE,
                Component.translatable(playerClass.resource().value().name()));
        return new ClassOption(key, Component.translatable(playerClass.name()),
                Component.translatable(playerClass.description()), resource, bonuses(playerClass.modifiers()),
                List.copyOf(kit));
    }

    /** Each modifier at level 1, with its growth: "+5 Strength (+1 per Level)", or "+1 Dexterity". */
    static List<Component> bonuses(List<CharacterModifier> modifiers) {
        List<Component> lines = new ArrayList<>();
        for (CharacterModifier modifier : modifiers) {
            Component bonus = Component.translatable(GeneratedTexts.GUI_STAT_BONUS,
                    CharacterSheetView.signed(modifier.attribute(), modifier.operation(), modifier.amount()),
                    CharacterSheetView.name(modifier.attribute()));
            lines.add(modifier.perLevel() == 0 ? bonus : Component.translatable(GeneratedTexts.GUI_STAT_GROWTH, bonus,
                    CharacterSheetView.signed(modifier.attribute(), modifier.operation(), modifier.perLevel())));
        }
        return List.copyOf(lines);
    }
}
