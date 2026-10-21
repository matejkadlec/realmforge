package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.spell.Spellbook;

/**
 * Where the spell bar and the cast bar go (architecture 18; C3b, RLM-25), without world access so JUnit covers it. The
 * bar is as wide as the hotbar and sits right above the status rows and the XP bar, which NeoForge stacks through
 * {@code Gui#leftHeight} and {@code Gui#rightHeight}: eight slots, then the racial slot after a small gap. It raises
 * the stack, so the selected item's name and the action bar message stay above it, and the cast bar goes above those.
 */
public final class SpellBarLayout {
    /** A slot's square. */
    public static final int SLOT = 18;
    /** From one slot's left edge to the next one's. */
    public static final int STEP = 20;
    /** Between the bar's last slot and the racial slot. */
    public static final int RACIAL_GAP = 6;
    /** The bar and the racial slot together: the hotbar's 182 pixels. */
    public static final int WIDTH = (Spellbook.BAR_SLOTS - 1) * STEP + SLOT + RACIAL_GAP + SLOT;
    /** Between the stack below and the bar. */
    public static final int GAP = 2;
    public static final int CAST_BAR_WIDTH = 120;
    public static final int CAST_BAR_HEIGHT = 11;
    /** How far above the raised stack the cast bar's bottom is: room for the item name and the action bar message. */
    public static final int CAST_BAR_ABOVE = 22;

    private SpellBarLayout() {
    }

    /** The left edge of {@code slot}'s square, the bar's 0 to 7 or the racial slot, on a screen {@code guiWidth} wide. */
    public static int slotX(int slot, int guiWidth) {
        int left = guiWidth / 2 - WIDTH / 2;
        return slot < Spellbook.BAR_SLOTS ? left + slot * STEP : left + WIDTH - SLOT;
    }

    /** The slots' top edge, above a stack of status rows {@code stack} high. */
    public static int top(int guiHeight, int stack) {
        return guiHeight - stack - GAP - SLOT;
    }

    /** The stack once the bar is on it, with a pixel of room above. */
    public static int raised(int stack) {
        return stack + GAP + SLOT + 1;
    }

    /** The cast bar's top edge, above the raised stack {@code stack}. */
    public static int castBarTop(int guiHeight, int stack) {
        return guiHeight - stack - CAST_BAR_ABOVE - CAST_BAR_HEIGHT;
    }
}
