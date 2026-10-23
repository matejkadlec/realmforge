package io.github.matejkadlec.betterkeybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.betterkeybinds.SecondaryKeysFile.Binding;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecondaryKeysFileTest {
    @Test
    void readsKeysWithAndWithoutModifier() {
        Map<String, Binding> bindings = SecondaryKeysFile.parse(List.of(
                "key.chat:key.keyboard.t",
                "key.inventory:key.keyboard.e:SHIFT",
                "key.pickItem:key.mouse.4:NONE"));

        assertEquals(Map.of(
                "key.chat", new Binding("key.keyboard.t", "NONE"),
                "key.inventory", new Binding("key.keyboard.e", "SHIFT"),
                "key.pickItem", new Binding("key.mouse.4", "NONE")), bindings);
    }

    @Test
    void skipsCommentsBlankAndMalformedLines() {
        Map<String, Binding> bindings = SecondaryKeysFile.parse(List.of(
                "# a comment",
                "",
                "   ",
                "no colon here",
                ":key.keyboard.t",
                "key.chat:",
                "  key.jump:key.keyboard.space  "));

        assertEquals(Map.of("key.jump", new Binding("key.keyboard.space", "NONE")), bindings);
    }

    @Test
    void keyBindNamesMayHoldColons() {
        Map<String, Binding> bindings = SecondaryKeysFile.parse(List.of(
                "mod:key.open:key.keyboard.o",
                "mod:key.close:key.keyboard.c:CONTROL"));

        assertEquals(Map.of(
                "mod:key.open", new Binding("key.keyboard.o", "NONE"),
                "mod:key.close", new Binding("key.keyboard.c", "CONTROL")), bindings);
    }

    @Test
    void aLaterLineForTheSameKeyBindWins() {
        Map<String, Binding> bindings = SecondaryKeysFile.parse(List.of(
                "key.chat:key.keyboard.t",
                "key.chat:key.keyboard.y"));

        assertEquals(Map.of("key.chat", new Binding("key.keyboard.y", "NONE")), bindings);
    }

    @Test
    void writesAHeaderThenSortedLinesWithoutNoneModifiers() {
        Map<String, Binding> bindings = new LinkedHashMap<>();
        bindings.put("key.inventory", new Binding("key.keyboard.e", "SHIFT"));
        bindings.put("key.chat", new Binding("key.keyboard.t", "NONE"));

        List<String> lines = SecondaryKeysFile.write(bindings);

        assertTrue(lines.getFirst().startsWith("# Better Key Binds"));
        assertEquals(List.of("key.chat:key.keyboard.t", "key.inventory:key.keyboard.e:SHIFT"),
                lines.stream().filter(line -> !line.startsWith("#")).toList());
    }

    @Test
    void whatIsWrittenReadsBackTheSame() {
        Map<String, Binding> bindings = Map.of(
                "key.chat", new Binding("key.keyboard.t", "NONE"),
                "key.attack", new Binding("key.mouse.5", "ALT"),
                "mod:key.odd", new Binding("key.keyboard.keypad.add", "CONTROL_OR_COMMAND"));

        assertEquals(bindings, SecondaryKeysFile.parse(SecondaryKeysFile.write(bindings)));
    }
}
