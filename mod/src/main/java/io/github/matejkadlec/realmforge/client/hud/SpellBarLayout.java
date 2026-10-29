package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.spell.Spellbook;
import java.math.BigDecimal;

/**
 * Where the spell bar, the resource bar under it and the cast bar go (architecture 18; C3b, RLM-25; RLM-400; RLM-404),
 * without world access so JUnit covers it. The spell bar's nine slots sit side by side (the racial slot last), wider
 * than the hotbar (Matěj's playtest of 2026-10-21), with the resource bar attached below, as wide: one group, which the
 * player can move and resize ({@link Group}; the spell options, Matěj 2026-10-24). Its default place stays the same on
 * the screen, whatever rows the stack has (Matěj, 2026-10-24: absorption hearts must not move it): centred,
 * {@value #GAP} px above the item name's line of a survival player's usual stack ({@value #BASE_STACK}). NeoForge
 * stacks the status rows and our XP bar through {@code Gui#leftHeight} and {@code Gui#rightHeight}, vanilla draws the
 * selected item's name on the line above that stack and the action bar message above that. Wherever the group is, the
 * message and the cast bar keep clear of it.
 */
public final class SpellBarLayout {
    /** A slot's square at size 1, a third bigger than a hotbar slot. */
    public static final int SLOT = 24;
    /** The nine slots side by side, at size 1. */
    public static final int WIDTH = Spellbook.SLOT_COUNT * SLOT;
    /**
     * Between the item name's line and the block's bottom, the resource bar's: with the line's 9 px and the pixel under
     * it, 24 px to the level text above the XP bar, twice the 12 px the spell bar had to it before the resource bar
     * (Matěj, 2026-10-24).
     */
    public static final int GAP = 14;
    /**
     * The stack the default place is measured from: the hotbar's 39, the health and the armor row (every class starts
     * in armor) and the XP bar's 14, where vanilla draws the item name in survival. Absorption hearts, more health
     * rows, the air row or a mount's hearts make the stack higher and leave the group where it is.
     */
    static final int BASE_STACK = 73;
    /**
     * The resource bar's height: a 1 px border around 11 rows, the text's 9 outlined rows with one free row above and
     * below, an odd count, so the text sits exactly in the middle (Matěj, 2026-10-24: about twice the XP bar's 5 px,
     * as much as the text needs).
     */
    public static final int RESOURCE_BAR_HEIGHT = 13;
    /** The group's height at size 1: the slots and the resource bar. */
    public static final int HEIGHT = SLOT + RESOURCE_BAR_HEIGHT;
    /** A stacked row's height: the font's 9 px and a pixel of room. */
    static final int ROW = 10;

    /** The Spellbar Size option's range and step: the group's width as a share of its width at size 1. */
    public static final double MIN_SIZE = 0.5;
    public static final double MAX_SIZE = 1.5;
    public static final double SIZE_STEP = 0.05;
    /** Vanilla's hotbar, as wide as Mirror Hotbar's Width makes the group. */
    public static final int HOTBAR_WIDTH = 182;
    /** A saved place's fraction that means the middle, on every screen. */
    public static final double MIDDLE = 0.5;
    /** The saved place that means the default place. */
    public static final double DEFAULT_PLACE = -1;

    /** The cast bar: the spell's icon, then the bar, in one frame. */
    public static final int CAST_BAR_WIDTH = 200;
    public static final int CAST_BAR_HEIGHT = 20;
    /** How far down the screen the cast bar's middle is at most, as a share of the screen's height. */
    static final double CAST_BAR_CENTER = 0.7;
    /**
     * How far above the spell bar the cast bar ends at least: room for the action bar message, which vanilla draws
     * 13 px above the raised stack's row, with its backdrop.
     */
    static final int CAST_BAR_ABOVE = 28;
    /** How far below the group the cast bar begins when there is no room above it. */
    static final int CAST_BAR_BELOW = 8;
    /**
     * Half the width of the screen's middle columns, where the stacked rows, the action bar message and the cast bar
     * go: the cast bar's half.
     */
    static final int MIDDLE_HALF_WIDTH = CAST_BAR_WIDTH / 2;
    /**
     * Vanilla's action bar message: its line's middle is {@value #MESSAGE_OVER_STACK} px above the stack (NeoForge's
     * 68 - 59) and at least {@value #MESSAGE_LOWEST} px above the screen's bottom.
     */
    static final int MESSAGE_OVER_STACK = 9;
    static final int MESSAGE_LOWEST = 68;
    /** How far above the message's middle its backdrop begins: the text's 4 px and the backdrop's 2. */
    static final int MESSAGE_ABOVE = 6;

    /**
     * The spell bar and resource bar group: its top left corner and its scale, the size (or the hotbar's width) that
     * its width and height at size 1 are drawn at.
     */
    public record Group(int x, int y, float scale) {
        public int width() {
            return SpellBarLayout.width(scale);
        }

        public int height() {
            return SpellBarLayout.height(scale);
        }

        public int right() {
            return x + width();
        }

        public int bottom() {
            return y + height();
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    }

    private SpellBarLayout() {
    }

    /**
     * The left edge of {@code slot}'s square at size 1, the bar's 0 to 7 or the racial slot, with the bar in the middle
     * of a span {@code spanWidth} wide: the group's own {@link #WIDTH} for its first slot at 0.
     */
    public static int slotX(int slot, int spanWidth) {
        return spanWidth / 2 - WIDTH / 2 + slot * SLOT;
    }

