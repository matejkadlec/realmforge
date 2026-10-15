package io.github.matejkadlec.realmforge.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.testing.RoundTrip;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.junit.jupiter.api.Test;

class CharacterDataTest {
    private static final ResourceKey<Race> HUMAN = ResourceKey.create(CharacterRegistries.RACE, Realmforge.id("human"));
    private static final ResourceKey<PlayerClass> WARRIOR =
            ResourceKey.create(CharacterRegistries.CLASS, Realmforge.id("warrior"));
    private static final ResourceKey<PlayerClass> MAGE =
            ResourceKey.create(CharacterRegistries.CLASS, Realmforge.id("mage"));

    private static final ResourceKey<Attribute> STRENGTH = GeneratedStats.STRENGTH.unwrapKey().orElseThrow();
    private static final ResourceKey<Attribute> INTELLECT = GeneratedStats.INTELLECT.unwrapKey().orElseThrow();

    private static final CharacterData CHARACTER = new CharacterData(Optional.of(HUMAN),
            List.of(WARRIOR, MAGE), 123_456_789_012L, Optional.of(20), Map.of(STRENGTH, 3, INTELLECT, 1));

    @Test
    void codecRoundTrips() {
        assertEquals(CHARACTER, RoundTrip.nbt(CharacterData.CODEC, CHARACTER));
        assertEquals(CHARACTER, RoundTrip.json(CharacterData.CODEC, CHARACTER));
        assertEquals(CharacterData.NONE, RoundTrip.nbt(CharacterData.CODEC, CharacterData.NONE));
    }

    @Test
    void streamCodecRoundTrips() {
        assertEquals(CHARACTER, RoundTrip.stream(CharacterData.STREAM_CODEC, CHARACTER));
        assertEquals(CharacterData.NONE, RoundTrip.stream(CharacterData.STREAM_CODEC, CharacterData.NONE));
    }

    /** Every field is optional in the saved shape, so a later field never makes older saves unreadable. */
    @Test
    void emptySaveIsNoCharacter() {
        assertEquals(CharacterData.NONE, RoundTrip.parseSnbt(CharacterData.CODEC, "{}").getOrThrow());
    }

    @Test
    void savedShapeReads() {
        CharacterData read = RoundTrip.parseSnbt(CharacterData.CODEC,
                "{race: \"realmforge:human\", classes: [\"realmforge:warrior\"], total_xp: 450L}").getOrThrow();
        assertEquals(new CharacterData(Optional.of(HUMAN), List.of(WARRIOR), 450,
                Optional.empty()), read);
    }

    /** RLM-196: the stat points spent on each stat, by the stat's id. */
    @Test
    void savedStatPointsRead() {
        CharacterData read = RoundTrip.parseSnbt(CharacterData.CODEC,
                "{race: \"realmforge:human\", stat_points: {\"realmforge:strength\": 2}}").getOrThrow();
        assertEquals(Map.of(STRENGTH, 2), read.statPoints());
        assertEquals(2, read.spentStatPoints());
    }

    @Test
    void invalidSavesAreRefused() {
        assertTrue(RoundTrip.parseSnbt(CharacterData.CODEC,
                "{classes: [\"realmforge:mage\", \"realmforge:mage\"]}").isError(), "a class twice");
        assertTrue(RoundTrip.parseSnbt(CharacterData.CODEC, "{total_xp: -1L}").isError(), "negative XP");
        assertTrue(RoundTrip.parseSnbt(CharacterData.CODEC, "{level_cap: 0}").isError(), "a cap below 1");
        assertTrue(RoundTrip.parseSnbt(CharacterData.CODEC, "{stat_points: {\"realmforge:strength\": 0}}").isError(),
                "a stat with 0 spent points");
    }

    @Test
    void constructorEnforcesTheSameRules() {
        assertThrows(IllegalArgumentException.class, () -> new CharacterData(Optional.empty(),
                List.of(MAGE, MAGE), 0, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterData(Optional.empty(), List.of(), -1, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterData(Optional.empty(), List.of(), 0, Optional.of(0)));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterData(Optional.empty(), List.of(), 0, Optional.empty(), Map.of(STRENGTH, 0)));
    }

    @Test
    void aPointSpentAddsToItsStat() {
        CharacterData more = CHARACTER.plusStatPoint(STRENGTH).plusStatPoint(GeneratedStats.VITALITY.unwrapKey()
                .orElseThrow());
        assertEquals(4, more.statPoints().get(STRENGTH));
        assertEquals(6, more.spentStatPoints());
        assertEquals(Map.of(STRENGTH, 3, INTELLECT, 1), CHARACTER.statPoints(), "the old value is unchanged");
        assertEquals(0, CharacterData.NONE.spentStatPoints());
    }

    @Test
    void classesAreCopied() {
        var classes = new ArrayList<>(List.of(WARRIOR));
        CharacterData character = new CharacterData(Optional.of(HUMAN), classes, 0,
                Optional.empty());
        classes.add(MAGE);
        assertEquals(List.of(WARRIOR), character.classes());
    }

    @Test
    void aCharacterExistsOnceItHasARace() {
        assertFalse(CharacterData.NONE.exists());
        assertTrue(CHARACTER.exists());
    }

    /** Creating a character sets the race and the one class, and keeps XP, the cached cap and spent points. */
    @Test
    void createSetsRaceAndClassAndKeepsTheRest() {
        CharacterData before = new CharacterData(Optional.empty(), List.of(), 300, Optional.of(10));
        assertEquals(new CharacterData(Optional.of(HUMAN), List.of(MAGE), 300, Optional.of(10)),
                before.create(HUMAN, MAGE));
        assertEquals(CHARACTER.statPoints(), CHARACTER.create(HUMAN, MAGE).statPoints());
    }
}
