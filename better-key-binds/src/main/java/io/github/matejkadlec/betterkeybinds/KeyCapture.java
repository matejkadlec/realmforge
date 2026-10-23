package io.github.matejkadlec.betterkeybinds;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Records the keys pressed while a secondary key button waits for its key, the way NeoForge's Key Binds screen does
 * for the primary key: the first modifier key pressed (Ctrl, Shift, Alt) becomes the modifier, any other key the key,
 * and the binding is complete once every key pressed has been released. A modifier pressed alone becomes the key
 * itself. Esc unbinds. Unlike the screen, a release before any press (the Enter or Space that pressed the button) is
 * ignored instead of unbinding. Generic over the key type, so the rules are tested without Minecraft.
 *
 * @param <K> the key type, {@code InputConstants.Key} in the game
 */
public final class KeyCapture<K> {
    private @Nullable K key;
    private @Nullable K modifier;
    private boolean keyHeld;
    private boolean modifierHeld;

    /**
     * The capture's result: {@code key} with {@code modifier} (the modifier key pressed, or null), or no key at all
     * when Esc unbound it.
     */
    public record Result<K>(@Nullable K key, @Nullable K modifier) {
        public boolean unbinds() {
            return key == null;
        }
    }

    /** A key went down; {@code isModifierKey} tells whether it is Ctrl, Shift or Alt (or Cmd on macOS). */
    public void press(K pressed, boolean isModifierKey) {
        if (modifier == null && isModifierKey) {
            modifier = pressed;
            modifierHeld = true;
        } else {
            key = pressed;
            keyHeld = true;
        }
    }

    /** A key went up: the result once the binding is complete, or null while keys are still held. */
    public @Nullable Result<K> release(K released, boolean escape) {
        if (escape) {
            return new Result<>(null, null);
        }
        if (key == null && modifier == null) {
            return null;
        }
        if (Objects.equals(released, key)) {
            keyHeld = false;
        } else if (Objects.equals(released, modifier)) {
            modifierHeld = false;
        }
        if (keyHeld || modifierHeld) {
            return null;
        }
        return key != null ? new Result<>(key, modifier) : new Result<>(modifier, null);
    }

    /** The modifier key held right now, which a mouse button pressed now is combined with; null without one. */
    public @Nullable K heldModifier() {
        return modifierHeld ? modifier : null;
    }
}
