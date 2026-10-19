package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.qa.combat.CastLog;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Casting;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.net.CastStartedPayload;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * C3a-2 (RLM-210): cast times, channels and interrupts (architecture 11.2). Players cast the test spells of
 * {@code content/spells/} at husks that stand still: {@code test_slow_bolt} deals 10 fire damage after a 2 s cast and
 * then costs 20 mana, {@code test_drain} pays 15 mana and starts its 5 s cooldown when it begins, then deals 2 arcane
 * damage every second for 3 s, and {@code test_homing_bolt} deals 4 frost damage after 1 s, locks on and may be cast
 * while moving. The server ticks the casts, so the checks wait real ticks, with a few ticks of slack around each end.
 */
public final class CastGameTests {
    private static final double TOLERANCE = 1e-4;
    static final double HEALTH = 1000;

    private CastGameTests() {
    }

    /**
     * A cast time takes its full time, less Cast Time Reduction: the effects, the cost and the cooldown come at the
     * end, the global cooldown at the start, and a second cast meanwhile is refused.
     */
    @QaGameTest
    public static void aCastCompletesAfterItsCastTimeLessCastTimeReduction(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-cast-time");
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        learn(helper, player, GeneratedSpells.TEST_BOLT);

        long now = helper.getLevel().getGameTime();
        assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk);
        helper.assertValueEqual(Spells.currentCast(player), Optional.of(
                new Cast(GeneratedSpells.TEST_SLOW_BOLT, now, 40, false)), "a 2 s cast without Cast Time Reduction");
        helper.assertValueEqual(player.getData(SpellAttachments.COMBAT).globalCooldownEnd(), now + 20,
                "the global cooldown starts with the cast");
        Spells.cancelCast(player);
        Spells.resetCooldowns(player);

