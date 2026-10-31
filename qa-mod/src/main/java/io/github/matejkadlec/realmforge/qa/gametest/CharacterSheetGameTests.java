package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.character.CharacterSheetView;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Mark;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.PrimaryStat;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Row;
import io.github.matejkadlec.realmforge.character.StatNumbers;
import io.github.matejkadlec.realmforge.combat.CombatAttachments;
import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.DamageKind;
import io.github.matejkadlec.realmforge.combat.EnchantmentProtection;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * RLM-136, RLM-509, RLM-235, RLM-233: the character sheet shows the player's name, level and attributes on three tabs
 * (Matěj's layout of 2026-10-31): Character with the primary stats (what they give in their tooltips) and the other
 * stats in two columns, Stamina right below Luck (RLM-170); Offense with Physical, Critical Hits under it, and Magic;
 * Defense with Mitigation, Health and Resistances, a row per kind of hit from resistance stats and protection
 * enchantments together. No stat shows twice; every stat of ours shows, the mob-only resistances never, Apothic
 * Attributes' ranged stats and Healing Received do, and every row has its tooltip text. Values with one decimal
 * (RLM-530). The sheet's view model is common code ({@link CharacterSheetView}), so the test builds it for a fake
 * player on the server after {@code /qa stats} set Strength, and compares it with the attributes the stat rules
 * produced.
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
        helper.assertValueEqual(headings(view, 0), List.of(List.of("Physical", "Critical Hits"), List.of("Magic")),
                "Offense's columns (Matěj, 2026-10-31)");
        helper.assertValueEqual(headings(view, 1), List.of(List.of("Mitigation", "Health"), List.of("Resistances")),
                "Defense's columns: two, as on every stat tab");
        List<String> physical = ids(view.statTabs().getFirst().columns().getFirst().sections().getFirst().rows());
        List<String> physicalOrder = ids(Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED, GeneratedStats.ARMOR_PIERCE,
                GeneratedStats.ARMOR_SHRED);
        for (String path : List.of("arrow_damage", "draw_speed", "arrow_velocity")) {
            physicalOrder.add(BuiltInRegistries.ATTRIBUTE.get(Identifier.fromNamespaceAndPath("apothic_attributes",
                    path)).orElseThrow().value().getDescriptionId());
        }
        helper.assertValueEqual(physical, physicalOrder, "Physical's order (Matěj, 2026-10-31)");
        helper.assertValueEqual(ids(view.character().other().getFirst()), ids(Attributes.LUCK, GeneratedStats.STAMINA,
                Attributes.ENTITY_INTERACTION_RANGE, Attributes.BLOCK_INTERACTION_RANGE), "the other stats' left column");
        helper.assertValueEqual(ids(view.character().other().get(1)), ids(Attributes.BLOCK_BREAK_SPEED,
                Attributes.MOVEMENT_SPEED, NeoForgeMod.SWIM_SPEED, GeneratedStats.XP_MULTIPLIER),
                "the other stats' right column");
        helper.assertTrue(view.character().identity().parts().isEmpty(), "no character: no race or class");
        helper.assertTrue(view.character().xp().isEmpty(), "no character: no XP bar");

        // Every stat of ours shows once, the mob-only resistances never, and the damage type resistances in the
        // Resistances rows instead of their own; no row shows twice (Matěj, 2026-10-31).
        Set<String> hidden = new HashSet<>(Set.of(GeneratedStats.SPELL_RESISTANCE.value().getDescriptionId()));
        server.registryAccess().lookupOrThrow(CombatRegistries.SPELL_SCHOOL).listElements().forEach(school ->
                school.value().resistance().ifPresent(stat -> hidden.add(stat.value().getDescriptionId())));
        GeneratedStats.DAMAGE_RESISTANCES.forEach(resistance -> hidden.add(resistance.stat().value()
                .getDescriptionId()));
        for (Holder<Attribute> stat : GeneratedStats.ALL) {
            String id = stat.value().getDescriptionId();
            long shown = view.rows().stream().filter(row -> row.id().equals(id)).count();
            if (hidden.contains(id)) {
                helper.assertValueEqual(shown, 0L, stat.getRegisteredName() + " has no row of its own");
            } else {
                helper.assertValueEqual(shown, 1L, stat.getRegisteredName() + " shows once");
                assertValue(helper, view, stat, player.getAttributeValue(stat));
            }
        }
        List<String> allIds = view.rows().stream().map(Row::id).toList();
        helper.assertValueEqual(new HashSet<>(allIds).size(), allIds.size(), "no row shows twice: " + allIds);
        helper.assertValueEqual(row(helper, view, Attributes.KNOCKBACK_RESISTANCE).value(), "0%",
                "knockback resistance, a share");
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
        List<Row> other = view.character().other().getFirst();
        List<String> otherIds = other.stream().map(Row::id).toList();
        int luck = otherIds.indexOf(Attributes.LUCK.value().getDescriptionId());
        helper.assertTrue(luck >= 0, "the other stats' left column lists Luck: " + otherIds);
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

    /**
     * RLM-235, RLM-233 (Matěj, 2026-10-31): a Resistances row per kind of hit shows what the player's resistance stats
     * and protection enchantments block of it together, 1 − (1 − stats) × (1 − enchantments), the enchantments' part
     * from the game's own protection rules; the tooltip splits the two parts, and Fire and Magic have enchantments
     * only. The server keeps the synced copy the client reads current.
     */
    @QaGameTest
    public static void resistancesCombineStatsAndEnchantments(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-sheet-resist");
        player.doTick();
        StatsCommands.setBase(player, GeneratedStats.PHYSICAL_RESISTANCE, 0.5);
        CharacterSheetView bare = CharacterSheetView.of(player);
        List<Row> rows = resistances(bare);
        helper.assertValueEqual(rows.stream().map(row -> row.label().getString()).toList(),
                List.of("Physical", "Projectile", "Explosion", "Fall", "Fire", "Magic"), "a row per kind of hit");
        helper.assertValueEqual(rows.stream().map(Row::value).toList(), List.of("50%", "50%", "0%", "0%", "0%", "0%"),
                "no armor: 50% Physical Resistance counts for melee hits and projectiles");
        helper.assertValueEqual(tooltip(rows.getFirst()), List.of("From stats: 50%", "From enchantments: 0%"),
                "Physical's split");
        helper.assertValueEqual(tooltip(rows.getLast()), List.of("From enchantments: 0%"), "Magic: enchantments only");
        helper.assertValueEqual(tooltip(rows.get(4)), List.of("From enchantments: 0%"), "Fire: enchantments only");

        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        chest.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION),
                4);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        StatsCommands.setBase(player, GeneratedStats.PROJECTILE_RESISTANCE, 0.5);
        EnchantmentProtection protection = EnchantmentProtection.compute(helper.getLevel(), player);
        for (DamageKind kind : DamageKind.values()) {
            helper.assertTrue(protection.share(kind) > 0, "Protection IV counts against " + kind);
        }
        List<Row> enchanted = resistances(CharacterSheetView.of(player));
        double physical = protection.share(DamageKind.PHYSICAL);
        helper.assertValueEqual(enchanted.getFirst().value(), StatNumbers.share(1 - 0.5 * (1 - physical)),
                "Physical: 1 - (1 - 50%) x (1 - Protection's share)");
        helper.assertValueEqual(tooltip(enchanted.getFirst()), List.of("From stats: 50%",
                "From enchantments: " + StatNumbers.share(physical)), "the split");
        helper.assertValueEqual(tooltip(enchanted.get(1)).getFirst(), "From stats: 75%",
                "Projectile: 50% Physical and 50% Projectile multiply to 75%, the cap");
        helper.assertValueEqual(enchanted.getLast().value(), StatNumbers.share(protection.share(DamageKind.MAGIC)),
                "Magic: Protection alone");

        // The client's sheet reads the copy the server keeps current every half second.
        for (int tick = 0; tick < 10; tick++) {
            player.doTick();
        }
        helper.assertValueEqual(player.getData(CombatAttachments.ENCHANTMENT_PROTECTION), protection,
                "the synced shares");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    private static List<Row> resistances(CharacterSheetView view) {
        return view.rows().stream().filter(row -> row.id().startsWith("resistance/")).toList();
    }

    /** A row's tooltip after its description. */
    private static List<String> tooltip(Row row) {
        return row.tooltip().stream().skip(1).map(Component::getString).toList();
    }

    private static List<List<String>> headings(CharacterSheetView view, int tab) {
        return view.statTabs().get(tab).columns().stream().map(column -> column.sections().stream()
                .map(section -> section.title().getString()).toList()).toList();
    }

    private static List<String> ids(List<Row> rows) {
        return rows.stream().map(Row::id).toList();
    }

    @SafeVarargs
    private static List<String> ids(Holder<Attribute>... attributes) {
        List<String> ids = new ArrayList<>();
        for (Holder<Attribute> attribute : attributes) {
            ids.add(attribute.value().getDescriptionId());
        }
        return ids;
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
