package io.github.matejkadlec.realmforge.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class BossPhaseMachineTest {
    private static final String BOSSES = "data/realmforge/realmforge/boss";
    /** The trigger types by their ids, as {@link PhaseTriggerTypes} registers them. */
    private static final Map<String, MapCodec<? extends PhaseTrigger>> TRIGGERS = Map.of(
            "realmforge:health_below", HealthBelow.MAP_CODEC,
            "realmforge:time_in_phase", TimeInPhase.MAP_CODEC);

    private static final List<BossPhase> PHASES = List.of(
            phase("awakening", null),
            phase("summoning", new HealthBelow(0.7F)),
            phase("fury", new HealthBelow(0.3F)),
            phase("enrage", new TimeInPhase(600)));

    @Test
    void theFightStaysInAPhaseUntilTheNextTriggerHolds() {
        assertEquals(OptionalInt.empty(), next(0, 1.0F, 0));
        assertEquals(OptionalInt.empty(), next(0, 0.7F, 0), "health_below is strictly below");
        assertEquals(OptionalInt.of(1), next(0, 0.69F, 0));
    }

    @Test
    void phasesComeInOrderEvenPastTwoThresholds() {
        assertEquals(OptionalInt.of(1), next(0, 0.1F, 0), "a hit past 70 % and 30 % enters the phase between first");
        assertEquals(OptionalInt.of(2), next(1, 0.1F, 0), "and the next one after it");
        assertEquals(OptionalInt.empty(), next(1, 0.5F, 10_000), "time does not enter a health phase");
    }

    @Test
    void aTimedPhaseWaitsForItsTimeInThePhaseBefore() {
        assertEquals(OptionalInt.empty(), next(2, 0.1F, 599));
        assertEquals(OptionalInt.of(3), next(2, 0.1F, 600));
        assertEquals(OptionalInt.empty(), next(3, 0.0F, 10_000), "the last phase is the last");
    }

    @Test
    void aTriggerOnTheFirstPhaseNeverMatters() {
        List<BossPhase> phases = List.of(phase("first", new HealthBelow(0.5F)), phase("second", new HealthBelow(0.4F)));
        assertEquals(OptionalInt.of(1), BossPhaseMachine.next(phases, 0, new BossPhaseMachine.Progress(0.3F, 0)));
        assertEquals(OptionalInt.empty(), BossPhaseMachine.next(phases, -1, new BossPhaseMachine.Progress(0.3F, 0)));
    }

    @Test
    void triggerCodecsReadTheGeneratedFormat() {
        assertEquals(new HealthBelow(0.5F), trigger(JsonParser.parseString(
                "{\"type\": \"realmforge:health_below\", \"health\": 0.5}").getAsJsonObject()));
        assertEquals(new TimeInPhase(80), trigger(JsonParser.parseString(
                "{\"type\": \"realmforge:time_in_phase\", \"after_ticks\": 80}").getAsJsonObject()));
    }

    /**
     * Every boss {@code rf gen} wrote goes through all its phases in order in a fight that takes a little health and
     * a second every step, and reaches its last phase before its health is gone.
     */
    @Test
    void everyBossDefinitionReachesEveryPhaseInOrder() throws IOException {
        List<Path> files = generatedBosses();
        assertFalse(files.isEmpty(), "no boss definitions were generated; run `uv run rf gen`");
        for (Path file : files) {
            List<BossPhase> phases = phases(file);
            List<Integer> visited = new ArrayList<>(List.of(0));
            int current = 0;
            int ticksInPhase = 0;
            for (float health = 1.0F; health > 0 && current < phases.size() - 1; health -= 0.005F) {
                OptionalInt next = BossPhaseMachine.next(phases, current, new BossPhaseMachine.Progress(health,
                        ticksInPhase));
                if (next.isPresent()) {
                    current = next.getAsInt();
                    visited.add(current);
                    ticksInPhase = 0;
                } else {
                    ticksInPhase += 20;
                }
            }
            for (int index = 0; index < visited.size(); index++) {
                assertEquals(index, visited.get(index), file + ": phases in order " + visited);
            }
            assertEquals(phases.size() - 1, current, file + ": the last phase is reached before the boss dies");
        }
    }

    private static OptionalInt next(int current, float health, int ticksInPhase) {
        return BossPhaseMachine.next(PHASES, current, new BossPhaseMachine.Progress(health, ticksInPhase));
    }

    private static BossPhase phase(String id, PhaseTrigger trigger) {
        return new BossPhase(id, "boss.realmforge.test." + id, Optional.ofNullable(trigger), List.of(), List.of());
    }

    /** The boss files {@code rf gen} wrote, in name order. */
    private static List<Path> generatedBosses() throws IOException {
        String root = System.getProperty("realmforge.generatedResources");
        assertTrue(root != null, "realmforge.generatedResources is not set; run the tests through Gradle");
        Path folder = Path.of(root).resolve(BOSSES);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.toString().endsWith(".json")).sorted().toList();
        }
    }

    /** A generated boss's phases with their triggers; their spells play no part in the machine. */
    private static List<BossPhase> phases(Path file) throws IOException {
        JsonObject boss;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            boss = JsonParser.parseReader(reader).getAsJsonObject();
        }
        List<BossPhase> phases = new ArrayList<>();
        for (JsonElement element : boss.getAsJsonArray("phases")) {
            JsonObject phase = element.getAsJsonObject();
            PhaseTrigger trigger = phase.has("enter_when") ? trigger(phase.getAsJsonObject("enter_when")) : null;
            phases.add(phase(phase.get("id").getAsString(), trigger));
        }
        return phases;
    }

    private static PhaseTrigger trigger(JsonObject json) {
        String type = json.get("type").getAsString();
        MapCodec<? extends PhaseTrigger> codec = TRIGGERS.get(type);
        assertTrue(codec != null, "unknown phase trigger type " + type);
        return codec.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }
}
