package io.github.matejkadlec.realmforge.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/** Matěj's format of 2026-10-29 (RLM-520): "Welcome to Realmforge v0.0.1 (Alpha)". */
class WelcomeMessageTest {
    @Test
    void theVersionDropsTheMinecraftSuffix() {
        assertEquals("0.0.1", WelcomeMessage.version("0.0.1+mc26.1.2"));
        assertEquals("1.0.0", WelcomeMessage.version("1.0.0"));
    }

    @Test
    void thePhaseIsTrimmedAndMissingMeansNone() {
        assertEquals("Alpha", WelcomeMessage.phase(" Alpha "));
        assertEquals("", WelcomeMessage.phase(null));
        assertEquals("", WelcomeMessage.phase(""));
    }

    @Test
    void aPhaseGoesInParentheses() {
        TranslatableContents line = contents(WelcomeMessage.message("0.0.1", "Alpha"));
        assertEquals(GeneratedTexts.MESSAGE_WELCOME_PHASE, line.getKey());
        assertArrayEquals(new Object[] {"0.0.1", "Alpha"}, line.getArgs());
    }

    @Test
    void withoutAPhaseTheLineHasNoParentheses() {
        TranslatableContents line = contents(WelcomeMessage.message("1.0.0", ""));
        assertEquals(GeneratedTexts.MESSAGE_WELCOME, line.getKey());
        assertArrayEquals(new Object[] {"1.0.0"}, line.getArgs());
    }

    private static TranslatableContents contents(Component component) {
        return (TranslatableContents) component.getContents();
    }
}
