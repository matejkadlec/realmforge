package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The geometry of a melee spell's hit shape (RLM-399; architecture 11.3): one of Better Combat's sword hitboxes at the
 * caster's melee reach, so a melee spell hits where a melee attack of the same reach does and grows with the reach
 * (Matěj's playtest of 2026-10-31). Pure: JUnit covers it.
 *
 * <p>Better Combat 3.2.2's rules, read from its jar ({@code WeaponHitBoxes}, {@code TargetFinder}): a box in the frame
 * of the look, so it tilts with the pitch, from the shape's origin (the eyes, lowered by 15 % of the caster's height)
 * to {@code reach} ahead. A hitbox is struck when it touches the box (or the box holds its middle), its nearest point
 * lies within {@code reach} of the origin, and, for a swing, its middle or its nearest point lies within half the
 * swing's angle of the look.
 *
 * <ul>
 *   <li><b>swing</b> (Better Combat's horizontal plane, its sword's two slashes): 2 × reach wide, reach / 3 tall,
 *   120°. The default melee hit shape: Slam, Reckless Strike.</li>
 *   <li><b>stab</b> (its forward box, the sword's stab): reach / 2 wide and tall. Shield Bash.</li>
 * </ul>
 *
 * @param kind a swing or a stab
 * @param reach how far ahead of the origin it reaches, in blocks: the caster's melee reach
 */
public record MeleeShape(Kind kind, double reach) {
    /** How far below the eyes the shape starts, as a share of the caster's height (Better Combat's 0.15). */
    public static final double ORIGIN_DROP = 0.15;
    private static final double PARALLEL = 1e-9;

    /** The frame of a look: the shape's origin, and the forward, right and up unit vectors from the pitch and yaw. */
    public record Frame(Vec3 origin, Vec3 forward, Vec3 right, Vec3 up) {
        /** The frame of a look of {@code pitch} and {@code yaw} degrees (Minecraft's x and y rotation) from a point. */
        public static Frame of(Vec3 origin, float pitch, float yaw) {
            Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
            Vec3 up = Vec3.directionFromRotation(pitch - 90, yaw);
            Vec3 right = forward.cross(up).normalize();
            return new Frame(origin, forward, right, up);
        }

        /** {@code point} in this frame: across (right), up and ahead (forward) of the origin. */
        public Vec3 local(Vec3 point) {
            Vec3 offset = point.subtract(origin);
            return new Vec3(offset.dot(right), offset.dot(up), offset.dot(forward));
        }

        /** The world point {@code across}, {@code up} and {@code ahead} of the origin. */
        public Vec3 world(double across, double up, double ahead) {
            return origin.add(right.scale(across)).add(this.up.scale(up)).add(forward.scale(ahead));
        }
    }

    /** The box's size: across, up and ahead, in blocks. */
    public Vec3 size() {
        return switch (kind) {
            case SWING -> new Vec3(2 * reach, reach / 3, reach);
            case STAB -> new Vec3(reach / 2, reach / 2, reach);
        };
    }

    /** Whether the shape in {@code frame} strikes the hitbox {@code box}, by Better Combat's rules (above). */
    public boolean strikes(Frame frame, AABB box) {
        Vec3 half = size().scale(0.5);
        Vec3 centre = frame.world(0, 0, reach / 2);
        if (!touches(frame, centre, half, box) && !holds(frame, centre, half, box.getCenter())) {
            return false;
        }
        Vec3 nearest = nearestPoint(box, frame.origin()).subtract(frame.origin());
        if (nearest.length() > reach) {
            return false;
        }
        double halfAngle = kind.angleDegrees() / 2;
        return halfAngle <= 0 || nearest.lengthSqr() < PARALLEL
                || angle(box.getCenter().subtract(frame.origin()), frame.forward()) <= halfAngle
                || angle(nearest, frame.forward()) <= halfAngle;
    }

    /** How far from the origin the shape reaches at most: what a search for hitboxes round the caster covers. */
    public double extent() {
        Vec3 size = size();
        return Math.sqrt(size.x * size.x / 4 + size.y * size.y / 4 + size.z * size.z);
    }

    /**
     * The shape's outline in the world, as pairs of points (each line's two ends): what the hitbox display draws
     * (RLM-399). The box's twelve edges, and for a swing its angle: the arc of {@code reach} it sweeps in the middle of
     * the box, in {@code arcSegments} straight pieces, with the two lines from the origin to the arc's ends.
     */
    public List<Vec3[]> outline(Frame frame, int arcSegments) {
        List<Vec3[]> lines = new ArrayList<>();
        Vec3 half = size().scale(0.5);
        double[] across = {-half.x, half.x};
        double[] up = {-half.y, half.y};
        double[] ahead = {0, reach};
        for (double y : up) {
            for (double z : ahead) {
                lines.add(new Vec3[] {frame.world(across[0], y, z), frame.world(across[1], y, z)});
            }
            for (double x : across) {
                lines.add(new Vec3[] {frame.world(x, y, ahead[0]), frame.world(x, y, ahead[1])});
            }
        }
        for (double x : across) {
            for (double z : ahead) {
                lines.add(new Vec3[] {frame.world(x, up[0], z), frame.world(x, up[1], z)});
            }
        }
        double halfAngle = Math.toRadians(kind.angleDegrees() / 2);
        if (halfAngle > 0) {
            Vec3 previous = frame.world(Math.sin(-halfAngle) * reach, 0, Math.cos(-halfAngle) * reach);
            lines.add(new Vec3[] {frame.origin(), previous});
            for (int n = 1; n <= arcSegments; n++) {
                double angle = -halfAngle + 2 * halfAngle * n / arcSegments;
                Vec3 point = frame.world(Math.sin(angle) * reach, 0, Math.cos(angle) * reach);
                lines.add(new Vec3[] {previous, point});
                previous = point;
            }
            lines.add(new Vec3[] {frame.origin(), previous});
        }
        return lines;
    }

    /**
     * Points spread through {@code box}: a grid at most {@code spacing} apart along each axis, at most {@code max} per
     * axis and at least 2, kept {@code inset} of each side's length inside the box (0 keeps the faces).
     */
    public static List<Vec3> samples(AABB box, double spacing, int max, double inset) {
        int nx = count(box.getXsize(), spacing, max);
        int ny = count(box.getYsize(), spacing, max);
        int nz = count(box.getZsize(), spacing, max);
        List<Vec3> points = new ArrayList<>(nx * ny * nz);
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                for (int k = 0; k < nz; k++) {
                    points.add(new Vec3(along(box.minX, box.maxX, i, nx, inset),
                            along(box.minY, box.maxY, j, ny, inset), along(box.minZ, box.maxZ, k, nz, inset)));
                }
            }
        }
        return points;
    }

    /**
     * Whether the box of {@code half} extents round {@code centre}, turned as {@code frame}, and {@code box} overlap:
     * no axis separates them (the separating axis test over both boxes' axes and their nine cross products).
     */
    private static boolean touches(Frame frame, Vec3 centre, Vec3 half, AABB box) {
        Vec3[] turned = {frame.right(), frame.up(), frame.forward()};
        double[] turnedHalf = {half.x, half.y, half.z};
        Vec3[] world = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
        double[] worldHalf = {box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2};
        Vec3 between = box.getCenter().subtract(centre);
        List<Vec3> axes = new ArrayList<>(List.of(turned));
        axes.addAll(List.of(world));
        for (Vec3 a : turned) {
            for (Vec3 b : world) {
                Vec3 cross = a.cross(b);
                if (cross.lengthSqr() > PARALLEL) {
                    axes.add(cross);
                }
            }
        }
        for (Vec3 axis : axes) {
            double reach = projection(turned, turnedHalf, axis) + projection(world, worldHalf, axis);
            if (Math.abs(between.dot(axis)) > reach) {
                return false;
            }
        }
        return true;
    }

    private static double projection(Vec3[] axes, double[] halves, Vec3 onto) {
        double sum = 0;
        for (int i = 0; i < axes.length; i++) {
            sum += halves[i] * Math.abs(axes[i].dot(onto));
        }
        return sum;
    }

    /** Whether the box of {@code half} extents round {@code centre}, turned as {@code frame}, holds a point. */
    private static boolean holds(Frame frame, Vec3 centre, Vec3 half, Vec3 point) {
        Vec3 offset = point.subtract(centre);
        return Math.abs(offset.dot(frame.right())) <= half.x && Math.abs(offset.dot(frame.up())) <= half.y
                && Math.abs(offset.dot(frame.forward())) <= half.z;
    }

    private static Vec3 nearestPoint(AABB box, Vec3 point) {
        return new Vec3(Mth.clamp(point.x, box.minX, box.maxX), Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    /** The angle between two vectors, in degrees. */
    private static double angle(Vec3 a, Vec3 b) {
        return Math.toDegrees(Math.acos(Mth.clamp(a.normalize().dot(b.normalize()), -1, 1)));
    }

    private static int count(double size, double spacing, int max) {
        return Mth.clamp((int) Math.ceil(size / spacing) + 1, 2, max);
    }

    private static double along(double min, double max, int index, int count, double inset) {
        double from = min + (max - min) * inset;
        double to = max - (max - min) * inset;
        return from + (to - from) * index / (count - 1);
    }

    /** The shapes a melee spell swings: Better Combat's sword hitboxes. */
    public enum Kind implements StringRepresentable {
        SWING("swing", 120),
        STAB("stab", 0);

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String serializedName;
        private final double angleDegrees;

        Kind(String serializedName, double angleDegrees) {
            this.serializedName = serializedName;
            this.angleDegrees = angleDegrees;
        }

        /** How wide a swing sweeps, in degrees; 0 for a stab, which only its box limits. */
        public double angleDegrees() {
            return angleDegrees;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
