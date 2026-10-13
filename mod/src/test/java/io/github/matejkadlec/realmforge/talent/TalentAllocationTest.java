package io.github.matejkadlec.realmforge.talent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.testing.RoundTrip;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class TalentAllocationTest {
    private static final Identifier FIRE = Realmforge.id("mage_fire");
    private static final Identifier ARMS = Realmforge.id("warrior_arms");
    private static final TalentAllocation TALENTS = new TalentAllocation(
            Map.of(FIRE, Map.of("improved_firebolt", 5, "ignite", 2), ARMS, Map.of("deep_wounds", 1)), 3);

    @Test
    void codecRoundTrips() {
        assertEquals(TALENTS, RoundTrip.nbt(TalentAllocation.CODEC, TALENTS));
        assertEquals(TALENTS, RoundTrip.json(TalentAllocation.CODEC, TALENTS));
        assertEquals(TalentAllocation.NONE, RoundTrip.nbt(TalentAllocation.CODEC, TalentAllocation.NONE));
    }

    @Test
    void streamCodecRoundTrips() {
        assertEquals(TALENTS, RoundTrip.stream(TalentAllocation.STREAM_CODEC, TALENTS));
        assertEquals(TalentAllocation.NONE, RoundTrip.stream(TalentAllocation.STREAM_CODEC, TalentAllocation.NONE));
    }

    @Test
    void emptySaveIsNoTalents() {
        assertEquals(TalentAllocation.NONE, RoundTrip.parseSnbt(TalentAllocation.CODEC, "{}").getOrThrow());
    }

    @Test
    void savedShapeReads() {
        TalentAllocation read = RoundTrip.parseSnbt(TalentAllocation.CODEC,
                "{ranks: {\"realmforge:mage_fire\": {ignite: 1}}, unspent_points: 4}").getOrThrow();
        assertEquals(new TalentAllocation(Map.of(FIRE, Map.of("ignite", 1)), 4), read);
    }

    @Test
    void invalidSavesAreRefused() {
        assertTrue(RoundTrip.parseSnbt(TalentAllocation.CODEC,
                "{ranks: {\"realmforge:mage_fire\": {ignite: 0}}}").isError(), "rank 0");
        assertTrue(RoundTrip.parseSnbt(TalentAllocation.CODEC,
                "{ranks: {\"realmforge:mage_fire\": {\"Ignite!\": 1}}}").isError(), "a malformed node id");
        assertTrue(RoundTrip.parseSnbt(TalentAllocation.CODEC, "{unspent_points: -1}").isError(), "negative points");
    }

    @Test
    void constructorEnforcesTheSameRules() {
        assertThrows(IllegalArgumentException.class, () -> new TalentAllocation(Map.of(FIRE, Map.of("x", 0)), 0));
        assertThrows(IllegalArgumentException.class, () -> new TalentAllocation(Map.of(FIRE, Map.of("X", 1)), 0));
        assertThrows(IllegalArgumentException.class, () -> new TalentAllocation(Map.of(), -1));
    }

    /** An empty tree means nothing is allocated in it, so it is dropped and equals no tree at all. */
    @Test
    void emptyTreesAreDropped() {
        assertEquals(TalentAllocation.NONE, new TalentAllocation(Map.of(FIRE, Map.of()), 0));
    }

    @Test
    void ranksAndPointsSpent() {
        assertEquals(5, TALENTS.rank(FIRE, "improved_firebolt"));
        assertEquals(0, TALENTS.rank(FIRE, "pyroblast"));
        assertEquals(0, TALENTS.rank(Realmforge.id("other"), "ignite"));
        assertEquals(7, TALENTS.pointsSpent(FIRE));
        assertEquals(0, TALENTS.pointsSpent(Realmforge.id("other")));
    }
}
