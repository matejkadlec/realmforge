package io.github.matejkadlec.realmforge.qa.gametest;

import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.HEALTH;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.assertEnded;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.assertHealth;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.caster;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.husk;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.learn;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.logout;

import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.qa.combat.SoundLog;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAim;
import io.github.matejkadlec.realmforge.spell.SpellArea;
import io.github.matejkadlec.realmforge.spell.SpellBar;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.SpellProjectile;
import io.github.matejkadlec.realmforge.spell.SpellTarget;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.net.CastTargetPayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Spell aiming (RLM-344, RLM-346) and aim assistance (RLM-345; architecture 11.2, 11.3). Players cast the test
 * spells of {@code content/spells/}: {@code test_forward_missile} (an instant frost bolt along the caster's look, 20
 * blocks a second, no lifetime, 3 damage, shattering like ice where it hits), {@code test_slow_missile} (the same after
 * a 1 s cast, 4 damage, a 2 s lifetime), {@code test_scorch} (an area at a ground point within 16 blocks), and the
 * targeted {@code test_bolt} (instant, 6 damage) and {@code test_slow_bolt} (a 2 s cast, 10 damage), both 16 blocks,
 * and the ally spell {@code test_mend} (instant, a heal of 4, 16 blocks).
 * They aim as the spell bar does: at nothing, so the bolt flies where the caster looks and the area lands where it
 * looks, or at an entity or a point.
 *
 * <p>A bolt without a lifetime flies until something stops it, so the tests that could miss put a wall behind their
 * mobs: a stray bolt must not reach a test beside this one.
 */
public final class SpellAimingGameTests {
    private static final double TOLERANCE = 0.01;
    private static final Identifier SHATTER = Identifier.withDefaultNamespace("block.glass.break");
    private static final double RANGE = 16;

    private SpellAimingGameTests() {
    }

