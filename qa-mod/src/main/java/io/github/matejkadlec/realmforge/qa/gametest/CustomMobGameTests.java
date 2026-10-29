package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.mob.MobScaling;
import io.github.matejkadlec.realmforge.mob.ai.KnockUp;
import io.github.matejkadlec.realmforge.mob.ai.Wandering;
import io.github.matejkadlec.realmforge.mob.custom.CasterMob;
import io.github.matejkadlec.realmforge.mob.custom.CustomMob;
import io.github.matejkadlec.realmforge.mob.custom.CustomMobSpec;
import io.github.matejkadlec.realmforge.mob.custom.MeleeMob;
import io.github.matejkadlec.realmforge.qa.combat.CastLog;
import io.github.matejkadlec.realmforge.qa.combat.SoundLog;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMobExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMobExpectations.DropExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMobExpectations.MobExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMobExpectations.VariantExpectation;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.SpellProjectile;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.Targets;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * W3's Done when (RLM-33, architecture 14.2) and Matěj's playtest of it (2026-10-23): the custom mobs spawn with levels
 * and their scaling, as every hostile mob (architecture 14.1), drop only their loot (a Sentinel its own gem), every
 * drop as often as its YAML says and more with Looting, sound as their YAML says, and break apart when they die. What
 * the archetypes do: the Hewn Chanter stands still facing its target while it casts Stoneform, loses the cast with its
 * sight of the target or with its range in the first half of the cast, and steps aside between casts; its stone spares
 * the stone creatures and hurts any other creature in its way. The Hewn Sentinel, of one of three gems it keeps, hits
 * every 1.5 seconds and throws its target up. Without a target, both wander as vanilla's mobs do (Matěj's playtest,
 * 2026-10-24), and a guard walks back to its post. {@link ContentMobGameTests} checks the registrations.
 *
 * <p>Other tests log their own players in, so a mob that must follow a known player spawns where that player stands:
 * nobody else is nearer.
 */
public final class CustomMobGameTests {
    /** Rolls of a mob's loot: a 15 % drop misses all of them with a chance of 0.85^400. */
    static final int ROLLS = 400;
    /** How far a drop's share of the rolls may stray from its chance: more than five standard deviations. */
    static final double SHARE_TOLERANCE = 0.1;
    private static final double TOLERANCE = 1e-4;
    static final int LOOTING = 3;
    /** Apotheosis adds its gems to a player's kills through its own loot modifier, whatever the mob. */
    static final String APOTHEOSIS = "apotheosis";
    /** Sentinels created to see each of its three gems: one gem misses all of them with a chance of (2/3)^60. */
    private static final int SENTINELS = 60;
    /** Ticks the step test walks its mobs, a tenth of a block each. */
    private static final int STEP_TICKS = 60;
    /**
     * One tick's walk: a tenth of a block along z, and a little down onto the floor, so the mob stands on the ground,
     * which a mob without AI never checks itself, and its steps sound.
     */
    private static final Vec3 STEP = new Vec3(0, -0.05, 0.1);

    private CustomMobGameTests() {
    }

    /**
     * Each custom mob spawns near a level 20 player at a level near 20, a boss's body at its definition's fixed level
     * (W5a), with that level's scaling and full health.
     */
    @QaGameTest
    public static void customMobsSpawnWithLevelsAndTheirScaling(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-custom-mob-level");
        try {
            player.setData(StatsAttachments.LEVEL, new EntityLevel(20));
            Vec3 where = new Vec3(2.5, 1, 2.5);
            for (var type : GeneratedMobs.ALL) {
                CustomMob mob = helper.spawn(type.get(), where, EntitySpawnReason.SPAWN_ITEM_USE);
                mob.setNoAi(true);
                String what = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
                OptionalInt level = MobLevels.level(mob);
                helper.assertTrue(level.isPresent(), what + " has no level");
                OptionalInt fixed = mob instanceof MobLevels.FixedLevel boss ? boss.fixedLevel() : OptionalInt.empty();
                if (fixed.isPresent()) {
                    helper.assertValueEqual(level.getAsInt(), fixed.getAsInt(), what + "'s fixed level");
                } else {
                    helper.assertTrue(level.getAsInt() >= 17 && level.getAsInt() <= 23,
                            what + " is level " + level.getAsInt() + " near a level 20 player");
                }
                MobScaling scaling = MobScaling.at(helper.getLevel().registryAccess(), level.getAsInt());
                helper.assertTrue(scaling.health() > 1, "level " + level.getAsInt() + " scales health");
                assertClose(helper, mob.getMaxHealth(),
                        mob.getAttributeBaseValue(Attributes.MAX_HEALTH) * scaling.health(), what + " maximum health");
                assertClose(helper, mob.getHealth(), mob.getMaxHealth(), what + " starts with full health");
                mob.discard();
            }
        } finally {
            logout(player);
        }
        helper.succeed();
    }

