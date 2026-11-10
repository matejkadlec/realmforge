package io.github.matejkadlec.realmforge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.SpellModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;

/**
 * The orb, the placeholder shape of a spell model (architecture 11.6, {@link SpellModel.Shape#ORB}): three crossed
 * slabs half a block across, drawn with a look's material. The material is one 16 x 16 tile, and the slabs' faces take
 * it pixel for pixel, as a block's faces do: it repeats where a face is longer than the tile (a texture repeats unless
 * its metadata clamps it). The projectile's renderer draws the orb as it flies; a caster's renderer draws it growing in
 * front of the caster while a spell forms it.
 */
public final class SpellOrbs {
    /** The orb's model layer, registered by {@link SpellEntityRenderers}. */
    static final ModelLayerLocation LAYER = new ModelLayerLocation(Realmforge.id("spell_orb"), "main");
    /** The model's slabs are 8 pixels across: at scale 1 the orb is half a block wide. */
    private static final float MODEL_BLOCKS = 0.5F;
    /** A material's size in pixels, which the slabs' faces are mapped onto. */
    private static final int MATERIAL_SIZE = 16;
    private static final float TICKS_PER_SECOND = 20;

    private final Orb model;

    public SpellOrbs(EntityRendererProvider.Context context) {
        model = new Orb(context.bakeLayer(LAYER));
    }

    /** The orb's mesh: three slabs of 8 x 8 x 2 pixels through one middle, one across each axis. */
    static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("main", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -4.0F, -1.0F, 8.0F, 8.0F, 2.0F)
                .addBox(-1.0F, -4.0F, -4.0F, 2.0F, 8.0F, 8.0F)
                .addBox(-4.0F, -1.0F, -4.0F, 8.0F, 2.0F, 8.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, MATERIAL_SIZE, MATERIAL_SIZE);
    }

    /**
     * Submits an orb {@code size} blocks across at the pose's origin (its middle) with the material {@code texture},
     * lit by {@code light}, turned as {@code orientation} says: tumbling by {@code ageInTicks}, or pointed along
     * {@code yaw} and {@code pitch} (the flight's, in degrees) and spun about that line at {@code spin} degrees a
     * second.
     */
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture, float size,
            SpellModel.Orientation orientation, float spin, float yaw, float pitch, float ageInTicks, int light,
            int outlineColor) {
        if (size <= 0) {
            return;
        }
        poseStack.pushPose();
        if (orientation == SpellModel.Orientation.FORWARD) {
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
            poseStack.mulPose(Axis.ZP.rotationDegrees(ageInTicks / TICKS_PER_SECOND * spin));
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.sin(ageInTicks * 0.1F) * 180.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.cos(ageInTicks * 0.1F) * 180.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(ageInTicks * 0.15F) * 360.0F));
        }
        float scale = size / MODEL_BLOCKS;
        poseStack.scale(-scale, -scale, scale);
        collector.submitModel(model, Unit.INSTANCE, poseStack, texture, light, OverlayTexture.NO_OVERLAY,
                outlineColor, null);
        poseStack.popPose();
    }

    /** The orb's model: nothing moves in it, the pose turns it. */
    private static final class Orb extends Model<Unit> {
        Orb(ModelPart root) {
            super(root, RenderTypes::entityCutout);
        }
    }
}
