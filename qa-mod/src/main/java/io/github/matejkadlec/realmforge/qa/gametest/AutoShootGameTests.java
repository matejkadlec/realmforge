package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.combat.AutoShoot;
import io.github.matejkadlec.realmforge.combat.AutoShoot.Weapon;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * Auto Shoot's rule (RLM-496, {@link AutoShoot}) on real items: which items it releases, and that its bow release
 * fires a full-draw arrow, the crit arrow of {@code HitTracker}, even when the server counts a tick less than the
 * client. The client's release itself (holding right click) needs a client: the playtest covers it.
 */
public final class AutoShootGameTests {
    private AutoShootGameTests() {
    }

    /** Bows and crossbows are released; the trident, throwables and other items never are. */
    @QaGameTest
    public static void onlyBowsAndCrossbowsAreReleased(GameTestHelper helper) {
        helper.assertValueEqual(AutoShoot.weapon(new ItemStack(Items.BOW)), Weapon.BOW, "a bow");
        helper.assertValueEqual(AutoShoot.weapon(new ItemStack(Items.CROSSBOW)), Weapon.CROSSBOW, "a crossbow");
        helper.assertValueEqual(AutoShoot.weapon(new ItemStack(Items.TRIDENT)), Weapon.NONE, "the trident");
        helper.assertValueEqual(AutoShoot.weapon(new ItemStack(Items.SNOWBALL)), Weapon.NONE, "a snowball");
        helper.assertValueEqual(AutoShoot.weapon(new ItemStack(Items.SHIELD)), Weapon.NONE, "a shield");
        helper.assertValueEqual(AutoShoot.weapon(ItemStack.EMPTY), Weapon.NONE, "an empty hand");
        helper.succeed();
    }

    /** A bow is released one tick past full draw, a crossbow once the server loaded it, whatever its use time. */
    @QaGameTest
    public static void aBowWaitsForFullDrawAndACrossbowForItsLoad(GameTestHelper helper) {
        ItemStack bow = new ItemStack(Items.BOW);
        helper.assertFalse(AutoShoot.ready(bow, BowItem.MAX_DRAW_DURATION), "a bow at exactly full draw waits");
        helper.assertTrue(AutoShoot.ready(bow, AutoShoot.BOW_RELEASE_TICKS), "a bow one tick past full draw");
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        helper.assertFalse(AutoShoot.ready(crossbow, 200), "an empty crossbow keeps loading");
        crossbow.set(DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
        helper.assertTrue(AutoShoot.ready(crossbow, 1), "a loaded crossbow");
        helper.assertFalse(AutoShoot.ready(new ItemStack(Items.TRIDENT), 200), "the trident is never released");
        helper.succeed();
    }

    /**
     * The release fires a crit arrow even when the server's count is a tick behind the client's (one tick short of
     * the release), and a release a tick short of full draw does not: the margin is what keeps every shot full.
     */
    @QaGameTest
    public static void theReleaseFiresAFullDrawArrow(GameTestHelper helper) {
        helper.assertTrue(shoot(helper, "qa-auto-shoot-full", AutoShoot.BOW_RELEASE_TICKS - 1).isCritArrow(),
                "a release a tick early is still at full draw");
        helper.assertFalse(shoot(helper, "qa-auto-shoot-short", BowItem.MAX_DRAW_DURATION - 1).isCritArrow(),
                "a tick short of full draw is no crit");
        helper.succeed();
    }

    /** A survival player who draws a bow for {@code ticks} and lets go: the arrow it fires. */
    private static AbstractArrow shoot(GameTestHelper helper, String name, int ticks) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name);
        try {
            player.setGameMode(GameType.SURVIVAL);
            ItemStack quiver = new ItemStack(Items.ARROW, 4);
            player.setItemInHand(InteractionHand.OFF_HAND, quiver);
            ItemStack bow = new ItemStack(Items.BOW);
            player.setItemInHand(InteractionHand.MAIN_HAND, bow);
            bow.releaseUsing(helper.getLevel(), player, bow.getUseDuration(player) - ticks);
            helper.assertValueEqual(quiver.getCount(), 3, "arrows left in the off hand after the shot");
            List<AbstractArrow> arrows = helper.getLevel().getEntitiesOfClass(AbstractArrow.class,
                    AABB.ofSize(player.position(), 8, 8, 8), arrow -> arrow.getOwner() == player);
            helper.assertValueEqual(arrows.size(), 1, "arrows the player fired");
            AbstractArrow arrow = arrows.getFirst();
            arrow.discard();
            return arrow;
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }
}
