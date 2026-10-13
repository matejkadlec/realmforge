package io.github.matejkadlec.realmforge.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    private static final Operation ADD = Operation.ADD_VALUE;
    private static final Operation BASE = Operation.ADD_MULTIPLIED_BASE;
    private static final Operation TOTAL = Operation.ADD_MULTIPLIED_TOTAL;
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
                        bonus("stat/realmforge/0/strength", Attributes.ATTACK_DAMAGE, 0.2, BASE),
                        bonus("stat/realmforge/1/strength", Attributes.MOVEMENT_SPEED, 0.04, BASE)),
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
                        bonus("stat/realmforge/0/strength", Attributes.ATTACK_DAMAGE, 0.25, BASE),
                        bonus("stat/realmforge/1/strength", Attributes.MOVEMENT_SPEED, 0.05, BASE),
                        bonus("stat/realmforge/at_25_0/strength", Attributes.ARMOR, 2.0, ADD),
                        bonus("stat/realmforge/at_25_1/strength", Attributes.MAX_HEALTH, 0.05, TOTAL)),
                reached);

        List<StatBonus> both = StatRules.bonuses(STRENGTH, RULE, 60);
        assertEquals(bonus("stat/realmforge/at_50_0/strength", Attributes.ARMOR, 3.0, ADD), both.getLast(),
                "both milestones, each with its own id");
        assertEquals(5, both.size());
    }

    @Test
    void idsNameTheRuleNamespaceTheEntryAndThenThePath() {
        Identifier rule = Identifier.fromNamespaceAndPath("othermod", "strength");
        assertEquals(Realmforge.id("stat/othermod/0/strength"), StatRules.perPointId(rule, 0));
        assertEquals(Realmforge.id("stat/othermod/at_25_1/strength"), StatRules.milestoneId(rule, 25, 1));
    }

    @Test
    void rulesWhosePathsLookAlikeGetDifferentIds() {
        Identifier foreign = Identifier.fromNamespaceAndPath("foo", "bar");
        Identifier ours = Realmforge.id("foo/bar");
        assertNotEquals(StatRules.perPointId(foreign, 0), StatRules.perPointId(ours, 0));
        assertNotEquals(StatRules.milestoneId(foreign, 25, 0), StatRules.milestoneId(ours, 25, 0));
        // A path may end like a milestone entry; the entry comes before the path, so it cannot collide.
        Identifier x = Realmforge.id("x");
        assertNotEquals(StatRules.perPointId(Realmforge.id("x/at_25"), 0), StatRules.milestoneId(x, 25, 0));
        assertNotEquals(StatRules.perPointId(Realmforge.id("x/at_25_0"), 0), StatRules.milestoneId(x, 25, 0));
    }

    @Test
    void onlyOurStatPrefixIsOwned() {
        assertTrue(StatRules.isStatModifier(Realmforge.id("stat/realmforge/0/strength")));
        assertFalse(StatRules.isStatModifier(Realmforge.id("race/human/0")), "another source of ours");
        assertFalse(StatRules.isStatModifier(Identifier.fromNamespaceAndPath("othermod", "stat/othermod/0/x")));
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
                    {"points": 10, "modifiers": [
                        {"attribute": "minecraft:armor", "operation": "add_value", "amount": 1}]},
                    {"points": 5, "modifiers": [
                        {"attribute": "minecraft:armor", "operation": "add_value", "amount": 1}]}]}
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

    private static StatBonus bonus(String path, Holder<Attribute> attribute, double amount, Operation op) {
        return new StatBonus(attribute, new AttributeModifier(Realmforge.id(path), amount, op));
    }

    private static DataResult<StatRule> decode(String json) {
        return StatRule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }
}
