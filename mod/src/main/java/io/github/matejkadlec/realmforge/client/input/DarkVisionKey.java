package io.github.matejkadlec.realmforge.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.world.net.ToggleDarkVisionPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

/**
 * The Dark Vision key (N by default, rebindable in Options > Controls under Realmforge): each press asks the server to
 * flip the player's Dark Vision (architecture 19.1). The server decides; the client only sends the intent.
 */
public final class DarkVisionKey {
    /** The heading of our keys; its name is the text {@code key.category.realmforge.main} (content/texts/). */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Realmforge.id("main"));

    public static final Lazy<KeyMapping> KEY = Lazy.of(() -> new KeyMapping(
            GeneratedTexts.KEY_DARK_VISION,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY));

    private DarkVisionKey() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.registerCategory(CATEGORY);
            event.register(KEY.get());
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onClientTick());
    }

    private static void onClientTick() {
        while (KEY.get().consumeClick()) {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            // A server without Realmforge (or without this payload) would drop the connection for an unknown payload.
            if (connection != null && connection.hasChannel(ToggleDarkVisionPayload.TYPE)) {
                ClientPacketDistributor.sendToServer(ToggleDarkVisionPayload.INSTANCE);
            }
        }
    }
}