    /** The resource bar's top edge: right under the slots that begin at {@code top}. */
    public static int resourceBarTop(int top) {
        return top + SLOT;
    }

    /**
     * Where the resource bar's text is drawn: the font's digits cover 7 rows from it, their outline a row more each
     * way, so the outline fills the bar's inner rows but the first and the last.
     */
    public static int resourceTextTop(int barTop) {
        return barTop + 3;
    }

    /**
     * The group's scale: as wide as the hotbar while {@code mirrorHotbar} is on, which overrides the size; else the
     * {@code size}, held to its range.
     */
    public static float scale(double size, boolean mirrorHotbar) {
        if (mirrorHotbar) {
            return (float) HOTBAR_WIDTH / WIDTH;
        }
        return (float) Math.clamp(size, MIN_SIZE, MAX_SIZE);
    }

    /** The Spellbar Size option's value nearest {@code size}: on its step, in its range. */
    public static double stepSize(double size) {
        double steps = Math.round((Math.clamp(size, MIN_SIZE, MAX_SIZE) - MIN_SIZE) / SIZE_STEP);
        return Math.round((MIN_SIZE + steps * SIZE_STEP) * 100) / 100.0;
    }

    /** The Spellbar Size option's value as its row shows it: "1", "0.5", "1.05". */
    public static String sizeText(double size) {
        return BigDecimal.valueOf(stepSize(size)).stripTrailingZeros().toPlainString();
    }

    public static int width(float scale) {
        return Math.round(WIDTH * scale);
    }

    public static int height(float scale) {
        return Math.round(HEIGHT * scale);
    }

    /**
     * The group at {@code scale} on a screen {@code guiWidth} by {@code guiHeight}: at the default place while
     * {@code placeX} or {@code placeY} is {@value #DEFAULT_PLACE}, else at those fractions of the room the screen
     * leaves it each way ({@link #fraction}), so a saved place moves with the screen's size and stays on it.
     */
    public static Group place(int guiWidth, int guiHeight, double placeX, double placeY, float scale) {
        int width = width(scale);
        int height = height(scale);
        if (placeX < 0 || placeY < 0) {
            int bottom = guiHeight - BASE_STACK - GAP;
            return clamp(guiWidth, guiHeight, new Group(position(MIDDLE, guiWidth - width), bottom - height, scale));
        }
        return clamp(guiWidth, guiHeight, new Group(position(placeX, guiWidth - width),
                position(placeY, guiHeight - height), scale));
    }

    /** {@code group} moved as little as it takes to be wholly on the screen; at the left or top if it is too big. */
    public static Group clamp(int guiWidth, int guiHeight, Group group) {
        int x = Math.max(0, Math.min(group.x(), guiWidth - group.width()));
        int y = Math.max(0, Math.min(group.y(), guiHeight - group.height()));
        return new Group(x, y, group.scale());
    }

    /** The left edge of a group in the middle of a screen that leaves it {@code room} px across. */
    public static int middle(int room) {
        return Math.max(0, room / 2);
    }

    /**
     * The fraction that saves a group's edge at {@code position} in the {@code room} the screen leaves it:
     * {@value #MIDDLE} exactly for a group in the middle ({@link #middle}), which keeps it there on any screen.
     */
    public static double fraction(int position, int room) {
        if (room <= 0 || position == middle(room)) {
            return MIDDLE;
        }
        return Math.clamp((double) position / room, 0, 1);
    }

    private static int position(double fraction, int room) {
        return fraction == MIDDLE ? middle(room) : (int) Math.round(fraction * room);
    }

    /**
     * The stack once the group is drawn: when the group lies in the middle columns and reaches down to the action bar
     * message (or the next stacked row) of {@code stack}, the next row goes above the group, ending a pixel above it.
     * A stack that already reaches higher stays as it is, and a group away from the middle columns or above the
     * message leaves it be.
     */
    public static int raised(int guiWidth, int guiHeight, Group group, int stack) {
        int messageTop = guiHeight - Math.max(stack + MESSAGE_OVER_STACK, MESSAGE_LOWEST) - MESSAGE_ABOVE;
        if (!inMiddleColumns(guiWidth, group) || group.bottom() <= messageTop) {
            return stack;
        }
        return Math.max(stack, guiHeight - group.y() + ROW);
    }

    /**
     * The cast bar's top edge: toward the middle of the screen, clear of the group. A group in the middle columns that
     * reaches down to that place puts the cast bar above itself, with room for the action bar message between, or, at
     * the top of the screen, below itself.
     */
    public static int castBarTop(int guiWidth, int guiHeight, Group group) {
        int centered = (int) Math.round(guiHeight * CAST_BAR_CENTER) - CAST_BAR_HEIGHT / 2;
        if (!inMiddleColumns(guiWidth, group) || group.bottom() + CAST_BAR_BELOW <= centered) {
            return centered;
        }
        int above = Math.min(centered, group.y() - CAST_BAR_ABOVE - CAST_BAR_HEIGHT);
        if (above >= 0) {
            return above;
        }
        return Math.min(Math.max(centered, group.bottom() + CAST_BAR_BELOW), guiHeight - CAST_BAR_HEIGHT);
    }

    private static boolean inMiddleColumns(int guiWidth, Group group) {
        int middle = guiWidth / 2;
        return group.x() < middle + MIDDLE_HALF_WIDTH && group.right() > middle - MIDDLE_HALF_WIDTH;
    }
}
