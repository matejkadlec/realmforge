package io.github.matejkadlec.realmforge.qa.gametest;

import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.close;
import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.setBase;

import io.github.matejkadlec.realmforge.combat.WeaponBlocks;
import io.github.matejkadlec.realmforge.compat.bettercombat.TwoHandedWeapons;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.qa.combat.QaItems;
import io.github.matejkadlec.realmforge.qa.combat.SoundLog;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * RLM-297: blocking with a two-handed weapon (architecture 9.3), with the rules' defaults (Matěj, 2026-10-22): a hit
 * in the first 0.5 s after raising the weapon is blocked in full, a later one by half; after a blocked hit the weapon
 * goes down and cannot be raised for 1 s, as after lowering it by hand. The QA mod's test greatsword stands for the
 * pack's two-handed weapons: Better Combat's fallback calls it a claymore.
 */
public final class WeaponBlockGameTests {
    /** Where the player stands: the platform's north edge, facing south (yaw 0) or north (yaw 180). */
    private static final Vec3 EDGE = new Vec3(2.5, 1, 0.5);
    /** A hit's damage, before the block. */
    private static final float HIT = 10;
    /** A smaller hit, which opens the hurt cooldown window that a later {@link #HIT} still beats. */
    private static final float SMALL_HIT = 4;
    /** Ticks past the full-block window of 10 ticks (0.5 s). */
    private static final int AFTER_WINDOW = 12;

    private WeaponBlockGameTests() {
    }

    /**
     * Sword Blocking Mechanics' tag of raisable weapons holds Better Combat's two-handed weapons whose right click does
     * nothing else: the test greatsword (a claymore), not a spear (two-handed, but its right click charges), not a
     * one-handed sword (the mod's own default, which our data replaces), not a bow.
     */
    @QaGameTest
    public static void theTagHoldsTwoHandedWeaponsOnly(GameTestHelper helper) {
        helper.assertTrue(new ItemStack(QaItems.TEST_GREATSWORD.get()).is(WeaponBlocks.RAISABLE),
                "the test greatsword is raised to block");
        helper.assertFalse(new ItemStack(Items.IRON_SPEAR).is(WeaponBlocks.RAISABLE), "a spear keeps its charge");
        helper.assertFalse(new ItemStack(Items.IRON_SWORD).is(WeaponBlocks.RAISABLE), "a sword is one-handed");
        helper.assertFalse(new ItemStack(Items.BOW).is(WeaponBlocks.RAISABLE), "a bow keeps its draw");
        helper.succeed();
    }

    /**
     * A client's tag update from its server over the network (a {@code /reload} there) replaces the items' tags with
     * the server's, whose raisable tag is empty data; the handler binds it again. Over an integrated server's
     * connection it leaves the shared items to the server. The tags are stripped and rebound within this one call on
     * the server thread, so no other test sees them stripped.
     */
    @QaGameTest
    public static void aClientRebindsTheRaisableWeaponsAfterItsServersTagUpdate(GameTestHelper helper) {
        ItemStack greatsword = new ItemStack(QaItems.TEST_GREATSWORD.get());
        RegistryAccess registries = helper.getLevel().registryAccess();
        try {
            stripRaisable();
            TwoHandedWeapons.onTagsReceived(new TagsUpdatedEvent.ClientPacketReceived(registries, true));
            helper.assertFalse(greatsword.is(WeaponBlocks.RAISABLE),
                    "an integrated server's client leaves the shared items to the server");
            TwoHandedWeapons.onTagsReceived(new TagsUpdatedEvent.ClientPacketReceived(registries, false));
            helper.assertTrue(greatsword.is(WeaponBlocks.RAISABLE), "a remote server's tag update rebinds the tag");
        } finally {
            TwoHandedWeapons.bind();
        }
        helper.succeed();
    }

    /** What a tag update from a server does to the raisable tag: every item loses it (the server's data is empty). */
    private static void stripRaisable() {
        BuiltInRegistries.ITEM.listElements().forEach(holder -> holder.bindTags(
                holder.tags().filter(tag -> !tag.equals(WeaponBlocks.RAISABLE)).toList()));
    }

