package io.github.matejkadlec.betterkeybinds;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Focus fixes for a Key Binds screen with a search box (Controlling's), through NeoForge's screen events:
 * <ul>
 *   <li>the screen opens with nothing focused, so the search's suggestions stay closed until the box is clicked
 *       (Controlling focuses it on opening);</li>
 *   <li>before every key, character and click, the screen's focused widget and the box's own focus agree again.
 *       Controlling's Esc unfocuses the box but leaves it the screen's focused widget, and vanilla then ignores a
 *       click on it ({@code AbstractContainerEventHandler#setFocused} skips the widget it already has), so the box
 *       could not be clicked again; its Ctrl+F focuses the box without the screen, which would send the typed keys
 *       nowhere.</li>
 * </ul>
 */
final class KeyBindsScreenFocus {
    private KeyBindsScreenFocus() {
    }

    static void register(IEventBus gameBus) {
        gameBus.addListener(ScreenEvent.Init.Post.class, event -> {
            if (event.getScreen() instanceof KeyBindsScreen screen && screen.getFocused() instanceof EditBox) {
                screen.setFocused(null);
            }
        });
        gameBus.addListener(ScreenEvent.KeyPressed.Pre.class, event -> sync(event.getScreen()));
        gameBus.addListener(ScreenEvent.CharacterTyped.Pre.class, event -> sync(event.getScreen()));
        gameBus.addListener(ScreenEvent.MouseButtonPressed.Pre.class, event -> sync(event.getScreen()));
    }

    private static void sync(Screen screen) {
        if (!(screen instanceof KeyBindsScreen)) {
            return;
        }
        GuiEventListener focused = screen.getFocused();
        if (focused instanceof EditBox box && !box.isFocused()) {
            screen.setFocused(null);
            return;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox box && box.isFocused() && child != focused) {
                screen.setFocused(box);
                return;
            }
        }
    }
}
