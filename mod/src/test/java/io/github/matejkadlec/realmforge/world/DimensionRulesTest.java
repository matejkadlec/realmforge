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
import java.util.List;
import java.util.Optional;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class DimensionRulesTest {
    private static final MobLevelRule RULE =
            new MobLevelRule(2, 100, Optional.of(new MobLevelRule.Depth(0, 10)), 1, 1, 6);

    @Test
    void levelRisesOneStepPerBlocksPerLevelOfHorizontalDistance() {
        assertEquals(2, RULE.levelAt(0, 64, 0, 0));
        assertEquals(2, RULE.levelAt(99.9, 64, 0, 0));
        assertEquals(3, RULE.levelAt(100, 64, 0, 0));
        assertEquals(4, RULE.levelAt(0, 64, -250, 0));
        assertEquals(3, RULE.levelAt(60, 64, 80, 0), "60, 80 is 100 blocks away");
        assertEquals(2, RULE.levelAt(70, 64, 70, 0), "70, 70 is 99 blocks away");
    }

    @Test
    void depthAddsLevelsBelowItsLine() {
        assertEquals(2, RULE.levelAt(0, 0, 0, 0));
        assertEquals(2, RULE.levelAt(0, -9.5, 0, 0));
        assertEquals(3, RULE.levelAt(0, -10, 0, 0));
        assertEquals(5, RULE.levelAt(0, -35, 0, 0));
        MobLevelRule flat = new MobLevelRule(2, 100, Optional.empty(), 1, 1, 6);
        assertEquals(2, flat.levelAt(0, -35, 0, 0));
    }

    @Test
    void levelsAreClampedAfterTheJitter() {
        assertEquals(6, RULE.levelAt(10_000, 64, 0, 0));
        assertEquals(6, RULE.levelAt(10_000, 64, 0, 1));
        assertEquals(1, RULE.levelAt(0, 64, 0, -1));
        assertEquals(3, RULE.levelAt(0, 64, 0, 1));
        assertEquals(6, RULE.levelAt(30_000_000, 64, 0, 0), "the world's edge");
    }

    @Test
    void randomLevelsStayWithinTheJitter() {
        RandomSource random = RandomSource.create(42);
        boolean low = false;
        boolean high = false;
        for (int i = 0; i < 200; i++) {
            int level = RULE.levelAt(150, 64, 0, random);
            assertTrue(level >= 2 && level <= 4, "level " + level);
            low |= level == 2;
            high |= level == 4;
        }
        assertTrue(low && high, "both ends of the jitter come up");
        MobLevelRule steady = new MobLevelRule(2, 100, Optional.empty(), 0, 1, 6);
        assertEquals(3, steady.levelAt(150, 64, 0, random));
    }

    @Test
    void codecReadsTheGeneratedFormat() {
        DimensionRules rules = decode("""
                {"dimension": "minecraft:overworld", "mob_levels": {"origin_level": 2, "blocks_per_level": 100,
                 "depth": {"below_y": 0, "blocks_per_level": 10}, "jitter": 1, "min_level": 1, "max_level": 6}}
                """).getOrThrow();
        assertEquals(new DimensionRules(Level.OVERWORLD, Optional.of(RULE), List.of()), rules);
    }

    @Test
    void mobLevelsDepthAndJitterAreOptional() {
        assertEquals(new DimensionRules(Level.END, Optional.empty(), List.of()),
                decode("{\"dimension\": \"minecraft:the_end\"}").getOrThrow());
        DimensionRules rules = decode("""
                {"dimension": "minecraft:overworld", "mob_levels": {"origin_level": 1, "blocks_per_level": 5,
                 "min_level": 1, "max_level": 1}}
                """).getOrThrow();
        assertEquals(Optional.of(new MobLevelRule(1, 5, Optional.empty(), 0, 1, 1)), rules.mobLevels());
    }

    @Test
    void codecRejectsBrokenRanges() {
        assertTrue(rule("\"origin_level\": 2, \"blocks_per_level\": 5, \"min_level\": 3, \"max_level\": 2").isError(),
                "min above max");
        assertTrue(rule("\"origin_level\": 7, \"blocks_per_level\": 5, \"min_level\": 1, \"max_level\": 6").isError(),
                "origin above max");
        assertTrue(rule("\"origin_level\": 1, \"blocks_per_level\": 0, \"min_level\": 1, \"max_level\": 6").isError(),
                "no distance step");
        assertTrue(rule("\"origin_level\": 1, \"blocks_per_level\": 5, \"jitter\": -1, \"min_level\": 1, "
                + "\"max_level\": 6").isError(), "negative jitter");
        assertTrue(rule("\"origin_level\": 1, \"blocks_per_level\": 5, \"jitter\": 1001, \"min_level\": 1, "
                + "\"max_level\": 6").isError(), "jitter above the limit");
        assertTrue(decode("{\"dimension\": \"minecraft:overworld\", \"mob_levels\": {\"origin_level\": 1, "
                + "\"blocks_per_level\": 5, \"depth\": {\"below_y\": 0, \"blocks_per_level\": 0}, \"min_level\": 1, "
                + "\"max_level\": 6}}").isError(), "no depth step");
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
