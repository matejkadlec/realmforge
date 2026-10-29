package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.boss.Boss;
import io.github.matejkadlec.realmforge.boss.BossAbility;
import io.github.matejkadlec.realmforge.boss.BossMob;
import io.github.matejkadlec.realmforge.boss.BossPhase;
import io.github.matejkadlec.realmforge.boss.BossRegistries;
import io.github.matejkadlec.realmforge.boss.Bosses;
import io.github.matejkadlec.realmforge.boss.HealthBelow;
import io.github.matejkadlec.realmforge.boss.PhaseTrigger;
import io.github.matejkadlec.realmforge.boss.TimeInPhase;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedBossExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedBossExpectations.AbilityExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedBossExpectations.BossExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedBossExpectations.PhaseExpectation;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.Tags;

/**
 * W5a (RLM-490): every entry of {@code content/bosses/} reached the datapack registry {@code realmforge:boss} as
 * written ({@link GeneratedBossExpectations}), its body is a {@link BossMob} in {@code c:bosses} that finds it, and
 * every phase's name has its text.
 */
public final class ContentBossGameTests {
    private static final double TOLERANCE = 1e-6;

    private ContentBossGameTests() {
    }

    @QaGameTest
    public static void contentBossesAreLoadedAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedBossExpectations.BOSSES.isEmpty(), "no boss expectations were generated");
        Registry<Boss> bosses = helper.getLevel().registryAccess().lookupOrThrow(BossRegistries.BOSS);
        Set<String> expectedIds = new TreeSet<>();
        GeneratedBossExpectations.BOSSES.forEach(expected -> expectedIds.add(expected.id()));
        Set<String> loadedIds = new TreeSet<>();
        bosses.keySet().stream()
                .filter(id -> id.getNamespace().equals(Realmforge.MOD_ID))
                .forEach(id -> loadedIds.add(id.toString()));
        helper.assertValueEqual(loadedIds, expectedIds, "bosses in realmforge:boss");

        for (BossExpectation expected : GeneratedBossExpectations.BOSSES) {
            String id = expected.id();
            Boss boss = bosses.getValue(Identifier.parse(id));
            helper.assertTrue(boss != null, "boss is not loaded: " + id);
            helper.assertValueEqual(BuiltInRegistries.ENTITY_TYPE.getKey(boss.entity()).toString(), expected.entity(),
                    id + " entity");
            helper.assertValueEqual(boss.level(), expected.level(), id + " level");
            helper.assertTrue(Math.abs(boss.arena().radius() - expected.arenaRadius()) <= TOLERANCE,
                    id + " arena radius");
            helper.assertValueEqual(boss.barColor().getName(), expected.barColor(), id + " bar colour");
            helper.assertValueEqual(boss.phaseChangeTicks(), expected.phaseChangeTicks(), id + " phase change");
            helper.assertValueEqual(boss.phases().size(), expected.phases().size(), id + " phases");
            for (int index = 0; index < boss.phases().size(); index++) {
                assertPhase(helper, id, boss.phases().get(index), expected.phases().get(index));
            }

            EntityType<?> type = boss.entity();
            helper.assertTrue(type.builtInRegistryHolder().is(Tags.EntityTypes.BOSSES), id + "'s body is not in c:bosses");
            Entity body = type.create(helper.getLevel(), EntitySpawnReason.COMMAND);
            helper.assertTrue(body instanceof BossMob, id + "'s body is no BossMob: " + body);
            BossMob mob = (BossMob) body;
            helper.assertValueEqual(mob.definition().map(entry -> entry.key().identifier().toString()),
                    Optional.of(id), id + "'s body finds its fight");
            helper.assertValueEqual(Bosses.of(helper.getLevel().registryAccess(), type).map(entry -> entry.value()),
                    Optional.of(boss), id + " by its entity type");
            mob.discard();
        }
        helper.succeed();
    }

    private static void assertPhase(GameTestHelper helper, String boss, BossPhase phase, PhaseExpectation expected) {
        String what = boss + " phase " + expected.id();
        helper.assertValueEqual(phase.id(), expected.id(), what + " id");
        helper.assertValueEqual(phase.name(), expected.nameKey(), what + " name");
        helper.assertTrue(Language.getInstance().has(phase.name()), what + ": no en_us name");
        Optional<PhaseTrigger> trigger = phase.enterWhen();
        switch (expected.trigger()) {
            case "" -> helper.assertTrue(trigger.isEmpty(), what + " has a trigger: " + trigger);
            case "realmforge:health_below" -> helper.assertTrue(trigger.orElse(null) instanceof HealthBelow below
                    && Math.abs(below.health() - expected.triggerValue()) <= TOLERANCE, what + " trigger " + trigger);
            case "realmforge:time_in_phase" -> helper.assertTrue(trigger.orElse(null) instanceof TimeInPhase time
                    && time.afterTicks() == (int) expected.triggerValue(), what + " trigger " + trigger);
            default -> helper.fail(what + ": unknown trigger type " + expected.trigger());
        }
        helper.assertValueEqual(phase.onEnter().stream().map(spell -> spell.identifier().toString()).toList(),
                expected.onEnter(), what + " on_enter");
        helper.assertValueEqual(phase.abilities().size(), expected.abilities().size(), what + " abilities");
        for (int index = 0; index < phase.abilities().size(); index++) {
            BossAbility ability = phase.abilities().get(index);
            AbilityExpectation want = expected.abilities().get(index);
            helper.assertValueEqual(ability.spell().identifier().toString(), want.spell(), what + " ability spell");
            helper.assertValueEqual(ability.cooldownTicks(), want.cooldownTicks(), what + " ability cooldown");
        }
    }
}