        StatsCommands.setBase(player, GeneratedStats.CAST_TIME_REDUCTION, 0.5);
        long start = helper.getLevel().getGameTime();
        assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk);
        helper.assertValueEqual(Spells.currentCast(player).map(Cast::durationTicks), Optional.of(20),
                "50 % Cast Time Reduction halves the cast");
        helper.assertValueEqual(Spells.currentCast(player).map(Cast::startGameTime), Optional.of(start),
                "the cast starts now");
        helper.assertValueEqual(SpellCaster.cast(player, GeneratedSpells.TEST_BOLT, husk).failureOrNull(),
                CastFailure.ALREADY_CASTING, "a second cast during the first");
        assertHealth(helper, husk, HEALTH, "nothing lands when the cast begins");
        assertPool(helper, player, 100, "the cost is paid at the end");
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(Casting.isCasting(player), "still casting after 15 of 20 ticks");
            assertHealth(helper, husk, HEALTH, "nothing lands before the cast completes");
        });
        helper.runAfterDelay(25, () -> {
            helper.assertFalse(Casting.isCasting(player), "the cast is over");
            assertHealth(helper, husk, HEALTH - 10, "test_slow_bolt landed");
            assertPool(helper, player, 80, "and cost 20 mana");
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.COMPLETED);
            logout(player);
            helper.succeed();
        });
    }

    /** Mana that runs out during the cast fails it at the end, without its effects or its cost. */
    @QaGameTest
    public static void aCastWhoseResourceRunsOutFailsWithoutItsEffects(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-cast-resource");
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk);
        Resources.set(player, ResourceTypes.MANA, 5);
        helper.runAfterDelay(50, () -> {
            helper.assertFalse(Casting.isCasting(player), "the cast ended");
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.NOT_ENOUGH_RESOURCE);
            assertHealth(helper, husk, HEALTH, "the failed cast dealt nothing");
            assertPool(helper, player, 5, "and cost nothing");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * A target that leaves the spell's range during the cast fails it at the end, without its effects; a spell that
     * locks on keeps its target wherever it goes in the caster's dimension.
     */
    @QaGameTest
    public static void aCastWhoseTargetLeavesRangeFailsWithoutItsEffects(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-cast-range");
        ServerPlayer seeker = caster(helper, "qa-cast-range-lock");
        seeker.snapTo(helper.absoluteVec(new Vec3(0.5, 1, 0.5)), 0, 0);
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        learn(helper, seeker, GeneratedSpells.TEST_HOMING_BOLT);
        assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk);
        assertBegun(helper, seeker, GeneratedSpells.TEST_HOMING_BOLT, husk);
        husk.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 19.5)), 0, 0);
        // The seeker may move during its cast: walking after the husk interrupts nothing.
        seeker.snapTo(helper.absoluteVec(new Vec3(0.5, 1, 2.5)), 0, 0);
        helper.runAfterDelay(50, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.OUT_OF_RANGE);
            assertPool(helper, player, 100, "the failed cast cost nothing");
            assertEnded(helper, seeker, GeneratedSpells.TEST_HOMING_BOLT, CastEnd.COMPLETED);
            assertHealth(helper, husk, HEALTH - 4, "only test_homing_bolt, locked on, landed beyond its range");
            logout(seeker);
            logout(player);
            helper.succeed();
        });
    }

    /**
     * A channel pays and starts its cooldown when it begins, then runs its effects at every interval; moving ends it
     * early, and the ticks already run stay applied.
     */
    @QaGameTest
    public static void aChannelTicksAtItsIntervalAndStopsWhenInterrupted(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-cast-channel");
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_DRAIN);
        long now = helper.getLevel().getGameTime();
        assertBegun(helper, player, GeneratedSpells.TEST_DRAIN, husk);
        assertPool(helper, player, 85, "the channel pays 15 mana when it begins");
        helper.assertValueEqual(player.getData(SpellAttachments.COMBAT).cooldowns().get(GeneratedSpells.TEST_DRAIN),
                now + 100, "and starts its 5 s cooldown");
        helper.assertValueEqual(Spells.currentCast(player), Optional.of(
                new Cast(GeneratedSpells.TEST_DRAIN, now, 60, true)), "a 3 s channel");
        assertHealth(helper, husk, HEALTH, "the first tick comes after an interval");
        helper.runAfterDelay(15, () -> assertHealth(helper, husk, HEALTH, "no tick before the first interval"));
        helper.runAfterDelay(30, () -> assertHealth(helper, husk, HEALTH - 2, "one tick after a second"));
        helper.runAfterDelay(45, () -> {
            assertHealth(helper, husk, HEALTH - 4, "two ticks after two seconds");
            walk(helper, player, new Vec3(1.5, 1, 0.5));
        });
        helper.runAfterDelay(75, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_DRAIN, CastEnd.MOVED);
            assertHealth(helper, husk, HEALTH - 4, "moving stopped the third tick; the first two stay");
            assertPool(helper, player, 85, "and the cost is not refunded");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Being pushed is not moving (RLM-243): a melee hit's knockback moves a casting mob, and a player its client moves
     * after a melee hit's knockback, without a key pressed (RLM-261), and both casts go on and complete.
     */
    @QaGameTest
    public static void aCasterPushedByMeleeKeepsItsCast(GameTestHelper helper) {
        Husk target = husk(helper, new BlockPos(0, 1, 4));
        Husk caster = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 1, 1));
        MobLevels.setLevel(caster, EntityLevel.MIN);
        CombatGameTests.setBase(caster, Attributes.MAX_HEALTH, HEALTH);
        CombatGameTests.setBase(caster, Attributes.KNOCKBACK_RESISTANCE, 0);
        caster.setHealth((float) HEALTH);
        Husk attacker = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 1, 0));
        MobLevels.setLevel(attacker, EntityLevel.MIN);
        assertBegun(helper, caster, GeneratedSpells.TEST_SLOW_BOLT, target);
        Vec3 start = caster.position();
        helper.assertTrue(attacker.doHurtTarget(helper.getLevel(), caster), "the attacker's melee hit missed");

        // The player stands clear of the husks, so no crowding pushes it.
        ServerPlayer player = caster(helper, "qa-cast-pushed", new Vec3(4.5, 1, 3.5));
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, target);
        // A melee hit's knockback from the north, the call LivingEntity#hurtServer makes for it.
        player.knockback(0.4, 0, -1);
        // The knockback reaches the player through its client: the server only sees where to, and no key pressed.
        helper.runAfterDelay(3, () -> player.snapTo(helper.absoluteVec(new Vec3(4.5, 1, 4.4)), 0, 0));

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(caster.position().distanceTo(start) > 3 * Casting.MOVE_TOLERANCE,
                    "the knockback moved the husk: " + caster.position().distanceTo(start) + " blocks");
            helper.assertTrue(Casting.isCasting(caster), "the pushed husk lost its cast");
            helper.assertTrue(Casting.isCasting(player), "the pushed player lost its cast");
        });
        helper.runAfterDelay(50, () -> {
            assertEnded(helper, caster, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.COMPLETED);
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.COMPLETED);
            assertHealth(helper, target, HEALTH - 20, "both casts landed");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Moving without a push loses the cast, whatever keys the client reports (RLM-261): a modified client can report
     * no key pressed while it walks, so a player the server did not push moves by itself.
     */
    @QaGameTest
    public static void aPlayerMovedWithoutAPushLosesItsCast(GameTestHelper helper) {
        Husk target = husk(helper, new BlockPos(0, 1, 4));
        ServerPlayer player = caster(helper, "qa-cast-keys-up", new Vec3(4.5, 1, 0.5));
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, target);
        player.snapTo(helper.absoluteVec(new Vec3(4.5, 1, 1.5)), 0, 0);

        helper.runAfterDelay(5, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.MOVED);
            assertHealth(helper, target, HEALTH, "the cast did not land");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Moving by itself loses the cast (RLM-243): a player pressing a movement key while its client moves it, and a mob
     * that walks.
     */
    @QaGameTest
    public static void aCasterThatMovesByItselfLosesItsCast(GameTestHelper helper) {
        Husk target = husk(helper, new BlockPos(0, 1, 4));
        ServerPlayer player = caster(helper, "qa-cast-walker");
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, target);
        walk(helper, player, new Vec3(2.5, 1, 1.5));

        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 1, 0));
        MobLevels.setLevel(husk, EntityLevel.MIN);
        assertBegun(helper, husk, GeneratedSpells.TEST_SLOW_BOLT, target);
        // Its navigation walks it, as its AI would, setting its movement input every tick; like GameTestHelper#walkTo,
        // only once it stands on the ground.
        Vec3 goal = helper.absoluteVec(new Vec3(4.5, 1, 3.5));
        helper.runAfterDelay(2, () -> helper.assertTrue(husk.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0),
                "the husk found no path"));

        helper.runAfterDelay(35, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.MOVED);
            helper.assertTrue(husk.position().distanceTo(goal) < 2.5, "the husk did not walk: at "
                    + helper.relativeVec(husk.position()) + ", zza " + husk.zza + ", casting " + Casting.isCasting(husk)
                    + ", path " + husk.getNavigation().getPath() + ", done " + husk.getNavigation().isDone());
            assertEnded(helper, husk, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.MOVED);
            assertHealth(helper, target, HEALTH, "neither cast landed");
            logout(player);
            helper.succeed();
        });
    }

    /** Cast Time Reduction shortens a channel's interval, so it runs all its ticks in less time, then completes. */
    @QaGameTest
    public static void aFasterChannelKeepsItsTicks(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-cast-channel-fast");
        StatsCommands.setBase(player, GeneratedStats.CAST_TIME_REDUCTION, 0.5);
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_DRAIN);
        assertBegun(helper, player, GeneratedSpells.TEST_DRAIN, husk);
        helper.assertValueEqual(Spells.currentCast(player).map(Cast::durationTicks), Optional.of(30),
                "three ticks every half second");
        helper.runAfterDelay(15, () -> assertHealth(helper, husk, HEALTH - 2, "one tick after half a second"));
        helper.runAfterDelay(40, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_DRAIN, CastEnd.COMPLETED);
            assertHealth(helper, husk, HEALTH - 6, "all three ticks");
            logout(player);
            helper.succeed();
        });
    }

    /** A target that dies during a channel ends it before its next tick. */
    @QaGameTest
    public static void aChannelStopsWhenItsTargetDies(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-cast-channel-dead");
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_DRAIN);
        assertBegun(helper, player, GeneratedSpells.TEST_DRAIN, husk);
        husk.kill(helper.getLevel());
        helper.runAfterDelay(30, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_DRAIN, CastEnd.TARGET_LOST);
            logout(player);
            helper.succeed();
        });
    }

    /**
     * No melee while casting (ADR 0020): a hit during a cast lands nothing, Better Combat's disable tag is set for the
     * cast only, and a hit after it lands.
     */
    @QaGameTest
    public static void meleeIsRefusedDuringACastAndAllowedAfterIt(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-cast-melee");
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 2));
        Husk husk = husk(helper, new BlockPos(2, 1, 4));
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk);
        helper.assertTrue(hasBetterCombatTag(player), "Better Combat's disable tag is missing during the cast");
        player.attack(pig);
        helper.assertValueEqual(pig.getHealth(), pig.getMaxHealth(), "a melee hit during the cast");
        helper.runAfterDelay(50, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.COMPLETED);
            helper.assertFalse(hasBetterCombatTag(player), "Better Combat's disable tag outlived the cast");
            helper.assertFalse(player.getData(SpellAttachments.COMBAT).setBetterCombatTag(), "the flag too");
            player.attack(pig);
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "a melee hit after the cast did no damage");
            logout(player);
            helper.succeed();
        });
    }

    /** An operator's own Better Combat tag survives a cast; a tag a crash left behind goes at the next login. */
    @QaGameTest
    public static void onlyTheTagACastSetIsRemoved(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer operator = caster(helper, "qa-cast-op-tag");
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, operator, GeneratedSpells.TEST_SLOW_BOLT);
        operator.addTag(CombatState.BETTER_COMBAT_DISABLED_TAG);
        assertBegun(helper, operator, GeneratedSpells.TEST_SLOW_BOLT, husk);
        helper.assertFalse(operator.getData(SpellAttachments.COMBAT).setBetterCombatTag(),
                "the cast did not set the operator's tag");
        Spells.cancelCast(operator);
        helper.assertTrue(hasBetterCombatTag(operator), "the operator's own tag survives the cast");
        logout(operator);

        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-cast-crash");
        ServerPlayer crashed = caster(helper, profile);
        crashed.addTag(CombatState.BETTER_COMBAT_DISABLED_TAG);
        crashed.getData(SpellAttachments.COMBAT).setSetBetterCombatTag(true);
        // A crash: the save holds the cast's tag and the flag that the cast set it.
        logout(crashed);
        ServerPlayer back = QaPlayers.login(server, profile);
        helper.assertFalse(hasBetterCombatTag(back), "the login removed the tag a crash left behind");
        helper.assertFalse(back.getData(SpellAttachments.COMBAT).setBetterCombatTag(), "and the flag");
        logout(back);
        helper.succeed();
    }

    /** Death, a dimension change, logging out and a cancel interrupt a cast, and none of them lets it land. */
    @QaGameTest
    public static void deathDimensionLogoutAndCancelInterruptACast(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Husk husk = husk(helper, new BlockPos(2, 1, 4));

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 1, 0));
        zombie.setNoAi(true);
        MobLevels.setLevel(zombie, EntityLevel.MIN);
        assertBegun(helper, zombie, GeneratedSpells.TEST_SLOW_BOLT, husk);
        zombie.kill(helper.getLevel());
        assertEnded(helper, zombie, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.DIED);

        ServerPlayer traveller = caster(helper, "qa-cast-dimension");
        learn(helper, traveller, GeneratedSpells.TEST_SLOW_BOLT);
        assertBegun(helper, traveller, GeneratedSpells.TEST_SLOW_BOLT, husk);
        ServerLevel instances = InstanceDimension.level(server);
        traveller.teleportTo(instances, 0.5, 100, 0.5, Set.of(), 0.0F, 0.0F, true);
        assertEnded(helper, traveller, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.CHANGED_DIMENSION);
        logout(traveller);

        ServerPlayer leaver = caster(helper, "qa-cast-logout");
        learn(helper, leaver, GeneratedSpells.TEST_SLOW_BOLT);
        assertBegun(helper, leaver, GeneratedSpells.TEST_SLOW_BOLT, husk);
        logout(leaver);
        assertEnded(helper, leaver, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.LOGGED_OUT);
        helper.assertFalse(hasBetterCombatTag(leaver), "the cast's tag never reaches the save");

        ServerPlayer canceller = caster(helper, "qa-cast-cancel");
        learn(helper, canceller, GeneratedSpells.TEST_SLOW_BOLT);
        assertBegun(helper, canceller, GeneratedSpells.TEST_SLOW_BOLT, husk);
        run(server, canceller, "qa spell cancel");
        assertEnded(helper, canceller, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.CANCELLED);
        helper.assertFalse(Spells.cancelCast(canceller), "nothing left to cancel");

        helper.runAfterDelay(50, () -> {
            assertHealth(helper, husk, HEALTH, "no interrupted cast landed");
            assertPool(helper, canceller, 100, "and none cost anything");
            logout(canceller);
            helper.succeed();
        });
    }

    /**
     * A player who starts tracking the caster mid-cast is sent the cast with its original start time
     * ({@code PlayerEvent.StartTracking}, architecture 11.2 step 3).
     */
    @QaGameTest
    public static void aLateWatcherGetsTheCastWithItsStartTime(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-cast-watched");
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        long start = helper.getLevel().getGameTime();
        assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk);
        helper.assertValueEqual(Casting.startedPayload(husk), Optional.empty(), "the husk casts nothing");
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(Casting.startedPayload(player), Optional.of(new CastStartedPayload(player.getId(),
                    new Cast(GeneratedSpells.TEST_SLOW_BOLT, start, 40, false))), "the cast as it began");
            logout(player);
            helper.succeed();
        });
    }

    /** A fake player at the platform's south edge, in survival, at 0 % crit chance, with a full pool of 100 mana. */
    static ServerPlayer caster(GameTestHelper helper, String name) {
        return caster(helper, name, new Vec3(2.5, 1, 0.5));
    }

    private static ServerPlayer caster(GameTestHelper helper, String name, Vec3 pos) {
        return caster(helper, new GameProfile(UUID.randomUUID(), name), pos);
    }

    private static ServerPlayer caster(GameTestHelper helper, GameProfile profile) {
        return caster(helper, profile, new Vec3(2.5, 1, 0.5));
    }

    private static ServerPlayer caster(GameTestHelper helper, GameProfile profile, Vec3 pos) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, profile, pos);
        player.setGameMode(GameType.SURVIVAL);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.MAX_MANA, 100);
        Resources.tick(player);
        return player;
    }

    /** A husk that stands still with {@value #HEALTH} health, no armor and level 1. */
    static Husk husk(GameTestHelper helper, BlockPos pos) {
        Husk husk = helper.spawn(EntityType.HUSK, pos);
        husk.setNoAi(true);
        MobLevels.setLevel(husk, EntityLevel.MIN);
        CombatGameTests.setBase(husk, Attributes.MAX_HEALTH, HEALTH);
        CombatGameTests.setBase(husk, Attributes.ARMOR, 0);
        CombatGameTests.setBase(husk, Attributes.KNOCKBACK_RESISTANCE, 1);
        husk.setHealth((float) HEALTH);
        return husk;
    }

    static void learn(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> spell) {
        Holder.Reference<Spell> holder = Spells.get(helper.getLevel().registryAccess(), spell)
                .orElseThrow(() -> new IllegalStateException("no spell " + spell.identifier()));
        Spells.learn(player, holder, 1);
    }

    static void assertBegun(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            LivingEntity target) {
        CastResult result = SpellCaster.cast(caster, spell, target);
        helper.assertTrue(result.success(), spell.identifier() + " was refused: " + result.failure());
        helper.assertTrue(result.begun(), spell.identifier() + " ran at once instead of beginning a cast");
        helper.assertTrue(result.entity().orElse(null) == target,
                spell.identifier() + " aimed at " + result.target());
        helper.assertTrue(Casting.isCasting(caster), spell.identifier() + " is not in progress");
    }

    static void assertEnded(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            CastEnd expected) {
        helper.assertFalse(Casting.isCasting(caster), spell.identifier() + " is still in progress");
        helper.assertValueEqual(CastLog.last(caster), Optional.of(new CastLog.Ended(spell, expected)),
                caster.getPlainTextName() + "'s last cast");
    }

    static void assertHealth(GameTestHelper helper, LivingEntity entity, double expected, String what) {
        helper.assertTrue(Math.abs(entity.getHealth() - expected) <= TOLERANCE, what + ": health " + expected
                + " expected, got " + entity.getHealth());
    }

    private static void assertPool(GameTestHelper helper, ServerPlayer player, double expected, String what) {
        float amount = Resources.amount(player, ResourceTypes.MANA);
        helper.assertTrue(Math.abs(amount - expected) <= TOLERANCE, what + ": mana " + expected + " expected, got "
                + amount);
    }

    /** {@code player} walks to {@code pos}: its client presses forward and moves it there. */
    static void walk(GameTestHelper helper, ServerPlayer player, Vec3 pos) {
        player.setLastClientInput(new Input(true, false, false, false, false, false, false));
        player.snapTo(helper.absoluteVec(pos), player.getYRot(), player.getXRot());
    }

    private static boolean hasBetterCombatTag(ServerPlayer player) {
        return player.entityTags().contains(CombatState.BETTER_COMBAT_DISABLED_TAG);
    }

    private static void run(MinecraftServer server, ServerPlayer player, String command) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        server.getCommands().performPrefixedCommand(source, command);
    }

    static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
