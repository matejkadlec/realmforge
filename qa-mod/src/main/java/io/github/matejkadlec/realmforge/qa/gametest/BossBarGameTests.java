package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.boss.BossBar;
import io.github.matejkadlec.realmforge.qa.boss.QaBosses;
import io.github.matejkadlec.realmforge.qa.boss.TestBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * The boss bar (RLM-173, architecture 14.3) on the QA mod's test boss: the players tracking the boss see it, it
 * follows the boss's health and title, and it goes away when the boss dies or a player stops tracking it.
 *
 * <p>A player starts tracking an entity once its client has the entity's chunk and reports where it stands. A fake
 * player's client does neither, so the tests play that part every tick ({@link #playClient}).
 */
public final class BossBarGameTests {
    private static final String NAME = "Test Boss";
    private static final float EPSILON = 1.0E-4F;

    private BossBarGameTests() {
    }

    /** A player near the boss sees its bar, which follows its health and title and goes away when it dies. */
    @QaGameTest
    public static void aPlayerNearTheBossSeesItsBarUntilItDies(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-boss-bar-near");
        TestBoss boss = boss(helper);
        BossBar bar = boss.bossBar();
        helper.assertTrue(boss.is(Tags.EntityTypes.BOSSES), "the test boss is in c:bosses");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    playClient(helper, player);
                    helper.assertTrue(bar.players().contains(player), "the player tracking the boss sees its bar");
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(bar.title().getString(), NAME, "the bar's title");
                    assertProgress(helper, bar, 1.0F, "a boss at full health");
                    boss.setHealth(boss.getMaxHealth() / 4);
                })
                .thenExecuteAfter(1, () -> {
                    assertProgress(helper, bar, 0.25F, "a boss at a quarter of its health");
                    bar.setTitle(Component.literal("Phase two"));
                    helper.assertValueEqual(bar.title().getString(), "Phase two", "the title the boss set");
                    bar.setTitle(null);
                    helper.assertValueEqual(bar.title().getString(), NAME, "the boss's name again");
                    boss.kill(helper.getLevel());
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertTrue(bar.players().isEmpty(), "nobody sees a dead boss's bar");
                    bar.startSeenBy(player);
                    helper.assertTrue(bar.players().isEmpty(), "a player who starts tracking a dying boss sees no bar");
                    CastGameTests.logout(player);
                })
                .thenSucceed();
    }

    /** A player who walks out of the boss's tracking range or logs out no longer sees its bar; the others still do. */
    @QaGameTest
    public static void aPlayerWhoLeavesNoLongerSeesTheBar(GameTestHelper helper) {
        ServerPlayer walker = QaPlayers.loginOnPlatform(helper, "qa-boss-bar-walker", new Vec3(0.5, 1, 0.5));
        ServerPlayer leaver = QaPlayers.loginOnPlatform(helper, "qa-boss-bar-leaver", new Vec3(4.5, 1, 0.5));
        BossBar bar = boss(helper).bossBar();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    playClient(helper, walker);
                    playClient(helper, leaver);
                    helper.assertTrue(bar.players().contains(walker) && bar.players().contains(leaver),
                            "both players see the bar: " + bar.players());
                })
                .thenExecute(() -> {
                    CastGameTests.logout(leaver);
                    helper.assertFalse(bar.players().contains(leaver), "a player who logged out no longer sees it");
                    helper.assertTrue(bar.players().contains(walker), "the other player still sees it");

                    walker.snapTo(helper.absoluteVec(new Vec3(-400, 1, 0.5)), 0, 0);
                    helper.getLevel().getChunkSource().move(walker);
                    helper.assertFalse(bar.players().contains(walker), "a player out of range no longer sees it");
                    CastGameTests.logout(walker);
                })
                .thenSucceed();
    }

    /** A test boss that stands still at the platform's north edge. */
    private static TestBoss boss(GameTestHelper helper) {
        TestBoss boss = helper.spawn(QaBosses.TEST_BOSS.get(), new BlockPos(2, 1, 4));
        boss.setNoAi(true);
        return boss;
    }

    /**
     * Plays a fake player's client for a tick: it acknowledges the chunks the server sent, so the server sends the next
     * ones (it stops after one batch it has no answer for), and reports where it stands, which is when the server
     * checks which entities the player tracks ({@code ServerChunkCache#move}).
     */
    private static void playClient(GameTestHelper helper, ServerPlayer player) {
        player.connection.chunkSender.onChunkBatchReceivedByClient(PlayerChunkSender.MAX_CHUNKS_PER_TICK);
        helper.getLevel().getChunkSource().move(player);
    }

    private static void assertProgress(GameTestHelper helper, BossBar bar, float expected, String what) {
        helper.assertTrue(Math.abs(bar.progress() - expected) < EPSILON,
                what + ": the bar is at " + bar.progress() + ", not " + expected);
    }
}
