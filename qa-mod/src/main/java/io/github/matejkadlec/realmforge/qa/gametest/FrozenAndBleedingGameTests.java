package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.boss.BossMob;
import io.github.matejkadlec.realmforge.effect.EffectLooks;
import io.github.matejkadlec.realmforge.effect.EffectSource;
import io.github.matejkadlec.realmforge.effect.Effects;
import io.github.matejkadlec.realmforge.effect.StackTicks;
import io.github.matejkadlec.realmforge.effect.StatusEffect;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-539: Frozen, a root (architecture 11.4), and the bleeds, physical damage over time kept per caster's spell
 * ({@code effect/StackTicks}): since RLM-601 Rend's and Deep Wounds' effects of their own, both of the tag
 * {@code #realmforge:bleeds}. {@code test_rend} bleeds 12 over 6 s of Rend's effect, 1 every half second;
 * {@code test_lacerate} 6 over 6 s of it. A stack's ticks come at the end of every half second since it was applied,
 * so the checks sample the health between two ticks: about 9, 19, ..., 119 ticks after the cast.
 */
public final class FrozenAndBleedingGameTests {
    /** Every bleed (RLM-601): Rend and Deep Wounds. */
    static final TagKey<MobEffect> BLEEDS = TagKey.create(Registries.MOB_EFFECT, Realmforge.id("bleeds"));

    private static final double TOLERANCE = 1e-3;
    private static final double HEALTH = SpellGameTests.HEALTH;
    /** How far a Frozen mob may drift from where it stood: rounding, never a step. */
    private static final double STILL = 0.05;

    private FrozenAndBleedingGameTests() {
    }

    /** A Frozen zombie neither walks nor jumps for the whole effect, but still hits the player next to it. */
    @QaGameTest(timeoutTicks = 120)
    public static void aFrozenZombieStaysButStillHits(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-frozen-target", new Vec3(2.5, 1, 1.5));
        player.setHealth(player.getMaxHealth());
        // A block from the player: within a zombie's melee reach, without a step.
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(2.5, 1, 2.5));
        zombie.setTarget(player);
        // A helmet keeps daylight from burning it: any damage ends Frozen at a 50 % chance (RLM-542).
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.addEffect(new MobEffectInstance(GeneratedEffects.FROZEN, 100));
        Vec3 start = zombie.position();
        helper.assertTrue(EffectLooks.has(zombie, StatusEffect.Look.ICE), "clients draw it in ice");
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(zombie.position().distanceTo(start) < STILL,
                    "the Frozen zombie moved from " + start + " to " + zombie.position());
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "the Frozen zombie hit no one next to it");
            zombie.removeEffect(GeneratedEffects.FROZEN);
            helper.assertFalse(EffectLooks.has(zombie, StatusEffect.Look.ICE), "the ice goes with the effect");
            logout(player);
            helper.succeed();
        });
    }

    /** A boss immune to Frozen (the test titan, {@code immune_to}) never takes it; an ordinary mob does. */
    @QaGameTest
    public static void anImmuneBossIgnoresFrozen(GameTestHelper helper) {
        BossMob titan = (BossMob) helper.spawn(GeneratedMobs.TEST_TITAN.get(), new Vec3(2.5, 1, 2.5));
        titan.targetSelector.removeAllGoals(goal -> true);
        helper.assertFalse(titan.addEffect(new MobEffectInstance(GeneratedEffects.FROZEN, 100)),
                "the titan took Frozen");
        helper.assertFalse(titan.hasEffect(GeneratedEffects.FROZEN), "the titan is Frozen");
        helper.assertTrue(titan.addEffect(new MobEffectInstance(GeneratedEffects.STUNNED, 20)),
                "it is immune to Frozen and the bleeds, not to a stun");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(0, 1, 0));
        helper.assertTrue(husk.addEffect(new MobEffectInstance(GeneratedEffects.FROZEN, 100)),
                "an ordinary mob has no immunity");
        helper.succeed();
    }

    /**
     * RLM-601: Rend and Deep Wounds are the bleeds of {@code #realmforge:bleeds}, and a boss whose {@code immune_to}
     * names the tag (the test titan) takes neither, from a spell or added by hand; an ordinary mob takes both, and the
     * titan's Frozen immunity leaves a stun to land.
     */
    @QaGameTest
    public static void anImmuneBossIgnoresEveryBleed(GameTestHelper helper) {
        helper.assertTrue(GeneratedEffects.REND.is(BLEEDS), "Rend is a bleed");
        helper.assertTrue(GeneratedEffects.DEEP_WOUNDS.is(BLEEDS), "Deep Wounds is a bleed");
        helper.assertFalse(GeneratedEffects.FROZEN.is(BLEEDS), "Frozen is no bleed");
        ServerPlayer player = player(helper, "qa-bleed-boss", new Vec3(2.5, 1, 0.5));
        BossMob titan = (BossMob) helper.spawn(GeneratedMobs.TEST_TITAN.get(), new Vec3(2.5, 1, 2.5));
        titan.targetSelector.removeAllGoals(goal -> true);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(0, 1, 4));
        EffectSource source = new EffectSource(player.getUUID(), Optional.empty(), Optional.empty());
        for (Holder<MobEffect> bleed : List.of(GeneratedEffects.REND, GeneratedEffects.DEEP_WOUNDS)) {
            String name = bleed.unwrapKey().orElseThrow().identifier().toString();
            helper.assertFalse(StackTicks.apply(titan, bleed, player, source, 12, 120), "the titan bled from " + name);
            helper.assertFalse(titan.addEffect(new MobEffectInstance(bleed, 120)), "the titan took " + name);
            helper.assertFalse(titan.hasEffect(bleed), "the titan has " + name);
            helper.assertValueEqual(StackTicks.of(titan, bleed).size(), 0, "no stack of " + name + " on the titan");
            helper.assertTrue(StackTicks.apply(husk, bleed, player, source, 12, 120), "an ordinary mob bleeds");
            helper.assertTrue(husk.hasEffect(bleed), "the husk has " + name);
        }
        helper.assertTrue(titan.addEffect(new MobEffectInstance(GeneratedEffects.STUNNED, 20)),
                "the titan is immune to the bleeds and Frozen only, so a stun still lands");
        logout(player);
        helper.succeed();
    }

    /** Will To Survive's cleanse breaks Frozen, a loss of control, as every harmful effect. */
    @QaGameTest
    public static void willToSurviveBreaksFrozen(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-frozen-cleanse", new Vec3(2.5, 1, 0.5));
        player.addEffect(new MobEffectInstance(GeneratedEffects.FROZEN, 100));
        Spells.learn(player, spell(helper, GeneratedSpells.WILL_TO_SURVIVE), 1);
        CastResult cast = SpellCaster.cast(player, GeneratedSpells.WILL_TO_SURVIVE, player);
        helper.assertTrue(cast.success(), "Will To Survive was refused: " + cast.failure());
        helper.assertFalse(player.hasEffect(GeneratedEffects.FROZEN), "the player is still Frozen");
        logout(player);
        helper.succeed();
    }

    /**
     * Rend's bleed deals its total over 6 s in 12 ticks of a half second, fixed when applied, through the target's
     * armor: 1 a tick against 20 armor; only Rend's effect shows, never Deep Wounds' (RLM-601).
     */
    @QaGameTest(timeoutTicks = 160)
    public static void bleedingDealsItsTotalInTwelveTicksThroughArmor(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-bleed-total", new Vec3(2.5, 1, 0.5));
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        CombatGameTests.setBase(husk, Attributes.ARMOR, 20);
        cast(helper, player, GeneratedSpells.TEST_REND, husk);
        helper.assertTrue(husk.hasEffect(GeneratedEffects.REND), "the husk is not bleeding");
        helper.assertFalse(husk.hasEffect(GeneratedEffects.DEEP_WOUNDS), "Rend gave Deep Wounds' effect");
        helper.assertValueEqual(StackTicks.of(husk, GeneratedEffects.REND).size(), 1, "one stack");
        helper.runAfterDelay(4, () -> assertHealth(helper, husk, HEALTH, "no tick before half a second"));
        helper.runAfterDelay(14, () -> assertHealth(helper, husk, HEALTH - 1, "the first tick, armor ignored"));
        helper.runAfterDelay(64, () -> assertHealth(helper, husk, HEALTH - 6, "6 ticks in 3 s"));
        helper.runAfterDelay(130, () -> {
            assertHealth(helper, husk, HEALTH - 12, "12 ticks of 1 in 6 s");
            helper.assertFalse(husk.hasEffect(GeneratedEffects.REND), "the bleed ran out");
            helper.assertValueEqual(StackTicks.of(husk, GeneratedEffects.REND).size(), 0, "no stack left");
            logout(player);
            helper.succeed();
        });
    }

    /** Two spells' bleeds of one effect stack on one target: 12 and 6 over the same 6 s. */
    @QaGameTest(timeoutTicks = 160)
    public static void twoSourcesStack(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-bleed-stack", new Vec3(2.5, 1, 0.5));
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        cast(helper, player, GeneratedSpells.TEST_REND, husk);
        helper.runAfterDelay(1, () -> cast(helper, player, GeneratedSpells.TEST_LACERATE, husk));
        helper.runAfterDelay(2, () -> helper.assertValueEqual(StackTicks.of(husk, GeneratedEffects.REND).size(),
                2, "a stack per spell"));
        helper.runAfterDelay(135, () -> {
            assertHealth(helper, husk, HEALTH - 18, "12 + 6");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * The same spell from the same caster refreshes its stack: test_rend again after 6 ticks starts its 12 ticks anew,
     * so 6 + 12 land in all; another caster's test_rend is a stack of its own.
     */
    @QaGameTest(timeoutTicks = 220)
    public static void theSameSourceRefreshes(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-bleed-refresh", new Vec3(2.5, 1, 0.5));
        ServerPlayer other = player(helper, "qa-bleed-other", new Vec3(0.5, 1, 0.5));
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        cast(helper, player, GeneratedSpells.TEST_REND, husk);
        helper.runAfterDelay(64, () -> {
            assertHealth(helper, husk, HEALTH - 6, "6 ticks");
            cast(helper, player, GeneratedSpells.TEST_REND, husk);
            helper.assertValueEqual(StackTicks.of(husk, GeneratedEffects.REND).size(), 1,
                    "the same source refreshes its stack");
            cast(helper, other, GeneratedSpells.TEST_REND, husk);
            helper.assertValueEqual(StackTicks.of(husk, GeneratedEffects.REND).size(), 2,
                    "another caster's is a stack of its own");
        });
        helper.runAfterDelay(200, () -> {
            assertHealth(helper, husk, HEALTH - 6 - 12 - 12, "6, then 12 anew, and the other caster's 12");
            logout(player);
            logout(other);
            helper.succeed();
        });
    }

    /** Milk, a cleanse or {@code /effect clear} takes the bleed's stacks with the effect. */
    @QaGameTest
    public static void removingBleedingEndsEveryStack(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-bleed-clear", new Vec3(2.5, 1, 0.5));
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        cast(helper, player, GeneratedSpells.TEST_REND, husk);
        husk.removeEffect(GeneratedEffects.REND);
        helper.assertValueEqual(StackTicks.of(husk, GeneratedEffects.REND).size(), 0, "the stacks went");
        helper.runAfterDelay(40, () -> {
            assertHealth(helper, husk, HEALTH, "no tick after the effect was removed");
            helper.assertValueEqual(Effects.source(husk, GeneratedEffects.REND).map(EffectSource::caster),
                    Optional.empty(), "and no source");
            logout(player);
            helper.succeed();
        });
    }

    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 where) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, where);
        player.setGameMode(GameType.SURVIVAL);
        // A fake player's client never reports that it loaded, which keeps the player invulnerable.
        player.connection.markClientLoaded();
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        return player;
    }

    private static void cast(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> spell,
            LivingEntity target) {
        Spells.learn(player, spell(helper, spell), 1);
        player.getData(SpellAttachments.COMBAT).clearCooldowns();
        CastResult result = SpellCaster.cast(player, spell, target);
        helper.assertTrue(result.success(), spell.identifier() + " was refused: " + result.failure());
    }

    private static Holder.Reference<Spell> spell(GameTestHelper helper, ResourceKey<Spell> spell) {
        return Spells.get(helper.getLevel().registryAccess(), spell)
                .orElseThrow(() -> new IllegalStateException("no spell " + spell.identifier()));
    }

    private static void assertHealth(GameTestHelper helper, LivingEntity entity, double expected, String what) {
        helper.assertTrue(Math.abs(entity.getHealth() - expected) <= TOLERANCE, what + ": health " + expected
                + " expected, got " + entity.getHealth());
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
