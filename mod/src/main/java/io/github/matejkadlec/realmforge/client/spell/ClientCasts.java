package io.github.matejkadlec.realmforge.client.spell;

import io.github.matejkadlec.realmforge.client.animation.CastAnimation;
import io.github.matejkadlec.realmforge.spell.Casting;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellVisuals;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.net.CastEndedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastStartedPayload;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client's store of every cast in progress it was told of (architecture 11.2 step 3, 11.6): the local player's and
 * every tracked caster's, mobs and bosses included, from {@code CastStartedPayload} and {@code CastEndedPayload}. C3b's
 * cast bar reads it, and it raises a casting player's hands ({@code client/animation/CastAnimation}, RLM-435) when
 * a cast starts and lowers them a tick after it ends, so a cast that replaces another keeps them up (RLM-454,
 * {@link PendingLowerings}). What forms in a caster's hands is drawn from it too ({@code client/render/SpellForming},
 * RLM-640).
 *
 * <p>Which animation the arms play (RLM-641, ADR 0073 decision 8): a cast time's {@code cast}, a channel's {@code
 * channel}, or the one the spell names; a cast time that completes and formed a projectile ends in the short {@code
 * release}; and an instant spell's {@code CastStartedPayload}, marked as an instant, plays its push. The push is no
 * cast: it is kept apart from the casts in progress ({@link TimedPoses}), so it has no cast bar, nothing forms and
 * nothing ends it but its time, after which the arms go down.
 *
 * <p>While the local player casts, the attack key does nothing, not even a swing, and neither while an instant
 * spell's animation blocks their melee, which the synced {@code combat} attachment says ({@link
 * Casting#meleeBlocked}): the server refuses melee then anyway (ADR 0020, ADR 0073, architecture 9.2). A cast that
 * slows them also keeps them from sprinting until it ends (RLM-371, ADR 0057), as using an item does. Main thread
 * only.
 */
public final class ClientCasts {
    private static final CurrentCasts CASTS = new CurrentCasts();
    private static final PendingLowerings LOWERINGS = new PendingLowerings();
    private static final TimedPoses TIMED = new TimedPoses();

    private ClientCasts() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            CASTS.clear();
            LOWERINGS.clear();
            TIMED.clear();
        });
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, ClientCasts::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> lowerHands());
        NeoForge.EVENT_BUS.addListener(MovementInputUpdateEvent.class, ClientCasts::onMovementInput);
    }

    /**
     * A cast began, or an instant spell's animation (RLM-641), which is played and timed but never kept as a cast.
     * A swing (RLM-399) is kept, so the attack key does nothing during it, but raises no hands: its weapon move is
     * RLM-444's.
     */
    public static void start(CastStartedPayload payload) {
        Cast cast = payload.cast();
        int casterId = payload.casterId();
        if (cast.instant()) {
            TIMED.start(casterId, cast.endGameTime());
            caster(casterId).ifPresent(caster -> CastAnimation.play(caster, animation(cast, CastAnimation.INSTANT)));
            return;
        }
        CASTS.start(casterId, cast);
        if (!cast.swing()) {
            TIMED.stop(casterId);
            caster(casterId).ifPresent(caster -> CastAnimation.show(caster,
                    animation(cast, cast.channel() ? CastAnimation.CHANNEL : CastAnimation.CAST)));
        }
    }

    /** The player animation of {@code cast}'s spell: the one it names, else {@code byDefault}, its cast type's. */
    private static Identifier animation(Cast cast, Identifier byDefault) {
        return spell(cast.spell()).flatMap(spell -> spell.cast().playerAnimation()).orElse(byDefault);
    }

    /** The spell {@code key} names in the client's synced registry, if it is there. */
    private static Optional<Spell> spell(ResourceKey<Spell> key) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? Optional.empty() : Spells.get(level.registryAccess(), key).map(Holder::value);
    }

    /** Whether the cast this client knows {@code casterId} to be casting is a swing (RLM-399). */
    public static boolean isSwing(int casterId) {
        return caster(casterId).flatMap(ClientCasts::current).filter(Cast::swing).isPresent();
    }

    /**
     * A cast ended. A cast time that completed and formed a projectile ends in the release, a short push as the
     * projectile leaves (RLM-641), timed like an instant spell's animation; otherwise the hands stay up until the next
     * client tick ({@link PendingLowerings}).
     */
    public static void end(CastEndedPayload payload) {
        int casterId = payload.casterId();
        ClientLevel level = Minecraft.getInstance().level;
        boolean released = level != null && payload.end().completed()
                && CASTS.get(casterId, level.getGameTime()).filter(cast -> cast.spell().equals(payload.spell())
                        && CurrentCasts.releases(cast, formsProjectile(cast.spell()))).isPresent();
        CASTS.end(casterId, payload.spell());
        if (released) {
            TIMED.start(casterId, level.getGameTime() + CastAnimation.RELEASE_TICKS);
            caster(casterId).ifPresent(caster -> CastAnimation.play(caster, CastAnimation.RELEASE));
        } else {
            LOWERINGS.ended(casterId);
        }
    }

    /** Whether the spell {@code key} names forms a projectile in its caster's hands during its cast. */
    private static boolean formsProjectile(ResourceKey<Spell> key) {
        return spell(key).flatMap(SpellVisuals::formedProjectile).isPresent();
    }

    /**
     * Lowers the hands of every caster whose cast ended since the last tick and who has not cast again, and of every
     * caster whose instant spell's animation or release is over (game time stands still while the game is paused, so
     * nothing is due then). Arms that a cast or a newer such animation took stay up.
     */
    private static void lowerHands() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            LOWERINGS.clear();
            TIMED.clear();
            return;
        }
        long now = level.getGameTime();
        for (int casterId : LOWERINGS.due(id -> CASTS.get(id, now).isPresent() || TIMED.playing(id))) {
            caster(casterId).ifPresent(CastAnimation::lower);
        }
        for (int casterId : TIMED.due(now)) {
            if (CASTS.get(casterId, now).isEmpty()) {
                caster(casterId).ifPresent(CastAnimation::lower);
            }
        }
    }

    /** The entity {@code casterId} names in the client's level, if it is there. */
    private static Optional<Entity> caster(int casterId) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? Optional.empty() : Optional.ofNullable(level.getEntity(casterId));
    }

    /** Vanilla's attack key (melee, block breaking) ends here; cancelling it also suppresses the hand swing. */
    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && (current(player).isPresent() || Casting.meleeBlocked(player))) {
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

    /** Every cast in progress this client knows of, by the caster's entity id. */
    public static Map<Integer, Cast> all() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? Map.of() : CASTS.all(level.getGameTime());
    }

    /** {@code caster}'s cast in progress, if this client knows of one. */
    public static Optional<Cast> current(Entity caster) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? Optional.empty() : CASTS.get(caster.getId(), level.getGameTime());
    }
}
