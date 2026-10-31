package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The geometry of a melee spell's hit shape (RLM-399; architecture 11.3): a slice of a circle or a box in front of the
 * caster, in the frame of its look, so it tilts with the pitch as the crosshair does, and {@code height} tall round the
 * look's line. Pure: JUnit covers it.
 *
 * <ul>
 *   <li><b>sector</b> (Slam's swing): the points within {@code reach} of the caster's eyes, measured across the look's
 *   plane, and within half of {@code angleDegrees} of the look on either side;</li>
 *   <li><b>box</b> (Reckless Strike's stab, Shield Bash's bash): {@code width} wide, from the front of the caster's body
 *   ({@code bodyHalfWidth} ahead of its eyes) to {@code reach} beyond it.</li>
 * </ul>
 *
 * A hitbox is in the shape when one of its {@link #samples} is: a grid at most a quarter of a block apart.
 *
 * @param kind sector or box
 * @param reach sector: its radius; box: its depth beyond the caster's body, in blocks
 * @param angleDegrees sector: how wide; 0 for a box
 * @param width box: how wide; 0 for a sector
 * @param height how tall round the look's line
 */
public record MeleeShape(Kind kind, double reach, double angleDegrees, double width, double height) {
    /** The farthest two samples of a hitbox lie apart, along each axis. */
    private static final double SAMPLE_SPACING = 0.25;
    private static final int MAX_SAMPLES_PER_AXIS = 9;

    /** The frame of a look: the eyes, and the forward, right and up unit vectors from the pitch and the yaw. */
    public record Frame(Vec3 eyes, Vec3 forward, Vec3 right, Vec3 up) {
        /** The frame of a look of {@code pitch} and {@code yaw} degrees (Minecraft's x and y rotation) from {@code eyes}. */
        public static Frame of(Vec3 eyes, float pitch, float yaw) {
            Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
            Vec3 up = Vec3.directionFromRotation(pitch - 90, yaw);
            Vec3 right = forward.cross(up).normalize();
            return new Frame(eyes, forward, right, up);
        }

        /** {@code point} in this frame: across (right), up and ahead (forward) of the eyes. */
        public Vec3 local(Vec3 point) {
            Vec3 offset = point.subtract(eyes);
            return new Vec3(offset.dot(right), offset.dot(up), offset.dot(forward));
        }

        /** The world point {@code across}, {@code up} and {@code ahead} of the eyes. */
        public Vec3 world(double across, double up, double ahead) {
            return eyes.add(right.scale(across)).add(this.up.scale(up)).add(forward.scale(ahead));
        }
    }

    /** Whether the world point {@code point} lies in the shape of a caster {@code bodyHalfWidth} wide in {@code frame}. */
    public boolean contains(Frame frame, double bodyHalfWidth, Vec3 point) {
        Vec3 local = frame.local(point);
        if (Math.abs(local.y) > height / 2) {
            return false;
        }
        return switch (kind) {
            case SECTOR -> local.z > 0 && Math.hypot(local.x, local.z) <= reach
                    && Math.toDegrees(Math.atan2(Math.abs(local.x), local.z)) <= angleDegrees / 2;
            case BOX -> Math.abs(local.x) <= width / 2 && local.z > bodyHalfWidth && local.z <= bodyHalfWidth + reach;
        };
    }

    /** Whether the hitbox {@code box} reaches into the shape: one of its samples lies in it. */
    public boolean reaches(Frame frame, double bodyHalfWidth, AABB box) {
        for (Vec3 sample : samples(box, SAMPLE_SPACING, MAX_SAMPLES_PER_AXIS, 0)) {
            if (contains(frame, bodyHalfWidth, sample)) {
                return true;
            }
        }
        return false;
    }

    /** How far from the eyes the shape reaches at most: what a search for hitboxes round the caster covers. */
    public double extent(double bodyHalfWidth) {
        double ahead = kind == Kind.SECTOR ? reach : bodyHalfWidth + reach;
        return Math.sqrt(ahead * ahead + Math.pow(Math.max(width, kind == Kind.SECTOR ? 2 * reach : 0) / 2, 2)
                + height * height / 4);
    }

    /**
     * The corners of the shape's outline in the world, as pairs of points (each line's two ends): what the hitbox display
     * draws (RLM-399). A sector's arc is drawn in {@code arcSegments} straight pieces.
     */
    public List<Vec3[]> outline(Frame frame, double bodyHalfWidth, int arcSegments) {
        List<Vec3[]> lines = new ArrayList<>();
        for (double up : new double[] {-height / 2, height / 2}) {
            switch (kind) {
                case SECTOR -> {
                    double half = Math.toRadians(angleDegrees / 2);
                    Vec3 previous = null;
                    for (int n = 0; n <= arcSegments; n++) {
                        double angle = -half + 2 * half * n / arcSegments;
                        Vec3 point = frame.world(Math.sin(angle) * reach, up, Math.cos(angle) * reach);
                        if (previous != null) {
                            lines.add(new Vec3[] {previous, point});
                        }
                        previous = point;
                    }
                    Vec3 centre = frame.world(0, up, 0);
                    lines.add(new Vec3[] {centre, frame.world(Math.sin(-half) * reach, up, Math.cos(-half) * reach)});
                    lines.add(new Vec3[] {centre, frame.world(Math.sin(half) * reach, up, Math.cos(half) * reach)});
                }
                case BOX -> {
                    double near = bodyHalfWidth;
                    double far = bodyHalfWidth + reach;
                    double side = width / 2;
                    lines.add(new Vec3[] {frame.world(-side, up, near), frame.world(side, up, near)});
                    lines.add(new Vec3[] {frame.world(side, up, near), frame.world(side, up, far)});
                    lines.add(new Vec3[] {frame.world(side, up, far), frame.world(-side, up, far)});
                    lines.add(new Vec3[] {frame.world(-side, up, far), frame.world(-side, up, near)});
                }
            }
        }
        // The edges between the bottom and the top.
        double half = Math.toRadians(angleDegrees / 2);
        List<Vec3> corners = switch (kind) {
            case SECTOR -> List.of(frame.world(0, 0, 0), frame.world(Math.sin(-half) * reach, 0, Math.cos(-half) * reach),
                    frame.world(Math.sin(half) * reach, 0, Math.cos(half) * reach));
            case BOX -> List.of(frame.world(-width / 2, 0, bodyHalfWidth), frame.world(width / 2, 0, bodyHalfWidth),
                    frame.world(width / 2, 0, bodyHalfWidth + reach), frame.world(-width / 2, 0, bodyHalfWidth + reach));
        };
        for (Vec3 corner : corners) {
            lines.add(new Vec3[] {corner.add(frame.up().scale(-height / 2)), corner.add(frame.up().scale(height / 2))});
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

    private static int count(double size, double spacing, int max) {
        return Mth.clamp((int) Math.ceil(size / spacing) + 1, 2, max);
    }

    private static double along(double min, double max, int index, int count, double inset) {
        double from = min + (max - min) * inset;
        double to = max - (max - min) * inset;
        return from + (to - from) * index / (count - 1);
    }

    /** The shapes a melee spell swings. */
    public enum Kind implements StringRepresentable {
        SECTOR("sector"),
        BOX("box");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String serializedName;

        Kind(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
