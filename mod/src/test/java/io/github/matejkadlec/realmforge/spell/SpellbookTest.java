package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.testing.RoundTrip;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

class SpellbookTest {
    private static final ResourceKey<Spell> FIREBOLT = key("firebolt");
    private static final ResourceKey<Spell> FROSTBOLT = key("frostbolt");
    private static final Spellbook BOOK = new Spellbook(Map.of(FIREBOLT, 3, FROSTBOLT, 1),
            Map.of(0, FIREBOLT, 11, FROSTBOLT));

    @Test
    void codecRoundTrips() {
        assertEquals(BOOK, RoundTrip.nbt(Spellbook.CODEC, BOOK));
        assertEquals(BOOK, RoundTrip.json(Spellbook.CODEC, BOOK));
        assertEquals(Spellbook.EMPTY, RoundTrip.nbt(Spellbook.CODEC, Spellbook.EMPTY));
    }

    @Test
    void streamCodecRoundTrips() {
        assertEquals(BOOK, RoundTrip.stream(Spellbook.STREAM_CODEC, BOOK));
        assertEquals(Spellbook.EMPTY, RoundTrip.stream(Spellbook.STREAM_CODEC, Spellbook.EMPTY));
    }

    @Test
    void emptySaveIsAnEmptyBook() {
        assertEquals(Spellbook.EMPTY, RoundTrip.parseSnbt(Spellbook.CODEC, "{}").getOrThrow());
    }

    /** Bar slots are saved as string keys, since NBT compounds and JSON objects take no others. */
    @Test
    void savedShapeReads() {
        Spellbook read = RoundTrip.parseSnbt(Spellbook.CODEC,
                "{spells: {\"realmforge:firebolt\": 2}, bar: {\"4\": \"realmforge:firebolt\"}}").getOrThrow();
        assertEquals(new Spellbook(Map.of(FIREBOLT, 2), Map.of(4, FIREBOLT)), read);
    }

    @Test
    void invalidSavesAreRefused() {
        assertTrue(RoundTrip.parseSnbt(Spellbook.CODEC, "{spells: {\"realmforge:firebolt\": 0}}").isError(),
                "spell level 0");
        assertTrue(RoundTrip.parseSnbt(Spellbook.CODEC, "{bar: {\"-1\": \"realmforge:firebolt\"}}").isError(),
                "a negative slot");
        assertTrue(RoundTrip.parseSnbt(Spellbook.CODEC, "{bar: {\"01\": \"realmforge:firebolt\"}}").isError(),
                "a slot written two ways");
        assertTrue(RoundTrip.parseSnbt(Spellbook.CODEC, "{bar: {first: \"realmforge:firebolt\"}}").isError(),
                "a slot that is no number");
    }

    @Test
    void constructorEnforcesTheSameRules() {
        assertThrows(IllegalArgumentException.class, () -> new Spellbook(Map.of(FIREBOLT, 0), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Spellbook(Map.of(), Map.of(-1, FIREBOLT)));
    }

    @Test
    void levelOfAKnownSpell() {
        assertEquals(Optional.of(3), BOOK.level(FIREBOLT));
        assertEquals(Optional.empty(), BOOK.level(key("unknown")));
    }

    @Test
    void learningASpellSetsItsLevelAndKeepsTheBar() {
        Spellbook learned = BOOK.withSpell(FIREBOLT, 1).withSpell(key("blink"), 2);
        assertEquals(Optional.of(1), learned.level(FIREBOLT), "learning again replaces the level");
        assertEquals(Optional.of(2), learned.level(key("blink")));
        assertEquals(BOOK.bar(), learned.bar());
        assertThrows(IllegalArgumentException.class, () -> BOOK.withSpell(FIREBOLT, 0));
    }

    private static ResourceKey<Spell> key(String path) {
        return ResourceKey.create(SpellRegistries.SPELL, Realmforge.id(path));
    }
}
