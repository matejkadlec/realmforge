package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AimAssistTest {
    private static final double CLOSE = 1e-4;
    private static final Vec3 EYE = Vec3.ZERO;
    /** 40 blocks along the look, +z. */
    private static final Vec3 END = new Vec3(0, 0, 40);

    @Test
    void theAllowedMissGrowsWithTheDistanceAsMatejsNumbers() {
        assertEquals(0, AimAssist.allowedMiss(0), CLOSE);
        assertEquals(0.5, AimAssist.allowedMiss(4), CLOSE);
        assertEquals(1, AimAssist.allowedMiss(8), CLOSE);
        assertEquals(2, AimAssist.allowedMiss(16), CLOSE);
        assertEquals(4, AimAssist.allowedMiss(32), CLOSE);
        assertEquals(0, AimAssist.allowedMiss(-1), CLOSE);
    }

    @Test
    void aLineThroughTheBoxOrFromInsideItMissesNothing() {
        assertEquals(0, AimAssist.miss(EYE, END, box(0, 8), 1), CLOSE);
        assertEquals(0, AimAssist.miss(new Vec3(0, 0, 8), END, box(0, 8), 1), CLOSE);
    }

    @Test
    void theMissIsHowFarTheLinePassesBesideTheBox() {
        // A box 0.6 wide with its middle 1.3 blocks to the side: its near side is 1 block from the line.
        assertEquals(1, AimAssist.miss(EYE, END, box(1.3, 8), 2), CLOSE);
        assertEquals(0.5, AimAssist.miss(EYE, END, box(0.8, 4), 2), CLOSE);
    }

    @Test
    void aMissBeyondTheLimitIsInfinite() {
        assertTrue(Double.isInfinite(AimAssist.miss(EYE, END, box(1.3, 8), 0.9)));
        assertTrue(Double.isInfinite(AimAssist.miss(EYE, END, box(1.3, 8), 0)));
    }

    @Test
    void theConeTakesAMobMissedByLessThanItsDistanceAllowsAndNotOneMissedByMore() {
        // At 16 blocks the cone allows 2 blocks.
        assertTrue(candidate("near", 16, 1.9).inCone());
        assertTrue(candidate("edge", 16, 2).inCone());
        assertFalse(candidate("beyond", 16, 2.1).inCone());
        // Right next to the player nothing is allowed.
        assertFalse(candidate("beside", 0.5, 0.1).inCone());
        assertEquals(Optional.empty(), AimAssist.choose(List.of(candidate("beyond", 16, 2.1))));
    }

    @Test
    void directAimWinsOverEveryRuleAndTheNearestUnderTheCrosshairWins() {
        AimAssist.Candidate<String> boss = new AimAssist.Candidate<>("boss", 8, 0.5, false, true, true, true, 0.1,
                true, 20, 50);
        AimAssist.Candidate<String> far = direct("far", 12, false);
        AimAssist.Candidate<String> near = direct("near", 6, false);
        assertEquals(Optional.of("near"), AimAssist.choose(List.of(boss, far, near)));
        assertEquals(Optional.of("boss"), AimAssist.choose(List.of(boss)));
    }

    @Test
    void aPassiveMobGetsNoAssistanceButDirectAimHitsIt() {
        AimAssist.Candidate<String> cow = new AimAssist.Candidate<>("cow", 8, 0.2, false, false, false, false, 1,
                false, 1, 1);
        assertFalse(cow.inCone());
        assertEquals(Optional.empty(), AimAssist.choose(List.of(cow)));
        AimAssist.Candidate<String> hostile = candidate("husk", 8, 0.9);
        assertEquals(Optional.of("husk"), AimAssist.choose(List.of(cow, hostile)));
        assertEquals(Optional.of("cow"), AimAssist.choose(List.of(direct("cow", 8, false), hostile)));
    }

    @Test
    void theRulesGoInOrderEachBreakingOnlyTheTiesAbove() {
        AimAssist.Candidate<String> plain = new AimAssist.Candidate<>("plain", 8, 0.1, false, true, false, false, 1,
                false, 1, 1);
        AimAssist.Candidate<String> level = with(plain, "level", false, false, 1, false, 1, 9);
        AimAssist.Candidate<String> rank = with(plain, "rank", false, false, 1, false, 2, 1);
        AimAssist.Candidate<String> current = with(plain, "current", false, false, 1, true, 1, 1);
        AimAssist.Candidate<String> dying = with(plain, "dying", false, false, 0.2, false, 1, 1);
        AimAssist.Candidate<String> boss = with(plain, "boss", false, true, 1, false, 1, 1);
        AimAssist.Candidate<String> add = with(plain, "add", true, false, 1, false, 1, 1);

        assertEquals(Optional.of("add"), AimAssist.choose(List.of(plain, level, rank, current, dying, boss, add)));
        assertEquals(Optional.of("boss"), AimAssist.choose(List.of(plain, level, rank, current, dying, boss)));
        assertEquals(Optional.of("dying"), AimAssist.choose(List.of(plain, level, rank, current, dying)));
        assertEquals(Optional.of("current"), AimAssist.choose(List.of(plain, level, rank, current)));
        assertEquals(Optional.of("rank"), AimAssist.choose(List.of(plain, level, rank)));
        assertEquals(Optional.of("level"), AimAssist.choose(List.of(plain, level)));
    }

    @Test
    void aLowerRuleNeverOutweighsAHigherOne() {
        // A dying mob of the lowest rank and level, farthest from the crosshair, beats a healthy current target of a
        // higher rank and level right beside it: in order, not added up (Q2).
        AimAssist.Candidate<String> dying = new AimAssist.Candidate<>("dying", 8, 0.9, false, true, false, false,
                0.1, false, 0.5, 1);
        AimAssist.Candidate<String> current = new AimAssist.Candidate<>("current", 8, 0.1, false, true, false, false,
                1, true, 10, 30);
        assertEquals(Optional.of("dying"), AimAssist.choose(List.of(current, dying)));
    }

    @Test
    void theLastRuleTakesTheMobNearestToTheCrosshairForItsDistance() {
        // 1 block beside at 16 blocks is nearer the crosshair than 0.8 beside at 8.
        assertEquals(Optional.of("far"), AimAssist.choose(List.of(candidate("near", 8, 0.8),
                candidate("far", 16, 1))));
    }

    @Test
    void aBoltTurnsAtMostItsTurnPerTickAndKeepsItsSpeed() {
        Vec3 velocity = new Vec3(0, 0, 1);
        Vec3 turned = AimAssist.turn(velocity, new Vec3(1, 0, 0), AimAssist.TURN_PER_TICK);
        assertEquals(1, turned.length(), CLOSE);
        assertEquals(AimAssist.TURN_PER_TICK, Math.acos(turned.normalize().dot(velocity)), CLOSE);
        assertTrue(turned.x > 0, "it turned toward +x: " + turned);
    }

    @Test
    void aBoltWithinItsTurnPointsStraightAtItsTarget() {
        Vec3 velocity = new Vec3(0, 0, 2);
        Vec3 toward = new Vec3(0.05, 0, 1);
        Vec3 turned = AimAssist.turn(velocity, toward, AimAssist.TURN_PER_TICK);
        assertEquals(0, turned.normalize().distanceTo(toward.normalize()), CLOSE);
        assertEquals(2, turned.length(), CLOSE);
    }

    @Test
    void aBoltWithoutAWayToTurnFliesOn() {
        Vec3 velocity = new Vec3(0, 0, 1);
        assertEquals(velocity, AimAssist.turn(velocity, new Vec3(0, 0, -1), AimAssist.TURN_PER_TICK));
        assertEquals(velocity, AimAssist.turn(velocity, Vec3.ZERO, AimAssist.TURN_PER_TICK));
        assertEquals(Vec3.ZERO, AimAssist.turn(Vec3.ZERO, velocity, AimAssist.TURN_PER_TICK));
    }

    /** A 0.6 x 1.8 x 0.6 box (a zombie's) with its middle at {@code x}, 0, {@code z}. */
    private static AABB box(double x, double z) {
        return new AABB(x - 0.3, -0.9, z - 0.3, x + 0.3, 0.9, z + 0.3);
    }

    /** A hostile mob of no special rule, {@code along} blocks out and missed by {@code miss}. */
    private static AimAssist.Candidate<String> candidate(String name, double along, double miss) {
        return new AimAssist.Candidate<>(name, along, miss, false, true, false, false, 1, false, 1, 1);
    }

    private static AimAssist.Candidate<String> direct(String name, double along, boolean assistable) {
        return new AimAssist.Candidate<>(name, along, 0, true, assistable, false, false, 1, false, 1, 1);
    }

    private static AimAssist.Candidate<String> with(AimAssist.Candidate<String> base, String name, boolean add,
            boolean boss, double health, boolean current, double rank, int level) {
        return new AimAssist.Candidate<>(name, base.along(), base.miss(), false, true, add, boss, health, current, rank,
                level);
    }
}
