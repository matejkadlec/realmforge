package io.github.matejkadlec.realmforge.client.compat.pickupnotifier;

import fuzs.pickupnotifier.common.PickUpNotifier;
import fuzs.pickupnotifier.common.config.AnchorPoint;
import fuzs.pickupnotifier.common.config.ClientConfig;
import fuzs.puzzleslib.common.api.config.v3.ConfigDataHolder;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Pick Up Notifier's list of pickups starts at the middle of the screen's right edge and grows downwards (Matěj's
 * playtest, 2026-10-23, RLM-308). The pack's config anchors the list at the top right (pack README, "Configuration"),
 * a fixed number of GUI pixels below the top edge ({@code offset_y}); that number would land mid-screen at one window
 * size and GUI scale only, and off the bottom at a smaller one. So every client tick this sets it to half the GUI
 * height. The mod has no API for it, but reads its config's fields every frame, and the file keeps its own value. A
 * player who picks another corner keeps that corner's offset. Runs only while Pick Up Notifier is loaded (architecture
 * 19.4); the core mod compiles against the pack's build, so an update that changes the fields fails the build, not the
 * game.
 */
public final class PickUpListPosition {
    private static final String MOD_ID = "pickupnotifier";

    private PickUpListPosition() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        if (ModList.get().isLoaded(MOD_ID)) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> startAtMiddle());
        }
    }

    // AnchorPoint is deprecated, but it is still the type of the mod's screen_corner option.
    @SuppressWarnings("deprecation")
    private static void startAtMiddle() {
        ConfigDataHolder<ClientConfig> holder = PickUpNotifier.CONFIG.getHolder(ClientConfig.class);
        if (!holder.isAvailable()) {
            return;
        }
        ClientConfig.DisplayConfig display = holder.getConfig().display;
        if (display.position == AnchorPoint.TOP_RIGHT) {
            display.offsetY = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2;
        }
    }
}
