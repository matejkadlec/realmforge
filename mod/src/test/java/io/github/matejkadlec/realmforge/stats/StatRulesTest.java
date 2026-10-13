package io.github.matejkadlec.realmforge.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.Realmforge;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

class StatRulesTest {
    private static final Identifier STRENGTH = Realmforge.id("strength");

    private static final StatRule RULE = new StatRule(Attributes.LUCK,
            List.of(new StatRule.PerPoint(new StatRule.Modifier(Attributes.ATTACK_DAMAGE, Operation.ADD_MULTIPLIED_BASE,
                            0.01), Optional.empty()),
                    new StatRule.PerPoint(new StatRule.Modifier(Attributes.MOVEMENT_SPEED,
                            Operation.ADD_MULTIPLIED_BASE, 0.002), Optional.of(0.2))),
            List.of(new StatRule.Milestone(25, List.of(
                            new StatRule.Modifier(Attributes.ARMOR, Operation.ADD_VALUE, 2.0),
                            new StatRule.Modifier(Attributes.MAX_HEALTH, Operation.ADD_MULTIPLIED_TOTAL, 0.05))),
                    new StatRule.Milestone(50, List.of(
                            new StatRule.Modifier(Attributes.ARMOR, Operation.ADD_VALUE, 3.0)))));

    @Test
    void everyPointGivesItsAmount() {
        assertEquals(List.of(
                        bonus("stat/strength/0", Attributes.ATTACK_DAMAGE, 0.2, Operation.ADD_MULTIPLIED_BASE),
                        bonus("stat/strength/1", Attributes.MOVEMENT_SPEED, 0.04, Operation.ADD_MULTIPLIED_BASE)),
                StatRules.bonuses(STRENGTH, RULE, 20));
    }

    @Test
    void noPointsGiveNothing() {
        assertEquals(List.of(), StatRules.bonuses(STRENGTH, RULE, 0));
    }

    @Test
    void capLimitsThePerPointTotal() {
        assertEquals(0.1, perPoint(1).totalAt(50), 1e-12);
        assertEquals(0.2, perPoint(1).totalAt(100), 1e-12);
        assertEquals(0.2, perPoint(1).totalAt(1_000), "the cap holds far past it");
        assertEquals(10.0, perPoint(0).totalAt(1_000), 1e-9, "no cap, no limit");
    }

    @Test
    void milestonesApplyFromTheirPointsOn() {
        List<StatBonus> justBelow = StatRules.bonuses(STRENGTH, RULE, 24.99);
        assertEquals(2, justBelow.size(), "only the per-point bonuses");

        List<StatBonus> reached = StatRules.bonuses(STRENGTH, RULE, 25);
        assertEquals(List.of(
                        bonus("stat/strength/0", Attributes.ATTACK_DAMAGE, 0.25, Operation.ADD_MULTIPLIED_BASE),
                        bonus("stat/strength/1", Attributes.MOVEMENT_SPEED, 0.05, Operation.ADD_MULTIPLIED_BASE),
                        bonus("stat/strength/at_25/0", Attributes.ARMOR, 2.0, Operation.ADD_VALUE),
                        bonus("stat/strength/at_25/1", Attributes.MAX_HEALTH, 0.05, Operation.ADD_MULTIPLIED_TOTAL)),
                reached);

        List<StatBonus> both = StatRules.bonuses(STRENGTH, RULE, 60);
        assertEquals(bonus("stat/strength/at_50/0", Attributes.ARMOR, 3.0, Operation.ADD_VALUE), both.getLast(),
                "both milestones, each with its own id");
        assertEquals(5, both.size());
    }

    @Test
    void anotherNamespaceKeepsItsNamespaceInTheId() {
        Identifier rule = Identifier.fromNamespaceAndPath("othermod", "strength");
        assertEquals(Realmforge.id("stat/othermod/strength/0"), StatRules.perPointId(rule, 0));
        assertEquals(Realmforge.id("stat/othermod/strength/at_25/1"), StatRules.milestoneId(rule, 25, 1));
    }

    @Test
    void onlyOurStatPrefixIsOwned() {
        assertTrue(StatRules.isStatModifier(Realmforge.id("stat/strength/0")));
        assertFalse(StatRules.isStatModifier(Realmforge.id("race/human/0")), "another source of ours");
        assertFalse(StatRules.isStatModifier(Identifier.fromNamespaceAndPath("othermod", "stat/strength/0")));
    }

    @Test
    void codecReadsTheGeneratedFormat() {
        StatRule rule = decode("""
                {"stat": "minecraft:luck",
                 "per_point": [{"attribute": "minecraft:movement_speed", "operation": "add_multiplied_base",
                                "amount": 0.002, "max": 0.2}],
                 "milestones": [{"points": 25, "modifiers": [
                     {"attribute": "minecraft:armor", "operation": "add_value", "amount": 2}]}]}
                """).getOrThrow();
        assertTrue(rule.stat().is(Attributes.LUCK));
        assertEquals(1, rule.perPoint().size());
        assertTrue(rule.perPoint().getFirst().modifier().attribute().is(Attributes.MOVEMENT_SPEED));
        assertEquals(Operation.ADD_MULTIPLIED_BASE, rule.perPoint().getFirst().modifier().operation());
        assertEquals(Optional.of(0.2), rule.perPoint().getFirst().max());
        assertEquals(25, rule.milestones().getFirst().points());
        assertEquals(2.0, rule.milestones().getFirst().modifiers().getFirst().amount());
    }

    @Test
    void codecReadsARuleWithOnlyMilestones() {
        StatRule rule = decode("""
                {"stat": "minecraft:luck", "milestones": [{"points": 5, "modifiers": [
                    {"attribute": "minecraft:armor", "operation": "add_value", "amount": 1}]}]}
                """).getOrThrow();
        assertEquals(List.of(), rule.perPoint());
    }

    @Test
    void codecRejectsBrokenRules() {
        assertTrue(decode("{\"stat\": \"minecraft:luck\"}").isError(), "a rule that gives nothing");
        assertTrue(decode("""
                {"stat": "minecraft:luck", "milestones": [
                    {"points": 10, "modifiers": [{"attribute": "minecraft:armor", "operation": "add_value", "amount": 1}]},
                    {"points": 5, "modifiers": [{"attribute": "minecraft:armor", "operation": "add_value", "amount": 1}]}]}
                """).isError(), "milestones out of order");
        assertTrue(decode("""
                {"stat": "minecraft:luck", "per_point": [
                    {"attribute": "minecraft:armor", "operation": "add_value", "amount": -1, "max": 5}]}
                """).isError(), "a cap on a negative amount");
        assertTrue(decode("""
                {"stat": "minecraft:luck", "per_point": [
                    {"attribute": "minecraft:no_such_attribute", "operation": "add_value", "amount": 1}]}
                """).isError(), "an unknown attribute");
        assertTrue(decode("""
                {"stat": "minecraft:luck", "milestones": [{"points": 5, "modifiers": []}]}
                """).isError(), "a milestone that gives nothing");
    }

    private static StatRule.PerPoint perPoint(int index) {
        return RULE.perPoint().get(index);
    }

    private static StatBonus bonus(String path, Holder<Attribute> attribute, double amount, Operation operation) {
        return new StatBonus(attribute, new AttributeModifier(Realmforge.id(path), amount, operation));
    }

    private static DataResult<StatRule> decode(String json) {
        return StatRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }
}
