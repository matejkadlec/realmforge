package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.combat.CombatConfig;
import io.github.matejkadlec.realmforge.combat.WeaponBlocks;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.combat.QaItems;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.DamageEffect;
import io.github.matejkadlec.realmforge.spell.EffectContext;
import io.github.matejkadlec.realmforge.spell.Passives;
import io.github.matejkadlec.realmforge.spell.ProjectileEffect;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.SpellTarget;
import io.github.matejkadlec.realmforge.spell.SpellText;
import io.github.matejkadlec.realmforge.spell.SpellTooltip;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-541: passive spells, learned with ranks, never cast and never on the spell bar. Improved Frostbolt raises
 * Frostbolt's damage, Two-Handed Weapon Specialization gives its Attack Damage only with a two-handed weapon in hand
 * and shortens a two-handed block's cooldown at rank 3, Shatter raises crit chance only against Frozen targets, and the
 * tooltip shows a passive's rank and the next one. Frostbolt's hit runs through its damage effect at once, with no
 * crit and no Spell Power, so the numbers are its base: 8 at rank 1 (RLM-534).
 */
public final class PassiveSpellGameTests {
    private static final double TOLERANCE = 1e-3;
    private static final double HEALTH = SpellGameTests.HEALTH;
    private static final Identifier SPECIALIZATION_MODIFIER = Passives.modifierId(
            GeneratedSpells.TWO_HANDED_WEAPON_SPECIALIZATION.identifier(), 0);

    private PassiveSpellGameTests() {
    }

    /** A passive spell never goes onto the bar, a cast of it is refused, and so is placing it there by hand. */
    @QaGameTest
    public static void aPassiveSpellIsNeverCastNorOnTheBar(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-passive-bar");
        learn(helper, player, GeneratedSpells.IMPROVED_FROSTBOLT, 1);
        helper.assertValueEqual(player.getData(SpellAttachments.SPELLBOOK)
                .barSlotOf(GeneratedSpells.IMPROVED_FROSTBOLT), -1, "learning it put it on no slot");
        helper.assertValueEqual(SpellCaster.cast(player, GeneratedSpells.IMPROVED_FROSTBOLT, (LivingEntity) null)
                .failureOrNull(), CastFailure.PASSIVE, "a passive spell is never cast");
        helper.assertFalse(Spells.placeOnBar(player, 0, Optional.of(GeneratedSpells.IMPROVED_FROSTBOLT)),
                "the spellbook cannot put it on the bar");
        logout(player);
        helper.succeed();
    }

