package io.github.matejkadlec.realmforge.character;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.character.CharacterSheetView.Identity;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Part;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Specialization;
import io.github.matejkadlec.realmforge.stats.ClassRole;
import io.github.matejkadlec.realmforge.stats.StatRule;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

/** RLM-136, RLM-196 and RLM-509: what the character sheet's rows, tooltips and identity line say. */
class CharacterSheetViewTest {
    private static final Optional<Part> HUMAN = Optional.of(new Part(Component.literal("Human"), List.of()));
    private static final Optional<Part> MAGE = Optional.of(new Part(Component.literal("Mage"), List.of()));

    /** RLM-509: the specialization with the most points names the line. */
    @Test
    void theLineNamesTheSpecializationWithTheMostPoints() {
        assertEquals("Human Frost Mage", line(List.of(spec("fire", "Fire", 3), spec("frost", "Frost", 10),
                spec("arcane", "Arcane", 9))));
    }

    @Test
    void aTieGoesAlphabetically() {
        assertEquals("Human Fire Mage", line(List.of(spec("frost", "Frost", 10), spec("fire", "Fire", 10))),
                "10 Fire and 10 Frost points (Matěj, RLM-509)");
    }

    @Test
    void noPointsNameRaceAndClassOnly() {
        assertEquals("Human Mage", line(List.of()), "before specialization points exist");
        assertEquals("Human Mage", line(List.of(spec("fire", "Fire", 0), spec("frost", "Frost", 0))),
                "no points in any");
    }

    @Test
    void aMissingPartIsLeftOut() {
        assertEquals("Human", CharacterSheetView.identity(HUMAN, List.of(spec("fire", "Fire", 5)), Optional.empty())
                .line().getString(), "no class: no specialization either");
        assertEquals("", CharacterSheetView.identity(Optional.empty(), List.of(), Optional.empty()).line()
                .getString(), "no character");
    }

    private static String line(List<Specialization> specializations) {
        Identity identity = CharacterSheetView.identity(HUMAN, specializations, MAGE);
        return identity.line().getString();
    }

    private static Specialization spec(String id, String name, int points) {
        return new Specialization(id, Component.literal(name), points);
    }

    /**
     * RLM-196: the {@code +}'s tooltip lists what the next point gives: each per-point bonus that still grows, then the
     * bonuses of a milestone it reaches, with one decimal (RLM-530).
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

    /**
     * RLM-509: a primary stat's tooltip lists the per-point totals in yellow, then its milestones, gold once reached
     * and gray while ahead, with half percents kept (RLM-530).
     */
    @Test
    void aPrimaryStatsTooltipListsWhatItsPointsGive() {
        StatRule rule = new StatRule(Attributes.LUCK, List.of(
                new StatRule.PerPoint(new StatRule.Modifier(Attributes.ATTACK_SPEED, Operation.ADD_MULTIPLIED_BASE,
                        0.005), Optional.empty())),
                List.of(new StatRule.Milestone(10, List.of(new StatRule.Modifier(Attributes.ARMOR,
                        Operation.ADD_VALUE, 2.0))), new StatRule.Milestone(25, List.of(new StatRule.Modifier(
                        Attributes.MAX_HEALTH, Operation.ADD_VALUE, 5.0)))));
        List<Component> lines = CharacterSheetView.ruleLines(rule, 13, Optional.empty());
        assertEquals(3, lines.size(), "the per-point total and both milestones");
        assertEquals("+6.5%", amount(lines.getFirst()), "13 points of 0.5%: no longer rounded to 7%");
        assertEquals(List.of(CharacterSheetView.BONUS, CharacterSheetView.MILESTONE, CharacterSheetView.AHEAD),
                lines.stream().map(CharacterSheetViewTest::color).toList(), "reached at 10, ahead at 25");
        assertEquals(List.of(), CharacterSheetView.ruleLines(new StatRule(Attributes.LUCK, List.of(), List.of()), 5,
                Optional.empty()), "a rule that gives nothing lists nothing");
    }

    /** The amount of each "+1% Attack Damage" line: its translation's first argument. */
    private static List<String> amounts(List<Component> gains) {
        return gains.stream().map(CharacterSheetViewTest::amount).toList();
    }

    private static String amount(Component line) {
        return (String) ((TranslatableContents) line.getContents()).getArgs()[0];
    }

    private static int color(Component line) {
        TextColor color = line.getStyle().getColor();
        return color == null ? 0 : 0xFF000000 | color.getValue();
    }
}
