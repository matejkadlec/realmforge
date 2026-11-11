package io.github.matejkadlec.realmforge.client.render;

import com.geckolib.cache.BakedModelCache;
import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.SpellModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/**
 * The cubes of the spell models of shape {@code cubes} (RLM-640, ADR 0075, architecture 11.6), read from GeckoLib's
 * baked models ({@code assets/<ns>/geckolib/models/spell/<id>.geo.json}, which {@code rf gen} writes) and drawn here
 * cube by cube, so each cube can have a size of its own while the model forms and the whole can fade.
 *
 * <p>A {@link Mesh} holds a model's cubes in the order they appear while it forms: by the distance of their middles
 * from the model's heart, the middle of its bounds. GeckoLib bakes a cube's corners before its own rotation and turns
 * it about its pivot when it draws it ({@code GeoCube#render}); the middles and the bounds here are the turned ones,
 * computed with the cube's own transform, so they are where the cube is drawn. Meshes are kept per baked model, and a
 * resource reload, which bakes new ones, replaces them.
 */
final class SpellMeshes {
    /**
     * A model's cubes, ready to draw.
     *
     * @param cubes the cubes, the one nearest the model's heart first
     * @param middles each cube's middle as it is drawn, in blocks of the model's own size
     * @param heart the middle of the model's bounds, which is drawn at the pose's origin
     * @param width the larger of its extents across its length (x and y): what a spell model's scale measures
     */
    record Mesh(GeoCube[] cubes, Vector3f[] middles, Vector3f heart, float width) {
        int count() {
            return cubes.length;
        }
    }

    private record Cached(BakedGeoModel baked, Optional<Mesh> mesh) {
    }

    private static final Map<Identifier, Cached> MESHES = new HashMap<>();

    private SpellMeshes() {
    }

    /**
     * The mesh of the spell model {@code model} (a {@code realmforge:spell_model} id); empty when its GeckoLib model
     * is missing or has no cube, which a caller draws as the orb.
     */
    static Optional<Mesh> of(Identifier model) {
        Identifier geometry = SpellModel.geometry(model);
        BakedModelCache models = GeckoLibResources.getBakedModels();
        BakedGeoModel baked = models == null ? null : models.cache().get(geometry);
        Cached cached = MESHES.get(model);
        if (cached == null || cached.baked() != baked) {
            cached = new Cached(baked, baked == null ? Optional.empty() : mesh(baked));
            if (cached.mesh().isEmpty()) {
                Realmforge.LOGGER.warn("Spell model {} has no GeckoLib model {} with cubes: drawn as the orb", model,
                        geometry);
            }
            MESHES.put(model, cached);
        }
        return cached.mesh();
    }

    private static Optional<Mesh> mesh(BakedGeoModel baked) {
        List<GeoCube> cubes = new ArrayList<>();
        for (GeoBone bone : baked.topLevelBones()) {
            collect(bone, cubes);
        }
        if (cubes.isEmpty()) {
            return Optional.empty();
        }
        Vector3f low = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f high = new Vector3f(Float.NEGATIVE_INFINITY);
        Vector3f[] middles = new Vector3f[cubes.size()];
        for (int index = 0; index < middles.length; index++) {
            middles[index] = middle(cubes.get(index), low, high);
        }
        Vector3f heart = new Vector3f(low).add(high).mul(0.5F);
        double[] distances = new double[middles.length];
        for (int index = 0; index < distances.length; index++) {
            distances[index] = middles[index].distance(heart);
        }
        int[] ranks = FormingCurves.ranks(distances);
        GeoCube[] ordered = new GeoCube[ranks.length];
        Vector3f[] orderedMiddles = new Vector3f[ranks.length];
        for (int rank = 0; rank < ranks.length; rank++) {
            ordered[rank] = cubes.get(ranks[rank]);
            orderedMiddles[rank] = middles[ranks[rank]];
        }
        float width = Math.max(high.x - low.x, high.y - low.y);
        return width > 0 ? Optional.of(new Mesh(ordered, orderedMiddles, heart, width)) : Optional.empty();
    }

    private static void collect(GeoBone bone, List<GeoCube> into) {
        if (bone instanceof CuboidGeoBone cuboid) {
            into.addAll(List.of(cuboid.cubes));
        }
        for (GeoBone child : bone.children()) {
            collect(child, into);
        }
    }

    /**
     * The middle of {@code cube} as it is drawn, turned about its pivot as {@code GeoCube#render} turns it; widens
     * the bounds {@code low} to {@code high} by its turned corners.
     */
    private static Vector3f middle(GeoCube cube, Vector3f low, Vector3f high) {
        PoseStack turn = new PoseStack();
        cube.translateToPivotPoint(turn);
        cube.rotate(turn);
        cube.translateAwayFromPivotPoint(turn);
        Vector3f cubeLow = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f cubeHigh = new Vector3f(Float.NEGATIVE_INFINITY);
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            for (GeoVertex vertex : quad.vertices()) {
                Vector3f corner = turn.last().pose()
                        .transformPosition(vertex.posX(), vertex.posY(), vertex.posZ(), new Vector3f());
                cubeLow.min(corner);
                cubeHigh.max(corner);
            }
        }
        low.min(cubeLow);
        high.max(cubeHigh);
        return cubeLow.add(cubeHigh).mul(0.5F);
    }

    /** How big each cube of a mesh is drawn, as a share of its size, by its rank. */
    @FunctionalInterface
    interface CubeSizes {
        CubeSizes WHOLE = (rank, count) -> 1;

        float of(int rank, int count);
    }

    /**
     * Draws {@code mesh} into {@code buffer} at {@code pose}, its heart at the pose's origin, each cube at
     * {@code sizes}' share of its size about its own middle, lit by {@code light} and tinted {@code color} (ARGB: its
     * alpha fades the whole).
     */
    static void draw(PoseStack.Pose pose, VertexConsumer buffer, Mesh mesh, CubeSizes sizes, int light, int color) {
        PoseStack poseStack = new PoseStack();
        poseStack.last().set(pose);
        Vector3f heart = mesh.heart();
        poseStack.translate(-heart.x, -heart.y, -heart.z);
        for (int rank = 0; rank < mesh.count(); rank++) {
            float size = sizes.of(rank, mesh.count());
            if (size <= 0) {
                continue;
            }
            poseStack.pushPose();
            if (size < 1) {
                Vector3f middle = mesh.middles()[rank];
                poseStack.translate(middle.x, middle.y, middle.z);
                poseStack.scale(size, size, size);
                poseStack.translate(-middle.x, -middle.y, -middle.z);
            }
            // GeoCube#render turns the pose about the cube's pivot itself and leaves it turned.
            mesh.cubes()[rank].render(poseStack, buffer, light, OverlayTexture.NO_OVERLAY, color);
            poseStack.popPose();
        }
    }
}
