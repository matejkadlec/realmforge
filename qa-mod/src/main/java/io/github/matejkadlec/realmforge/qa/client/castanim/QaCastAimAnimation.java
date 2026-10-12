package io.github.matejkadlec.realmforge.qa.client.castanim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.AnimationContainer;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Wraps the test cast so its arms follow where the player looks (RLM-108). playerAnimator's
 * {@code THIRD_PERSON_MODEL} draws the model's own arms in first person, and those hang on the body: its yaw trails
 * the head by up to 50 degrees and it never pitches. A pose keyed for a level look therefore swings off to the side
 * after a turn, leaves the view when looking up and fills the top of it when looking down.
 *
 * <p>In the first-person pass the arms turn rigidly with the camera about the eye: their rotation is composed with
 * the camera's pitch and its yaw relative to the body, and their shoulder pivots move so each hand keeps its place
 * on screen. Seen from outside (third person, other players) only the rotation follows the head, as vanilla's bow
 * pose aims, so the shoulders stay on the body.
 *
 * <p>Model space here is a ModelPart's: pixels, y down, the front at -z, origin at the neck; playerAnimator hands
 * over pivots as positions and the part's {@code xRot}/{@code yRot}/{@code zRot} as rotations, which
 * {@code ModelPart} applies as Rz * Ry * Rx.
 */
final class QaCastAimAnimation extends AnimationContainer<IAnimation> {
    /** LivingEntityRenderer moves the model 1.501 blocks down after flipping it; PlayerRenderer scales it. */
    private static final float MODEL_ORIGIN_HEIGHT = 1.501F;
    private static final float PLAYER_MODEL_SCALE = 0.9375F;
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float FULL_TURN = Mth.TWO_PI;

    private final AbstractClientPlayer player;

    QaCastAimAnimation(AbstractClientPlayer player, IAnimation animation) {
        super(animation);
        this.player = player;
    }

    @Override
    public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value0) {
        Vec3f value = super.get3DTransform(modelName, type, tickDelta, value0);
        if (!modelName.equals("rightArm") && !modelName.equals("leftArm")) {
            return value;
        }
        // The first-person flag stays set until the camera's own player is drawn, so check whose model this is.
        boolean firstPerson = FirstPersonMode.isFirstPersonPass()
                && player == Minecraft.getInstance().getCameraEntity();
        return switch (type) {
            case ROTATION -> aim(value, look(tickDelta, firstPerson));
            case POSITION -> firstPerson ? movePivot(value, look(tickDelta, true)) : value;
            default -> value;
        };
    }

    /**
     * The look relative to the body in radians: x the pitch (down is positive), y the yaw (right is positive), as
     * vanilla's head gets them. In first person the camera's angles, which follow the mouse every frame.
     */
    private Vector3f look(float tickDelta, boolean firstPerson) {
        float bodyYaw = Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
        float yaw = firstPerson
                ? player.getViewYRot(tickDelta)
                : Mth.rotLerp(tickDelta, player.yHeadRotO, player.yHeadRot);
        float pitch = firstPerson ? player.getViewXRot(tickDelta) : Mth.lerp(tickDelta, player.xRotO, player.getXRot());
        return new Vector3f(pitch * Mth.DEG_TO_RAD, Mth.wrapDegrees(yaw - bodyYaw) * Mth.DEG_TO_RAD, 0.0F);
    }

    /** The look as a rotation in model space: yaw about the vertical axis after pitch, like the head. */
    private static Matrix3f lookRotation(Vector3f look) {
        return new Matrix3f().rotationY(look.y).rotateX(look.x);
    }

    /** The arm's rotation turned by the look, written back as the part's angles. */
    private static Vec3f aim(Vec3f rotation, Vector3f look) {
        float x = rotation.getX();
        float y = rotation.getY();
        float z = rotation.getZ();
        Vector3f angles = zyxAngles(lookRotation(look).rotateZYX(z, y, x));
        // Every angle is ambiguous by a full turn; take the one nearest the plain sum, so fading in and out of the
        // cast (which blends the angles one by one) swings the arms the short way.
        return new Vec3f(nearest(angles.x, x + look.x), nearest(angles.y, y + look.y), nearest(angles.z, z));
    }

    /** The shoulder pivot turned about the eye with the camera, so the whole arm moves with the view. */
    private Vec3f movePivot(Vec3f pivot, Vector3f look) {
        float eyeY = (MODEL_ORIGIN_HEIGHT - player.getEyeHeight() / (PLAYER_MODEL_SCALE * player.getScale()))
                * PIXELS_PER_BLOCK;
        Vector3f fromEye = new Vector3f(pivot.getX(), pivot.getY() - eyeY, pivot.getZ());
        lookRotation(look).transform(fromEye);
        return new Vec3f(fromEye.x, fromEye.y + eyeY, fromEye.z);
    }

    /**
     * The angles (x, y, z) with Rz(z) * Ry(y) * Rx(x) equal to the matrix, y in [-pi/2, pi/2]. JOML 1.10.5's own
     * {@code Quaternionf#getEulerAnglesZYX} returns a wrong x (it gives -1.549 for -1.6), so this reads the matrix.
     */
    private static Vector3f zyxAngles(Matrix3f rotation) {
        // JOML's mCR is column C, row R: m02 is row 2, column 0 (-sin y).
        float y = (float) Math.asin(Mth.clamp(-rotation.m02(), -1.0F, 1.0F));
        float x = (float) Mth.atan2(rotation.m12(), rotation.m22());
        float z = (float) Mth.atan2(rotation.m01(), rotation.m00());
        return new Vector3f(x, y, z);
    }

    private static float nearest(float angle, float target) {
        return angle + FULL_TURN * Math.round((target - angle) / FULL_TURN);
    }
}
