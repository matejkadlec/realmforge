package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.logging.LogUtils;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRealmExpectations.RealmExpectation;
import io.github.matejkadlec.realmforge.world.realm.PortalFrame;
import io.github.matejkadlec.realmforge.world.realm.RealmforgeBricksBlock;
import io.github.matejkadlec.realmforge.world.realm.RealmPortalBlock;
import io.github.matejkadlec.realmforge.world.realm.RealmPortalBlocks;
import io.github.matejkadlec.realmforge.world.realm.RealmPortalLinker;
import io.github.matejkadlec.realmforge.world.realm.RealmPortals;
import io.github.matejkadlec.realmforge.world.realm.Realms;
import io.github.matejkadlec.realmforge.world.realm.SealstoneBlock;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * The realm portal (W2b, RLM-154; realm portal spec, "Tests"): a signet lights a complete frame only for a player with
 * the realm's unlock; the sealstone's gestures; a broken frame closes; the portal refuses a player without the unlock
 * and lets them travel after it; trips into the realm and back link to the portals at the scaled positions or build
 * them standing on the floor, also in the worst rounding; trip-built frames drop nothing; fluids never pass; every
 * other entity travels, but not a vehicle carrying a player without the unlock; a player travels on their first tick
 * in the portal and does not bounce straight back (RLM-335).
 *
 * <p>Trips use far-away places of their own, so no two tests find each other's portals: Overworld x and z in their own
 * cells of the realm's scale (8 blocks at 1:8) around 20,000, realm x and z around 2,500. Positions in the realm are
 * absolute. A trip runs the portal's own {@code getPortalDestination} and teleports the entity with it, as vanilla's
 * portal processing does once the portal's time is up. Each trip logs how long it took on the server thread: a trip
 * that builds a portal generates the chunks around it there, as vanilla's nether portal does (realm portal spec,
 * "Arriving").
 */
public final class RealmPortalGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The sealstone of the frame on {@link QaGameTests#PLATFORM_9X9}, which runs along x. */
    private static final BlockPos SEALSTONE = new BlockPos(4, 0, 4);
    /** The flat GameTest world's grass, where far-away Overworld frames stand. */
    private static final int OVERWORLD_GROUND = -61;
    private static final int REALM_FLOOR = 40;

    private RealmPortalGameTests() {
    }

    // The sealstone -------------------------------------------------------------------------------------------------

