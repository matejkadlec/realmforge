package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.CharacterCreation;
import io.github.matejkadlec.realmforge.character.CharacterRegistries;
import io.github.matejkadlec.realmforge.character.CharacterSheetView;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations.Bonus;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations.Sample;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations.StatRuleExpectation;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import io.github.matejkadlec.realmforge.stats.ClassRole;
import io.github.matejkadlec.realmforge.stats.StatRules;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-170: Dexterity and Vitality give different bonuses by class role (architecture 8.3). A Warrior (melee) and a Mage
 * (caster) with the same Dexterity and Vitality get the bonuses the pipeline computes for their role: the Warrior
 * attack speed and armor, the Mage cooldown reduction and magic shield instead, both the role-free ones alike, and each
 * one's character sheet lists only its own.
 */
public final class ClassRoleGameTests {
    /** A value every role-bound rule samples: both rules' last milestone. */
    private static final double POINTS = 25;

    private ClassRoleGameTests() {
    }

    @QaGameTest
    public static void aWarriorAndAMageGetTheirRolesBonuses(GameTestHelper helper) {
        ServerPlayer warrior = character(helper, "qa-role-warrior", "warrior", new Vec3(1.5, 1, 2.5));
        ServerPlayer mage = character(helper, "qa-role-mage", "mage", new Vec3(3.5, 1, 2.5));
        helper.assertValueEqual(CharacterStats.role(warrior), Optional.of(ClassRole.MELEE), "the Warrior's role");
        helper.assertValueEqual(CharacterStats.role(mage), Optional.of(ClassRole.CASTER), "the Mage's role");
        for (ServerPlayer player : List.of(warrior, mage)) {
            setValue(player, GeneratedStats.DEXTERITY, POINTS);
            setValue(player, GeneratedStats.VITALITY, POINTS);
        }

        for (String stat : List.of("realmforge:dexterity", "realmforge:vitality")) {
            StatRuleExpectation rule = rule(stat);
            assertRuleBonuses(helper, warrior, rule, sample(rule, "melee"));
            assertRuleBonuses(helper, mage, rule, sample(rule, "caster"));
        }
        assertRuleBonus(helper, warrior, Attributes.ATTACK_SPEED, "dexterity", true);
        assertRuleBonus(helper, warrior, Attributes.ARMOR, "vitality", true);
        assertRuleBonus(helper, warrior, GeneratedStats.COOLDOWN_REDUCTION, "dexterity", false);
        assertRuleBonus(helper, warrior, GeneratedStats.MAGIC_SHIELD, "vitality", false);
        assertRuleBonus(helper, mage, GeneratedStats.COOLDOWN_REDUCTION, "dexterity", true);
        assertRuleBonus(helper, mage, GeneratedStats.MAGIC_SHIELD, "vitality", true);
        assertRuleBonus(helper, mage, Attributes.ATTACK_SPEED, "dexterity", false);
        assertRuleBonus(helper, mage, Attributes.ARMOR, "vitality", false);

        assertSheetNames(helper, warrior, GeneratedStats.DEXTERITY, "Attack Speed", "Cooldown Reduction");
        assertSheetNames(helper, warrior, GeneratedStats.VITALITY, "Armor", "Magic Shield");
        assertSheetNames(helper, mage, GeneratedStats.DEXTERITY, "Cooldown Reduction", "Attack Speed");
        assertSheetNames(helper, mage, GeneratedStats.VITALITY, "Magic Shield", "Armor");
        for (ServerPlayer player : List.of(warrior, mage)) {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** A human of {@code playerClass}, in survival, as {@code RageGameTests} makes one. */
    private static ServerPlayer character(GameTestHelper helper, String name, String playerClass, Vec3 at) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, at);
        player.setGameMode(GameType.SURVIVAL);
        helper.assertValueEqual(CharacterCreation.create(player,
                        ResourceKey.create(CharacterRegistries.RACE, Realmforge.id("human")),
                        ResourceKey.create(CharacterRegistries.CLASS, Realmforge.id(playerClass))),
                CharacterCreation.Result.CREATED, "a human " + playerClass);
        return player;
    }

