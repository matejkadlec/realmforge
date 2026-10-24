package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SpellVisualsTest {
    private static final double CLOSE = 1e-6;

    @Test
    void theHewnChanterFormsItsStoneInItsRaisedHands() {
        // 0.7 wide, 3 tall, its eyes at vanilla's 85 % (2.55), facing south: its hands reach about 1.1 blocks ahead
        // and 3 blocks up while it casts.
        Vec3 offset = SpellVisuals.formOffset(0.7F, 3.0F, 2.55F, 0);
        assertEquals(0, offset.x, CLOSE);
        assertEquals(2.95, offset.y, CLOSE);
        assertEquals(1.1, offset.z, CLOSE);
    }

    @Test
    void theFormPointTurnsWithTheBody() {
        // Yaw 90 faces west (-x).
        Vec3 offset = SpellVisuals.formOffset(0.7F, 3.0F, 2.55F, 90);
        assertEquals(-1.1, offset.x, CLOSE);
        assertEquals(0, offset.z, CLOSE);
    }

    @Test
    void aShortCasterFormsNoHigherThanItsHeight() {
        // A 2-block caster's eyes are at 1.7: 0.4 above them would be past the room it stands in.
        assertEquals(2.0, SpellVisuals.formOffset(0.6F, 2.0F, 1.7F, 0).y, CLOSE);
    }
}
