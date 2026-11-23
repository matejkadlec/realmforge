package io.github.matejkadlec.realmforge.client.animation;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.ModifierLayer;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import io.github.matejkadlec.realmforge.Realmforge;
import javax.annotation.Nullable;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The cast animations (C6-1, RLM-435, RLM-641; ADR 0020, ADR 0073 decision 8, architecture 11.6): one Player
 * Animation Library (PAL) layer per player plays an animation of {@code assets/realmforge/player_animations/} while
 * that player casts, its arms aimed where the player looks ({@link AimedCastAnimation}):
 *
 * <ul>
 *   <li>{@link #CAST}, looped: both hands rise and meet below the crosshair, for a cast time;</li>
 *   <li>{@link #CHANNEL}, looped: both hands pushed forward and held, for a channel;</li>
 *   <li>{@link #INSTANT}, once: both hands push forward and back over {@code SpellCast#PUSH_TICKS}, for an instant
 *   spell;</li>
 *   <li>{@link #RELEASE}, once: a short push of both hands over {@link #RELEASE_TICKS}, as a formed projectile
 *   leaves.</li>
 * </ul>
 *
 * A spell may name another animation for its cast ({@code SpellCast#playerAnimation}). {@code client/spell/ClientCasts}
 * decides which plays when, from {@code CastStartedPayload} and {@code CastEndedPayload}, for the caster and every
 * player tracking them; the QA mod's {@code /qa cast} shows {@link #CAST}. Every PAL call of the core mod sits in
 * this package.
 */
public final class CastAnimation {
    /** A cast time's animation: {@code cast.json}. */
    public static final Identifier CAST = Realmforge.id("cast");
    /** A channel's animation: {@code channel.json}. */
    public static final Identifier CHANNEL = Realmforge.id("channel");
    /** An instant spell's animation: {@code instant.json}, as long as {@code SpellCast#PUSH_TICKS}. */
    public static final Identifier INSTANT = Realmforge.id("instant");
    /** The push as a formed projectile leaves the hands: {@code release.json}. */
    public static final Identifier RELEASE = Realmforge.id("release");
    /** How long {@link #RELEASE} plays, in ticks: a quarter of a second (Matěj, 2026-10-29). */
    public static final int RELEASE_TICKS = 5;

    /**
     * Above Better Combat's attack layer (2000) and idle poses (1 to 4): PAL applies layers from low to high
     * priority, so the cast wins on every body part it animates and the rest shows through.
     */
    private static final int LAYER_PRIORITY = 3000;
    private static final Identifier LAYER_ID = Realmforge.id("cast");
    /** How long a looped animation fades in and every animation fades out, in ticks. */
    private static final int FADE_TICKS = 4;
    /** How long an animation that plays once fades in: short, or the fade would swallow its motion. */
    private static final int QUICK_FADE_TICKS = 2;

    private CastAnimation() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(CastAnimation::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // Every client player (every Avatar: mannequins too) gets a CastLayer in its animation stack when created.
        event.enqueueWork(() -> PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID, LAYER_PRIORITY, avatar -> new CastLayer()));
    }

    /** Raises {@code entity}'s hands into {@link #CAST} when {@code casting}, or lowers them ({@link #lower}). */
    public static void show(Entity entity, boolean casting) {
        if (casting) {
            show(entity, CAST);
        } else {
            lower(entity);
        }
    }

    /**
     * Shows the looped {@code animation} on {@code entity}'s arms, fading in. A player who already shows it stays as
     * they are, so a cast that replaces another of the same kind keeps its pose and its tick: {@code ClientCasts}
     * lowers the hands only a tick after a cast's end, by which time the replacing cast has started (RLM-454). Any
     * other entity is ignored (mobs have no PAL layer).
     */
    public static void show(Entity entity, Identifier animation) {
        if (layer(entity) instanceof CastLayer layer && !animation.equals(layer.showing)) {
            replace((AbstractClientPlayer) entity, layer, animation, FADE_TICKS);
        }
    }

    /**
     * Plays {@code animation} on {@code entity}'s arms from its start, whatever they show: an animation that plays
     * once (an instant spell's push, a release), which a second one right after it starts again.
     */
    public static void play(Entity entity, Identifier animation) {
        if (layer(entity) instanceof CastLayer layer) {
            replace((AbstractClientPlayer) entity, layer, animation, QUICK_FADE_TICKS);
        }
    }

    /** Lowers {@code entity}'s hands, fading out of whatever they show; a player who shows nothing stays so. */
    public static void lower(Entity entity) {
        if (layer(entity) instanceof CastLayer layer && layer.showing != null) {
            layer.showing = null;
            layer.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, EasingType.EASE_IN_OUT_SINE), null);
        }
    }

    /** {@code entity}'s cast layer when it is a client player with one, else null. */
    @Nullable
    private static IAnimation layer(Entity entity) {
        return entity instanceof AbstractClientPlayer player
                ? PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER_ID) : null;
    }

    private static void replace(AbstractClientPlayer player, CastLayer layer, Identifier animation, int fadeTicks) {
        layer.showing = animation;
        layer.replaceAnimationWithFade(
                AbstractFadeModifier.standardFadeIn(fadeTicks, EasingType.EASE_IN_OUT_SINE),
                create(player, animation));
    }

    /**
     * {@code animation} for this player, its arms aimed where they look, or null (no animation) when it failed to
     * load. PAL names an animation by its file's namespace and its {@code name} field, and reads the playerAnimator
     * format of our files as it is ({@code PlayerAnimatorLoader}): one that loops does so as its file says, and one
     * that plays once eases back to the resting pose between its {@code endTick} and its {@code stopTick}.
     */
    @Nullable
    private static IAnimation create(AbstractClientPlayer player, Identifier id) {
        Animation animation = PlayerAnimResources.getAnimation(id);
        if (animation == null) {
            Realmforge.LOGGER.warn("Player animation {} is missing", id);
            return null;
        }
        // A controller of its own plays the animation: triggered, it runs as the file says, whatever the state
        // handler (which picks nothing) returns. In first person, draw both arms of the third-person model with the
        // animation applied, as Better Combat does for its swings, instead of vanilla's first-person hand. The aim
        // wraps the controller, not the layer, so the fade out of a cast still blends from the aimed pose.
        PlayerAnimationController controller =
                new PlayerAnimationController(player, (self, state, setter) -> PlayState.STOP);
        controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        controller.triggerAnimation(animation);
        return new AimedCastAnimation(player, controller);
    }

    /** Its own type, so a lookup in the player's animation data needs no unchecked cast. */
    private static final class CastLayer extends ModifierLayer<IAnimation> {
        /** The animation the layer shows, null for none; main thread only. */
        @Nullable
        private Identifier showing;
    }
}
