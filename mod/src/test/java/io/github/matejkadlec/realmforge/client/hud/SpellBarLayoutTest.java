package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void theBlockOfSlotsAndResourceBarSitsAboveTheItemNamesLine() {
        // Survival with two status rows and the XP bar: vanilla draws the item name from 270 - 63.
        assertEquals(270 - 63, SpellBarLayout.itemNameTop(270, 63, true));
        assertEquals(270 - 63 - 26 - 13 - 24, SpellBarLayout.top(270, 63, true));
        // A low stack still keeps vanilla's least height, 59.
        assertEquals(270 - 59 - 26 - 13 - 24, SpellBarLayout.top(270, 40, true));
        // Creative: vanilla draws the name 14 px lower, and the block follows it.
        assertEquals(270 - 77 + 14 - 26 - 13 - 24, SpellBarLayout.top(270, 77, false));
    }

    @Test
    void theResourceBarHangsUnderTheSlotsWithItsTextInTheMiddle() {
        int top = SpellBarLayout.top(270, 63, true);
        int barTop = SpellBarLayout.resourceBarTop(top);
        assertEquals(top + 24, barTop, "attached to the slots, no gap");
        int innerTop = barTop + 1;
        int innerRows = SpellBarLayout.RESOURCE_BAR_HEIGHT - 2;
        assertEquals(1, innerRows % 2, "an odd inner height");
        // The digits cover 7 rows from the text's top, the outline one more each way.
        int outlineTop = SpellBarLayout.resourceTextTop(barTop) - 1;
        int outlineBottom = SpellBarLayout.resourceTextTop(barTop) + 7;
        assertEquals(outlineTop - innerTop, innerTop + innerRows - 1 - outlineBottom, "as much room above as below");
        assertEquals(1, outlineTop - innerTop, "a free row above and below the outline");
    }

    @Test
    void theBlockEndsThreeTimesTheOldGapAboveTheLevelText() {
        // Survival, health and food rows only: the XP bar goes above a stack of 49 and raises it to 63.
        int stack = 49;
        int levelTop = XpBarLayout.textTop(XpBarLayout.barTop(270, stack));
        int raised = XpBarLayout.raisedStack(stack, true);
        int blockBottom = SpellBarLayout.resourceBarTop(SpellBarLayout.top(270, raised, true))
                + SpellBarLayout.RESOURCE_BAR_HEIGHT;
        int oldBarBottom = SpellBarLayout.itemNameTop(270, raised, true) - 2;
        assertEquals(12, levelTop - oldBarBottom, "the spell bar's gap to the level text before the resource bar");
        assertEquals(36, levelTop - blockBottom, "three times that");
        assertTrue(blockBottom <= SpellBarLayout.itemNameTop(270, raised, true), "clear of the item name's line");
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
        int barTop = SpellBarLayout.top(720, 63, true);
        assertEquals((int) Math.round(720 * 0.7) - 10, SpellBarLayout.castBarTop(720, barTop), "a tall screen");
        int lowTop = SpellBarLayout.top(270, 63, true);
        assertEquals(lowTop - 28 - 20, SpellBarLayout.castBarTop(270, lowTop), "a short screen");
    }
}
