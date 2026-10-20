package io.github.matejkadlec.realmforge.qa.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.Requirement;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedDimensionRuleExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedDimensionRuleExpectations.DimensionRulesExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedDimensionRuleExpectations.Sample;
import io.github.matejkadlec.realmforge.world.DimensionRules;
import io.github.matejkadlec.realmforge.world.MobLevelRule;
import io.github.matejkadlec.realmforge.world.WorldRegistries;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Checks that every entry of {@code content/dimensions/} reached the server's datapack registry
 * {@code realmforge:dimension_rules} as the content says, that the game's mob level rule gives the levels and weights
 * the pipeline computes near a player of each sample level, and that the access requirements are the content's
 * ({@link GeneratedDimensionRuleExpectations}, architecture 6.5).
 */
public final class ContentDimensionRuleGameTests {
    private ContentDimensionRuleGameTests() {
    }

    @QaGameTest
    public static void contentDimensionRulesAreLoadedAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedDimensionRuleExpectations.DIMENSION_RULES.isEmpty(),
                "no dimension rule expectations were generated");
        Registry<DimensionRules> registry =
                helper.getLevel().registryAccess().lookupOrThrow(WorldRegistries.DIMENSION_RULES);

        Set<String> expectedIds = new TreeSet<>();
        GeneratedDimensionRuleExpectations.DIMENSION_RULES.forEach(expected -> expectedIds.add(expected.id()));
        Set<String> loadedIds = new TreeSet<>();
        registry.keySet().stream()
                .filter(id -> id.getNamespace().equals(Realmforge.MOD_ID))
                .forEach(id -> loadedIds.add(id.toString()));
        helper.assertValueEqual(loadedIds, expectedIds, "entries in realmforge:dimension_rules");

        for (DimensionRulesExpectation expected : GeneratedDimensionRuleExpectations.DIMENSION_RULES) {
            DimensionRules rules = registry.getValue(Identifier.parse(expected.id()));
            helper.assertTrue(rules != null, "dimension rules are not loaded: " + expected.id());
            ResourceKey<Level> dimension =
                    ResourceKey.create(Registries.DIMENSION, Identifier.parse(expected.dimension()));
            helper.assertValueEqual(rules.dimension(), dimension, expected.id() + " dimension");
            helper.assertValueEqual(DimensionRules.of(helper.getLevel().registryAccess(), dimension),
                    Optional.of(rules), expected.id() + " found by its dimension");
            helper.assertValueEqual(rules.mobLevels().map(ContentDimensionRuleGameTests::asWritten),
                    expected.mobLevels(), expected.id() + " mob levels");
            List<Requirement> access = expected.access().stream()
                    .map(json -> Requirement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow())
                    .toList();
            helper.assertValueEqual(rules.access(), access, expected.id() + " access requirements");

            for (Sample sample : expected.samples()) {
                MobLevelRule rule = rules.mobLevels().orElseThrow();
                helper.assertValueEqual(rule.chances(sample.playerLevel()),
                        new MobLevelRule.Chances(sample.lowest(), sample.weights()),
                        expected.id() + " near a player of level " + sample.playerLevel());
            }
        }
        helper.succeed();
    }

    private static GeneratedDimensionRuleExpectations.MobLevelRule asWritten(MobLevelRule rule) {
        return new GeneratedDimensionRuleExpectations.MobLevelRule(rule.entryLevel(), rule.maxLevel(), rule.weights());
    }
}
