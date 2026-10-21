package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** A description's placeholders (architecture 6.3): filling them, and how seconds and blocks read. */
class SpellTextTest {
    private static final ResourceKey<SpellSchool> FIRE_KEY = ResourceKey.create(CombatRegistries.SPELL_SCHOOL,
            Realmforge.id("fire"));
    private static final SpellSchool FIRE = new SpellSchool("Fire", SpellSchool.Kind.MAGIC, 0xFF7F1F, 0xFF7F1F,
            ResourceKey.create(Registries.DAMAGE_TYPE, Realmforge.id("fire")), Optional.empty(), Optional.empty());
    private static final Function<ResourceKey<SpellSchool>, Optional<SpellSchool>> SCHOOLS =
            key -> key.equals(FIRE_KEY) ? Optional.of(FIRE) : Optional.empty();

    @Test
    void placeholdersTakeTheirValues() {
        assertEquals("Hurls a bolt for 17 damage, slowing it for 3 sec.", fill(
                "Hurls a bolt for {damage} damage, slowing it for {duration} sec.",
                Map.of("damage", "17", "duration", "3")));
        assertEquals("17 and 17", fill("{damage} and {damage}", Map.of("damage", "17")));
    }

    @Test
    void aPlaceholderWithoutAValueStaysAsItIs() {
        assertEquals("Heals for {heal}.", fill("Heals for {heal}.", Map.of("damage", "4")));
        assertEquals("A $1 brace {", fill("A $1 brace {", Map.of()));
    }

    @Test
    void valuesWithDollarsAndBackslashesAreTakenLiterally() {
        assertEquals("costs $5\\", fill("costs {damage}", Map.of("damage", "$5\\")));
    }

    @Test
    void aDamageNumberShowsItsSchoolsIconAndColour() {
        Component filled = SpellText.fill("for {damage} damage", Map.of("damage", "17"), Map.of("damage", FIRE_KEY),
                SCHOOLS);
        assertEquals("for Fire 17 damage", filled.getString(), "as plain text the icon reads as the school's name");
        List<Style> styles = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        filled.visit((style, text) -> {
            styles.add(style);
            texts.add(text);
            return Optional.empty();
        }, Style.EMPTY);
        int icon = texts.indexOf("￼");
        assertEquals(new FontDescription.AtlasSprite(Identifier.withDefaultNamespace("gui"), Realmforge.id("school/fire")),
                styles.get(icon).getFont(), "the school's GUI sprite, drawn as a glyph");
        assertEquals(TextColor.fromRgb(0xFF7F1F), styles.get(texts.indexOf("17")).getColor());
    }

    @Test
    void secondsAndBlocksKeepUpToTwoDecimals() {
        assertEquals("3", SpellText.decimal(3.0));
        assertEquals("1.5", SpellText.decimal(1.5));
        assertEquals("0.05", SpellText.decimal(0.05));
        assertEquals("2.13", SpellText.decimal(2.125));
        assertEquals("16", SpellText.decimal(16.004));
    }

    private static String fill(String template, Map<String, String> values) {
        return SpellText.fill(template, values, Map.of(), SCHOOLS).getString();
    }
}
