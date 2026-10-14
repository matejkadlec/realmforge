package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatRule;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import io.github.matejkadlec.realmforge.stats.StatsRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.PercentageAttribute;

/**
 * What the character sheet shows (RLM-136; architecture 18: a thin screen draws a view model computed in common code):
 * the player's name and level, then three tabs of two columns each (Matěj, 2026-10-13). Stats: the primary stats with
 * what they give, and the other attributes. Combat: offense and defense. Magic: spells and mana, and the spell
 * schools' power and resistance. Built from a player's synced attributes and the synced registries, so the client
 * screen and the GameTests read the same rows. Every label is a translation; values are formatted here.
 *
 * @param title the player's name and level
 * @param tabs the tabs in order
 */
public record CharacterSheetView(Component title, List<Tab> tabs) {
    /** Text colours the screen draws with: section titles, rows, reached bonuses and bonuses still ahead. */
    public static final int TITLE = 0xFFFFD24D;
    public static final int STAT = 0xFFFFFFFF;
    public static final int BONUS = 0xFF7FD97F;
    public static final int AHEAD = 0xFF8C8C8C;

    /** A tab: its name and the sections of its two columns. */
    public record Tab(Component name, List<Section> left, List<Section> right) {
    }

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

    /** An attribute on the sheet, under its own name or under one the sheet gives it ({@code label}, a text key). */
    private record Entry(Holder<Attribute> attribute, @Nullable String label) {
    }

    /** The order of the primary stats on the sheet (STR, DEX, VIT, INT); others follow in id order. */
    private static final List<Holder<Attribute>> PRIMARY_ORDER = List.of(GeneratedStats.STRENGTH,
            GeneratedStats.DEXTERITY, GeneratedStats.VITALITY, GeneratedStats.INTELLECT);

    private static final List<Entry> OTHER = List.of(entry(Attributes.LUCK),
            entry(Attributes.BLOCK_BREAK_SPEED, GeneratedTexts.GUI_MINING_SPEED),
            entry(Attributes.BLOCK_INTERACTION_RANGE, GeneratedTexts.GUI_BLOCK_REACH),
            entry(Attributes.ENTITY_INTERACTION_RANGE, GeneratedTexts.GUI_ENTITY_REACH),
            entry(Attributes.MOVEMENT_SPEED, GeneratedTexts.GUI_MOVEMENT_SPEED),
            entry(NeoForgeMod.SWIM_SPEED, GeneratedTexts.GUI_SWIMMING_SPEED),
            entry(GeneratedStats.XP_MULTIPLIER));
    private static final List<Entry> OFFENSE = List.of(entry(Attributes.ATTACK_DAMAGE), entry(Attributes.ATTACK_SPEED),
            entry(GeneratedStats.CRIT_CHANCE), entry(GeneratedStats.CRIT_DAMAGE), entry(GeneratedStats.ARMOR_PIERCE),
            entry(GeneratedStats.ARMOR_PIERCE_PERCENT), entry(GeneratedStats.PROTECTION_PIERCE),
            entry(GeneratedStats.PROTECTION_PIERCE_PERCENT), entry(GeneratedStats.LIFESTEAL),
            entry(GeneratedStats.OVERHEAL));
    private static final List<Entry> DEFENSE = List.of(entry(Attributes.MAX_HEALTH),
            entry(GeneratedStats.HEALTH_REGENERATION), entry(Attributes.ARMOR), entry(Attributes.ARMOR_TOUGHNESS),
            entry(GeneratedStats.MAGIC_SHIELD), entry(GeneratedStats.DODGE_CHANCE));
    private static final List<Entry> SPELLS = List.of(entry(GeneratedStats.SPELL_POWER),
            entry(GeneratedStats.SPELL_RESISTANCE), entry(GeneratedStats.COOLDOWN_REDUCTION),
            entry(GeneratedStats.CAST_TIME_REDUCTION));
    private static final List<Entry> MANA = List.of(entry(GeneratedStats.MAX_MANA),
            entry(GeneratedStats.MANA_REGENERATION));

