package io.github.matejkadlec.realmforge.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matejkadlec.realmforge.Realmforge;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * Key labels that show every character (C3b, RLM-25). Minecraft reads {@code §} in any text as the start of a formatting
 * code, so a key whose label is the section sign (the key right of Ů on a Czech QWERTZ keyboard, Matěj's default for
 * the spell bar's eighth slot) shows as an empty button. The label draws it from our font {@code realmforge:keys}
 * instead ({@code assets/realmforge/font/keys.json}), which maps a private codepoint to vanilla's own section sign glyph
 * in its font sheet, so it looks like any other key label. A spell bar slot's corner shows a short label of three
 * characters, five with a modifier ({@link #spellBarLabel}, RLM-498).
 */
public final class KeyLabels {
    private static final char SECTION_SIGN = '§';
    /** The codepoint {@code realmforge:keys} draws as the section sign. */
    static final String SECTION_GLYPH = "";
    static final FontDescription FONT = new FontDescription.Resource(Realmforge.id("keys"));
    /** The longest label of a single key in a spell bar slot's corner. */
    private static final int SHORT_LENGTH = 3;
    private static final String MOUSE = "key.mouse.";
    private static final String KEYPAD = "key.keyboard.keypad.";
    /**
     * The labels of keys whose names are longer than {@value #SHORT_LENGTH} characters, by their names in
     * {@code InputConstants} (RLM-498; Matěj, 2026-10-29, who reviews the table in the playtest).
     */
    private static final Map<String, String> FIXED = Map.ofEntries(
            Map.entry("key.mouse.left", "LMB"),
            Map.entry("key.mouse.right", "RMB"),
            Map.entry("key.mouse.middle", "MMB"),
            Map.entry("key.keyboard.left.control", "CTR"),
            Map.entry("key.keyboard.right.control", "CTR"),
            Map.entry("key.keyboard.left.shift", "SHI"),
            Map.entry("key.keyboard.right.shift", "SHI"),
            Map.entry("key.keyboard.left.alt", "ALT"),
            Map.entry("key.keyboard.right.alt", "ALT"),
            Map.entry("key.keyboard.left.win", "WIN"),
            Map.entry("key.keyboard.right.win", "WIN"),
            Map.entry("key.keyboard.caps.lock", "CAP"),
            Map.entry("key.keyboard.backspace", "BAC"),
            Map.entry("key.keyboard.print.screen", "PRS"),
            Map.entry("key.keyboard.scroll.lock", "SCL"),
            Map.entry("key.keyboard.num.lock", "NUM"),
            Map.entry("key.keyboard.pause", "PAU"),
            Map.entry("key.keyboard.page.up", "PGU"),
            Map.entry("key.keyboard.page.down", "PGD"),
            Map.entry("key.keyboard.insert", "INS"),
            Map.entry("key.keyboard.delete", "DEL"),
            Map.entry("key.keyboard.home", "HOM"),
            Map.entry("key.keyboard.escape", "ESC"),
            Map.entry("key.keyboard.enter", "ENT"),
            Map.entry("key.keyboard.space", "SPC"),
            Map.entry("key.keyboard.menu", "MNU"),
            Map.entry("key.keyboard.up", "UP"),
            Map.entry("key.keyboard.down", "DWN"),
            Map.entry("key.keyboard.left", "LFT"),
            Map.entry("key.keyboard.right", "RGT"),
            Map.entry("key.keyboard.world.1", "W1"),
            Map.entry("key.keyboard.world.2", "W2"));
    /** The numeric keypad's keys that are not digits, by the end of their names: N and the key's symbol. */
    private static final Map<String, String> KEYPAD_SYMBOLS = Map.of("add", "+", "subtract", "-", "multiply", "*",
            "divide", "/", "decimal", ".", "equal", "=", "enter", "EN");

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

    /**
     * The label of a key bind in a spell bar slot's corner (RLM-498; Matěj, 2026-10-29), on the HUD and in the
     * spellbook alike: the key's short label ({@link #shortLabel(String, String)}), or for a key with a modifier the
     * modifier's, a hyphen and the key's first character ("CTR-R"), with every section sign visible.
     */
    public static Component spellBarLabel(KeyModifier modifier, InputConstants.Key key) {
        return visible(Component.literal(shortLabel(modifier, key.getName(), key.getDisplayName().getString())));
    }

    /**
     * A key bind's label of at most {@value #SHORT_LENGTH} characters, or at most 5 with a modifier ({@code modifier}
     * not {@code NONE}): the modifier's three letters, a hyphen and the first character of the key's own label, so
     * CTRL + R reads CTR-R and CTRL + SHIFT reads CTR-S (Matěj's rule: a longer key is cut to its first character).
     */
    static String shortLabel(KeyModifier modifier, String name, String displayName) {
        String key = shortLabel(name, displayName);
        String prefix = switch (modifier) {
            case CONTROL, CONTROL_OR_COMMAND -> "CTR";
            case SHIFT -> "SHI";
            case ALT -> "ALT";
            case NONE -> "";
        };
        return prefix.isEmpty() ? key : prefix + "-" + key.substring(0, key.offsetByCodePoints(0, 1));
    }

    /**
     * A single key's label of at most {@value #SHORT_LENGTH} characters, by its name in {@code InputConstants}
     * ({@code key.keyboard.page.up}) and the name the game shows ({@code displayName}): the fixed label of
     * {@link #FIXED}, the mouse's buttons as LMB, RMB, MMB and MB4 onwards, the numeric keypad's keys as N and the key
     * (N1, N+), a name of up to three characters as the game shows it (T, F12, Ü, §), and any other name's first three
     * characters in upper case.
     */
    static String shortLabel(String name, String displayName) {
        String fixed = FIXED.get(name);
        if (fixed != null) {
            return fixed;
        }
        if (name.startsWith(MOUSE)) {
            return mouseButton(name.substring(MOUSE.length()));
        }
        if (name.startsWith(KEYPAD)) {
            String key = name.substring(KEYPAD.length());
            String symbol = KEYPAD_SYMBOLS.get(key);
            return symbol != null ? "N" + symbol : first("N" + key.toUpperCase(Locale.ROOT));
        }
        return displayName.codePointCount(0, displayName.length()) <= SHORT_LENGTH ? displayName
                : first(displayName.toUpperCase(Locale.ROOT));
    }

    /** Mouse button {@code button} ("4"): MB4, or M10 once the number has two digits. */
    private static String mouseButton(String button) {
        return first((button.length() > 1 ? "M" : "MB") + button.toUpperCase(Locale.ROOT));
    }

    /** The first {@value #SHORT_LENGTH} characters of {@code text}, or all of it when it is shorter. */
    private static String first(String text) {
        int length = text.codePointCount(0, text.length());
        return length <= SHORT_LENGTH ? text : text.substring(0, text.offsetByCodePoints(0, SHORT_LENGTH));
    }
}
