package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.logging.LogUtils;
import io.github.matejkadlec.realmforge.generated.GeneratedBlocks;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRealmExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRealmExpectations.RealmExpectation;
import io.github.matejkadlec.realmforge.world.realm.Realms;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * The stone realm, Undervault (W2a, RLM-32; realm portal spec, "Access and unlocks", "Multiplayer", "Respawn"): a
 * player teleported into the realm is refused before its unlock and let in after it, a mob is kept out of the realm
 * and inside it, a death in the realm respawns in Overworld, a parrot on the shoulder stays behind, beds there set no
 * spawn and do not explode, the realm's terrain is one walkable cave floor under a ceiling, and its ores are Matěj's
 * ore table (RLM-449).
 *
 * <p>Positions in the realm are absolute: {@code /execute in} would scale relative ones by the realm's coordinate
 * scale. The realm's chunks at its origin are generated on first use, on the server thread.
 */
public final class RealmGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Somewhere above the realm's floor near its origin; the trips here need no safe spot. */
    private static final Vec3 IN_REALM = new Vec3(8.5, 64, 8.5);

    private RealmGameTests() {
    }

    /** Done when of W2a: a player teleported into the realm is refused before the unlock and allowed after it. */
    @QaGameTest
    public static void playerIsRefusedBeforeTheUnlockAndLetInAfterIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RealmExpectation expected = stone();
        ServerLevel realm = realm(helper, expected);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-realm-access");
        try {
            Vec3 start = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
            player.teleportTo(helper.getLevel(), start.x, start.y, start.z, Set.of(), 0.0F, 0.0F, true);

            helper.assertFalse(teleport(player, realm, IN_REALM), "a teleport into the realm is refused without its unlock");
            helper.assertTrue(player.level() == helper.getLevel(), "the refused player stays in Overworld");
            helper.assertTrue(player.position().closerThan(start, 0.01), "the refused player stays where they were");

            award(helper, player, expected.unlockAdvancement());
            helper.assertTrue(teleport(player, realm, IN_REALM), "a teleport into the realm passes with its unlock");
            helper.assertTrue(player.level() == realm, "the unlocked player is in the realm");

            helper.assertTrue(teleport(player, helper.getLevel(), start), "the way back to Overworld is always open");
            helper.assertTrue(player.level() == helper.getLevel(), "the player is back in Overworld");
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * Done when of W2a: a mob teleported into the realm stays where it is, as does one teleported out of it, while a
     * mob teleported within the realm moves; both of vanilla's ways to move an entity are covered.
     */
    @QaGameTest
    public static void mobsStayOutOfTheRealmAndInsideIt(GameTestHelper helper) {
        ServerLevel realm = realm(helper, stone());
        Zombie outside = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        Vec3 where = outside.position();
        helper.assertTrue(outside.teleport(transition(realm, IN_REALM)) == null,
                "Entity#teleport into the realm is refused for a mob");
        helper.assertFalse(outside.teleportTo(realm, IN_REALM.x, IN_REALM.y, IN_REALM.z, Set.of(), 0.0F, 0.0F, false),
                "Entity#teleportTo (/tp) into the realm is refused for a mob");
        helper.assertTrue(outside.level() == helper.getLevel() && !outside.isRemoved()
                && outside.position().closerThan(where, 0.01), "the mob stays where it was");

        Zombie inside = EntityType.ZOMBIE.create(realm, EntitySpawnReason.COMMAND);
        helper.assertTrue(inside != null, "a zombie can be created in the realm");
        inside.snapTo(IN_REALM.x, IN_REALM.y, IN_REALM.z);
        realm.addFreshEntity(inside);
        try {
            Vec3 out = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
            helper.assertTrue(inside.teleport(transition(helper.getLevel(), out)) == null,
                    "a mob in the realm cannot be teleported out of it");
            helper.assertTrue(inside.level() == realm && !inside.isRemoved(), "the mob stays in the realm");
            Vec3 within = IN_REALM.add(3, 0, 0);
            helper.assertTrue(inside.teleportTo(realm, within.x, within.y, within.z, Set.of(), 0.0F, 0.0F, false),
                    "a teleport within the realm passes");
            helper.assertTrue(inside.position().closerThan(within, 0.01), "the mob moved within the realm");
        } finally {
            inside.discard();
        }
        helper.succeed();
    }

    /**
     * Done when of W2a: a death in the realm respawns in Overworld, also for a player whose spawn point a command set
     * in the realm.
     */
    @QaGameTest
    public static void deathInTheRealmRespawnsInOverworld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RealmExpectation expected = stone();
        ServerLevel realm = realm(helper, expected);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-realm-respawn");
        try {
            award(helper, player, expected.unlockAdvancement());
            helper.assertTrue(teleport(player, realm, IN_REALM), "the unlocked player enters the realm");
            player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            helper.assertTrue(player.level().dimension() == Level.OVERWORLD,
                    "a death in the realm respawns in Overworld, not in " + player.level().dimension().identifier());

            // A spawn point set in the realm by command (beds there set none): two free blocks, so vanilla takes it.
            BlockPos spawn = BlockPos.containing(IN_REALM);
            realm.setBlockAndUpdate(spawn, Blocks.AIR.defaultBlockState());
            realm.setBlockAndUpdate(spawn.above(), Blocks.AIR.defaultBlockState());
            player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                    LevelData.RespawnData.of(realm.dimension(), spawn, 0.0F, 0.0F), true), false);
            helper.assertTrue(teleport(player, realm, IN_REALM), "the player enters the realm again");
            player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            helper.assertTrue(player.level().dimension() == Level.OVERWORLD,
                    "a spawn point set in the realm still respawns in Overworld");
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * A parrot on a player's shoulder stays in the level the player leaves, on a trip into the realm and at a death in
     * it, also when it landed on the shoulder in the same tick (vanilla's own drop waits 20 ticks). The second parrot
     * perches once the realm's chunk has loaded its entities, so it is a real entity there before it sits down. The
     * GameTest server runs its ticks back to back, so the test generates that cave chunk on the server thread, as
     * {@code MobLevelGameTests} does: generated in the background, it was ready in time only when an earlier test had
     * made it, and the test alone timed out on tick 602. Loading its entities still runs off the server thread (about
     * 70 ticks alone); the timeout leaves room for a CI runner busy with the other GameTests' chunks.
     */
    @QaGameTest(timeoutTicks = 600)
    public static void shoulderParrotsStayBehind(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RealmExpectation expected = stone();
        ServerLevel realm = realm(helper, expected);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-realm-parrot");
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.teleportTo(helper.getLevel(), start.x, start.y, start.z, Set.of(), 0.0F, 0.0F, true);
        award(helper, player, expected.unlockAdvancement());

        UUID first = perchParrot(helper, player, helper.getLevel());
        helper.assertTrue(teleport(player, realm, IN_REALM), "the player enters the realm");
        helper.assertTrue(player.getShoulderEntityLeft().isEmpty(), "the shoulder is empty after the trip");
        helper.assertTrue(helper.getLevel().getEntity(first) instanceof Parrot,
                "the parrot stayed in Overworld, the level the player left");
        helper.assertTrue(realm.getEntity(first) == null, "the parrot did not come along");

        long chunk = ChunkPos.containing(BlockPos.containing(IN_REALM)).pack();
        realm.getChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(realm.areEntitiesLoaded(chunk), "the realm's chunk is loading"))
                .thenExecute(() -> {
                    UUID second = perchParrot(helper, player, realm);
                    ServerPlayer respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
                    try {
                        helper.assertTrue(respawned.getShoulderEntityLeft().isEmpty(),
                                "the respawned player has no parrot");
                        helper.assertTrue(realm.getEntity(second) instanceof Parrot,
                                "the parrot stayed in the realm at the death");
                    } finally {
                        server.getPlayerList().remove(respawned);
                    }
                })
                .thenSucceed();
    }

    /** A bed in the realm sets no spawn point and does not explode: a player always respawns in Overworld. */
    @QaGameTest
    public static void bedsInTheRealmSetNoSpawnAndDoNotExplode(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RealmExpectation expected = stone();
        ServerLevel realm = realm(helper, expected);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-realm-bed");
        try {
            award(helper, player, expected.unlockAdvancement());
            helper.assertTrue(teleport(player, realm, IN_REALM), "the player enters the realm");
            BlockPos foot = BlockPos.containing(IN_REALM).east();
            BlockPos head = foot.north();
            BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            realm.setBlockAndUpdate(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
            realm.setBlockAndUpdate(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
            realm.setBlockAndUpdate(foot.below(), Blocks.STONE.defaultBlockState());
            realm.setBlockAndUpdate(head.below(), Blocks.STONE.defaultBlockState());

            // As a right-click on the bed: where beds explode (the Nether), this is the explosion.
            realm.getBlockState(head).useWithoutItem(realm, player,
                    new BlockHitResult(Vec3.atCenterOf(head), Direction.UP, head, false));
            helper.assertTrue(player.startSleepInBed(head).left().isPresent(), "nobody sleeps in the realm");
            helper.assertFalse(player.isSleeping(), "the player is not asleep");
            helper.assertTrue(player.getRespawnConfig() == null, "a bed in the realm sets no spawn point");
            helper.assertTrue(realm.getBlockState(head).is(Blocks.RED_BED)
                    && realm.getBlockState(foot).is(Blocks.RED_BED), "the bed did not explode");
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * The realm is one walkable cave floor under a ceiling (Matěj, 2026-10-15): bedrock at the bottom and the top,
     * and in most columns around the origin a floor at about y 32 with tens of blocks of air above it; pillars fill the
     * rest. Coreslate lies below the floor with the ancient debris, siltstone in the ceiling, mottlestone in patches.
     * Logs the measured shape.
     */
    @QaGameTest(timeoutTicks = 400)
    public static void theRealmIsOneCaveFloorUnderACeiling(GameTestHelper helper) {
        ServerLevel realm = realm(helper, stone());
        int open = 0;
        int columns = 0;
        int lowestFloor = Integer.MAX_VALUE;
        int highestFloor = Integer.MIN_VALUE;
        int debris = 0;
        int debrisAboveFloor = 0;
        int coreslateBelow = 0;
        int siltstoneAbove = 0;
        int mottlestone = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int chunkX = -2; chunkX < 2; chunkX++) {
            for (int chunkZ = -2; chunkZ < 2; chunkZ++) {
                ChunkAccess chunk = realm.getChunk(chunkX, chunkZ);
                ChunkPos chunkPos = chunk.getPos();
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int worldX = chunkPos.getMinBlockX() + x;
                        int worldZ = chunkPos.getMinBlockZ() + z;
                        columns++;
                        helper.assertTrue(chunk.getBlockState(pos.set(worldX, 0, worldZ)).is(Blocks.BEDROCK),
                                "bedrock at the realm's bottom");
                        helper.assertTrue(chunk.getBlockState(pos.set(worldX, 127, worldZ)).is(Blocks.BEDROCK),
                                "bedrock at the realm's top");
                        int floor = -1;
                        int air = 0;
                        for (int y = 1; y < 127; y++) {
                            BlockState state = chunk.getBlockState(pos.set(worldX, y, worldZ));
                            coreslateBelow += y < 20 && state.is(GeneratedBlocks.CORESLATE.get()) ? 1 : 0;
                            siltstoneAbove += y > 110 && state.is(GeneratedBlocks.SILTSTONE.get()) ? 1 : 0;
                            mottlestone += state.is(GeneratedBlocks.MOTTLESTONE.get()) ? 1 : 0;
                            if (state.is(Blocks.ANCIENT_DEBRIS)) {
                                debris++;
                                if (y > 30) {
                                    debrisAboveFloor++;
                                }
                            }
                            if (state.isAir()) {
                                if (floor < 0) {
                                    floor = y - 1;
                                }
                                air++;
                            }
                        }
                        if (floor >= 0 && air >= 40) {
                            open++;
                            lowestFloor = Math.min(lowestFloor, floor);
                            highestFloor = Math.max(highestFloor, floor);
                        }
                    }
                }
            }
        }
        LOGGER.info("Undervault around its origin: {} of {} columns open, floor at y {} to {}, {} ancient debris "
                + "({} above y 30)", open, columns, lowestFloor, highestFloor, debris, debrisAboveFloor);
        helper.assertTrue(open * 2 >= columns, "at least half the columns are open cave: " + open + " of " + columns);
        helper.assertTrue(lowestFloor >= 22 && highestFloor <= 42,
                "the walkable floor lies at about y 32: y " + lowestFloor + " to " + highestFloor);
        helper.assertValueEqual(debrisAboveFloor, 0, "ancient debris above the floor");
        helper.assertTrue(debris > 0, "ancient debris lies below the floor");
        helper.assertTrue(coreslateBelow > columns * 15, "coreslate below the floor: " + coreslateBelow + " blocks");
        helper.assertTrue(siltstoneAbove > columns * 10, "siltstone in the ceiling: " + siltstoneAbove + " blocks");
        helper.assertTrue(mottlestone > 0, "patches of mottlestone");
        assertCaveShape(helper, realm);
        helper.succeed();
    }

    /**
     * Matěj's ore table (2026-10-25, RLM-449): iron, gold and emerald anywhere, in the stones of the walkable cave and
     * as their deepslate ores in the coreslate below the floor; ancient debris only below the floor, about twice the
     * Nether's rate; no coal and no diamond, which stay Overworld's. Logs each ore per chunk, and how many of them
     * touch air in their chunk (what a player walking the cave sees; the coreslate's ores are found by digging down),
     * for tuning the frequencies.
     */
    @QaGameTest(timeoutTicks = 400)
    public static void theRealmGeneratesMatejsOreTable(GameTestHelper helper) {
        ServerLevel realm = realm(helper, stone());
        Map<Block, int[]> ores = new LinkedHashMap<>();
        for (Block ore : List.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
                Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.ANCIENT_DEBRIS, Blocks.COAL_ORE,
                Blocks.DEEPSLATE_COAL_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE)) {
            ores.put(ore, new int[2]); // blocks, and those touching air in their chunk
        }
        int chunks = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int chunkX = -2; chunkX < 2; chunkX++) {
            for (int chunkZ = -2; chunkZ < 2; chunkZ++) {
                ChunkAccess chunk = realm.getChunk(chunkX, chunkZ);
                chunks++;
                ChunkPos chunkPos = chunk.getPos();
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 1; y < 127; y++) {
                            int[] count = ores.get(chunk.getBlockState(
                                    pos.set(chunkPos.getMinBlockX() + x, y, chunkPos.getMinBlockZ() + z)).getBlock());
                            if (count == null) {
                                continue;
                            }
                            count[0]++;
                            count[1] += touchesAir(chunk, pos) ? 1 : 0;
                        }
                    }
                }
            }
        }
        StringBuilder table = new StringBuilder();
        for (Map.Entry<Block, int[]> ore : ores.entrySet()) {
            table.append(String.format(" %s %.1f (%.1f seen)", BuiltInRegistries.BLOCK.getKey(ore.getKey()).getPath(),
                    ore.getValue()[0] / (double) chunks, ore.getValue()[1] / (double) chunks));
        }
        LOGGER.info("Undervault's ores per chunk over {} chunks:{}", chunks, table);
        for (Block absent : List.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.DIAMOND_ORE,
                Blocks.DEEPSLATE_DIAMOND_ORE)) {
            helper.assertValueEqual(ores.get(absent)[0], 0, absent + " in Undervault");
        }
        for (Block inTheCave : List.of(Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.EMERALD_ORE)) {
            helper.assertTrue(ores.get(inTheCave)[1] > 0, inTheCave + " where a player sees it");
        }
        for (Block belowTheFloor : List.of(Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_GOLD_ORE,
                Blocks.DEEPSLATE_EMERALD_ORE)) {
            helper.assertTrue(ores.get(belowTheFloor)[0] > 0, belowTheFloor + " in the coreslate below the floor");
        }
        // Two veins of each size per chunk lay about 3.8 blocks a chunk; W2a's one of each, half that.
        helper.assertTrue(ores.get(Blocks.ANCIENT_DEBRIS)[0] >= chunks * 2,
                "ancient debris at about twice the Nether's rate: " + ores.get(Blocks.ANCIENT_DEBRIS)[0] + " in "
                        + chunks + " chunks");
        helper.assertValueEqual(ores.get(Blocks.ANCIENT_DEBRIS)[1], 0, "ancient debris touching air");
        helper.succeed();
    }

    /** Whether a side of the block at {@code pos} faces air inside its chunk. */
    private static boolean touchesAir(ChunkAccess chunk, BlockPos pos) {
        ChunkPos chunkPos = chunk.getPos();
        for (Direction direction : Direction.values()) {
            BlockPos side = pos.relative(direction);
            if (chunkPos.contains(side) && chunk.getBlockState(side).isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The terrain's density function over 2,048 by 2,048 blocks, one column every 16 blocks, without generating the
     * chunks: most columns are open cave whose floor lies within a few blocks of y 32 under a ceiling at about y 100,
     * and the rest are pillars from floor to ceiling.
     */
    private static void assertCaveShape(GameTestHelper helper, ServerLevel realm) {
        DensityFunction density = realm.getChunkSource().randomState().router().finalDensity();
        int columns = 0;
        int open = 0;
        int floorNear32 = 0;
        int ceilingHigh = 0;
        for (int x = -1024; x < 1024; x += 16) {
            for (int z = -1024; z < 1024; z += 16) {
                columns++;
                int floor = -1;
                int ceiling = -1;
                for (int y = 1; y < 127; y++) {
                    if (density.compute(new DensityFunction.SinglePointContext(x, y, z)) <= 0) {
                        floor = floor < 0 ? y - 1 : floor;
                        ceiling = y + 1;
                    }
                }
                if (floor >= 0 && ceiling - floor > 40) {
                    open++;
                    floorNear32 += floor >= 24 && floor <= 40 ? 1 : 0;
                    ceilingHigh += ceiling >= 85 ? 1 : 0;
                }
            }
        }
        LOGGER.info("Undervault's density over 2,048 blocks: {} of {} columns open, {} with the floor at y 24 to 40, "
                + "{} with the ceiling at y 85 or above", open, columns, floorNear32, ceilingHigh);
        helper.assertTrue(open >= columns * 3 / 4, "mostly open cave: " + open + " of " + columns + " columns");
        helper.assertTrue(open < columns, "some pillars from floor to ceiling: " + open + " of " + columns + " open");
        helper.assertTrue(floorNear32 >= open * 95 / 100, "one walkable floor near y 32: " + floorNear32 + " of " + open);
        helper.assertTrue(ceilingHigh >= open * 95 / 100, "a high ceiling: " + ceilingHigh + " of " + open);
    }

    /** The realm's dimension: its type links at 1:16 and has no skylight; it is the realm of the content. */
    @QaGameTest
    public static void theRealmDimensionIsTheContents(GameTestHelper helper) {
        RealmExpectation expected = stone();
        ServerLevel realm = realm(helper, expected);
        helper.assertValueEqual(realm.dimensionType().coordinateScale(), 16.0, "the realm's coordinate scale");
        helper.assertFalse(realm.dimensionType().hasSkyLight(), "the realm has no sky light");
        helper.assertTrue(realm.dimensionType().hasCeiling(), "the realm has a ceiling");
        helper.assertTrue(Realms.isRealm(realm.registryAccess(), realm.dimension()), "the dimension is a realm");
        helper.assertFalse(Realms.isRealm(realm.registryAccess(), Level.OVERWORLD), "Overworld is no realm");
        helper.succeed();
    }

    static RealmExpectation stone() {
        return GeneratedRealmExpectations.REALMS.stream()
                .filter(realm -> realm.id().equals("realmforge:stone"))
                .findFirst()
                .orElseThrow();
    }

    static ServerLevel realm(GameTestHelper helper, RealmExpectation expected) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(expected.dimension()));
        ServerLevel level = helper.getLevel().getServer().getLevel(dimension);
        helper.assertTrue(level != null, "the GameTest world has no " + expected.dimension() + " dimension");
        return level;
    }

    static void award(GameTestHelper helper, ServerPlayer player, String advancement) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(Identifier.parse(advancement));
        helper.assertTrue(holder != null, "the realm's unlock advancement exists: " + advancement);
        for (String criterion : holder.value().criteria().keySet()) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    /** A teleport as {@code /tp} and waystone warps make one; true when it happened. */
    private static boolean teleport(ServerPlayer player, ServerLevel level, Vec3 to) {
        return player.teleportTo(level, to.x, to.y, to.z, Set.of(), 0.0F, 0.0F, true);
    }

    private static TeleportTransition transition(ServerLevel level, Vec3 to) {
        return new TeleportTransition(level, to, Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING);
    }

    /** Spawns a parrot tamed by the player in {@code level} at the player, and puts it on their left shoulder. */
    private static UUID perchParrot(GameTestHelper helper, ServerPlayer player, ServerLevel level) {
        Parrot parrot = EntityType.PARROT.create(level, EntitySpawnReason.COMMAND);
        helper.assertTrue(parrot != null, "a parrot can be created");
        parrot.snapTo(player.getX(), player.getY(), player.getZ());
        parrot.tame(player);
        helper.assertTrue(level.addFreshEntity(parrot), "the parrot is added to its level");
        UUID id = parrot.getUUID();
        // A parrot perches only on a player standing on the ground; a fake player that was just teleported is not.
        player.setOnGround(true);
        helper.assertTrue(parrot.setEntityOnShoulder(player), "the parrot sits on the player's shoulder");
        helper.assertFalse(player.getShoulderEntityLeft().isEmpty(), "the shoulder holds the parrot");
        List<Parrot> left = level.getEntitiesOfClass(Parrot.class, new AABB(player.blockPosition()).inflate(4));
        helper.assertTrue(left.isEmpty(), "on the shoulder, the parrot is no entity");
        return id;
    }
}
