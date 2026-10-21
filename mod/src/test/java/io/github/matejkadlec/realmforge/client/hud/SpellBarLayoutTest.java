package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.spell.Spellbook;
import org.junit.jupiter.api.Test;

/** Where the spell bar and the cast bar go (C3b, RLM-25). */
class SpellBarLayoutTest {
    @Test
    void theBarIsAsWideAsTheHotbarAndCentred() {
        assertEquals(182, SpellBarLayout.WIDTH);
        assertEquals(480 / 2 - 91, SpellBarLayout.slotX(0, 480), "its left edge is the hotbar's");
        assertEquals(480 / 2 - 91 + 7 * 20, SpellBarLayout.slotX(7, 480));
        assertEquals(480 / 2 + 91 - 18, SpellBarLayout.slotX(Spellbook.RACIAL_SLOT, 480),
                "the racial slot ends where the hotbar does");
    }

    @Test
    void itSitsOnTheStackAndRaisesIt() {
        assertEquals(270 - 49 - 2 - 18, SpellBarLayout.top(270, 49), "above two status rows");
        assertEquals(49 + 2 + 18 + 1, SpellBarLayout.raised(49));
        assertEquals(270 - 70 - 22 - 11, SpellBarLayout.castBarTop(270, 70));
    }
}
