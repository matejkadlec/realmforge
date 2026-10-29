package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations.Bonus;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations.Sample;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations.StatExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations.StatRuleExpectation;
import io.github.matejkadlec.realmforge.stats.ClassRole;
import io.github.matejkadlec.realmforge.stats.ExhaustionReduction;
import io.github.matejkadlec.realmforge.stats.StatBonus;
import io.github.matejkadlec.realmforge.stats.StatRule;
import io.github.matejkadlec.realmforge.stats.StatRules;
import io.github.matejkadlec.realmforge.stats.StatsRegistries;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.common.PercentageAttribute;

/**
 * Checks that every stat in {@code content/stats/} is registered as the content says (how it lowers exhaustion
 * included) and sits on players, and that every stat rule in {@code content/stat_rules/} reached the datapack registry
 * {@code realmforge:stat_rule} and gives each class role the bonuses the pipeline computes
 * ({@link GeneratedStatExpectations}, the pattern of architecture 6.5).
 */
public final class ContentStatGameTests {
    // The pipeline and the game compute the same formula in doubles; allow for rounding only.
    static final double TOLERANCE = 1e-9;

    private ContentStatGameTests() {
    }

    @QaGameTest
    public static void contentStatsAreRegisteredAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedStatExpectations.STATS.isEmpty(), "no stat expectations were generated");
        Set<String> expectedIds = new TreeSet<>();
        GeneratedStatExpectations.STATS.forEach(expected -> expectedIds.add(expected.id()));
        Set<String> registeredIds = new TreeSet<>();
        BuiltInRegistries.ATTRIBUTE.keySet().stream()
                .filter(id -> id.getNamespace().equals(Realmforge.MOD_ID))
                .forEach(id -> registeredIds.add(id.toString()));
        helper.assertValueEqual(registeredIds, expectedIds, "our attributes in minecraft:attribute");
        helper.assertValueEqual(GeneratedStats.ALL.size(), expectedIds.size(), "GeneratedStats.ALL");

        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-stats-registered");
        for (StatExpectation expected : GeneratedStatExpectations.STATS) {
            Holder<Attribute> holder = attribute(expected.id());
            helper.assertTrue(holder.value() instanceof RangedAttribute, expected.id() + " is a ranged attribute");
            RangedAttribute attribute = (RangedAttribute) holder.value();
            helper.assertValueEqual(attribute.getDescriptionId(), expected.descriptionId(), expected.id() + " key");
            helper.assertValueEqual(attribute.getDefaultValue(), expected.defaultValue(), expected.id() + " default");
            helper.assertValueEqual(attribute.getMinValue(), 0.0, expected.id() + " minimum");
            helper.assertValueEqual(attribute.getMaxValue(), expected.maxValue(), expected.id() + " maximum");
            helper.assertValueEqual(attribute instanceof PercentageAttribute, expected.percent(),
                    expected.id() + " shown as a percentage");
            helper.assertTrue(attribute.isClientSyncable(), expected.id() + " is synced to clients");

            AttributeInstance instance = player.getAttribute(holder);
            helper.assertTrue(instance != null, "players have " + expected.id());
            helper.assertValueEqual(instance.getBaseValue(), expected.defaultValue(), expected.id() + " on a player");

            Optional<ExhaustionReduction> exhaustion = GeneratedStats.LOWER_EXHAUSTION.stream()
                    .filter(reduction -> reduction.stat().is(holder))
                    .findFirst();
            helper.assertValueEqual(exhaustion.map(ExhaustionReduction::perPoint).orElse(0.0),
                    expected.exhaustionPerPoint(), expected.id() + " lowers exhaustion per point by");
            helper.assertValueEqual(exhaustion.map(ExhaustionReduction::max).orElse(0.0), expected.exhaustionMax(),
                    expected.id() + " lowers exhaustion at most by");
        }
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    @QaGameTest
    public static void contentStatRulesGiveWhatThePipelineComputes(GameTestHelper helper) {
        helper.assertFalse(GeneratedStatExpectations.STAT_RULES.isEmpty(), "no stat rule expectations were generated");
        Registry<StatRule> rules = helper.getLevel().registryAccess().lookupOrThrow(StatsRegistries.STAT_RULE);

        Set<String> expectedIds = new TreeSet<>();
        GeneratedStatExpectations.STAT_RULES.forEach(expected -> expectedIds.add(expected.id()));
        Set<String> loadedIds = new TreeSet<>();
        rules.keySet().stream()
                .filter(id -> id.getNamespace().equals(Realmforge.MOD_ID))
                .forEach(id -> loadedIds.add(id.toString()));
        helper.assertValueEqual(loadedIds, expectedIds, "stat rules in realmforge:stat_rule");

        for (StatRuleExpectation expected : GeneratedStatExpectations.STAT_RULES) {
            Identifier id = Identifier.parse(expected.id());
            StatRule rule = rules.getValue(id);
            helper.assertTrue(rule != null, "stat rule is not loaded: " + expected.id());
            helper.assertValueEqual(rule.stat().getRegisteredName(), expected.stat(), expected.id() + " stat");
            for (Sample sample : expected.samples()) {
                assertBonuses(helper, StatRules.bonuses(id, rule, sample.value(), role(sample)), sample.bonuses(),
                        expected.id() + " at " + sample.value() + " for role " + sample.role());
            }
        }
        helper.succeed();
    }

    /** The game's bonuses equal the pipeline's, in the same order. */
    static void assertBonuses(GameTestHelper helper, List<StatBonus> got, List<Bonus> want, String where) {
        helper.assertValueEqual(got.size(), want.size(), where + ": number of bonuses");
        for (int i = 0; i < want.size(); i++) {
            Bonus expected = want.get(i);
            StatBonus actual = got.get(i);
            String what = where + ", bonus " + i;
            helper.assertValueEqual(actual.modifier().id().toString(), expected.id(), what + " id");
            helper.assertValueEqual(actual.attribute().getRegisteredName(), expected.attribute(), what + " attribute");
            helper.assertValueEqual(actual.modifier().operation().getSerializedName(), expected.operation(),
                    what + " operation");
            helper.assertTrue(close(actual.modifier().amount(), expected.amount()),
                    what + ": amount " + expected.amount() + " expected, got " + actual.modifier().amount());
        }
    }

    /** The class role a sample is for: empty for a player without a character. */
    static Optional<ClassRole> role(Sample sample) {
        return Arrays.stream(ClassRole.values())
                .filter(role -> role.getSerializedName().equals(sample.role()))
                .findFirst();
    }

    static boolean close(double got, double want) {
        return Math.abs(got - want) <= TOLERANCE * Math.max(1.0, Math.abs(want));
    }

    static Holder<Attribute> attribute(String id) {
        return BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(id))
                .orElseThrow(() -> new IllegalStateException("attribute is not registered: " + id));
    }
}
