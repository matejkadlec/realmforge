package io.github.matejkadlec.realmforge.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class MobLevelLabelTest {
    /** RLM-338's Done when: the colour at every boundary of the table. */
    @Test
    void theColourFollowsTheLevelDifference() {
        assertEquals(MobLevelLabel.GRAY, MobLevelLabel.color(-20));
        assertEquals(MobLevelLabel.GRAY, MobLevelLabel.color(-5));
        assertEquals(MobLevelLabel.GRAY, MobLevelLabel.color(-4));
        assertEquals(MobLevelLabel.GREEN, MobLevelLabel.color(-3));
        assertEquals(MobLevelLabel.YELLOW, MobLevelLabel.color(-2));
        assertEquals(MobLevelLabel.YELLOW, MobLevelLabel.color(0));
        assertEquals(MobLevelLabel.YELLOW, MobLevelLabel.color(2));
        assertEquals(MobLevelLabel.ORANGE, MobLevelLabel.color(3));
        assertEquals(MobLevelLabel.RED, MobLevelLabel.color(4));
        assertEquals(MobLevelLabel.SKULL_RED, MobLevelLabel.color(5));
        assertEquals(MobLevelLabel.SKULL_RED, MobLevelLabel.color(20));
        assertEquals(List.of(0xAAAAAA, 0x55FF55, 0xFFFF55, 0xFF9933, 0xFF5555, 0xE02020),
                List.of(MobLevelLabel.GRAY, MobLevelLabel.GREEN, MobLevelLabel.YELLOW, MobLevelLabel.ORANGE,
                        MobLevelLabel.RED, MobLevelLabel.SKULL_RED), "Matěj's colours");
    }

    @Test
    void theSkullShowsFromFiveLevelsAbove() {
        assertFalse(MobLevelLabel.skull(4));
        assertTrue(MobLevelLabel.skull(5));
        assertTrue(MobLevelLabel.skull(20));
        assertFalse(MobLevelLabel.skull(-20));
    }

    @Test
    void aLevelReadsLikeTheXpBarsInItsColour() {
        Component level = MobLevelLabel.level(20, 17);
        TranslatableContents contents = translatable(level);
        assertEquals(GeneratedTexts.GUI_XP_BAR_LEVEL, contents.getKey());
        assertEquals(List.of(20), List.of(contents.getArgs()));
        assertEquals(TextColor.fromRgb(MobLevelLabel.ORANGE), level.getStyle().getColor());
        assertFalse(level.getStyle().isBold());
    }

    @Test
    void fiveLevelsAboveShowsTheBloodRedSkullRightAfterABoldBloodRedLv() {
        Component level = MobLevelLabel.level(30, 25);
        assertEquals(TextColor.fromRgb(MobLevelLabel.SKULL_RED), level.getStyle().getColor());
        assertTrue(level.getStyle().isBold());
        Object[] args = translatable(level).getArgs();
        assertEquals(1, args.length);
        Component skull = (Component) args[0];
        assertEquals(MobLevelLabel.STEP_BACK + MobLevelLabel.SKULL, skull.getString(), "no space after the dot");
        Style style = skull.getStyle();
        assertEquals(MobLevelLabel.SYMBOLS, style.getFont());
        assertEquals(TextColor.fromRgb(MobLevelLabel.SKULL_RED), style.getColor(), "tints the skull, not its eyes");
        assertFalse(style.isBold(), "a bold glyph is drawn twice, a pixel apart, and a bold space is a pixel wider");
    }

    @Test
    void theNameStaysWhiteAndUpright() {
        Component line = MobLevelLabel.line(12, 12, Component.literal("Bob").withStyle(ChatFormatting.ITALIC,
                ChatFormatting.GOLD));
        List<Component> parts = line.getSiblings();
        assertEquals(3, parts.size());
        assertEquals(GeneratedTexts.GUI_XP_BAR_LEVEL, translatable(parts.get(0)).getKey());
        assertEquals(" ", parts.get(1).getString());
        Component name = parts.get(2);
        assertEquals("Bob", name.getString());
        assertFalse(name.getStyle().isItalic());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.WHITE), name.getStyle().getColor());
    }

    /** The font and the code points the label uses are the ones {@code rf gen} writes ({@code generate/fonts.py}). */
    @Test
    void theSkullAndTheStepBackAreInTheGeneratedFont() throws IOException {
        FontDescription.Resource font = (FontDescription.Resource) MobLevelLabel.SYMBOLS;
        String path = "/assets/" + font.id().getNamespace() + "/font/" + font.id().getPath() + ".json";
        JsonArray providers;
        try (InputStream stream = MobLevelLabelTest.class.getResourceAsStream(path)) {
            assertTrue(stream != null, path + " is missing; run `uv run rf gen`");
            providers = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("providers");
        }
        JsonObject bitmap = providers.get(0).getAsJsonObject();
        assertEquals("bitmap", bitmap.get("type").getAsString());
        assertTrue(bitmap.getAsJsonArray("chars").get(0).getAsString().contains(MobLevelLabel.SKULL));
        assertEquals("realmforge:font/symbols.png", bitmap.get("file").getAsString());
        JsonObject space = providers.get(1).getAsJsonObject();
        assertEquals("space", space.get("type").getAsString());
        assertEquals(-5, space.getAsJsonObject("advances").get(MobLevelLabel.STEP_BACK).getAsInt(),
                "the default font's space (4) and the bold pixel");
        assertTrue(MobLevelLabelTest.class.getResource("/assets/realmforge/textures/font/symbols.png") != null,
                "the symbols texture");
    }

    private static TranslatableContents translatable(Component component) {
        assertTrue(component.getContents() instanceof TranslatableContents, component + " is not translatable");
        return (TranslatableContents) component.getContents();
    }
}