    /**
     * Each custom mob's loot table, and each of its variants' (the mob's own drops and the variant's), gives only the
     * items of its YAML, each in its count and about as often as its chance, through the mob's own drop code
     * ({@code LivingEntity#dropFromLootTable}, as when it dies).
     */
    @QaGameTest
    public static void customMobsDropTheirLoot(GameTestHelper helper) {
        DamageSource source = helper.getLevel().damageSources().generic();
        for (MobExpectation expected : GeneratedMobExpectations.MOBS) {
            CustomMob mob = spawn(helper, expected.id());
            mob.setNoAi(true);
            helper.assertValueEqual(mob.getLootTable().map(key -> key.identifier().toString()).orElse("none"),
                    expected.lootTable(), expected.id() + " loot table");
            rollLoot(helper, mob, source, key(expected.lootTable()), expected.loot(), 0, expected.id());
            for (VariantExpectation variant : expected.variants()) {
                helper.assertTrue(mob.setVariant(variant.id()), expected.id() + " has no variant " + variant.id());
                rollLoot(helper, mob, source, mob.variantLootTable().orElseThrow(), variant.loot(), 0,
                        expected.id() + " " + variant.id());
            }
            mob.discard();
        }
        helper.succeed();
    }

    /**
     * Looting raises every drop as its YAML says, as vanilla's do: with Looting III, a drop with a Looting count gives
     * up to that many more per level, and a drop with a Looting chance drops that much more often per level. A
     * player's kill also draws Apotheosis's own drops (its gems, ADR 0038), which are not the mob's loot.
     */
    @QaGameTest
    public static void customMobsDropMoreWithLooting(GameTestHelper helper) {
        ServerPlayer killer = QaPlayers.loginOnPlatform(helper, "qa-custom-mob-looting");
        try {
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.LOOTING), LOOTING);
            killer.setItemInHand(InteractionHand.MAIN_HAND, sword);
            DamageSource source = helper.getLevel().damageSources().playerAttack(killer);
            for (MobExpectation expected : GeneratedMobExpectations.MOBS) {
                CustomMob mob = spawn(helper, expected.id());
                mob.setNoAi(true);
                rollLoot(helper, mob, source, key(expected.lootTable()), expected.loot(), LOOTING, expected.id());
                for (VariantExpectation variant : expected.variants()) {
                    mob.setVariant(variant.id());
                    rollLoot(helper, mob, source, mob.variantLootTable().orElseThrow(), variant.loot(), LOOTING,
                            expected.id() + " " + variant.id());
                }
                mob.discard();
            }
        } finally {
            logout(killer);
        }
        helper.succeed();
    }

    /**
     * Rolls {@code table} {@value #ROLLS} times: only {@code loot}'s items, each in its count (with {@code looting}
     * levels' extra) and as often as its chance ({@link #expectedShare}); with Looting, a drop that
     * Looting may raise beyond its count does so at least once.
     */
    private static void rollLoot(GameTestHelper helper, CustomMob mob, DamageSource source, ResourceKey<LootTable> table,
            List<DropExpectation> loot, int looting, String what) {
        ServerLevel level = helper.getLevel();
        Map<String, DropExpectation> drops = loot.stream()
                .collect(Collectors.toMap(DropExpectation::item, Function.identity()));
        Map<String, Integer> rolled = new HashMap<>();
        Set<String> raised = new HashSet<>();
        for (int i = 0; i < ROLLS; i++) {
            List<ItemStack> stacks = new ArrayList<>();
            mob.dropFromLootTable(level, source, false, table, stacks::add);
            for (ItemStack stack : stacks) {
                if (stack.isEmpty()) {
                    continue; // a count of 0 (vanilla's own drops spawn nothing for it)
                }
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id.getNamespace().equals(APOTHEOSIS)) {
                    continue;
                }
                String item = id.toString();
                DropExpectation drop = drops.get(item);
                helper.assertTrue(drop != null, what + " dropped " + item + ", which is not its loot");
                int most = drop.max() + looting * drop.lootingCount();
                helper.assertTrue(stack.getCount() >= drop.min() && stack.getCount() <= most,
                        what + " dropped " + stack.getCount() + " " + item + ", not " + drop.min() + " to " + most);
                if (stack.getCount() > drop.max()) {
                    raised.add(item);
                }
                rolled.merge(item, 1, Integer::sum);
            }
        }
        for (DropExpectation drop : loot) {
            double chance = expectedShare(drop, looting);
            double share = rolled.getOrDefault(drop.item(), 0) / (double) ROLLS;
            helper.assertTrue(Math.abs(share - chance) <= SHARE_TOLERANCE,
                    what + " dropped " + drop.item() + " in " + share + " of " + ROLLS + " rolls, not about " + chance);
            if (looting > 0 && drop.lootingCount() > 0) {
                helper.assertTrue(raised.contains(drop.item()),
                        what + " never dropped more than " + drop.max() + " " + drop.item() + " with Looting");
            }
        }
    }

    /**
     * The share of rolls in which {@code drop} gives any of its item with {@code looting} levels: its chance (raised by
     * its Looting chance per level), less the rolls whose count stays 0. A count from 0 to max gives none in one of
     * {@code max + 1} rolls, unless Looting's extra adds some: vanilla's {@code enchanted_count_increase} adds the levels
     * times a draw from 0 to {@code lootingCount}, rounded, which is 0 for a draw below 0.5 / levels.
     */
    static double expectedShare(DropExpectation drop, int looting) {
        double chance = Math.min(1, drop.chance() + looting * drop.lootingChance());
        if (drop.min() > 0) {
            return chance;
        }
        double noBonus = looting == 0 || drop.lootingCount() == 0
                ? 1
                : Math.min(1, 0.5 / (looting * drop.lootingCount()));
        return chance * (1 - noBonus / (drop.max() + 1));
    }

    /**
     * A Sentinel that dies drops from its variant's loot table: of {@value #SENTINELS} Topaz Sentinels, some drop Topaz
     * and none a Sapphire or a Ruby.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 100)
    public static void aHewnSentinelDropsItsOwnGem(GameTestHelper helper) {
        Vec3 where = new Vec3(4.5, 1, 4.5);
        for (int i = 0; i < SENTINELS; i++) {
            MeleeMob sentinel = helper.spawn(GeneratedMobs.HEWN_SENTINEL.get(), where);
            sentinel.setNoAi(true);
            helper.assertTrue(sentinel.setVariant("topaz"), "the Sentinel has a topaz variant");
            sentinel.kill(helper.getLevel());
        }
        helper.succeedWhen(() -> {
            Map<String, Integer> dropped = new HashMap<>();
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(9))) {
                dropped.merge(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString(),
                        item.getItem().getCount(), Integer::sum);
            }
            helper.assertTrue(dropped.containsKey("realmforge:topaz"), "no Topaz Sentinel dropped Topaz: " + dropped);
            helper.assertFalse(dropped.containsKey("realmforge:sapphire") || dropped.containsKey("realmforge:ruby"),
                    "a Topaz Sentinel dropped another gem: " + dropped);
        });
    }

    /**
     * Each custom mob plays its YAML's sounds: its hurt sound when hurt and its death sound when it dies, our own sound
     * events (Matěj's playtest heard neither). A mob with death particles breaks apart: its body is gone the tick after
     * it dies, instead of falling over for a second. The mobs stand 4 blocks apart, four to a row, all on the floor
     * (a mob off it would fall away from where its death sound played).
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 100)
    public static void customMobsSoundAndBreakApartAsTheirYamlSays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long start = level.getGameTime();
        List<CustomMob> mobs = new ArrayList<>();
        helper.assertTrue(GeneratedMobs.ALL.size() <= 16, "at most 16 mobs fit on the floor");
        for (int i = 0; i < GeneratedMobs.ALL.size(); i++) {
            CustomMob mob = helper.spawn(GeneratedMobs.ALL.get(i).get(), new Vec3(2.5 + 4 * (i % 4), 1,
                    2.5 + 4 * (i / 4)));
            mob.setNoAi(true);
            mob.hurtServer(level, level.damageSources().generic(), 1);
            mobs.add(mob);
        }
        // Past the hurt cooldown's half (10 ticks), so the killing hit counts in full and plays the death sound.
        helper.runAfterDelay(15, () -> mobs.forEach(mob -> mob.hurtServer(level, level.damageSources().generic(),
                Float.MAX_VALUE)));
        helper.runAfterDelay(17, () -> {
            for (CustomMob mob : mobs) {
                String what = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
                List<Identifier> heard = SoundLog.near(level, mob.position(), 1, start);
                CustomMobSpec.Sounds sounds = mob.spec().sounds();
                sounds.hurt().ifPresent(hurt -> helper.assertTrue(heard.contains(hurt),
                        what + " played no hurt sound " + hurt + ": " + heard));
                sounds.death().ifPresent(death -> helper.assertTrue(heard.contains(death),
                        what + " played no death sound " + death + ": " + heard));
                if (mob.spec().deathParticles().isPresent()) {
                    helper.assertTrue(mob.isRemoved() && mob.getRemovalReason() == Entity.RemovalReason.KILLED,
                            what + " broke apart: its body is gone at once");
                }
            }
            helper.succeed();
        });
    }

    /**
     * The Sentinel walks with its heavy stone steps, the Chanter without a sound: both walk 6 blocks over the floor
     * (moved as their AI moves them, without the AI, which other tests' players could draw away).
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 100)
    public static void hewnStepsSoundAsTheirYamlSays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long start = level.getGameTime();
        MeleeMob sentinel = helper.spawn(GeneratedMobs.HEWN_SENTINEL.get(), new Vec3(2.5, 1, 2.5));
        CasterMob chanter = helper.spawn(GeneratedMobs.HEWN_CHANTER.get(), new Vec3(14.5, 1, 2.5));
        sentinel.setNoAi(true);
        chanter.setNoAi(true);
        AtomicInteger walked = new AtomicInteger();
        helper.onEachTick(() -> {
            if (walked.incrementAndGet() <= STEP_TICKS) {
                sentinel.move(MoverType.SELF, STEP);
                chanter.move(MoverType.SELF, STEP);
            }
        });
        helper.runAfterDelay(STEP_TICKS + 5, () -> {
            helper.assertTrue(sentinel.position().z - helper.absoluteVec(new Vec3(2.5, 1, 2.5)).z > 5,
                    "the Sentinel walked: " + sentinel.position());
            Identifier heavyStep = sentinel.spec().sounds().step().orElseThrow();
            List<Identifier> sentinelSounds = SoundLog.near(level, helper.absoluteVec(new Vec3(2.5, 1, 5.5)), 4,
                    start);
            helper.assertTrue(sentinelSounds.contains(heavyStep), "the Sentinel's steps: " + sentinelSounds);
            List<Identifier> chanterSteps = SoundLog.near(level, helper.absoluteVec(new Vec3(14.5, 1, 5.5)), 4,
                    start).stream().filter(sound -> sound.getPath().contains("step")).toList();
            helper.assertTrue(chanterSteps.isEmpty(), "the Chanter walks silently: " + chanterSteps);
            helper.succeed();
        });
    }

    /**
     * A Sentinel rolls one of its three gems when it is created, each about as often, and keeps it when it is saved
     * and loaded; its gem picks its loot table.
     */
    @QaGameTest
    public static void aHewnSentinelRollsAGemAndKeepsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<String, Integer> rolled = new HashMap<>();
        for (int i = 0; i < SENTINELS; i++) {
            MeleeMob sentinel = GeneratedMobs.HEWN_SENTINEL.get().create(level, EntitySpawnReason.COMMAND);
            helper.assertTrue(sentinel != null, "a Sentinel can be created");
            rolled.merge(sentinel.variant().orElseThrow().id(), 1, Integer::sum);
            sentinel.discard();
        }
        helper.assertValueEqual(rolled.keySet(), Set.of("sapphire", "ruby", "topaz"), "the gems rolled");

        MeleeMob ruby = helper.spawn(GeneratedMobs.HEWN_SENTINEL.get(), new BlockPos(2, 1, 2));
        ruby.setNoAi(true);
        ruby.setVariant("ruby");
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(problems, level.registryAccess());
        ruby.saveWithoutId(output);
        CompoundTag saved = output.buildResult();
        for (int i = 0; i < 10; i++) {
            MeleeMob loaded = GeneratedMobs.HEWN_SENTINEL.get().create(level, EntitySpawnReason.LOAD);
            helper.assertTrue(loaded != null, "a Sentinel can be created");
            loaded.load(TagValueInput.create(problems, level.registryAccess(), saved));
            helper.assertValueEqual(loaded.variant().map(CustomMobSpec.Variant::id), Optional.of("ruby"),
                    "the loaded Sentinel's gem");
            helper.assertValueEqual(loaded.variantLootTable().map(key -> key.identifier().toString()),
                    Optional.of("realmforge:entities/hewn_sentinel/ruby"), "the loaded Sentinel's loot table");
            loaded.discard();
        }
        helper.assertTrue(problems.isEmpty(), "problems saving or loading the Sentinel: " + problems.getReport());
        helper.succeed();
    }

    /**
     * The Hewn Sentinel walks up to its target and hits it every 1.5 seconds (30 ticks, Matěj's playtest: half as fast
     * as vanilla's monsters), and each hit throws its target half a block up.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 200)
    public static void aHewnSentinelHitsSlowlyAndThrowsItsTargetUp(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-sentinel-target", new Vec3(4.5, 1, 4.5));
        MeleeMob sentinel = helper.spawn(GeneratedMobs.HEWN_SENTINEL.get(), new Vec3(2.5, 1, 4.5));
        int interval = sentinel.spec().melee().attackIntervalTicks();
        double throwUp = KnockUp.velocity(sentinel.spec().melee().knockUpBlocks());
        List<Integer> hits = new ArrayList<>();
        List<Double> lifts = new ArrayList<>();
        AtomicInteger lastHit = new AtomicInteger(player.getLastHurtByMobTimestamp());
        helper.onEachTick(() -> {
            sentinel.setTarget(player);
            if (player.getLastHurtByMobTimestamp() != lastHit.get()) {
                lastHit.set(player.getLastHurtByMobTimestamp());
                hits.add(lastHit.get());
                lifts.add(player.getDeltaMovement().y);
            }
            player.setHealth(player.getMaxHealth());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hits.size() >= 3, "the Sentinel hit its target 3 times: " + hits);
            for (int i = 1; i < hits.size(); i++) {
                int gap = hits.get(i) - hits.get(i - 1);
                helper.assertTrue(gap >= interval - 1, "the Sentinel hit again after " + gap + " ticks: " + hits);
            }
            for (double thrown : lifts) {
                helper.assertTrue(Math.abs(thrown - throwUp) < 0.02,
                        "a hit threw its target up at " + thrown + " blocks a tick, not " + throwUp + ": " + lifts);
            }
            logout(player);
        });
    }

    /**
     * Each custom mob wanders as vanilla's mobs do (Matěj's playtest, 2026-10-24): vanilla's random stroll at the
     * Zombified Piglin's priority, looking at players and around it, and no post. Its stroll walks it off at the Zombified
     * Piglin's speed, whatever its own (architecture 14.2).
     *
     * <p>The test triggers the stroll, instead of waiting for vanilla's roll (on average once every 6 seconds), whenever
     * the mob stands on the ground without a path, until it has wandered a block: vanilla's ground navigation makes no
     * path for a mob in the air (the tick it spawns), and most of the stroll's random spots lie on the ground below the
     * raised platform, which the mob reaches only part of the way.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void customMobsWanderAsVanillaMobsDo(GameTestHelper helper) {
        List<CustomMob> mobs = new ArrayList<>();
        Map<CustomMob, Vec3> starts = new HashMap<>();
        Map<CustomMob, Double> speeds = new HashMap<>();
        Map<CustomMob, WaterAvoidingRandomStrollGoal> strolls = new HashMap<>();
        // A boss's body guards its arena instead (W5a, BossGameTests).
        List<EntityType<? extends CustomMob>> strollers = GeneratedMobs.ALL.stream()
                .<EntityType<? extends CustomMob>>map(type -> type.get())
                .filter(type -> {
                    CustomMob probe = type.create(helper.getLevel(), EntitySpawnReason.COMMAND);
                    boolean wanders = probe != null && probe.spec().idle() == CustomMobSpec.Idle.STROLL;
                    if (probe != null) {
                        probe.discard();
                    }
                    return wanders;
                })
                .toList();
        for (int n = 0; n < strollers.size(); n++) {
            CustomMob mob = helper.spawn(strollers.get(n), new Vec3(4.5 + 8 * n, 1, 8.5));
            String what = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
            Map<Class<?>, Integer> goals = new HashMap<>();
            for (WrappedGoal goal : mob.goalSelector.getAvailableGoals()) {
                goals.merge(goal.getGoal().getClass(), goal.getPriority(), Math::min);
                if (goal.getGoal() instanceof WaterAvoidingRandomStrollGoal stroll) {
                    strolls.put(mob, stroll);
                }
            }
            helper.assertValueEqual(goals.get(WaterAvoidingRandomStrollGoal.class), Wandering.PRIORITY,
                    what + "'s stroll priority");
            helper.assertValueEqual(goals.get(LookAtPlayerGoal.class), Wandering.LOOK_PRIORITY,
                    what + "'s priority of looking at players");
            helper.assertValueEqual(goals.get(RandomLookAroundGoal.class), Wandering.LOOK_PRIORITY,
                    what + "'s priority of looking around");
            // No player of another test may draw it into a fight, which would stop its stroll.
            mob.targetSelector.removeAllGoals(goal -> true);
            mobs.add(mob);
            starts.put(mob, mob.position());
        }
        helper.onEachTick(() -> {
            for (CustomMob mob : mobs) {
                mob.setTarget(null);
                if (mob.getNavigation().isInProgress()) {
                    speeds.put(mob, mob.getMoveControl().getSpeedModifier()
                            * mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
                } else if (mob.onGround() && mob.position().subtract(starts.get(mob)).horizontalDistance() <= 1) {
                    strolls.get(mob).trigger();
                }
            }
        });
        helper.succeedWhen(() -> {
            for (CustomMob mob : mobs) {
                String what = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
                helper.assertTrue(mob.post().isEmpty(), what + " took a post: " + mob.post());
                helper.assertTrue(mob.position().subtract(starts.get(mob)).horizontalDistance() > 1,
                        what + " wandered off from " + starts.get(mob) + ": " + mob.position() + ", running "
                                + mob.goalSelector.getAvailableGoals().stream().filter(WrappedGoal::isRunning)
                                        .map(goal -> goal.getGoal().getClass().getSimpleName()).toList());
                double speed = speeds.getOrDefault(mob, 0.0);
                helper.assertTrue(Math.abs(speed - Wandering.SPEED) < TOLERANCE,
                        what + " wandered at " + speed + ", not the Zombified Piglin's " + Wandering.SPEED);
            }
        });
    }

    /**
     * A guard (a mob whose YAML says {@code idle: guard}) takes the spot where it first stands as its post, and once it
     * has no target it walks back to the post from wherever it is. No mob of the content guards since Matěj's playtest
     * of 2026-10-24, so the test makes a Hewn Sentinel that does.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 200)
    public static void aGuardWalksBackToItsPost(GameTestHelper helper) {
        EntityType<MeleeMob> type = GeneratedMobs.HEWN_SENTINEL.get();
        CustomMobSpec spec = Objects.requireNonNull(type.create(helper.getLevel(), EntitySpawnReason.STRUCTURE)).spec();
        MeleeMob sentinel = new MeleeMob(type, helper.getLevel(), new CustomMobSpec(CustomMobSpec.Idle.GUARD,
                spec.sounds(), spec.deathParticles(), spec.melee(), spec.spells(), spec.kiting(), spec.variants(),
                spec.hands()));
        sentinel.setPersistenceRequired();
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        sentinel.snapTo(start.x, start.y, start.z, 0, 0);
        helper.getLevel().addFreshEntity(sentinel);
        BlockPos post = helper.absolutePos(new BlockPos(2, 1, 2));
        // No player of another test may draw it away.
        helper.onEachTick(() -> sentinel.setTarget(null));
        AtomicBoolean moved = new AtomicBoolean();
        helper.succeedWhen(() -> {
            if (!moved.get()) {
                helper.assertTrue(sentinel.post().filter(post::equals).isPresent(),
                        "the Sentinel took its post at " + post + ": " + sentinel.post());
                Vec3 away = helper.absoluteVec(new Vec3(6.5, 1, 6.5));
                sentinel.teleportTo(away.x, away.y, away.z);
                moved.set(true);
                throw helper.assertionException("the Sentinel was moved away from its post");
            }
            helper.assertTrue(sentinel.atPost(), "the Sentinel is back at its post: " + sentinel.position());
        });
    }

    /**
     * The Hewn Chanter, 6 blocks from its target, casts Stoneform: it shows the cast to clients, makes its forming
     * sound, stands still and faces its target while it casts, and its stone (with the spell's look) flies at the
     * target and hurts it.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void aHewnChanterCastsStoneformAtItsTarget(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-chanter-target", new Vec3(12.5, 1, 8.5));
        CasterMob chanter = helper.spawn(GeneratedMobs.HEWN_CHANTER.get(), new Vec3(6.5, 1, 8.5));
        long start = helper.getLevel().getGameTime();
        float before = player.getHealth();
        AtomicBoolean shown = new AtomicBoolean();
        AtomicBoolean stood = new AtomicBoolean(true);
        AtomicBoolean faced = new AtomicBoolean(true);
        AtomicBoolean looked = new AtomicBoolean();
        AtomicReference<Vec3> castFrom = new AtomicReference<>();
        helper.onEachTick(() -> {
            chanter.setTarget(player);
            Optional<CombatState.Cast> cast = Spells.currentCast(chanter);
            if (cast.isEmpty()) {
                castFrom.set(null);
            } else {
                shown.compareAndSet(false, chanter.isCasting());
                if (castFrom.get() == null) {
                    castFrom.set(chanter.position());
                } else if (chanter.position().distanceTo(castFrom.get()) > 0.05) {
                    stood.set(false);
                }
                float toward = (float) (Mth.atan2(player.getZ() - chanter.getZ(), player.getX() - chanter.getX())
                        * Mth.RAD_TO_DEG) - 90.0F;
                if (Mth.degreesDifferenceAbs(chanter.yBodyRot, toward) > 15) {
                    faced.set(false);
                }
            }
            for (SpellProjectile stone : helper.getLevel().getEntitiesOfClass(SpellProjectile.class,
                    chanter.getBoundingBox().inflate(16))) {
                looked.compareAndSet(false, stone.texture().isPresent());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(shown.get(), "the Chanter began a cast and showed it to clients");
            helper.assertTrue(stood.get(), "the Chanter stood still while it cast");
            helper.assertTrue(faced.get(), "the Chanter faced its target while it cast");
            helper.assertTrue(looked.get(), "Stoneform's stone has its look");
            helper.assertTrue(SoundLog.near(helper.getLevel(), chanter.position(), 1, start).stream()
                    .anyMatch(sound -> sound.getPath().equals("spell.stoneform.form")), "the stone made its sound");
            helper.assertTrue(player.getHealth() < before, "Stoneform hurt the Chanter's target");
            logout(player);
        });
    }

    /**
     * Blocks that come between the Chanter and its target end its cast at once (Matěj's playtest: even at 99 %), and
     * it then goes after the target.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 300)
    public static void aHewnChanterLosesItsCastWithItsSight(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-chanter-sight", new Vec3(11.5, 1, 8.5));
        CasterMob chanter = helper.spawn(GeneratedMobs.HEWN_CHANTER.get(), new Vec3(4.5, 1, 8.5));
        AtomicReference<Vec3> walled = new AtomicReference<>();
        helper.onEachTick(() -> {
            chanter.setTarget(player);
            player.setHealth(player.getMaxHealth());
            Optional<CombatState.Cast> cast = Spells.currentCast(chanter);
            if (walled.get() == null && cast.isPresent()
                    && helper.getLevel().getGameTime() - cast.get().startGameTime() >= cast.get().durationTicks() - 3) {
                for (int z = 4; z <= 12; z++) {
                    for (int y = 1; y <= 4; y++) {
                        helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
                    }
                }
                walled.set(chanter.position());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(walled.get() != null, "a wall went up late in the Chanter's cast");
            helper.assertValueEqual(CastLog.last(chanter).map(CastLog.Ended::end), Optional.of(CastEnd.NO_LINE_OF_SIGHT),
                    "the Chanter's cast ended with its sight");
            helper.assertTrue(chanter.position().distanceTo(walled.get()) > 1.5,
                    "the Chanter went after its target: " + chanter.position());
            logout(player);
        });
    }

    /**
     * A target that leaves the Chanter's range in the first half of its cast ends the cast at once, and the Chanter
     * closes in again.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 300)
    public static void aHewnChanterLosesItsCastWhenItsTargetLeavesRangeEarly(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-chanter-early", new Vec3(8.5, 1, 8.5));
        CasterMob chanter = helper.spawn(GeneratedMobs.HEWN_CHANTER.get(), new Vec3(2.5, 1, 8.5));
        AtomicBoolean fled = new AtomicBoolean();
        AtomicReference<Double> fledTo = new AtomicReference<>();
        helper.onEachTick(() -> {
            chanter.setTarget(player);
            player.setHealth(player.getMaxHealth());
            Optional<CombatState.Cast> cast = Spells.currentCast(chanter);
            if (!fled.get() && cast.isPresent()
                    && helper.getLevel().getGameTime() - cast.get().startGameTime() >= 2) {
                QaPlayers.onPlatform(helper, player, new Vec3(15.5, 1, 8.5));
                fled.set(true);
                fledTo.set(Targets.distance(chanter, player));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(fled.get(), "the target fled early in the Chanter's cast");
            helper.assertTrue(fledTo.get() > chanter.reach(), "the target fled out of range: " + fledTo.get());
            helper.assertValueEqual(CastLog.last(chanter).map(CastLog.Ended::end), Optional.of(CastEnd.OUT_OF_RANGE),
                    "the Chanter's cast ended with its range");
            helper.assertTrue(Targets.distance(chanter, player) < fledTo.get() - 2, "the Chanter closed in");
            logout(player);
        });
    }

    /**
     * A target that leaves the Chanter's range past half of its cast is too late: the cast completes, and the stone
     * flies at it.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void aHewnChanterFinishesItsCastWhenItsTargetLeavesRangeLate(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-chanter-late", new Vec3(8.5, 1, 8.5));
        CasterMob chanter = helper.spawn(GeneratedMobs.HEWN_CHANTER.get(), new Vec3(2.5, 1, 8.5));
        AtomicBoolean fled = new AtomicBoolean();
        helper.onEachTick(() -> {
            chanter.setTarget(player);
            player.setHealth(player.getMaxHealth());
            Optional<CombatState.Cast> cast = Spells.currentCast(chanter);
            if (!fled.get() && cast.isPresent() && helper.getLevel().getGameTime() - cast.get().startGameTime()
                    >= cast.get().durationTicks() * 0.6) {
                QaPlayers.onPlatform(helper, player, new Vec3(13.5, 1, 8.5));
                fled.set(true);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(fled.get(), "the target fled late in the Chanter's cast");
            helper.assertValueEqual(CastLog.last(chanter), Optional.of(new CastLog.Ended(GeneratedSpells.STONEFORM,
                    CastEnd.COMPLETED)), "the Chanter's cast completed");
            logout(player);
        });
    }

    /** After a cast, the Chanter steps aside about 2 blocks, away from its target, and stays in range. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void aHewnChanterStepsAsideBetweenCasts(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-chanter-step", new Vec3(10.5, 1, 8.5));
        CasterMob chanter = helper.spawn(GeneratedMobs.HEWN_CHANTER.get(), new Vec3(6.5, 1, 8.5));
        AtomicReference<Vec3> castAt = new AtomicReference<>();
        AtomicReference<Double> castDistance = new AtomicReference<>();
        helper.onEachTick(() -> {
            chanter.setTarget(player);
            player.setHealth(player.getMaxHealth());
            if (castAt.get() == null && CastLog.last(chanter).map(CastLog.Ended::end).filter(CastEnd::completed)
                    .isPresent()) {
                castAt.set(chanter.position());
                castDistance.set(Targets.distance(chanter, player));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(castAt.get() != null, "the Chanter completed a cast");
            double stepped = chanter.position().distanceTo(castAt.get());
            helper.assertTrue(stepped >= 1.2, "the Chanter stepped " + stepped + " blocks");
            double distance = Targets.distance(chanter, player);
            helper.assertTrue(distance > castDistance.get() && distance <= chanter.reach(),
                    "the Chanter stepped away from its target and stayed in range: " + distance);
            logout(player);
        });
    }

    /** Stoneform's stone stops at a stone creature in its way, the Sentinel, and leaves it and the target unhurt. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void aStoneformStoneSparesStoneCreatures(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-stone-spares", new Vec3(10.5, 1, 8.5));
        CasterMob chanter = helper.spawn(GeneratedMobs.HEWN_CHANTER.get(), new Vec3(3.5, 1, 8.5));
        MeleeMob sentinel = helper.spawn(GeneratedMobs.HEWN_SENTINEL.get(), new Vec3(7.5, 1, 8.5));
        sentinel.setNoAi(true);
        assertStoneStops(helper, chanter, player, sentinel, false);
    }

    /** Stoneform's stone hits any other creature in its way, a zombie, and hurts it instead of the target. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void aStoneformStoneHurtsOtherCreaturesInItsWay(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-stone-hits", new Vec3(10.5, 1, 8.5));
        CasterMob chanter = helper.spawn(GeneratedMobs.HEWN_CHANTER.get(), new Vec3(3.5, 1, 8.5));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(7.5, 1, 8.5));
        assertStoneStops(helper, chanter, player, zombie, true);
    }

    /**
     * The Chanter's first completed cast at {@code player} sends a stone into {@code inWay}, which it hurts or not,
     * and never into the player.
     */
    private static void assertStoneStops(GameTestHelper helper, CasterMob chanter, ServerPlayer player,
            LivingEntity inWay, boolean hurts) {
        float playerHealth = player.getHealth();
        float inWayHealth = inWay.getHealth();
        AtomicLong completedAt = new AtomicLong(-1);
        helper.onEachTick(() -> {
            chanter.setTarget(player);
            if (completedAt.get() < 0 && CastLog.last(chanter).map(CastLog.Ended::end).filter(CastEnd::completed)
                    .isPresent()) {
                completedAt.set(helper.getLevel().getGameTime());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(completedAt.get() >= 0
                    && helper.getLevel().getGameTime() - completedAt.get() >= 15, "the Chanter's stone flew");
            helper.assertValueEqual(player.getHealth(), playerHealth, "the target is unhurt");
            helper.assertValueEqual(inWay.getHealth() < inWayHealth, hurts, "the creature in the stone's way is hurt");
            logout(player);
        });
    }

    private static ResourceKey<LootTable> key(String table) {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table));
    }

    private static CustomMob spawn(GameTestHelper helper, String id) {
        for (var type : GeneratedMobs.ALL) {
            if (type.getId().toString().equals(id)) {
                return helper.spawn(type.get(), new BlockPos(2, 1, 2));
            }
        }
        throw helper.assertionException(id + " is not one of GeneratedMobs.ALL");
    }

    /** A fake player in survival at {@code where} on the platform. */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 where) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, where);
        player.setGameMode(GameType.SURVIVAL);
        // A fake player's client never reports that it loaded, which keeps the player invulnerable.
        player.connection.markClientLoaded();
        return player;
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    private static void assertClose(GameTestHelper helper, double got, double want, String what) {
        helper.assertTrue(Math.abs(got - want) <= TOLERANCE * Math.max(1, Math.abs(want)),
                what + ": expected " + want + ", got " + got);
    }
}
