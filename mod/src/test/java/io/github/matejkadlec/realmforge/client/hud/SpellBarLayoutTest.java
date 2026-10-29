package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.spell.Spellbook;
import org.junit.jupiter.api.Test;

class SpellBarLayoutTest {
    private static final double DEFAULT = SpellBarLayout.DEFAULT_PLACE;

    private static SpellBarLayout.Group defaultGroup(int guiWidth, int guiHeight) {
        return SpellBarLayout.place(guiWidth, guiHeight, DEFAULT, DEFAULT, 1);
    }

    @Test
    void theNineSlotsSitSideBySide() {
        assertEquals(216, SpellBarLayout.WIDTH, "nine 24 px slots");
        assertEquals(0, SpellBarLayout.slotX(0, SpellBarLayout.WIDTH));
        assertEquals(7 * 24, SpellBarLayout.slotX(7, SpellBarLayout.WIDTH));
        assertEquals(216 - 24, SpellBarLayout.slotX(Spellbook.RACIAL_SLOT, SpellBarLayout.WIDTH),
                "the racial slot right after the eighth, no gap");
        assertEquals(480 / 2 - 108, SpellBarLayout.slotX(0, 480), "in the middle of a wider span");
    }

    @Test
    void theDefaultPlaceIsCentredAboveTheItemNamesLine() {
        // Survival with the health, armor and food rows and the XP bar: vanilla draws the item name from 270 - 73.
        SpellBarLayout.Group group = defaultGroup(480, 270);
        assertEquals(480 / 2 - 108, group.x(), "where the bar was before it could move");
        assertEquals(270 - 73 - 14 - 13 - 24, group.y());
        assertEquals(720 - 73 - 14 - 13 - 24, defaultGroup(1280, 720).y(), "measured from the screen's bottom");
        assertEquals(216, group.width());
        assertEquals(37, group.height(), "the slots and the resource bar");
    }

