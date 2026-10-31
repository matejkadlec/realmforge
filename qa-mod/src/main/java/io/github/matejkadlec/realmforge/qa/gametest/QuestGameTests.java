package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import io.github.matejkadlec.realmforge.dungeon.instance.PortalLink;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.progression.ProgressionStats;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedQuestBookExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedQuestBookExpectations.TaskSample;
import io.github.matejkadlec.realmforge.qa.instance.SpikeLayouts;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Casting;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAim;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.monster.zombie.Husk;

/**
 * Q1-2 (RLM-422): our mod reaches FTB Quests only through advancements and statistics (architecture 16, ADR 0006). The
 * pipeline writes an advancement for each sample of our quest tasks into the QA mod's data
 * ({@link GeneratedQuestBookExpectations#TASK_SAMPLES}), as it writes one for a quest's task into the core mod's; these
 * tests complete them through the game's own paths: a level-up, a dungeon run's completion and a cast. They count the
 * dungeons statistic, and compare the quest book FTB Quests loaded in the GameTest server, which loads the pack's mods
 * and gets the pack's config (ADR 0042), with the pipeline's.
 */
public final class QuestGameTests {
    private static final int TIMEOUT = 1200;
    /** The packages of the compile-only pack mods the lean dev client lacks: FTB's, and Apotheosis's with Placebo. */
    private static final List<String> PACK_MOD_PACKAGES = List.of("dev.ftb.", "dev.shadowsoffire.");
    private static final BlockPos PORTAL = new BlockPos(2, 1, 4);

    private QuestGameTests() {
    }

    /** A level task's advancement is done once the character reaches its level or a higher one. */
    @QaGameTest
    public static void levelTasksCompleteOnceTheLevelIsReached(GameTestHelper helper) {
        ServerPlayer player = StatPointsGameTests.loginWithCharacter(helper, "qa-quest-level");
        TaskSample sample = sample(helper, "level", true);
        int level = Integer.parseInt(sample.target());
        AdvancementHolder advancement = advancement(helper, sample);
        Characters.rewardXp(player, Characters.xpToReach(player, level - 1).orElseThrow());
        helper.assertValueEqual(Characters.level(player), level - 1, "the level below the task's");
        helper.assertFalse(isDone(player, advancement), "not done one level below");
        Characters.rewardXp(player, Characters.xpToReach(player, level + 1).orElseThrow()
                - Characters.data(player).totalXp());
        helper.assertValueEqual(Characters.level(player), level + 1, "two levels at once");
        helper.assertTrue(isDone(player, advancement), "done above the task's level too");
        CastGameTests.logout(player);
        helper.succeed();
    }

