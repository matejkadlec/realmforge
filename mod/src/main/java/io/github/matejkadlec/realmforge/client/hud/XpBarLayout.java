package io.github.matejkadlec.realmforge.client.hud;

/**
 * Where {@link XpBarHud} draws the XP bar, in GUI pixels from the top of the screen, as plain arithmetic for JUnit.
 * NeoForge stacks the status rows (health, armor, food, air) through {@code Gui#leftHeight} and
 * {@code Gui#rightHeight}: a row stacked at height {@code h} is drawn from {@code guiHeight - h}, 9 px tall, and adds
 * 10 to the height, so the highest row starts at {@code guiHeight - stack + 10}.
 */
final class XpBarLayout {
    /** As wide as the hotbar and as tall as vanilla's experience bar. */
    static final int WIDTH = 182;
    static final int HEIGHT = 5;
    /**
     * The least stack the bar goes above: the health row's, also where no status row is drawn (creative), which keeps
     * the bar clear of vanilla's experience level ({@code guiHeight - 35}, 9 px).
     */
    static final int MIN_STACK = 49;
    /** What the bar and its level text add to the stack. */
    static final int STACK = 14;
    /**
     * Where the player cannot be hurt (creative), vanilla draws the selected item's name 14 px lower than the stack
     * says, as no heart row is there ({@code Gui#extractSelectedItemName}); the bar is, so the stack grows by as much.
     */
    static final int NO_HEARTS_NAME_SHIFT = 14;
    /** The font's line height: text drawn at {@code y} covers {@code y} to {@code y + 8}. */
    static final int TEXT_HEIGHT = 9;

    private XpBarLayout() {
    }

    /** The stack the bar goes above: the higher of NeoForge's two heights, at least {@value #MIN_STACK}. */
    static int stack(int leftHeight, int rightHeight) {
        return Math.max(MIN_STACK, Math.max(leftHeight, rightHeight));
    }

    /** The bar's top edge: its {@value #HEIGHT} px end 2 px above the highest status row. */
    static int barTop(int guiHeight, int stack) {
        return guiHeight - stack + 3;
    }

    /** The level text's top edge: its {@value #TEXT_HEIGHT} px overlap the bar's top 2 px, like vanilla's level. */
    static int textTop(int barTop) {
        return barTop - 7;
    }

    /** NeoForge's heights once the bar is drawn, so later rows and the selected item's name stay above it. */
    static int raisedStack(int stack, boolean canHurtPlayer) {
        return stack + STACK + (canHurtPlayer ? 0 : NO_HEARTS_NAME_SHIFT);
    }
}
