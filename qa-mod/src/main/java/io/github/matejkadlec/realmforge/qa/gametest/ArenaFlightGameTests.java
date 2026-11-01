package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.boss.ArenaFlight;
import io.github.matejkadlec.realmforge.boss.BossMob;
import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * RLM-559: creative flight is off in a boss's arena (Matěj, 2026-10-16 and 2026-10-29, RLM-232), here the test titan's
 * (12 blocks round its home). Flight comes from {@code neoforge:creative_flight}, the attribute Apotheosis's Unbound
 * gives, put on the player directly. Another test's boss may stand near enough for its arena to reach the player too
 * (Astone's is 20 blocks), so the tests check what the titan holds, and that flight is back exactly when no boss holds
 * the player ({@link #assertFlightFollowsTheHolds}).
 */
public final class ArenaFlightGameTests {
    private static final Vec3 MIDDLE = new Vec3(8.5, 1, 8.5);
    /** In the titan's arena, in the air. */
    private static final Vec3 INSIDE = MIDDLE.add(3, 3, 0);
    /** Above every boss arena of the GameTests: the titan's is 12 blocks round its home, Astone's 20. */
    private static final double ABOVE = 40;
    private static final Identifier UNBOUND = Identifier.fromNamespaceAndPath("realmforge_qa", "unbound");
    private static final int CHECK = ArenaFlight.CHECK_INTERVAL + 1;

    private ArenaFlightGameTests() {
    }

    /**
     * Done when of RLM-559: a player flying into a boss's arena loses flight, gets Slow Falling and the warning, gets
     * flight back on leaving the arena, loses it again on coming back, and gets it back when the boss dies.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void flightIsOffInABossArenaUntilThePlayerLeavesOrTheBossDies(GameTestHelper helper) {
        BossMob titan = titan(helper);
        ServerPlayer player = flyer(helper, "qa-arena-flight");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(titan.home().isPresent(), "the titan took its home"))
                .thenExecute(() -> {
                    QaPlayers.onPlatform(helper, player, INSIDE);
                    player.getAbilities().flying = true;
                    QaPlayers.sentChatLines(player);
                })
                .thenExecuteAfter(CHECK, () -> {
                    player.doTick();
                    helper.assertTrue(ArenaFlight.isHeldBy(player, titan), "the titan's arena holds the player");
                    helper.assertFalse(player.mayFly(), "no flight in the arena");
                    helper.assertFalse(player.getAbilities().flying, "the server landed the flying player");
                    helper.assertTrue(player.hasEffect(MobEffects.SLOW_FALLING), "Slow Falling on losing flight");
                    helper.assertTrue(warned(player), "the warning in chat");
                    QaPlayers.onPlatform(helper, player, INSIDE.add(0, ABOVE, 0));
                })
                .thenExecuteAfter(CHECK, () -> {
                    helper.assertFalse(ArenaFlight.isHeldBy(player, titan), "the titan lets go of a player who left");
                    helper.assertTrue(player.mayFly(), "flight back above every arena");
                    QaPlayers.onPlatform(helper, player, INSIDE);
                })
                .thenExecuteAfter(CHECK, () -> {
                    helper.assertTrue(ArenaFlight.isHeldBy(player, titan), "held again back in the arena");
                    helper.assertFalse(player.mayFly(), "no flight again back in the arena");
                    titan.kill(helper.getLevel());
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertFalse(ArenaFlight.isHeldBy(player, titan), "the boss's death lets go of the player");
                    assertFlightFollowsTheHolds(helper, player);
                    ApotheosisGameTests.logout(player);
                })
                .thenSucceed();
    }

    /**
     * Done when of RLM-559: the boss's reset gives flight back at once. A player without flight is held too, so flight
     * gained inside stays off, but gets no warning and no Slow Falling.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void theBossResetGivesFlightBack(GameTestHelper helper) {
        BossMob titan = titan(helper);
        ServerPlayer flyer = flyer(helper, "qa-arena-reset");
        ServerPlayer walker = ApotheosisGameTests.player(helper, "qa-arena-walker");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(titan.home().isPresent(), "the titan took its home"))
                .thenExecute(() -> {
                    QaPlayers.onPlatform(helper, flyer, INSIDE);
                    QaPlayers.onPlatform(helper, walker, MIDDLE.add(-3, 0, 0));
                    QaPlayers.sentChatLines(walker);
                })
                .thenExecuteAfter(CHECK, () -> {
                    helper.assertTrue(ArenaFlight.isHeldBy(flyer, titan), "the titan's arena holds the flyer");
                    helper.assertFalse(flyer.mayFly(), "no flight in the arena");
                    helper.assertTrue(ArenaFlight.isHeldBy(walker, titan), "the arena holds the walker too");
                    helper.assertFalse(warned(walker), "no warning for a player who could not fly");
                    helper.assertFalse(walker.hasEffect(MobEffects.SLOW_FALLING), "no Slow Falling either");
                    titan.reset();
                    helper.assertFalse(ArenaFlight.isHeldBy(flyer, titan), "the reset lets go of the flyer");
                    assertFlightFollowsTheHolds(helper, flyer);
                    helper.assertFalse(ArenaFlight.isHeldBy(walker, titan), "the reset lets go of the walker");
                    ApotheosisGameTests.logout(flyer);
                    ApotheosisGameTests.logout(walker);
                })
                .thenSucceed();
    }

    /**
     * PR review of RLM-559: a held player who leaves for another dimension (a realm portal, Waystones, /tp) gets flight
     * back from their own tick. The boss's check never runs for them there, and a boss stops ticking once nobody is
     * near. A boss removed without dying (a discard; its chunk unloading goes the same way) lets go too.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void flightReturnsInAnotherDimensionAndWhenTheBossIsRemoved(GameTestHelper helper) {
        BossMob titan = titan(helper);
        ServerPlayer player = flyer(helper, "qa-arena-away");
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(titan.home().isPresent(), "the titan took its home"))
                .thenExecute(() -> QaPlayers.onPlatform(helper, player, INSIDE))
                .thenExecuteAfter(CHECK, () -> {
                    helper.assertTrue(ArenaFlight.isHeldBy(player, titan), "the titan's arena holds the player");
                    helper.assertTrue(nether != null, "the GameTest server has the Nether");
                    player.teleportTo(nether, 0.5, 200, 0.5, Set.of(), 0.0F, 0.0F, true);
                    // What the player's tick runs every 5 ticks (PlayerTickEvent.Post), before the titan's next check
                    // could run. A fake player's tick count only advances in the level's entity loop, so ticking it
                    // here would never reach that multiple.
                    ArenaFlight.recheck(player);
                    helper.assertFalse(ArenaFlight.isHeldBy(player, titan),
                            "the player's side lets go of a boss in another dimension");
                    assertFlightFollowsTheHolds(helper, player);
                    Vec3 back = helper.absoluteVec(INSIDE);
                    player.teleportTo(helper.getLevel(), back.x, back.y, back.z, Set.of(), 0.0F, 0.0F, true);
                })
                .thenExecuteAfter(CHECK, () -> {
                    helper.assertTrue(ArenaFlight.isHeldBy(player, titan), "held again back in the arena");
                    titan.discard();
                    helper.assertFalse(ArenaFlight.isHeldBy(player, titan), "a removed boss lets go of the player");
                    assertFlightFollowsTheHolds(helper, player);
                    ApotheosisGameTests.logout(player);
                })
                .thenSucceed();
    }

    /** The test titan in the middle of the platform, which draws nobody into a fight. */
    private static BossMob titan(GameTestHelper helper) {
        BossMob titan = (BossMob) helper.spawn(GeneratedMobs.TEST_TITAN.get(), MIDDLE);
        titan.targetSelector.removeAllGoals(goal -> true);
        return titan;
    }

    /** A survival player with creative flight from its attribute, as Unbound gives it, outside the arena for now. */
    private static ServerPlayer flyer(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, MIDDLE.add(0, ABOVE, 0));
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).addPermanentModifier(
                new AttributeModifier(UNBOUND, 1, AttributeModifier.Operation.ADD_VALUE));
        helper.assertTrue(player.mayFly(), "the attribute lets the player fly");
        return player;
    }

    /** The player can fly exactly when no boss's arena holds them. */
    private static void assertFlightFollowsTheHolds(GameTestHelper helper, ServerPlayer player) {
        helper.assertValueEqual(player.mayFly(), !ArenaFlight.isHeld(player),
                "flight is back exactly when no boss holds the player");
    }

    /** Whether the server sent the player the arena's chat line since the last drain. */
    private static boolean warned(ServerPlayer player) {
        List<Component> lines = QaPlayers.sentChatLines(player);
        return lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(GeneratedTexts.MESSAGE_ARENA_NO_FLIGHT_CHAT));
    }
}
