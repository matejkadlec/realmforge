package io.github.matejkadlec.realmforge.qa.gametest;

import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.HEALTH;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.assertEnded;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.assertHealth;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.caster;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.husk;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.learn;
import static io.github.matejkadlec.realmforge.qa.gametest.CastGameTests.logout;

import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Casting;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAim;
import io.github.matejkadlec.realmforge.spell.SpellArea;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.SpellProjectile;
import io.github.matejkadlec.realmforge.spell.SpellTarget;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * C3a-3 (RLM-211): the spell entities and the remaining effect and targeting types (architecture 5.3, 11.3). Players
 * at the platform's south edge, facing south, cast the test spells of {@code content/spells/} at husks: {@code
 * test_missile} (a projectile: 5 fire damage and Slowness for 3 s), {@code test_forward_missile} (a projectile along
 * the caster's look: 3 frost damage), {@code test_scorch} (an area at a ground point: 2 fire damage every second for
 * 3 s within 2 blocks), {@code test_mending_ring} (an area at the caster's feet: 2 healing every second for 2 s to its
 * allies within 3 blocks), {@code test_chill} (Slowness II for 3 s), {@code test_cleanse} (removes Slowness),
 * {@code test_blink} (a teleport up to 4 blocks along the look), {@code test_charge} (a dash at an enemy) and {@code
 * test_shove} (a knockback). The server ticks the spell entities, so the checks wait real ticks, with slack.
 */
public final class SpellEffectGameTests {
    private static final double TOLERANCE = 1e-4;

    private SpellEffectGameTests() {
    }

    /** A projectile flies to its enemy and runs its effects on it once, then is gone. */
    @QaGameTest
    public static void aProjectileHitsItsEnemyAndRunsItsEffects(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-effect-missile");
        Husk husk = husk(helper, new BlockPos(2, 1, 4));
        learn(helper, player, GeneratedSpells.TEST_MISSILE);
        CastResult result = SpellCaster.cast(player, GeneratedSpells.TEST_MISSILE, husk);
        helper.assertTrue(result.success() && !result.begun(), "test_missile was not cast: " + result);
        helper.assertValueEqual(spellEntities(helper, SpellProjectile.class).size(), 1, "projectiles in flight");
        assertHealth(helper, husk, HEALTH, "nothing lands before the projectile arrives");
        helper.runAfterDelay(15, () -> {
            assertHealth(helper, husk, HEALTH - 5, "the projectile hit for 5");
            MobEffectInstance slowness = husk.getEffect(MobEffects.SLOWNESS);
            helper.assertTrue(slowness != null && slowness.getAmplifier() == 0 && slowness.getDuration() <= 60,
                    "the projectile's Slowness I for 3 s: " + slowness);
            helper.assertValueEqual(spellEntities(helper, SpellProjectile.class).size(), 0,
                    "the projectile is gone after its hit");
            logout(player);
            helper.succeed();
        });
    }

