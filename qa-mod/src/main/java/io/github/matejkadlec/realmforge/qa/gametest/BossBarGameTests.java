package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.boss.BossBar;
import io.github.matejkadlec.realmforge.qa.boss.QaBosses;
import io.github.matejkadlec.realmforge.qa.boss.TestBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * The boss bar (RLM-173, architecture 14.3) on the QA mod's test boss: the players tracking the boss see it, it
 * follows the boss's health and title, and it goes away when the boss dies or a player stops tracking it.
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
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(bar.players().contains(player), "the player tracking the boss sees its bar");
            helper.assertValueEqual(bar.title().getString(), NAME, "the bar's title");
            assertProgress(helper, bar, 1.0F, "a boss at full health");

            boss.setHealth(boss.getMaxHealth() / 4);
            helper.runAfterDelay(1, () -> {
                assertProgress(helper, bar, 0.25F, "a boss at a quarter of its health");

                bar.setTitle(Component.literal("Phase two"));
                helper.assertValueEqual(bar.title().getString(), "Phase two", "the title the boss set");
                bar.setTitle(null);
                helper.assertValueEqual(bar.title().getString(), NAME, "the boss's name again");

                boss.kill(helper.getLevel());
                helper.runAfterDelay(1, () -> {
                    helper.assertTrue(bar.players().isEmpty(), "nobody sees a dead boss's bar");
                    bar.startSeenBy(player);
                    helper.assertTrue(bar.players().isEmpty(), "a player who starts tracking a dying boss sees no bar");
                    CastGameTests.logout(player);
                    helper.succeed();
                });
            });
        });
    }

    /**
     * A player who walks out of the boss's tracking range or logs out no longer sees its bar; the others still do,
     * and a player who comes back sees it again.
     */
    @QaGameTest
    public static void aPlayerWhoLeavesNoLongerSeesTheBar(GameTestHelper helper) {
        ServerPlayer walker = QaPlayers.loginOnPlatform(helper, "qa-boss-bar-walker", new Vec3(0.5, 1, 0.5));
        ServerPlayer leaver = QaPlayers.loginOnPlatform(helper, "qa-boss-bar-leaver", new Vec3(4.5, 1, 0.5));
        BossBar bar = boss(helper).bossBar();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(bar.players().contains(walker) && bar.players().contains(leaver),
                    "both players see the bar: " + bar.players());

            CastGameTests.logout(leaver);
            helper.assertFalse(bar.players().contains(leaver), "a player who logged out no longer sees the bar");
            helper.assertTrue(bar.players().contains(walker), "the other player still sees it");

            // The server moves a player when its client's move arrives; a fake player has no client.
            walker.snapTo(helper.absoluteVec(new Vec3(-400, 1, 0.5)), 0, 0);
            helper.getLevel().getChunkSource().move(walker);
            helper.assertFalse(bar.players().contains(walker), "a player out of tracking range no longer sees it");

            QaPlayers.onPlatform(helper, walker, new Vec3(0.5, 1, 0.5));
            helper.getLevel().getChunkSource().move(walker);
            helper.assertTrue(bar.players().contains(walker), "a player who comes back sees it again");
            CastGameTests.logout(walker);
            helper.succeed();
        });
    }

    /** A test boss that stands still at the platform's north edge. */
    private static TestBoss boss(GameTestHelper helper) {
        TestBoss boss = helper.spawn(QaBosses.TEST_BOSS.get(), new BlockPos(2, 1, 4));
        boss.setNoAi(true);
        return boss;
    }

    private static void assertProgress(GameTestHelper helper, BossBar bar, float expected, String what) {
        helper.assertTrue(Math.abs(bar.progress() - expected) < EPSILON,
                what + ": the bar is at " + bar.progress() + ", not " + expected);
    }
}
