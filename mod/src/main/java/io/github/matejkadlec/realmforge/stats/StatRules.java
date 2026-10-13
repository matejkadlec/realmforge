package io.github.matejkadlec.realmforge.stats;

import io.github.matejkadlec.realmforge.Realmforge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Which attribute modifiers a stat value gives under a {@link StatRule} (architecture 8.3), without world access.
 * The pipeline computes the same in {@code StatRuleDef.bonuses_at}, and a GameTest compares the two.
 *
 * <p>Modifier ids are unique per rule and entry (architecture 8.2): {@code realmforge:stat/<rule>/<n>} for the n-th
 * per-point entry and {@code realmforge:stat/<rule>/at_<points>/<n>} for the n-th modifier of the milestone at
 * {@code points}. A rule outside our namespace keeps its namespace in the path ({@code stat/<namespace>/<path>/...}).
 */
public final class StatRules {
    /** The path prefix of every modifier stat rules write, in our namespace. */
    public static final String MODIFIER_PREFIX = "stat/";

    private StatRules() {
    }

    /**
     * The modifiers {@code value} points of the rule's stat give: each per-point entry whose total is not 0, then
     * every modifier of each milestone reached ({@code value >= points}), in the rule's order.
     */
    public static List<StatBonus> bonuses(Identifier rule, StatRule statRule, double value) {
        List<StatBonus> bonuses = new ArrayList<>();
        List<StatRule.PerPoint> perPoint = statRule.perPoint();
        for (int n = 0; n < perPoint.size(); n++) {
            StatRule.PerPoint entry = perPoint.get(n);
            double total = entry.totalAt(value);
            if (total != 0) {
                bonuses.add(bonus(perPointId(rule, n), entry.modifier(), total));
            }
        }
        for (StatRule.Milestone milestone : statRule.milestones()) {
            if (value >= milestone.points()) {
                List<StatRule.Modifier> modifiers = milestone.modifiers();
                for (int n = 0; n < modifiers.size(); n++) {
                    StatRule.Modifier modifier = modifiers.get(n);
                    bonuses.add(bonus(milestoneId(rule, milestone.points(), n), modifier, modifier.amount()));
                }
            }
        }
        return bonuses;
    }

    /** The id of the modifier the n-th per-point entry of {@code rule} gives. */
    public static Identifier perPointId(Identifier rule, int n) {
        return Realmforge.id(MODIFIER_PREFIX + rulePath(rule) + "/" + n);
    }

    /** The id of the n-th modifier of the milestone at {@code points} of {@code rule}. */
    public static Identifier milestoneId(Identifier rule, int points, int n) {
        return Realmforge.id(MODIFIER_PREFIX + rulePath(rule) + "/at_" + points + "/" + n);
    }

    /** Whether {@code id} names a modifier that stat rules write, so a recompute may replace or remove it. */
    public static boolean isStatModifier(Identifier id) {
        return id.getNamespace().equals(Realmforge.MOD_ID) && id.getPath().startsWith(MODIFIER_PREFIX);
    }

    private static String rulePath(Identifier rule) {
        return rule.getNamespace().equals(Realmforge.MOD_ID) ? rule.getPath()
                : rule.getNamespace() + "/" + rule.getPath();
    }

    private static StatBonus bonus(Identifier id, StatRule.Modifier modifier, double amount) {
        return new StatBonus(modifier.attribute(), new AttributeModifier(id, amount, modifier.operation()));
    }
}