    /**
     * A completed run counts for each party member online: the statistic and the trigger with the run's dungeon type, so
     * both the dungeon's task and any dungeon's are done. A run of no type (a QA command's) completes only any
     * dungeon's, and a player outside the party gets nothing.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void completedDungeonRunsCountAndCompleteDungeonTasks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        InstanceManager manager = InstanceManager.get(server);
        ServerPlayer player = StatPointsGameTests.loginWithCharacter(helper, "qa-quest-dungeon");
        ServerPlayer outsider = StatPointsGameTests.loginWithCharacter(helper, "qa-quest-outsider");
        TaskSample typed = sample(helper, "dungeon", true);
        AdvancementHolder typedTask = advancement(helper, typed);
        AdvancementHolder anyTask = advancement(helper, sample(helper, "dungeon", false));
        PortalLink link = new PortalLink(Identifier.parse(typed.target()),
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(PORTAL)));
        Plot portalRun = manager.open(SpikeLayouts.spike(), List.of(player.getUUID()), link, TIMEOUT);
        AtomicReference<Plot> untyped = new AtomicReference<>();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(portalRun.state() == PlotState.READY,
                        "plot is " + portalRun.state()))
                .thenExecute(() -> {
                    helper.assertTrue(manager.enter(player, portalRun), "the party member entered");
                    helper.assertValueEqual(completed(player), 0, "no dungeon completed yet");
                    helper.assertTrue(manager.complete(portalRun), "the run completed");
                    helper.assertValueEqual(completed(player), 1, "the statistic counts the run");
                    helper.assertTrue(isDone(player, typedTask), "the run's dungeon type's task is done");
                    helper.assertTrue(isDone(player, anyTask), "and any dungeon's");
                    helper.assertValueEqual(completed(outsider), 0, "a player outside the party gets nothing");
                    helper.assertFalse(isDone(outsider, anyTask), "nor any task");
                    manager.leave(player);
                })
                .thenWaitUntil(() -> helper.assertTrue(portalRun.state() == PlotState.FREE,
                        "plot is " + portalRun.state()))
                .thenExecute(() -> untyped.set(manager.open(SpikeLayouts.spike(), List.of(outsider.getUUID()))))
                .thenWaitUntil(() -> helper.assertTrue(untyped.get().state() == PlotState.ACTIVE,
                        "plot is " + untyped.get().state()))
                .thenExecute(() -> {
                    helper.assertTrue(manager.complete(untyped.get()), "the run of no type completed");
                    helper.assertValueEqual(completed(outsider), 1, "it counts too");
                    helper.assertTrue(isDone(outsider, anyTask), "any dungeon's task is done");
                    helper.assertFalse(isDone(outsider, typedTask), "the typed task is not");
                    manager.leave(outsider);
                })
                .thenWaitUntil(() -> helper.assertTrue(untyped.get().state() == PlotState.FREE,
                        "plot is " + untyped.get().state()))
                .thenExecute(() -> {
                    CastGameTests.logout(player);
                    CastGameTests.logout(outsider);
                })
                .thenSucceed();
    }

    /** An instant spell completes its spell's task and any spell's when it is cast. */
    @QaGameTest
    public static void castingAnInstantSpellCompletesSpellTasks(GameTestHelper helper) {
        ServerPlayer player = CastGameTests.caster(helper, "qa-quest-spell");
        TaskSample named = sample(helper, "spell", true);
        AdvancementHolder namedTask = advancement(helper, named);
        AdvancementHolder anyTask = advancement(helper, sample(helper, "spell", false));
        ResourceKey<Spell> spell = ResourceKey.create(SpellRegistries.SPELL, Identifier.parse(named.target()));
        CastGameTests.learn(helper, player, spell);
        // The sample is the first self-cast spell, a mana or a rage one (Battle Shout): the player has both.
        Resources.set(player, ResourceTypes.RAGE, 100);
        helper.assertFalse(isDone(player, namedTask), "not done before the cast");
        CastResult result = SpellCaster.cast(player, spell, SpellAim.NONE);
        helper.assertTrue(result.success() && !result.begun(), named.target() + " was not cast at once: "
                + result.failure());
        helper.assertTrue(isDone(player, namedTask), "the spell's task is done");
        helper.assertTrue(isDone(player, anyTask), "and any spell's");
        CastGameTests.logout(player);
        helper.succeed();
    }

