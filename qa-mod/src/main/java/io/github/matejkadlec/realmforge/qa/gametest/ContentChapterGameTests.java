package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.progression.Chapter;
import io.github.matejkadlec.realmforge.progression.Chapters;
import io.github.matejkadlec.realmforge.progression.LevelCaps;
import io.github.matejkadlec.realmforge.progression.ProgressionRegistries;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedChapterExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedChapterExpectations.CapSample;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedChapterExpectations.ChapterExpectation;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * C2 (RLM-23): every chapter in {@code content/chapters/} reached the datapack registry {@code realmforge:chapter} as
 * written, its advancement exists, and the game's level cap rule ({@link LevelCaps}) agrees with the pipeline's for
 * the generated samples ({@link GeneratedChapterExpectations}).
 */
public final class ContentChapterGameTests {
    private ContentChapterGameTests() {
    }

    @QaGameTest
    public static void contentChaptersAreLoadedAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedChapterExpectations.CHAPTERS.isEmpty(), "no chapter expectations were generated");
        Registry<Chapter> chapters = registry(helper);
        Set<String> expectedIds = new TreeSet<>();
        GeneratedChapterExpectations.CHAPTERS.forEach(expected -> expectedIds.add(expected.id()));
        Set<String> loadedIds = new TreeSet<>();
        chapters.keySet().stream()
                .filter(id -> id.getNamespace().equals(Realmforge.MOD_ID))
                .forEach(id -> loadedIds.add(id.toString()));
        helper.assertValueEqual(loadedIds, expectedIds, "chapters in realmforge:chapter");

        for (ChapterExpectation expected : GeneratedChapterExpectations.CHAPTERS) {
            Chapter chapter = chapters.getValue(Identifier.parse(expected.id()));
            helper.assertTrue(chapter != null, "chapter is not loaded: " + expected.id());
            helper.assertValueEqual(chapter, new Chapter(expected.nameKey(), expected.order(), expected.levelCap(),
                    expected.completedLevelCap()), expected.id());
            helper.assertTrue(Language.getInstance().has(expected.nameKey()),
                    expected.id() + ": no en_us text for " + expected.nameKey());
            ResourceKey<Chapter> key = ResourceKey.create(ProgressionRegistries.CHAPTER, Identifier.parse(expected.id()));
            helper.assertValueEqual(Chapters.advancementId(key).toString(), expected.advancement(),
                    expected.id() + " advancement id");
            helper.assertValueEqual(Chapters.ofAdvancement(Identifier.parse(expected.advancement())),
                    Optional.of(key), expected.advancement() + " names its chapter");
            AdvancementHolder advancement = helper.getLevel().getServer().getAdvancements()
                    .get(Identifier.parse(expected.advancement()));
            helper.assertTrue(advancement != null, "advancement is not loaded: " + expected.advancement());
            helper.assertTrue(advancement.value().display().isEmpty(), expected.advancement() + " has no display");
        }
        helper.succeed();
    }

    @QaGameTest
    public static void levelCapsMatchThePipeline(GameTestHelper helper) {
        Registry<Chapter> chapters = registry(helper);
        helper.assertFalse(GeneratedChapterExpectations.CAPS.isEmpty(), "no cap samples were generated");
        for (CapSample sample : GeneratedChapterExpectations.CAPS) {
            Set<Chapter> completed = new HashSet<>();
            sample.completed().forEach(id -> completed.add(chapters.getValue(Identifier.parse(id))));
            int cap = LevelCaps.cap(chapters.stream().toList(), completed::contains);
            helper.assertValueEqual(cap, sample.cap(), "the cap with " + sample.completed() + " completed");
        }
        helper.succeed();
    }

    private static Registry<Chapter> registry(GameTestHelper helper) {
        return helper.getLevel().registryAccess().lookupOrThrow(ProgressionRegistries.CHAPTER);
    }
}
