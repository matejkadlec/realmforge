package io.github.matejkadlec.realmforge.client.screen;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The spellbook's page (RLM-609; Matěj, 2026-10-21: "a real spellbook, a background texture of a book with a single
 * page open"): the book texture behind both tabs, and the inks its texts are written in. The texture is the nine-slice
 * GUI sprite {@code realmforge:spellbook/book} ({@code generate/spellbook.py}): the window's content lies on the page,
 * and the cover and the spine reach out round it. The page is light, so the texts are dark and have no shadow, which
 * would smear them; only what stands on something dark (a slot, a button) stays white.
 */
final class SpellbookPage {
    private static final Identifier BOOK = Realmforge.id("spellbook/book");
    /** The sprite's borders, as {@code generate/spellbook.py} draws them: the spine on the left, the cover's edges. */
    private static final int SPINE = 14;
    private static final int EDGE = 10;

    /** The ink of names and numbers: the palette's wood 2. */
    static final int INK = 0xFF2F2115;
    /** A heading's ink, the red of a manuscript's rubric: the palette's blood 3. */
    static final int RUBRIC = 0xFF7C1D1D;
    /** What is marked or chosen, a brighter red: the palette's blood 4. */
    static final int MARK = 0xFFA32924;
    /** A hint, or what is not available: the palette's wood 5. */
    static final int FAINT = 0xFF6D5132;
    /** What can be clicked but is not chosen: the palette's wood 6, and wood 4 under the mouse. */
    static final int IDLE = 0xFF856540;
    static final int IDLE_HOVER = 0xFF573F26;
    /** A thin line on the page, and the light shade under the mouse. */
    static final int RULE = 0x502F2115;
    static final int HOVER = 0x282F2115;

    private SpellbookPage() {
    }

    /** Draws the book so that its page covers the rectangle from left, top to right, bottom. */
    static void draw(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BOOK, left - SPINE, top - EDGE, right - left + SPINE + EDGE,
                bottom - top + 2 * EDGE);
    }

    /** {@code text} in {@code ink}, its middle at {@code centerX}, without a shadow. */
    static void centered(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int ink) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, ink, false);
    }
}