    /** Done when of RLM-541: Improved Frostbolt rank 3 raises Frostbolt's damage by 15 %, and no other spell's. */
    @QaGameTest
    public static void improvedFrostboltRaisesFrostboltsDamage(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-passive-frostbolt");
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        assertBolt(helper, player, husk, 8, "Frostbolt rank 1 without the passive");
        learn(helper, player, GeneratedSpells.IMPROVED_FROSTBOLT, 3);
        assertBolt(helper, player, husk, 8 * 1.15, "with Improved Frostbolt rank 3: 15 % more");
        helper.assertValueEqual(Passives.damageBonus(player, GeneratedSpells.TEST_BOLT), 0.0,
                "it names Frostbolt only");
        learn(helper, player, GeneratedSpells.IMPROVED_FROSTBOLT, 1);
        assertBolt(helper, player, husk, 8 * 1.05, "rank 1: 5 % more");
        logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-541: Two-Handed Weapon Specialization gives its +12 % Attack Damage at rank 3 only while a
     * two-handed weapon (Better Combat's: the test greatsword, a spear) is in the main hand, never with a one-handed
     * sword or an empty hand; and from rank 3 a two-handed block's cooldown is 0.25 s shorter.
     */
    @QaGameTest
    public static void twoHandedWeaponSpecializationAppliesOnlyWithATwoHandedWeapon(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-passive-two-handed");
        int cooldown = CombatConfig.blockingRules().cooldownTicks();
        learn(helper, player, GeneratedSpells.TWO_HANDED_WEAPON_SPECIALIZATION, 2);
        wield(player, new ItemStack(QaItems.TEST_GREATSWORD.get()));
        assertSpecialization(helper, player, Optional.of(0.08), "rank 2 with the greatsword: +8 %");
        helper.assertValueEqual(WeaponBlocks.cooldownTicks(player, CombatConfig.blockingRules()), cooldown,
                "rank 2 leaves the block cooldown alone");

        learn(helper, player, GeneratedSpells.TWO_HANDED_WEAPON_SPECIALIZATION, 3);
        CharacterStats.recompute(player);
        assertSpecialization(helper, player, Optional.of(0.12), "rank 3 with the greatsword: +12 %");
        helper.assertValueEqual(WeaponBlocks.cooldownTicks(player, CombatConfig.blockingRules()), cooldown - 5,
                "rank 3: the block cooldown 0.25 s shorter");
        wield(player, new ItemStack(Items.IRON_SPEAR));
        assertSpecialization(helper, player, Optional.of(0.12), "a spear is two-handed too");

        wield(player, new ItemStack(Items.IRON_SWORD));
        assertSpecialization(helper, player, Optional.empty(), "a one-handed sword gives nothing");
        helper.assertValueEqual(WeaponBlocks.cooldownTicks(player, CombatConfig.blockingRules()), cooldown,
                "nor shortens the cooldown");
        wield(player, ItemStack.EMPTY);
        assertSpecialization(helper, player, Optional.empty(), "nor does an empty hand");
        logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-541: Shatter rank 3 adds 30 % crit chance against a Frozen target only. At 70 % crit chance its
     * 30 % makes every Frostbolt on a Frozen husk crit (8 × 150 %); without Frozen it adds nothing.
     */
    @QaGameTest
    public static void shatterRaisesCritChanceOnlyAgainstFrozenTargets(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-passive-shatter");
        Husk frozen = SpellGameTests.husk(helper, new BlockPos(1, 1, 2));
        Husk free = SpellGameTests.husk(helper, new BlockPos(3, 1, 2));
        frozen.addEffect(new MobEffectInstance(GeneratedEffects.FROZEN, 200));
        learn(helper, player, GeneratedSpells.SHATTER, 3);
        helper.assertTrue(close(Passives.critChanceAgainst(player, frozen), 0.3), "30 % against a Frozen husk");
        helper.assertValueEqual(Passives.critChanceAgainst(player, free), 0.0, "nothing against a free one");

        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0.7);
        for (int hit = 0; hit < 5; hit++) {
            assertBolt(helper, player, frozen, 8 * 1.5, "hit " + (hit + 1) + " on the Frozen husk crits");
        }
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        assertBolt(helper, player, free, 8, "no crit chance and no Frozen: no crit");
        logout(player);
        helper.succeed();
    }

    /**
     * A passive's tooltip: its name, rank, "Passive", its description at the rank and the next rank's below the
     * highest; an active spell's damage counts the player's passives (Frostbolt's 8 with Improved Frostbolt's 15 %).
     */
    @QaGameTest
    public static void aPassivesTooltipShowsItsRankAndTheNext(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-passive-tooltip");
        helper.assertValueEqual(tooltip(helper, player, GeneratedSpells.IMPROVED_FROSTBOLT, 1), List.of(
                "Improved Frostbolt", "Rank 1 of 3", "Passive", "Your Frostbolt deals 5% more damage.", "Next rank:",
                "Your Frostbolt deals 10% more damage."), "rank 1 and the next");
        helper.assertValueEqual(tooltip(helper, player, GeneratedSpells.IMPROVED_FROSTBOLT, 3), List.of(
                "Improved Frostbolt", "Rank 3 of 3", "Passive", "Your Frostbolt deals 15% more damage."),
                "the highest rank has no next");
        helper.assertValueEqual(tooltip(helper, player, GeneratedSpells.TWO_HANDED_WEAPON_SPECIALIZATION, 1)
                        .subList(3, 4), List.of("+4% damage with a two-handed weapon in your main hand. From rank 3, "
                        + "its block cooldown is 0.25 sec shorter."), "its modifier and block cooldown");
        Spell frostbolt = spell(helper, GeneratedSpells.FROSTBOLT).value();
        helper.assertValueEqual(SpellText.values(frostbolt, 1, SpellText.statsOf(player),
                SpellText.schoolsOf(player.registryAccess()), 0.15).get("damage"), "9.2",
                "Frostbolt's 8 with 15 % from passives");
        logout(player);
        helper.succeed();
    }

    /** Frostbolt's hit at rank 1 on {@code husk}, run through its damage effect at once: it deals {@code expected}. */
    private static void assertBolt(GameTestHelper helper, ServerPlayer player, Husk husk, double expected,
            String what) {
        Holder.Reference<Spell> frostbolt = spell(helper, GeneratedSpells.FROSTBOLT);
        DamageEffect bolt = (DamageEffect) ((ProjectileEffect) frostbolt.value().effects().getFirst()).effects()
                .getFirst();
        husk.setHealth((float) HEALTH);
        bolt.applyTo(EffectContext.of(helper.getLevel(), player, frostbolt, 1, Optional.empty()), husk,
                SpellTarget.of(player, husk));
        double dealt = HEALTH - husk.getHealth();
        helper.assertTrue(close(dealt, expected), what + ": " + expected + " expected, dealt " + dealt);
    }

    /** The specialization's Attack Damage modifier after a recompute: its amount, or none. */
    private static void assertSpecialization(GameTestHelper helper, ServerPlayer player, Optional<Double> amount,
            String what) {
        CharacterStats.recompute(player);
        AttributeModifier modifier = player.getAttribute(Attributes.ATTACK_DAMAGE)
                .getModifier(SPECIALIZATION_MODIFIER);
        helper.assertValueEqual(Optional.ofNullable(modifier).map(AttributeModifier::amount), amount, what);
        if (modifier != null) {
            helper.assertValueEqual(modifier.operation(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                    what + ": it adds its percent");
        }
    }

    private static void wield(ServerPlayer player, ItemStack stack) {
        player.setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    private static List<String> tooltip(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> key,
            int level) {
        return SpellTooltip.lines(spell(helper, key).value(), level, SpellText.statsOf(player),
                SpellText.schoolsOf(helper.getLevel().registryAccess())).stream().map(Component::getString).toList();
    }

    private static Holder.Reference<Spell> spell(GameTestHelper helper, ResourceKey<Spell> key) {
        return Spells.get(helper.getLevel().registryAccess(), key).orElseThrow();
    }

    private static void learn(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> key, int level) {
        Spells.learn(player, spell(helper, key), level);
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, new Vec3(2.5, 1, 0.5));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    private static boolean close(double got, double want) {
        return Math.abs(got - want) <= TOLERANCE;
    }
}
