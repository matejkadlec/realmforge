package io.github.matejkadlec.realmforge.client.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class KeyLabelsTest {
    @Test
    void aLabelWithoutTheSectionSignStaysAsItIs() {
        Component label = Component.literal("R");
        assertSame(label, KeyLabels.visible(label));
    }

    @Test
    void theSectionSignIsDrawnFromOurFont() {
        Component visible = KeyLabels.visible(Component.literal("Shift + §"));
        assertEquals("Shift + " + KeyLabels.SECTION_GLYPH, visible.getString());
        assertEquals(List.of(FontDescription.DEFAULT, KeyLabels.FONT), fonts(visible));
    }

    @Test
    void shortLabelsKeepTheirGlyphAndLongOnesAreCut() {
        Component section = KeyLabels.visible(Component.literal("§"));
        assertSame(section, KeyLabels.shortLabel(section));
        assertEquals("Shi", KeyLabels.shortLabel(Component.literal("Shift")).getString());
    }

    /** The font of every non-empty piece of text in {@code component}, in order. */
    private static List<FontDescription> fonts(Component component) {
        List<FontDescription> fonts = new ArrayList<>();
        component.visit((Style style, String text) -> {
            if (!text.isEmpty()) {
                fonts.add(style.getFont());
            }
            return Optional.empty();
        }, Style.EMPTY);
        return fonts;
    }
}
