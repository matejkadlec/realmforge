package io.github.matejkadlec.realmforge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matejkadlec.realmforge.spell.SpellLook;
import io.github.matejkadlec.realmforge.spell.SpellModel;
import io.github.matejkadlec.realmforge.spell.SpellProjectile;
import java.util.Optional;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * A spell projectile (architecture 11.3, 11.6): its model with the material of its spell's look, as wide as its hitbox
 * times the model's scale, turned as the model says. Every model is the orb until the GeckoLib models (RLM-640,
 * {@link SpellOrbs}). A projectile without a model or without a look has no body: only its trail, which it spawns
 * itself.
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
        state.material = entity.look().flatMap(SpellLook::material);
        state.model = entity.model().map(model -> model.value());
        state.size = entity.getBbWidth();
        state.yaw = entity.getYRot(partialTick);
        state.pitch = entity.getXRot(partialTick);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.material.isPresent() && state.model.isPresent()) {
            SpellModel model = state.model.get();
            poseStack.pushPose();
            poseStack.translate(0.0F, state.size / 2, 0.0F);
            orbs.submit(poseStack, collector, state.material.get(), state.size * model.scale(), model.orientation(),
                    model.spinDegreesPerSecond(), state.yaw, state.pitch, state.ageInTicks, state.lightCoords,
                    state.outlineColor);
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }

    /** What a frame draws of a spell projectile: its look's material and its model, if it has them, and its size. */
    static final class State extends EntityRenderState {
        Optional<Identifier> material = Optional.empty();
        Optional<SpellModel> model = Optional.empty();
        float size;
        /** Where it flies, in degrees: a forward model points there. */
        float yaw;
        float pitch;
    }
}
