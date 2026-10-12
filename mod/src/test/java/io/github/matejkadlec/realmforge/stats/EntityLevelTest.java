package io.github.matejkadlec.realmforge.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.testing.RoundTrip;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

class EntityLevelTest {
    private static final EntityLevel LEVEL = new EntityLevel(37);

    @Test
    void codecRoundTrips() {
        assertEquals(LEVEL, RoundTrip.nbt(EntityLevel.CODEC, LEVEL));
        assertEquals(LEVEL, RoundTrip.json(EntityLevel.CODEC, LEVEL));
    }

    @Test
    void streamCodecRoundTrips() {
        assertEquals(LEVEL, RoundTrip.stream(EntityLevel.STREAM_CODEC, LEVEL));
        assertEquals(EntityLevel.DEFAULT, RoundTrip.stream(EntityLevel.STREAM_CODEC, EntityLevel.DEFAULT));
    }

    /** The saved shape is a plain number. */
    @Test
    void savedShapeIsTheNumber() {
        assertEquals(IntTag.valueOf(37), EntityLevel.CODEC.encodeStart(NbtOps.INSTANCE, LEVEL).getOrThrow());
        assertEquals(LEVEL, EntityLevel.CODEC.parse(NbtOps.INSTANCE, IntTag.valueOf(37)).getOrThrow());
    }

    @Test
    void levelsBelowOneAreRefused() {
        assertTrue(EntityLevel.CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createInt(0)).isError());
        assertTrue(EntityLevel.CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createInt(-3)).isError());
        assertThrows(IllegalArgumentException.class, () -> new EntityLevel(0));
    }

    @Test
    void defaultIsLevelOne() {
        assertEquals(1, EntityLevel.DEFAULT.value());
    }
}