    /** Sets the stat's base so that its value, the race's and class's points included, is {@code value}. */
    private static void setValue(ServerPlayer player, Holder<Attribute> stat, double value) {
        AttributeInstance instance = Objects.requireNonNull(player.getAttribute(stat));
        StatsCommands.setBase(player, stat, value - (instance.getValue() - instance.getBaseValue()));
    }

    /** The player carries exactly the modifiers the pipeline computes for the rule at its role's sample. */
    private static void assertRuleBonuses(GameTestHelper helper, ServerPlayer player, StatRuleExpectation rule,
            Sample sample) {
        String where = rule.id() + " for a " + sample.role() + " at " + sample.value();
        helper.assertValueEqual(player.getAttributeValue(ContentStatGameTests.attribute(rule.stat())), sample.value(),
                where + ": the stat's value");
        for (Bonus bonus : sample.bonuses()) {
            AttributeModifier got = player.getAttribute(ContentStatGameTests.attribute(bonus.attribute()))
                    .getModifier(Identifier.parse(bonus.id()));
            helper.assertTrue(got != null, where + ": no modifier " + bonus.id() + " on " + bonus.attribute());
            helper.assertTrue(ContentStatGameTests.close(got.amount(), bonus.amount()),
                    where + ": " + bonus.id() + " amount " + bonus.amount() + " expected, got " + got.amount());
        }
        long expected = sample.bonuses().size();
        long carried = ruleModifiers(player, Identifier.parse(rule.id()).getPath()).size();
        helper.assertValueEqual(carried, expected, where + ": number of the rule's modifiers");
    }

    /** Whether the player carries a modifier of the rule {@code realmforge:<rule>} on {@code attribute}. */
    private static void assertRuleBonus(GameTestHelper helper, ServerPlayer player, Holder<Attribute> attribute,
            String rule, boolean expected) {
        AttributeInstance instance = Objects.requireNonNull(player.getAttribute(attribute));
        boolean found = instance.getModifiers().stream().anyMatch(modifier -> isRuleModifier(modifier, rule));
        helper.assertValueEqual(found, expected, player.getPlainTextName() + ": " + rule + " gives "
                + attribute.getRegisteredName());
    }

    /** The stat's tooltip on the player's own sheet (RLM-509) names {@code shown} and never {@code hidden}. */
    private static void assertSheetNames(GameTestHelper helper, ServerPlayer player, Holder<Attribute> stat,
            String shown, String hidden) {
        String id = stat.value().getDescriptionId();
        List<String> lines = CharacterSheetView.of(player).character().primary().stream()
                .filter(primary -> primary.row().id().equals(id))
                .flatMap(primary -> primary.row().tooltip().stream().skip(1))
                .map(line -> line.getString())
                .toList();
        helper.assertTrue(lines.stream().anyMatch(line -> line.endsWith(" " + shown) || line.contains(" " + shown
                + ",")), player.getPlainTextName() + "'s sheet lists " + shown + " under " + stat.getRegisteredName()
                + ": " + lines);
        helper.assertFalse(lines.stream().anyMatch(line -> line.contains(" " + hidden)),
                player.getPlainTextName() + "'s sheet hides " + hidden + ": " + lines);
    }

    private static List<AttributeModifier> ruleModifiers(ServerPlayer player, String rule) {
        return GeneratedStatExpectations.STAT_RULES.stream()
                .flatMap(expected -> expected.samples().stream())
                .flatMap(sample -> sample.bonuses().stream())
                .map(Bonus::attribute)
                .distinct()
                .map(id -> Objects.requireNonNull(player.getAttribute(ContentStatGameTests.attribute(id))))
                .flatMap(instance -> instance.getModifiers().stream())
                .filter(modifier -> isRuleModifier(modifier, rule))
                .toList();
    }

    private static boolean isRuleModifier(AttributeModifier modifier, String rule) {
        return StatRules.isStatModifier(modifier.id()) && modifier.id().getPath().endsWith("/" + rule);
    }

    private static StatRuleExpectation rule(String stat) {
        return GeneratedStatExpectations.STAT_RULES.stream()
                .filter(rule -> rule.stat().equals(stat))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no stat rule reads " + stat));
    }

    private static Sample sample(StatRuleExpectation rule, String role) {
        return rule.samples().stream()
                .filter(sample -> role.equals(sample.role()) && sample.value() == POINTS)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(rule.id() + " has no sample at " + POINTS + " for a "
                        + role));
    }
}
