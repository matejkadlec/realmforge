package io.github.matejkadlec.realmforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class DimensionRulesTest {
    private static final List<Integer> WEIGHTS = List.of(5, 10, 20, 30, 20, 10, 5);
    private static final MobLevelRule RULE = new MobLevelRule(6, 60, WEIGHTS);

    @Test
    void aPlayerBetweenTheFloorAndTheCapMeetsEveryOffsetByItsWeight() {
        assertEquals(new MobLevelRule.Chances(17, WEIGHTS), RULE.chances(20));
        MobLevelRule.Chances chances = RULE.chances(20);
        assertEquals(17, chances.lowest());
        assertEquals(23, chances.highest());
        assertEquals(30, chances.weight(20));
        assertEquals(5, chances.weight(23));
        assertEquals(0, chances.weight(16), "more than 3 below");
        assertEquals(0, chances.weight(24), "more than 3 above");
        assertEquals(3, RULE.spread());
    }

    @Test
    void theFloorDropsTheOffsetsBelowIt() {
        assertEquals(new MobLevelRule.Chances(6, List.of(30, 20, 10, 5)), RULE.chances(6), "at the floor");
        assertEquals(new MobLevelRule.Chances(6, List.of(20, 30, 20, 10, 5)), RULE.chances(7));
        assertEquals(new MobLevelRule.Chances(6, WEIGHTS), RULE.chances(9), "the floor cuts nothing from 9 on");
    }

    @Test
    void aPlayerBelowTheFloorMeetsEveryMobAtIt() {
        assertEquals(new MobLevelRule.Chances(6, List.of(1)), RULE.chances(5));
        assertEquals(new MobLevelRule.Chances(6, List.of(1)), RULE.chances(1));
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < 50; i++) {
            assertEquals(6, RULE.levelFor(3, random));
        }
    }

    @Test
    void theCapDropsTheOffsetsAboveIt() {
        assertEquals(new MobLevelRule.Chances(57, List.of(5, 10, 20, 30)), RULE.chances(60), "at the cap");
        assertEquals(new MobLevelRule.Chances(58, List.of(5, 10, 20)), RULE.chances(61));
        assertEquals(new MobLevelRule.Chances(60, List.of(5)), RULE.chances(63));
        assertEquals(new MobLevelRule.Chances(60, List.of(1)), RULE.chances(64), "no offset left: the cap");
        assertEquals(new MobLevelRule.Chances(60, List.of(1)), RULE.chances(1000));
    }

    @Test
    void rollsFollowTheWeights() {
        RandomSource random = RandomSource.create(42);
        int rolls = 20_000;
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = 0; i < rolls; i++) {
            counts.merge(RULE.levelFor(20, random), 1, Integer::sum);
        }
        assertEquals(Set.of(17, 18, 19, 20, 21, 22, 23), counts.keySet());
        for (int offset = -3; offset <= 3; offset++) {
            double share = counts.get(20 + offset) / (double) rolls;
            double expected = WEIGHTS.get(offset + 3) / 100.0;
            assertEquals(expected, share, 0.015, "the share of offset " + offset);
        }
    }

    @Test
    void rollsAtTheFloorAreRenormalised() {
        RandomSource random = RandomSource.create(42);
        int rolls = 20_000;
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = 0; i < rolls; i++) {
            counts.merge(RULE.levelFor(6, random), 1, Integer::sum);
        }
        assertEquals(Set.of(6, 7, 8, 9), counts.keySet());
        assertEquals(30 / 65.0, counts.get(6) / (double) rolls, 0.015, "the player's own level, 30 of 65");
        assertEquals(5 / 65.0, counts.get(9) / (double) rolls, 0.015, "three above, 5 of 65");
    }

    @Test
    void codecReadsTheGeneratedFormat() {
        DimensionRules rules = decode("""
                {"dimension": "minecraft:overworld", "mob_levels": {"entry_level": 6, "max_level": 60,
                 "weights": [5, 10, 20, 30, 20, 10, 5]}}
                """).getOrThrow();
        assertEquals(new DimensionRules(Level.OVERWORLD, Optional.of(RULE), List.of()), rules);
    }

    @Test
    void mobLevelsAreOptional() {
        assertEquals(new DimensionRules(Level.END, Optional.empty(), List.of()),
                decode("{\"dimension\": \"minecraft:the_end\"}").getOrThrow());
        DimensionRules rules = decode("""
                {"dimension": "minecraft:overworld", "mob_levels": {"entry_level": 1, "max_level": 1, "weights": [1]}}
                """).getOrThrow();
        assertEquals(Optional.of(new MobLevelRule(1, 1, List.of(1))), rules.mobLevels());
    }

    @Test
    void codecRejectsBrokenRules() {
        assertTrue(rule("\"entry_level\": 7, \"max_level\": 6, \"weights\": [1]").isError(), "floor above cap");
        assertTrue(rule("\"entry_level\": 0, \"max_level\": 6, \"weights\": [1]").isError(), "floor 0");
        assertTrue(rule("\"entry_level\": 1, \"max_level\": 6, \"weights\": [1, 2]").isError(), "even weights");
        assertTrue(rule("\"entry_level\": 1, \"max_level\": 6, \"weights\": []").isError(), "no weights");
        assertTrue(rule("\"entry_level\": 1, \"max_level\": 6, \"weights\": [1, 0, 1]").isError(), "weight 0");
        assertTrue(rule("\"entry_level\": 1, \"max_level\": 6, \"weights\": [1001]").isError(),
                "a weight above the limit");
        assertTrue(rule("\"entry_level\": 1, \"max_level\": 6, \"weights\": [" + "1, ".repeat(21) + "1]")
                .isError(), "more than 10 offsets either side");
        assertTrue(rule("\"entry_level\": 1, \"max_level\": 6").isError(), "weights missing");
    }

    @Test
    void codecRoundTrips() {
        DimensionRules rules = new DimensionRules(Level.OVERWORLD, Optional.of(RULE), List.of());
        JsonElement json = DimensionRules.CODEC.encodeStart(JsonOps.INSTANCE, rules).getOrThrow();
        assertEquals(rules, DimensionRules.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    /** The file {@code rf gen} writes for {@code content/dimensions/overworld.yaml} decodes. */
    @Test
    void generatedOverworldRulesDecode() throws IOException {
        String path = "/data/realmforge/realmforge/dimension_rules/overworld.json";
        try (InputStream stream = DimensionRulesTest.class.getResourceAsStream(path)) {
            assertTrue(stream != null, path + " is missing; run `uv run rf gen`");
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            DimensionRules rules = DimensionRules.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(Level.OVERWORLD, rules.dimension());
            assertTrue(rules.mobLevels().isPresent(), "the Overworld has mob levels");
        }
    }

    private static DataResult<DimensionRules> rule(String fields) {
        return decode("{\"dimension\": \"minecraft:overworld\", \"mob_levels\": {" + fields + "}}");
    }

    private static DataResult<DimensionRules> decode(String json) {
        return DimensionRules.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }
}
