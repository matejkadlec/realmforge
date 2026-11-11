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
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * A spell projectile (architecture 11.3, 11.6): its model with the material of its spell's look, as wide as its hitbox
 * times the model's scale, turned as the model says and lit as the look says ({@link SpellModels}). A projectile
 * without a model or without a look has no body: only its trail, which it spawns itself.
 *
 * <p><b>It leaves the hands.</b> The server launches a bolt on the line it flies, which is not where the caster's
 * hands are on a player's screen, so its first ticks are drawn blended from where its caster's forming model was
 * drawn, and from that model's size, to its own path and size ({@link SpellForming#launch}). Only the drawing moves:
 * the entity flies where the server says.
 */
final class SpellProjectileRenderer extends EntityRenderer<SpellProjectile, SpellProjectileRenderer.State> {
    SpellProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        SpellOrbs.bake(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpellProjectile entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.material = entity.look().flatMap(SpellLook::material);
        state.light = entity.look().map(look -> look.value().light()).orElse(SpellLook.Light.WORLD);
        state.model = entity.model();
        state.size = entity.getBbWidth();
        state.yaw = entity.getYRot(partialTick);
        state.pitch = entity.getXRot(partialTick);
        state.launch = SpellForming.launch(entity, partialTick);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.material.isPresent() && state.model.isPresent()) {
            Holder<SpellModel> model = state.model.get();
            Vec3 from = state.launch.offset();
            poseStack.pushPose();
            poseStack.translate(from.x, from.y + state.size / 2, from.z);
            SpellModels.submit(poseStack, collector, state.material.get(), model, state.light,
                    new SpellModels.Pose(state.size * model.value().scale() * state.launch.size(), state.yaw,
                            state.pitch, state.ageInTicks, state.lightCoords, 1, 1, false),
                    state.outlineColor);
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }

    /** What a frame draws of a spell projectile: its look's material and light, its model and its size. */
    static final class State extends EntityRenderState {
        Optional<Identifier> material = Optional.empty();
        SpellLook.Light light = SpellLook.Light.WORLD;
        Optional<Holder<SpellModel>> model = Optional.empty();
        float size;
        /** Where it flies, in degrees: a forward model points there. */
        float yaw;
        float pitch;
        /** How far from its own path, and at what share of its size, it is drawn while it leaves the hands. */
        SpellForming.Launch launch = SpellForming.Launch.NONE;
    }
}
