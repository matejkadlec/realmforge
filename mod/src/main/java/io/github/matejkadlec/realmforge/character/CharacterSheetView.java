package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.DamageCalculator;
import io.github.matejkadlec.realmforge.combat.DamageKind;
import io.github.matejkadlec.realmforge.combat.EnchantmentProtection;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import io.github.matejkadlec.realmforge.core.CoreRegistries;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.generated.GeneratedCurves;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.stats.ClassRole;
import io.github.matejkadlec.realmforge.stats.DamageTypeResistance;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatRule;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import io.github.matejkadlec.realmforge.stats.StatsRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * What the character sheet shows (RLM-136; architecture 18: a thin screen draws a view model computed in common code):
 * the player's name and level, then three tabs (Matěj, 2026-10-30), with room for a Pet tab later (RLM-161).
 * <ul>
 *   <li><b>Character</b> (RLM-509): the race, specialization and class in one line ({@link Identity}), the race's and
 *   the class's parts showing what they are and give when hovered; the XP toward the next level; the four primary
 *   stats, one compact row each with a mark for each milestone, filled once reached, and the {@code +} that spends a
 *   stat point (RLM-196), what they give in their tooltips; then the other stats in two columns.</li>
 *   <li><b>Offense</b> (RLM-235; Matěj, 2026-10-31): Physical, with Critical Hits under it, and Magic.</li>
 *   <li><b>Defense</b>: Mitigation with Health under it, and Resistances, a row per {@link DamageKind}: what the
 *   player's resistance stats (RLM-233) and protection enchantments block of such a hit together
 *   ({@link #resistanceRows}). Two columns on every stat tab, so the panel stays narrow (Matěj, 2026-10-31).</li>
 * </ul>
 * No stat shows twice (Matěj, 2026-10-31). Every row has a tooltip: what the stat does, one text per stat
 * ({@code stat_tooltip.realmforge.<path>}, {@link #description}), which the guide (M4) can reuse; a primary stat's adds
 * what its points give the player's class role. Spell Resistance and the schools' resistances are mob-only stats
 * (Matěj, 2026-10-30, RLM-233), so the sheet never shows them, and the four damage type resistances show in the
 * Resistances rows. Apothic Attributes' ranged stats and Healing Received show when it is loaded, as in the pack. Built
 * from a player's synced attributes, character and level, its synced enchantment protection and the synced registries,
 * so the client screen and the GameTests read the same rows. Values: {@link StatNumbers}.
 *
 * @param title the player's name and level
 * @param character the Character tab
 * @param statTabs the tabs after it, Offense and Defense
 */
public record CharacterSheetView(Component title, CharacterTab character, List<StatTab> statTabs) {
    /** The translation key prefix of the stats' tooltips: the path of the attribute's id follows. */
    public static final String TOOLTIP_PREFIX = "stat_tooltip.realmforge.";

    /**
     * Text colours the screen draws with: column headings, rows, what a primary stat or the race gives in vanilla's
     * yellow ({@code ChatFormatting.YELLOW}), the milestones a stat has reached in vanilla's gold
     * ({@code ChatFormatting.GOLD}) and those still ahead in gray (Matěj, 2026-10-30: the green the bonuses had read
     * badly).
     */
    public static final int TITLE = 0xFFFFD24D;
    public static final int STAT = 0xFFFFFFFF;
    public static final int BONUS = 0xFFFFFF55;
    public static final int MILESTONE = 0xFFFFAA00;
    public static final int AHEAD = 0xFF8C8C8C;
    /** What a choice would give: the {@code +} that spends a stat point, and character creation's bonus lines. */
    public static final int CHOICE = 0xFF7FD97F;
    /** Descriptions: what a stat does, a race or a class. */
    public static final int TEXT = 0xFFC8C8C8;
    /**
     * The race's racial ability and passive in its tooltip: the heading or name in gold, what it is or gives in yellow,
     * as on a sword's tooltip (Matěj, 2026-10-30).
     */
    public static final int RACIAL_NAME = 0xFFFFAA00;
    public static final int RACIAL_DETAIL = 0xFFFFFF55;

    public CharacterSheetView {
        statTabs = List.copyOf(statTabs);
    }

    /**
     * The Character tab (RLM-509).
     *
     * @param name the tab's name
     * @param identity race, specialization and class in one line
     * @param xp the XP toward the next level, for a player with a character
     * @param primaryTitle the primary stats' heading
     * @param unspent "Unspent: 3" at the end of the heading, for a player with a character, else null
     * @param primary the primary stats, STR, DEX, VIT, INT
     * @param other the other stats in two columns, set apart without a heading
     */
    public record CharacterTab(Component name, Identity identity, Optional<XpBarView> xp, Component primaryTitle,
            @Nullable Component unspent, List<PrimaryStat> primary, List<List<Row>> other) {
        public CharacterTab {
            primary = List.copyOf(primary);
            other = other.stream().<List<Row>>map(List::copyOf).toList();
        }
    }

    /** Race, specialization and class, "Human Frost Mage": its parts in order. Empty without a character. */
    public record Identity(List<Part> parts) {
        public Identity {
            parts = List.copyOf(parts);
        }

        /** The parts joined by spaces. */
        public Component line() {
            MutableComponent line = Component.empty();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    line.append(" ");
                }
                line.append(parts.get(i).name());
            }
            return line;
        }
    }

    /** A part of the identity line and its tooltip, each line in its colour; a specialization's has none yet. */
    public record Part(Component name, List<Component> tooltip) {
        public Part {
            tooltip = List.copyOf(tooltip);
        }
    }

    /**
     * A specialization of the player's class and the points the player has in it. The specialization trees come with C5;
     * until then no player has points in any, and the line names race and class only.
     *
     * @param id its id, which names it in English, so the alphabetical order of a tie is the names' order
     * @param name its name
     * @param points the points the player has in it
     */
    public record Specialization(String id, Component name, int points) {
    }

    /**
     * A primary stat's compact row.
     *
     * @param row the stat: its name, value and tooltip, which lists what its points give
     * @param marks its milestones in order
     * @param spend the {@code +} that spends a stat point on it, or null when it offers none
     */
    public record PrimaryStat(Row row, List<Mark> marks, @Nullable Spend spend) {
        public PrimaryStat {
            marks = List.copyOf(marks);
        }
    }

    /** A milestone of a primary stat: its points and whether the player has reached them. */
    public record Mark(int points, boolean reached) {
    }

    /** A tab of stat columns. */
    public record StatTab(Component name, List<Column> columns) {
        public StatTab {
            columns = List.copyOf(columns);
        }
    }

    /** A column: its sections, one under another, each under its heading. */
    public record Column(List<Section> sections) {
        public Column {
            sections = List.copyOf(sections);
        }
    }

    /** A column's heading and its rows. */
    public record Section(Component title, List<Row> rows) {
        public Section {
            rows = List.copyOf(rows);
        }
    }

    /**
     * One stat.
     *
     * @param id the attribute's description id, or {@code resistance/<kind>} for a Resistances row, for tests
     * @param label its name on the sheet
     * @param value the formatted value
     * @param color the text colour, opaque ARGB
     * @param tooltip the lines under its name when hovered, each in its colour
     */
    public record Row(String id, Component label, String value, int color, List<Component> tooltip) {
        public Row {
            tooltip = List.copyOf(tooltip);
        }
    }

    /**
     * The {@code +} beside a primary stat (RLM-196).
     *
     * @param stat the primary stat it spends a point on
     * @param gains what one more point gives, a line each ("+1% Attack Damage"): each per-point bonus that grows, and
     *     each bonus of a milestone the point reaches
     */
    public record Spend(ResourceKey<Attribute> stat, List<Component> gains) {
        public Spend {
            gains = List.copyOf(gains);
        }
    }

    /** An attribute on the sheet, under its own name or under one the sheet gives it ({@code label}, a text key). */
    private record Entry(Holder<Attribute> attribute, @Nullable String label) {
    }

    /** The order of the primary stats on the sheet (STR, DEX, VIT, INT); others follow in id order. */
    private static final List<Holder<Attribute>> PRIMARY_ORDER = List.of(GeneratedStats.STRENGTH,
            GeneratedStats.DEXTERITY, GeneratedStats.VITALITY, GeneratedStats.INTELLECT);

    /** The other stats' two columns (Matěj, 2026-10-31): Stamina right below Luck (RLM-170). */
    private static final List<Entry> OTHER_LEFT = List.of(entry(Attributes.LUCK), entry(GeneratedStats.STAMINA),
            entry(Attributes.ENTITY_INTERACTION_RANGE, GeneratedTexts.GUI_ENTITY_REACH),
            entry(Attributes.BLOCK_INTERACTION_RANGE, GeneratedTexts.GUI_BLOCK_REACH));
    private static final List<Entry> OTHER_RIGHT = List.of(
            entry(Attributes.BLOCK_BREAK_SPEED, GeneratedTexts.GUI_MINING_SPEED),
            entry(Attributes.MOVEMENT_SPEED, GeneratedTexts.GUI_MOVEMENT_SPEED),
            entry(NeoForgeMod.SWIM_SPEED, GeneratedTexts.GUI_SWIMMING_SPEED),
            entry(GeneratedStats.XP_MULTIPLIER));
    /**
     * Offense's Physical column, melee and ranged together (Matěj, 2026-10-31: a Warrior uses a bow, a Hunter a melee
     * weapon), in his order: these, then Apothic Attributes' Arrow Damage, Draw Speed and Arrow Velocity
     * ({@link #APOTHIC_RANGED}).
     */
    private static final List<Entry> PHYSICAL = List.of(entry(Attributes.ATTACK_DAMAGE),
            entry(Attributes.ATTACK_SPEED), entry(GeneratedStats.ARMOR_PIERCE), entry(GeneratedStats.ARMOR_SHRED));
    /** Under Physical: our crit counts for melee hits, fully drawn shots and spells alike (architecture 9.1). */
    private static final List<Entry> CRITICAL = List.of(entry(GeneratedStats.CRIT_CHANCE),
            entry(GeneratedStats.CRIT_DAMAGE));
    /**
     * Mana first, then what shapes a spell (Matěj, 2026-10-14); the schools' powers follow from data. RLM-535's Spell
     * Penetration goes right after Spell Power.
     */
    private static final List<Entry> MAGIC = List.of(entry(GeneratedStats.MAX_MANA),
            entry(GeneratedStats.MANA_REGENERATION), entry(GeneratedStats.SPELL_POWER),
            entry(GeneratedStats.CAST_TIME_REDUCTION), entry(GeneratedStats.COOLDOWN_REDUCTION));
    private static final List<Entry> MITIGATION = List.of(entry(Attributes.ARMOR), entry(Attributes.ARMOR_TOUGHNESS),
            entry(GeneratedStats.MAGIC_SHIELD), entry(GeneratedStats.DODGE_CHANCE),
            entry(Attributes.KNOCKBACK_RESISTANCE));
    /** Healing Received, Apothic Attributes', goes between them ({@link #APOTHIC_HEALING}). */
    private static final List<Entry> HEALTH_BEFORE = List.of(entry(Attributes.MAX_HEALTH),
            entry(GeneratedStats.HEALTH_REGENERATION));
    private static final List<Entry> HEALTH_AFTER = List.of(entry(GeneratedStats.LIFESTEAL),
            entry(GeneratedStats.OVERHEAL));

    /** Apothic Attributes' stats on the sheet (RLM-235), there when it is loaded. */
    private static final String APOTHIC = "apothic_attributes";
    private static final List<String> APOTHIC_RANGED = List.of("arrow_damage", "draw_speed", "arrow_velocity");
    private static final String APOTHIC_HEALING = "healing_received";

    /** Attributes that read as a percentage of the player's base: players walk at 0.1, the default is 0.7. */
    private static final List<Holder<Attribute>> PERCENT_OF_BASE = List.of(Attributes.MOVEMENT_SPEED,
            Attributes.BLOCK_BREAK_SPEED);

    /**
     * Makes every attribute the sheet lists sync to the client, which draws the sheet from the player's own attributes.
     * Vanilla syncs most of them but not attack damage, which only the server computes; our stats are syncable from
     * their registration (architecture 8.1). Called once from {@link CharacterFeature} at common setup, when every
     * attribute the sheet lists is registered (NeoForge's swim speed, Apothic Attributes' and ours are not at mod
     * construction).
     */
    static void syncShownAttributes() {
        listed().forEach(entry -> entry.attribute().value().setSyncable(true));
    }

    /** The sheet of a player, from its attributes, level and character (synced to its own client) and the registries. */
    public static CharacterSheetView of(Player player) {
        int level = player.getExistingData(StatsAttachments.LEVEL).map(EntityLevel::value).orElse(EntityLevel.MIN);
        Component title = Component.translatable(GeneratedTexts.GUI_CHARACTER_TITLE, player.getName(), level);
        CharacterData character = Characters.data(player);
        Optional<XpBarView> xp = character.exists() ? xp(player.registryAccess(), character, Characters.level(player))
                : Optional.empty();
        EnchantmentProtection protection = EnchantmentProtection.of(player);
        // C5 brings the specialization trees and the player's points in them.
        return build(title, player::getAttributeValue, player::getAttributeBaseValue, player.registryAccess(),
                character, StatPoints.unspent(player), xp, List.of(), protection::share);
    }

    /**
     * The sheet for any source of attribute values and base values (both must answer for every stat and every
     * attribute the sheet lists), a character, its unspent stat points, its XP, its specialization points and what its
     * protection enchantments block of each kind of hit.
     */
    static CharacterSheetView build(Component title, ToDoubleFunction<Holder<Attribute>> value,
            ToDoubleFunction<Holder<Attribute>> base, HolderLookup.Provider registries, CharacterData character,
            int unspentPoints, Optional<XpBarView> xp, List<Specialization> specializations,
            ToDoubleFunction<DamageKind> enchantmentProtection) {
        List<StatRule> rules = new ArrayList<>();
        registries.lookupOrThrow(StatsRegistries.STAT_RULE).listElements().forEach(rule -> rules.add(rule.value()));
        List<SpellSchool> schools = new ArrayList<>();
        registries.lookupOrThrow(CombatRegistries.SPELL_SCHOOL).listElements()
                .sorted(Comparator.<Holder.Reference<SpellSchool>, Boolean>comparing(school -> school.value().magic())
                        .thenComparing(school -> school.key().identifier().toString()))
                .forEach(school -> schools.add(school.value()));

        // By key: the schools' holders come from the attribute registry, ours from GeneratedStats. The schools'
        // resistances are mob-only, so they count as placed and never show; the damage type resistances show in the
        // Resistances rows.
        Set<ResourceKey<Attribute>> placed = new HashSet<>();
        placed.add(key(GeneratedStats.SPELL_RESISTANCE));
        GeneratedStats.DAMAGE_RESISTANCES.forEach(resistance -> placed.add(key(resistance.stat())));
        schools.forEach(school -> {
            school.power().flatMap(Holder::unwrapKey).ifPresent(placed::add);
            school.resistance().flatMap(Holder::unwrapKey).ifPresent(placed::add);
        });
        listed().forEach(entry -> placed.add(key(entry.attribute())));
        // The rules' bonuses the player's main class's role gets; without a character, the role-free ones.
        Optional<ClassRole> role = character.classes().stream().findFirst()
                .flatMap(key -> registries.lookupOrThrow(CharacterRegistries.CLASS).get(key))
                .map(entry -> entry.value().role());

        List<PrimaryStat> primary = new ArrayList<>();
        for (Holder<Attribute> stat : primaryStats()) {
            placed.add(key(stat));
            double points = value.applyAsDouble(stat);
            List<StatRule> statRules = rules.stream().filter(rule -> rule.stat().is(key(stat))).toList();
            boolean spendable = character.exists() && unspentPoints > 0 && StatPoints.isPrimaryStat(key(stat));
            List<Component> tooltip = new ArrayList<>(List.of(description(stat)));
            List<Mark> marks = new ArrayList<>();
            for (StatRule rule : statRules) {
                tooltip.addAll(ruleLines(rule, points, role));
                rule.milestones().stream().sorted(Comparator.comparingInt(StatRule.Milestone::points))
                        .forEach(milestone -> marks.add(new Mark(milestone.points(), points >= milestone.points())));
            }
            primary.add(new PrimaryStat(new Row(stat.value().getDescriptionId(), name(stat),
                    StatNumbers.points(points), STAT, tooltip), marks,
                    spendable ? new Spend(key(stat), gains(statRules, points, role)) : null));
        }
        // The unspent points at the end of the heading (Matěj, 2026-10-16): "Unspent:" white, the number yellow while
        // any are left.
        Component unspent = character.exists() ? Component.translatable(GeneratedTexts.GUI_UNSPENT_STAT_POINTS,
                Component.literal(String.valueOf(unspentPoints)).withColor((unspentPoints > 0 ? TITLE : STAT)
                        & 0xFFFFFF)) : null;
        List<Row> otherRight = rows(OTHER_RIGHT, value, base);
        // A stat of ours that no column names still shows among the other stats, so new content is never missing.
        for (Holder<Attribute> stat : GeneratedStats.ALL) {
            if (!placed.contains(key(stat))) {
                otherRight.add(statRow(stat, value.applyAsDouble(stat), STAT));
            }
        }
        CharacterTab characterTab = new CharacterTab(Component.translatable(GeneratedTexts.GUI_TAB_CHARACTER),
                identity(race(character, registries), specializations, playerClass(character, registries)), xp,
                Component.translatable(GeneratedTexts.GUI_PRIMARY_STATS), unspent, primary,
                List.of(rows(OTHER_LEFT, value, base), otherRight));

        List<Row> magic = rows(MAGIC, value, base);
        for (SpellSchool school : schools) {
            school.power().ifPresent(stat -> magic.add(statRow(stat, value.applyAsDouble(stat), school.color())));
        }
        StatTab offense = new StatTab(Component.translatable(GeneratedTexts.GUI_TAB_OFFENSE), List.of(
                new Column(List.of(section(GeneratedTexts.GUI_PHYSICAL, rows(physical(), value, base)),
                        section(GeneratedTexts.GUI_CRITICAL_HITS, rows(CRITICAL, value, base)))),
                new Column(List.of(section(GeneratedTexts.GUI_MAGIC, magic)))));
        StatTab defense = new StatTab(Component.translatable(GeneratedTexts.GUI_TAB_DEFENSE), List.of(
                new Column(List.of(section(GeneratedTexts.GUI_MITIGATION, rows(MITIGATION, value, base)),
                        section(GeneratedTexts.GUI_HEALTH, rows(health(), value, base)))),
                new Column(List.of(section(GeneratedTexts.GUI_RESISTANCES,
                        resistanceRows(value, registries, enchantmentProtection))))));
        return new CharacterSheetView(title, characterTab, List.of(offense, defense));
    }

    /**
     * The Resistances rows (Matěj, 2026-10-31), one per {@link DamageKind}: the share of such a hit the player does not
     * take, from their resistance stats and protection enchantments together ({@link #combined}). The stats are those
     * of {@link GeneratedStats#DAMAGE_RESISTANCES} whose tag the kind's damage type is in, multiplied and at most 75 %,
     * as the damage pipeline applies them ({@link DamageCalculator#damageTypeResistance}); Fire and Magic have none.
     * Armor is not in it: what it blocks depends on the hit's size. The tooltip says what the row counts, then splits
     * the value: from stats (where any count), from enchantments.
     */
    static List<Row> resistanceRows(ToDoubleFunction<Holder<Attribute>> value, HolderLookup.Provider registries,
            ToDoubleFunction<DamageKind> enchantmentProtection) {
        HolderLookup.RegistryLookup<DamageType> types = registries.lookupOrThrow(Registries.DAMAGE_TYPE);
        List<Row> rows = new ArrayList<>();
        for (DamageKind kind : DamageKind.values()) {
            String name = kind.name().toLowerCase(Locale.ROOT);
            List<DamageTypeResistance> counted = types.get(kind.example())
                    .map(type -> GeneratedStats.DAMAGE_RESISTANCES.stream()
                            .filter(resistance -> type.is(resistance.damage())).toList())
                    .orElse(List.of());
            double stats = DamageCalculator.damageTypeResistance(counted.stream()
                    .mapToDouble(resistance -> value.applyAsDouble(resistance.stat())).toArray());
            double enchantments = enchantmentProtection.applyAsDouble(kind);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(colored(Component.translatable(TOOLTIP_PREFIX + "resistance_" + name), TEXT));
            if (!counted.isEmpty()) {
                tooltip.add(colored(Component.translatable(GeneratedTexts.GUI_RESISTANCE_FROM_STATS,
                        StatNumbers.share(stats)), BONUS));
            }
            tooltip.add(colored(Component.translatable(GeneratedTexts.GUI_RESISTANCE_FROM_ENCHANTMENTS,
                    StatNumbers.share(enchantments)), BONUS));
            rows.add(new Row("resistance/" + name, Component.translatable(resistanceName(kind)),
                    StatNumbers.share(combined(stats, enchantments)), STAT, tooltip));
        }
        return rows;
    }

    /**
     * Two reductions of one hit together, each on what the other leaves: 1 − (1 − stats) × (1 − enchantments) (Matěj,
     * 2026-10-31), each kept between 0 and 1.
     */
    static double combined(double stats, double enchantments) {
        return 1 - (1 - Math.clamp(stats, 0, 1)) * (1 - Math.clamp(enchantments, 0, 1));
    }

    private static String resistanceName(DamageKind kind) {
        return switch (kind) {
            case PHYSICAL -> GeneratedTexts.GUI_RESISTANCE_PHYSICAL;
            case PROJECTILE -> GeneratedTexts.GUI_RESISTANCE_PROJECTILE;
            case EXPLOSION -> GeneratedTexts.GUI_RESISTANCE_EXPLOSION;
            case FALL -> GeneratedTexts.GUI_RESISTANCE_FALL;
            case FIRE -> GeneratedTexts.GUI_RESISTANCE_FIRE;
            case MAGIC -> GeneratedTexts.GUI_RESISTANCE_MAGIC;
        };
    }

    /** The XP bar's view of a player with a character, while the XP curve exists. */
    private static Optional<XpBarView> xp(HolderLookup.Provider registries, CharacterData character, int level) {
        Optional<Curve> curve = registries.lookupOrThrow(CoreRegistries.CURVE).get(GeneratedCurves.XP_TO_NEXT_LEVEL)
                .map(Holder::value);
        return curve.map(xpCurve -> XpBarView.of(xpCurve, character.totalXp(), level,
                character.levelCap().orElse(Integer.MAX_VALUE)));
    }

    /** What the XP bar reads: "350 / 1200 XP", or that the level cap holds the player. */
    public static Component xpText(XpBarView xp) {
        return xp.capped() ? Component.translatable(GeneratedTexts.GUI_XP_CAPPED)
                : Component.translatable(GeneratedTexts.GUI_XP_PROGRESS, xp.xpInLevel(), xp.xpToNext());
    }

    /**
     * The identity line (RLM-509): the race, the specialization the player has the most points in, and the class,
     * "Human Frost Mage". A tie goes alphabetically ("Human Fire Mage" with 10 Fire and 10 Frost points); with no
     * points in any, "Human Mage". A player without a race or class leaves that part out.
     */
    static Identity identity(Optional<Part> race, List<Specialization> specializations, Optional<Part> playerClass) {
        List<Part> parts = new ArrayList<>();
        race.ifPresent(parts::add);
        playerClass.ifPresent(classPart -> {
            shownSpecialization(specializations).ifPresent(spec -> parts.add(new Part(spec.name(), List.of())));
            parts.add(classPart);
        });
        return new Identity(parts);
    }

    /** The specialization the line names: the one with the most points, a tie alphabetically; none without points. */
    static Optional<Specialization> shownSpecialization(List<Specialization> specializations) {
        return specializations.stream().filter(spec -> spec.points() > 0)
                .min(Comparator.<Specialization>comparingInt(spec -> -spec.points())
                        .thenComparing(Specialization::id));
    }

    /**
     * The race's part: its name, then in its tooltip its description, what it gives, and its racial ability and
     * passive (C4), each a gold heading over a yellow line (Matěj, 2026-10-30): what the Origin tab showed.
     */
    private static Optional<Part> race(CharacterData character, HolderLookup.Provider registries) {
        return character.race().flatMap(key -> registries.lookupOrThrow(CharacterRegistries.RACE).get(key))
                .map(Holder::value).map(race -> {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(colored(Component.translatable(race.description()), TEXT));
                    CharacterCreationView.bonuses(race.modifiers()).forEach(line -> tooltip.add(colored(line, BONUS)));
                    race.racialSpell().ifPresent(spell -> {
                        tooltip.add(colored(Component.translatable(GeneratedTexts.GUI_RACIAL_ABILITY), RACIAL_NAME));
                        tooltip.add(colored(Component.translatable(spell.value().name()), RACIAL_DETAIL));
                    });
                    race.racialPassive().ifPresent(passive -> {
                        tooltip.add(colored(Component.translatable(passive.name()), RACIAL_NAME));
                        tooltip.add(colored(Component.literal(RacialPassives.description(passive)), RACIAL_DETAIL));
                    });
                    return new Part(Component.translatable(race.name()), tooltip);
                });
    }

    /** The class's part: its name, and its description in its tooltip. */
    private static Optional<Part> playerClass(CharacterData character, HolderLookup.Provider registries) {
        return character.classes().stream().findFirst()
                .flatMap(key -> registries.lookupOrThrow(CharacterRegistries.CLASS).get(key)).map(Holder::value)
                .map(entry -> new Part(Component.translatable(entry.name()),
                        List.of(colored(Component.translatable(entry.description()), TEXT))));
    }

    /** The tab names in order: Character, then the stat tabs. */
    public List<Component> tabNames() {
        List<Component> names = new ArrayList<>();
        names.add(character.name());
        statTabs.forEach(tab -> names.add(tab.name()));
        return names;
    }

    /**
     * Every row of the sheet: the primary stats, the other stats column by column, then the stat tabs column by column,
     * section by section.
     */
    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        character.primary().forEach(stat -> rows.add(stat.row()));
        character.other().forEach(rows::addAll);
        statTabs.forEach(tab -> tab.columns().forEach(column -> column.sections()
                .forEach(section -> rows.addAll(section.rows()))));
        return rows;
    }

    /** What a stat does: its tooltip text ({@code content/texts/stat_tooltip.yaml}), in the descriptions' gray. */
    public static Component description(Holder<Attribute> attribute) {
        return colored(Component.translatable(TOOLTIP_PREFIX + key(attribute).identifier().getPath()), TEXT);
    }

    /** Every attribute the columns list by name (the primary stats and the schools' powers come from data). */
    private static Stream<Entry> listed() {
        return Stream.of(OTHER_LEFT, OTHER_RIGHT, physical(), CRITICAL, MAGIC, MITIGATION, health())
                .flatMap(List::stream);
    }

    private static List<Entry> physical() {
        List<Entry> entries = new ArrayList<>(PHYSICAL);
        APOTHIC_RANGED.forEach(path -> apothic(path).ifPresent(attribute -> entries.add(entry(attribute))));
        return entries;
    }

    private static List<Entry> health() {
        List<Entry> entries = new ArrayList<>(HEALTH_BEFORE);
        apothic(APOTHIC_HEALING).ifPresent(attribute -> entries.add(entry(attribute)));
        entries.addAll(HEALTH_AFTER);
        return entries;
    }

    /** An attribute of Apothic Attributes, while it is loaded. */
    private static Optional<Holder<Attribute>> apothic(String path) {
        return BuiltInRegistries.ATTRIBUTE.get(Identifier.fromNamespaceAndPath(APOTHIC, path))
                .map(attribute -> attribute);
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
                    ? StatNumbers.percentOf(current, base.applyAsDouble(attribute))
                    : StatNumbers.value(attribute, current);
            rows.add(new Row(attribute.value().getDescriptionId(), name(attribute), shown, STAT,
                    List.of(description(attribute))));
        }
        return rows;
    }

    /**
     * What one more point gives a player of {@code role} under the stat's rules: each per-point bonus for that role
     * that grows (one that reached its cap does not), then the bonuses for that role of each milestone the point
     * reaches.
     */
    static List<Component> gains(List<StatRule> rules, double points, Optional<ClassRole> role) {
        List<Component> gains = new ArrayList<>();
        for (StatRule rule : rules) {
            for (StatRule.PerPoint entry : rule.perPoint()) {
                double more = entry.totalAt(points + 1) - entry.totalAt(points);
                if (Math.abs(more) > 1e-9 && entry.modifier().appliesTo(role)) {
                    gains.add(bonus(entry.modifier(), more));
                }
            }
            for (StatRule.Milestone milestone : rule.milestones()) {
                if (points < milestone.points() && points + 1 >= milestone.points()) {
                    milestone.modifiers().stream().filter(modifier -> modifier.appliesTo(role))
                            .forEach(modifier -> gains.add(bonus(modifier, modifier.amount())));
                }
            }
        }
        return gains;
    }

    /**
     * What the rule gives a player of {@code role} at {@code points}, for a primary stat's tooltip: each per-point
     * total for that role that is not 0, in yellow, then every milestone with a bonus for that role, listing those
     * bonuses only, in gold once reached and gray while ahead.
     */
    static List<Component> ruleLines(StatRule rule, double points, Optional<ClassRole> role) {
        List<Component> lines = new ArrayList<>();
        for (StatRule.PerPoint entry : rule.perPoint()) {
            double total = entry.totalAt(points);
            if (total != 0 && entry.modifier().appliesTo(role)) {
                lines.add(colored(bonus(entry.modifier(), total), BONUS));
            }
        }
        for (StatRule.Milestone milestone : rule.milestones()) {
            MutableComponent bonuses = Component.empty();
            for (StatRule.Modifier modifier : milestone.modifiers()) {
                if (!modifier.appliesTo(role)) {
                    continue;
                }
                if (!bonuses.getSiblings().isEmpty()) {
                    bonuses.append(", ");
                }
                bonuses.append(bonus(modifier, modifier.amount()));
            }
            if (!bonuses.getSiblings().isEmpty()) {
                lines.add(colored(Component.translatable(GeneratedTexts.GUI_STAT_MILESTONE, milestone.points(),
                        bonuses), points >= milestone.points() ? MILESTONE : AHEAD));
            }
        }
        return lines;
    }

    private static MutableComponent bonus(StatRule.Modifier modifier, double amount) {
        return Component.translatable(GeneratedTexts.GUI_STAT_BONUS,
                StatNumbers.signed(modifier.attribute(), modifier.operation(), amount), name(modifier.attribute()));
    }

    private static Row statRow(Holder<Attribute> stat, double value, int color) {
        return new Row(stat.value().getDescriptionId(), name(stat), StatNumbers.value(stat, value), color,
                List.of(description(stat)));
    }

    private static Component colored(Component text, int color) {
        return text.copy().withColor(color & 0xFFFFFF);
    }

    private static ResourceKey<Attribute> key(Holder<Attribute> attribute) {
        return attribute.unwrapKey().orElseThrow(() -> new IllegalStateException("unregistered attribute"));
    }

    /**
     * An attribute's name on the sheet, on its row and in what a primary stat or a race gives: the name the sheet
     * gives it (Movement Speed for vanilla's Speed), or its own.
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
        return new Section(Component.translatable(titleKey), rows);
    }
}
