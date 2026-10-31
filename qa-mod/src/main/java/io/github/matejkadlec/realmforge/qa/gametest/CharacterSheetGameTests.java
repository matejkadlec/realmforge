package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.character.CharacterSheetView;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Mark;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.PrimaryStat;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Row;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Section;
import io.github.matejkadlec.realmforge.character.StatNumbers;
import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * RLM-136, RLM-509, RLM-235: the character sheet shows the player's name, level and attributes on three tabs:
 * Character with the primary stats (what they give in their tooltips) and the other stats, Stamina right below Luck
 * (RLM-170); Offense with Melee, Ranged and Spells, a stat that counts for several in each; Defense with Health and
 * Mitigation. Every stat of ours shows, the mob-only resistances never (RLM-233), Apothic Attributes' ranged stats and
 * Healing Received do, and every row has its tooltip text. Values with one decimal (RLM-530). The sheet's view model is
 * common code ({@link CharacterSheetView}), so the test builds it for a fake player on the server after {@code /qa
 * stats} set Strength, and compares it with the attributes the stat rules produced.
 */
public final class CharacterSheetGameTests {
    private CharacterSheetGameTests() {
    }

    @QaGameTest
    public static void sheetShowsThePlayersAttributes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-sheet");
        player.doTick();
        StatsCommands.setBase(player, GeneratedStats.STRENGTH, 25);
        StatsCommands.setBase(player, GeneratedStats.VITALITY, 10);
        CharacterSheetView view = CharacterSheetView.of(player);

        helper.assertValueEqual(view.title().getString(), "qa-sheet · Lv. 1", "the sheet's heading");
        helper.assertValueEqual(view.tabNames().stream().map(Component::getString).toList(),
                List.of("Character", "Offense", "Defense"), "the sheet's tabs (Matěj, 2026-10-30)");
        helper.assertValueEqual(headings(view, 0), List.of("Melee", "Ranged", "Spells"), "Offense's columns");
        helper.assertValueEqual(headings(view, 1), List.of("Health", "Mitigation"), "Defense's columns");
        helper.assertTrue(view.character().identity().parts().isEmpty(), "no character: no race or class");
        helper.assertTrue(view.character().xp().isEmpty(), "no character: no XP bar");