    /** Attributes that read as a percentage of the player's base: players walk at 0.1, the default is 0.7. */
    private static final List<Holder<Attribute>> PERCENT_OF_BASE = List.of(Attributes.MOVEMENT_SPEED,
            Attributes.BLOCK_BREAK_SPEED);

    /**
     * Makes every attribute the sheet lists sync to the client, which draws the sheet from the player's own attributes.
     * Vanilla syncs most of them but not attack damage, which only the server computes; our stats are syncable from
     * their registration (architecture 8.1). Called once from {@link CharacterFeature} at common setup, when every
     * attribute the sheet lists is registered (NeoForge's swim speed and ours are not at mod construction).
     */
    static void syncShownAttributes() {
        listed().forEach(entry -> entry.attribute().value().setSyncable(true));
    }

    /** The sheet of a player, from its attributes and level (synced to its own client) and the synced registries. */
    public static CharacterSheetView of(Player player) {
        int level = player.getExistingData(StatsAttachments.LEVEL).map(EntityLevel::value).orElse(EntityLevel.MIN);
        Component title = Component.translatable(GeneratedTexts.GUI_CHARACTER_TITLE, player.getName(), level);
        return build(title, player::getAttributeValue, player::getAttributeBaseValue, player.registryAccess());
    }

    /**
     * The sheet for any source of attribute values and base values: both must answer for every stat and every
     * attribute the sheet lists.
     */
    static CharacterSheetView build(Component title, ToDoubleFunction<Holder<Attribute>> value,
            ToDoubleFunction<Holder<Attribute>> base, HolderLookup.Provider registries) {
        List<StatRule> rules = new ArrayList<>();
        registries.lookupOrThrow(StatsRegistries.STAT_RULE).listElements().forEach(rule -> rules.add(rule.value()));
        List<SpellSchool> schools = new ArrayList<>();
        registries.lookupOrThrow(CombatRegistries.SPELL_SCHOOL).listElements()
                .sorted(Comparator.<Holder.Reference<SpellSchool>, Boolean>comparing(school -> school.value().magic())
                        .thenComparing(school -> school.key().identifier().toString()))
                .forEach(school -> schools.add(school.value()));

        // By key: the schools' holders come from the attribute registry, ours from GeneratedStats.
        Set<ResourceKey<Attribute>> placed = new HashSet<>();
        schools.forEach(school -> {
            school.power().flatMap(Holder::unwrapKey).ifPresent(placed::add);
            school.resistance().flatMap(Holder::unwrapKey).ifPresent(placed::add);
        });
        listed().forEach(entry -> placed.add(key(entry.attribute())));

        List<Row> primary = new ArrayList<>();
        for (Holder<Attribute> stat : primaryStats()) {
            placed.add(key(stat));
            double points = value.applyAsDouble(stat);
            primary.add(statRow(stat, points, STAT));
            for (StatRule rule : rules) {
                if (rule.stat().is(key(stat))) {
                    primary.addAll(ruleRows(stat, rule, points));
                }
            }
        }
        List<Row> other = rows(OTHER, value, base);
        // A stat of ours that no section names still shows, under Other, so new content is never missing.
        for (Holder<Attribute> stat : GeneratedStats.ALL) {
            if (!placed.contains(key(stat))) {
                other.add(statRow(stat, value.applyAsDouble(stat), STAT));
            }
        }
        List<Row> schoolRows = new ArrayList<>();
        for (SpellSchool school : schools) {
            school.power().ifPresent(stat -> schoolRows.add(statRow(stat, value.applyAsDouble(stat), school.color())));
            school.resistance().ifPresent(stat -> schoolRows.add(statRow(stat, value.applyAsDouble(stat),
                    school.color())));
        }
        return new CharacterSheetView(title, List.of(
                tab(GeneratedTexts.GUI_TAB_STATS, List.of(section(GeneratedTexts.GUI_PRIMARY_STATS, primary)),
                        List.of(section(GeneratedTexts.GUI_OTHER_STATS, other))),
                tab(GeneratedTexts.GUI_TAB_COMBAT,
                        List.of(section(GeneratedTexts.GUI_OFFENSE, rows(OFFENSE, value, base))),
                        List.of(section(GeneratedTexts.GUI_DEFENSE, rows(DEFENSE, value, base)))),
                tab(GeneratedTexts.GUI_TAB_MAGIC,
                        List.of(section(GeneratedTexts.GUI_SPELLS, rows(SPELLS, value, base)),
                                section(GeneratedTexts.GUI_MANA, rows(MANA, value, base))),
                        List.of(section(GeneratedTexts.GUI_SPELL_SCHOOLS, schoolRows)))));
    }

