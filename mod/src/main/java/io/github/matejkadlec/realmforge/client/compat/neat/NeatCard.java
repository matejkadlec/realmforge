package io.github.matejkadlec.realmforge.client.compat.neat;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * Neat's card above a mob whose renderer is not a {@code LivingEntityRenderer}: our custom mobs, which GeckoLib
 * renders (architecture 14.2, 19.4). Neat fills its card's state in a mixin on
 * {@code LivingEntityRenderer#extractRenderState} and draws it after any renderer's {@code submit}, so this runs that
 * method of an invisible living renderer on the mob's render state, which lets Neat fill the card as for any vanilla
 * mob, and then shows the mob's level in it as {@link NeatCompat} does for them. The custom mob's own renderer
 * extracts its state afterwards, so its values win wherever both write.
 */
public final class NeatCard {
    private final CardRenderer renderer;

    NeatCard(EntityRendererProvider.Context context) {
        renderer = new CardRenderer(context);
    }

    /** Lets Neat fill its card in {@code state} for {@code entity}, with the mob's level before its name. */
    public void extract(LivingEntity entity, LivingEntityRenderState state, float partialTick) {
        renderer.extractRenderState(entity, state, partialTick);
        NeatCompat.showLevel(entity, state);
    }

    /** A living renderer that only extracts: its model is empty and it never draws. */
    private static final class CardRenderer
            extends LivingEntityRenderer<LivingEntity, LivingEntityRenderState, EntityModel<LivingEntityRenderState>> {
        CardRenderer(EntityRendererProvider.Context context) {
            super(context, new EmptyModel(), 0.0F);
        }

        @Override
        public Identifier getTextureLocation(LivingEntityRenderState state) {
            return MissingTextureAtlasSprite.getLocation();
        }

        @Override
        public LivingEntityRenderState createRenderState() {
            return new LivingEntityRenderState();
        }
    }

    private static final class EmptyModel extends EntityModel<LivingEntityRenderState> {
        EmptyModel() {
            super(new ModelPart(List.of(), Map.of()));
        }
    }
}
