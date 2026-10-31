package io.github.matejkadlec.realmforge.client.render;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.effect.EffectLooks;
import io.github.matejkadlec.realmforge.effect.StatusEffect;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * Frozen's look (RLM-539, Matěj, 2026-10-30: "the mob quite literally have ice on them, Packed Ice texture can be used
 * for that, and I also want the affected mob to shiver from the cold"; architecture 18). A living entity whose effects
 * give it {@link StatusEffect.Look#ICE} ({@code realmforge:effect_looks}, synced by the server) shakes as a fully
 * frozen one does in powder snow, and its model is drawn again over it with vanilla's Packed Ice texture, half
 * transparent. The texture is vanilla's own, referenced where it lies ({@code textures/block/packed_ice.png}), never
 * copied into our assets (CLAUDE.md rule 8).
 *
 * <p>Every vanilla living renderer and the players' get the layer. Our custom mobs are GeckoLib's, whose layers do not
 * compile against our render state (CLAUDE.md, GeckoLib 5): they shiver ({@code CustomMob#isFullyFrozen} on the
 * client), without the ice, until the Frost visuals (RLM-544).
 */
public final class FrozenLook {
    /** Whether a frame draws the entity in ice: render data the modifier sets, the layer reads. */
    private static final ContextKey<Boolean> ICE = new ContextKey<>(Realmforge.id("ice"));
    private static final Identifier PACKED_ICE = Identifier.withDefaultNamespace("textures/block/packed_ice.png");
    /** White at about 70 % opacity: the ice over the body, which shows through it. */
    private static final int ICE_TINT = 0xB4FFFFFF;

    private FrozenLook() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterRenderStateModifiersEvent.class, FrozenLook::onRegisterModifiers);
        modBus.addListener(EntityRenderersEvent.AddLayers.class, FrozenLook::onAddLayers);
    }

    /** Whether {@code entity} is drawn in ice now. */
    public static boolean iced(LivingEntity entity) {
        return EffectLooks.has(entity, StatusEffect.Look.ICE);
    }

    private static void onRegisterModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {
        }, (entity, state) -> {
            if (iced(entity)) {
                state.isFullyFrozen = true;
                state.setRenderData(ICE, Boolean.TRUE);
            }
        });
    }

    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            EntityRenderer<?, ?> renderer = event.getRenderer(type);
            if (renderer instanceof LivingEntityRenderer<?, ?, ?> living) {
                addLayer(living);
            }
        }
        for (PlayerModelType skin : event.getSkins()) {
            LivingEntityRenderer<?, ?, ?> player = event.getPlayerRenderer(skin);
            if (player != null) {
                addLayer(player);
            }
        }
    }

    private static <S extends LivingEntityRenderState, M extends EntityModel<? super S>> void addLayer(
            LivingEntityRenderer<?, S, M> renderer) {
        renderer.addLayer(new IceLayer<>(renderer));
    }

    /** The model again, in Packed Ice, over an entity drawn in ice. */
    private static final class IceLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
            extends RenderLayer<S, M> {
        IceLayer(RenderLayerParent<S, M> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot,
                float xRot) {
            if (Boolean.TRUE.equals(state.getRenderData(ICE)) && !state.isInvisible) {
                collector.order(1).submitModel(getParentModel(), state, poseStack,
                        RenderTypes.entityTranslucent(PACKED_ICE), lightCoords, OverlayTexture.NO_OVERLAY, ICE_TINT,
                        null, state.outlineColor, null);
            }
        }
    }
}
