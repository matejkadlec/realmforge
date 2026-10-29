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
 * {@code content/spells/} at husks that stand still: {@code test_slow_bolt} costs 20 mana when its 2 s cast begins and
 * deals 10 fire damage when it completes, {@code test_drain} pays 15 mana and starts its 5 s cooldown when it begins,
 * pays 15 mana again as each later second begins (RLM-403) and deals 2 arcane damage every second for 3 s, and
 * {@code test_homing_bolt} deals 4 frost damage after 1 s, locks on and may be cast while moving. The server ticks the
 * casts, so the checks wait real ticks, with a few ticks of slack around each end.
 */
public final class CastGameTests {
    private static final double TOLERANCE = 1e-4;
    static final double HEALTH = 1000;

    private CastGameTests() {
    }

    /**
     * A cast time takes its full time, less Cast Time Reduction: the cost and the global cooldown come at the start
     * (RLM-403), the effects and the cooldown at the end, and a second cast meanwhile is refused. A cancel keeps the
     * cost spent.
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
        helper.assertValueEqual(player.getData(SpellAttachments.COMBAT).globalCooldownEnd(), now + 10,
                "the global cooldown starts with the cast");
        assertPool(helper, player, 80, "the cost is paid when the cast begins");
        Spells.cancelCast(player);
        Spells.resetCooldowns(player);
        assertPool(helper, player, 80, "a cancel keeps it spent");

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
        assertPool(helper, player, 60, "the second cast paid its 20 mana at once");
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(Casting.isCasting(player), "still casting after 15 of 20 ticks");
            assertHealth(helper, husk, HEALTH, "nothing lands before the cast completes");
        });
        helper.runAfterDelay(25, () -> {
            helper.assertFalse(Casting.isCasting(player), "the cast is over");
            assertHealth(helper, husk, HEALTH - 10, "test_slow_bolt landed");
            assertPool(helper, player, 60, "and cost nothing more at the end");
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.COMPLETED);
            logout(player);
            helper.succeed();
        });
    }

    /** A cast time paid when it began (RLM-403), so mana lost during the cast takes nothing from it. */
    @QaGameTest
    public static void aCastTimePaidWhenItBeganCompletesWhateverTheManaLeft(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-cast-resource");
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk);
        assertPool(helper, player, 80, "paid when the cast began");
        Resources.set(player, ResourceTypes.MANA, 5);
        helper.runAfterDelay(50, () -> {
            helper.assertFalse(Casting.isCasting(player), "the cast ended");
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.COMPLETED);
            assertHealth(helper, husk, HEALTH - 10, "the bolt landed");
            assertPool(helper, player, 5, "and took no more mana at the end");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * A channel pays its cost again as each later second begins (RLM-403), after the tick due then; a pool short of the
     * next second's cost ends it, and the ticks already run stay applied.
     */
    @QaGameTest
    public static void aChannelThatCannotPayItsNextSecondEnds(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-cast-channel-poor");
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_DRAIN);
        assertBegun(helper, player, GeneratedSpells.TEST_DRAIN, husk);
        Resources.set(player, ResourceTypes.MANA, 10);
        helper.runAfterDelay(30, () -> {
            helper.assertFalse(Casting.isCasting(player), "10 mana do not pay the second second");
            assertEnded(helper, player, GeneratedSpells.TEST_DRAIN, CastEnd.NOT_ENOUGH_RESOURCE);
            assertHealth(helper, husk, HEALTH - 2, "the first second's tick, which its payment covered, landed");
            assertPool(helper, player, 10, "the refused payment took nothing");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * A target that goes more than twice the spell's range away during a player's cast ends it at once, without its
     * effects (the sticky target, RLM-345: up to twice the range it is kept); a spell that locks on keeps its target
     * wherever it goes in the caster's dimension. The husk goes 40 blocks up, clear of the tests beside this one.
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
        husk.snapTo(helper.absoluteVec(new Vec3(2.5, 41, 3.5)), 0, 0);
        // The seeker may move during its cast, at full speed (ADR 0039).
        assertSlowed(helper, seeker, false, "a spell castable while moving does not slow");
        seeker.snapTo(helper.absoluteVec(new Vec3(0.5, 1, 2.5)), 0, 0);
        helper.runAfterDelay(50, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.OUT_OF_RANGE);
            assertPool(helper, player, 80, "the failed cast keeps the cost it paid when it began");
            assertEnded(helper, seeker, GeneratedSpells.TEST_HOMING_BOLT, CastEnd.COMPLETED);
            assertHealth(helper, husk, HEALTH - 4, "only test_homing_bolt, locked on, landed beyond its range");
            logout(seeker);
            logout(player);
            helper.succeed();
        });
    }

