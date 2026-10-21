package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.spell.Spellbook;
import org.junit.jupiter.api.Test;

class SpellBarLayoutTest {
    @Test
    void theNineSlotsSitSideBySideAroundTheMiddle() {
        assertEquals(216, SpellBarLayout.WIDTH, "nine 24 px slots");
        assertEquals(480 / 2 - 108, SpellBarLayout.slotX(0, 480));
        assertEquals(480 / 2 - 108 + 7 * 24, SpellBarLayout.slotX(7, 480));
        assertEquals(480 / 2 + 108 - 24, SpellBarLayout.slotX(Spellbook.RACIAL_SLOT, 480),
                "the racial slot right after the eighth, no gap");
    }

    @Test
    void theBarSitsAboveTheItemNamesLine() {
        // Survival with two status rows and the XP bar: vanilla draws the item name from 270 - 63.
        assertEquals(270 - 63, SpellBarLayout.itemNameTop(270, 63, true));
        assertEquals(270 - 63 - 2 - 24, SpellBarLayout.top(270, 63, true));
        // A low stack still keeps vanilla's least height, 59.
        assertEquals(270 - 59 - 2 - 24, SpellBarLayout.top(270, 40, true));
        // Creative: vanilla draws the name 14 px lower, and the bar follows it.
        assertEquals(270 - 77 + 14 - 2 - 24, SpellBarLayout.top(270, 77, false));
    }

    @Test
    void theRaisedStackPutsTheNextRowAboveTheBar() {
        int top = SpellBarLayout.top(270, 63, true);
        int raised = SpellBarLayout.raised(270, top);
        // A row stacked next is drawn from guiHeight - raised, 9 px tall: it ends a pixel above the bar.
        assertEquals(top - 1, 270 - raised + 9);
    }

    @Test
    void theCastBarGoesTowardTheMiddleButStaysClearOfTheSpellBar() {
        int barTop = SpellBarLayout.top(540, 63, true);
        assertEquals((int) Math.round(540 * 0.7) - 10, SpellBarLayout.castBarTop(540, barTop), "a tall screen");
        int lowTop = SpellBarLayout.top(270, 63, true);
        assertEquals(lowTop - 28 - 20, SpellBarLayout.castBarTop(270, lowTop), "a short screen");
    }
}
