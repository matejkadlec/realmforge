package io.github.matejkadlec.realmforge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.matejkadlec.realmforge.spell.SpellLook;
import io.github.matejkadlec.realmforge.spell.SpellModel;
import java.util.Optional;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a spell model with a look's material (RLM-640, ADR 0073, ADR 0075, architecture 11.6): in flight
 * ({@link SpellProjectileRenderer}) and while it forms in a caster's hands ({@link SpellForming}), the same way in
 * both. A model of cubes is drawn cube by cube from GeckoLib's baked model ({@link SpellMeshes}); the orb, and a
 * model whose GeckoLib model is missing, as the three slabs ({@link SpellOrbs}).
 */
final class SpellModels {
    /** How many light levels above the world's a {@link SpellLook.Light#GLOW} model is drawn. */
    static final int GLOW_LEVELS = 5;
    private static final float TICKS_PER_SECOND = 20;

    private SpellModels() {
    }

    /**
     * How a model shows at a moment.
     *
     * @param width how wide it is drawn at its full size, in blocks: the projectile's hitbox times the model's scale
     * @param yaw where it points, a projectile's own yaw in degrees ({@code atan2(dx, dz)}); a tumbling model
     *     ignores it
     * @param pitch where it points, a projectile's own pitch in degrees (up is positive)
     * @param ageInTicks the time its tumble or spin reads
     * @param light the packed light where it is, before its look's light
     * @param alpha how opaque it is, 1 unless it fades away
     * @param formed how far it has formed, 0 to 1; 1 for a model in flight
     * @param cubeByCube whether a model of cubes forms cube by cube ({@code grow}) instead of as a whole
     */
    record Pose(float width, float yaw, float pitch, float ageInTicks, int light, float alpha, float formed,
            boolean cubeByCube) {
    }

    /** The light a model of a look with {@code light} is drawn in, where the world's packed light is {@code coords}. */
    static int lit(SpellLook.Light light, int coords) {
        return switch (light) {
            case WORLD -> coords;
            case GLOW -> LightCoordsUtil.withBlock(coords,
                    Math.min(15, LightCoordsUtil.block(coords) + GLOW_LEVELS));
            case EMIT -> LightCoordsUtil.FULL_BRIGHT;
        };
    }

    /**
     * Submits {@code model} with {@code material}, its middle at the pose's origin, as {@code pose} says, lit as
     * {@code look} says.
     */
    static void submit(PoseStack poseStack, SubmitNodeCollector collector, Identifier material,
            Holder<SpellModel> model, SpellLook.Light look, Pose pose, int outlineColor) {
        if (pose.alpha() <= 0 || pose.width() <= 0) {
            return;
        }
        int light = lit(look, pose.light());
        int color = ARGB.white(pose.alpha());
        SpellModel shape = model.value();
        Optional<SpellMeshes.Mesh> mesh = shape.shape() == SpellModel.Shape.CUBES
                ? model.unwrapKey().flatMap(key -> SpellMeshes.of(key.identifier())) : Optional.empty();
        boolean forming = pose.formed() < 1;
        if (mesh.isEmpty()) {
            // The orb has no cubes to grow one by one: it scales up as a whole.
            float width = pose.width() * (forming ? FormingCurves.whole(pose.formed()) : 1);
            SpellOrbs.get().submit(poseStack, collector, material, width, shape.orientation(),
                    shape.spinDegreesPerSecond(), pose.yaw(), pose.pitch(), pose.ageInTicks(), light, color,
                    outlineColor);
            return;
        }
        SpellMeshes.Mesh cubes = mesh.get();
        boolean grows = forming && pose.cubeByCube();
        float width = pose.width() * (forming && !grows ? FormingCurves.whole(pose.formed()) : 1);
        float formed = pose.formed();
        SpellMeshes.CubeSizes sizes = grows
                ? (rank, count) -> FormingCurves.cube(rank, count, formed) : SpellMeshes.CubeSizes.WHOLE;
        poseStack.pushPose();
        poseStack.mulPose(turn(shape, pose.yaw(), pose.pitch(), pose.ageInTicks()));
        float scale = width / cubes.width();
        poseStack.scale(scale, scale, scale);
        collector.submitCustomGeometry(poseStack,
                pose.alpha() < 1 ? RenderTypes.entityTranslucent(material) : RenderTypes.entityCutout(material),
                (last, buffer) -> SpellMeshes.draw(last, buffer, cubes, sizes, light, color));
        poseStack.popPose();
    }

    /**
     * How a model of cubes is turned: a forward one's front (the model's -z, Blockbench's north) along its flight
     * and spun about it, a tumbling one over every axis.
     */
    private static Quaternionf turn(SpellModel shape, float yaw, float pitch, float ageInTicks) {
        Quaternionf turn = new Quaternionf();
        if (shape.orientation() == SpellModel.Orientation.FORWARD) {
            turn.mul(Axis.YP.rotationDegrees(yaw));
            turn.mul(Axis.XP.rotationDegrees(-pitch));
            turn.mul(Axis.ZP.rotationDegrees(ageInTicks / TICKS_PER_SECOND * shape.spinDegreesPerSecond()));
            // The pose's +z is the flight now; the model's front is its -z.
            turn.mul(Axis.YP.rotationDegrees(180));
        } else {
            turn.mul(Axis.YP.rotationDegrees(Mth.sin(ageInTicks * 0.1F) * 180.0F));
            turn.mul(Axis.XP.rotationDegrees(Mth.cos(ageInTicks * 0.1F) * 180.0F));
            turn.mul(Axis.ZP.rotationDegrees(Mth.sin(ageInTicks * 0.15F) * 360.0F));
        }
        return turn;
    }

    /**
     * Where the growing edge of a forming model is, from its middle, in blocks: the middle of the newest cube that
     * shows at {@code pose}'s progress, turned and sized as the model is drawn. Zero for a model without cubes.
     */
    static Vec3 edge(Holder<SpellModel> model, Pose pose) {
        SpellModel shape = model.value();
        Optional<SpellMeshes.Mesh> mesh = shape.shape() == SpellModel.Shape.CUBES
                ? model.unwrapKey().flatMap(key -> SpellMeshes.of(key.identifier())) : Optional.empty();
        if (mesh.isEmpty()) {
            return Vec3.ZERO;
        }
        SpellMeshes.Mesh cubes = mesh.get();
        Vector3f from = new Vector3f(cubes.middles()[FormingCurves.edge(cubes.count(), pose.formed())])
                .sub(cubes.heart()).mul(pose.width() / cubes.width());
        turn(shape, pose.yaw(), pose.pitch(), pose.ageInTicks()).transform(from);
        return new Vec3(from.x, from.y, from.z);
    }
}
