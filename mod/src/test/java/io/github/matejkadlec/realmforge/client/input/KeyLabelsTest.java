package io.github.matejkadlec.realmforge.client.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.client.settings.KeyModifier;
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
    void longKeyNamesGetTheirFixedLabels() {
        assertEquals("RMB", label("key.mouse.right", "Right Button"));
        assertEquals("LMB", label("key.mouse.left", "Left Button"));
        assertEquals("MMB", label("key.mouse.middle", "Middle Button"));
        assertEquals("CTR", label("key.keyboard.left.control", "Left Control"));
        assertEquals("CTR", label("key.keyboard.right.control", "Right Control"));
        assertEquals("SHI", label("key.keyboard.left.shift", "Left Shift"));
        assertEquals("ALT", label("key.keyboard.right.alt", "Right Alt"));
        assertEquals("CAP", label("key.keyboard.caps.lock", "Caps Lock"));
        assertEquals("BAC", label("key.keyboard.backspace", "Backspace"));
        assertEquals("PRS", label("key.keyboard.print.screen", "Print Screen"));
        assertEquals("SCL", label("key.keyboard.scroll.lock", "Scroll Lock"));
        assertEquals("PGU", label("key.keyboard.page.up", "Page Up"));
        assertEquals("PGD", label("key.keyboard.page.down", "Page Down"));
        assertEquals("INS", label("key.keyboard.insert", "Insert"));
        assertEquals("DEL", label("key.keyboard.delete", "Delete"));
        assertEquals("ESC", label("key.keyboard.escape", "Escape"));
        assertEquals("ENT", label("key.keyboard.enter", "Enter"));
        assertEquals("SPC", label("key.keyboard.space", "Space"));
        assertEquals("NUM", label("key.keyboard.num.lock", "Num Lock"));
        assertEquals("PAU", label("key.keyboard.pause", "Pause"));
        assertEquals("HOM", label("key.keyboard.home", "Home"));
        assertEquals("UP", label("key.keyboard.up", "Up Arrow"));
    }

    @Test
    void mouseButtonsAndTheKeypadFollowTheirPattern() {
        assertEquals("MB4", label("key.mouse.4", "Button 4"));
        assertEquals("MB5", label("key.mouse.5", "Button 5"));
        assertEquals("M12", label("key.mouse.12", "Button 12"));
        assertEquals("N1", label("key.keyboard.keypad.1", "Keypad 1"));
        assertEquals("N+", label("key.keyboard.keypad.add", "Keypad +"));
        assertEquals("NEN", label("key.keyboard.keypad.enter", "Keypad Enter"));
    }

    @Test
    void shortNamesStayAndUnknownLongOnesAreCutToThreeCapitals() {
        assertEquals("T", label("key.keyboard.t", "T"));
        assertEquals("F12", label("key.keyboard.f12", "F12"));
        assertEquals("Ü", label("scancode.42", "Ü"));
        assertEquals("End", label("key.keyboard.end", "End"));
        assertEquals("NOT", label("key.keyboard.unknown", "Not Bound"));
        assertEquals("ŮŘŠ", label("scancode.7", "ůřšá"));
    }

    @Test
    void aTwoKeyBindIsTheModifierAHyphenAndTheKeysFirstCharacter() {
        assertEquals("CTR-R", KeyLabels.shortLabel(KeyModifier.CONTROL, "key.keyboard.r", "R"));
        assertEquals("ALT-C", KeyLabels.shortLabel(KeyModifier.ALT, "key.keyboard.c", "C"));
        assertEquals("SHI-C", KeyLabels.shortLabel(KeyModifier.SHIFT, "key.keyboard.c", "C"));
        assertEquals("CTR-S", KeyLabels.shortLabel(KeyModifier.CONTROL, "key.keyboard.left.shift", "Left Shift"));
        assertEquals("CTR-S", KeyLabels.shortLabel(KeyModifier.CONTROL, "key.keyboard.s", "S"));
        assertEquals("CTR-F", KeyLabels.shortLabel(KeyModifier.CONTROL_OR_COMMAND, "key.keyboard.f1", "F1"));
        assertEquals("ALT-R", KeyLabels.shortLabel(KeyModifier.ALT, "key.mouse.right", "Right Button"));
        assertEquals("R", KeyLabels.shortLabel(KeyModifier.NONE, "key.keyboard.r", "R"));
    }

    @Test
    void theSpellBarLabelKeepsTheSectionSignVisible() {
        Component section = KeyLabels.visible(Component.literal(
                KeyLabels.shortLabel(KeyModifier.SHIFT, "key.keyboard.apostrophe", "§")));
        assertEquals("SHI-" + KeyLabels.SECTION_GLYPH, section.getString());
    }

    private static String label(String name, String displayName) {
        return KeyLabels.shortLabel(name, displayName);
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
