package io.github.matejkadlec.betterkeybinds;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matejkadlec.betterkeybinds.mixin.KeyBindsScreenAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.InputQuirks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.jspecify.annotations.Nullable;

/**
 * A pressed secondary key button waits for its key, as a primary one does: keys and mouse buttons pressed on the Key
 * Binds screen go to {@link KeyCapture} instead of the screen, through NeoForge's screen events, so this works on
 * vanilla's screen and on any screen that extends it (Controlling's) without replacing it. Esc unbinds; a mouse button
 * binds at once, with the modifier key held at that moment.
 */
public final class SecondaryKeyCapture {
    // Mac systems send scan code 63 (only as a release) when Fn is pressed; the screen ignores it too.
    private static final int MAC_FN_SCANCODE = 63;

    private static @Nullable KeyMapping waitingFor;
    private static KeyCapture<InputConstants.Key> capture = new KeyCapture<>();

    private SecondaryKeyCapture() {
    }

    static void register(IEventBus gameBus) {
        gameBus.addListener(ScreenEvent.KeyPressed.Pre.class, SecondaryKeyCapture::onKeyPressed);
        gameBus.addListener(ScreenEvent.KeyReleased.Pre.class, SecondaryKeyCapture::onKeyReleased);
        gameBus.addListener(ScreenEvent.MouseButtonPressed.Pre.class, SecondaryKeyCapture::onMouseButtonPressed);
        gameBus.addListener(ScreenEvent.CharacterTyped.Pre.class, SecondaryKeyCapture::onCharacterTyped);
        gameBus.addListener(ScreenEvent.Closing.class, event -> waitingFor = null);
    }

    /** The secondary key button of {@code primary} was pressed: wait for its key, and stop waiting for a primary. */
    static void start(KeyMapping primary) {
        if (!(Minecraft.getInstance().screen instanceof KeyBindsScreen screen)) {
            return;
        }
        waitingFor = primary;
        capture = new KeyCapture<>();
        screen.selectedKey = null;
        refresh(screen);
    }

    /** Whether the secondary key button of {@code primary} waits for its key. */
    public static boolean isWaitingFor(KeyMapping primary) {
        return waitingFor == primary;
    }

    private static @Nullable KeyBindsScreen waitingScreen(ScreenEvent event) {
        if (waitingFor == null || !(event.getScreen() instanceof KeyBindsScreen screen)) {
            return null;
        }
        // A primary key button pressed meanwhile (with the keyboard) takes over.
        if (screen.selectedKey != null) {
            waitingFor = null;
            return null;
        }
        return screen;
    }

    private static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (waitingScreen(event) != null) {
            InputConstants.Key key = InputConstants.getKey(event.getKeyEvent());
            capture.press(key, KeyModifier.isKeyCodeModifier(key));
            event.setCanceled(true);
        }
    }

    private static void onKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        KeyBindsScreen screen = waitingScreen(event);
        if (screen == null || (InputQuirks.ON_OSX && event.getScanCode() == MAC_FN_SCANCODE)) {
            return;
        }
        event.setCanceled(true);
        KeyCapture.Result<InputConstants.Key> result = capture.release(InputConstants.getKey(event.getKeyEvent()),
                event.getKeyEvent().isEscape());
        if (result != null) {
            finish(screen, result.unbinds() ? InputConstants.UNKNOWN : result.key(), modifier(result.modifier()));
        }
    }

    private static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        KeyBindsScreen screen = waitingScreen(event);
        if (screen != null) {
            event.setCanceled(true);
            finish(screen, InputConstants.Type.MOUSE.getOrCreate(event.getButton()), modifier(capture.heldModifier()));
        }
    }

    private static void onCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        // The key's character must not reach a focused text box (Controlling's search) while it is being bound.
        if (waitingScreen(event) != null) {
            event.setCanceled(true);
        }
    }

    private static void finish(KeyBindsScreen screen, InputConstants.Key key, KeyModifier modifier) {
        KeyMapping primary = waitingFor;
        waitingFor = null;
        if (primary != null) {
            SecondaryKeys.bind(primary, modifier, key);
        }
        refresh(screen);
    }

    private static KeyModifier modifier(InputConstants.@Nullable Key modifierKey) {
        return modifierKey == null ? KeyModifier.NONE : KeyModifier.getKeyModifier(modifierKey);
    }

    // Rebuilds vanilla's key lookup and relabels every row, as the screen does after a primary key changes.
    private static void refresh(KeyBindsScreen screen) {
        ((KeyBindsScreenAccessor) screen).betterKeyBinds$keyBindsList().resetMappingAndUpdateButtons();
    }
}
