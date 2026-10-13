package io.github.matejkadlec.realmforge.qa.client.castanim;

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
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.castanim.QaCastPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client side of the test cast (ADR 0020): one Player Animation Library (PAL) layer per player plays
 * {@code assets/realmforge_qa/player_animations/cast_test.json} while the server says that player casts, its arms
 * aimed where the player looks ({@link QaCastAimAnimation}), and the local player's attack key does nothing during
 * their own cast.
 */
public final class QaCastAnimationClient {
    /**
     * Above Better Combat's attack layer (2000) and idle poses (1 to 4): PAL applies layers from low to high
     * priority, so the cast wins on every body part it animates and the rest shows through.
     */
    private static final int LAYER_PRIORITY = 3000;
    private static final Identifier LAYER_ID = RealmforgeQa.id("cast");
    /**
     * PAL names an animation by its file's namespace and its {@code name} field; it reads the playerAnimator format
     * of {@code cast_test.json} as it is ({@code PlayerAnimatorLoader}).
     */
    private static final Identifier ANIMATION_ID = RealmforgeQa.id("cast_test");
    private static final int FADE_TICKS = 4;

    /** Whether the server said the local player casts. Trails the server by the ping. */
    private static boolean localPlayerCasting;

    private QaCastAnimationClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(QaCastAnimationClient::onClientSetup);
        modEventBus.addListener(QaCastAnimationClient::onRegisterPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(QaCastAnimationClient::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(QaCastAnimationClient::onLoggingOut);
        QaCastShots.registerIfRequested();
    }

    static boolean isLocalPlayerCasting() {
        return localPlayerCasting;
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // Every client player (every Avatar: mannequins too) gets a CastLayer in its animation stack when created.
        event.enqueueWork(() -> PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID, LAYER_PRIORITY, avatar -> new CastLayer()));
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
        if (PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER_ID) instanceof CastLayer layer) {
            IAnimation next = payload.isStart() ? createCastAnimation(player) : null;
            layer.replaceAnimationWithFade(
                    AbstractFadeModifier.standardFadeIn(FADE_TICKS, EasingType.EASE_IN_OUT_SINE), next);
        }
    }

    /**
     * The test animation for this player, its arms aimed where they look, or null (no animation) when it failed to
     * load.
     */
    private static IAnimation createCastAnimation(AbstractClientPlayer player) {
        Animation animation = PlayerAnimResources.getAnimation(ANIMATION_ID);
        if (animation == null) {
            RealmforgeQa.LOGGER.warn("Player animation {} is missing", ANIMATION_ID);
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
        return new QaCastAimAnimation(player, controller);
    }

    /** Its own type, so a lookup in the player's animation data needs no unchecked cast. */
    private static final class CastLayer extends ModifierLayer<IAnimation> {
    }
}
