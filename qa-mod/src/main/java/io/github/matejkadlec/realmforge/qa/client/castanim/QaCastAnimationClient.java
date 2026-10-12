package io.github.matejkadlec.realmforge.qa.client.castanim;

import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.castanim.QaCastAnimation;
import io.github.matejkadlec.realmforge.qa.castanim.QaCastPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client side of the test cast (ADR 0020): one playerAnimator layer per player plays
 * {@code assets/realmforge_qa/player_animations/cast_test.json} while the server says that player casts, and the local
 * player's attack key does nothing during their own cast.
 */
public final class QaCastAnimationClient {
    /**
     * Above Better Combat's attack layer (2000) and idle poses (1 to 4): playerAnimator applies layers from low to
     * high priority, so the cast wins on every body part it animates and the rest shows through.
     */
    private static final int LAYER_PRIORITY = 3000;
    private static final ResourceLocation LAYER_ID = RealmforgeQa.id("cast");
    /** playerAnimator names an animation by its file's namespace and its {@code name} field. */
    private static final ResourceLocation ANIMATION_ID = RealmforgeQa.id("cast_test");
    private static final int FADE_TICKS = 4;

    /** Whether the server said the local player casts. Trails the server by the ping. */
    private static boolean localPlayerCasting;

    private QaCastAnimationClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(QaCastAnimationClient::onClientSetup);
        NeoForge.EVENT_BUS.addListener(QaCastAnimationClient::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(QaCastAnimationClient::onLoggingOut);
        QaCastAnimation.installClientHandler(QaCastAnimationClient::handle);
        QaCastShots.registerIfRequested();
    }

    static boolean isLocalPlayerCasting() {
        return localPlayerCasting;
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // Every client player gets a CastLayer in its animation stack when it is created.
        event.enqueueWork(() -> PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID, LAYER_PRIORITY, player -> new CastLayer()));
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
        if (PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER_ID) instanceof CastLayer layer) {
            IAnimation next = payload.isStart() ? createCastAnimation() : null;
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE), next);
        }
    }

    /** The test animation, or null (no animation) when it failed to load. */
    private static IAnimation createCastAnimation() {
        IPlayable animation = PlayerAnimationRegistry.getAnimation(ANIMATION_ID);
        if (animation == null) {
            RealmforgeQa.LOGGER.warn("Player animation {} is missing", ANIMATION_ID);
            return null;
        }
        // In first person, draw both arms of the third-person model with the animation applied, as Better Combat
        // does for its swings, instead of vanilla's first-person hand.
        return animation.playAnimation()
                .setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
    }

    /** Its own type, so a lookup in the player's animation data needs no unchecked cast. */
    private static final class CastLayer extends ModifierLayer<IAnimation> {
    }
}