    @Test
    void theResourceBarHangsUnderTheSlotsWithItsTextInTheMiddle() {
        int barTop = SpellBarLayout.resourceBarTop(0);
        assertEquals(24, barTop, "attached to the slots, no gap");
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
    void theDefaultPlaceEndsTwiceTheOldGapAboveTheLevelText() {
        // Survival, health, armor and food rows: the XP bar goes above a stack of 59 and raises it to 73.
        int stack = 59;
        int levelTop = XpBarLayout.textTop(XpBarLayout.barTop(270, stack));
        int raised = XpBarLayout.raisedStack(stack, true);
        assertEquals(SpellBarLayout.BASE_STACK, raised);
        int blockBottom = defaultGroup(480, 270).bottom();
        int itemNameTop = 270 - raised;
        int oldBarBottom = itemNameTop - 2;
        assertEquals(12, levelTop - oldBarBottom, "the spell bar's gap to the level text before the resource bar");
        assertEquals(24, levelTop - blockBottom, "twice that");
        assertTrue(blockBottom <= itemNameTop, "clear of the item name's line");
    }

    @Test
    void absorptionHeartsLeaveTheDefaultPlaceWhereItIs() {
        // A second heart row raises the stack by 10: the XP bar and the item name move up, the group does not.
        SpellBarLayout.Group group = defaultGroup(480, 270);
        int raisedBelow = XpBarLayout.raisedStack(69, true);
        assertTrue(group.bottom() <= 270 - raisedBelow, "still clear of the item name's line one row higher");
        assertEquals(270 - group.y() + 10, SpellBarLayout.raised(480, 270, group, raisedBelow),
                "the next row goes above the group");
        assertEquals(250, SpellBarLayout.raised(480, 270, group, 250), "a stack that already reaches above it stays");
    }

    @Test
    void theRaisedStackPutsTheNextRowAboveTheGroup() {
        SpellBarLayout.Group group = defaultGroup(480, 270);
        int raised = SpellBarLayout.raised(480, 270, group, 73);
        // A row stacked next is drawn from guiHeight - raised, 9 px tall: it ends a pixel above the group.
        assertEquals(group.y() - 1, 270 - raised + 9);
        // The action bar message, 9 px above the stack's row, with its backdrop: clear of the group too.
        int messageMiddle = 270 - (raised + 9);
        assertTrue(messageMiddle + 4 + 3 <= group.y());
    }

    @Test
    void aGroupAwayFromTheMiddleOrAboveTheMessageLeavesTheStackBe() {
        SpellBarLayout.Group centred = defaultGroup(1280, 720);
        assertTrue(SpellBarLayout.raised(1280, 720, centred, 73) > 73, "the default place raises it");
        SpellBarLayout.Group side = new SpellBarLayout.Group(0, centred.y(), 1);
        assertEquals(73, SpellBarLayout.raised(1280, 720, side, 73), "as high, but not in the middle columns");
        SpellBarLayout.Group high = SpellBarLayout.place(480, 270, SpellBarLayout.MIDDLE, 0.2, 1);
        assertEquals(73, SpellBarLayout.raised(480, 270, high, 73), "above the action bar message");
        SpellBarLayout.Group low = SpellBarLayout.place(480, 270, SpellBarLayout.MIDDLE, 1, 1);
        assertEquals(73, SpellBarLayout.raised(480, 270, low, 73),
                "on the hotbar, below the message: the message stays above it");
    }

    @Test
    void theCastBarGoesTowardTheMiddleButStaysClearOfTheGroup() {
        SpellBarLayout.Group tall = defaultGroup(1280, 720);
        assertEquals((int) Math.round(720 * 0.7) - 10, SpellBarLayout.castBarTop(1280, 720, tall), "a tall screen");
        SpellBarLayout.Group low = defaultGroup(480, 270);
        assertEquals(low.y() - 28 - 20, SpellBarLayout.castBarTop(480, 270, low), "a short screen: above it");
        SpellBarLayout.Group top = SpellBarLayout.place(480, 270, SpellBarLayout.MIDDLE, 0, 1);
        int belowTop = SpellBarLayout.castBarTop(480, 270, top);
        assertEquals((int) Math.round(270 * 0.7) - 10, belowTop, "a group at the top leaves it in its place");
        SpellBarLayout.Group middle = SpellBarLayout.place(480, 270, SpellBarLayout.MIDDLE, 0.7, 1);
        int aboveMiddle = SpellBarLayout.castBarTop(480, 270, middle);
        assertTrue(aboveMiddle + 20 + 28 <= middle.y(), "above a group in the way, with room for the message");
        SpellBarLayout.Group side = SpellBarLayout.place(1280, 270, 0, 0.7, 1);
        assertEquals((int) Math.round(270 * 0.7) - 10, SpellBarLayout.castBarTop(1280, 270, side),
                "a group at the side is not in its way");
    }

    @Test
    void withNoRoomAboveTheCastBarGoesBelowTheGroup() {
        // A short screen, the group high up in the middle columns, over the cast bar's usual place.
        SpellBarLayout.Group group = SpellBarLayout.place(480, 100, SpellBarLayout.MIDDLE, 0.3, 1);
        int top = SpellBarLayout.castBarTop(480, 100, group);
        assertTrue(top >= group.bottom(), "below the group");
        assertTrue(top + 20 <= 100, "on the screen");
    }

    @Test
    void theGroupCannotLeaveTheScreen() {
        SpellBarLayout.Group off = new SpellBarLayout.Group(-30, 400, 1);
        SpellBarLayout.Group clamped = SpellBarLayout.clamp(480, 270, off);
        assertEquals(0, clamped.x(), "not past the left edge");
        assertEquals(270 - 37, clamped.y(), "not past the bottom");
        SpellBarLayout.Group right = SpellBarLayout.clamp(480, 270, new SpellBarLayout.Group(400, -5, 1));
        assertEquals(480 - 216, right.x(), "not past the right edge");
        assertEquals(0, right.y(), "not past the top");
        SpellBarLayout.Group inside = new SpellBarLayout.Group(100, 50, 1);
        assertEquals(inside, SpellBarLayout.clamp(480, 270, inside), "a group on the screen stays where it is");
        SpellBarLayout.Group big = SpellBarLayout.clamp(200, 270, new SpellBarLayout.Group(30, 50, 1));
        assertEquals(0, big.x(), "wider than the screen: at the left");
    }

    @Test
    void aSavedPlaceStaysOnTheScreenAndInTheMiddleWhenTheScreenChanges() {
        // Saved by the drag mode on one screen, placed on another: fractions of the room the screen leaves.
        int roomX = 427 - 216;
        SpellBarLayout.Group placed = SpellBarLayout.place(427, 240, 120.0 / roomX, 1, 1);
        assertEquals(120, placed.x(), "a fraction gives back the edge it was saved from");
        assertEquals(240 - 37, placed.y());
        SpellBarLayout.Group larger = SpellBarLayout.place(960, 540, 120.0 / roomX, 1, 1);
        assertEquals(540 - 37, larger.y(), "still at the bottom");
        assertTrue(larger.right() <= 960);
        double middle = SpellBarLayout.fraction(SpellBarLayout.middle(roomX), roomX);
        assertEquals(SpellBarLayout.MIDDLE, middle, "a group in the middle is saved as the middle");
        for (int guiWidth : new int[] {427, 480, 640, 961, 1000}) {
            SpellBarLayout.Group group = SpellBarLayout.place(guiWidth, 270, middle, 0.5, 1);
            assertEquals(guiWidth / 2 - 108, group.x(), "in the middle of a " + guiWidth + " px screen");
        }
        assertEquals(120.0 / roomX, SpellBarLayout.fraction(120, roomX));
        assertEquals(0, SpellBarLayout.fraction(0, roomX));
        assertEquals(1, SpellBarLayout.fraction(roomX, roomX));
    }

    @Test
    void theSizeScalesTheGroupAndMirroringTheHotbarOverridesIt() {
        assertEquals(1F, SpellBarLayout.scale(1, false));
        assertEquals(1.5F, SpellBarLayout.scale(1.5, false));
        assertEquals(0.5F, SpellBarLayout.scale(0.2, false), "held to the range");
        assertEquals(1.5F, SpellBarLayout.scale(3, false), "held to the range");
        float mirror = SpellBarLayout.scale(1.5, true);
        assertEquals(182, SpellBarLayout.width(mirror), "exactly the hotbar's width, whatever the size");
        assertEquals(Math.round(37 * 182 / 216F), SpellBarLayout.height(mirror), "the height keeps the ratio");
        assertEquals(324, SpellBarLayout.width(1.5F));
        assertEquals(56, SpellBarLayout.height(1.5F), "the height grows with the width");
        assertEquals(108, SpellBarLayout.width(0.5F));
        SpellBarLayout.Group big = SpellBarLayout.place(480, 270, DEFAULT, DEFAULT, 1.5F);
        assertEquals(480 / 2 - 162, big.x(), "a bigger group's default place is centred too");
        assertEquals(270 - 73 - 14, big.bottom(), "and ends where the default group does");
    }

    @Test
    void theSizeOptionStepsByFiveHundredthsAndReadsShort() {
        assertEquals(1.05, SpellBarLayout.stepSize(1.04));
        assertEquals(1.0, SpellBarLayout.stepSize(1.01));
        assertEquals(0.5, SpellBarLayout.stepSize(0.4), "held to the range");
        assertEquals(1.5, SpellBarLayout.stepSize(1.6), "held to the range");
        assertEquals("1", SpellBarLayout.sizeText(1.0));
        assertEquals("0.5", SpellBarLayout.sizeText(0.5));
        assertEquals("1.05", SpellBarLayout.sizeText(1.05));
        assertEquals("1.5", SpellBarLayout.sizeText(1.5));
    }

    @Test
    void theGroupContainsWhatItCovers() {
        SpellBarLayout.Group group = new SpellBarLayout.Group(10, 20, 1);
        assertTrue(group.contains(10, 20));
        assertTrue(group.contains(225.5, 56.5));
        assertFalse(group.contains(226, 40), "past the right edge");
        assertFalse(group.contains(9.5, 40), "left of it");
        assertFalse(group.contains(50, 57), "below it");
    }
}
