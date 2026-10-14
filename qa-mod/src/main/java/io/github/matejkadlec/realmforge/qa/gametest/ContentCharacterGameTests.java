package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.CharacterBonuses;
import io.github.matejkadlec.realmforge.character.CharacterRegistries;
import io.github.matejkadlec.realmforge.character.KitItem;
import io.github.matejkadlec.realmforge.character.PlayerClass;
import io.github.matejkadlec.realmforge.character.Race;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedClassExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedClassExpectations.ClassExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRaceExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRaceExpectations.RaceExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations.Bonus;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * Checks that every race in {@code content/races/} and every class in {@code content/classes/} reached their datapack
 * registries as the content says, and that their modifiers give the bonuses the pipeline computes at sample levels
 * ({@link GeneratedRaceExpectations}, {@link GeneratedClassExpectations}, the pattern of architecture 6.5).
 */
public final class ContentCharacterGameTests {
    private ContentCharacterGameTests() {
    }

    @QaGameTest
    public static void contentRacesLoadAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedRaceExpectations.RACES.isEmpty(), "no race expectations were generated");
        Registry<Race> races = helper.getLevel().registryAccess().lookupOrThrow(CharacterRegistries.RACE);
        Set<String> expectedIds = new TreeSet<>();
        GeneratedRaceExpectations.RACES.forEach(expected -> expectedIds.add(expected.id()));
        helper.assertValueEqual(ours(races), expectedIds, "races in realmforge:race");

        for (RaceExpectation expected : GeneratedRaceExpectations.RACES) {
            Identifier id = Identifier.parse(expected.id());
            Race race = races.getValue(id);
            helper.assertTrue(race != null, "race is not loaded: " + expected.id());
            helper.assertValueEqual(race.name(), expected.nameKey(), expected.id() + " name");
            helper.assertValueEqual(race.description(), expected.descriptionKey(), expected.id() + " description");
            helper.assertValueEqual(race.classes().stream().map(c -> c.getRegisteredName()).toList(),
                    expected.classes(), expected.id() + " classes");
            for (GeneratedRaceExpectations.Sample sample : expected.samples()) {
                ContentStatGameTests.assertBonuses(helper, CharacterBonuses.bonuses(CharacterBonuses.RACE_PREFIX, id,
                                race.modifiers(), sample.level()), sample.bonuses().stream()
                                .map(b -> new Bonus(b.id(), b.attribute(), b.operation(), b.amount())).toList(),
                        expected.id() + " at level " + sample.level());
            }
        }
        helper.succeed();
    }

    @QaGameTest
    public static void contentClassesLoadAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedClassExpectations.CLASSES.isEmpty(), "no class expectations were generated");
        Registry<PlayerClass> classes = helper.getLevel().registryAccess().lookupOrThrow(CharacterRegistries.CLASS);
        Set<String> expectedIds = new TreeSet<>();
        GeneratedClassExpectations.CLASSES.forEach(expected -> expectedIds.add(expected.id()));
        helper.assertValueEqual(ours(classes), expectedIds, "classes in realmforge:class");

        for (ClassExpectation expected : GeneratedClassExpectations.CLASSES) {
            Identifier id = Identifier.parse(expected.id());
            PlayerClass playerClass = classes.getValue(id);
            helper.assertTrue(playerClass != null, "class is not loaded: " + expected.id());
            helper.assertValueEqual(playerClass.name(), expected.nameKey(), expected.id() + " name");
            helper.assertValueEqual(playerClass.description(), expected.descriptionKey(),
                    expected.id() + " description");
            helper.assertValueEqual(playerClass.resource().getRegisteredName(), expected.resource(),
                    expected.id() + " resource");
            List<String> kit = playerClass.startingKit().stream().map(ContentCharacterGameTests::kitItem).toList();
            List<String> expectedKit = expected.startingKit().stream()
                    .map(item -> item.item() + " x" + item.count()).toList();
            helper.assertValueEqual(kit, expectedKit, expected.id() + " starting kit");
            for (GeneratedClassExpectations.Sample sample : expected.samples()) {
                ContentStatGameTests.assertBonuses(helper, CharacterBonuses.bonuses(CharacterBonuses.CLASS_PREFIX, id,
                                playerClass.modifiers(), sample.level()), sample.bonuses().stream()
                                .map(b -> new Bonus(b.id(), b.attribute(), b.operation(), b.amount())).toList(),
                        expected.id() + " at level " + sample.level());
            }
        }
        helper.succeed();
    }

    /** The resource types the pipeline lets a class name ({@code RESOURCE_TYPES}) are the ones the game registers. */
    @QaGameTest
    public static void resourceTypesAreRegistered(GameTestHelper helper) {
        Set<String> registered = new TreeSet<>();
        SpellRegistries.RESOURCE_TYPES.keySet().forEach(id -> registered.add(id.toString()));
        helper.assertValueEqual(registered, new TreeSet<>(Set.of(Realmforge.MOD_ID + ":mana",
                Realmforge.MOD_ID + ":rage")), "realmforge:resource_type");
        helper.succeed();
    }

    private static String kitItem(KitItem item) {
        return item.item().getRegisteredName() + " x" + item.count();
    }

    private static Set<String> ours(Registry<?> registry) {
        Set<String> ids = new TreeSet<>();
        registry.keySet().stream().filter(id -> id.getNamespace().equals(Realmforge.MOD_ID))
                .forEach(id -> ids.add(id.toString()));
        return ids;
    }
}