    /** A projectile along a direction hits the first enemy in its way, without a target aimed at. */
    @QaGameTest
    public static void aProjectileAlongADirectionHitsTheEnemyInItsWay(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-effect-forward");
        Husk husk = husk(helper, new BlockPos(2, 1, 4));
        learn(helper, player, GeneratedSpells.TEST_FORWARD_MISSILE);
        CastResult result = SpellCaster.cast(player, GeneratedSpells.TEST_FORWARD_MISSILE, SpellAim.NONE);
        helper.assertTrue(result.success(), "test_forward_missile was refused: " + result.failure());
        helper.assertValueEqual(result.target().map(SpellTarget::kind), Optional.of(SpellTarget.Kind.DIRECTION),
                "the target is a direction");
        helper.assertTrue(result.target().orElseThrow().direction().distanceTo(new Vec3(0, 0, 1)) < TOLERANCE,
                "the direction is the caster's look, south: " + result.target());
        helper.runAfterDelay(15, () -> {
            assertHealth(helper, husk, HEALTH - 3, "the projectile hit the husk ahead for 3");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * An area at a ground point runs its effects on the enemies inside at the end of every interval for its duration,
     * and on nobody outside it or allied to the caster; then it is gone.
     */
    @QaGameTest(timeoutTicks = 120)
    public static void anAreaRunsItsEffectsOnTheEnemiesInsideAtItsInterval(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-effect-scorch");
        Husk inside = husk(helper, new BlockPos(2, 1, 3));
        Husk outside = husk(helper, new BlockPos(0, 1, 0));
        learn(helper, player, GeneratedSpells.TEST_SCORCH);
        Vec3 point = helper.absoluteVec(new Vec3(2.5, 1, 3.5));
        CastResult result = SpellCaster.cast(player, GeneratedSpells.TEST_SCORCH, SpellAim.at(point));
        helper.assertTrue(result.success(), "test_scorch was refused: " + result.failure());
        List<SpellArea> areas = spellEntities(helper, SpellArea.class);
        helper.assertValueEqual(areas.size(), 1, "areas placed");
        helper.assertTrue(areas.getFirst().position().distanceTo(point) < TOLERANCE, "the area lies at the point");
        float playerHealth = player.getHealth();
        helper.runAfterDelay(12, () -> assertHealth(helper, inside, HEALTH, "no tick before the first interval"));
        helper.runAfterDelay(30, () -> assertHealth(helper, inside, HEALTH - 2, "one tick after a second"));
        helper.runAfterDelay(50, () -> assertHealth(helper, inside, HEALTH - 4, "two ticks after two seconds"));
        helper.runAfterDelay(75, () -> {
            assertHealth(helper, inside, HEALTH - 6, "three ticks in its 3 s");
            assertHealth(helper, outside, HEALTH, "the husk beyond its radius");
            assertHealth(helper, player, playerHealth, "the caster, its own ally, inside its reach");
            helper.assertValueEqual(spellEntities(helper, SpellArea.class).size(), 0, "the area is gone");
            logout(player);
            helper.succeed();
        });
    }

    /** An area that affects allies heals the caster and its allies inside it, never an enemy. */
    @QaGameTest
    public static void anAreaForAlliesHealsTheAlliesInside(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-effect-ring");
        ServerPlayer ally = caster(helper, "qa-effect-ring-ally");
        ally.snapTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
        Husk husk = husk(helper, new BlockPos(3, 1, 1));
        husk.setHealth(500);
        for (ServerPlayer hurt : List.of(player, ally)) {
            // No food or stat regeneration in the way: below 18 food nothing heals by itself.
            hurt.getFoodData().setFoodLevel(17);
            StatsCommands.setBase(hurt, GeneratedStats.HEALTH_REGENERATION, 0);
            hurt.setHealth(10);
        }
        learn(helper, player, GeneratedSpells.TEST_MENDING_RING);
        CastResult result = SpellCaster.cast(player, GeneratedSpells.TEST_MENDING_RING, SpellAim.NONE);
        helper.assertTrue(result.success(), "test_mending_ring was refused: " + result.failure());
        helper.runAfterDelay(55, () -> {
            assertHealth(helper, player, 14, "the caster healed twice by 2");
            assertHealth(helper, ally, 14, "and its ally");
            assertHealth(helper, husk, 500, "the husk inside is no ally");
            logout(ally);
            logout(player);
            helper.succeed();
        });
    }

    /** {@code apply_effect} gives the target a status effect; {@code remove_effect} takes one off the caster or an ally. */
    @QaGameTest
    public static void statusEffectsAreAppliedAndRemoved(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-effect-status");
        ServerPlayer ally = caster(helper, "qa-effect-status-ally");
        ally.snapTo(helper.absoluteVec(new Vec3(0.5, 1, 0.5)), 0, 0);
        Husk husk = husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_CHILL);
        learn(helper, player, GeneratedSpells.TEST_CLEANSE);

        assertCast(helper, player, GeneratedSpells.TEST_CHILL, SpellAim.at(husk));
        MobEffectInstance chill = husk.getEffect(MobEffects.SLOWNESS);
        helper.assertTrue(chill != null && chill.getAmplifier() == 1 && chill.getDuration() == 60,
                "Slowness II for 3 s on the husk: " + chill);

        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200));
        ally.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200));
        resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_CLEANSE, SpellAim.NONE);
        helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), "the cleanse took Slowness off the caster");
        helper.assertTrue(ally.hasEffect(MobEffects.SLOWNESS), "and only off the caster");
        resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_CLEANSE, SpellAim.at(ally));
        helper.assertFalse(ally.hasEffect(MobEffects.SLOWNESS), "the cleanse at the ally took its Slowness off");
        helper.assertTrue(husk.hasEffect(MobEffects.SLOWNESS), "the husk keeps its Slowness");
        logout(ally);
        logout(player);
        helper.succeed();
    }

    /** A teleport moves the caster along its look by the targeting's range, and stops short of a wall. */
    @QaGameTest
    public static void aTeleportMovesTheCasterAlongItsLookShortOfBlocks(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-effect-blink");
        learn(helper, player, GeneratedSpells.TEST_BLINK);
        assertCast(helper, player, GeneratedSpells.TEST_BLINK, SpellAim.NONE);
        assertAt(helper, player, new Vec3(2.5, 1, 4.5), "4 blocks south");

        helper.setBlock(new BlockPos(2, 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.STONE);
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 0.5)), 0, 0);
        resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_BLINK, SpellAim.NONE);
        assertAt(helper, player, new Vec3(2.5, 1, 2.5), "in front of the wall");
        helper.assertTrue(player.level().noCollision(player), "the caster stands free");
        logout(player);
        helper.succeed();
    }

    /** A dash throws the caster at its enemy. A mob casts it, since the server moves mobs, not players. */
    @QaGameTest
    public static void aDashThrowsTheCasterAtItsEnemy(GameTestHelper helper) {
        Husk caster = freeHusk(helper, new BlockPos(2, 1, 0));
        Husk target = husk(helper, new BlockPos(2, 1, 4));
        double start = caster.getZ();
        CastResult result = SpellCaster.cast(caster, GeneratedSpells.TEST_CHARGE, target);
        helper.assertTrue(result.success(), "test_charge was refused: " + result.failure());
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(caster.getZ() - start > 1.5, "the husk rushed south toward its target: from " + start
                    + " to " + caster.getZ());
            helper.succeed();
        });
    }

    /**
     * A knockback pushes the target away from the caster and interrupts its cast; a cast that may be cast while moving
     * goes on.
     */
    @QaGameTest
    public static void aKnockbackPushesTheTargetAndInterruptsItsCast(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-effect-shove");
        Husk pushed = freeHusk(helper, new BlockPos(2, 1, 2));
        Husk target = husk(helper, new BlockPos(0, 1, 4));
        learn(helper, player, GeneratedSpells.TEST_SHOVE);
        CastResult begun = SpellCaster.cast(pushed, GeneratedSpells.TEST_SLOW_BOLT, target);
        helper.assertTrue(begun.begun(), "the husk's test_slow_bolt did not begin: " + begun);
        double start = pushed.getZ();
        assertCast(helper, player, GeneratedSpells.TEST_SHOVE, SpellAim.at(pushed));
        assertEnded(helper, pushed, GeneratedSpells.TEST_SLOW_BOLT, CastEnd.KNOCKED_BACK);

        Husk seeker = freeHusk(helper, new BlockPos(4, 1, 2));
        helper.assertTrue(SpellCaster.cast(seeker, GeneratedSpells.TEST_HOMING_BOLT, target).begun(),
                "the seeker's test_homing_bolt did not begin");
        resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_SHOVE, SpellAim.at(seeker));
        helper.assertTrue(Casting.isCasting(seeker),
                "a knockback leaves a cast that may be cast while moving");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(pushed.getZ() - start > 0.5, "the husk was pushed south, away from the caster: from "
                    + start + " to " + pushed.getZ());
            logout(player);
            helper.succeed();
        });
    }

    /** A ground point out of range, or one behind blocks, refuses the cast. */
    @QaGameTest
    public static void aGroundPointMustBeInRangeAndInSight(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-effect-point");
        learn(helper, player, GeneratedSpells.TEST_SCORCH);
        helper.assertValueEqual(SpellCaster.cast(player, GeneratedSpells.TEST_SCORCH,
                SpellAim.at(helper.absoluteVec(new Vec3(2.5, 1, 30)))).failureOrNull(), CastFailure.OUT_OF_RANGE,
                "a point 30 blocks away");
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        helper.assertValueEqual(SpellCaster.cast(player, GeneratedSpells.TEST_SCORCH,
                SpellAim.at(helper.absoluteVec(new Vec3(2.5, 1, 3.5)))).failureOrNull(), CastFailure.NO_LINE_OF_SIGHT,
                "a point behind a wall");
        logout(player);
        helper.succeed();
    }

    /** A husk with its physics but no AI goals, at level 1, that knockback pushes fully. */
    private static Husk freeHusk(GameTestHelper helper, BlockPos pos) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, pos);
        MobLevels.setLevel(husk, EntityLevel.MIN);
        CombatGameTests.setBase(husk, Attributes.MAX_HEALTH, HEALTH);
        CombatGameTests.setBase(husk, Attributes.KNOCKBACK_RESISTANCE, 0);
        husk.setHealth((float) HEALTH);
        return husk;
    }

    private static void assertCast(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            SpellAim aim) {
        CastResult result = SpellCaster.cast(caster, spell, aim);
        helper.assertTrue(result.success() && !result.begun(), spell.identifier() + " was not cast: " + result);
    }

    private static void assertAt(GameTestHelper helper, Entity entity, Vec3 expected, String what) {
        Vec3 at = helper.relativeVec(entity.position());
        helper.assertTrue(at.distanceTo(expected) < 0.01, what + ": at " + expected + " expected, got " + at);
    }

    private static void resetCooldowns(LivingEntity caster) {
        Spells.resetCooldowns(caster);
    }

    /** The spell entities of {@code type} in this test's space; the tests beside it have their own. */
    private static <T extends Entity> List<T> spellEntities(GameTestHelper helper, Class<T> type) {
        return helper.getLevel().getEntitiesOfClass(type, helper.getBounds());
    }
}
