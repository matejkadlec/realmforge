package io.github.matejkadlec.betterkeybinds;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

/** Small helpers for the row mixins of vanilla's and Controlling's Key Binds lists. */
public final class KeyBindsRows {
    private KeyBindsRows() {
    }

    /**
     * A row's children (or narratables) with its secondary key button after the primary one, so clicks and narration
     * reach it. The row's own list, unchanged, while the button does not exist yet.
     */
    public static <T> List<T> withSecondaryButton(List<T> children, Button secondaryButton) {
        if (secondaryButton == null) {
            return children;
        }
        List<T> withButton = new ArrayList<>(children);
        // The rows hold the primary button first and Reset second; the secondary sits between them, as on screen.
        @SuppressWarnings("unchecked")
        T button = (T) secondaryButton;
        withButton.add(Math.min(1, withButton.size()), button);
        return withButton;
    }

    /** Whether the Key Binds screen waits for the primary key of {@code key} ("> key <" on its button). */
    public static boolean isSelected(KeyMapping key) {
        return Minecraft.getInstance().screen instanceof KeyBindsScreen screen && screen.selectedKey == key;
    }
}