    /**
     * A channel pays and starts its cooldown when it begins, pays again as each later second begins (RLM-403) and runs
     * its effects at every interval; moving does not end it (ADR 0039), an interrupt does, and the ticks already run
     * stay applied.
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
        helper.runAfterDelay(15, () -> {
            assertHealth(helper, husk, HEALTH, "no tick before the first interval");
            assertPool(helper, player, 85, "nor a second payment");
        });
        helper.runAfterDelay(30, () -> {
            assertHealth(helper, husk, HEALTH - 2, "one tick after a second");
            assertPool(helper, player, 70, "and the second second paid");
        });
        helper.runAfterDelay(45, () -> {
            assertHealth(helper, husk, HEALTH - 4, "two ticks after two seconds");
            assertPool(helper, player, 55, "and the third second paid");
            walk(helper, player, new Vec3(1.5, 1, 0.5));
        });
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(Casting.isCasting(player), "moving ended the channel");
            helper.assertTrue(Casting.cancel(player), "no channel to cancel");
        });
        helper.runAfterDelay(75, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_DRAIN, CastEnd.CANCELLED);
            assertSlowed(helper, player, false, "the cancel took the cast's slow away");
            assertHealth(helper, husk, HEALTH - 4, "the cancel stopped the third tick; the first two stay");
            assertPool(helper, player, 55, "and the seconds paid are not refunded");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Moving slows, it never interrupts (RLM-261, ADR 0039): a player walking with a movement key down and a mob walking
     * on its path both keep their casts, at half their speed, and get their full speed back when the casts complete.
     */
    @QaGameTest
    public static void aMovingCasterIsSlowedAndKeepsItsCast(GameTestHelper helper) {
        Husk target = husk(helper, new BlockPos(0, 1, 4));
        ServerPlayer player = caster(helper, "qa-cast-walker");
        double playerSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, target);
        assertSlowed(helper, player, true, "the cast slows the player");
        helper.assertValueEqual(player.getAttributeValue(Attributes.MOVEMENT_SPEED), playerSpeed * 0.5,
                "half the speed is left (ADR 0047)");
        walk(helper, player, new Vec3(2.5, 1, 1.5));

        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 1, 0));
        MobLevels.setLevel(husk, EntityLevel.MIN);
        Vec3 huskStart = husk.position();
        assertBegun(helper, husk, GeneratedSpells.TEST_SLOW_BOLT, target);
        assertSlowed(helper, husk, true, "the cast slows the mob");
        // Its navigation walks it, as its AI would; like GameTestHelper#walkTo, only once it stands on the ground.
        Vec3 goal = helper.absoluteVec(new Vec3(4.5, 1, 3.5));
        helper.runAfterDelay(2, () -> helper.assertTrue(husk.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0),
                "the husk found no path"));

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(Casting.isCasting(player), "walking ended the player's cast");
            helper.assertTrue(husk.position().distanceTo(huskStart) > 0.5, "the husk did not walk: at "
                    + helper.relativeVec(husk.position()));
            helper.assertTrue(Casting.isCasting(husk), "walking ended the husk's cast");
        });
        helper.runAfterDelay(50, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.COMPLETED);
            assertEnded(helper, husk, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.COMPLETED);
            assertHealth(helper, target, HEALTH - 20, "both casts landed");
            assertSlowed(helper, player, false, "the player's full speed is back");
            helper.assertValueEqual(player.getAttributeValue(Attributes.MOVEMENT_SPEED), playerSpeed,
                    "the player's speed as before the cast");
            assertSlowed(helper, husk, false, "the husk's full speed is back");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * A cast that slows its caster stops its sprint until it ends (RLM-371, ADR 0057): a sprinting player who starts
     * Test Slow Bolt moves at half their walking speed, not at 65 % of it, a sprint their client asks for during the
     * cast is stopped at the next tick, and after the cast they sprint again. A spell castable while moving keeps the
     * sprint.
     */
    @QaGameTest
    public static void aCastThatSlowsStopsTheSprintUntilItEnds(GameTestHelper helper) {
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        ServerPlayer player = caster(helper, "qa-cast-sprinter");
        ServerPlayer seeker = caster(helper, "qa-cast-sprinter-free");
        seeker.snapTo(helper.absoluteVec(new Vec3(0.5, 1, 0.5)), 0, 0);
        double walking = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        learn(helper, seeker, GeneratedSpells.TEST_HOMING_BOLT);
        askToSprint(player);
        askToSprint(seeker);
        assertSpeed(helper, player, walking * 1.3, "sprinting before the cast");

        assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk);
        helper.assertFalse(player.isSprinting(), "the cast did not stop the sprint");
        helper.assertTrue(Casting.blocksSprint(player), "the cast does not keep the player from sprinting");
        assertSpeed(helper, player, walking * 0.5, "half the walking speed during the cast");
        assertBegun(helper, seeker, GeneratedSpells.TEST_HOMING_BOLT, husk);
        helper.assertTrue(seeker.isSprinting(), "a spell castable while moving stopped the sprint");
        helper.assertFalse(Casting.blocksSprint(seeker), "a spell castable while moving keeps its caster from sprinting");
        askToSprint(player);
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(player.isSprinting(), "a sprint asked for during the cast was not stopped");
            assertSpeed(helper, player, walking * 0.5, "still half the walking speed");
        });
        helper.runAfterDelay(50, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.COMPLETED);
            helper.assertFalse(Casting.blocksSprint(player), "the ended cast still keeps the player from sprinting");
            askToSprint(player);
        });
        helper.runAfterDelay(52, () -> {
            helper.assertTrue(player.isSprinting(), "the player does not sprint again after the cast");
            assertSpeed(helper, player, walking * 1.3, "sprinting after the cast");
            logout(seeker);
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Cast Time Reduction shortens a channel's interval and its seconds, so it runs all its ticks and pays for all its
     * seconds in less time, then completes.
     */
    @QaGameTest
    public static void aFasterChannelKeepsItsTicks(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-cast-channel-fast");
        StatsCommands.setBase(player, GeneratedStats.CAST_TIME_REDUCTION, 0.5);
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_DRAIN);
        assertBegun(helper, player, GeneratedSpells.TEST_DRAIN, husk);
        helper.assertValueEqual(Spells.currentCast(player).map(Cast::durationTicks), Optional.of(30),
                "three ticks every half second");
        helper.runAfterDelay(15, () -> {
            assertHealth(helper, husk, HEALTH - 2, "one tick after half a second");
            assertPool(helper, player, 70, "and two seconds paid, half a second apart");
        });
        helper.runAfterDelay(40, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_DRAIN, CastEnd.COMPLETED);
            assertHealth(helper, husk, HEALTH - 6, "all three ticks");
            assertPool(helper, player, 55, "and all three seconds paid");
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

    /**
     * Death, a dimension change, logging out and a cancel interrupt a cast, and none of them lets it land; the cost it
     * paid when it began stays spent (RLM-403).
     */
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
            assertPool(helper, canceller, 80, "the cancelled cast's cost stays paid");
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
        return caster(helper, new GameProfile(UUID.randomUUID(), name));
    }

    private static ServerPlayer caster(GameTestHelper helper, GameProfile profile) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, profile, new Vec3(2.5, 1, 0.5));
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

    /**
     * {@code player}'s client asks to sprint: what the server does with {@code ServerboundPlayerCommandPacket}'s
     * {@code START_SPRINTING}, which it ignores from a fake player, whose client never reports that it loaded.
     */
    private static void askToSprint(ServerPlayer player) {
        player.setSprinting(true);
    }

    private static void assertSpeed(GameTestHelper helper, LivingEntity entity, double expected, String what) {
        double speed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(Math.abs(speed - expected) <= TOLERANCE, what + ": movement speed " + expected
                + " expected, got " + speed);
    }

    private static void assertSlowed(GameTestHelper helper, LivingEntity caster, boolean slowed, String message) {
        helper.assertValueEqual(caster.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(Casting.CAST_SLOW.id()),
                slowed, message);
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