    /** A bolt cast at the sky flies away, straight up, and is removed once it is above the build height. */
    @QaGameTest
    public static void aBoltAtTheSkyIsRemovedAboveTheBuildHeight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = caster(helper, "qa-aim-sky");
        learn(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE);
        Vec3 ground = helper.absoluteVec(new Vec3(2.5, 1, 0.5));
        player.snapTo(new Vec3(ground.x, level.getMaxY() - 2, ground.z), 0, -90);
        SpellProjectile bolt = castBolt(helper, player);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(bolt.isRemoved(), "the bolt is gone 10 blocks up: at " + bolt.position());
            helper.assertTrue(bolt.getY() > level.getMaxY() + 1, "it got above the build height, " + level.getMaxY()
                    + ": at " + bolt.position());
            logout(player);
            helper.succeed();
        });
    }

    /**
     * A bolt fired down from above the build height flies on and hits the mob below (RLM-446): the caster stands
     * higher than any block can be, as on the top block layer or flying above it.
     */
    @QaGameTest
    public static void aBoltFiredDownFromAboveTheBuildHeightHitsTheMobBelow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = caster(helper, "qa-aim-down");
        learn(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE);
        Vec3 ground = helper.absoluteVec(new Vec3(2.5, 1, 0.5));
        Husk husk = husk(helper, new BlockPos(2, 1, 0));
        husk.setNoGravity(true);
        husk.snapTo(new Vec3(ground.x, level.getMaxY() - 3, ground.z), 0, 0);
        player.snapTo(new Vec3(ground.x, level.getMaxY() + 3, ground.z), 0, 90);
        SpellProjectile bolt = castBolt(helper, player);
        helper.assertTrue(bolt.getY() > level.getMaxY() + 1, "it starts above the build height, " + level.getMaxY()
                + ": at " + bolt.position());
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(bolt.isRemoved(), "the bolt stopped at the husk: at " + bolt.position());
            assertHealth(helper, husk, HEALTH - 3, "the husk below took the bolt");
            logout(player);
            helper.succeed();
        });
    }

    /** Above the build height, a bolt fired level or up is removed on its first tick: it is not coming back down. */
    @QaGameTest
    public static void aBoltFiredLevelOrUpAboveTheBuildHeightIsRemovedAtOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = caster(helper, "qa-aim-level");
        learn(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE);
        Vec3 ground = helper.absoluteVec(new Vec3(2.5, 1, 0.5));
        player.snapTo(new Vec3(ground.x, level.getMaxY() + 3, ground.z), 0, 0);
        SpellProjectile levelBolt = castBolt(helper, player);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(levelBolt.isRemoved(), "the level bolt is gone: at " + levelBolt.position());
            Spells.resetCooldowns(player);
            player.snapTo(new Vec3(ground.x, level.getMaxY() + 3, ground.z), 0, -90);
            SpellProjectile up = castBolt(helper, player);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(up.isRemoved(), "the bolt fired up is gone: at " + up.position());
                logout(player);
                helper.succeed();
            });
        });
    }

    /**
     * A bolt that leaves the chunks where entities move (a player's simulation distance; here the test's forced chunks)
     * is removed there, instead of hanging in the air: fired level above everything, nothing else can stop it.
     */
    @QaGameTest(timeoutTicks = 600)
    public static void aBoltIsRemovedWhereEntitiesStopMoving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = caster(helper, "qa-aim-far");
        learn(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE);
        Vec3 ground = helper.absoluteVec(new Vec3(2.5, 1, 0.5));
        player.snapTo(new Vec3(ground.x, level.getMaxY() - 20, ground.z), 0, 0);
        SpellProjectile bolt = castBolt(helper, player);
        helper.succeedWhen(() -> {
            helper.assertTrue(bolt.isRemoved(), "the bolt still flies at " + bolt.position());
            BlockPos next = BlockPos.containing(bolt.position().add(bolt.getDeltaMovement()));
            helper.assertFalse(level.isPositionEntityTicking(next), "it was removed where entities stop moving, "
                    + "not before: at " + bolt.position());
            logout(player);
        });
    }

    /** A bolt cast at a wall crashes there: it shatters on the wall, and the mob behind it is untouched. */
    @QaGameTest
    public static void aBoltCrashesIntoAWall(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-aim-wall");
        wall(helper, 3, 3);
        Husk behind = husk(helper, new BlockPos(2, 1, 4));
        learn(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE);
        long since = helper.getLevel().getGameTime();
        castBolt(helper, player);
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(spellEntities(helper, SpellProjectile.class).size(), 0, "bolts in flight");
            assertHealth(helper, behind, HEALTH, "the husk behind the wall");
            List<Identifier> sounds = SoundLog.near(helper.getLevel(), helper.absoluteVec(new Vec3(2.5, 2.6, 3)), 1,
                    since);
            helper.assertTrue(sounds.contains(SHATTER), "the bolt shattered on the wall: " + sounds);
            logout(player);
            helper.succeed();
        });
    }

    /** A bolt damages the first mob it reaches, a passive cow as much as a hostile husk. */
    @QaGameTest
    public static void aBoltHitsPassiveAndHostileMobsAlike(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-aim-mobs");
        wall(helper, 4, 4);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(1, 1, 3));
        cow.setNoAi(true);
        Husk husk = husk(helper, new BlockPos(3, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE);
        assertCast(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE, SpellAim.at(cow));
        Spells.resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE, SpellAim.at(husk));
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "the cow took the bolt: " + cow.getHealth());
            assertHealth(helper, husk, HEALTH - 3, "the husk took the bolt");
            logout(player);
            helper.succeed();
        });
    }

    /** An entity that cannot be damaged (an invulnerable husk) stops a bolt untouched; the husk behind it is spared. */
    @QaGameTest
    public static void anEntityThatCannotBeDamagedStopsABolt(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-aim-shield");
        wall(helper, 4, 4);
        Husk shield = husk(helper, new BlockPos(2, 1, 2));
        shield.setInvulnerable(true);
        Husk behind = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE);
        long since = helper.getLevel().getGameTime();
        assertCast(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE, SpellAim.at(behind));
        helper.runAfterDelay(15, () -> {
            helper.assertValueEqual(spellEntities(helper, SpellProjectile.class).size(), 0, "bolts in flight");
            assertHealth(helper, shield, HEALTH, "the invulnerable husk");
            assertHealth(helper, behind, HEALTH, "the husk behind it");
            List<Identifier> sounds = SoundLog.near(helper.getLevel(), shield.position(), 3, since);
            helper.assertTrue(sounds.contains(SHATTER), "the bolt shattered on the invulnerable husk: " + sounds);
            logout(player);
            helper.succeed();
        });
    }

    /** A bolt flies through its caster's allies (another player) to the mob behind them. */
    @QaGameTest
    public static void aBoltPassesThroughAllies(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-aim-ally");
        ServerPlayer ally = caster(helper, "qa-aim-ally-front");
        ally.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 2)), 0, 0);
        wall(helper, 4, 4);
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        float allyHealth = ally.getHealth();
        learn(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE);
        assertCast(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE, SpellAim.at(husk));
        helper.runAfterDelay(15, () -> {
            assertHealth(helper, ally, allyHealth, "the ally in the way");
            assertHealth(helper, husk, HEALTH - 3, "the husk behind the ally");
            logout(ally);
            logout(player);
            helper.succeed();
        });
    }

    /**
     * A cast-time bolt aimed at nothing flies where the caster looks when the cast completes (the crosshair then), not
     * where it looked when the cast began.
     */
    @QaGameTest
    public static void aCastTimeBoltFliesWhereTheCasterLooksAtTheEnd(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-aim-turn");
        Husk first = husk(helper, new BlockPos(2, 1, 4));
        Husk turnedTo = husk(helper, new BlockPos(0, 1, 4));
        learn(helper, player, GeneratedSpells.TEST_SLOW_MISSILE);
        lookAt(player, first.getBoundingBox().getCenter());
        CastResult result = SpellCaster.cast(player, GeneratedSpells.TEST_SLOW_MISSILE, SpellAim.NONE);
        helper.assertTrue(result.begun(), "test_slow_missile did not begin: " + result);
        helper.assertTrue(result.target().map(SpellTarget::followsLook).orElse(false),
                "a direction from the look follows it: " + result.target());
        helper.runAfterDelay(10, () -> lookAt(player, turnedTo.getBoundingBox().getCenter()));
        helper.runAfterDelay(45, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_MISSILE, CastEnd.COMPLETED);
            assertHealth(helper, turnedTo, HEALTH - 4, "the bolt hit the husk the caster turned to");
            assertHealth(helper, first, HEALTH, "the husk it looked at when the cast began");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Aim assistance (RLM-345): a bolt cast where the crosshair points goes to a hostile mob beside it. At 12 blocks
     * the cone allows 1.5 blocks, and the husk's near side is 0.7 blocks beside the crosshair; the bolt homes in on it.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void anAssistedBoltHitsAHostileMobBesideTheCrosshair(GameTestHelper helper) {
        ServerPlayer player = assistedCaster(helper, "qa-assist-beside");
        Husk husk = husk(helper, new BlockPos(9, 1, 12));
        SpellProjectile bolt = castBolt(helper, player);
        helper.assertValueEqual(bolt.homing(), Optional.<LivingEntity>of(husk), "the bolt homes in on the husk");
        helper.runAfterDelay(20, () -> {
            assertHealth(helper, husk, HEALTH - 3, "the husk beside the crosshair");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Beyond the cone (a husk 2.7 blocks beside the crosshair at 12 blocks, where the cone allows 1.5) and for a
     * passive mob in it (a cow 0.55 blocks beside it at 8 blocks, Q6), a bolt gets no assistance: it flies straight
     * where the crosshair points and crashes into the wall behind them.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void aBoltGetsNoAssistanceBeyondTheConeOrTowardAPassiveMob(GameTestHelper helper) {
        ServerPlayer player = assistedCaster(helper, "qa-assist-beyond");
        Husk husk = husk(helper, new BlockPos(11, 1, 12));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(9, 1, 8));
        cow.setNoAi(true);
        cow.snapTo(helper.absoluteVec(new Vec3(9.5, 1, 8.5)), 0, 0);
        SpellProjectile bolt = castBolt(helper, player);
        helper.assertValueEqual(bolt.homing(), Optional.<LivingEntity>empty(), "the bolt homes in on");
        helper.runAfterDelay(20, () -> {
            assertHealth(helper, husk, HEALTH, "the husk beyond the cone");
            helper.assertValueEqual(cow.getHealth(), cow.getMaxHealth(), "the cow's health");
            helper.assertValueEqual(spellEntities(helper, SpellProjectile.class).size(), 0, "bolts in flight");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * An assisted bolt follows a mob on the move: the husk walks east 3 blocks a second, so a straight bolt at where
     * it stood would miss it by more than a block when it gets there; the homing bolt curves after it and hits.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void anAssistedBoltFollowsAMovingMob(GameTestHelper helper) {
        ServerPlayer player = assistedCaster(helper, "qa-assist-moving");
        Husk husk = husk(helper, new BlockPos(9, 1, 12));
        Vec3 start = husk.position();
        long cast = helper.getLevel().getGameTime();
        castBolt(helper, player);
        helper.onEachTick(() -> {
            long ticks = Math.min(helper.getLevel().getGameTime() - cast, 20);
            husk.setPos(start.x + 0.15 * ticks, start.y, start.z);
        });
        helper.succeedWhen(() -> {
            assertHealth(helper, husk, HEALTH - 3, "the moving husk");
            logout(player);
        });
    }

    /**
     * A player's targeted cast-time spell keeps its target beyond the spell's range (the sticky target, RLM-345):
     * Test Slow Bolt (16 blocks, 2 s) cast at a husk 6 blocks away lands after the husk went 22 blocks away. A second
     * cast ends at once, out of range, when the husk goes more than twice the range away (40 blocks up).
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void aStickyTargetIsKeptBeyondTheRangeAndLostBeyondTwiceIt(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-assist-sticky");
        player.snapTo(helper.absoluteVec(new Vec3(0.5, 1, 0.5)), 0, 0);
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        Husk husk = husk(helper, new BlockPos(0, 1, 6));
        Vec3 near = husk.position();
        CastGameTests.assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk);
        helper.runAfterDelay(5, () -> husk.teleportTo(near.x + 16, near.y, near.z + 10));
        helper.runAfterDelay(50, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.COMPLETED);
            assertHealth(helper, husk, HEALTH - 10, "the husk 22 blocks away took the bolt");
            Spells.resetCooldowns(player);
            husk.teleportTo(near.x, near.y, near.z);
            CastGameTests.assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk);
        });
        helper.runAfterDelay(55, () -> husk.teleportTo(near.x, near.y + 40, near.z));
        helper.runAfterDelay(57, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.OUT_OF_RANGE);
            assertHealth(helper, husk, HEALTH - 10, "the second bolt never landed");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * A sticky target that dies during the cast ends it at once, the target lost, and the cast picks no other: Q3.
     * Its text is still "You have no target" (RLM-468 gives it its own).
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void aStickyTargetThatDiesEndsTheCastAtOnce(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-assist-dies");
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        Husk husk = husk(helper, new BlockPos(2, 1, 6));
        Husk other = husk(helper, new BlockPos(3, 1, 6));
        CastGameTests.assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk);
        helper.runAfterDelay(5, () -> husk.kill(helper.getLevel()));
        helper.runAfterDelay(7, () -> {
            assertEnded(helper, player, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.TARGET_LOST);
            assertHealth(helper, other, HEALTH, "the husk beside it");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * From the spell bar, a targeted spell whose client named no target goes to the hostile mob beside the crosshair
     * (Test Bolt, 6 damage; 0.7 blocks beside at 8 blocks, where the cone allows 1). A target the client names behind
     * the player is refused: the server checks the widest view a client can have.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void theSpellBarAssistsATargetedSpellAndRefusesATargetBehindThePlayer(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-assist-bar");
        player.snapTo(helper.absoluteVec(new Vec3(8.5, 1, 4.5)), 0, 0);
        learn(helper, player, GeneratedSpells.TEST_BOLT);
        Husk ahead = husk(helper, new BlockPos(9, 1, 12));
        Husk behind = husk(helper, new BlockPos(8, 1, 1));
        CastResult assisted = SpellBar.cast(player, GeneratedSpells.TEST_BOLT, Optional.empty(), false);
        helper.assertTrue(assisted.success(), "test_bolt was refused: " + assisted.failure());
        helper.assertTrue(assisted.entity().orElse(null) == ahead, "it went to " + assisted.target());
        assertHealth(helper, ahead, HEALTH - 6, "the husk beside the crosshair");
        Spells.resetCooldowns(player);
        CastResult refused = SpellBar.cast(player, GeneratedSpells.TEST_BOLT, Optional.of(behind.getId()), false);
        helper.assertValueEqual(refused.failureOrNull(), CastFailure.INVALID_TARGET, "a target behind the player");
        assertHealth(helper, behind, HEALTH, "the husk behind the player");
        logout(player);
        helper.succeed();
    }

    /**
     * From the spell bar, an ally spell whose client named no target goes to the player beside the crosshair (Test
     * Mend, a heal of 4; 0.7 blocks beside at 8 blocks, where the cone allows 1: {@code AimAssist#ally}). With nobody
     * near the crosshair, it heals the caster, as a WoW heal without a friendly target.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void theSpellBarAssistsAnAllySpellToAPlayerBesideTheCrosshair(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-assist-ally");
        player.snapTo(helper.absoluteVec(new Vec3(8.5, 1, 4.5)), 0, 0);
        learn(helper, player, GeneratedSpells.TEST_MEND);
        ServerPlayer ally = caster(helper, "qa-assist-ally-hurt");
        ally.snapTo(helper.absoluteVec(new Vec3(9.5, 1, 12.5)), 0, 0);
        player.setHealth(player.getMaxHealth() - 10);
        ally.setHealth(ally.getMaxHealth() - 10);
        float allyHealth = ally.getHealth();
        float playerHealth = player.getHealth();

        CastResult assisted = SpellBar.cast(player, GeneratedSpells.TEST_MEND, Optional.empty(), false);
        helper.assertTrue(assisted.success(), "test_mend was refused: " + assisted.failure());
        helper.assertTrue(assisted.entity().orElse(null) == ally, "it went to " + assisted.target());
        helper.assertTrue(ally.getHealth() > allyHealth, "the ally beside the crosshair was not healed");
        helper.assertValueEqual(player.getHealth(), playerHealth, "the caster's health");

        Spells.resetCooldowns(player);
        ally.snapTo(helper.absoluteVec(new Vec3(15.5, 1, 12.5)), 0, 0);
        CastResult self = SpellBar.cast(player, GeneratedSpells.TEST_MEND, Optional.empty(), false);
        helper.assertTrue(self.success(), "test_mend was refused: " + self.failure());
        helper.assertTrue(self.entity().orElse(null) == player, "with nobody near the crosshair it went to "
                + self.target());
        helper.assertTrue(player.getHealth() > playerHealth, "the caster was not healed");
        logout(ally);
        logout(player);
        helper.succeed();
    }

    /**
     * A calm neutral mob gets no assistance (Q6): a bolt cast with a calm wolf beside the crosshair (0.77 blocks at 12
     * blocks, where the cone allows 1.5) flies straight on. Once the wolf is angry at the caster, the next bolt homes in
     * on it ({@code AimAssist#hostile}).
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void anAssistedBoltSkipsACalmNeutralMobButNotAnAngryOne(GameTestHelper helper) {
        ServerPlayer player = assistedCaster(helper, "qa-assist-neutral");
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(9, 1, 12));
        wolf.setNoAi(true);
        wolf.snapTo(helper.absoluteVec(new Vec3(9.5, 1, 12.5)), 0, 0);
        helper.assertValueEqual(castBolt(helper, player).homing(), Optional.<LivingEntity>empty(),
                "the bolt beside a calm wolf homes in on");
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(spellEntities(helper, SpellProjectile.class).size(), 0, "bolts in flight");
            wolf.setPersistentAngerTarget(EntityReference.<LivingEntity>of(player));
            wolf.setTimeToRemainAngry(400);
            Spells.resetCooldowns(player);
            helper.assertValueEqual(castBolt(helper, player).homing(), Optional.<LivingEntity>of(wolf),
                    "the bolt beside an angry wolf homes in on");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * The target circle's mob (RLM-345, Q4; {@code Casting#circleTarget}): the server names it to the caster's client
     * in {@code CastTargetPayload} when a cast-time spell begins. A cast at an entity names its target; a bolt that
     * follows the crosshair names the mob aim assistance would send it to, a husk beside the crosshair.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void aCastNamesTheMobUnderItsTargetCircle(GameTestHelper helper) {
        ServerPlayer player = assistedCaster(helper, "qa-assist-circle");
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        learn(helper, player, GeneratedSpells.TEST_SLOW_MISSILE);
        Husk aside = husk(helper, new BlockPos(4, 1, 6));
        Husk beside = husk(helper, new BlockPos(9, 1, 12));
        QaPlayers.sentPayloads(player, CastTargetPayload.class);

        CastGameTests.assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, aside);
        helper.assertValueEqual(QaPlayers.sentPayloads(player, CastTargetPayload.class),
                List.of(new CastTargetPayload(Optional.of(aside.getId()))), "the circle of a cast at an entity");
        Spells.cancelCast(player);
        Spells.resetCooldowns(player);

        CastResult missile = SpellCaster.cast(player, GeneratedSpells.TEST_SLOW_MISSILE, SpellAim.NONE);
        helper.assertTrue(missile.success() && missile.begun(), "test_slow_missile did not begin: " + missile);
        helper.assertValueEqual(QaPlayers.sentPayloads(player, CastTargetPayload.class),
                List.of(new CastTargetPayload(Optional.of(beside.getId()))),
                "the circle of a bolt that follows the crosshair");
        Spells.cancelCast(player);
        logout(player);
        helper.succeed();
    }

    /** A ground spell aimed at the floor within its range lands where the caster looks. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void aGroundSpellLandsWhereTheCasterLooks(GameTestHelper helper) {
        ServerPlayer player = groundCaster(helper, "qa-aim-ground");
        lookAt(player, helper.absoluteVec(new Vec3(8.5, 1, 6.5)));
        assertLanding(helper, player, SpellAim.NONE, helper.absoluteVec(new Vec3(8.5, 1, 6.5)), "6 blocks ahead");
        logout(player);
        helper.succeed();
    }

    /**
     * A ground spell aimed at the floor beyond its range clamps: it lands at the farthest point in range along the
     * look, on the floor below it.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void aGroundSpellAimedBeyondItsRangeLandsAtTheClampedPoint(GameTestHelper helper) {
        ServerPlayer player = groundCaster(helper, "qa-aim-clamp");
        lookAt(player, helper.absoluteVec(new Vec3(8.5, 1, 60.5)));
        Vec3 clamped = player.getEyePosition().add(player.getLookAngle().scale(RANGE));
        assertLanding(helper, player, SpellAim.NONE, onFloor(helper, clamped), "16 blocks along the look");
        logout(player);
        helper.succeed();
    }

    /**
     * A ground spell aimed at the sky lands on the floor below the point 16 blocks along the look. The look rises 10
     * degrees, so that point stays below the barriers the test framework puts over the platform.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void aGroundSpellAimedAtTheSkyLandsBelowTheClampedPoint(GameTestHelper helper) {
        ServerPlayer player = groundCaster(helper, "qa-aim-up");
        player.snapTo(player.position(), 0, -10);
        Vec3 clamped = player.getEyePosition().add(player.getLookAngle().scale(RANGE));
        assertLanding(helper, player, SpellAim.NONE, onFloor(helper, clamped), "below the point in the sky");
        logout(player);
        helper.succeed();
    }

    /** A ground spell aimed at a ceiling lands on the floor below the spot the look hit. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void aGroundSpellAimedAtACeilingLandsOnTheFloorBelow(GameTestHelper helper) {
        ServerPlayer player = groundCaster(helper, "qa-aim-ceiling");
        for (int x = 7; x <= 9; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            }
        }
        lookAt(player, helper.absoluteVec(new Vec3(8.5, 4, 4.5)));
        assertLanding(helper, player, SpellAim.NONE, helper.absoluteVec(new Vec3(8.5, 1, 4.5)), "below the ceiling");
        logout(player);
        helper.succeed();
    }

    /** A ground spell aimed at a wall lands at its foot, on the floor in front of it. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void aGroundSpellAimedAtAWallLandsAtItsFoot(GameTestHelper helper) {
        ServerPlayer player = groundCaster(helper, "qa-aim-ground-wall");
        for (int x = 6; x <= 10; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
            }
        }
        assertLanding(helper, player, SpellAim.NONE, helper.absoluteVec(new Vec3(8.5, 1, 6)), "at the wall's foot");
        logout(player);
        helper.succeed();
    }

    /**
     * A ground spell aimed at a point beyond its range clamps along the line toward it; one aimed at a point in range
     * that the caster cannot see is refused. Aimed at a mob behind a low wall, it lands at the mob's feet.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void aGroundSpellAimedAtAPointOrAMob(GameTestHelper helper) {
        ServerPlayer player = groundCaster(helper, "qa-aim-point");
        Vec3 far = helper.absoluteVec(new Vec3(8.5, 1, 40.5));
        Vec3 toward = far.subtract(player.getEyePosition()).normalize();
        assertLanding(helper, player, SpellAim.at(far),
                onFloor(helper, player.getEyePosition().add(toward.scale(RANGE))), "a point 40 blocks away, clamped");

        for (int x = 6; x <= 10; x++) {
            helper.setBlock(new BlockPos(x, 1, 5), Blocks.STONE);
        }
        Husk husk = husk(helper, new BlockPos(8, 1, 6));
        Spells.resetCooldowns(player);
        assertLanding(helper, player, SpellAim.at(husk), husk.position(), "the husk's feet behind the low wall");

        for (int x = 6; x <= 10; x++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
            }
        }
        Spells.resetCooldowns(player);
        helper.assertValueEqual(SpellCaster.cast(player, GeneratedSpells.TEST_SCORCH,
                SpellAim.at(helper.absoluteVec(new Vec3(8.5, 1, 7.5)))).failureOrNull(),
                CastFailure.NO_LINE_OF_SIGHT, "a point in range behind a wall");
        logout(player);
        helper.succeed();
    }

    /**
     * A player with {@code test_forward_missile} at the 17 x 17 platform's north edge, in the middle, facing south, and
     * a wall across the platform 15 blocks ahead, which stops every bolt that misses.
     */
    private static ServerPlayer assistedCaster(GameTestHelper helper, String name) {
        ServerPlayer player = caster(helper, name);
        player.snapTo(helper.absoluteVec(new Vec3(8.5, 1, 0.5)), 0, 0);
        learn(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE);
        for (int x = 0; x < 17; x++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 15), Blocks.STONE);
            }
        }
        return player;
    }

    /** A player with {@code test_scorch} at the 17 x 17 platform's north edge, in the middle, facing south. */
    private static ServerPlayer groundCaster(GameTestHelper helper, String name) {
        ServerPlayer player = caster(helper, name);
        player.snapTo(helper.absoluteVec(new Vec3(8.5, 1, 0.5)), 0, 0);
        learn(helper, player, GeneratedSpells.TEST_SCORCH);
        return player;
    }

    /** Casts {@code test_scorch} at {@code aim} and checks that it lands at {@code expected} (absolute). */
    private static void assertLanding(GameTestHelper helper, ServerPlayer player, SpellAim aim, Vec3 expected,
            String what) {
        CastResult result = SpellCaster.cast(player, GeneratedSpells.TEST_SCORCH, aim);
        helper.assertTrue(result.success(), "test_scorch was refused (" + what + "): " + result.failure());
        Vec3 landing = result.target().orElseThrow().position();
        helper.assertTrue(landing.distanceTo(expected) < TOLERANCE, what + ": landed at " + helper.relativeVec(landing)
                + ", expected " + helper.relativeVec(expected));
        List<SpellArea> areas = helper.getLevel().getEntitiesOfClass(SpellArea.class, new AABB(landing, landing)
                .inflate(TOLERANCE));
        helper.assertValueEqual(areas.size(), 1, "areas at the landing (" + what + ")");
        areas.forEach(Entity::discard);
    }

    /** {@code point} (absolute) moved down onto the top of the platform's floor. */
    private static Vec3 onFloor(GameTestHelper helper, Vec3 point) {
        return new Vec3(point.x, helper.absoluteVec(new Vec3(0, 1, 0)).y, point.z);
    }

    /** Casts {@code test_forward_missile} where the player looks and returns its bolt, just launched. */
    private static SpellProjectile castBolt(GameTestHelper helper, ServerPlayer player) {
        assertCast(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE, SpellAim.NONE);
        List<SpellProjectile> bolts = helper.getLevel().getEntitiesOfClass(SpellProjectile.class,
                new AABB(player.getEyePosition(), player.getEyePosition()).inflate(1.5));
        helper.assertValueEqual(bolts.size(), 1, "bolts launched");
        return bolts.getFirst();
    }

    private static void assertCast(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            SpellAim aim) {
        CastResult result = SpellCaster.cast(caster, spell, aim);
        helper.assertTrue(result.success() && !result.begun(), spell.identifier() + " was not cast: " + result);
    }

    /**
     * Turns {@code caster} to look from its eyes at {@code at} (absolute), as {@code Entity#lookAt} does, without the
     * packet a player's would send to its client.
     */
    static void lookAt(LivingEntity caster, Vec3 at) {
        Vec3 offset = at.subtract(caster.getEyePosition());
        double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        caster.setXRot((float) -Math.toDegrees(Math.atan2(offset.y, horizontal)));
        caster.setYRot((float) Math.toDegrees(Math.atan2(offset.z, offset.x)) - 90);
        caster.setYHeadRot(caster.getYRot());
    }

    /** A stone wall across the 5 x 5 platform at {@code z}, from the floor up to {@code height} blocks. */
    private static void wall(GameTestHelper helper, int z, int height) {
        for (int x = 0; x < 5; x++) {
            for (int y = 1; y <= height; y++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    /** The spell entities of {@code type} in this test's space; the tests beside it have their own. */
    private static <T extends Entity> List<T> spellEntities(GameTestHelper helper, Class<T> type) {
        return helper.getLevel().getEntitiesOfClass(type, helper.getBounds());
    }
}
