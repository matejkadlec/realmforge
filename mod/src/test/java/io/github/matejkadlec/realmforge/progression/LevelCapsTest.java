package io.github.matejkadlec.realmforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LevelCapsTest {
    private static final Chapter ONE = new Chapter("chapter.one", 1, 10, 20);
    private static final Chapter TWO = new Chapter("chapter.two", 2, 20, 30);
    private static final Chapter THREE = new Chapter("chapter.three", 3, 30, 40);
    private static final List<Chapter> CHAPTERS = List.of(THREE, ONE, TWO);

    @Test
    void aNewPlayerHasTheFirstChaptersCap() {
        assertEquals(10, LevelCaps.cap(CHAPTERS, chapter -> false));
    }

    @Test
    void completingAChapterRaisesTheCapToItsCompletedCap() {
        assertEquals(20, LevelCaps.cap(CHAPTERS, Set.of(ONE)::contains));
        assertEquals(30, LevelCaps.cap(CHAPTERS, Set.of(ONE, TWO)::contains));
        assertEquals(40, LevelCaps.cap(CHAPTERS, Set.of(ONE, TWO, THREE)::contains));
    }

    @Test
    void theHighestCompletedChapterCountsWhateverTheOrderOfCompletion() {
        assertEquals(30, LevelCaps.cap(CHAPTERS, Set.of(TWO)::contains));
        assertEquals(40, LevelCaps.cap(CHAPTERS, Set.of(ONE, THREE)::contains));
    }

    @Test
    void withoutChaptersOnlyTheGuardCaps() {
        assertEquals(LevelCaps.MAX_LEVEL, LevelCaps.cap(List.of(), chapter -> true));
    }

    @Test
    void nextIsTheFirstChapterNotCompleted() {
        assertEquals(Optional.of(ONE), LevelCaps.next(CHAPTERS, chapter -> false));
        assertEquals(Optional.of(TWO), LevelCaps.next(CHAPTERS, Set.of(ONE)::contains));
        assertEquals(Optional.of(ONE), LevelCaps.next(CHAPTERS, Set.of(TWO)::contains));
        assertEquals(Optional.empty(), LevelCaps.next(CHAPTERS, Set.copyOf(CHAPTERS)::contains));
    }

    @Test
    void theCodecReadsTheGeneratedShapeAndRefusesACapThatDoesNotRise() {
        String json = "{\"name\": \"chapter.realmforge.chapter_1.name\", \"order\": 1, \"level_cap\": 10, "
                + "\"completed_level_cap\": 20}";
        Chapter chapter = Chapter.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assertEquals(new Chapter("chapter.realmforge.chapter_1.name", 1, 10, 20), chapter);
        String flat = "{\"name\": \"n\", \"order\": 1, \"level_cap\": 10, \"completed_level_cap\": 10}";
        assertTrue(Chapter.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(flat)).isError());
        String zero = "{\"name\": \"n\", \"order\": 0, \"level_cap\": 10, \"completed_level_cap\": 20}";
        assertTrue(Chapter.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(zero)).isError());
    }
}