    /**
     * Done when of RLM-297: a frontal hit after the full-block window deals half its damage; the weapon goes down at
     * the end of the tick and cannot be raised for 1 s.
     */
    @QaGameTest
    public static void aRaisedGreatswordBlocksHalfOfALaterFrontalHit(GameTestHelper helper) {
        ServerPlayer player = blocker(helper, "qa-block-half", 0);
        Zombie zombie = zombieInFront(helper);
        ItemStack greatsword = raise(player, new ItemStack(QaItems.TEST_GREATSWORD.get()), AFTER_WINDOW);
        helper.assertTrue(WeaponBlocks.isRaised(player), "the greatsword is raised");

        float before = player.getHealth();
        player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(zombie), HIT);
        double dealt = before - player.getHealth();
        helper.assertTrue(close(dealt, HIT / 2), "half of a " + HIT + " hit expected, dealt " + dealt);
        helper.assertTrue(player.getCooldowns().isOnCooldown(greatsword), "the blocked hit started the cooldown");
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    helper.assertFalse(player.isUsingItem(), "the blocked hit lowered the weapon");
                    player.startUsingItem(InteractionHand.MAIN_HAND);
                    helper.assertTrue(player.getCooldowns().isOnCooldown(greatsword),
                            "the weapon is still on its cooldown");
                    helper.getLevel().getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    /** A frontal hit in the first 0.5 s after raising the weapon is blocked in full. */
    @QaGameTest
    public static void aHitInTheFirstHalfSecondIsBlockedInFull(GameTestHelper helper) {
        ServerPlayer player = blocker(helper, "qa-block-full", 0);
        Zombie zombie = zombieInFront(helper);
        ItemStack greatsword = raise(player, new ItemStack(QaItems.TEST_GREATSWORD.get()), 2);

        float before = player.getHealth();
        helper.assertFalse(player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(zombie),
                HIT), "the hit was stopped");
        helper.assertTrue(close(player.getHealth(), before), "nothing dealt: " + (before - player.getHealth()));
        helper.assertTrue(player.damageContainers.isEmpty(), "the stopped hit left no damage container");
        helper.assertTrue(player.getCooldowns().isOnCooldown(greatsword), "the full block started the cooldown");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-387: a zombie's hit opens the player's hurt cooldown window, the player raises the greatsword,
     * and a second zombie's equal hit, which the window drops anyway, leaves the weapon alone: no block sound, no
     * cooldown, still raised. The next real hit, bigger than the window's last one, is still blocked in full.
     */
    @QaGameTest
    public static void aRaisedWeaponIgnoresAHitTheWindowDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = blocker(helper, "qa-block-window", 0);
        Zombie first = zombieInFront(helper);
        Zombie second = zombieInFront(helper);
        player.hurtServer(level, level.damageSources().mobAttack(first), SMALL_HIT);
        helper.assertTrue(player.invulnerableTime > 10, "the first hit opened a window: " + player.invulnerableTime);
        ItemStack greatsword = raise(player, new ItemStack(QaItems.TEST_GREATSWORD.get()), 2);
        long sounds = blockSounds(helper, player);

        float before = player.getHealth();
        helper.assertFalse(player.hurtServer(level, level.damageSources().mobAttack(second), SMALL_HIT),
                "the window dropped the second zombie's equal hit");
        helper.assertTrue(close(player.getHealth(), before), "nothing dealt: " + (before - player.getHealth()));
        helper.assertTrue(WeaponBlocks.isRaised(player), "the weapon is still raised");
        helper.assertFalse(player.getCooldowns().isOnCooldown(greatsword), "the dropped hit started no cooldown");
        helper.assertValueEqual(blockSounds(helper, player), sounds, "block sounds after the dropped hit");

