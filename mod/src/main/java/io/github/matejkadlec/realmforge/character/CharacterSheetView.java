package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.stats.StatRule;
import io.github.matejkadlec.realmforge.stats.StatsRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.PercentageAttribute;

/**
 * What the character sheet shows (RLM-136; architecture 18: a thin screen draws a view model computed in common code):
 * the primary stats with what they give, the secondary stats, the spell schools' power and resistance, and the vanilla
 * attributes a player cares about. Built from a player's synced attributes and the synced registries, so the client
 * screen and the GameTests read the same rows. Every label is a translation; values are formatted here.
 *
 * @param left the primary stats and the combat attributes
 * @param right the secondary stats and the spell schools
 */
public record CharacterSheetView(List<Section> left, List<Section> right) {
    /** Text colours of the rows; the screen draws with them. */
    public static final int TITLE = 0xFFFFD24D;
    public static final int STAT = 0xFFFFFFFF;
    public static final int BONUS = 0xFF7FD97F;
    public static final int AHEAD = 0xFF8C8C8C;

    /** A titled group of rows. */
    public record Section(Component title, List<Row> rows) {
    }

    /**
     * One line of the sheet.
     *
     * @param id what the row shows, for tests: an attribute's description id, or {@code <stat description id>/<n>}
     *     for the n-th line under a primary stat
     * @param label the text on the left
     * @param value the formatted value on the right; empty for a line under a primary stat
     * @param indented whether the row is a line under a primary stat (what it gives)
     * @param color the text colour, opaque ARGB
     */
    public record Row(String id, Component label, String value, boolean indented, int color) {
    }

    /** The order of the primary stats on the sheet (the ticket's STR, DEX, VIT, INT); others follow in id order. */
    private static final List<Holder<Attribute>> PRIMARY_ORDER = List.of(GeneratedStats.STRENGTH,
            GeneratedStats.DEXTERITY, GeneratedStats.VITALITY, GeneratedStats.INTELLECT);

    /** The vanilla attributes a player cares about, with how each value reads. */
    private static final List<Holder<Attribute>> COMBAT = List.of(Attributes.MAX_HEALTH, Attributes.ARMOR,
            Attributes.ARMOR_TOUGHNESS, Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED, Attributes.MOVEMENT_SPEED);

    /**
     * Makes every vanilla attribute the sheet shows sync to the client, which draws the sheet from the player's own
     * attributes. Vanilla syncs all of {@link #COMBAT} but attack damage, which only the server computes; our stats are
     * syncable from their registration (architecture 8.1). Called once from {@link CharacterFeature}.
     */
    static void syncShownAttributes() {
        for (Holder<Attribute> attribute : COMBAT) {
            attribute.value().setSyncable(true);
        }
    }

    /** The sheet of a player, from its attributes (synced to its own client) and the synced registries. */
    public static CharacterSheetView of(Player player) {
        return build(player::getAttributeValue, player::getAttributeBaseValue, player.registryAccess());
    }

    /**
     * The sheet for any source of attribute values and base values: both must answer for every stat and
     * {@link #COMBAT}. Movement speed reads as a percent of its base: players walk at a base of 0.1, while the
     * attribute's own default is 0.7.
     */
    static CharacterSheetView build(ToDoubleFunction<Holder<Attribute>> value, ToDoubleFunction<Holder<Attribute>> base,
            HolderLookup.Provider registries) {
        List<StatRule> rules = new ArrayList<>();
        registries.lookupOrThrow(StatsRegistries.STAT_RULE).listElements().forEach(rule -> rules.add(rule.value()));
        List<SpellSchool> schools = new ArrayList<>();
        registries.lookupOrThrow(CombatRegistries.SPELL_SCHOOL).listElements()
                .sorted(Comparator.<Holder.Reference<SpellSchool>, Boolean>comparing(school -> school.value().magic())
                        .thenComparing(school -> school.key().identifier().toString()))
                .forEach(school -> schools.add(school.value()));

        // By key: the schools' holders come from the attribute registry, ours from GeneratedStats.
        Set<ResourceKey<Attribute>> schoolStats = new HashSet<>();
        schools.forEach(school -> {
            school.power().flatMap(Holder::unwrapKey).ifPresent(schoolStats::add);
            school.resistance().flatMap(Holder::unwrapKey).ifPresent(schoolStats::add);
        });

        List<Row> primary = new ArrayList<>();
        for (Holder<Attribute> stat : primaryStats()) {
            double points = value.applyAsDouble(stat);
            primary.add(statRow(stat, points, STAT));
            for (StatRule rule : rules) {
                if (rule.stat().is(key(stat))) {
                    primary.addAll(ruleRows(stat, rule, points));
                }
            }
        }
        List<Row> combat = new ArrayList<>();
        for (Holder<Attribute> attribute : COMBAT) {
            double current = value.applyAsDouble(attribute);
            String shown = attribute.is(Attributes.MOVEMENT_SPEED)
                    ? percentOf(current, base.applyAsDouble(attribute))
                    : points(current);
            combat.add(new Row(attribute.value().getDescriptionId(), name(attribute), shown, false, STAT));
        }
        List<Row> secondary = new ArrayList<>();
        for (Holder<Attribute> stat : GeneratedStats.ALL) {
            if (!GeneratedStats.PRIMARY.contains(stat) && !schoolStats.contains(key(stat))) {
                secondary.add(statRow(stat, value.applyAsDouble(stat), STAT));
            }
        }
        List<Row> schoolRows = new ArrayList<>();
        for (SpellSchool school : schools) {
            school.power().ifPresent(stat -> schoolRows.add(statRow(stat, value.applyAsDouble(stat), school.color())));
            school.resistance().ifPresent(stat -> schoolRows.add(statRow(stat, value.applyAsDouble(stat),
                    school.color())));
        }
        return new CharacterSheetView(
                List.of(section(GeneratedTexts.GUI_PRIMARY_STATS, primary), section(GeneratedTexts.GUI_COMBAT_STATS, combat)),
                List.of(section(GeneratedTexts.GUI_SECONDARY_STATS, secondary),
                        section(GeneratedTexts.GUI_SPELL_SCHOOLS, schoolRows)));
    }

