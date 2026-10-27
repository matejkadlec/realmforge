package io.github.matejkadlec.realmforge.qa.client.castanim;

import io.github.matejkadlec.realmforge.client.animation.CastAnimation;
import io.github.matejkadlec.realmforge.qa.castanim.QaCastPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client side of the test cast (ADR 0020): while the server says a player casts, they show the core mod's cast
 * animation ({@code client/animation/CastAnimation}, the one every cast-time and channelled spell plays, RLM-435),
 * and the local player's attack key does nothing during their own cast.
 */
public final class QaCastAnimationClient {
    /** Whether the server said the local player casts. Trails the server by the ping. */
    private static boolean localPlayerCasting;

    private QaCastAnimationClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(QaCastAnimationClient::onRegisterPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(QaCastAnimationClient::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(QaCastAnimationClient::onLoggingOut);
        QaCastShots.registerIfRequested();
    }

    static boolean isLocalPlayerCasting() {
        return localPlayerCasting;
    }

    /** {@code QaCastAnimation} registers the payload without a handler (ADR 0027); this is its handler. */
    private static void onRegisterPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(QaCastPayload.TYPE, (payload, context) -> handle(payload));
    }

    /**
     * Better Combat steps aside while its disable tag is set, so a left click reaches vanilla's attack (and block
     * breaking) here; cancelling it also suppresses the hand swing.
     */
    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && localPlayerCasting) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        localPlayerCasting = false;
    }

    private static void handle(QaCastPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(payload.entityId());
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        if (player == minecraft.player) {
            localPlayerCasting = payload.isStart();
        }
        CastAnimation.show(player, payload.isStart());
    }
}
