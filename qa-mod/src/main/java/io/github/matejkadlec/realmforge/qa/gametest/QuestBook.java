package io.github.matejkadlec.realmforge.qa.gametest;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedQuestBookExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedQuestBookExpectations.ChapterExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedQuestBookExpectations.QuestExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedQuestBookExpectations.RewardExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedQuestBookExpectations.TaskExpectation;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Compares the quest book FTB Quests loaded with the pipeline's ({@link QuestGameTests}). Every FTB Quests type of the
 * QA mod's quest tests lives here, out of the GameTest classes: the QA mod lists their declared methods when it is
 * constructed ({@link QaGameTests}), which resolves the types in their signatures, lambdas included, and the lean dev
 * client ({@code -PleanClient}) runs without FTB Quests. This class loads only when its test runs, in the GameTest
 * server, which loads the pack's mods.
 */
final class QuestBook {
    private QuestBook() {
    }

    /**
     * The loaded book has the pipeline's chapters, each with its quests, and each quest its dependencies, tasks and
     * rewards, by id and type, every reward's team flag and auto-claim as FTB Quests resolves them.
     */
    static void assertMatchesThePipeline(GameTestHelper helper) {
        helper.assertTrue(ServerQuestFile.exists(), "FTB Quests loaded no quest book");
        Map<String, Chapter> chapters = byCode(ServerQuestFile.getInstance().getAllChapters());
        Map<String, ChapterExpectation> expected = GeneratedQuestBookExpectations.CHAPTERS.stream()
                .collect(Collectors.toMap(ChapterExpectation::code, Function.identity()));
        helper.assertValueEqual(chapters.keySet(), expected.keySet(), "the loaded chapters");
        for (ChapterExpectation chapter : GeneratedQuestBookExpectations.CHAPTERS) {
            Map<String, Quest> quests = byCode(chapters.get(chapter.code()).getQuests());
            helper.assertValueEqual(quests.keySet(), chapter.quests().stream().map(QuestExpectation::code)
                    .collect(Collectors.toSet()), "the quests of " + chapter.id());
            for (QuestExpectation quest : chapter.quests()) {
                assertQuest(helper, quests.get(quest.code()), quest);
            }
        }
    }

    private static void assertQuest(GameTestHelper helper, Quest loaded, QuestExpectation quest) {
        helper.assertValueEqual(loaded.isOptional(), quest.optional(), quest.id() + " optional");
        helper.assertValueEqual(loaded.streamDependencies().map(object -> object.getCodeString()).sorted().toList(),
                quest.dependencies().stream().sorted().toList(), quest.id() + "'s dependencies");
        Map<String, String> tasks = loaded.getTasks().stream().collect(Collectors.toMap(task -> task.getCodeString(),
                task -> task.getType().getTypeForSerialization()));
        helper.assertValueEqual(tasks, quest.tasks().stream().collect(Collectors.toMap(TaskExpectation::code,
                TaskExpectation::type)), quest.id() + "'s tasks");
        Map<String, Reward> rewards = byCode(loaded.getRewards());
        helper.assertValueEqual(rewards.keySet(), quest.rewards().stream().map(RewardExpectation::code)
                .collect(Collectors.toSet()), quest.id() + "'s rewards");
        for (RewardExpectation expected : quest.rewards()) {
            Reward reward = rewards.get(expected.code());
            String name = quest.id() + "'s reward " + expected.code();
            helper.assertValueEqual(reward.getType().getTypeForSerialization(), expected.type(), name + " type");
            helper.assertValueEqual(reward.isTeamReward(), expected.team(), name + " is a team reward");
            helper.assertValueEqual(reward.getAutoClaimType().getId(), expected.autoClaim(), name + " auto-claim");
        }
    }

    private static <T extends QuestObjectBase> Map<String, T> byCode(Collection<T> objects) {
        // A lambda: QuestObjectBase::getCodeString would also name its static getCodeString(QuestObjectBase).
        return objects.stream().collect(Collectors.toMap(object -> object.getCodeString(), Function.identity()));
    }
}