        helper.assertFalse(player.hurtServer(level, level.damageSources().mobAttack(first), HIT),
                "the next, bigger hit was blocked in full");
        helper.assertTrue(close(player.getHealth(), before), "nothing dealt: " + (before - player.getHealth()));
        helper.assertTrue(player.getCooldowns().isOnCooldown(greatsword), "the real block started the cooldown");
        helper.assertValueEqual(blockSounds(helper, player), sounds + 1, "block sounds after the real block");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** A hit from behind is not blocked, nor one at a player holding up a one-handed sword, nor magic. */
    @QaGameTest
    public static void hitsFromBehindOrAtASwordOrOfMagicAreNotBlocked(GameTestHelper helper) {
        Zombie zombie = zombieInFront(helper);
        ServerPlayer facingAway = blocker(helper, "qa-block-behind", 180);
        raise(facingAway, new ItemStack(QaItems.TEST_GREATSWORD.get()), AFTER_WINDOW);
        assertFullHit(helper, facingAway, helper.getLevel().damageSources().mobAttack(zombie), "a hit from behind");
        assertFullHit(helper, facingAway, helper.getLevel().damageSources().magic(), "magic");
        helper.getLevel().getServer().getPlayerList().remove(facingAway);

        ServerPlayer withSword = blocker(helper, "qa-block-sword", 0);
        raise(withSword, new ItemStack(Items.IRON_SWORD), AFTER_WINDOW);
        helper.assertFalse(WeaponBlocks.isRaised(withSword), "a one-handed sword is not raised");
        assertFullHit(helper, withSword, helper.getLevel().damageSources().mobAttack(zombie), "a hit at a sword");
        helper.getLevel().getServer().getPlayerList().remove(withSword);
        helper.succeed();
    }

    /** Lowering the weapon by hand starts the same cooldown, so spamming right click never chains full blocks. */
    @QaGameTest
    public static void loweringByHandStartsTheCooldown(GameTestHelper helper) {
        ServerPlayer player = blocker(helper, "qa-block-lower", 0);
        ItemStack greatsword = raise(player, new ItemStack(QaItems.TEST_GREATSWORD.get()), 3);
        player.releaseUsingItem();
        helper.assertFalse(player.isUsingItem(), "the weapon went down");
        helper.assertTrue(player.getCooldowns().isOnCooldown(greatsword), "lowering it started the cooldown");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A fake player at the platform's north edge, facing {@code yaw}, that takes damage (in survival, its client marked
     * as loaded) and never dodges.
     */
    private static ServerPlayer blocker(GameTestHelper helper, String name, float yaw) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, EDGE);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        player.snapTo(helper.absoluteVec(EDGE), yaw, 0);
        StatsCommands.setBase(player, GeneratedStats.DODGE_CHANCE, 0);
        setBase(player, Attributes.ARMOR, 0);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /** Puts {@code weapon} into the player's main hand and holds it up for {@code ticks} of the player's ticks. */
    private static ItemStack raise(ServerPlayer player, ItemStack weapon, int ticks) {
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        for (int i = 0; i < ticks; i++) {
            player.doTick();
        }
        return weapon;
    }

    /** How often the raised weapon's block sound played at the player. */
    private static long blockSounds(GameTestHelper helper, ServerPlayer player) {
        return SoundLog.count(helper.getLevel(), player.position(), 0.5, SoundEvents.SHIELD_BLOCK);
    }

    /** A zombie that stands still south of the platform's north edge, at level 1, so its hits are unscaled. */
    private static Zombie zombieInFront(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 3));
        zombie.setNoAi(true);
        MobLevels.setLevel(zombie, EntityLevel.MIN);
        return zombie;
    }

    private static void assertFullHit(GameTestHelper helper, ServerPlayer player,
            net.minecraft.world.damagesource.DamageSource source, String what) {
        player.invulnerableTime = 0;
        player.setHealth(player.getMaxHealth());
        float before = player.getHealth();
        player.hurtServer(helper.getLevel(), source, HIT);
        double dealt = before - player.getHealth();
        helper.assertTrue(close(dealt, HIT), what + ": the full " + HIT + " expected, dealt " + dealt);
    }
}
