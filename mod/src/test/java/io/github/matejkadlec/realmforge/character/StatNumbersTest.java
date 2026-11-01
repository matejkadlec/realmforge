package io.github.matejkadlec.realmforge.character;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

/**
 * RLM-530: every value on the character sheet with one decimal place, percentages included (Matěj, 2026-10-30), half
 * up, without a trailing {@code .0}.
 */
class StatNumbersTest {
    @Test
    void pointsAreWholeOrHaveOneDecimal() {
        assertEquals("25", StatNumbers.points(25));
        assertEquals("12.5", StatNumbers.points(12.5));
        assertEquals("52.8", StatNumbers.points(52.8));
        assertEquals("1.6", StatNumbers.points(1.6000000001));
        assertEquals("0", StatNumbers.points(0));
        assertEquals("0.3", StatNumbers.points(0.25), "half up");
        assertEquals("20", StatNumbers.points(19.96), "rounds to one decimal first");
        assertEquals("6.4", StatNumbers.points(6.44));
        assertEquals("6.5", StatNumbers.points(6.45), "half up, from the decimal the double prints as");
        assertEquals("0.2", StatNumbers.points(0.15), "0.15 is a little less as a double, but reads 0.2");
        assertEquals("-2.5", StatNumbers.points(-2.45), "half away from zero below it");
        assertEquals("0", StatNumbers.points(-0.01), "no negative zero");
    }

    @Test
    void sharesReadInPercentWithOneDecimal() {
        assertEquals("0.5%", StatNumbers.share(0.005), "Strength's half a percent of lifesteal is no longer 1%");
        assertEquals("13.5%", StatNumbers.share(0.135));
        assertEquals("120%", StatNumbers.share(1.2), "no trailing .0");
        assertEquals("5%", StatNumbers.share(0.05));
        assertEquals("12.5%", StatNumbers.share(0.125));
        assertEquals("0.3%", StatNumbers.share(0.0025), "half up");
        assertEquals("0%", StatNumbers.share(0));
        assertEquals("100.5%", StatNumbers.share(1.005));
        assertEquals("100.4%", StatNumbers.share(1.004));
        assertEquals("0%", StatNumbers.share(-0.0001), "no negative zero");
    }

    @Test
    void movementSpeedReadsAsAPercentOfItsBase() {
        assertEquals("105%", StatNumbers.percentOf(0.105, 0.1));
        assertEquals("100%", StatNumbers.percentOf(0.1, 0.1));
        assertEquals("100.6%", StatNumbers.percentOf(0.1006, 0.1), "the 100.6% Matěj saw, no longer 101%");
        assertEquals("0.3", StatNumbers.percentOf(0.3, 0), "no base: the plain value");
    }

    @Test
    void anAttributeReadsInItsUnit() {
        assertEquals("15%", StatNumbers.value(GeneratedStats.CRIT_CHANCE, 0.15), "a percentage attribute");
        assertEquals("52.8", StatNumbers.value(Attributes.MAX_HEALTH, 52.8), "points");
    }

    @Test
    void bonusesAreSignedInTheirUnit() {
        assertEquals("+25%", StatNumbers.signed(Attributes.ATTACK_DAMAGE, Operation.ADD_MULTIPLIED_BASE, 0.25),
                "a multiplied operation is a share");
        assertEquals("+5%", StatNumbers.signed(Attributes.MAX_HEALTH, Operation.ADD_MULTIPLIED_TOTAL, 0.05));
        assertEquals("+25", StatNumbers.signed(Attributes.MAX_HEALTH, Operation.ADD_VALUE, 25),
                "adding to points is points");
        assertEquals("+2.5%", StatNumbers.signed(GeneratedStats.CRIT_CHANCE, Operation.ADD_VALUE, 0.025),
                "adding to a share is a share, with one decimal");
        assertEquals("+0.1%", StatNumbers.signed(GeneratedStats.CRIT_CHANCE, Operation.ADD_VALUE, 0.001),
                "what one point gives does not read 0%");
        assertEquals("+0.5%", StatNumbers.signed(Attributes.ATTACK_SPEED, Operation.ADD_MULTIPLIED_BASE, 0.005));
        assertEquals("-5%", StatNumbers.signed(Attributes.MOVEMENT_SPEED, Operation.ADD_MULTIPLIED_BASE, -0.05));
        assertEquals("-2", StatNumbers.signed(Attributes.ARMOR, Operation.ADD_VALUE, -2));
        assertEquals("-0.2", StatNumbers.signed(Attributes.ARMOR, Operation.ADD_VALUE, -0.2));
        assertEquals("+0", StatNumbers.signed(Attributes.ARMOR, Operation.ADD_VALUE, -0.01), "no -0");
    }
}
