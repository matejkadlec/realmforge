package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * A projectile's trajectory (RLM-639, ADR 0073): a pure function of the launch point, the aim, the ticks flown and
 * the seed, so the server and its clients fly the same path. Every projectile flies {@code straight} (ADR 0052).
 */
class SpellTrajectoryTest {
    private static final SpellTrajectory STRAIGHT = new SpellTrajectory(SpellTrajectory.Kind.STRAIGHT);
    private static final Vec3 LAUNCH = new Vec3(10.5, 64.25, -3.75);
    /** Frostbolt's 30 blocks a second, up and to one side. */
    private static final Vec3 AIM = new Vec3(0.9, 0.3, -1.2).normalize().scale(1.5);

    @Test
    void straightStepsByItsAimEveryTickWhateverTheSeed() {
        for (int tick : new int[] {0, 1, 7, 199}) {
            for (long seed : new long[] {0, 42, Long.MIN_VALUE}) {
                assertSame(AIM, STRAIGHT.velocity(AIM, tick, seed), "tick " + tick + ", seed " + seed);
            }
        }
    }

    @Test
    void straightIsOnTheLineThroughTheLaunchPointAlongTheAim() {
        assertEquals(LAUNCH, STRAIGHT.position(LAUNCH, AIM, 0, 7), "it has not flown yet");
        for (int ticks : new int[] {1, 5, 40}) {
            Vec3 position = STRAIGHT.position(LAUNCH, AIM, ticks, 7);
            assertTrue(position.distanceTo(LAUNCH.add(AIM.scale(ticks))) < 1e-9, ticks + " ticks along the aim");
            assertEquals(STRAIGHT.position(LAUNCH, AIM, ticks, 99), position, "the seed changes nothing");
        }
    }

    @Test
    void thePathIsTheProjectilesOwnStepsAddedUp() {
        // SpellProjectile#tick moves by velocity(aim, flown, seed) and counts the tick: the same sum, to the bit.
        double x = LAUNCH.x;
        double y = LAUNCH.y;
        double z = LAUNCH.z;
        for (int flown = 0; flown < 60; flown++) {
            assertEquals(new Vec3(x, y, z), STRAIGHT.position(LAUNCH, AIM, flown, 3), "after " + flown + " ticks");
            Vec3 step = STRAIGHT.velocity(AIM, flown, 3);
            x += step.x;
            y += step.y;
            z += step.z;
        }
    }

    @Test
    void theSamePathFromTheSameSpawnData() {
        // A client computes its path from the trajectory, the aim, the seed and the ticks flown that the spawn carries.
        SpellTrajectory onClient = SpellTrajectory.CODEC.parse(JsonOps.INSTANCE,
                SpellTrajectory.CODEC.encodeStart(JsonOps.INSTANCE, STRAIGHT).getOrThrow()).getOrThrow();
        assertEquals(STRAIGHT, onClient);
        Vec3 joinedAt = STRAIGHT.position(LAUNCH, AIM, 12, 5);
        assertEquals(STRAIGHT.position(LAUNCH, AIM, 20, 5), onClient.position(joinedAt, AIM, 8, 5),
                "a client that starts to track it mid-flight flies on along the server's path");
    }

    @Test
    void readsItsKindAndRefusesAnUnknownOne() {
        assertEquals(STRAIGHT, SpellTrajectory.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"kind\": \"straight\"}")).getOrThrow());
        assertTrue(SpellTrajectory.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"kind\": \"curve\"}"))
                .isError(), "no curve: every projectile flies straight (Matěj, 2026-10-29)");
        assertTrue(SpellTrajectory.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).isError(),
                "a trajectory names its kind");
    }
}
