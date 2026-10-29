package io.github.matejkadlec.realmforge.character;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.stats.ClassRole;
import io.github.matejkadlec.realmforge.stats.StatRule;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

/**
 * RLM-136: how the character sheet's values read. Display rounding (Matěj, 2026-10-14): a percentage to a whole
 * number, any other number to one decimal, both half up.
 */
class CharacterSheetViewTest {
    @Test
    void pointsAreWholeOrHaveOneDecimal() {
        assertEquals("25", CharacterSheetView.points(25));
        assertEquals("12.5", CharacterSheetView.points(12.5));
        assertEquals("1.6", CharacterSheetView.points(1.6000000001));
        assertEquals("0", CharacterSheetView.points(0));
        assertEquals("20", CharacterSheetView.points(19.96), "rounds to one decimal first");
        assertEquals("6.4", CharacterSheetView.points(6.44));
        assertEquals("6.5", CharacterSheetView.points(6.45), "half up, from the decimal the double prints as");
        assertEquals("0.2", CharacterSheetView.points(0.15), "0.15 is a little less as a double, but reads 0.2");
        assertEquals("-2.5", CharacterSheetView.points(-2.45), "half away from zero below it");
        assertEquals("0", CharacterSheetView.points(-0.01), "no negative zero");
    }

    @Test
    void sharesReadInPercent() {
        assertEquals("5%", CharacterSheetView.share(0.05));
        assertEquals("13%", CharacterSheetView.share(0.125), "a whole number, half up");
        assertEquals("0%", CharacterSheetView.share(0));
        assertEquals("150%", CharacterSheetView.share(1.5));
        assertEquals("101%", CharacterSheetView.share(1.005), "100.5 rounds up");
        assertEquals("100%", CharacterSheetView.share(1.004), "100.4 rounds down");
    }

    @Test
    void movementSpeedReadsAsAPercentOfItsBase() {
        assertEquals("105%", CharacterSheetView.percentOf(0.105, 0.1));
        assertEquals("100%", CharacterSheetView.percentOf(0.1, 0.1));
        assertEquals("101%", CharacterSheetView.percentOf(0.1006, 0.1), "the 100.6% Matěj saw reads 101%");
        assertEquals("0.3", CharacterSheetView.percentOf(0.3, 0), "no base: the plain value");
    }

    @Test
    void bonusesAreSignedInTheirUnit() {
        assertEquals("+25%", CharacterSheetView.signed(Attributes.ATTACK_DAMAGE, Operation.ADD_MULTIPLIED_BASE, 0.25),
                "a multiplied operation is a share");
        assertEquals("+5%", CharacterSheetView.signed(Attributes.MAX_HEALTH, Operation.ADD_MULTIPLIED_TOTAL, 0.05));
        assertEquals("+25", CharacterSheetView.signed(Attributes.MAX_HEALTH, Operation.ADD_VALUE, 25),
                "adding to points is points");
        assertEquals("+3%", CharacterSheetView.signed(GeneratedStats.CRIT_CHANCE, Operation.ADD_VALUE, 0.025),
                "adding to a share is a share, rounded to a whole percent");
        assertEquals("-5%", CharacterSheetView.signed(Attributes.MOVEMENT_SPEED, Operation.ADD_MULTIPLIED_BASE, -0.05));
        assertEquals("-2", CharacterSheetView.signed(Attributes.ARMOR, Operation.ADD_VALUE, -2));
    }

    /** RLM-196: what one point gives reads with one decimal, so Dexterity's 0.1% crit chance does not read 0%. */
    @Test
    void aPointsGainReadsWithOneDecimal() {
        assertEquals("+0.1%", CharacterSheetView.gain(GeneratedStats.CRIT_CHANCE, Operation.ADD_VALUE, 0.001));
        assertEquals("+0.5%", CharacterSheetView.gain(Attributes.ATTACK_SPEED, Operation.ADD_MULTIPLIED_BASE, 0.005));
        assertEquals("+1%", CharacterSheetView.gain(Attributes.ATTACK_DAMAGE, Operation.ADD_MULTIPLIED_BASE, 0.01));
        assertEquals("+1", CharacterSheetView.gain(Attributes.MAX_HEALTH, Operation.ADD_VALUE, 1));
        assertEquals("-0.2", CharacterSheetView.gain(Attributes.ARMOR, Operation.ADD_VALUE, -0.2));
    }

    /**
     * RLM-196: the {@code +}'s tooltip lists what the next point gives: each per-point bonus that still grows, then the
     * bonuses of a milestone it reaches.
     */
    @Test
    void gainsAreWhatTheNextPointGives() {
        StatRule rule = new StatRule(Attributes.LUCK, List.of(
                new StatRule.PerPoint(new StatRule.Modifier(Attributes.ATTACK_DAMAGE, Operation.ADD_MULTIPLIED_BASE,
                        0.01), Optional.empty()),
                new StatRule.PerPoint(new StatRule.Modifier(Attributes.MOVEMENT_SPEED, Operation.ADD_MULTIPLIED_BASE,
                        0.002), Optional.of(0.2))),
                List.of(new StatRule.Milestone(25, List.of(
                        new StatRule.Modifier(Attributes.ARMOR, Operation.ADD_VALUE, 2.0)))));
        assertEquals(List.of("+1%", "+0.2%"), amounts(CharacterSheetView.gains(List.of(rule), 3, Optional.empty())));
        assertEquals(List.of("+1%", "+0.2%", "+2"),
                amounts(CharacterSheetView.gains(List.of(rule), 24, Optional.empty())),
                "the 25th point reaches the milestone");
        assertEquals(List.of("+1%"), amounts(CharacterSheetView.gains(List.of(rule), 100, Optional.empty())),
                "movement speed is at its cap");
        assertEquals(List.of(), amounts(CharacterSheetView.gains(List.of(), 3, Optional.empty())),
                "a stat without a rule gives nothing");
    }

    /** RLM-170: the {@code +} lists the bonuses of the player's role and the role-free ones, nothing else. */
    @Test
    void gainsAreThoseOfThePlayersRole() {
        StatRule rule = new StatRule(Attributes.LUCK, List.of(
                new StatRule.PerPoint(new StatRule.Modifier(Attributes.ATTACK_SPEED, Operation.ADD_MULTIPLIED_BASE,
                        0.015, List.of(ClassRole.MELEE)), Optional.empty()),
                new StatRule.PerPoint(new StatRule.Modifier(Attributes.ARMOR, Operation.ADD_VALUE, 1.0),
                        Optional.empty())),
                List.of(new StatRule.Milestone(10, List.of(
                        new StatRule.Modifier(Attributes.MAX_HEALTH, Operation.ADD_VALUE, 5.0,
                                List.of(ClassRole.CASTER))))));
        assertEquals(List.of("+1.5%", "+1"), amounts(CharacterSheetView.gains(List.of(rule), 9,
                Optional.of(ClassRole.MELEE))), "melee: its attack speed, and the armor everyone gets");
        assertEquals(List.of("+1", "+5"), amounts(CharacterSheetView.gains(List.of(rule), 9,
                Optional.of(ClassRole.CASTER))), "caster: the armor, and its milestone's health");
        assertEquals(List.of("+1"), amounts(CharacterSheetView.gains(List.of(rule), 9, Optional.empty())),
                "no character: the role-free armor only");
    }

    /** The amount of each "+1% Attack Damage" line: its translation's first argument. */
    private static List<String> amounts(List<Component> gains) {
        return gains.stream().map(gain -> (String) ((TranslatableContents) gain.getContents()).getArgs()[0]).toList();
    }
}
