package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRealmExpectations.RealmExpectation;
import io.github.matejkadlec.realmforge.world.realm.PortalFrame;
import io.github.matejkadlec.realmforge.world.realm.PortalStoneBlock;
import io.github.matejkadlec.realmforge.world.realm.RealmPortalBlock;
import io.github.matejkadlec.realmforge.world.realm.RealmPortalBlocks;
import io.github.matejkadlec.realmforge.world.realm.RealmPortalLinker;
import io.github.matejkadlec.realmforge.world.realm.RealmPortals;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The realm portal (W2b, RLM-154; realm portal spec, "Tests"): a sigil lights a complete frame only for a player with
 * the realm's unlock; the selector's gestures; a broken frame closes; the portal refuses a player without the unlock
 * and lets them travel after it; trips into the realm and back link to the portals at the scaled positions or build
 * them, also in the worst rounding; trip-built frames drop nothing; fluids and other entities never pass.
 *
 * <p>Trips use far-away places of their own, so no two tests find each other's portals: Overworld x and z in their own
 * 16-block cells around 40,000, realm x and z around 2,500. Positions in the realm are absolute. A trip runs the
 * portal's own {@code getPortalDestination} and teleports the player with it, as vanilla's portal processing does
 * once the 2 seconds are up.
 */
public final class RealmPortalGameTests {
    /** The selector of the frame on {@link QaGameTests#PLATFORM_9X9}, which runs along x. */
    private static final BlockPos SELECTOR = new BlockPos(4, 0, 4);
    /** The flat GameTest world's grass, where far-away Overworld frames stand. */
    private static final int OVERWORLD_GROUND = -61;
    private static final int REALM_FLOOR = 40;

    private RealmPortalGameTests() {
    }

    // The selector -------------------------------------------------------------------------------------------------

