package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations.Bonus;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations.Sample;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedStatExpectations.StatRuleExpectation;
import io.github.matejkadlec.realmforge.stats.AttributeCaps;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import io.github.matejkadlec.realmforge.stats.StatRules;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * F4: the stat rules reach players (architecture 8.2, 8.3). Primary stats change the attributes their rule names,
 * and bonus max health survives a relog, a respawn after death and leaving the End. Expected values come from the
 * generated expectations ({@link GeneratedStatExpectations}), so the tests follow the content. F4c: attributes go
 * past vanilla's caps (8.4).
 *
 * <p>Players log in on their test's platform ({@link QaPlayers#loginOnPlatform}), and a respawned one is moved back
 * onto it before it ticks. Their connection never ticks them, so a test plays a client tick with
 * {@code ServerPlayer#doTick}, the call the network handler makes once per tick; the server never simulates a
 * player's movement, so the players do not fall.
 */
public final class StatsGameTests {
    private StatsGameTests() {
    }

    /** Done when of F4: Strength raises attack damage (and gives its milestone) as its stat rule says. */
    @QaGameTest
    public static void strengthRaisesAttackDamageAsConfigured(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-stats-strength");
        tick(player, 1);
        double before = player.getAttributeValue(Attributes.ATTACK_DAMAGE);

        Sample sample = highestSample(rule("realmforge:strength"));
        setBase(player, GeneratedStats.STRENGTH, sample.value());
        tick(player, CharacterStats.CHECK_INTERVAL);
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) > before,
                "attack damage rose with " + sample.value() + " Strength");
        assertAttributes(helper, player, sample, "with " + sample.value() + " Strength");

        setBase(player, GeneratedStats.STRENGTH, 0);
        tick(player, CharacterStats.CHECK_INTERVAL);
        helper.assertValueEqual(player.getAttributeValue(Attributes.ATTACK_DAMAGE), before, "attack damage at 0");
        helper.assertFalse(hasStatModifiers(player), "no stat modifier is left at 0 Strength");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-170: every value is {@code (default + flat) × (1 + sum of percents)} (architecture 8.2).
     * Matěj's example: a player's 1 attack damage, +2 and +1 from two sources and +15% and +10% from two more give
     * {@code (1 + 2 + 1) × 1.25 = 5}; Strength's flat and percent bonuses join the same two sums.
     */
    @QaGameTest
    public static void percentBonusesMultiplyTheFlatSum(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-stats-formula");
        tick(player, 1);
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        helper.assertValueEqual(damage.getValue(), 1.0, "a player's attack damage with nothing on it");
        damage.addTransientModifier(modifier("formula/flat_2", 2, AttributeModifier.Operation.ADD_VALUE));
        damage.addTransientModifier(modifier("formula/flat_1", 1, AttributeModifier.Operation.ADD_VALUE));
        damage.addTransientModifier(modifier("formula/percent_15", 0.15,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        damage.addTransientModifier(modifier("formula/percent_10", 0.1,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        helper.assertTrue(ContentStatGameTests.close(damage.getValue(), 5), "(1 + 2 + 1) × (1 + 15% + 10%) is 5, got "
                + damage.getValue());

        Sample sample = highestSample(rule("realmforge:strength"));
        StatsCommands.setBase(player, GeneratedStats.STRENGTH, sample.value());
        double flat = 1 + 2 + 1;
        double percent = 0.15 + 0.1;
        int flatBonuses = 0;
        int percentBonuses = 0;
        for (Bonus bonus : sample.bonuses()) {
            if (!bonus.attribute().equals("minecraft:attack_damage")) {
                continue;
            }
            helper.assertFalse(bonus.operation().equals("add_multiplied_total"), "Strength never multiplies the total");
            if (bonus.operation().equals("add_value")) {
                flat += bonus.amount();
                flatBonuses++;
            } else {
                percent += bonus.amount();
                percentBonuses++;
            }
        }
        helper.assertTrue(flatBonuses > 0 && percentBonuses > 0, "Strength at " + sample.value()
                + " gives attack damage both flat and in percent");
        double want = flat * (1 + percent);
        helper.assertTrue(ContentStatGameTests.close(damage.getValue(), want), "with " + sample.value()
                + " Strength: (" + flat + ") × (1 + " + percent + ") = " + want + " expected, got " + damage.getValue());
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** Done when of F4: bonus max health and the health above the base maximum come back after a relog. */
    @QaGameTest
    public static void bonusMaxHealthSurvivesRelog(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-stats-relog");
        ServerPlayer first = QaPlayers.loginOnPlatform(helper, profile);
        float max = withVitality(helper, first);
        first.setHealth(max);
        server.getPlayerList().remove(first);

        ServerPlayer again = QaPlayers.login(server, profile);
        helper.assertValueEqual(again.getMaxHealth(), max, "max health after the relog, before its first tick");
        helper.assertValueEqual(again.getHealth(), max, "health after the relog, before its first tick");
        tick(again, 1);
        helper.assertValueEqual(again.getMaxHealth(), max, "max health after the first tick");
        helper.assertValueEqual(again.getHealth(), max, "health after the first tick");
        server.getPlayerList().remove(again);
        helper.succeed();
    }

    /** Done when of F4: after death, the respawned player gets its bonus max health back and full health. */
    @QaGameTest
    public static void bonusMaxHealthSurvivesRespawn(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer dead = QaPlayers.loginOnPlatform(helper, "qa-stats-respawn");
        float max = withVitality(helper, dead);

        ServerPlayer respawned =
                QaPlayers.onPlatform(helper, server.getPlayerList().respawn(dead, false, Entity.RemovalReason.KILLED));
        helper.assertTrue(respawned != dead, "a respawn after death makes a new player entity");
        helper.assertValueEqual(respawned.getAttributeBaseValue(GeneratedStats.VITALITY),
                dead.getAttributeBaseValue(GeneratedStats.VITALITY), "vanilla copies the base Vitality");
        tick(respawned, 1);
        helper.assertValueEqual(respawned.getMaxHealth(), max, "max health after the respawn's first tick");
        helper.assertValueEqual(respawned.getHealth(), max, "health is refilled to the new maximum");
        server.getPlayerList().remove(respawned);
        helper.succeed();
    }

    /**
     * Leaving the End is no death: the player keeps the health it had. Vanilla copies the permanent modifiers (ours,
     * effects') and the health, but equipment modifiers are transient and return only on the first tick, so a max
     * health bonus from gear (another mod's: ours never adds max health) is missing when vanilla clamps the health.
     * The first-tick recompute restores it.
     */
    @QaGameTest
    public static void leavingTheEndKeepsHealthAboveTheBaseMaximum(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer leaving = QaPlayers.loginOnPlatform(helper, "qa-stats-end");
        float withoutGear = withVitality(helper, leaving);
        leaving.setItemSlot(EquipmentSlot.MAINHAND, stick(Attributes.MAX_HEALTH, 10));
        tick(leaving, 1);
        float max = leaving.getMaxHealth();
        helper.assertTrue(max > withoutGear, "the gear raised max health");
        leaving.setHealth(max);

        ServerPlayer back = QaPlayers.onPlatform(helper,
                server.getPlayerList().respawn(leaving, true, Entity.RemovalReason.CHANGED_DIMENSION));
        helper.assertTrue(back.getHealth() < max, "vanilla clamps the health while the gear is missing");
        tick(back, 1);
        helper.assertValueEqual(back.getMaxHealth(), max, "max health after leaving the End");
        helper.assertValueEqual(back.getHealth(), max, "health after leaving the End");
        server.getPlayerList().remove(back);
        helper.succeed();
    }

    /**
     * Architecture 8.2: equipment applies its modifiers only on the player's first tick, so a stat from gear counts
     * from the tick it is equipped, and after a relog the saved bonus waits for that tick instead of being recomputed
     * without the gear.
     */
    @QaGameTest
    public static void strengthFromGearKeepsItsBonusAcrossRelogs(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-stats-gear");
        StatRuleExpectation strength = rule("realmforge:strength");
        Sample sample = highestSample(strength);

        ServerPlayer first = QaPlayers.loginOnPlatform(helper, profile);
        tick(first, 1);
        first.setItemSlot(EquipmentSlot.MAINHAND, stick(GeneratedStats.STRENGTH, sample.value()));
        tick(first, 1);
        helper.assertValueEqual(first.getAttributeValue(GeneratedStats.STRENGTH), sample.value(), "Strength from gear");
        assertAttributes(helper, first, sample, "right after equipping the gear");
        server.getPlayerList().remove(first);

        ServerPlayer again = QaPlayers.login(server, profile);
        helper.assertValueEqual(again.getAttributeValue(GeneratedStats.STRENGTH), 0.0,
                "the gear's Strength waits for the first tick");
        assertAttributes(helper, again, sample, "after the relog, before its first tick (saved)");
        tick(again, 1);
        helper.assertValueEqual(again.getAttributeValue(GeneratedStats.STRENGTH), sample.value(),
                "Strength from gear after the first tick");
        assertAttributes(helper, again, sample, "after the relog's first tick");
        server.getPlayerList().remove(again);
        helper.succeed();
    }

    /**
     * Done when of F4c (architecture 8.4): attribute modifiers take max health, armor, armor toughness and attack
     * damage past vanilla's caps (1024, 30, 20 and 2048), the values and the health come back after a relog, and the
     * raised caps still clamp.
     */
    @QaGameTest
    public static void attributesGoPastVanillasCaps(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-stats-caps");
        ServerPlayer first = QaPlayers.loginOnPlatform(helper, profile);
        tick(first, 1);
        double health = first.getAttributeBaseValue(Attributes.MAX_HEALTH) + 5000;
        double damage = first.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + 5000;
        addModifier(first, Attributes.MAX_HEALTH, 5000);
        addModifier(first, Attributes.ARMOR, 100);
        addModifier(first, Attributes.ARMOR_TOUGHNESS, 50);
        addModifier(first, Attributes.ATTACK_DAMAGE, 5000);
        first.setHealth(first.getMaxHealth());
        assertPastVanillasCaps(helper, first, health, damage, "with the modifiers");
        server.getPlayerList().remove(first);

        ServerPlayer again = QaPlayers.login(server, profile);
        assertPastVanillasCaps(helper, again, health, damage, "after the relog, before its first tick");
        tick(again, CharacterStats.CHECK_INTERVAL);
        assertPastVanillasCaps(helper, again, health, damage, "after the relog's stat check");

        addModifier(again, Attributes.ARMOR, 2 * AttributeCaps.max(Attributes.ARMOR));
        helper.assertValueEqual(again.getAttributeValue(Attributes.ARMOR), AttributeCaps.max(Attributes.ARMOR),
                "armor beyond the raised cap");
        server.getPlayerList().remove(again);
        helper.succeed();
    }

    private static void assertPastVanillasCaps(GameTestHelper helper, ServerPlayer player, double health,
            double damage, String when) {
        helper.assertTrue(health > 1024 && damage > 2048, "the expected values are past vanilla's caps");
        helper.assertValueEqual(player.getMaxHealth(), (float) health, "max health " + when);
        helper.assertValueEqual(player.getHealth(), (float) health, "health " + when);
        helper.assertValueEqual(player.getArmorValue(), 100, "armor " + when);
        helper.assertValueEqual(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 50.0, "armor toughness " + when);
        helper.assertValueEqual(player.getAttributeValue(Attributes.ATTACK_DAMAGE), damage, "attack damage " + when);
    }

    /** Adds {@code amount} to the attribute as a saved modifier of the QA mod, replacing an earlier one. */
    private static void addModifier(ServerPlayer player, Holder<Attribute> attribute, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            throw new IllegalStateException("players lack " + attribute.getRegisteredName());
        }
        instance.addOrReplacePermanentModifier(new AttributeModifier(RealmforgeQa.id("attribute_caps"), amount,
                AttributeModifier.Operation.ADD_VALUE));
    }

    /** Gives the player the highest Vitality the expectations sample, waits for the recompute, returns max health. */
    private static float withVitality(GameTestHelper helper, ServerPlayer player) {
        tick(player, 1);
        Sample sample = highestSample(rule("realmforge:vitality"));
        setBase(player, GeneratedStats.VITALITY, sample.value());
        tick(player, CharacterStats.CHECK_INTERVAL);
        assertAttributes(helper, player, sample, "with " + sample.value() + " Vitality");
        float max = player.getMaxHealth();
        helper.assertTrue(max > 20, "Vitality raised max health above the base 20: " + max);
        player.setHealth(max);
        return max;
    }

    /** Every attribute the sample's bonuses touch has the value vanilla's formula gives for them. */
    private static void assertAttributes(GameTestHelper helper, ServerPlayer player, Sample sample, String when) {
        Set<String> attributes = new LinkedHashSet<>();
        sample.bonuses().forEach(bonus -> attributes.add(bonus.attribute()));
        helper.assertFalse(attributes.isEmpty(), "the sample at " + sample.value() + " gives bonuses");
        for (String id : attributes) {
            Holder<Attribute> attribute = ContentStatGameTests.attribute(id);
            double want = expectedValue(player.getAttributeBaseValue(attribute), sample.bonuses(), id);
            double got = player.getAttributeValue(attribute);
            helper.assertTrue(ContentStatGameTests.close(got, want), id + " " + when + ": " + want + " expected, got "
                    + got);
        }
    }

    /** {@code AttributeInstance#calculateValue} for an attribute whose only modifiers are {@code bonuses}. */
    private static double expectedValue(double base, List<Bonus> bonuses, String attribute) {
        double value = base;
        for (Bonus bonus : bonuses) {
            if (bonus.attribute().equals(attribute) && bonus.operation().equals("add_value")) {
                value += bonus.amount();
            }
        }
        double result = value;
        for (Bonus bonus : bonuses) {
            if (bonus.attribute().equals(attribute) && bonus.operation().equals("add_multiplied_base")) {
                result += value * bonus.amount();
            }
        }
        for (Bonus bonus : bonuses) {
            if (bonus.attribute().equals(attribute) && bonus.operation().equals("add_multiplied_total")) {
                result *= 1 + bonus.amount();
            }
        }
        return result;
    }

    private static AttributeModifier modifier(String path, double amount, AttributeModifier.Operation operation) {
        return new AttributeModifier(RealmforgeQa.id(path), amount, operation);
    }

    private static StatRuleExpectation rule(String stat) {
        return GeneratedStatExpectations.STAT_RULES.stream()
                .filter(rule -> rule.stat().equals(stat))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no stat rule reads " + stat));
    }

    /**
     * The sample with the most points for a player without a character, as the tests' players are: past the rule's
     * milestones and caps. Samples come role by role, the role-free ones first, each in ascending points.
     */
    private static Sample highestSample(StatRuleExpectation rule) {
        return rule.samples().stream()
                .filter(sample -> sample.role() == null)
                .reduce((lower, higher) -> higher)
                .orElseThrow(() -> new IllegalStateException(rule.id() + " has no role-free sample"));
    }

    private static void setBase(ServerPlayer player, Holder<Attribute> stat, double value) {
        AttributeInstance instance = player.getAttribute(stat);
        if (instance == null) {
            throw new IllegalStateException("players lack " + stat.getRegisteredName());
        }
        instance.setBaseValue(value);
    }

    private static boolean hasStatModifiers(ServerPlayer player) {
        return player.getAttributes().pack().stream()
                .flatMap(packed -> packed.modifiers().stream())
                .anyMatch(modifier -> StatRules.isStatModifier(modifier.id()));
    }

    /** A stick that adds {@code amount} to {@code attribute} in the main hand, like gear with a fixed stat. */
    static ItemStack stick(Holder<Attribute> attribute, double amount) {
        ItemStack stick = new ItemStack(Items.STICK);
        stick.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(attribute, new AttributeModifier(Realmforge.id("qa/stick"), amount,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build());
        return stick;
    }

    static void tick(ServerPlayer player, int ticks) {
        for (int i = 0; i < ticks; i++) {
            player.doTick();
        }
    }
}
