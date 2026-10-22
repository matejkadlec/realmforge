package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.spell.Spellbook;

/**
 * Where the spell bar and the cast bar go (architecture 18; C3b, RLM-25), without world access so JUnit covers it.
 * NeoForge stacks the status rows and our XP bar through {@code Gui#leftHeight} and {@code Gui#rightHeight}, and
 * vanilla draws the selected item's name on the line above that stack. The bar goes right above that line, its nine
 * slots side by side (the racial slot last), wider than the hotbar (Matěj's playtest of 2026-10-21). It raises the
 * stack once the name is drawn, so the action bar message stays above it; the cast bar goes higher up, toward the
 * middle of the screen.
 */
public final class SpellBarLayout {
    /** A slot's square, a third bigger than a hotbar slot. */
    public static final int SLOT = 24;
    /** The nine slots side by side. */
    public static final int WIDTH = Spellbook.SLOT_COUNT * SLOT;
    /** Between the item name's line and the bar. */
    public static final int GAP = 2;
    /** The least height vanilla draws the item name at ({@code Gui#extractSelectedItemName}). */
    static final int ITEM_NAME_HEIGHT = 59;
    /** Where the player cannot be hurt (creative), vanilla draws the item name 14 px lower. */
    static final int NO_HEARTS_NAME_SHIFT = 14;
    /** A stacked row's height: the font's 9 px and a pixel of room. */
    static final int ROW = 10;

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

    private SpellBarLayout() {
    }

    /** The left edge of {@code slot}'s square, the bar's 0 to 7 or the racial slot, on a screen {@code guiWidth} wide. */
    public static int slotX(int slot, int guiWidth) {
        return guiWidth / 2 - WIDTH / 2 + slot * SLOT;
    }

    /** The top of the line vanilla draws the selected item's name on, above a stack {@code stack} high. */
    static int itemNameTop(int guiHeight, int stack, boolean canHurtPlayer) {
        return guiHeight - Math.max(stack, ITEM_NAME_HEIGHT) + (canHurtPlayer ? 0 : NO_HEARTS_NAME_SHIFT);
    }

    /** The slots' top edge, right above the item name's line. */
    public static int top(int guiHeight, int stack, boolean canHurtPlayer) {
        return itemNameTop(guiHeight, stack, canHurtPlayer) - GAP - SLOT;
    }

    /** The stack once the bar is on it: the next row ends a pixel above the bar. */
    public static int raised(int guiHeight, int top) {
        return guiHeight - top + ROW;
    }

    /** The cast bar's top edge: toward the middle of the screen, and clear of the spell bar at {@code barTop}. */
    public static int castBarTop(int guiHeight, int barTop) {
        int centered = (int) Math.round(guiHeight * CAST_BAR_CENTER) - CAST_BAR_HEIGHT / 2;
        return Math.min(centered, barTop - CAST_BAR_ABOVE - CAST_BAR_HEIGHT);
    }
}
