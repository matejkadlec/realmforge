package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatRule;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import io.github.matejkadlec.realmforge.stats.StatsRegistries;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
 * the player's name and level, then four tabs of two columns each (Matěj, 2026-10-13 and 2026-10-14). Stats: the
 * primary stats with what they give, and the other attributes. Combat: offense and defense. Magic: spells with mana,
 * and the spell schools' power and resistance. Origin: the race (its description and what it gives) and the class (its
 * description, then a placeholder for the subclass). Built from a player's synced attributes, character and the
 * synced registries, so the client screen and the GameTests read the same rows. Every label is a translation; values
 * are formatted here, rounded for display only: a percentage to a whole number, any other number to one decimal.
 *
 * @param title the player's name and level
 * @param tabs the tabs in order
 */
public record CharacterSheetView(Component title, List<Tab> tabs) {
    /** The id prefix of the Origin tab's rows, for tests: {@code origin/race/description}, ... */
    public static final String ORIGIN = "origin/";

    /** Text colours the screen draws with: section titles, rows, reached bonuses and bonuses still ahead. */
    public static final int TITLE = 0xFFFFD24D;
    public static final int STAT = 0xFFFFFFFF;
    public static final int BONUS = 0xFF7FD97F;
    public static final int AHEAD = 0xFF8C8C8C;
    /** Descriptions on the Origin tab. */
    public static final int TEXT = 0xFFC8C8C8;

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
            entry(GeneratedStats.MAGIC_SHIELD), entry(GeneratedStats.DODGE_CHANCE),
            entry(GeneratedStats.SPELL_RESISTANCE));
    /** Mana first, then what shapes a spell (Matěj, 2026-10-14). */
    private static final List<Entry> SPELLS = List.of(entry(GeneratedStats.MAX_MANA),
            entry(GeneratedStats.MANA_REGENERATION), entry(GeneratedStats.SPELL_POWER),
            entry(GeneratedStats.COOLDOWN_REDUCTION), entry(GeneratedStats.CAST_TIME_REDUCTION));

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

    /** The sheet of a player, from its attributes, level and character (synced to its own client) and the registries. */
    public static CharacterSheetView of(Player player) {
        int level = player.getExistingData(StatsAttachments.LEVEL).map(EntityLevel::value).orElse(EntityLevel.MIN);
        Component title = Component.translatable(GeneratedTexts.GUI_CHARACTER_TITLE, player.getName(), level);
        return build(title, player::getAttributeValue, player::getAttributeBaseValue, player.registryAccess(),
                Characters.data(player));
    }

    /**
     * The sheet for any source of attribute values and base values (both must answer for every stat and every
     * attribute the sheet lists) and a character.
     */
    static CharacterSheetView build(Component title, ToDoubleFunction<Holder<Attribute>> value,
            ToDoubleFunction<Holder<Attribute>> base, HolderLookup.Provider registries, CharacterData character) {
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
                        List.of(section(GeneratedTexts.GUI_SPELLS, rows(SPELLS, value, base))),
                        List.of(section(GeneratedTexts.GUI_SPELL_SCHOOLS, schoolRows))),
                origin(character, registries)));
    }

    /**
     * The Origin tab (Matěj, 2026-10-14): the race on the left (its description and what it gives; its racial ability
     * comes with the spell system), the class on the right (its description, then the subclass, a placeholder until
     * subclasses exist). A race or class the player lacks, or whose entry is gone, reads as "None".
     */
    private static Tab origin(CharacterData character, HolderLookup.Provider registries) {
        Optional<Race> race = character.race()
                .flatMap(key -> registries.lookupOrThrow(CharacterRegistries.RACE).get(key)).map(Holder::value);
        Optional<PlayerClass> playerClass = character.classes().stream().findFirst()
                .flatMap(key -> registries.lookupOrThrow(CharacterRegistries.CLASS).get(key)).map(Holder::value);

        List<Row> raceRows = new ArrayList<>();
        race.ifPresent(entry -> {
            raceRows.add(new Row(ORIGIN + "race/description", Component.translatable(entry.description()), "", false,
                    TEXT));
            List<Component> bonuses = CharacterCreationView.bonuses(entry.modifiers());
            for (int n = 0; n < bonuses.size(); n++) {
                raceRows.add(new Row(ORIGIN + "race/bonus/" + n, bonuses.get(n), "", false, BONUS));
            }
        });
        List<Row> classRows = new ArrayList<>();
        playerClass.ifPresent(entry -> classRows.add(new Row(ORIGIN + "class/description",
                Component.translatable(entry.description()), "", false, TEXT)));
        Section subclass = new Section(Component.translatable(GeneratedTexts.GUI_SUBCLASS_WIP),
                List.of(new Row(ORIGIN + "subclass/description",
                        Component.translatable(GeneratedTexts.GUI_SUBCLASS_WIP_DESCRIPTION), "", false, TEXT)));

        Component none = Component.translatable(GeneratedTexts.GUI_NONE);
        Section raceSection = new Section(Component.translatable(GeneratedTexts.GUI_ORIGIN_RACE,
                race.<Component>map(entry -> Component.translatable(entry.name())).orElse(none)), List.copyOf(raceRows));
        Section classSection = new Section(Component.translatable(GeneratedTexts.GUI_ORIGIN_CLASS,
                playerClass.<Component>map(entry -> Component.translatable(entry.name())).orElse(none)),
                List.copyOf(classRows));
        return new Tab(Component.translatable(GeneratedTexts.GUI_TAB_ORIGIN), List.of(raceSection),
                List.of(classSection, subclass));
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

    /**
     * Points, rounded half up to one decimal and shown without a trailing {@code .0}: {@code 25}, {@code 12.5},
     * {@code 6.44} reads {@code 6.4} and {@code 6.45} {@code 6.5} (Matěj, 2026-10-14). The decimal the value prints as
     * is what rounds, so a double just below {@code 6.45} still reads {@code 6.5}.
     */
    public static String points(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        return plain(BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP));
    }

    /**
     * A share in percent, rounded half up to a whole number (Matěj, 2026-10-14): {@code 0.05} reads {@code 5%},
     * {@code 1.005} {@code 101%} and {@code 1.004} {@code 100%}. Only the display rounds; the game keeps the value.
     */
    public static String share(double value) {
        if (!Double.isFinite(value)) {
            return value * 100 + "%";
        }
        return plain(BigDecimal.valueOf(value).movePointRight(2).setScale(0, RoundingMode.HALF_UP)) + "%";
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
        return Stream.of(OTHER, OFFENSE, DEFENSE, SPELLS).flatMap(List::stream);
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
    static Component name(Holder<Attribute> attribute) {
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

    /** A rounded number without trailing zeros or an exponent: {@code 20}, {@code 6.5}, {@code -2}. */
    private static String plain(BigDecimal rounded) {
        BigDecimal stripped = rounded.stripTrailingZeros();
        return (stripped.signum() == 0 ? BigDecimal.ZERO : stripped).toPlainString();
    }

    private static Tab tab(String nameKey, List<Section> left, List<Section> right) {
        return new Tab(Component.translatable(nameKey), left, right);
    }
}
