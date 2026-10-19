package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.compat.apothic_attributes.ApothicAttributesCompat;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-271: Apothic Attributes, which the dev runs load as the pack does, never crits or dodges next to our crit and
 * dodge (the core mod's {@code compat/apothic_attributes}, architecture 19.4). The attackers carry the modifiers an
 * affix or gem of Apotheosis would give them (+1,000 % crit chance, +500 % crit damage, +100 % dodge chance), so a
 * test fails as soon as either mechanism could fire.
 */
public final class ApothicAttributesGameTests {
    /** Where a player stands: the platform's north edge, within reach of the target in its middle. */
    private static final Vec3 EDGE = new Vec3(2.5, 1, 0.5);
    private static final int HITS = 20;

    private ApothicAttributesGameTests() {
    }

    /** Every entity type with attributes starts at 0 % crit chance and a crit damage of 1.0, which adds nothing. */
    @QaGameTest
    @SuppressWarnings("unchecked")
    public static void everyEntityTypeStartsWithoutItsCrit(GameTestHelper helper) {
        Holder<Attribute> critChance = attribute(ApothicAttributesCompat.CRIT_CHANCE);
        Holder<Attribute> critDamage = attribute(ApothicAttributesCompat.CRIT_DAMAGE);
        int types = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!DefaultAttributes.hasSupplier(type)) {
                continue;
            }
            AttributeSupplier supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
            helper.assertTrue(supplier.hasAttribute(critChance) && supplier.hasAttribute(critDamage),
                    type + " has Apothic Attributes' crit attributes");
            helper.assertValueEqual(supplier.getBaseValue(critChance), ApothicAttributesCompat.CRIT_CHANCE_BASE,
                    type + "'s base crit chance");
            helper.assertValueEqual(supplier.getBaseValue(critDamage), ApothicAttributesCompat.CRIT_DAMAGE_NEUTRAL,
                    type + "'s base crit damage");
            types++;
        }
        helper.assertTrue(types > 50, "the vanilla mobs and the player were checked: " + types + " types");
        helper.succeed();
    }

    /** Done when of RLM-271: no mob crit comes from Apothic Attributes, even with crit modifiers on the attacker. */
    @QaGameTest
    public static void aMobsCritModifiersNeverCritByApothicAttributes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = CombatGameTests.attacker(helper);
        Pillager mob = CombatGameTests.target(helper);
        addCritModifiers(zombie);
        helper.assertValueEqual(zombie.getAttributeValue(attribute(ApothicAttributesCompat.CRIT_DAMAGE)),
                ApothicAttributesCompat.CRIT_DAMAGE_NEUTRAL, "the zombie's crit damage stays capped at 1.0");

        for (int i = 0; i < HITS; i++) {
            CombatGameTests.settle(mob);
            CombatGameTests.assertDealt(helper, mob, level.damageSources().mobAttack(zombie), 6, 6,
                    "melee hit " + (i + 1) + " at +1,000 % of its crit chance");
        }
        helper.succeed();
    }

    /** Done when of RLM-271: a spell hit crits only by our roll, whatever the caster's Apothic Attributes crit. */
    @QaGameTest
    public static void aSpellHitCritsOnlyByOurRoll(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-apothic-spell-crit");
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0);
        StatsCommands.setBase(player, GeneratedStats.FIRE_POWER, 0);
        addCritModifiers(player);
        Pillager mob = CombatGameTests.target(helper);

        for (int i = 0; i < HITS; i++) {
            CombatGameTests.settle(mob);
            CombatGameTests.assertDealt(helper, mob,
                    CombatGameTests.school(helper, GeneratedSpellSchools.FIRE, player), 10, 10,
                    "fire hit " + (i + 1) + " at 0 % of our crit chance");
        }
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 1);
        CombatGameTests.settle(mob);
        CombatGameTests.assertDealt(helper, mob, CombatGameTests.school(helper, GeneratedSpellSchools.FIRE, player),
                10, 10 * 1.5, "at 100 % of our crit chance, our crit with the base 50 % crit damage");
        logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-271: Apothic Attributes' dodge never fires, against a player's hits within reach, which it
     * would dodge at its +100 % dodge chance.
     */
    @QaGameTest
    public static void apothicAttributesDodgeNeverFires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "qa-apothic-dodge");
        Pillager mob = CombatGameTests.target(helper);
        Holder<Attribute> dodgeChance = attribute(ApothicAttributesCompat.DODGE_CHANCE);
        mob.getAttribute(dodgeChance).addPermanentModifier(modifier("dodge", 1));
        helper.assertValueEqual(mob.getAttributeValue(dodgeChance), ApothicAttributesCompat.DODGE_CHANCE_MAX,
                "the pillager's dodge chance stays capped at 0");
        helper.assertTrue(player.distanceTo(mob) < 3, "the pillager is within the player's reach");

        for (int i = 0; i < HITS; i++) {
            CombatGameTests.settle(mob);
            CombatGameTests.assertDealt(helper, mob, level.damageSources().playerAttack(player), 5, 5,
                    "hit " + (i + 1) + " on a pillager at +100 % of its dodge chance");
        }
        logout(player);
        helper.succeed();
    }

    private static void addCritModifiers(LivingEntity attacker) {
        attacker.getAttribute(attribute(ApothicAttributesCompat.CRIT_CHANCE))
                .addPermanentModifier(modifier("crit_chance", 10));
        attacker.getAttribute(attribute(ApothicAttributesCompat.CRIT_DAMAGE))
                .addPermanentModifier(modifier("crit_damage", 5));
    }

    private static AttributeModifier modifier(String name, double amount) {
        return new AttributeModifier(Identifier.fromNamespaceAndPath(RealmforgeQa.MOD_ID, "gametest/" + name), amount,
                AttributeModifier.Operation.ADD_VALUE);
    }

    private static Holder<Attribute> attribute(Identifier id) {
        return BuiltInRegistries.ATTRIBUTE.get(id).orElseThrow();
    }

    /** A fake player in survival mode on the platform's edge, at 0 % of our crit chance. */
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, EDGE);
        player.setGameMode(GameType.SURVIVAL);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        return player;
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
