package io.github.matejkadlec.realmforge.client.input;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Key labels that show every character (C3b, RLM-25). Minecraft reads {@code §} in any text as the start of a formatting
 * code, so a key whose label is the section sign (the key right of Ů on a Czech QWERTZ keyboard, Matěj's default for
 * the spell bar's eighth slot) shows as an empty button. The label draws it from our font {@code realmforge:keys}
 * instead ({@code assets/realmforge/font/keys.json}), which maps a private codepoint to vanilla's own section sign glyph
 * in its font sheet, so it looks like any other key label.
 */
public final class KeyLabels {
    private static final char SECTION_SIGN = '§';
    /** The codepoint {@code realmforge:keys} draws as the section sign. */
    static final String SECTION_GLYPH = "";
    static final FontDescription FONT = new FontDescription.Resource(Realmforge.id("keys"));
    /** The longest label a spell bar slot's corner shows. */
    private static final int SHORT_LENGTH = 3;

    private KeyLabels() {
    }

    /** {@code label} with every section sign drawn as the glyph; the label itself when it has none. */
    public static Component visible(Component label) {
        String text = label.getString();
        if (text.indexOf(SECTION_SIGN) < 0) {
            return label;
        }
        MutableComponent visible = Component.empty();
        int start = 0;
        for (int at = text.indexOf(SECTION_SIGN); at >= 0; at = text.indexOf(SECTION_SIGN, start)) {
            if (at > start) {
                visible.append(text.substring(start, at));
            }
            visible.append(Component.literal(SECTION_GLYPH).withStyle(Style.EMPTY.withFont(FONT)));
            start = at + 1;
        }
        if (start < text.length()) {
            visible.append(text.substring(start));
        }
        return visible;
    }

    /** A key's label short enough for a spell bar slot's corner: "R", "§", "Shift" as "Shi". */
    public static Component shortLabel(Component label) {
        String text = label.getString();
        return text.length() <= SHORT_LENGTH ? label : Component.literal(text.substring(0, SHORT_LENGTH));
    }
}