    /** A sigil lights a complete frame for a player with the unlock, never without it, and switches a lit one. */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void aSigilLightsACompleteFrameOnlyWithTheUnlock(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        PortalFrame.Frame frame = new PortalFrame.Frame(helper.absolutePos(SELECTOR), Direction.Axis.X);
        buildFrame(level, frame);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-sigil", new Vec3(4.5, 1, 7.5));
        try {
            use(player, level, frame.selector(), sigil(stone), ItemStack.EMPTY, false);
            helper.assertFalse(lit(level, frame), "a sigil opens nothing for a player without the realm's unlock");

            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            use(player, level, frame.selector(), sigil(stone), ItemStack.EMPTY, false);
            helper.assertTrue(lit(level, frame), "the sigil lights the complete frame");
            helper.assertValueEqual(RealmPortals.destination(level, frame.selector()), Optional.of(id(stone)),
                    "the portal opens onto the stone realm");
            helper.assertTrue(player.getMainHandItem().getCount() == 1, "the sigil stays in the hand");

            // A portal that shows another entry switches straight to the sigil's realm.
            Identifier overworld = Realmforge.id("overworld");
            RealmPortals.light(level, frame, overworld);
            helper.assertValueEqual(RealmPortals.destination(level, frame.selector()), Optional.of(overworld),
                    "the portal shows Overworld for a moment");
            use(player, level, frame.selector(), sigil(stone), ItemStack.EMPTY, false);
            helper.assertTrue(lit(level, frame), "the switched portal stays lit");
            helper.assertValueEqual(RealmPortals.destination(level, frame.selector()), Optional.of(id(stone)),
                    "the sigil switched the portal to its realm");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** A frame with a missing side block, or with a block in its opening, stays dark. */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void anIncompleteOrBlockedFrameStaysDark(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        PortalFrame.Frame frame = new PortalFrame.Frame(helper.absolutePos(SELECTOR), Direction.Axis.X);
        buildFrame(level, frame);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-incomplete", new Vec3(4.5, 1, 7.5));
        try {
            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            BlockPos side = frame.at(3, 2);
            level.setBlockAndUpdate(side, Blocks.AIR.defaultBlockState());
            use(player, level, frame.selector(), sigil(stone), ItemStack.EMPTY, false);
            helper.assertFalse(lit(level, frame), "a frame without a side block stays dark");

            level.setBlockAndUpdate(side, RealmPortalBlocks.portalStone().defaultBlockState());
            level.setBlockAndUpdate(frame.at(1, 2), Blocks.STONE.defaultBlockState());
            use(player, level, frame.selector(), sigil(stone), ItemStack.EMPTY, false);
            helper.assertFalse(lit(level, frame), "a block in the opening keeps the frame dark");
            helper.assertTrue(level.getBlockState(frame.at(1, 2)).is(Blocks.STONE), "the block stays");

            level.setBlockAndUpdate(frame.at(1, 2), Blocks.AIR.defaultBlockState());
            use(player, level, frame.selector(), sigil(stone), ItemStack.EMPTY, false);
            helper.assertTrue(lit(level, frame), "the completed frame lights");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * The sneak gesture with an empty main hand closes a portal in Overworld whatever the off hand holds (a shield
     * here), and a broken frame block closes it too; either way the frame lights again with the sigil (RLM-133).
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void closedOrBrokenFramesRelightWithTheSigil(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        PortalFrame.Frame frame = new PortalFrame.Frame(helper.absolutePos(SELECTOR), Direction.Axis.X);
        buildFrame(level, frame);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-gesture", new Vec3(4.5, 1, 7.5));
        try {
            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            use(player, level, frame.selector(), sigil(stone), ItemStack.EMPTY, false);
            helper.assertTrue(lit(level, frame), "the sigil lights the frame");

            use(player, level, frame.selector(), ItemStack.EMPTY, new ItemStack(Items.SHIELD), true);
            helper.assertTrue(dark(level, frame), "the sneak gesture with a shield in the off hand closes the portal");
            helper.assertValueEqual(RealmPortals.destination(level, frame.selector()), Optional.empty(),
                    "a closed portal leads nowhere");

            use(player, level, frame.selector(), sigil(stone), ItemStack.EMPTY, false);
            helper.assertTrue(lit(level, frame), "the closed frame lights again with the sigil");

            BlockPos side = frame.at(-1, 1);
            level.setBlockAndUpdate(side, Blocks.AIR.defaultBlockState());
            helper.assertTrue(dark(level, frame), "breaking a frame block takes the whole opening with it");
            helper.assertValueEqual(RealmPortals.destination(level, frame.selector()), Optional.empty(),
                    "the broken portal leads nowhere");
            level.setBlockAndUpdate(side, RealmPortalBlocks.portalStone().defaultBlockState());
            use(player, level, frame.selector(), sigil(stone), ItemStack.EMPTY, false);
            helper.assertTrue(lit(level, frame), "the rebuilt frame lights again with the sigil");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * A frame inside a realm opens only back to Overworld (RLM-188): every sigil is refused, and the sneak gesture,
     * a shield in the off hand, opens it onto return.
     */
    @QaGameTest
    public static void aFrameInARealmOpensOnlyBackToOverworld(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel realm = RealmGameTests.realm(helper, stone);
        PortalFrame.Frame frame = new PortalFrame.Frame(new BlockPos(2_380, REALM_FLOOR, 2_380), Direction.Axis.Z);
        clearSpot(realm, frame);
        buildFrame(realm, frame);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-realm-frame");
        try {
            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            Vec3 inFront = Vec3.atBottomCenterOf(frame.at(1, 0).relative(frame.front()));
            helper.assertTrue(player.teleportTo(realm, inFront.x, inFront.y, inFront.z, Set.of(), 0.0F, 0.0F, true),
                    "the unlocked player enters the realm");
            use(player, realm, frame.selector(), sigil(stone), ItemStack.EMPTY, false);
            helper.assertFalse(lit(realm, frame), "a sigil is refused on a frame inside a realm");

            use(player, realm, frame.selector(), ItemStack.EMPTY, new ItemStack(Items.SHIELD), true);
            helper.assertTrue(lit(realm, frame), "the sneak gesture with a shield in the off hand opens the frame");
            helper.assertValueEqual(RealmPortals.destination(realm, frame.selector()),
                    Optional.of(Realmforge.id("overworld")), "the portal opens onto Overworld");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** The selector refuses in the instance dimension, which has no realm entry. */
    @QaGameTest
    public static void theSelectorRefusesInTheInstanceDimension(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
        helper.assertTrue(instances != null, "the instance dimension is loaded");
        PortalFrame.Frame frame = new PortalFrame.Frame(new BlockPos(-60_000, 100, -60_000), Direction.Axis.X);
        buildFrame(instances, frame);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-instance");
        try {
            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            use(player, instances, frame.selector(), sigil(stone), ItemStack.EMPTY, false);
            helper.assertFalse(lit(instances, frame), "the portal cannot open in the instance dimension");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    // Travel -------------------------------------------------------------------------------------------------------

    /**
     * Done when of W2b: the portal refuses a player without the realm's unlock when they step in, so its countdown
     * never starts, and lets them travel into the realm after the unlock.
     */
    @QaGameTest
    public static void thePortalRefusesAPlayerBeforeTheUnlockAndPassesThemAfterIt(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        ServerLevel realm = RealmGameTests.realm(helper, stone);
        PortalFrame.Frame frame = litOverworldFrame(level, 2_400 * 16 + 1, 2_400 * 16 + 1, stone);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-refusal");
        try {
            BlockPos entry = frame.at(1, 1);
            BlockState portal = level.getBlockState(entry);
            standIn(player, level, frame);
            portal.entityInside(level, entry, player, InsideBlockEffectApplier.NOOP, true);
            helper.assertTrue(player.portalProcess == null, "the portal does not start for a player without the unlock");

            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            portal.entityInside(level, entry, player, InsideBlockEffectApplier.NOOP, true);
            helper.assertTrue(player.portalProcess != null, "the portal starts for the unlocked player");
            travel(helper, player, level, entry);
            helper.assertTrue(player.level() == realm, "the unlocked player travelled into the realm");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * Done when of W2b: a trip into the realm builds a return portal within 7 blocks of the scaled position, with frame
     * blocks that drop nothing, and arrives in front of it; the trip back arrives at the Overworld portal it came from;
     * a second Overworld portal within 128 blocks of the first arrives at that same realm portal.
     */
    @QaGameTest(timeoutTicks = 200)
    public static void aTripIntoTheRealmBuildsAPortalAndTheWayBackLinksToIt(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        ServerLevel realm = RealmGameTests.realm(helper, stone);
        int cellX = 2_500;
        int cellZ = 2_500;
        PortalFrame.Frame first = litOverworldFrame(level, cellX * 16 + 1, cellZ * 16 + 1, stone);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-trip");
        try {
            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            travel(helper, player, level, first.at(1, 1));
            helper.assertTrue(player.level() == realm, "the trip leads into the realm");

            PortalFrame.Frame built = realmPortalNear(helper, realm, new BlockPos(cellX, REALM_FLOOR, cellZ));
            helper.assertTrue(Math.abs(built.selector().getX() - cellX) <= 7 && Math.abs(built.selector().getZ() - cellZ) <= 7,
                    "the return portal stands within 7 blocks of the scaled position: " + built.selector());
            helper.assertTrue(built.selector().getY() >= 20 && built.selector().getY() <= 90,
                    "the built frame stands at the realm's arrival heights: " + built.selector());
            helper.assertTrue(realm.getBlockState(built.selector()).getValue(PortalStoneBlock.GENERATED)
                    && realm.getBlockState(built.at(-1, 2)).getValue(PortalStoneBlock.GENERATED),
                    "a frame a trip built is marked generated");
            assertArrivedAt(helper, player, built);

            travel(helper, player, realm, built.at(1, 1));
            helper.assertTrue(player.level() == level, "the way back leads to Overworld");
            assertArrivedAt(helper, player, first);
            helper.assertValueEqual(selectorsNear(level, first.selector(), RealmPortalLinker.OVERWORLD_SEARCH_RADIUS),
                    1L, "the way back built no second Overworld portal");

            // A second Overworld portal, within 128 blocks of the first, aiming at the realm portal's own cell.
            PortalFrame.Frame second = litOverworldFrame(level, built.selector().getX() * 16 + 8,
                    built.selector().getZ() * 16 + 8, stone);
            helper.assertTrue(second.selector().distManhattan(first.selector()) > 4
                            && Math.abs(second.selector().getX() - first.selector().getX()) <= 128
                            && Math.abs(second.selector().getZ() - first.selector().getZ()) <= 128,
                    "the second portal stands apart from the first, within 128 blocks of it");
            travel(helper, player, level, second.at(1, 1));
            helper.assertTrue(player.level() == realm, "the second portal leads into the realm");
            assertArrivedAt(helper, player, built);
            helper.assertValueEqual(selectorsNear(realm, built.selector(), 16), 1L,
                    "both Overworld portals lead to one realm portal");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * The worst rounding: an Overworld portal centred at x 15 of its cell aims at the cell's realm x, and the only free
     * spot in the realm lies 7 blocks towards negative x. The trip builds the return portal there, and the way back
     * still arrives at the Overworld portal it came from (realm portal spec, "Linking").
     */
    @QaGameTest(timeoutTicks = 200)
    public static void theWorstRoundingStillLinksBack(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        ServerLevel realm = RealmGameTests.realm(helper, stone);
        int cellX = 2_600;
        int cellZ = 2_600;
        PortalFrame.Frame first = litOverworldFrame(level, cellX * 16 + 15, cellZ * 16 + 8, stone);
        // Solid rock all around the scaled position, but for one spot 7 blocks west of it: a frame across x, arrived at
        // from the east.
        BlockState rock = Blocks.STONE.defaultBlockState();
        for (int x = cellX - 9; x <= cellX + 9; x++) {
            for (int z = cellZ - 9; z <= cellZ + 9; z++) {
                for (int y = 19; y <= 91; y++) {
                    realm.setBlock(new BlockPos(x, y, z), rock, Block.UPDATE_CLIENTS);
                }
            }
        }
        PortalFrame.Frame only = new PortalFrame.Frame(new BlockPos(cellX - 7, REALM_FLOOR, cellZ), Direction.Axis.Z);
        for (int across = -1; across <= PortalFrame.WIDTH; across++) {
            for (int up = 0; up <= PortalFrame.HEIGHT; up++) {
                realm.setBlock(only.at(across, up), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        for (int across = 0; across < PortalFrame.WIDTH; across++) {
            for (int up = 0; up <= 1; up++) {
                realm.setBlock(only.at(across, up).relative(only.front().getOpposite()), Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS);
            }
        }
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-rounding");
        try {
            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            travel(helper, player, level, first.at(1, 1));
            helper.assertTrue(player.level() == realm, "the trip leads into the realm");
            PortalFrame.Frame built = realmPortalNear(helper, realm, new BlockPos(cellX, REALM_FLOOR, cellZ));
            helper.assertValueEqual(built, only, "the return portal stands at the only free spot");

            travel(helper, player, realm, built.at(1, 1));
            helper.assertTrue(player.level() == level, "the way back leads to Overworld");
            assertArrivedAt(helper, player, first);
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * A trip from a realm portal with no Overworld portal near its scaled-back position builds one there. Over the
     * flat GameTest world nothing fits at Overworld's arrival heights (64 to 310), so it is carved out on a platform at
     * y 64; the trip back arrives at the realm portal it came from.
     */
    @QaGameTest(timeoutTicks = 200)
    public static void aWayBackWithNoPortalNearBuildsOneOnAPlatform(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        ServerLevel realm = RealmGameTests.realm(helper, stone);
        PortalFrame.Frame start = new PortalFrame.Frame(new BlockPos(2_700, REALM_FLOOR, 2_700), Direction.Axis.X);
        clearSpot(realm, start);
        buildFrame(realm, start);
        RealmPortals.light(realm, start, Realmforge.id("overworld"));
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-way-back");
        try {
            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            Vec3 inFront = Vec3.atBottomCenterOf(start.at(1, 0).relative(start.front()));
            helper.assertTrue(player.teleportTo(realm, inFront.x, inFront.y, inFront.z, Set.of(), 0.0F, 0.0F, true),
                    "the unlocked player enters the realm");
            travel(helper, player, realm, start.at(1, 1));
            helper.assertTrue(player.level() == level, "the way back leads to Overworld");

            // The realm opening's centre, x and z 2,700.5, times 16.
            BlockPos scaled = new BlockPos(2_700 * 16 + 8, 64, 2_700 * 16 + 8);
            Optional<PortalFrame.Frame> built = RealmPortalLinker.findPortal(level, scaled,
                    RealmPortalLinker.OVERWORLD_BUILD_RADIUS, id(stone));
            helper.assertTrue(built.isPresent(), "the way back built an Overworld portal near " + scaled);
            helper.assertValueEqual(built.get().selector().getY(), 64, "the built frame stands on a platform at y 64");
            helper.assertTrue(level.getBlockState(built.get().selector()).getValue(PortalStoneBlock.GENERATED),
                    "the built frame is marked generated");
            helper.assertTrue(level.getBlockState(built.get().at(1, -1).relative(built.get().front())).is(Blocks.STONE_BRICKS),
                    "the platform is of Overworld's arrival block");
            assertArrivedAt(helper, player, built.get());

            travel(helper, player, level, built.get().at(1, 1));
            helper.assertTrue(player.level() == realm, "the trip back leads into the realm");
            assertArrivedAt(helper, player, start);
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    // Blocks, fluids and entities ---------------------------------------------------------------------------------

    /** Portal Stone and the selector of a frame a trip built drop nothing; a player's frame drops its blocks. */
    @QaGameTest
    public static void tripBuiltFramesDropNothing(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        for (Block block : List.of(RealmPortalBlocks.portalStone(), RealmPortalBlocks.selector())) {
            BlockState placed = block.defaultBlockState();
            List<ItemStack> drops = Block.getDrops(placed, helper.getLevel(), pos, null, null, pickaxe);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()),
                    BuiltInRegistries.BLOCK.getKey(block) + " a player placed drops itself, not " + drops);
            List<ItemStack> built = Block.getDrops(placed.setValue(PortalStoneBlock.GENERATED, true),
                    helper.getLevel(), pos, null, null, pickaxe);
            helper.assertTrue(built.isEmpty(), BuiltInRegistries.BLOCK.getKey(block) + " a trip built drops nothing");
        }
        helper.succeed();
    }

    /**
     * Water never washes a portal away: a bucket emptied into the opening, by a player or a dispenser (which empties
     * without a hit), and water flowing into it leave every portal block in place.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 120)
    public static void waterNeverWashesThePortalAway(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        PortalFrame.Frame frame = new PortalFrame.Frame(helper.absolutePos(SELECTOR), Direction.Axis.X);
        buildFrame(level, frame);
        RealmPortals.light(level, frame, id(stone));
        BucketItem bucket = (BucketItem) Items.WATER_BUCKET;
        BlockPos target = frame.at(1, 1);
        Direction facing = frame.front();
        bucket.emptyContents(null, level, target, new BlockHitResult(Vec3.atCenterOf(target), facing, target, false));
        bucket.emptyContents(null, level, frame.at(0, 2), null);
        helper.assertTrue(lit(level, frame), "a bucket and a dispenser leave the portal in place");
        // A source in front of the opening's middle, at its foot: it flows along the floor, and never into the portal.
        level.setBlockAndUpdate(frame.at(1, 0).relative(facing), Blocks.WATER.defaultBlockState());
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(lit(level, frame), "flowing water leaves the portal in place");
            for (BlockPos pos : frame.opening()) {
                helper.assertTrue(level.getFluidState(pos).isEmpty(), "no water inside the portal at " + pos);
            }
            helper.succeed();
        });
    }

    /** Only players travel: a mob, an item and a player riding a pig never start the portal's countdown. */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void onlyPlayersOnFootStepIntoThePortal(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        PortalFrame.Frame frame = new PortalFrame.Frame(helper.absolutePos(SELECTOR), Direction.Axis.X);
        buildFrame(level, frame);
        RealmPortals.light(level, frame, id(stone));
        BlockPos entry = frame.at(1, 0);
        BlockState portal = level.getBlockState(entry);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 6));
        portal.entityInside(level, entry, zombie, InsideBlockEffectApplier.NOOP, true);
        helper.assertTrue(zombie.portalProcess == null, "a mob does not step into the realm portal");
        ItemEntity item = new ItemEntity(level, entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5,
                new ItemStack(Items.STONE));
        level.addFreshEntity(item);
        portal.entityInside(level, entry, item, InsideBlockEffectApplier.NOOP, true);
        helper.assertTrue(item.portalProcess == null, "an item does not step into the realm portal");

        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-rider", new Vec3(4.5, 1, 6.5));
        try {
            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 1, 6));
            helper.assertTrue(player.startRiding(pig, true, false), "the player mounts the pig");
            portal.entityInside(level, entry, player, InsideBlockEffectApplier.NOOP, true);
            helper.assertTrue(player.portalProcess == null, "a mounted player does not step into the portal");
            player.stopRiding();
            portal.entityInside(level, entry, player, InsideBlockEffectApplier.NOOP, true);
            helper.assertTrue(player.portalProcess != null, "on foot, the player steps in");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    // Helpers ------------------------------------------------------------------------------------------------------

    private static Identifier id(RealmExpectation realm) {
        return Identifier.parse(realm.id());
    }

    private static ItemStack sigil(RealmExpectation realm) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(realm.sigil())));
    }

    /** The 13 Portal Stone and the selector of {@code frame}, as a player places them, with an empty opening. */
    private static void buildFrame(ServerLevel level, PortalFrame.Frame frame) {
        level.setBlockAndUpdate(frame.selector(), RealmPortalBlocks.selector().defaultBlockState());
        for (BlockPos pos : frame.portalStones()) {
            level.setBlockAndUpdate(pos, RealmPortalBlocks.portalStone().defaultBlockState());
        }
        for (BlockPos pos : frame.opening()) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    /** Clears room for {@code frame} in the realm: a floor under it and on both sides, air around it. */
    private static void clearSpot(ServerLevel level, PortalFrame.Frame frame) {
        for (int across = -2; across <= PortalFrame.WIDTH + 1; across++) {
            for (int depth = -2; depth <= 2; depth++) {
                for (int up = -1; up <= PortalFrame.HEIGHT + 1; up++) {
                    BlockPos pos = frame.at(across, up).relative(frame.front(), depth);
                    level.setBlock(pos, up == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** A frame along x in Overworld, its selector on the flat world's grass at x, z, lit onto the realm. */
    private static PortalFrame.Frame litOverworldFrame(ServerLevel level, int x, int z, RealmExpectation realm) {
        PortalFrame.Frame frame = new PortalFrame.Frame(new BlockPos(x, OVERWORLD_GROUND, z), Direction.Axis.X);
        buildFrame(level, frame);
        RealmPortals.light(level, frame, id(realm));
        return frame;
    }

    /** The player uses the selector at {@code selector} as a right-click with the main hand would. */
    private static void use(ServerPlayer player, ServerLevel level, BlockPos selector, ItemStack mainHand,
            ItemStack offHand, boolean sneaking) {
        player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
        player.setItemInHand(InteractionHand.OFF_HAND, offHand);
        player.setShiftKeyDown(sneaking);
        player.gameMode.useItemOn(player, level, mainHand, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(selector), Direction.UP, selector, false));
        player.setShiftKeyDown(false);
    }

    /** Puts the player into the opening's bottom middle. */
    private static void standIn(ServerPlayer player, ServerLevel level, PortalFrame.Frame frame) {
        Vec3 at = Vec3.atBottomCenterOf(frame.at(1, 0));
        player.teleportTo(level, at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
    }

    /** The trip through the portal block at {@code entry}, as vanilla makes it once the portal's time is up. */
    private static void travel(GameTestHelper helper, ServerPlayer player, ServerLevel level, BlockPos entry) {
        BlockState state = level.getBlockState(entry);
        helper.assertTrue(state.getBlock() instanceof RealmPortalBlock, "a portal block at " + entry);
        TeleportTransition transition = ((RealmPortalBlock) state.getBlock()).getPortalDestination(level, player, entry);
        helper.assertTrue(transition != null, "the portal at " + entry + " leads somewhere");
        player.teleport(transition);
    }

    /** The realm's return portal within the realm's search radius of {@code centre}. */
    private static PortalFrame.Frame realmPortalNear(GameTestHelper helper, ServerLevel realm, BlockPos centre) {
        Optional<PortalFrame.Frame> frame = RealmPortalLinker.findPortal(realm, centre,
                RealmPortalLinker.searchRadius(realm), Realmforge.id("overworld"));
        helper.assertTrue(frame.isPresent(), "a return portal near " + centre);
        return frame.get();
    }

    /** The player stands in front of the frame's opening, or in it. */
    private static void assertArrivedAt(GameTestHelper helper, ServerPlayer player, PortalFrame.Frame frame) {
        BlockPos middle = frame.at(1, 0);
        BlockPos at = player.blockPosition();
        helper.assertTrue(at.getY() == middle.getY() && Math.abs(at.getX() - middle.getX()) <= 1
                        && Math.abs(at.getZ() - middle.getZ()) <= 1,
                "the player arrived at the portal at " + middle + ", not at " + at);
    }

    /** The realm selectors within {@code radius} of {@code centre}, lit or not. */
    private static long selectorsNear(ServerLevel level, BlockPos centre, int radius) {
        return level.getPoiManager().getInSquare(type -> type.is(RealmPortalBlocks.SELECTOR_POI), centre, radius,
                PoiManager.Occupancy.ANY).count();
    }

    private static boolean lit(ServerLevel level, PortalFrame.Frame frame) {
        return frame.opening().stream().allMatch(pos -> level.getBlockState(pos).getBlock() instanceof RealmPortalBlock);
    }

    private static boolean dark(ServerLevel level, PortalFrame.Frame frame) {
        return frame.opening().stream().noneMatch(pos -> level.getBlockState(pos).getBlock() instanceof RealmPortalBlock);
    }
}