    /**
     * A cast time completes a spell task when it completes, not when it begins; a cancelled one never does. The test
     * spell {@code test_slow_bolt} takes 2 s.
     */
    @QaGameTest
    public static void aCastTimeCompletesSpellTasksOnlyWhenItCompletes(GameTestHelper helper) {
        ServerPlayer player = CastGameTests.caster(helper, "qa-quest-cast-time");
        ServerPlayer cancelled = CastGameTests.caster(helper, "qa-quest-cancelled");
        Husk husk = CastGameTests.husk(helper, new BlockPos(2, 1, 3));
        AdvancementHolder anyTask = advancement(helper, sample(helper, "spell", false));
        CastGameTests.learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        CastGameTests.learn(helper, cancelled, GeneratedSpells.TEST_SLOW_BOLT);
        CastGameTests.assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk);
        CastGameTests.assertBegun(helper, cancelled, GeneratedSpells.TEST_SLOW_BOLT, husk);
        helper.assertFalse(isDone(player, anyTask), "a cast that only began is not done");
        Spells.cancelCast(cancelled);
        helper.runAfterDelay(50, () -> {
            helper.assertFalse(Casting.isCasting(player), "the cast is over");
            helper.assertTrue(isDone(player, anyTask), "the completed cast completes the task");
            helper.assertFalse(isDone(cancelled, anyTask), "the cancelled one does not");
            CastGameTests.logout(player);
            CastGameTests.logout(cancelled);
            helper.succeed();
        });
    }

    /** The pipeline's custom statistics are registered, and each has its {@code Stat}, which a stat task reads. */
    @QaGameTest
    public static void ourStatisticsAreRegistered(GameTestHelper helper) {
        helper.assertFalse(GeneratedQuestBookExpectations.CUSTOM_STATS.isEmpty(), "no custom statistic was generated");
        for (String name : GeneratedQuestBookExpectations.CUSTOM_STATS) {
            Identifier registered = BuiltInRegistries.CUSTOM_STAT.getValue(Identifier.parse(name));
            helper.assertTrue(registered != null, name + " is not registered");
            helper.assertTrue(Stats.CUSTOM.contains(registered), name + " has no Stat");
        }
        helper.assertTrue(BuiltInRegistries.CUSTOM_STAT.getValue(ProgressionStats.DUNGEONS_COMPLETED)
                == ProgressionStats.DUNGEONS_COMPLETED, "the registry holds the instance the game counts by");
        helper.succeed();
    }

    /**
     * The quest book FTB Quests loaded is the pipeline's: the same chapters, each with its quests, and each quest with
     * its dependencies, tasks and rewards, by id and type, every reward per player and auto-claimed as the pipeline
     * says (architecture 16). While {@code content/quests/} is empty, no chapter is loaded at all. The comparison and
     * its FTB Quests types live in {@link QuestBook}.
     */
    @QaGameTest
    public static void theLoadedQuestBookMatchesThePipeline(GameTestHelper helper) {
        QuestBook.assertMatchesThePipeline(helper);
        helper.succeed();
    }

    /**
     * No GameTest class names a type of a compile-only pack mod (FTB Quests, FTB Library, FTB Teams; Apotheosis,
     * Apothic Attributes, Placebo) in its methods' signatures, lambdas included: the QA mod lists those methods when it
     * is constructed ({@link QaGameTests}), which resolves those types, and the lean dev client ({@code -PleanClient})
     * has none of these mods, so it would not start. Such a type belongs in a helper class that a test calls, as
     * {@link QuestBook} and {@link ApotheosisLoot} are.
     */
    @QaGameTest
    public static void gameTestClassesNameNoPackModTypeInTheirMethods(GameTestHelper helper) {
        for (Class<?> holder : QaGameTests.holders()) {
            for (Method method : holder.getDeclaredMethods()) {
                List<Class<?>> types = new ArrayList<>(List.of(method.getParameterTypes()));
                types.add(method.getReturnType());
                for (Class<?> type : types) {
                    helper.assertFalse(PACK_MOD_PACKAGES.stream().anyMatch(type.getTypeName()::startsWith),
                            holder.getSimpleName() + "#" + method.getName() + " names " + type.getTypeName());
                }
            }
        }
        helper.succeed();
    }

    /** The sample of a kind with a target (a level always has one), or the one for any. */
    private static TaskSample sample(GameTestHelper helper, String kind, boolean targeted) {
        return GeneratedQuestBookExpectations.TASK_SAMPLES.stream()
                .filter(sample -> sample.kind().equals(kind) && sample.target().isEmpty() != targeted)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no " + kind + " task sample was generated"));
    }

    private static AdvancementHolder advancement(GameTestHelper helper, TaskSample sample) {
        AdvancementHolder advancement = helper.getLevel().getServer().getAdvancements()
                .get(Identifier.parse(sample.advancement()));
        helper.assertTrue(advancement != null, "the sample advancement " + sample.advancement() + " is not loaded");
        return advancement;
    }

    private static boolean isDone(ServerPlayer player, AdvancementHolder advancement) {
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static int completed(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(ProgressionStats.DUNGEONS_COMPLETED));
    }
}
