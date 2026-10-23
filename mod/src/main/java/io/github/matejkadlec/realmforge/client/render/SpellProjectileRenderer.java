package io.github.matejkadlec.realmforge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matejkadlec.realmforge.spell.SpellProjectile;
import java.util.Optional;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

/**
 * A spell projectile (architecture 11.3, 11.6): the orb of its appearance with its spell's texture, as wide as its
 * hitbox ({@link SpellOrbs}); a projectile without one is only its trail of sparks, which it draws itself.
 */
final class SpellProjectileRenderer extends EntityRenderer<SpellProjectile, SpellProjectileRenderer.State> {
    private final SpellOrbs orbs;

    SpellProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        orbs = new SpellOrbs(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpellProjectile entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.texture = entity.texture();
        state.size = entity.getBbWidth();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        state.texture.ifPresent(texture -> {
            poseStack.pushPose();
            poseStack.translate(0.0F, state.size / 2, 0.0F);
            orbs.submit(poseStack, collector, texture, state.size, state.ageInTicks, state.lightCoords,
                    state.outlineColor);
            poseStack.popPose();
        });
        super.submit(state, poseStack, collector, camera);
    }

    /** What a frame draws of a spell projectile: its texture, if it has an appearance, and its size. */
    static final class State extends EntityRenderState {
        Optional<Identifier> texture = Optional.empty();
        float size;
    }
}
