package io.github.matejkadlec.realmforge.qa.client.castanim;

import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.AnimationContainer;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Wraps the test cast so its arms follow where the player looks (RLM-108). PAL's {@code THIRD_PERSON_MODEL} draws
 * the model's own arms in first person, and those hang on the body: its yaw trails the head by up to 50 degrees and
 * it never pitches. A pose keyed for a level look therefore swings off to the side after a turn, leaves the view when
 * looking up and fills the top of it when looking down.
 *
 * <p>In the first-person pass the arms turn rigidly with the camera about the eye: their rotation is composed with
 * the camera's pitch and its yaw relative to the body, and their shoulder pivots move so each hand keeps its place
 * on screen. Seen from outside (third person, other players) only the rotation follows the head, as vanilla's bow
 * pose aims, so the shoulders stay on the body. PAL's own {@code setFirstPersonFollowsCamera} turns the whole model
 * by the head's angles of the last ticks instead, which trail the camera while it turns.
 *
 * <p>Model space here is a ModelPart's: pixels, y down, the front at -z, origin at the neck. A PAL bone holds the
 * part's {@code xRot}/{@code yRot}/{@code zRot} as its rotation, which {@code ModelPart} applies as Rz * Ry * Rx,
 * and its position as the offset from the part's initial pose with y up ({@code RenderUtil#translatePartToBone}).
 */
final class QaCastAimAnimation extends AnimationContainer<IAnimation> {
    /** LivingEntityRenderer moves the model 1.501 blocks down after flipping it; the avatar renderer scales it. */
    private static final float MODEL_ORIGIN_HEIGHT = 1.501F;
    private static final float PLAYER_MODEL_SCALE = 0.9375F;
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float FULL_TURN = Mth.TWO_PI;
    /** The arms' initial poses in {@code PlayerModel#createMesh}, the same for wide and slim arms. */
    private static final Vector3fc RIGHT_SHOULDER = new Vector3f(-5.0F, 2.0F, 0.0F);
    private static final Vector3fc LEFT_SHOULDER = new Vector3f(5.0F, 2.0F, 0.0F);

    private final AbstractClientPlayer player;
    private float partialTick;
    private boolean firstPersonPass;

    QaCastAimAnimation(AbstractClientPlayer player, IAnimation animation) {
        super(animation);
        this.player = player;
    }

    /** Called every frame before the bones are read, for this player's own render pass. */
    @Override
    public void setupAnim(AnimationData state) {
        super.setupAnim(state);
        partialTick = state.getPartialTick();
        firstPersonPass = state.isFirstPersonPass();
    }

    @Override
    public void get3DTransform(PlayerAnimBone bone) {
        super.get3DTransform(bone);
        Vector3fc shoulder = switch (bone.getName()) {
            case "right_arm" -> RIGHT_SHOULDER;
            case "left_arm" -> LEFT_SHOULDER;
            default -> null;
        };
        if (shoulder == null) {
            return;
        }
        Vector3f look = look(partialTick, firstPersonPass);
        aim(bone.rotation, look);
        if (firstPersonPass) {
            movePivot(bone.position, shoulder, look);
        }
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

    /** Turns the arm's rotation by the look and writes it back as the part's angles. */
    private static void aim(Vector3f rotation, Vector3f look) {
        float x = rotation.x;
        float y = rotation.y;
        float z = rotation.z;
        Vector3f angles = zyxAngles(lookRotation(look).rotateZYX(z, y, x));
        // Every angle is ambiguous by a full turn; take the one nearest the plain sum, so fading in and out of the
        // cast (which blends the angles one by one) swings the arms the short way.
        rotation.set(nearest(angles.x, x + look.x), nearest(angles.y, y + look.y), nearest(angles.z, z));
    }

    /** Turns the shoulder pivot about the eye with the camera, so the whole arm moves with the view. */
    private void movePivot(Vector3f offset, Vector3fc shoulder, Vector3f look) {
        float eyeY = (MODEL_ORIGIN_HEIGHT - player.getEyeHeight() / (PLAYER_MODEL_SCALE * player.getScale()))
                * PIXELS_PER_BLOCK;
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