        // Every stat of ours shows, the mob-only resistances never; no column lists a stat twice.
        Set<String> mobOnly = new HashSet<>(Set.of(GeneratedStats.SPELL_RESISTANCE.value().getDescriptionId()));
        server.registryAccess().lookupOrThrow(CombatRegistries.SPELL_SCHOOL).listElements().forEach(school ->
                school.value().resistance().ifPresent(stat -> mobOnly.add(stat.value().getDescriptionId())));
        for (Holder<Attribute> stat : GeneratedStats.ALL) {
            String id = stat.value().getDescriptionId();
            long shown = view.rows().stream().filter(row -> row.id().equals(id)).count();
            if (mobOnly.contains(id)) {
                helper.assertValueEqual(shown, 0L, stat.getRegisteredName() + " is mob-only (RLM-233)");
            } else {
                helper.assertTrue(shown >= 1, stat.getRegisteredName() + " shows on the sheet");
                assertValue(helper, view, stat, player.getAttributeValue(stat));
            }
        }
        for (List<Row> column : columns(view)) {
            List<String> ids = column.stream().map(Row::id).toList();
            helper.assertValueEqual(new HashSet<>(ids).size(), ids.size(), "a column lists a stat once: " + ids);
        }
        // Stats that count for several show in each column they affect (RLM-235): our crit counts for a fully drawn
        // shot too.
        String critChance = GeneratedStats.CRIT_CHANCE.value().getDescriptionId();
        helper.assertValueEqual(view.statTabs().getFirst().columns().stream()
                .filter(column -> column.rows().stream().anyMatch(row -> row.id().equals(critChance))).count(), 3L,
                "crit chance under Melee, Ranged and Spells");
        // Apothic Attributes' ranged stats and Healing Received, which the GameTest server loads as a pack mod.
        for (String path : List.of("arrow_damage", "draw_speed", "arrow_velocity", "healing_received")) {
            Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.get(Identifier.fromNamespaceAndPath(
                    "apothic_attributes", path)).orElseThrow();
            helper.assertValueEqual(row(helper, view, attribute).value(), "100%", path + " on the sheet");
        }
        helper.assertValueEqual(row(helper, view, BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(
                "apothic_attributes:draw_speed")).orElseThrow()).label().getString(), "Draw Speed",
                "Apothic Attributes' own name");

        // Every row has a tooltip: what the stat does, from its own text.
        for (Row row : view.rows()) {
            helper.assertFalse(row.tooltip().isEmpty(), row.id() + " has a tooltip");
            Component description = row.tooltip().getFirst();
            String key = ((TranslatableContents) description.getContents()).getKey();
            helper.assertTrue(key.startsWith(CharacterSheetView.TOOLTIP_PREFIX), row.id() + "'s tooltip text: " + key);
            helper.assertFalse(description.getString().equals(key), row.id() + "'s tooltip text exists: " + key);
        }
        helper.assertValueEqual(row(helper, view, GeneratedStats.HEALTH_REGENERATION).tooltip().getFirst().getString(),
                "Amount of health you regenerate per 5 seconds in addition to natural health regeneration.",
                "Matěj's text");

        Row miningSpeed = row(helper, view, Attributes.BLOCK_BREAK_SPEED);
        helper.assertValueEqual(miningSpeed.label().getString(), "Mining Speed", "the sheet's name for it");
        helper.assertValueEqual(miningSpeed.value(), "100%", "mining speed reads as a percent of the player's base");
        helper.assertValueEqual(row(helper, view, NeoForgeMod.SWIM_SPEED).value(), "100%", "swimming speed");
        helper.assertValueEqual(row(helper, view, Attributes.BLOCK_INTERACTION_RANGE).value(), "4.5", "block reach");
        helper.assertValueEqual(row(helper, view, GeneratedStats.XP_MULTIPLIER).value(), "100%", "XP multiplier");
        // Stamina sits right below Luck among the other stats (RLM-170), and Vitality's milestone at 10 gives it 10.
        List<Row> other = view.character().other();
        List<String> otherIds = other.stream().map(Row::id).toList();
        int luck = otherIds.indexOf(Attributes.LUCK.value().getDescriptionId());
        helper.assertTrue(luck >= 0, "the other stats list Luck: " + otherIds);
        helper.assertValueEqual(otherIds.get(luck + 1), GeneratedStats.STAMINA.value().getDescriptionId(),
                "the row below Luck");
        helper.assertValueEqual(other.get(luck + 1).value(), "10", "10 Vitality's Stamina");
        for (Holder<Attribute> attribute : List.of(Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.ATTACK_DAMAGE)) {
            assertValue(helper, view, attribute, player.getAttributeValue(attribute));
        }
        // The client draws the sheet from the player's own attributes, so every attribute on it must sync to the client;
        // vanilla keeps attack damage on the server unless the sheet makes it syncable, and Apothic Attributes' ranged
        // stats too (RLM-235).
        Set<String> shown = view.rows().stream().map(Row::id).collect(Collectors.toSet());
        List<Holder.Reference<Attribute>> onSheet = BuiltInRegistries.ATTRIBUTE.listElements()
                .filter(attribute -> shown.contains(attribute.value().getDescriptionId())).toList();
        helper.assertTrue(onSheet.stream().anyMatch(attribute -> attribute.is(Attributes.ATTACK_DAMAGE)),
                "the sheet shows attack damage");
        for (Holder.Reference<Attribute> attribute : onSheet) {
            helper.assertTrue(attribute.value().isClientSyncable(),
                    attribute.getRegisteredName() + " is on the sheet, so it must sync to the client");
        }
        Row movementSpeed = row(helper, view, Attributes.MOVEMENT_SPEED);
        helper.assertValueEqual(movementSpeed.label().getString(), "Movement Speed", "the sheet's name for Speed");
        helper.assertValueEqual(movementSpeed.value(), "100%",
                "movement speed reads as a percent of the player's base");
        helper.assertValueEqual(row(helper, view, GeneratedStats.STRENGTH).value(), "25", "Strength");
        helper.assertValueEqual(row(helper, view, GeneratedStats.CRIT_CHANCE).value(), "15%",
                "the base 5% crit chance and Strength's 10% at 10 points");
        helper.assertValueEqual(row(helper, view, GeneratedStats.LIFESTEAL).value(), "12.5%",
                "25 Strength's half a percent each, no longer rounded to 13% (RLM-530)");

        // A primary stat's tooltip: its text, the per-point totals, then its milestones; its marks show them.
        PrimaryStat strength = primary(helper, view, GeneratedStats.STRENGTH);
        helper.assertValueEqual(tooltip(strength, 1), "+12.5 Attack Damage", "what 25 Strength gives");
        helper.assertValueEqual(tooltip(strength, 2), "+12.5% Lifesteal", "half percents kept (RLM-530)");
        helper.assertValueEqual(tooltip(strength, 4), "At 25: +25% Attack Damage, +5 Armor Pierce",
                "Strength's second milestone");
        helper.assertValueEqual(color(strength.row().tooltip().get(4)), CharacterSheetView.MILESTONE,
                "the milestone is reached at 25");
        helper.assertValueEqual(color(strength.row().tooltip().get(1)), CharacterSheetView.BONUS,
                "what each point gives");
        helper.assertValueEqual(strength.marks(), List.of(new Mark(10, true), new Mark(25, true)), "both marks filled");
        PrimaryStat vitality = primary(helper, view, GeneratedStats.VITALITY);
        // A player without a character gets no role's bonus, so Vitality lists health and regeneration only.
        helper.assertValueEqual(tooltip(vitality, 2), "+2 Health Regeneration", "10 Vitality's health regeneration");
        helper.assertValueEqual(color(vitality.row().tooltip().get(3)), CharacterSheetView.MILESTONE,
                "Vitality's 10 is reached");
        helper.assertValueEqual(color(vitality.row().tooltip().get(4)), CharacterSheetView.AHEAD,
                "Vitality's 25 is still ahead");
        helper.assertValueEqual(vitality.marks(), List.of(new Mark(10, true), new Mark(25, false)), "one mark filled");
        // Intellect is 0, so its per-point lines are left out and its first milestone follows its text.
        helper.assertValueEqual(tooltip(primary(helper, view, GeneratedStats.INTELLECT), 1),
                "At 10: +100 Max Mana, +5 Mana Regeneration", "Intellect's first milestone");

        StatsCommands.setBase(player, GeneratedStats.DEXTERITY, 25);
        CharacterSheetView dexterous = CharacterSheetView.of(player);
        helper.assertValueEqual(row(helper, dexterous, Attributes.MOVEMENT_SPEED).value(), "120%",
                "Dexterity's movement speed bonus at 10");
        helper.assertValueEqual(row(helper, dexterous, NeoForgeMod.SWIM_SPEED).value(), "120%",
                "Dexterity's swimming speed bonus at 10");
        // Dexterity's first milestone, under the sheet's names for both speeds.
        helper.assertValueEqual(tooltip(primary(helper, dexterous, GeneratedStats.DEXTERITY), 2),
                "At 10: +20% Movement Speed, +20% Swimming Speed", "Dexterity's movement and swimming speed line");

        Row firePower = row(helper, view, GeneratedStats.FIRE_POWER);
        helper.assertValueEqual(firePower.label().getString(), "Fire Power", "school stats show their names");
        helper.assertValueEqual(firePower.color(), 0xFFFF7F1F, "school stats take the school's colour");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    private static List<String> headings(CharacterSheetView view, int tab) {
        return view.statTabs().get(tab).columns().stream().map(column -> column.title().getString()).toList();
    }

    /** Every list of rows the sheet shows: the other stats, then each column of the stat tabs. */
    private static List<List<Row>> columns(CharacterSheetView view) {
        List<List<Row>> columns = new ArrayList<>();
        columns.add(view.character().other());
        view.statTabs().forEach(tab -> tab.columns().stream().map(Section::rows).forEach(columns::add));
        return columns;
    }

    private static void assertValue(GameTestHelper helper, CharacterSheetView view, Holder<Attribute> attribute,
            double value) {
        helper.assertValueEqual(row(helper, view, attribute).value(), StatNumbers.value(attribute, value),
                attribute.getRegisteredName() + " on the sheet");
    }

    private static Row row(GameTestHelper helper, CharacterSheetView view, Holder<Attribute> attribute) {
        String id = attribute.value().getDescriptionId();
        return view.rows().stream().filter(row -> row.id().equals(id)).findFirst()
                .orElseThrow(() -> helper.assertionException(Component.literal("the sheet has no row " + id)));
    }

    private static PrimaryStat primary(GameTestHelper helper, CharacterSheetView view, Holder<Attribute> stat) {
        String id = stat.value().getDescriptionId();
        return view.character().primary().stream().filter(primary -> primary.row().id().equals(id)).findFirst()
                .orElseThrow(() -> helper.assertionException(Component.literal("no primary stat " + id)));
    }

    private static String tooltip(PrimaryStat stat, int line) {
        return stat.row().tooltip().get(line).getString();
    }

    private static int color(Component line) {
        TextColor color = line.getStyle().getColor();
        return color == null ? 0 : 0xFF000000 | color.getValue();
    }
}
