package io.github.matejkadlec.realmforge.client.animation;

import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The arm math of the cast animation (RLM-108, ADR 0020): it turns the animated arms with where the player looks,
 * without touching Player Animation Library, so JUnit covers it ({@code CastArmsTest}).
 *
 * <p>Model space here is a ModelPart's: pixels, y down, the front at -z, origin at the neck. A PAL bone holds the
 * part's {@code xRot}/{@code yRot}/{@code zRot} as its rotation, which {@code ModelPart} applies as Rz * Ry * Rx,
 * and its position as the offset from the part's initial pose with y up ({@code RenderUtil#translatePartToBone}).
 */
final class CastArms {
    /** The arms' initial poses in {@code PlayerModel#createMesh}, the same for wide and slim arms. */
    static final Vector3fc RIGHT_SHOULDER = new Vector3f(-5.0F, 2.0F, 0.0F);
    static final Vector3fc LEFT_SHOULDER = new Vector3f(5.0F, 2.0F, 0.0F);

    /** LivingEntityRenderer moves the model 1.501 blocks down after flipping it; the avatar renderer scales it. */
    private static final float MODEL_ORIGIN_HEIGHT = 1.501F;
    private static final float PLAYER_MODEL_SCALE = 0.9375F;
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float FULL_TURN = Mth.TWO_PI;

    private CastArms() {
    }

    /**
     * The look relative to the body in radians: x the pitch (down is positive), y the yaw (right is positive), as
     * vanilla's head gets them, from the look's pitch and yaw and the body's yaw in degrees.
     */
    static Vector3f look(float pitch, float yaw, float bodyYaw) {
        return new Vector3f(pitch * Mth.DEG_TO_RAD, Mth.wrapDegrees(yaw - bodyYaw) * Mth.DEG_TO_RAD, 0.0F);
    }

    /** The look as a rotation in model space: yaw about the vertical axis after pitch, like the head. */
    static Matrix3f lookRotation(Vector3f look) {
        return new Matrix3f().rotationY(look.y).rotateX(look.x);
    }

    /** Turns the arm's rotation by the look and writes it back as the part's angles. */
    static void aim(Vector3f rotation, Vector3f look) {
        float x = rotation.x;
        float y = rotation.y;
        float z = rotation.z;
        Vector3f angles = zyxAngles(lookRotation(look).rotateZYX(z, y, x));
        // Every angle is ambiguous by a full turn; take the one nearest the plain sum, so fading in and out of the
        // cast (which blends the angles one by one) swings the arms the short way.
        rotation.set(nearest(angles.x, x + look.x), nearest(angles.y, y + look.y), nearest(angles.z, z));
    }

    /**
     * The eye's height in model space, as its distance below the model's origin in pixels, for an eye
     * {@code eyeHeight} blocks above the feet of a player of scale {@code scale}.
     */
    static float eyeY(float eyeHeight, float scale) {
        return (MODEL_ORIGIN_HEIGHT - eyeHeight / (PLAYER_MODEL_SCALE * scale)) * PIXELS_PER_BLOCK;
    }

    /**
     * Turns the shoulder pivot about the eye with the camera, so the whole arm moves with the view: {@code offset} is
     * the bone's position, the offset from {@code shoulder}, which this rewrites.
     */
    static void movePivot(Vector3f offset, Vector3fc shoulder, Vector3f look, float eyeY) {
        // The part's pivot in model space, relative to the eye.
        Vector3f fromEye = new Vector3f(
                shoulder.x() + offset.x, shoulder.y() - offset.y - eyeY, shoulder.z() + offset.z);
        lookRotation(look).transform(fromEye);
        offset.set(fromEye.x - shoulder.x(), -(fromEye.y + eyeY - shoulder.y()), fromEye.z - shoulder.z());
    }

    /**
     * The angles (x, y, z) with Rz(z) * Ry(y) * Rx(x) equal to the matrix, y in [-pi/2, pi/2]. JOML 1.10.5's own
     * {@code Quaternionf#getEulerAnglesZYX} returned a wrong x (it gave -1.549 for -1.6), so this reads the matrix.
     */
    static Vector3f zyxAngles(Matrix3f rotation) {
        // JOML's mCR is column C, row R: m02 is row 2, column 0 (-sin y).
        float y = (float) Math.asin(Mth.clamp(-rotation.m02(), -1.0F, 1.0F));
        float x = (float) Mth.atan2(rotation.m12(), rotation.m22());
        float z = (float) Mth.atan2(rotation.m01(), rotation.m00());
        return new Vector3f(x, y, z);
    }

    /** {@code angle} moved by whole turns to lie within half a turn of {@code target}. */
    static float nearest(float angle, float target) {
        return angle + FULL_TURN * Math.round((target - angle) / FULL_TURN);
    }
}
