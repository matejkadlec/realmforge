package io.github.matejkadlec.realmforge.client.render;

import net.minecraft.world.phys.Vec3;

/**
 * Where a casting player's hands meet (RLM-640, ADR 0073 decision 7): the point a forming projectile is drawn at
 * and leaves from on screen, as plain math, so JUnit covers it ({@code HandPointsTest}). The cast animation raises
 * both arms forward and aims them where the player looks ({@code client/animation/CastArms}):
 *
 * <ul>
 *   <li><b>in first person</b> the whole arms turn about the eye with the camera, so the point is fixed on the
 *   screen: {@link #REACH} ahead of the eye and {@link #DROP} below the line of sight, which is below the screen's
 *   middle and under the crosshair at every field of view;</li>
 *   <li><b>seen from outside</b> the shoulders stay on the body and only the arms aim, so the point is
 *   {@link #REACH} from between the shoulders along the look.</li>
 * </ul>
 *
 * A mob's point is its own ({@code SpellVisuals#formOffset}).
 */
final class HandPoints {
    /** How far ahead the hands meet, in blocks for a player of scale 1: just past the tips of the raised arms. */
    static final double REACH = 0.7;
    /**
     * How far below the line of sight the point is in first person: the shoulders are a third of a block below the
     * eyes ({@code CastArms#eyeY}), and a model sits on the hands, a little above them.
     */
    static final double DROP = 0.3;
    /** The height of the shoulders above the feet for a player of scale 1 ({@code PlayerModel}, 0.9375 a pixel). */
    static final double SHOULDER_HEIGHT = 1.29;
    /** Seen from outside, how far above the arms' line the point is, so a model sits on the hands, not in them. */
    static final double LIFT = 0.05;

    private HandPoints() {
    }

    /**
     * The point for a player whose feet are at {@code feet}, of {@code scale}, looking along {@code pitch} and
     * {@code yaw} (vanilla's, in degrees), with their eyes {@code eyeHeight} above the feet; {@code firstPerson}
     * when the camera looks through their eyes.
     */
    static Vec3 player(Vec3 feet, double eyeHeight, double scale, float pitch, float yaw, boolean firstPerson) {
        Vec3 ahead = Vec3.directionFromRotation(pitch, yaw);
        Vec3 up = Vec3.directionFromRotation(pitch - 90, yaw);
        Vec3 pivot = feet.add(0, firstPerson ? eyeHeight : SHOULDER_HEIGHT * scale, 0);
        double above = (firstPerson ? -DROP : LIFT) * scale;
        return pivot.add(ahead.scale(REACH * scale)).add(up.scale(above));
    }
}
