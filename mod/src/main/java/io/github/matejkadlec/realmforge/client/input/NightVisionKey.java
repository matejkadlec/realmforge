package io.github.matejkadlec.realmforge.client.input;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.world.net.ToggleNightVisionPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

/**
 * The Night Vision key (¨ by default, rebindable in Options > Controls under Realmforge): each press asks the server to
 * flip the player's Night Vision (architecture 19.1). The server decides; the client only sends the intent. It moved
 * from H, one of the spell bar's keys (Matěj, 2026-10-15), to ¨ (C3b, RLM-25, ADR 0046; Matěj's playtest of
 * 2026-10-21): the key by Enter on his Czech QWERTZ keyboard, a US keyboard's {@code \}, free in the pack.
 */
public final class NightVisionKey {
    /** The heading of our keys; its name is the text {@code key.category.realmforge.main} (content/texts/). */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Realmforge.id("main"));

    public static final Lazy<KeyMapping> KEY = Lazy.of(() -> new RealmforgeKeyMapping(
            GeneratedTexts.KEY_NIGHT_VISION, GLFW.GLFW_KEY_BACKSLASH, CATEGORY));

    private NightVisionKey() {
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
            if (connection != null && connection.hasChannel(ToggleNightVisionPayload.TYPE)) {
                ClientPacketDistributor.sendToServer(ToggleNightVisionPayload.INSTANCE);
            }
        }
    }
}
