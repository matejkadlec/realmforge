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
            Map.of(0, FIREBOLT, 7, FROSTBOLT));

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
        assertThrows(IllegalArgumentException.class, () -> new Spellbook(Map.of(), Map.of(9, FIREBOLT)),
                "slots are the bar's 0 to 7 and the racial 8");
    }

    @Test
    void aLearnedSpellTakesTheFirstEmptySlotOnce() {
        Spellbook book = new Spellbook(Map.of(FIREBOLT, 1), Map.of(0, FIREBOLT, 1, key("blink")));
        Spellbook placed = book.withSpellOnBar(key("frost_nova"));
        assertEquals(Optional.of(key("frost_nova")), placed.slot(2));
        assertEquals(placed, placed.withSpellOnBar(key("frost_nova")), "a spell on the bar stays where it is");
        assertEquals(book, book.withSpellOnBar(FIREBOLT));
        Spellbook full = Spellbook.EMPTY;
        for (int slot = 0; slot < Spellbook.BAR_SLOTS; slot++) {
            full = full.withSlot(slot, Optional.of(key("spell_" + slot)));
        }
        assertEquals(full, full.withSpellOnBar(key("one_more")), "a full bar takes no more");
        assertEquals(Optional.empty(), full.withSlot(Spellbook.RACIAL_SLOT, Optional.empty()).slot(Spellbook.RACIAL_SLOT));
    }

    @Test
    void placingASpellOnAnotherSwapsThem() {
        Spellbook book = new Spellbook(Map.of(), Map.of(0, FIREBOLT, 3, key("blink")));
        Spellbook swapped = book.placed(3, FIREBOLT);
        assertEquals(Map.of(3, FIREBOLT, 0, key("blink")), swapped.bar(), "firebolt moves, blink takes its slot");
        assertEquals(Map.of(0, FIREBOLT, 3, key("blink"), 5, key("nova")), book.placed(5, key("nova")).bar(),
                "a spell from the book goes into an empty slot");
        assertEquals(Map.of(0, key("nova"), 3, key("blink")), book.placed(0, key("nova")).bar(),
                "and replaces the one it lands on");
        assertEquals(Map.of(5, FIREBOLT, 3, key("blink")), book.placed(5, FIREBOLT).bar(), "moved to an empty slot");
        assertEquals(3, book.barSlotOf(key("blink")));
        assertEquals(-1, book.barSlotOf(key("nova")));
        assertThrows(IllegalArgumentException.class, () -> book.placed(Spellbook.RACIAL_SLOT, FIREBOLT),
                "the racial slot is the race's");
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
