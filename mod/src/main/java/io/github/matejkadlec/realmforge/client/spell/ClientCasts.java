package io.github.matejkadlec.realmforge.client.spell;

import io.github.matejkadlec.realmforge.client.animation.CastAnimation;
import io.github.matejkadlec.realmforge.spell.Casting;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.net.CastEndedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastStartedPayload;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client's store of every cast in progress it was told of (architecture 11.2 step 3, 11.6): the local player's and
 * every tracked caster's, mobs and bosses included, from {@code CastStartedPayload} and {@code CastEndedPayload}. C3b's
 * cast bar reads it, and it raises a casting player's hands ({@code client/animation/CastAnimation}, RLM-435) when
 * a cast starts and lowers them when it ends. While the local player casts, the attack key does nothing, not even a
 * swing: the server refuses melee during a cast anyway (ADR 0020, architecture 9.2). A cast that slows them also
 * keeps them from sprinting until it ends (RLM-371, ADR 0057), as using an item does. Main thread only.
 */
public final class ClientCasts {
    private static final CurrentCasts CASTS = new CurrentCasts();

    private ClientCasts() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> CASTS.clear());
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, ClientCasts::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, ClientCasts::onMovementInput);
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

    /**
     * Fires in {@code LocalPlayer#aiStep} after the keys are read and before vanilla starts a sprint: while the local
     * player's cast keeps them from sprinting ({@link Casting#blocksSprint}, which the synced {@code combat} attachment
     * answers), a sprint stops, the sprint key is let go of and the double tap of forward forgotten, as vanilla does
     * while a player uses an item that slows them. The server stops the sprint too, at the cast's start and every tick.
     */
    private static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !Casting.blocksSprint(player)) {
            return;
        }
        if (player.isSprinting()) {
            player.setSprinting(false);
        }
        player.sprintTriggerTime = 0;
        ClientInput input = event.getInput();
        input.keyPresses = withoutSprint(input.keyPresses);
    }

    /** {@code keys} with the sprint key let go of. */
    private static Input withoutSprint(Input keys) {
        return new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), keys.shift(), false);
    }

    /** {@code caster}'s cast in progress, if this client knows of one. */
    public static Optional<Cast> current(Entity caster) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? Optional.empty() : CASTS.get(caster.getId(), level.getGameTime());
    }
}
