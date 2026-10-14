package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The XP bar keeps clear of everything vanilla draws around it, for the stacks the status rows leave: no row (creative,
 * 39), one heart row (49), hearts and armor or two heart rows (59), and more. The pixel spans follow
 * {@code net.minecraft.client.gui.Gui} in 26.1.2.
 */
class XpBarLayoutTest {
    private static final int GUI_HEIGHT = 240;
    /** Vanilla's experience level: {@code guiHeight - 24 - 9 - 2}, drawn only when the level is above 0. */
    private static final int VANILLA_LEVEL_TOP = GUI_HEIGHT - 35;

    @ParameterizedTest
    @CsvSource({"39, 39, false", "49, 49, true", "59, 49, true", "49, 59, true", "79, 59, true"})
    void theBarSitsAboveTheStatusRowsAndBelowWhatFollows(int left, int right, boolean canHurtPlayer) {
        int stack = XpBarLayout.stack(left, right);
        int barTop = XpBarLayout.barTop(GUI_HEIGHT, stack);
        int barBottom = barTop + XpBarLayout.HEIGHT - 1;
        int textTop = XpBarLayout.textTop(barTop);
        int textBottom = textTop + XpBarLayout.TEXT_HEIGHT - 1;

        int highestRowTop = GUI_HEIGHT - Math.max(left, right) + 10;
        assertTrue(barBottom < highestRowTop, "the bar ends above the highest status row");
        assertTrue(textBottom < VANILLA_LEVEL_TOP, "the level text ends above vanilla's experience level");
        assertTrue(textBottom >= barTop, "the level text sits on the bar");

        int raised = XpBarLayout.raisedStack(stack, canHurtPlayer);
        int nextRowBottom = GUI_HEIGHT - raised + XpBarLayout.TEXT_HEIGHT - 1;
        assertTrue(nextRowBottom < textTop, "a row stacked after the bar ends above its text");
        int itemNameTop = GUI_HEIGHT - Math.max(raised, 59) + (canHurtPlayer ? 0 : 14);
        assertTrue(itemNameTop + XpBarLayout.TEXT_HEIGHT - 1 < textTop, "the selected item's name ends above the text");
    }

    @ParameterizedTest
    @CsvSource({"39, 39, 49", "49, 49, 49", "59, 49, 59", "49, 69, 69"})
    void theStackIsTheHigherHeightAtLeastTheHealthRows(int left, int right, int expected) {
        assertEquals(expected, XpBarLayout.stack(left, right));
    }
}
