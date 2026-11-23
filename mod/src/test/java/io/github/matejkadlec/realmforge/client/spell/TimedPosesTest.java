package io.github.matejkadlec.realmforge.client.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TimedPosesTest {
    private static final int CASTER = 7;
    private static final int OTHER = 9;

    @Test
    void anAnimationIsDueOnceWhenItsTimeIsOver() {
        TimedPoses poses = new TimedPoses();
        poses.start(CASTER, 110);
        assertTrue(poses.playing(CASTER));
        assertEquals(List.of(), poses.due(109), "a tick before its end");
        assertEquals(List.of(CASTER), poses.due(110), "at its end");
        assertFalse(poses.playing(CASTER), "forgotten once it was due");
        assertEquals(List.of(), poses.due(200), "and due only once");
    }

    @Test
    void aSecondAnimationRightAfterTheFirstMovesItsEnd() {
        TimedPoses poses = new TimedPoses();
        poses.start(CASTER, 110);
        // A second instant spell as the global cooldown ends: its animation plays to its own end.
        poses.start(CASTER, 120);
        assertEquals(List.of(), poses.due(110), "the first animation's end no longer lowers the arms");
        assertEquals(List.of(CASTER), poses.due(120));
    }

    @Test
    void aCastThatTakesTheArmsLeavesNothingToLower() {
        TimedPoses poses = new TimedPoses();
        poses.start(CASTER, 110);
        poses.start(OTHER, 105);
        poses.stop(CASTER);
        assertFalse(poses.playing(CASTER));
        assertEquals(List.of(OTHER), poses.due(500), "only the other caster's animation is left");
    }

    @Test
    void clearingForgetsEveryAnimation() {
        TimedPoses poses = new TimedPoses();
        poses.start(CASTER, 110);
        poses.clear();
        assertEquals(List.of(), poses.due(500));
    }
}
