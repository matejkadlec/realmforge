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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The cast animation (C6-1, RLM-435; ADR 0020, architecture 11.6): one Player Animation Library (PAL) layer per
 * player plays {@code assets/realmforge/player_animations/cast.json} while that player casts a cast-time or
 * channelled spell, its arms aimed where the player looks ({@link AimedCastAnimation}). {@code client/spell/ClientCasts}
 * shows it from {@code CastStartedPayload} to {@code CastEndedPayload}, for the caster and every player tracking
 * them; the QA mod's {@code /qa cast} shows the same animation. Every PAL call of the core mod sits in this package.
 */
public final class CastAnimation {
    /**
     * Above Better Combat's attack layer (2000) and idle poses (1 to 4): PAL applies layers from low to high
     * priority, so the cast wins on every body part it animates and the rest shows through.
     */
    private static final int LAYER_PRIORITY = 3000;
    private static final Identifier LAYER_ID = Realmforge.id("cast");
    /**
     * PAL names an animation by its file's namespace and its {@code name} field; it reads the playerAnimator format
     * of {@code cast.json} as it is ({@code PlayerAnimatorLoader}).
     */
    private static final Identifier ANIMATION_ID = Realmforge.id("cast");
    private static final int FADE_TICKS = 4;

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

    /**
     * Raises {@code entity}'s hands into the cast pose when {@code casting}, or lowers them, fading either way. A
     * player already in the state stays as they are, so a cast that replaces another keeps its pose; any other entity
     * is ignored (mobs have no PAL layer).
     */
    public static void show(Entity entity, boolean casting) {
        if (entity instanceof AbstractClientPlayer player
                && PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER_ID) instanceof CastLayer layer
                && layer.casting != casting) {
            layer.casting = casting;
            IAnimation next = casting ? createCastAnimation(player) : null;
            layer.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, EasingType.EASE_IN_OUT_SINE), next);
        }
    }

    /**
     * The cast animation for this player, its arms aimed where they look, or null (no animation) when it failed to
     * load.
     */
    private static IAnimation createCastAnimation(AbstractClientPlayer player) {
        Animation animation = PlayerAnimResources.getAnimation(ANIMATION_ID);
        if (animation == null) {
            Realmforge.LOGGER.warn("Player animation {} is missing", ANIMATION_ID);
            return null;
        }
        // A controller of its own plays the animation: triggered, it loops as the file says, whatever the state
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
        /** Whether the layer shows the cast; main thread only. */
        private boolean casting;
    }
}