    /** Every row of the sheet: tab by tab, left column first. */
    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (Tab tab : tabs) {
            for (List<Section> column : List.of(tab.left(), tab.right())) {
                column.forEach(section -> rows.addAll(section.rows()));
            }
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

    /** Every attribute the sections list by name (the primary stats and the schools' stats come from data). */
    private static Stream<Entry> listed() {
        return Stream.of(OTHER, OFFENSE, DEFENSE, SPELLS, MANA).flatMap(List::stream);
    }

    private static List<Holder<Attribute>> primaryStats() {
        List<Holder<Attribute>> stats = new ArrayList<>(PRIMARY_ORDER);
        GeneratedStats.PRIMARY.stream().filter(stat -> !stats.contains(stat)).forEach(stats::add);
        return stats;
    }

    private static List<Row> rows(List<Entry> entries, ToDoubleFunction<Holder<Attribute>> value,
            ToDoubleFunction<Holder<Attribute>> base) {
        List<Row> rows = new ArrayList<>();
        for (Entry entry : entries) {
            Holder<Attribute> attribute = entry.attribute();
            double current = value.applyAsDouble(attribute);
            String shown = PERCENT_OF_BASE.stream().anyMatch(percent -> attribute.is(percent))
                    ? percentOf(current, base.applyAsDouble(attribute))
                    : formatted(attribute, current);
            rows.add(new Row(attribute.value().getDescriptionId(), name(attribute), shown, false, STAT));
        }
        return rows;
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
        return new Row(stat.value().getDescriptionId(), name(stat), formatted(stat, value), false, color);
    }

    private static String formatted(Holder<Attribute> attribute, double value) {
        return attribute.value() instanceof PercentageAttribute ? share(value) : points(value);
    }

    private static ResourceKey<Attribute> key(Holder<Attribute> attribute) {
        return attribute.unwrapKey().orElseThrow(() -> new IllegalStateException("unregistered attribute"));
    }

    /**
     * An attribute's name on the sheet, on its row and in the bonuses of the primary stats: the name a section gives
     * it (Movement Speed for vanilla's Speed), or its own.
     */
    private static Component name(Holder<Attribute> attribute) {
        ResourceKey<Attribute> key = key(attribute);
        return listed().filter(entry -> entry.label() != null && key(entry.attribute()).equals(key)).findFirst()
                .<Component>map(entry -> Component.translatable(entry.label()))
                .orElseGet(() -> Component.translatable(attribute.value().getDescriptionId()));
    }

    private static Entry entry(Holder<Attribute> attribute) {
        return new Entry(attribute, null);
    }

    private static Entry entry(Holder<Attribute> attribute, String label) {
        return new Entry(attribute, label);
    }

    private static Section section(String titleKey, List<Row> rows) {
        return new Section(Component.translatable(titleKey), List.copyOf(rows));
    }

    private static Tab tab(String nameKey, List<Section> left, List<Section> right) {
        return new Tab(Component.translatable(nameKey), left, right);
    }
}
