package io.github.matejkadlec.realmforge.dungeon.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** An arriving player looks at the layout's way on (architecture 15.5). */
class InstanceLayoutTest {
    private static final List<RoomPlacement> ROOMS =
            List.of(new RoomPlacement(Identifier.parse("realmforge:rooms/test"), BlockPos.ZERO, Rotation.NONE));
    private static final Vec3 SPAWN = new Vec3(7.5, 1, 7.5);

    @Test
    void theYawFacesTheWayOn() {
        assertEquals(-90.0F, yaw(new BlockPos(14, 1, 7)), 1.0E-4F, "east");
        assertEquals(0.0F, yaw(new BlockPos(7, 1, 14)), 1.0E-4F, "south");
        assertEquals(90.0F, yaw(new BlockPos(0, 1, 7)), 1.0E-4F, "west");
        assertEquals(-180.0F, yaw(new BlockPos(7, 1, 0)), 1.0E-4F, "north");
        assertEquals(-45.0F, yaw(new BlockPos(14, 1, 14)), 1.0E-4F, "south-east");
    }

    @Test
    void withoutAWayOnOrStraightBelowItFacesSouth() {
        assertEquals(0.0F, new InstanceLayout(ROOMS, Optional.empty()).yawFrom(SPAWN));
        assertEquals(0.0F, yaw(new BlockPos(7, -10, 7)), "a shaft straight down");
    }

    @Test
    void aLayoutNeedsARoom() {
        assertThrows(IllegalArgumentException.class, () -> new InstanceLayout(List.of(), Optional.empty()));
    }

    private static float yaw(BlockPos wayOn) {
        return new InstanceLayout(ROOMS, Optional.of(wayOn)).yawFrom(SPAWN);
    }
}
