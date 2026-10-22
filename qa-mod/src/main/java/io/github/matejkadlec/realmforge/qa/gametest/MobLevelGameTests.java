package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.mob.MobAttachments;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.mob.MobScaling;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import io.github.matejkadlec.realmforge.world.MobLevelRule;
import io.github.matejkadlec.realmforge.world.MobLevelRule.StructureFloor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * W1's and RLM-303's Done when (architecture 14.1): a mob's level follows the nearest player, within the rule's offsets
 * of their level and never below the realm's floor, and a mob keeps the level it spawned with; a leveled mob has its
 * scaled maximum health and starts with full health; a split slime's children keep the parent's level; a mob added
 * without {@code finalizeSpawn} ({@code GameTestHelper#spawn}) gets a level and its scaling; a converted mob keeps a
 * level and the scaling that belongs to it. Also: a mob from world generation is leveled when it joins, passive mobs
 * get no level, a cured mob loses it, a leveled mob's hits grow with its level, and a mob loaded from disk is rescaled
 * to the current curves, keeping its share of health. RLM-342's: a structure raises the floor of the mobs that get
 * their level inside its pieces, by its id or its tag, the highest floor winning.
 *
 * <p>Other tests log their own players in, so a mob that must follow a known player spawns where that player stands:
 * nobody else is nearer.
 */
public final class MobLevelGameTests {
    private static final double TOLERANCE = 1e-4;
    /** Health no test's hits use up, for a target. */
    private static final double HEALTH = 1000;
    /** Mobs rolled per player level: the chance that 40 rolls all land on the player's own level is 0.3^40. */
    private static final int ROLLS = 40;
    private static final ResourceKey<Level> STONE =
            ResourceKey.create(Registries.DIMENSION, Identifier.parse("realmforge:stone"));
    /** Where in Undervault the floor test stands, far from the other realm tests' spots. */
    private static final Vec3 IN_UNDERVAULT = new Vec3(-1000.5, 64, 1000.5);

    private MobLevelGameTests() {
    }

    /** RLM-303: mobs spawn within 3 levels of the nearest player's level, and follow it when it changes. */
    @QaGameTest
    public static void levelFollowsTheNearestPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertValueEqual(level.dimension(), Level.OVERWORLD, "GameTests run in the Overworld");
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-mob-level-near");
        try {
            setPlayerLevel(player, 30);
            Set<Integer> seen = assertLevelsNear(helper, level, player, 27, 33);
            helper.assertTrue(seen.contains(30) && seen.size() >= 3, "a spread around level 30: " + seen);
            setPlayerLevel(player, 50);
            seen = assertLevelsNear(helper, level, player, 47, 53);
            helper.assertTrue(seen.contains(50) && seen.size() >= 3, "a spread around level 50: " + seen);
            setPlayerLevel(player, 1);
            seen = assertLevelsNear(helper, level, player, 1, 4);
            helper.assertTrue(seen.contains(1), "level 1 comes up at the Overworld's floor: " + seen);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * RLM-303: Undervault's floor is 6. A player below it meets every mob at 6; one just above it meets mobs from 6
     * up, none below.
     */
    @QaGameTest
    public static void undervaultsMobsNeverGoBelowItsFloor(GameTestHelper helper) {
        ServerLevel realm = helper.getLevel().getServer().getLevel(STONE);
        helper.assertTrue(realm != null, "the GameTest world has Undervault");
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-mob-level-floor");
        try {
            award(helper, player, "realmforge:realm/stone");
            realm.getChunk(Mth.floor(IN_UNDERVAULT.x) >> 4, Mth.floor(IN_UNDERVAULT.z) >> 4);
            helper.assertTrue(player.teleportTo(realm, IN_UNDERVAULT.x, IN_UNDERVAULT.y, IN_UNDERVAULT.z, Set.of(),
                    0.0F, 0.0F, true), "the unlocked player enters Undervault");
            setPlayerLevel(player, 3);
            assertLevelsNear(helper, realm, player, 6, 6);
            setPlayerLevel(player, 7);
            Set<Integer> seen = assertLevelsNear(helper, realm, player, 6, 10);
            helper.assertTrue(seen.contains(6) && seen.contains(7), "levels 6 and 7 come up: " + seen);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * RLM-342: inside an Ancient City's piece, the Overworld's rule floors a mob at 40, a Warden included, so a level 5
     * player meets every one at 40 and a level 45 player meets them near 45; just outside the piece, in the same chunk,
     * the realm's floor holds again. World generation records a structure's start and the references to it in the
     * chunks it covers: the test records one start of one piece over the player's spot.
     */
    @QaGameTest
    public static void anAncientCityRaisesTheFloorOfTheMobsItHolds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-mob-level-structure");
        BlockPos inside = player.blockPosition();
        BlockPos outside = platformCornerInTheChunkOf(helper, inside);
        Runnable remove = placeStart(level, BuiltinStructures.ANCIENT_CITY, inside);
        Warden warden = null;
        try {
            setPlayerLevel(player, 5);
            assertLevelsNear(helper, level, player, 40, 40);
            warden = EntityType.WARDEN.spawn(level, inside, EntitySpawnReason.TRIGGERED);
            helper.assertTrue(warden != null, "a warden spawned");
            helper.assertValueEqual(MobLevels.level(warden), OptionalInt.of(40), "a warden in the Ancient City");
            assertLevelsAt(helper, level, outside, 2, 8);
            setPlayerLevel(player, 45);
            Set<Integer> seen = assertLevelsNear(helper, level, player, 42, 48);
            helper.assertTrue(seen.contains(45) && seen.size() >= 3, "a spread around level 45: " + seen);
        } finally {
            if (warden != null) {
                warden.discard();
            }
            remove.run();
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * RLM-342: a structure floor names its structure or a structure tag, and of several that hold a spot the highest
     * wins; a floor whose structure holds nothing there leaves the realm's.
     */
    @QaGameTest
    public static void aStructureTagRaisesTheFloorAndTheHighestFloorWins(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos inside = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos outside = platformCornerInTheChunkOf(helper, inside);
        StructureFloor villages = floor("#minecraft:village", 20);
        StructureFloor plains = floor("minecraft:village_plains", 25);
        StructureFloor mineshafts = floor("#minecraft:mineshaft", 30);
        List<Integer> weights = List.of(5, 10, 20, 30, 20, 10, 5);
        Runnable remove = placeStart(level, BuiltinStructures.VILLAGE_PLAINS, inside);
        try {
            helper.assertValueEqual(MobLevels.floorAt(level, inside, new MobLevelRule(1, 60, weights,
                    List.of(villages))), 20, "a plains village by its tag");
            helper.assertValueEqual(MobLevels.floorAt(level, inside, new MobLevelRule(1, 60, weights,
                    List.of(villages, plains, mineshafts))), 25, "the highest floor that holds the spot");
            helper.assertValueEqual(MobLevels.floorAt(level, inside, new MobLevelRule(1, 60, weights,
                    List.of(mineshafts))), 1, "a floor of other structures");
            helper.assertValueEqual(MobLevels.floorAt(level, outside, new MobLevelRule(1, 60, weights,
                    List.of(villages, plains))), 1, "outside the piece");
        } finally {
            remove.run();
        }
        helper.succeed();
    }

    /** RLM-303: a mob keeps the level it spawned with when the player's level changes and when it loads again. */
    @QaGameTest
    public static void aMobKeepsTheLevelItSpawnedWith(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-mob-level-keep");
        Skeleton skeleton = null;
        try {
            setPlayerLevel(player, 30);
            skeleton = EntityType.SKELETON.spawn(level, player.blockPosition(), EntitySpawnReason.COMMAND);
            helper.assertTrue(skeleton != null, "a skeleton spawned");
            int spawned = assertLeveled(helper, skeleton);
            helper.assertTrue(spawned >= 27 && spawned <= 33, "level " + spawned + " near a level 30 player");
            setPlayerLevel(player, 55);
            NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(skeleton, level, true));
            helper.assertValueEqual(MobLevels.level(skeleton), OptionalInt.of(spawned),
                    "the skeleton's level after the player leveled up and it loaded again");
            assertScaled(helper, skeleton, spawned);
        } finally {
            if (skeleton != null) {
                skeleton.discard();
            }
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** A skeleton spawned through {@code finalizeSpawn} has its scaled maximum health, and all of it. */
    @QaGameTest
    public static void spawnedMobHasItsScaledMaximumHealthInFull(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-mob-level-health");
        try {
            setPlayerLevel(player, 20);
            assertSpawnedInFull(helper);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** {@link #spawnedMobHasItsScaledMaximumHealthInFull} next to its level 20 player. */
    private static void assertSpawnedInFull(GameTestHelper helper) {
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, 2, 1, 2, EntitySpawnReason.SPAWN_ITEM_USE);
        int level = assertLeveled(helper, skeleton);
        MobScaling scaling = MobScaling.at(helper.getLevel().registryAccess(), level);
        helper.assertTrue(level >= 17 && level <= 23, "level " + level + " near a level 20 player");
        helper.assertTrue(scaling.health() > 1, "level " + level + " scales health");
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
     * Spawns {@link #ROLLS} skeletons through {@code finalizeSpawn} where the player stands, checks that each one's
     * level is within {@code lowest .. highest} and scaled to it, removes them and returns the levels that came up.
     */
    private static Set<Integer> assertLevelsNear(GameTestHelper helper, ServerLevel level, ServerPlayer player,
            int lowest, int highest) {
        return assertLevelsAt(helper, level, player.blockPosition(), lowest, highest);
    }

    /** {@link #assertLevelsNear}, with the skeletons spawning at {@code pos}. */
    private static Set<Integer> assertLevelsAt(GameTestHelper helper, ServerLevel level, BlockPos pos, int lowest,
            int highest) {
        Set<Integer> seen = new TreeSet<>();
        for (int i = 0; i < ROLLS; i++) {
            Skeleton skeleton = EntityType.SKELETON.spawn(level, pos, EntitySpawnReason.COMMAND);
            helper.assertTrue(skeleton != null, "a skeleton spawned at " + pos);
            try {
                int got = assertLeveled(helper, skeleton);
                ServerPlayer nearest = (ServerPlayer) level.getNearestPlayer(skeleton, -1);
                helper.assertTrue(got >= lowest && got <= highest, "level " + got + " at " + pos + " near a level "
                        + (nearest != null ? MobLevels.level(nearest).orElse(EntityLevel.MIN) : 0)
                        + " player, expected " + lowest + " to " + highest);
                assertScaled(helper, skeleton, got);
                seen.add(got);
            } finally {
                skeleton.discard();
            }
        }
        return seen;
    }

    /**
     * Records a start of {@code structure} in the chunk of {@code inside}, as world generation does, with one jigsaw
     * piece two blocks high over {@code inside} alone, and a reference to it in that chunk. Returns what puts the
     * chunk's starts and references back as they were.
     */
    private static Runnable placeStart(ServerLevel level, ResourceKey<Structure> key, BlockPos inside) {
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key).value();
        ChunkPos chunkPos = ChunkPos.containing(inside);
        LevelChunk chunk = level.getChunk(chunkPos.x(), chunkPos.z());
        Map<Structure, StructureStart> starts = Map.copyOf(chunk.getAllStarts());
        Map<Structure, LongSet> references = new HashMap<>();
        chunk.getAllReferences().forEach((referenced, set) -> references.put(referenced, new LongOpenHashSet(set)));
        BoundingBox box = new BoundingBox(inside.getX(), inside.getY(), inside.getZ(), inside.getX(),
                inside.getY() + 1, inside.getZ());
        PoolElementStructurePiece piece = new PoolElementStructurePiece(level.getServer().getStructureManager(),
                EmptyPoolElement.INSTANCE, inside, 0, Rotation.NONE, box, LiquidSettings.IGNORE_WATERLOGGING);
        chunk.setStartForStructure(structure,
                new StructureStart(structure, chunkPos, 0, new PiecesContainer(List.of(piece))));
        chunk.addReferenceForStructure(structure, chunkPos.pack());
        return () -> {
            chunk.setAllStarts(starts);
            chunk.setAllReferences(references);
        };
    }

    /**
     * A corner of the test's platform, where a mob stands, in the same chunk as {@code pos}: the 5×5 platform crosses
     * at most one chunk border either way, so the chunk of its middle holds one of its corners.
     */
    private static BlockPos platformCornerInTheChunkOf(GameTestHelper helper, BlockPos pos) {
        for (BlockPos corner : List.of(new BlockPos(0, 1, 0), new BlockPos(4, 1, 0), new BlockPos(0, 1, 4),
                new BlockPos(4, 1, 4))) {
            BlockPos absolute = helper.absolutePos(corner);
            if (ChunkPos.containing(absolute).equals(ChunkPos.containing(pos))) {
                return absolute;
            }
        }
        throw helper.assertionException(Component.literal("no platform corner in the chunk of " + pos));
    }

    /** A structure floor of {@code structure}, a structure's id or a structure tag after {@code #}. */
    private static StructureFloor floor(String structure, int level) {
        boolean tag = structure.startsWith("#");
        return new StructureFloor(new ExtraCodecs.TagOrElementLocation(
                Identifier.parse(tag ? structure.substring(1) : structure), tag), level);
    }

    /** Sets the level of a player without a character, as {@code /qa level} does. */
    private static void setPlayerLevel(ServerPlayer player, int level) {
        player.setData(StatsAttachments.LEVEL, new EntityLevel(level));
    }

    private static void award(GameTestHelper helper, ServerPlayer player, String advancement) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(Identifier.parse(advancement));
        helper.assertTrue(holder != null, "the advancement exists: " + advancement);
        for (String criterion : holder.value().criteria().keySet()) {
            player.getAdvancements().award(holder, criterion);
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
