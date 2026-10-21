package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** A description's placeholders (architecture 6.3): filling them, and how seconds and blocks read. */
class SpellTextTest {
    @Test
    void placeholdersTakeTheirValues() {
        assertEquals("Hurls a bolt for 17 Fire damage, slowing it for 3 sec.", SpellText.fill(
                "Hurls a bolt for {damage} Fire damage, slowing it for {duration} sec.",
                Map.of("damage", "17", "duration", "3")));
        assertEquals("17 and 17", SpellText.fill("{damage} and {damage}", Map.of("damage", "17")));
    }

    @Test
    void aPlaceholderWithoutAValueStaysAsItIs() {
        assertEquals("Heals for {heal}.", SpellText.fill("Heals for {heal}.", Map.of("damage", "4")));
        assertEquals("A $1 brace {", SpellText.fill("A $1 brace {", Map.of()));
    }

    @Test
    void valuesWithDollarsAndBackslashesAreTakenLiterally() {
        assertEquals("costs $5\\", SpellText.fill("costs {damage}", Map.of("damage", "$5\\")));
    }

    @Test
    void secondsAndBlocksKeepUpToTwoDecimals() {
        assertEquals("3", SpellText.decimal(3.0));
        assertEquals("1.5", SpellText.decimal(1.5));
        assertEquals("0.05", SpellText.decimal(0.05));
        assertEquals("2.13", SpellText.decimal(2.125));
        assertEquals("16", SpellText.decimal(16.004));
    }
}
