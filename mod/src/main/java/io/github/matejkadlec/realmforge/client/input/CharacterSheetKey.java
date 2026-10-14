package io.github.matejkadlec.realmforge.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matejkadlec.realmforge.client.screen.CharacterSheetScreen;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

/**
 * The character sheet key (K by default, rebindable in Options > Controls under Realmforge; vanilla's C is the creative
 * hotbar key): opens {@link CharacterSheetScreen} in game, and the same key closes it.
 */
public final class CharacterSheetKey {
    public static final Lazy<KeyMapping> KEY = Lazy.of(() -> new KeyMapping(
            GeneratedTexts.KEY_CHARACTER_SHEET,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            DarkVisionKey.CATEGORY));

    private CharacterSheetKey() {
    }

    /** Called once from {@code RealmforgeClient}, after {@link DarkVisionKey}, which registers our key category. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(KEY.get()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onClientTick());
    }

    private static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        while (KEY.get().consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new CharacterSheetScreen());
            }
        }
    }
}
