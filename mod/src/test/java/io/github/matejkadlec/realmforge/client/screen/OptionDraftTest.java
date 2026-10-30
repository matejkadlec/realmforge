package io.github.matejkadlec.realmforge.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OptionDraftTest {
    /** A config value of 3, default 1, and how often it was written. */
    private final AtomicInteger config = new AtomicInteger(3);
    private final AtomicInteger writes = new AtomicInteger();

    private OptionDraft<Integer> draft() {
        return new OptionDraft<>(1, config::get, value -> {
            config.set(value);
            writes.incrementAndGet();
        });
    }

    @Test
    void aDraftStartsAtTheConfigsValue() {
        OptionDraft<Integer> draft = draft();
        assertEquals(3, draft.value());
        assertFalse(draft.changed());
        assertFalse(draft.isDefault());
    }

    @Test
    void cancelLeavesTheConfigAsItWas() {
        OptionDraft<Integer> draft = draft();
        draft.set(2);
        assertTrue(draft.changed());
        assertEquals(3, config.get(), "only Save & Quit writes");
    }

    @Test
    void saveWritesOnlyAChangedDraft() {
        OptionDraft<Integer> draft = draft();
        draft.save();
        assertEquals(0, writes.get(), "unchanged: nothing written");
        draft.set(2);
        draft.save();
        assertEquals(2, config.get());
        assertEquals(1, writes.get());
        assertFalse(draft.changed());
    }

    @Test
    void resetGoesBackToTheDefaultUntilSaved() {
        OptionDraft<Integer> draft = draft();
        draft.reset();
        assertEquals(1, draft.value());
        assertTrue(draft.isDefault());
        assertEquals(3, config.get());
    }

    @Test
    void theSearchMatchesPartOfANameIgnoringCase() {
        assertTrue(OptionDraft.matches("Spells Queue Size", ""), "an empty search shows every row");
        assertTrue(OptionDraft.matches("Spells Queue Size", "  "));
        assertTrue(OptionDraft.matches("Spells Queue Size", "queue"));
        assertTrue(OptionDraft.matches("Auto Shoot", "AUTO s"));
        assertFalse(OptionDraft.matches("Auto Shoot", "spellbar"));
    }
}