    /** A signet lights a complete frame for a player with the unlock, never without it, and switches a lit one. */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void aSignetLightsACompleteFrameOnlyWithTheUnlock(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        PortalFrame.Frame frame = new PortalFrame.Frame(helper.absolutePos(SEALSTONE), Direction.Axis.X);
        buildFrame(level, frame);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-signet", new Vec3(4.5, 1, 7.5));
        try {
            use(player, level, frame.sealstone(), signet(stone), ItemStack.EMPTY, false);
            helper.assertFalse(lit(level, frame), "a signet opens nothing for a player without the realm's unlock");

            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            use(player, level, frame.sealstone(), signet(stone), ItemStack.EMPTY, false);
            helper.assertTrue(lit(level, frame), "the signet lights the complete frame");
            helper.assertValueEqual(RealmPortals.destination(level, frame.sealstone()), Optional.of(id(stone)),
                    "the portal opens onto the stone realm");
            helper.assertTrue(player.getMainHandItem().getCount() == 1, "the signet stays in the hand");

            // A portal that shows another entry switches straight to the signet's realm.
            Identifier overworld = Realmforge.id("overworld");
            RealmPortals.light(level, frame, overworld);
            helper.assertValueEqual(RealmPortals.destination(level, frame.sealstone()), Optional.of(overworld),
                    "the portal shows Overworld for a moment");
            use(player, level, frame.sealstone(), signet(stone), ItemStack.EMPTY, false);
            helper.assertTrue(lit(level, frame), "the switched portal stays lit");
            helper.assertValueEqual(RealmPortals.destination(level, frame.sealstone()), Optional.of(id(stone)),
                    "the signet switched the portal to its realm");
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
        PortalFrame.Frame frame = new PortalFrame.Frame(helper.absolutePos(SEALSTONE), Direction.Axis.X);
        buildFrame(level, frame);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-incomplete", new Vec3(4.5, 1, 7.5));
        try {
            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            BlockPos side = frame.at(3, 2);
            level.setBlockAndUpdate(side, Blocks.AIR.defaultBlockState());
            use(player, level, frame.sealstone(), signet(stone), ItemStack.EMPTY, false);
            helper.assertFalse(lit(level, frame), "a frame without a side block stays dark");

            level.setBlockAndUpdate(side, RealmPortalBlocks.bricks().defaultBlockState());
            level.setBlockAndUpdate(frame.at(1, 2), Blocks.STONE.defaultBlockState());
            use(player, level, frame.sealstone(), signet(stone), ItemStack.EMPTY, false);
            helper.assertFalse(lit(level, frame), "a block in the opening keeps the frame dark");
            helper.assertTrue(level.getBlockState(frame.at(1, 2)).is(Blocks.STONE), "the block stays");

            level.setBlockAndUpdate(frame.at(1, 2), Blocks.AIR.defaultBlockState());
            use(player, level, frame.sealstone(), signet(stone), ItemStack.EMPTY, false);
            helper.assertTrue(lit(level, frame), "the completed frame lights");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * The sneak gesture with an empty main hand closes a portal in Overworld whatever the off hand holds (a shield
     * here), and a broken frame block closes it too; either way the frame lights again with the signet (RLM-133).
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void closedOrBrokenFramesRelightWithTheSignet(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        PortalFrame.Frame frame = new PortalFrame.Frame(helper.absolutePos(SEALSTONE), Direction.Axis.X);
        buildFrame(level, frame);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-gesture", new Vec3(4.5, 1, 7.5));
        try {
            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            use(player, level, frame.sealstone(), signet(stone), ItemStack.EMPTY, false);
            helper.assertTrue(lit(level, frame), "the signet lights the frame");

            use(player, level, frame.sealstone(), ItemStack.EMPTY, new ItemStack(Items.SHIELD), true);
            helper.assertTrue(dark(level, frame), "the sneak gesture with a shield in the off hand closes the portal");
            helper.assertValueEqual(RealmPortals.destination(level, frame.sealstone()), Optional.empty(),
                    "a closed portal leads nowhere");

            use(player, level, frame.sealstone(), signet(stone), ItemStack.EMPTY, false);
            helper.assertTrue(lit(level, frame), "the closed frame lights again with the signet");

            BlockPos side = frame.at(-1, 1);
            level.setBlockAndUpdate(side, Blocks.AIR.defaultBlockState());
            helper.assertTrue(dark(level, frame), "breaking a frame block takes the whole opening with it");
            helper.assertValueEqual(RealmPortals.destination(level, frame.sealstone()), Optional.empty(),
                    "the broken portal leads nowhere");
            level.setBlockAndUpdate(side, RealmPortalBlocks.bricks().defaultBlockState());
            use(player, level, frame.sealstone(), signet(stone), ItemStack.EMPTY, false);
            helper.assertTrue(lit(level, frame), "the rebuilt frame lights again with the signet");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * A frame inside a realm opens only back to Overworld (RLM-188): the realm's own signet opens it onto return
     * (Matěj, 2026-10-28: "the portal in the Undervault would get activated by the same signet"), and so does the sneak
     * gesture, a shield in the off hand.
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
            use(player, realm, frame.sealstone(), signet(stone), ItemStack.EMPTY, false);
            helper.assertTrue(lit(realm, frame), "the realm's own signet opens a frame inside the realm");
            helper.assertValueEqual(RealmPortals.destination(realm, frame.sealstone()),
                    Optional.of(Realmforge.id("overworld")), "the realm's signet opens the frame onto Overworld");

            RealmPortals.close(realm, frame.sealstone());
            helper.assertTrue(dark(realm, frame), "the frame is dark again");
            use(player, realm, frame.sealstone(), ItemStack.EMPTY, new ItemStack(Items.SHIELD), true);
            helper.assertTrue(lit(realm, frame), "the sneak gesture with a shield in the off hand opens the frame");
            helper.assertValueEqual(RealmPortals.destination(realm, frame.sealstone()),
                    Optional.of(Realmforge.id("overworld")), "the portal opens onto Overworld");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** The sealstone refuses in the instance dimension, which has no realm entry. */
    @QaGameTest
    public static void theSealstoneRefusesInTheInstanceDimension(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
        helper.assertTrue(instances != null, "the instance dimension is loaded");
        PortalFrame.Frame frame = new PortalFrame.Frame(new BlockPos(-60_000, 100, -60_000), Direction.Axis.X);
        buildFrame(instances, frame);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-instance");
        try {
            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            use(player, instances, frame.sealstone(), signet(stone), ItemStack.EMPTY, false);
            helper.assertFalse(lit(instances, frame), "the portal cannot open in the instance dimension");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * Done when of RLM-406: a dark sealstone lights up while a player holding a signet, in either hand, stands within 5
     * blocks of it, rising over 15 ticks, and fades once they step 5.5 blocks away; with empty hands it stays dark. A
     * lit portal glows only for its own realm's signet: a return portal, open onto Overworld, stays dark. The glow is
     * the sealstone's block light. The light engine updates it off the server thread, and the GameTest server runs its
     * ticks back to back, so with the worker threads busy with other tests' chunks (the realm trips generate realm
     * terrain) the light in the opening was still missing 160 ticks after the glow, and the test timed out on tick
     * 202 (RLM-485); the timeout leaves room for that, as {@code RealmGameTests#shoulderParrotsStayBehind}'s does.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 600)
    public static void theSealstoneGlowsWhileAPlayerWithASignetIsNear(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        PortalFrame.Frame frame = new PortalFrame.Frame(helper.absolutePos(SEALSTONE), Direction.Axis.X);
        buildFrame(level, frame);
        BlockPos sealstone = frame.sealstone();
        // 3 blocks in front of the sealstone's centre, and 5.5 blocks: just outside the glow's radius.
        Vec3 near = new Vec3(4.5, 1, 7.5);
        Vec3 away = new Vec3(4.5, 1, 10.0);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-glow", near);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    assertGlow(helper, level, sealstone, 0, "with empty hands the sealstone stays dark");
                    player.setItemInHand(InteractionHand.OFF_HAND, signet(stone));
                })
                .thenExecuteAfter(5, () -> {
                    int glow = level.getBlockState(sealstone).getValue(SealstoneBlock.GLOW);
                    helper.assertTrue(glow > 0 && glow < SealstoneBlock.MAX_GLOW,
                            "a few ticks after the signet came out, the glow is rising: " + glow);
                })
                .thenExecuteAfter(15, () -> assertGlow(helper, level, sealstone, SealstoneBlock.MAX_GLOW,
                        "a signet in the off hand lights the dark sealstone fully"))
                .thenWaitUntil(() -> helper.assertValueEqual(level.getBrightness(LightLayer.BLOCK, sealstone.above()),
                        SealstoneBlock.MAX_GLOW - 1, "the block light in the dark opening above the glowing sealstone"))
                .thenExecute(() -> QaPlayers.onPlatform(helper, player, away))
                .thenExecuteAfter(20, () -> assertGlow(helper, level, sealstone, 0,
                        "the sealstone faded once the player stepped out of its radius"))
                .thenExecute(() -> {
                    RealmPortals.light(level, frame, Realmforge.id("overworld"));
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.MAIN_HAND, signet(stone));
                    QaPlayers.onPlatform(helper, player, near);
                })
                .thenExecuteAfter(20, () -> {
                    assertGlow(helper, level, sealstone, 0, "a return portal stays dark for the stone realm's signet");
                    RealmPortals.light(level, frame, id(stone));
                })
                .thenExecuteAfter(20, () -> {
                    assertGlow(helper, level, sealstone, SealstoneBlock.MAX_GLOW,
                            "a portal open onto the stone realm glows for its signet in the main hand");
                    level.getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    /**
     * Which signets light a sealstone, by its dimension (RLM-458; Matěj, 2026-10-28: "I'd add the glowing effect as
     * well"): inside the stone realm its own signet does, dark or open onto Overworld, as it opens the frame back onto
     * Overworld there; in the instance dimension, which has no realm entry, no signet does; nothing else is a signet.
     * Overworld's glow over ticks is {@link #theSealstoneGlowsWhileAPlayerWithASignetIsNear}. This asks the rule
     * itself, with no player kept in the realm: a player there for the glow's ticks loads realm chunks in the
     * background, which held up the light and chunk work of the tests running beside it.
     */
    @QaGameTest
    public static void aSealstoneInARealmGlowsForTheRealmsSignet(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel realm = RealmGameTests.realm(helper, stone);
        ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
        helper.assertTrue(instances != null, "the instance dimension is loaded");
        Optional<Identifier> overworld = Optional.of(Realmforge.id("overworld"));
        helper.assertTrue(RealmPortals.lightsSealstone(realm, signet(stone), Optional.empty()),
                "a dark sealstone inside the realm glows for the realm's signet");
        helper.assertTrue(RealmPortals.lightsSealstone(realm, signet(stone), overworld),
                "a sealstone inside the realm open onto Overworld glows for the realm's signet");
        helper.assertFalse(RealmPortals.lightsSealstone(realm, new ItemStack(Items.SHIELD), Optional.empty()),
                "a shield is no signet");
        helper.assertFalse(RealmPortals.lightsSealstone(instances, signet(stone), Optional.empty()),
                "a sealstone in the instance dimension stays dark for every signet");
        helper.assertTrue(RealmPortals.lightsSealstone(helper.getLevel(), signet(stone), Optional.empty()),
                "a dark sealstone in Overworld glows for any signet");
        helper.assertFalse(RealmPortals.lightsSealstone(helper.getLevel(), signet(stone), overworld),
                "an Overworld sealstone open onto Overworld stays dark for the stone realm's signet");
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
        int scale = scale(realm);
        PortalFrame.Frame frame = litOverworldFrame(level, 2_400 * scale + 1, 2_400 * scale + 1, stone);
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
     * Done when of W2b: a trip into the realm builds a return portal within the realm's build radius (15 blocks at 1:8)
     * of the scaled position, with frame blocks that drop nothing, and arrives in front of it; the trip back arrives at
     * the Overworld portal it came from; a second Overworld portal within 128 blocks of the first arrives at that same
     * realm portal.
     */
    @QaGameTest(timeoutTicks = 200)
    public static void aTripIntoTheRealmBuildsAPortalAndTheWayBackLinksToIt(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        ServerLevel realm = RealmGameTests.realm(helper, stone);
        int cellX = 2_500;
        int cellZ = 2_500;
        int scale = scale(realm);
        int radius = RealmPortalLinker.buildRadius(realm);
        PortalFrame.Frame first = litOverworldFrame(level, cellX * scale + 1, cellZ * scale + 1, stone);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-trip");
        try {
            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            travel(helper, player, level, first.at(1, 1));
            helper.assertTrue(player.level() == realm, "the trip leads into the realm");

            PortalFrame.Frame built = realmPortalNear(helper, realm, new BlockPos(cellX, REALM_FLOOR, cellZ));
            helper.assertTrue(Math.abs(built.sealstone().getX() - cellX) <= radius
                            && Math.abs(built.sealstone().getZ() - cellZ) <= radius,
                    "the return portal stands within " + radius + " blocks of the scaled position: " + built.sealstone());
            helper.assertTrue(built.sealstone().getY() >= 20 && built.sealstone().getY() <= 90,
                    "the built frame stands at the realm's arrival heights: " + built.sealstone());
            // The realm's chunks there were never loaded: the trip still finds the cave floor (about y 32) instead
            // of carving a frame at the source's height clamped into the arrival heights, y 20.
            helper.assertTrue(built.sealstone().getY() != stoneArrivalMinY(realm),
                    "the trip found the floor in chunks nothing had loaded: " + built.sealstone());
            helper.assertTrue(realm.getBlockState(built.sealstone()).getValue(RealmforgeBricksBlock.GENERATED)
                    && realm.getBlockState(built.at(-1, 2)).getValue(RealmforgeBricksBlock.GENERATED),
                    "a frame a trip built is marked generated");
            assertCornersBuilt(helper, realm, built);
            assertArrivedAt(helper, player, built);
            // The frame sits in the floor (Matěj, 2026-10-20): the player stands at the opening's height and walks in.
            helper.assertValueEqual(player.blockPosition().getY(), built.at(1, 0).getY(),
                    "the player arrived at the opening's height, on the floor the frame is set into");

            travel(helper, player, realm, built.at(1, 1));
            helper.assertTrue(player.level() == level, "the way back leads to Overworld");
            assertArrivedAt(helper, player, first);
            helper.assertValueEqual(sealstonesNear(level, first.sealstone(), RealmPortalLinker.OVERWORLD_SEARCH_RADIUS),
                    1L, "the way back built no second Overworld portal");

            // A second Overworld portal, within 128 blocks of the first, aiming at the realm portal's own cell.
            PortalFrame.Frame second = litOverworldFrame(level, built.sealstone().getX() * scale + scale / 2,
                    built.sealstone().getZ() * scale + scale / 2, stone);
            helper.assertTrue(second.sealstone().distManhattan(first.sealstone()) > 4
                            && Math.abs(second.sealstone().getX() - first.sealstone().getX()) <= 128
                            && Math.abs(second.sealstone().getZ() - first.sealstone().getZ()) <= 128,
                    "the second portal stands apart from the first, within 128 blocks of it");
            travel(helper, player, level, second.at(1, 1));
            helper.assertTrue(player.level() == realm, "the second portal leads into the realm");
            assertArrivedAt(helper, player, built);
            helper.assertValueEqual(sealstonesNear(realm, built.sealstone(), RealmPortalLinker.searchRadius(realm)), 1L,
                    "both Overworld portals lead to one realm portal");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * The worst rounding: an Overworld portal centred at the last block of its cell (x 7 at 1:8) aims at the cell's realm
     * x, and the only free spot in the realm lies the realm's build radius (15 blocks at 1:8) towards negative x. The
     * trip builds the return portal there, and the way back still arrives at the Overworld portal it came from (realm
     * portal spec, "Linking").
     */
    @QaGameTest(timeoutTicks = 200)
    public static void theWorstRoundingStillLinksBack(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        ServerLevel realm = RealmGameTests.realm(helper, stone);
        int cellX = 2_600;
        int cellZ = 2_600;
        int scale = scale(realm);
        int radius = RealmPortalLinker.buildRadius(realm);
        PortalFrame.Frame first = litOverworldFrame(level, cellX * scale + scale - 1, cellZ * scale + scale / 2, stone);
        // No free block all around the scaled position, but for one spot the build radius west of it: a frame across x
        // set into the floor at y 40, arrived at from the east. Every free block there becomes a barrier, which counts
        // as floor and is never free, as rock is, and the cave's own stone stays. A barrier dampens light as air does,
        // so the fill queues no light update: filling the cave with stone queued tens of thousands, and on CI's two
        // cores the other tests' light and chunk work waited behind them.
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = cellX - radius - 2; x <= cellX + radius + 2; x++) {
            for (int z = cellZ - radius - 2; z <= cellZ + radius + 2; z++) {
                for (int y = 19; y <= 91; y++) {
                    BlockState state = realm.getBlockState(pos.set(x, y, z));
                    if (state.canBeReplaced() && state.getFluidState().isEmpty()) {
                        realm.setBlock(pos, barrier, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        PortalFrame.Frame only = new PortalFrame.Frame(new BlockPos(cellX - radius, REALM_FLOOR, cellZ), Direction.Axis.Z);
        BlockState floor = Blocks.STONE.defaultBlockState();
        for (int across = -1; across <= PortalFrame.WIDTH; across++) {
            realm.setBlock(only.at(across, -1), floor, Block.UPDATE_CLIENTS);
            for (int up = 0; up <= PortalFrame.HEIGHT; up++) {
                realm.setBlock(only.at(across, up), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        for (int across = 0; across < PortalFrame.WIDTH; across++) {
            BlockPos front = only.at(across, 0).relative(only.front().getOpposite());
            realm.setBlock(front.below(), floor, Block.UPDATE_CLIENTS);
            for (int up = 0; up <= 1; up++) {
                realm.setBlock(front.above(up), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
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
     * flat GameTest world nothing fits at Overworld's arrival heights (63 to 310), so it is carved out set into a
     * platform at y 63, with Realmforge Bricks in its corners; the trip back arrives at the realm portal it came from.
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

            // The realm opening's centre, x and z 2,700.5, times the realm's scale.
            int scale = scale(realm);
            BlockPos scaled = new BlockPos(2_700 * scale + scale / 2, 63, 2_700 * scale + scale / 2);
            Optional<PortalFrame.Frame> built = RealmPortalLinker.findPortal(level, scaled,
                    RealmPortalLinker.OVERWORLD_BUILD_RADIUS, id(stone));
            helper.assertTrue(built.isPresent(), "the way back built an Overworld portal near " + scaled);
            helper.assertValueEqual(built.get().sealstone().getY(), 63, "the built frame's bottom row is at y 63");
            helper.assertTrue(level.getBlockState(built.get().sealstone()).getValue(RealmforgeBricksBlock.GENERATED),
                    "the built frame is marked generated");
            helper.assertTrue(level.getBlockState(built.get().at(1, -1).relative(built.get().front())).is(Blocks.STONE_BRICKS),
                    "the frame is set into a platform of Overworld's arrival block, which reaches in front of it");
            assertCornersBuilt(helper, level, built.get());
            assertArrivedAt(helper, player, built.get());
            helper.assertValueEqual(player.blockPosition().getY(), built.get().at(1, 0).getY(),
                    "the player arrived on the platform at the opening's height");

            travel(helper, player, level, built.get().at(1, 1));
            helper.assertTrue(player.level() == realm, "the trip back leads into the realm");
            assertArrivedAt(helper, player, start);
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    // Blocks, fluids and entities ---------------------------------------------------------------------------------

    /** Realmforge Bricks and the sealstone of a frame a trip built drop nothing; a player's frame drops its blocks. */
    @QaGameTest
    public static void tripBuiltFramesDropNothing(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        for (Block block : List.of(RealmPortalBlocks.bricks(), RealmPortalBlocks.sealstone())) {
            BlockState placed = block.defaultBlockState();
            List<ItemStack> drops = Block.getDrops(placed, helper.getLevel(), pos, null, null, pickaxe);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()),
                    BuiltInRegistries.BLOCK.getKey(block) + " a player placed drops itself, not " + drops);
            List<ItemStack> built = Block.getDrops(placed.setValue(RealmforgeBricksBlock.GENERATED, true),
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
        PortalFrame.Frame frame = new PortalFrame.Frame(helper.absolutePos(SEALSTONE), Direction.Axis.X);
        buildFrame(level, frame);
        RealmPortals.light(level, frame, id(stone));
        BucketItem bucket = (BucketItem) Items.WATER_BUCKET;
        BlockPos target = frame.at(1, 1);
        Direction facing = frame.front();
        ItemStack water = new ItemStack(Items.WATER_BUCKET);
        bucket.emptyContents(null, level, target, new BlockHitResult(Vec3.atCenterOf(target), facing, target, false),
                water);
        bucket.emptyContents(null, level, frame.at(0, 2), null, water);
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

    /**
     * Every entity steps into the portal (Matěj, 2026-10-20) and starts its trip at once, a player in survival or
     * creative too (RLM-335). A pig carrying a player without the unlock does not, and a passenger never starts a trip
     * itself.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void everyEntityStepsInExceptOneCarryingPlayersWithoutAccess(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        PortalFrame.Frame frame = new PortalFrame.Frame(helper.absolutePos(SEALSTONE), Direction.Axis.X);
        buildFrame(level, frame);
        RealmPortals.light(level, frame, id(stone));
        BlockPos entry = frame.at(1, 0);
        BlockState portal = level.getBlockState(entry);
        RealmPortalBlock block = (RealmPortalBlock) portal.getBlock();

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 6));
        portal.entityInside(level, entry, zombie, InsideBlockEffectApplier.NOOP, true);
        helper.assertTrue(zombie.portalProcess != null, "a mob steps into the realm portal");
        helper.assertValueEqual(block.getPortalTransitionTime(level, zombie), 0, "a mob's trip starts at once");
        ItemEntity item = new ItemEntity(level, entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5,
                new ItemStack(Items.STONE));
        level.addFreshEntity(item);
        portal.entityInside(level, entry, item, InsideBlockEffectApplier.NOOP, true);
        helper.assertTrue(item.portalProcess != null, "an item steps into the realm portal");

        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-rider", new Vec3(4.5, 1, 6.5));
        try {
            boolean invulnerable = player.getAbilities().invulnerable;
            player.getAbilities().invulnerable = false;
            helper.assertValueEqual(block.getPortalTransitionTime(level, player), 0,
                    "a player's trip in survival starts at once");
            player.getAbilities().invulnerable = true;
            helper.assertValueEqual(block.getPortalTransitionTime(level, player), 0,
                    "a player's trip in creative starts at once");
            player.getAbilities().invulnerable = invulnerable;
            Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 1, 6));
            helper.assertTrue(player.startRiding(pig, true, false), "the player mounts the pig");
            portal.entityInside(level, entry, pig, InsideBlockEffectApplier.NOOP, true);
            helper.assertTrue(pig.portalProcess == null, "a pig carrying a player without the unlock stays out");

            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            portal.entityInside(level, entry, pig, InsideBlockEffectApplier.NOOP, true);
            helper.assertTrue(pig.portalProcess != null, "with the unlock, the pig steps in with its rider");
            portal.entityInside(level, entry, player, InsideBlockEffectApplier.NOOP, true);
            helper.assertTrue(player.portalProcess == null, "the rider itself starts no trip: its pig takes it along");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * Done when of RLM-335: a player in survival travels on the first tick they spend in the portal, through vanilla's
     * own portal processing. The portal cooldown vanilla sets after the trip keeps them from bouncing straight back
     * while they stand in the exit portal, and once they stepped out for half a second, stepping in takes them back at
     * once too.
     */
    @QaGameTest(timeoutTicks = 200)
    public static void aPlayerTravelsTheTickTheyStepInAndDoesNotBounceBack(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        ServerLevel realm = RealmGameTests.realm(helper, stone);
        int cellX = 3_100;
        int cellZ = 3_100;
        PortalFrame.Frame first = litOverworldFrame(level, cellX * scale(realm) + 1, cellZ * scale(realm) + 1, stone);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-portal-instant");
        try {
            RealmGameTests.award(helper, player, stone.unlockAdvancement());
            player.getAbilities().invulnerable = false;
            standIn(player, level, first);
            stepIn(player, level, first);
            helper.assertTrue(player.level() == realm, "one tick in the portal took the player into the realm");
            PortalFrame.Frame built = realmPortalNear(helper, realm, new BlockPos(cellX, REALM_FLOOR, cellZ));
            assertArrivedAt(helper, player, built);
            Vec3 arrival = player.position();
            helper.assertValueEqual(player.getPortalCooldown(), player.getDimensionChangingDelay(),
                    "the trip set vanilla's portal cooldown");
            // The cooldown waits while the client loads the new dimension; a real client's teleport confirmation ends
            // that, which a fake player never sends.
            player.hasChangedDimension();

            // Straight back into the exit portal: every tick inside starts the cooldown again, so no trip starts.
            standIn(player, realm, built);
            for (int tick = 0; tick < 3 * player.getDimensionChangingDelay(); tick++) {
                stepIn(player, realm, built);
            }
            helper.assertTrue(player.level() == realm, "the player standing in the exit portal did not bounce back");

            // Out of the portal until the cooldown is over, then in again: the trip back starts at once.
            player.teleportTo(realm, arrival.x, arrival.y, arrival.z, Set.of(), 0.0F, 0.0F, true);
            for (int tick = 0; tick < player.getDimensionChangingDelay(); tick++) {
                serverTick(player);
            }
            helper.assertFalse(player.isOnPortalCooldown(), "the cooldown is over half a second after stepping out");
            standIn(player, realm, built);
            stepIn(player, realm, built);
            helper.assertTrue(player.level() == level, "one tick in the portal took the player back to Overworld");
            assertArrivedAt(helper, player, first);
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * A mob travels into the realm and back, and a pig carries its rider, a mob too, through the portal: both arrive
     * at the linked portal, the rider still on the pig. Only the portal lets them in: a plain teleport is refused.
     */
    @QaGameTest(timeoutTicks = 200)
    public static void mobsAndTheirRidersTravelThroughThePortal(GameTestHelper helper) {
        RealmExpectation stone = RealmGameTests.stone();
        ServerLevel level = helper.getLevel();
        ServerLevel realm = RealmGameTests.realm(helper, stone);
        int cellX = 2_800;
        int cellZ = 2_800;
        PortalFrame.Frame first = litOverworldFrame(level, cellX * scale(realm) + 1, cellZ * scale(realm) + 1, stone);
        Vec3 inside = Vec3.atBottomCenterOf(first.at(1, 0));

        Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        helper.assertTrue(zombie != null, "a zombie");
        zombie.snapTo(inside.x, inside.y, inside.z, 0.0F, 0.0F);
        level.addFreshEntity(zombie);
        helper.assertTrue(zombie.teleport(new TeleportTransition(realm, inside, Vec3.ZERO, 0.0F, 0.0F,
                TeleportTransition.DO_NOTHING)) == null, "no mob enters the realm but through a realm portal");
        Entity arrived = travel(helper, zombie, level, first.at(1, 1));
        helper.assertTrue(arrived.level() == realm, "the zombie travelled into the realm");
        PortalFrame.Frame built = realmPortalNear(helper, realm, new BlockPos(cellX, REALM_FLOOR, cellZ));
        assertArrivedAt(helper, arrived, built);
        Entity back = travel(helper, arrived, realm, built.at(1, 1));
        helper.assertTrue(back.level() == level, "the zombie travelled back to Overworld");
        assertArrivedAt(helper, back, first);
        back.discard();

        Pig pig = EntityType.PIG.create(level, EntitySpawnReason.COMMAND);
        Zombie rider = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        helper.assertTrue(pig != null && rider != null, "a pig and its rider");
        pig.snapTo(inside.x, inside.y, inside.z, 0.0F, 0.0F);
        rider.snapTo(inside.x, inside.y, inside.z, 0.0F, 0.0F);
        level.addFreshEntity(pig);
        level.addFreshEntity(rider);
        helper.assertTrue(rider.startRiding(pig, true, false), "the zombie mounts the pig");
        Entity carried = travel(helper, pig, level, first.at(1, 1));
        helper.assertTrue(carried.level() == realm, "the pig travelled into the realm");
        helper.assertTrue(carried.getPassengers().size() == 1 && carried.getPassengers().getFirst() instanceof Zombie
                        && carried.getPassengers().getFirst().level() == realm,
                "its rider travelled with it and still rides it");
        assertArrivedAt(helper, carried, built);
        carried.getPassengers().forEach(Entity::discard);
        carried.discard();
        helper.succeed();
    }

    // Helpers ------------------------------------------------------------------------------------------------------

    private static Identifier id(RealmExpectation realm) {
        return Identifier.parse(realm.id());
    }

    private static ItemStack signet(RealmExpectation realm) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(realm.signet())));
    }

    /** The 13 Realmforge Bricks and the sealstone of {@code frame}, as a player places them, with an empty opening. */
    private static void buildFrame(ServerLevel level, PortalFrame.Frame frame) {
        level.setBlockAndUpdate(frame.sealstone(), RealmPortalBlocks.sealstone().defaultBlockState());
        for (BlockPos pos : frame.bricks()) {
            level.setBlockAndUpdate(pos, RealmPortalBlocks.bricks().defaultBlockState());
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

    /** A frame along x in Overworld, its sealstone on the flat world's grass at x, z, lit onto the realm. */
    private static PortalFrame.Frame litOverworldFrame(ServerLevel level, int x, int z, RealmExpectation realm) {
        PortalFrame.Frame frame = new PortalFrame.Frame(new BlockPos(x, OVERWORLD_GROUND, z), Direction.Axis.X);
        buildFrame(level, frame);
        RealmPortals.light(level, frame, id(realm));
        return frame;
    }

    /** The player uses the sealstone at {@code sealstone} as a right-click with the main hand would. */
    private static void use(ServerPlayer player, ServerLevel level, BlockPos sealstone, ItemStack mainHand,
            ItemStack offHand, boolean sneaking) {
        player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
        player.setItemInHand(InteractionHand.OFF_HAND, offHand);
        player.setShiftKeyDown(sneaking);
        player.gameMode.useItemOn(player, level, mainHand, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(sealstone), Direction.UP, sealstone, false));
        player.setShiftKeyDown(false);
    }

    /** Puts the player into the opening's bottom middle. */
    private static void standIn(ServerPlayer player, ServerLevel level, PortalFrame.Frame frame) {
        Vec3 at = Vec3.atBottomCenterOf(frame.at(1, 0));
        player.teleportTo(level, at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
    }

    /**
     * One server tick of the player standing in the frame's opening: the portal block at its bottom middle sees them
     * inside, as moving there does, and the player's tick processes the portal, as vanilla's portal processing does.
     */
    private static void stepIn(ServerPlayer player, ServerLevel level, PortalFrame.Frame frame) {
        BlockPos entry = frame.at(1, 0);
        BlockState portal = level.getBlockState(entry);
        if (portal.getBlock() instanceof RealmPortalBlock) {
            portal.entityInside(level, entry, player, InsideBlockEffectApplier.NOOP, true);
        }
        serverTick(player);
    }

    /**
     * One server tick of a fake player, which no connection ticks: the level sets its old position first, as
     * {@code ServerLevel#tickNonPassenger} does, so the tick checks the blocks it moved through since the last one,
     * not since the test began, and then the player ticks.
     */
    private static void serverTick(ServerPlayer player) {
        player.setOldPosAndRot();
        player.doTick();
    }

    /**
     * The trip through the portal block at {@code entry}, as vanilla makes it once the portal's time is up, and how
     * long it took: finding or building the exit portal (with the chunks it generates), then the teleport. Returns the
     * entity in the new level: a player stays the same, any other entity is copied there.
     */
    private static Entity travel(GameTestHelper helper, Entity entity, ServerLevel level, BlockPos entry) {
        BlockState state = level.getBlockState(entry);
        helper.assertTrue(state.getBlock() instanceof RealmPortalBlock, "a portal block at " + entry);
        long start = System.nanoTime();
        TeleportTransition transition = ((RealmPortalBlock) state.getBlock()).getPortalDestination(level, entity, entry);
        helper.assertTrue(transition != null, "the portal at " + entry + " leads somewhere");
        long linked = System.nanoTime();
        Entity arrived = entity.teleport(transition);
        LOGGER.info("Realm portal trip from {} {}: exit portal {} ms, teleport {} ms", level.dimension().identifier(),
                entry, (linked - start) / 1_000_000, (System.nanoTime() - linked) / 1_000_000);
        helper.assertTrue(arrived != null, "the trip through " + entry + " was let through");
        return arrived;
    }

    /** Every corner of a frame a trip built is Realmforge Bricks that drop nothing (Matěj, 2026-10-20). */
    private static void assertCornersBuilt(GameTestHelper helper, ServerLevel level, PortalFrame.Frame frame) {
        for (BlockPos corner : frame.corners()) {
            BlockState state = level.getBlockState(corner);
            helper.assertTrue(state.is(RealmPortalBlocks.bricks()) && state.getValue(RealmforgeBricksBlock.GENERATED),
                    "the built frame has generated Realmforge Bricks in its corner at " + corner + ", not " + state);
        }
    }

    /** The realm's coordinate scale: 8, as the Nether's (Matěj, 2026-10-28), which {@code RealmGameTests} checks. */
    private static int scale(ServerLevel realm) {
        return (int) realm.dimensionType().coordinateScale();
    }

    /** The lowest height a frame built in the stone realm may stand at, where a trip from the flat world carves. */
    private static int stoneArrivalMinY(ServerLevel realm) {
        return Realms.of(realm.registryAccess(), realm.dimension()).orElseThrow().value().arrival().minY();
    }

    /** The realm's return portal within the realm's search radius of {@code centre}. */
    private static PortalFrame.Frame realmPortalNear(GameTestHelper helper, ServerLevel realm, BlockPos centre) {
        Optional<PortalFrame.Frame> frame = RealmPortalLinker.findPortal(realm, centre,
                RealmPortalLinker.searchRadius(realm), Realmforge.id("overworld"));
        helper.assertTrue(frame.isPresent(), "a return portal near " + centre);
        return frame.get();
    }

    /**
     * The entity stands in front of the frame's opening, on the floor the frame stands on or at the opening's height
     * for a frame set into the floor, or in the opening.
     */
    private static void assertArrivedAt(GameTestHelper helper, Entity entity, PortalFrame.Frame frame) {
        BlockPos middle = frame.at(1, 0);
        BlockPos at = entity.blockPosition();
        helper.assertTrue((at.getY() == middle.getY() || at.getY() == middle.getY() - 1)
                        && Math.abs(at.getX() - middle.getX()) <= 1 && Math.abs(at.getZ() - middle.getZ()) <= 1,
                "the entity arrived at the portal at " + middle + ", not at " + at);
    }

    /** The sealstones within {@code radius} of {@code centre}, lit or not. */
    private static long sealstonesNear(ServerLevel level, BlockPos centre, int radius) {
        return level.getPoiManager().getInSquare(type -> type.is(RealmPortalBlocks.SEALSTONE_POI), centre, radius,
                PoiManager.Occupancy.ANY).count();
    }

    private static void assertGlow(GameTestHelper helper, ServerLevel level, BlockPos sealstone, int glow,
            String message) {
        BlockState state = level.getBlockState(sealstone);
        helper.assertValueEqual(state.getValue(SealstoneBlock.GLOW), glow, message);
        helper.assertValueEqual(state.getLightEmission(), glow, message + " (its block light)");
    }

    private static boolean lit(ServerLevel level, PortalFrame.Frame frame) {
        return frame.opening().stream().allMatch(pos -> level.getBlockState(pos).getBlock() instanceof RealmPortalBlock);
    }

    private static boolean dark(ServerLevel level, PortalFrame.Frame frame) {
        return frame.opening().stream().noneMatch(pos -> level.getBlockState(pos).getBlock() instanceof RealmPortalBlock);
    }
}
