package io.github.matejkadlec.realmforge.client.animation;

import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.AnimationContainer;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Wraps the cast animation so its arms follow where the player looks (RLM-108). PAL's {@code THIRD_PERSON_MODEL}
 * draws the model's own arms in first person, and those hang on the body: its yaw trails the head by up to 50 degrees
 * and it never pitches. A pose keyed for a level look therefore swings off to the side after a turn, leaves the view
 * when looking up and fills the top of it when looking down.
 *
 * <p>In the first-person pass the arms turn rigidly with the camera about the eye: their rotation is composed with
 * the camera's pitch and its yaw relative to the body, and their shoulder pivots move so each hand keeps its place
 * on screen. Seen from outside (third person, other players) only the rotation follows the head, as vanilla's bow
 * pose aims, so the shoulders stay on the body. PAL's own {@code setFirstPersonFollowsCamera} turns the whole model
 * by the head's angles of the last ticks instead, which trail the camera while it turns. The math is
 * {@link CastArms}.
 */
final class AimedCastAnimation extends AnimationContainer<IAnimation> {
    private final AbstractClientPlayer player;
    private float partialTick;
    private boolean firstPersonPass;

    AimedCastAnimation(AbstractClientPlayer player, IAnimation animation) {
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
            case "right_arm" -> CastArms.RIGHT_SHOULDER;
            case "left_arm" -> CastArms.LEFT_SHOULDER;
            default -> null;
        };
        if (shoulder == null) {
            return;
        }
        Vector3f look = look(partialTick, firstPersonPass);
        CastArms.aim(bone.rotation, look);
        if (firstPersonPass) {
            CastArms.movePivot(bone.position, shoulder, look, CastArms.eyeY(player.getEyeHeight(), player.getScale()));
        }
    }

    /** The look relative to the body; in first person the camera's angles, which follow the mouse every frame. */
    private Vector3f look(float tickDelta, boolean firstPerson) {
        float bodyYaw = Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
        float yaw = firstPerson
                ? player.getViewYRot(tickDelta)
                : Mth.rotLerp(tickDelta, player.yHeadRotO, player.yHeadRot);
        float pitch = firstPerson ? player.getViewXRot(tickDelta) : Mth.lerp(tickDelta, player.xRotO, player.getXRot());
        return CastArms.look(pitch, yaw, bodyYaw);
    }
}
