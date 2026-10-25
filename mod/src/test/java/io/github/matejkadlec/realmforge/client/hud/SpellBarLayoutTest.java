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
    void theBlockKeepsOnePlaceAboveTheItemNamesLine() {
        // Survival with the health, armor and food rows and the XP bar: vanilla draws the item name from 270 - 73.
        assertEquals(270 - 73 - 14 - 13 - 24, SpellBarLayout.top(270));
        assertEquals(720 - 73 - 14 - 13 - 24, SpellBarLayout.top(720), "measured from the screen's bottom");
    }

    @Test
    void theResourceBarHangsUnderTheSlotsWithItsTextInTheMiddle() {
        int top = SpellBarLayout.top(270);
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
    void theBlockEndsTwiceTheOldGapAboveTheLevelText() {
        // Survival, health, armor and food rows: the XP bar goes above a stack of 59 and raises it to 73.
        int stack = 59;
        int levelTop = XpBarLayout.textTop(XpBarLayout.barTop(270, stack));
        int raised = XpBarLayout.raisedStack(stack, true);
        assertEquals(SpellBarLayout.BASE_STACK, raised);
        int blockBottom = SpellBarLayout.resourceBarTop(SpellBarLayout.top(270)) + SpellBarLayout.RESOURCE_BAR_HEIGHT;
        int itemNameTop = 270 - raised;
        int oldBarBottom = itemNameTop - 2;
        assertEquals(12, levelTop - oldBarBottom, "the spell bar's gap to the level text before the resource bar");
        assertEquals(24, levelTop - blockBottom, "twice that");
        assertTrue(blockBottom <= itemNameTop, "clear of the item name's line");
    }

    @Test
    void absorptionHeartsLeaveTheBlockWhereItIs() {
        // A second heart row raises the stack by 10: the XP bar and the item name move up, the block does not.
        int top = SpellBarLayout.top(270);
        int raisedBelow = XpBarLayout.raisedStack(69, true);
        int blockBottom = SpellBarLayout.resourceBarTop(top) + SpellBarLayout.RESOURCE_BAR_HEIGHT;
        assertTrue(blockBottom <= 270 - raisedBelow, "still clear of the item name's line one row higher");
        assertEquals(270 - top + 10, SpellBarLayout.raised(270, top, raisedBelow), "the next row goes above the bar");
        assertEquals(250, SpellBarLayout.raised(270, top, 250), "a stack that already reaches above it stays as high");
    }

    @Test
    void theRaisedStackPutsTheNextRowAboveTheBar() {
        int top = SpellBarLayout.top(270);
        int raised = SpellBarLayout.raised(270, top, 73);
        // A row stacked next is drawn from guiHeight - raised, 9 px tall: it ends a pixel above the bar.
        assertEquals(top - 1, 270 - raised + 9);
    }

    @Test
    void theCastBarGoesTowardTheMiddleButStaysClearOfTheSpellBar() {
        int barTop = SpellBarLayout.top(720);
        assertEquals((int) Math.round(720 * 0.7) - 10, SpellBarLayout.castBarTop(720, barTop), "a tall screen");
        int lowTop = SpellBarLayout.top(270);
        assertEquals(lowTop - 28 - 20, SpellBarLayout.castBarTop(270, lowTop), "a short screen");
    }
}
