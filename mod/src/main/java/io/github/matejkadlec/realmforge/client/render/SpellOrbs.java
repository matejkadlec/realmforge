package io.github.matejkadlec.realmforge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.projectile.ShulkerBulletModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ShulkerBulletRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The orb of a spell projectile's {@code orb} appearance (architecture 11.6): vanilla's shulker bullet model, three
 * crossed slabs, spinning as the bullet does, with the spell's own texture. The projectile's renderer draws it as it
 * flies; a caster's renderer draws it growing in front of the caster while a spell forms it.
 */
public final class SpellOrbs {
    /** The model's slabs are 8 pixels across: at scale 1 the orb is half a block wide. */
    private static final float MODEL_BLOCKS = 0.5F;

    private final ShulkerBulletModel model;
    private final ShulkerBulletRenderState state = new ShulkerBulletRenderState();

    public SpellOrbs(EntityRendererProvider.Context context) {
        model = new ShulkerBulletModel(context.bakeLayer(ModelLayers.SHULKER_BULLET));
    }

    /**
     * Submits an orb {@code size} blocks across at the pose's origin (its middle), spun by {@code ageInTicks}, with
     * {@code texture}, lit by {@code light}.
     */
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture, float size,
            float ageInTicks, int light, int outlineColor) {
        if (size <= 0) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.sin(ageInTicks * 0.1F) * 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.cos(ageInTicks * 0.1F) * 180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(ageInTicks * 0.15F) * 360.0F));
        float scale = size / MODEL_BLOCKS;
        poseStack.scale(-scale, -scale, scale);
        state.lightCoords = light;
        state.ageInTicks = ageInTicks;
        collector.submitModel(model, state, poseStack, texture, light, OverlayTexture.NO_OVERLAY, outlineColor, null);
        poseStack.popPose();
    }
}
