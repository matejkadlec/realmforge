package io.github.matejkadlec.betterkeybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.betterkeybinds.KeyCapture.Result;
import org.junit.jupiter.api.Test;

class KeyCaptureTest {
    private final KeyCapture<String> capture = new KeyCapture<>();

    @Test
    void aKeyBindsOnRelease() {
        capture.press("T", false);

        assertEquals(new Result<>("T", null), capture.release("T", false));
    }

    @Test
    void aModifierHeldFirstBecomesTheModifier() {
        capture.press("Ctrl", true);
        capture.press("T", false);

        assertNull(capture.release("T", false), "Ctrl is still held");
        assertEquals(new Result<>("T", "Ctrl"), capture.release("Ctrl", false));
    }

    @Test
    void theReleaseOrderDoesNotMatter() {
        capture.press("Ctrl", true);
        capture.press("T", false);

        assertNull(capture.release("Ctrl", false), "T is still held");
        assertEquals(new Result<>("T", "Ctrl"), capture.release("T", false));
    }

    @Test
    void aModifierAloneBecomesTheKey() {
        capture.press("Shift", true);

        assertEquals(new Result<>("Shift", null), capture.release("Shift", false));
    }

    @Test
    void aSecondModifierIsTheKey() {
        capture.press("Ctrl", true);
        capture.press("Shift", true);
        capture.release("Shift", false);

        assertEquals(new Result<>("Shift", "Ctrl"), capture.release("Ctrl", false));
    }

    @Test
    void escapeUnbinds() {
        capture.press("Escape", false);

        Result<String> result = capture.release("Escape", true);

        assertTrue(result.unbinds());
    }

    @Test
    void aReleaseBeforeAnyPressIsIgnored() {
        // The Enter that pressed the button is released after the capture started.
        assertNull(capture.release("Enter", false));
        capture.press("T", false);

        assertEquals(new Result<>("T", null), capture.release("T", false));
    }

    @Test
    void theHeldModifierIsKnownForAMouseButton() {
        assertNull(capture.heldModifier());
        capture.press("Shift", true);

        assertEquals("Shift", capture.heldModifier());
    }
}