    /** Every row of the sheet, left column first. */
    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (List<Section> column : List.of(left, right)) {
            column.forEach(section -> rows.addAll(section.rows()));
        }
        return rows;
    }

    /** Points: a whole number, or one decimal ({@code 25}, {@code 12.5}, {@code 0.3}). */
    public static String points(double value) {
        String oneDecimal = String.format(Locale.ROOT, "%.1f", value);
        return oneDecimal.endsWith(".0") ? oneDecimal.substring(0, oneDecimal.length() - 2) : oneDecimal;
    }

    /** A share in percent: {@code 0.05} reads {@code 5%}, {@code 0.125} {@code 12.5%}. */
    public static String share(double value) {
        return points(value * 100) + "%";
    }

    /** A value as a percentage of a base: movement speed 0.105 of the base 0.1 reads {@code 105%}. */
    public static String percentOf(double value, double base) {
        return base == 0 ? points(value) : share(value / base);
    }

    /**
     * The amount a stat rule's modifier gives, signed: a share for multiplied operations and for adding to a share
     * ({@code +25%}), points otherwise ({@code +10}).
     */
    public static String signed(Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount) {
        boolean percent = operation != AttributeModifier.Operation.ADD_VALUE
                || attribute.value() instanceof PercentageAttribute;
        String number = percent ? share(Math.abs(amount)) : points(Math.abs(amount));
        return (amount < 0 ? "-" : "+") + number;
    }

    private static List<Holder<Attribute>> primaryStats() {
        List<Holder<Attribute>> stats = new ArrayList<>(PRIMARY_ORDER);
        GeneratedStats.PRIMARY.stream().filter(stat -> !stats.contains(stat)).forEach(stats::add);
        return stats;
    }

    /** What the rule gives at {@code points}: each per-point total that is not 0, then every milestone. */
    private static List<Row> ruleRows(Holder<Attribute> stat, StatRule rule, double points) {
        String id = stat.value().getDescriptionId();
        List<Row> rows = new ArrayList<>();
        for (StatRule.PerPoint entry : rule.perPoint()) {
            double total = entry.totalAt(points);
            if (total != 0) {
                rows.add(new Row(id + "/" + rows.size(), bonus(entry.modifier(), total), "", true, BONUS));
            }
        }
        for (StatRule.Milestone milestone : rule.milestones()) {
            MutableComponent bonuses = Component.empty();
            for (StatRule.Modifier modifier : milestone.modifiers()) {
                if (!bonuses.getSiblings().isEmpty()) {
                    bonuses.append(", ");
                }
                bonuses.append(bonus(modifier, modifier.amount()));
            }
            Component label = Component.translatable(GeneratedTexts.GUI_STAT_MILESTONE, milestone.points(), bonuses);
            rows.add(new Row(id + "/" + rows.size(), label, "", true, points >= milestone.points() ? BONUS : AHEAD));
        }
        return rows;
    }

    private static Component bonus(StatRule.Modifier modifier, double amount) {
        return Component.translatable(GeneratedTexts.GUI_STAT_BONUS,
                signed(modifier.attribute(), modifier.operation(), amount), name(modifier.attribute()));
    }

    private static Row statRow(Holder<Attribute> stat, double value, int color) {
        String shown = stat.value() instanceof PercentageAttribute ? share(value) : points(value);
        return new Row(stat.value().getDescriptionId(), name(stat), shown, false, color);
    }

    private static ResourceKey<Attribute> key(Holder<Attribute> attribute) {
        return attribute.unwrapKey().orElseThrow(() -> new IllegalStateException("unregistered attribute"));
    }

    private static Component name(Holder<Attribute> attribute) {
        return Component.translatable(attribute.value().getDescriptionId());
    }

    private static Section section(String titleKey, List<Row> rows) {
        return new Section(Component.translatable(titleKey), List.copyOf(rows));
    }
}
