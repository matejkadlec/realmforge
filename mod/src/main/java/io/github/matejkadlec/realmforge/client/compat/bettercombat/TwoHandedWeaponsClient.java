package io.github.matejkadlec.realmforge.client.compat.bettercombat;

import io.github.matejkadlec.realmforge.compat.bettercombat.TwoHandedWeapons;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client side of {@link TwoHandedWeapons}: a client of another server binds the tag when it joins, once Better
 * Combat has received the server's weapons, which may come after the tags. In single player the server shares the
 * item registry with the client and binds it for both. Runs only while Better Combat is loaded (architecture 19.4).
 */
public final class TwoHandedWeaponsClient {
    private TwoHandedWeaponsClient() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        if (ModList.get().isLoaded(TwoHandedWeapons.MOD_ID)) {
            NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> {
                if (!Minecraft.getInstance().isLocalServer()) {
                    TwoHandedWeapons.bind();
                }
            });
        }
    }
}
