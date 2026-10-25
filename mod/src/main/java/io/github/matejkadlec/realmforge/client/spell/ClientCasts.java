package io.github.matejkadlec.realmforge.client.spell;

import io.github.matejkadlec.realmforge.client.animation.CastAnimation;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.net.CastEndedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastStartedPayload;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client's store of every cast in progress it was told of (architecture 11.2 step 3, 11.6): the local player's and
 * every tracked caster's, mobs and bosses included, from {@code CastStartedPayload} and {@code CastEndedPayload}. C3b's
 * cast bar reads it, and it raises a casting player's hands ({@code client/animation/CastAnimation}, RLM-435) when
 * a cast starts and lowers them when it ends. While the local player casts, the attack key does nothing, not even a
 * swing: the server refuses melee during a cast anyway (ADR 0020, architecture 9.2). Main thread only.
 */
public final class ClientCasts {
    private static final CurrentCasts CASTS = new CurrentCasts();

    private ClientCasts() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> CASTS.clear());
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, ClientCasts::onInteractionKey);
    }

    public static void start(CastStartedPayload payload) {
        CASTS.start(payload.casterId(), payload.cast());
        caster(payload.casterId()).ifPresent(caster -> CastAnimation.show(caster, true));
    }

    /** A newer cast of another spell, already started, keeps the caster's hands up. */
    public static void end(CastEndedPayload payload) {
        CASTS.end(payload.casterId(), payload.spell());
        caster(payload.casterId()).ifPresent(caster -> CastAnimation.show(caster, current(caster).isPresent()));
    }

    /** The entity {@code casterId} names in the client's level, if it is there. */
    private static Optional<Entity> caster(int casterId) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? Optional.empty() : Optional.ofNullable(level.getEntity(casterId));
    }

    /** Vanilla's attack key (melee, block breaking) ends here; cancelling it also suppresses the hand swing. */
    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && current(player).isPresent()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** {@code caster}'s cast in progress, if this client knows of one. */
    public static Optional<Cast> current(Entity caster) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? Optional.empty() : CASTS.get(caster.getId(), level.getGameTime());
    }
}
