package io.github.matejkadlec.realmforge.client.hud;

import net.minecraft.util.Mth;

/** The geometry of the hit direction arc ({@link HitDirectionHud}), apart from drawing so JUnit can check it. */
public final class HitDirections {
    private HitDirections() {
    }

    /**
     * Where a point lies, seen from above, relative to where the player looks: radians clockwise from the top of the
     * screen, so 0 is straight ahead, π/2 to the right, π behind and -π/2 to the left. Minecraft's yaw is 0 facing
     * south (+Z) and 90 facing west (-X).
     */
    public static float screenAngle(double fromX, double fromZ, float yawDegrees, double toX, double toZ) {
        double bearing = Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
        return (float) Math.toRadians(Mth.wrapDegrees(bearing - yawDegrees));
    }
}
