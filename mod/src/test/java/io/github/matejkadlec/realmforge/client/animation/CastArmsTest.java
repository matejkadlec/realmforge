package io.github.matejkadlec.realmforge.client.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class CastArmsTest {
    private static final float CLOSE = 1e-4F;
    /** The hold pose of cast.json's right arm. */
    private static final Vector3f HOLD = new Vector3f(-1.60F, -0.22F, 0.10F);

    @Test
    void theAnglesOfAZyxRotationComeBackFromItsMatrix() {
        float[][] cases = {{-1.6F, 0.22F, 0.1F}, {0.4F, -1.2F, -2.9F}, {2.8F, 1.4F, 0.0F}, {-3.0F, 0.0F, 3.0F}};
        for (float[] angles : cases) {
            Matrix3f rotation = new Matrix3f().rotateZYX(angles[2], angles[1], angles[0]);
            assertClose(new Vector3f(angles[0], angles[1], angles[2]), CastArms.zyxAngles(rotation));
        }
    }

    @Test
    void aLevelLookLeavesThePoseAsItIs() {
        Vector3f rotation = new Vector3f(HOLD);
        CastArms.aim(rotation, CastArms.look(0, 30, 30));
        assertClose(HOLD, rotation);
    }

    @Test
    void lookingDownPitchesAnArmThatOnlyPitches() {
        Vector3f rotation = new Vector3f(-1.6F, 0, 0);
        CastArms.aim(rotation, CastArms.look(30, 0, 0));
        assertClose(new Vector3f(-1.6F + 30 * Mth.DEG_TO_RAD, 0, 0), rotation);
    }

    @Test
    void turningRightOfTheBodyYawsAnArmThatOnlyPitches() {
        Vector3f rotation = new Vector3f(-1.6F, 0, 0);
        // The head 40 degrees to the right of the body, which trails it.
        CastArms.aim(rotation, CastArms.look(0, 130, 90));
        assertClose(new Vector3f(-1.6F, 40 * Mth.DEG_TO_RAD, 0), rotation);
    }

    @Test
    void theYawRelativeToTheBodyWrapsAroundAFullTurn() {
        // The head at 170 degrees, the body at -170: 340 degrees apart one way, 20 to the left the short way.
        assertEquals(-20 * Mth.DEG_TO_RAD, CastArms.look(0, 170, -170).y, CLOSE);
    }

    @Test
    void anAimPastHalfATurnKeepsTheAngleNearestThePlainSum() {
        // -2.0 + -1.5 is past -pi: the matrix gives +2.78, a full turn the other way, which a fade would swing through.
        Vector3f rotation = new Vector3f(-2.0F, 0, 0);
        CastArms.aim(rotation, new Vector3f(-1.5F, 0, 0));
        assertEquals(-3.5F, rotation.x, CLOSE);
    }

    @Test
    void nearestMovesAnAngleByWholeTurnsOnly() {
        assertEquals(-3.0F + Mth.TWO_PI, CastArms.nearest(-3.0F, 3.0F), CLOSE);
        assertEquals(0.1F + 2 * Mth.TWO_PI, CastArms.nearest(0.1F, 0.1F + 2 * Mth.TWO_PI), CLOSE);
        assertEquals(1.0F, CastArms.nearest(1.0F, 1.2F), CLOSE);
    }

    @Test
    void theEyeOfAPlayerOfScaleOneIsAboveTheModelsOrigin() {
        // 1.62 blocks up, in a model drawn 1.501 blocks down at 0.9375 of a block: 3.6 pixels above the neck.
        assertEquals(-3.632F, CastArms.eyeY(1.62F, 1.0F), 1e-3F);
    }

    @Test
    void aLevelLookLeavesTheShoulderWhereItIs() {
        Vector3f offset = new Vector3f(0.5F, -1.0F, 0.25F);
        CastArms.movePivot(offset, CastArms.RIGHT_SHOULDER, CastArms.look(0, 0, 0), CastArms.eyeY(1.62F, 1.0F));
        assertClose(new Vector3f(0.5F, -1.0F, 0.25F), offset);
    }

    @Test
    void theShoulderTurnsRigidlyAboutTheEye() {
        float eyeY = CastArms.eyeY(1.62F, 1.0F);
        Vector3f offset = new Vector3f();
        CastArms.movePivot(offset, CastArms.RIGHT_SHOULDER, CastArms.look(45, 60, 0), eyeY);
        // Both pivots in model space relative to the eye; the bone's position has y up, the model's y down.
        Vector3f before = new Vector3f(CastArms.RIGHT_SHOULDER).sub(0, eyeY, 0);
        Vector3f after = new Vector3f(CastArms.RIGHT_SHOULDER).add(offset.x, -offset.y, offset.z).sub(0, eyeY, 0);
        assertEquals(before.length(), after.length(), CLOSE);
        assertClose(CastArms.lookRotation(CastArms.look(45, 60, 0)).transform(new Vector3f(before)), after);
    }

    @Test
    void turningNinetyDegreesRightMovesTheRightShoulderBehindTheEye() {
        float eyeY = CastArms.eyeY(1.62F, 1.0F);
        Vector3f offset = new Vector3f();
        CastArms.movePivot(offset, CastArms.RIGHT_SHOULDER, CastArms.look(0, 90, 0), eyeY);
        // A yaw of +90 degrees takes model x -5 (the right shoulder) to z +5: behind the eye, as the front is -z.
        assertClose(new Vector3f(5.0F, 0, 5.0F), offset);
    }

    private static void assertClose(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, CLOSE, "x of " + actual);
        assertEquals(expected.y, actual.y, CLOSE, "y of " + actual);
        assertEquals(expected.z, actual.z, CLOSE, "z of " + actual);
    }
}
