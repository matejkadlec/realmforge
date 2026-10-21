package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.effect.EffectSource;
import io.github.matejkadlec.realmforge.effect.Effects;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * C3c (RLM-26): our status effects from {@code content/effects/} (architecture 11.4). {@code test_ignite} sets
 * {@code test_burning} on a husk for 4 s, 2 fire damage a second; {@code test_regrowth} heals 2 arcane a second;
 * {@code test_sapped} takes 20 % movement speed and 10 % spell power. The ticks come at the end of every interval, so
 * the checks wait real ticks with a few ticks of slack around each tick.
 */
public final class EffectGameTests {
    private static final double TOLERANCE = 1e-4;
    private static final double HEALTH = SpellGameTests.HEALTH;

    private EffectGameTests() {
    }

    /**
     * Damage over time ticks at the end of each interval, raised by the caster's spell power and fire power as they are
     * when it ticks, and the effect remembers its caster and spell until it ends.
     */
    @QaGameTest
    public static void damageOverTimeTicksWithTheCastersStats(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-effect-dot");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_IGNITE);
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0.5);
        StatsCommands.setBase(player, GeneratedStats.FIRE_POWER, 0.2);
        CastResult result = SpellCaster.cast(player, GeneratedSpells.TEST_IGNITE, husk);
        helper.assertTrue(result.success(), "test_ignite was refused: " + result.failure());
        Optional<EffectSource> source = Effects.source(husk, GeneratedEffects.TEST_BURNING);
        helper.assertValueEqual(source.map(EffectSource::caster), Optional.of(player.getUUID()), "its caster");
        helper.assertValueEqual(source.flatMap(EffectSource::spell),
                Optional.of(GeneratedSpells.TEST_IGNITE.identifier()), "its spell");
        helper.assertValueEqual(source.flatMap(EffectSource::procOrigin), Optional.empty(), "no proc applied it");
        helper.runAfterDelay(15, () -> assertHealth(helper, husk, HEALTH, "no tick before its first interval"));
        helper.runAfterDelay(25, () -> assertHealth(helper, husk, HEALTH - 2 * 1.7,
                "the first tick: 2 × (1 + 50 % spell power + 20 % fire power)"));
        helper.runAfterDelay(90, () -> {
            assertHealth(helper, husk, HEALTH - 4 * 2 * 1.7, "4 s, 4 ticks");
            helper.assertFalse(husk.hasEffect(GeneratedEffects.TEST_BURNING), "the effect ran out");
            helper.assertValueEqual(Effects.source(husk, GeneratedEffects.TEST_BURNING), Optional.empty(),
                    "an ended effect has no source");
            logout(player);
            helper.succeed();
        });
    }

    /** A caster that left takes its stats with it: the ticks go on at the bare amount. */
    @QaGameTest
    public static void damageOverTimeWithoutItsCasterDealsTheBareAmount(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-effect-dot-gone");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 1);
        apply(husk, GeneratedEffects.TEST_BURNING, 40, 1, player);
        logout(player);
        helper.runAfterDelay(45, () -> {
            assertHealth(helper, husk, HEALTH - 2 * 2 * 2, "2 ticks of 2 × 2 (amplifier 1), without spell power");
            helper.succeed();
        });
    }

    /** Healing over time heals at the end of each interval, raised by the caster's spell power. */
    @QaGameTest
    public static void healingOverTimeHealsWithTheCastersStats(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-effect-hot");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        husk.setHealth(100);
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0.5);
        apply(husk, GeneratedEffects.TEST_REGROWTH, 60, 0, player);
        helper.runAfterDelay(65, () -> {
            assertHealth(helper, husk, 100 + 3 * 2 * 1.5, "3 ticks of 2 × 1.5");
            logout(player);
            helper.succeed();
        });
    }

    /** An effect's modifiers hold while it lasts, multiplied by its level, and leave with it. */
    @QaGameTest
    public static void modifiersHoldWhileTheEffectLasts(GameTestHelper helper) {
        ServerPlayer target = caster(helper, "qa-effect-sapped");
        ServerPlayer caster = caster(helper, "qa-effect-sapper");
        Identifier speedId = Realmforge.id("effect.test_sapped.minecraft.movement_speed");
        Identifier powerId = Realmforge.id("effect.test_sapped.realmforge.spell_power");
        apply(target, GeneratedEffects.TEST_SAPPED, 100, 1, caster);
        AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance power = target.getAttribute(GeneratedStats.SPELL_POWER);
        helper.assertValueEqual(modifier(speed, speedId), Optional.of(new AttributeModifier(speedId, -0.4,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)), "-20 % movement speed, twice at level 2");
        helper.assertValueEqual(modifier(power, powerId), Optional.of(new AttributeModifier(powerId, -0.2,
                AttributeModifier.Operation.ADD_VALUE)), "-10 % spell power, twice at level 2");
        target.removeEffect(GeneratedEffects.TEST_SAPPED);
        helper.assertValueEqual(modifier(speed, speedId), Optional.empty(), "the speed modifier left");
        helper.assertValueEqual(modifier(power, powerId), Optional.empty(), "the spell power modifier left");
        logout(target);
        logout(caster);
        helper.succeed();
    }

    /**
     * The latest application decides the source: the same effect from anything but a spell forgets it, and its
     * removal does too; a weaker application that changes nothing keeps it.
     */
    @QaGameTest
    public static void theLatestApplicationDecidesTheSource(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-effect-source");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        Optional<Identifier> spell = Optional.of(GeneratedSpells.TEST_IGNITE.identifier());
        EffectSource source = new EffectSource(player.getUUID(), spell, Optional.empty());
        helper.assertTrue(Effects.apply(husk, new MobEffectInstance(GeneratedEffects.TEST_BURNING, 100, 1), player,
                source), "the first application changes the target");
        helper.assertFalse(Effects.apply(husk, new MobEffectInstance(GeneratedEffects.TEST_BURNING, 20, 0), player,
                new EffectSource(player.getUUID(), Optional.empty(), Optional.empty())),
                "a weaker, shorter one changes nothing");
        helper.assertValueEqual(Effects.source(husk, GeneratedEffects.TEST_BURNING), Optional.of(source),
                "so the source stays");
        husk.addEffect(new MobEffectInstance(GeneratedEffects.TEST_BURNING, 200, 1));
        helper.assertValueEqual(Effects.source(husk, GeneratedEffects.TEST_BURNING), Optional.empty(),
                "an application by anything else forgets it");
        apply(husk, GeneratedEffects.TEST_BURNING, 400, 1, player);
        helper.assertTrue(Effects.source(husk, GeneratedEffects.TEST_BURNING).isPresent(), "a spell's again");
        husk.removeEffect(GeneratedEffects.TEST_BURNING);
        helper.assertValueEqual(Effects.source(husk, GeneratedEffects.TEST_BURNING), Optional.empty(),
                "a removed effect has no source");
        logout(player);
        helper.succeed();
    }

    private static ServerPlayer caster(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, new Vec3(2.5, 1, 0.5));
        player.setGameMode(GameType.SURVIVAL);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.MAX_MANA, 100);
        Resources.tick(player);
        return player;
    }

    private static void learn(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> spell) {
        Holder.Reference<Spell> holder = Spells.get(helper.getLevel().registryAccess(), spell)
                .orElseThrow(() -> new IllegalStateException("no spell " + spell.identifier()));
        Spells.learn(player, holder, 1);
    }

    private static void apply(LivingEntity target, Holder<MobEffect> effect, int ticks,
            int amplifier, ServerPlayer caster) {
        Effects.apply(target, new MobEffectInstance(effect, ticks, amplifier), caster,
                new EffectSource(caster.getUUID(), Optional.empty(), Optional.empty()));
    }

    private static Optional<AttributeModifier> modifier(AttributeInstance attribute, Identifier id) {
        return Optional.ofNullable(attribute.getModifier(id));
    }

    private static void assertHealth(GameTestHelper helper, LivingEntity entity, double expected, String what) {
        helper.assertTrue(Math.abs(entity.getHealth() - expected) <= TOLERANCE, what + ": health " + expected
                + " expected, got " + entity.getHealth());
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
