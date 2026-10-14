package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.mob.MobAttachments;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.mob.MobScaling;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import io.github.matejkadlec.realmforge.world.DimensionRules;
import io.github.matejkadlec.realmforge.world.MobLevelRule;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * W1's Done when (architecture 14.1): a mob's level follows the Overworld's rule by distance and depth; a leveled mob
 * has its scaled maximum health and starts with full health; a split slime's children keep the parent's level; a mob
 * added without {@code finalizeSpawn} ({@code GameTestHelper#spawn}) gets a level and its scaling; a converted mob
 * keeps a level and the scaling that belongs to it. Also: a mob from world generation is leveled when it joins, passive
 * mobs get no level, a cured mob loses it, a leveled mob's hits grow with its level, and a mob loaded from disk is
 * rescaled to the current curves, keeping its share of health.
 *
 * <p>GameTests run far from the origin (anywhere within 15 million blocks) at y -59, so a mob on a test's platform is
 * at the Overworld's top levels; tests that need other positions spawn there in absolute coordinates.
 */
public final class MobLevelGameTests {
    private static final double TOLERANCE = 1e-4;
    /** Health no test's hits use up, for a target. */
    private static final double HEALTH = 1000;

    private MobLevelGameTests() {
    }

    /** The level by distance and depth: near the origin, far from it, and deep below the origin. */
    @QaGameTest
    public static void levelFollowsDistanceAndDepth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertValueEqual(level.dimension(), Level.OVERWORLD, "GameTests run in the Overworld");
        MobLevelRule rule = DimensionRules.mobLevels(level.registryAccess(), Level.OVERWORLD).orElseThrow();

        int near = assertLevelFromTheRule(helper, rule, new BlockPos(0, 100, 0));
        int far = assertLevelFromTheRule(helper, rule, new BlockPos(100_000, 100, 0));
        assertLevelFromTheRule(helper, rule, new BlockPos(0, -40, 0));
        helper.assertTrue(rule.levelAt(100_000, 100, 0, -rule.jitter()) > rule.levelAt(0, 100, 0, rule.jitter()),
                "the Overworld's levels rise with distance beyond the jitter: " + near + " near, " + far + " far");
        helper.assertTrue(rule.levelAt(0, -40, 0, 0) > rule.levelAt(0, 100, 0, 0), "levels rise with depth");
        helper.succeed();
    }

    /** A skeleton spawned through {@code finalizeSpawn} has its scaled maximum health, and all of it. */
    @QaGameTest
    public static void spawnedMobHasItsScaledMaximumHealthInFull(GameTestHelper helper) {
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, 2, 1, 2, EntitySpawnReason.SPAWN_ITEM_USE);
        int level = assertLeveled(helper, skeleton);
        MobScaling scaling = MobScaling.at(helper.getLevel().registryAccess(), level);
        helper.assertTrue(scaling.health() > 1, "the platform's level " + level + " scales health");
        assertScaled(helper, skeleton, level);
        AttributeInstance health = skeleton.getAttribute(Attributes.MAX_HEALTH);
        helper.assertValueEqual(health.getModifiers().size(), 1, "the skeleton's only health modifier is its level's");
        assertClose(helper, skeleton.getMaxHealth(), health.getBaseValue() * scaling.health(), "maximum health");
        assertClose(helper, skeleton.getHealth(), skeleton.getMaxHealth(), "a new mob's health");
        helper.assertFalse(skeleton.hasData(MobAttachments.PENDING_REFILL), "the refill mark is gone after the join");

        // A zombie's own finalizeSpawn may raise its maximum again (the leader bonus); the join refills that too.
        for (Zombie zombie : List.of(helper.spawn(EntityType.ZOMBIE, 1, 1, 1, EntitySpawnReason.SPAWN_ITEM_USE),
                helper.spawn(EntityType.ZOMBIE, 3, 1, 3, EntitySpawnReason.SPAWN_ITEM_USE))) {
            assertScaled(helper, zombie, assertLeveled(helper, zombie));
            assertClose(helper, zombie.getHealth(), zombie.getMaxHealth(), "a new zombie's health");
        }
        helper.succeed();
    }

    /**
     * A mob spawned with a new chunk is finalized on a world generation thread, where {@code FinalizeSpawnEvent} only
     * marks it: when it joins the world, it gets its level and scaling there, and all of its health.
     */
    @QaGameTest
    public static void markedMobIsLeveledAndRefilledWhenItJoins(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.LOAD);
        helper.assertTrue(zombie != null, "a zombie can be created");
        zombie.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 2.5)), 0, 0);
        zombie.setPersistenceRequired();
        zombie.setData(MobAttachments.PENDING_REFILL, Boolean.TRUE);
        zombie.setHealth(5);
        level.addFreshEntity(zombie);

        assertScaled(helper, zombie, assertLeveled(helper, zombie));
        assertClose(helper, zombie.getHealth(), zombie.getMaxHealth(), "the marked zombie's health");
        helper.assertFalse(zombie.hasData(MobAttachments.PENDING_REFILL), "the mark is gone after the join");
        helper.succeed();
    }

    /** {@code GameTestHelper#spawn} skips {@code finalizeSpawn}; the mob still gets a level and its scaling. */
    @QaGameTest
    public static void mobAddedWithoutFinalizeSpawnGetsALevelAndItsScaling(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        int level = assertLeveled(helper, zombie);
        assertScaled(helper, zombie, level);
        MobScaling scaling = MobScaling.at(helper.getLevel().registryAccess(), level);
        assertClose(helper, zombie.getMaxHealth(),
                zombie.getAttribute(Attributes.MAX_HEALTH).getBaseValue() * scaling.health(), "maximum health");
        assertClose(helper, zombie.getHealth(), zombie.getMaxHealth(), "the zombie's health");
        helper.succeed();
    }

    /** A slime's children take its level, not the rules', with that level's scaling and full health. */
    @QaGameTest
    public static void splitSlimesKeepTheParentsLevel(GameTestHelper helper) {
        Slime slime = helper.spawn(EntityType.SLIME, new BlockPos(2, 1, 2));
        slime.setSize(4, true);
        MobLevels.setLevel(slime, 3);
        slime.setHealth(0);
        slime.remove(Entity.RemovalReason.KILLED);

        List<Slime> children = helper.getLevel().getEntities(EntityType.SLIME,
                new AABB(helper.absolutePos(new BlockPos(2, 1, 2))).inflate(4), Entity::isAlive);
        helper.assertTrue(children.size() >= 2, "the slime split: " + children.size() + " children");
        for (Slime child : children) {
            helper.assertValueEqual(child.getSize(), 2, "a child's size");
            helper.assertValueEqual(MobLevels.level(child), OptionalInt.of(3), "a child's level");
            assertScaled(helper, child, 3);
            assertClose(helper, child.getHealth(), child.getMaxHealth(), "a child's health");
        }
        helper.succeed();
    }

    /**
     * A piglin outside the Nether zombifies once its time there passes 300 ticks: the zombified piglin keeps the
     * piglin's level, and its scaling is that level's.
     */
    @QaGameTest(timeoutTicks = 40)
    public static void zombifiedPiglinKeepsItsLevelAndTheScalingOfIt(GameTestHelper helper) {
        Piglin piglin = helper.spawn(EntityType.PIGLIN, new BlockPos(2, 1, 2));
        MobLevels.setLevel(piglin, 3);
        piglin.setTimeInOverworld(AbstractPiglin.CONVERSION_TIME);
        AABB around = new AABB(helper.absolutePos(new BlockPos(2, 1, 2))).inflate(8);
        helper.succeedWhen(() -> {
            List<? extends Mob> zombified = helper.getLevel().getEntities(EntityType.ZOMBIFIED_PIGLIN, around,
                    Entity::isAlive);
            helper.assertValueEqual(zombified.size(), 1, "zombified piglins");
            Mob mob = zombified.getFirst();
            helper.assertValueEqual(MobLevels.level(mob), OptionalInt.of(3), "the zombified piglin's level");
            assertScaled(helper, mob, 3);
            assertClose(helper, mob.getHealth(), mob.getMaxHealth(), "the zombified piglin's health");
        });
    }

    /** A zombie villager cured into a villager, which gets no level, loses its level and any scaling. */
    @QaGameTest
    public static void curedMobLosesItsLevel(GameTestHelper helper) {
        ZombieVillager zombieVillager = helper.spawn(EntityType.ZOMBIE_VILLAGER, new BlockPos(2, 1, 2));
        MobLevels.setLevel(zombieVillager, 3);
        Villager villager = zombieVillager.convertTo(EntityType.VILLAGER,
                ConversionParams.single(zombieVillager, false, false),
                converted -> EventHooks.onLivingConvert(zombieVillager, converted));
        helper.assertTrue(villager != null, "the zombie villager converted");
        helper.assertFalse(villager.hasData(StatsAttachments.LEVEL), "the villager has a level");
        assertNoScaling(helper, villager);
        helper.succeed();
    }

    /** Animals and villagers get no level. */
    @QaGameTest
    public static void passiveMobsGetNoLevel(GameTestHelper helper) {
        for (Mob mob : List.<Mob>of(helper.spawn(EntityType.COW, new BlockPos(1, 1, 1)),
                helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3)),
                helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 1, 2)))) {
            helper.assertFalse(mob.hasData(StatsAttachments.LEVEL), mob.getType().getDescriptionId() + " has a level");
            assertNoScaling(helper, mob);
        }
        helper.succeed();
    }

    /** Every hit a leveled mob causes deals its level's damage multiple: melee and explosions alike. */
    @QaGameTest
    public static void leveledMobsHitHarder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 1, 0));
        zombie.setNoAi(true);
        Creeper creeper = EntityType.CREEPER.create(level, EntitySpawnReason.COMMAND);
        helper.assertTrue(creeper != null, "a creeper can be created");
        Husk target = helper.spawn(EntityType.HUSK, new BlockPos(3, 1, 3));
        target.setNoAi(true);
        MobLevels.setLevel(target, EntityLevel.MIN);
        setBase(target, Attributes.ARMOR, 0);
        setBase(target, Attributes.MAX_HEALTH, HEALTH);
        target.setHealth((float) HEALTH);

        MobLevels.setLevel(zombie, EntityLevel.MIN);
        assertDealt(helper, target, level.damageSources().mobAttack(zombie), 4, "a level 1 zombie");
        MobLevels.setLevel(zombie, 10);
        double multiple = MobScaling.at(level.registryAccess(), 10).damage();
        helper.assertTrue(multiple > 1, "level 10 raises damage");
        assertDealt(helper, target, level.damageSources().mobAttack(zombie), 4 * multiple, "a level 10 zombie");
        MobLevels.setLevel(creeper, 10);
        assertDealt(helper, target, level.damageSources().explosion(creeper, creeper), 4 * multiple,
                "a level 10 creeper's explosion");
        helper.succeed();
    }

    /**
     * A mob loaded from disk ({@code EntityJoinLevelEvent} with {@code loadedFromDisk}) is rescaled: modifiers from an
     * older curve become the current curve's, and the mob keeps its share of health.
     */
    @QaGameTest
    public static void rescalingFollowsTheCurvesAndKeepsTheHealthShare(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        int level = assertLeveled(helper, zombie);
        zombie.getAttribute(Attributes.MAX_HEALTH).addOrReplacePermanentModifier(
                new AttributeModifier(MobScaling.HEALTH_MODIFIER, 5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        zombie.getAttribute(Attributes.ARMOR).addOrReplacePermanentModifier(new AttributeModifier(
                Identifier.fromNamespaceAndPath("realmforge", "mob_level/stale"), 9,
                AttributeModifier.Operation.ADD_VALUE));
        zombie.setHealth(zombie.getMaxHealth() / 2);

        NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(zombie, helper.getLevel(), true));
        assertScaled(helper, zombie, level);
        helper.assertFalse(zombie.getAttribute(Attributes.ARMOR).hasModifier(
                Identifier.fromNamespaceAndPath("realmforge", "mob_level/stale")), "a stale modifier of ours is gone");
        assertClose(helper, zombie.getHealth(), zombie.getMaxHealth() / 2, "half health stays half");
        helper.succeed();
    }

    /**
     * Spawns a skeleton through {@code finalizeSpawn} at an absolute position, checks that its level is one the rule
     * gives there, removes it and returns the level.
     */
    private static int assertLevelFromTheRule(GameTestHelper helper, MobLevelRule rule, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        Skeleton skeleton = EntityType.SKELETON.spawn(level, pos, EntitySpawnReason.COMMAND);
        helper.assertTrue(skeleton != null, "a skeleton spawned at " + pos);
        try {
            int got = assertLeveled(helper, skeleton);
            int lowest = rule.levelAt(skeleton.getX(), skeleton.getY(), skeleton.getZ(), -rule.jitter());
            int highest = rule.levelAt(skeleton.getX(), skeleton.getY(), skeleton.getZ(), rule.jitter());
            helper.assertTrue(got >= lowest && got <= highest,
                    "level " + got + " at " + pos + ", the rule gives " + lowest + " to " + highest);
            assertScaled(helper, skeleton, got);
            return got;
        } finally {
            skeleton.discard();
        }
    }

    private static int assertLeveled(GameTestHelper helper, LivingEntity mob) {
        OptionalInt level = MobLevels.level(mob);
        helper.assertTrue(level.isPresent(), mob.getType().getDescriptionId() + " has no level");
        return level.getAsInt();
    }

    /** The mob's health and armor modifiers are its level's, as the current curves give them. */
    private static void assertScaled(GameTestHelper helper, LivingEntity mob, int level) {
        MobScaling scaling = MobScaling.at(helper.getLevel().registryAccess(), level);
        String what = mob.getType().getDescriptionId() + " at level " + level;
        assertClose(helper, amount(mob, Attributes.MAX_HEALTH, MobScaling.HEALTH_MODIFIER), scaling.health() - 1,
                what + ": health modifier");
        assertClose(helper, amount(mob, Attributes.ARMOR, MobScaling.ARMOR_MODIFIER), scaling.armor(),
                what + ": armor modifier");
        for (Holder<Attribute> attribute : List.of(Attributes.MAX_HEALTH, Attributes.ARMOR)) {
            long ours = mob.getAttribute(attribute).getModifiers().stream()
                    .filter(modifier -> MobScaling.owns(modifier.id())).count();
            helper.assertTrue(ours <= 1, what + ": more than one modifier of ours on " + attribute.getRegisteredName());
        }
    }

    private static void assertNoScaling(GameTestHelper helper, LivingEntity mob) {
        for (Holder<Attribute> attribute : List.of(Attributes.MAX_HEALTH, Attributes.ARMOR)) {
            AttributeInstance instance = mob.getAttribute(attribute);
            helper.assertTrue(instance == null || instance.getModifiers().stream()
                            .noneMatch(modifier -> MobScaling.owns(modifier.id())),
                    mob.getType().getDescriptionId() + " carries mob level scaling on " + attribute.getRegisteredName());
        }
    }

    /** The amount of a modifier, 0 when the mob lacks it (a part its level does not change). */
    private static double amount(LivingEntity mob, Holder<Attribute> attribute, Identifier id) {
        AttributeModifier modifier = mob.getAttribute(attribute).getModifier(id);
        return modifier != null ? modifier.amount() : 0;
    }

    private static void assertDealt(GameTestHelper helper, LivingEntity target, DamageSource source, double expected,
            String what) {
        target.invulnerableTime = 0;
        float before = target.getHealth();
        target.hurtServer(helper.getLevel(), source, 4);
        assertClose(helper, before - target.getHealth(), expected, what + ": damage dealt");
    }

    private static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
        entity.getAttribute(attribute).setBaseValue(value);
    }

    private static void assertClose(GameTestHelper helper, double got, double want, String what) {
        helper.assertTrue(Math.abs(got - want) <= TOLERANCE * Math.max(1, Math.abs(want)),
                what + ": expected " + want + ", got " + got);
    }
}
