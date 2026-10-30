package io.github.matejkadlec.realmforge.client.input;

import io.github.matejkadlec.realmforge.client.ClientConfig;
import io.github.matejkadlec.realmforge.combat.AutoShoot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Auto Shoot (RLM-496): while the option is on and right click is held, releases a bow at full draw and a crossbow
 * once loaded ({@link AutoShoot#ready}), as letting go of the button would. Vanilla's own repeat of a held use
 * button ({@code Minecraft#handleKeybinds}) then draws the bow again, or fires the loaded crossbow and loads it
 * again. With the option off, or the button released, nothing changes.
 */
public final class AutoShootInput {
    private AutoShootInput() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onClientTick());
    }

    private static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null || !ClientConfig.AUTO_SHOOT.get()
                || !player.isUsingItem() || !minecraft.options.keyUse.isDown()) {
            return;
        }
        // The stack in the hand, not the use item: the server's sync of the crossbow's loaded projectiles replaces it.
        if (AutoShoot.ready(player.getItemInHand(player.getUsedItemHand()), player.getTicksUsingItem())) {
            minecraft.gameMode.releaseUsingItem(player);
        }
    }
}
