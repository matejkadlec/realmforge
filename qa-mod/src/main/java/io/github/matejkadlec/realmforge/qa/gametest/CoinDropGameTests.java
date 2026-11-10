package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.core.CoreRegistries;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.generated.GeneratedCurves;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.item.Coins;
import io.github.matejkadlec.realmforge.loot.CoinDropMath;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.stats.MobRanks;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Mob coin drops (RLM-286): a hostile mob a player killed drops coins by its level and rank, 50 to 150 % of the curve
 * {@code realmforge:coin_drop}, paid in the largest coins; the ranges at levels 1, 50 and 60; Looting III and a high
 * luck leave the amount as it is; and no coins without a player's kill credit, from a mob that is no enemy, from the
 * smallest slime (rank 0) or with the gamerule {@code mob_drops} off.
 */
public final class CoinDropGameTests {
    /** Kills per level in the range tests: enough to see both ends of the range come up. */
    private static final int KILLS = 40;

    private CoinDropGameTests() {
    }

    /** A level 1 zombie always drops exactly 1 copper: 50 to 150 % of 1 rounds to 1. */
    @QaGameTest
    public static void aLevelOneMobDropsOneCopper(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-coins-1");
        for (int kill = 0; kill < KILLS; kill++) {
            List<ItemStack> coins = coinsOf(helper, EntityType.ZOMBIE, 1, player, true);
            helper.assertValueEqual(coins.size(), 1, "one stack of coins");
            helper.assertTrue(coins.getFirst().is(GeneratedItems.COPPER_COIN.get()), "of copper: " + coins.getFirst());
            helper.assertValueEqual(coins.getFirst().getCount(), 1, "one copper");
        }
        logout(player);
        helper.succeed();
    }

    /** Level 50 is about 1 gold: between half and one and a half of the curve, with gold exactly when it reaches 1. */
    @QaGameTest
    public static void aLevelFiftyMobDropsAboutOneGold(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-coins-50");
        assertRange(helper, player, 50, 9_988);
        logout(player);
        helper.succeed();
    }

    /** Level 60 is about 5 gold. */
    @QaGameTest
    public static void aLevelSixtyMobDropsAboutFiveGold(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-coins-60");
        assertRange(helper, player, 60, 49_926);
        logout(player);
        helper.succeed();
    }

    /**
     * Looting III on the weapon and a luck of 1024 change nothing: a level 1 zombie still drops exactly 1 copper
     * every time, where one more coin per level of Looting, as vanilla's drops get, would show at once; and a level
     * 50 zombie stays in its range.
     */
    @QaGameTest
    public static void lootingAndLuckLeaveTheDropUnchanged(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-coins-luck");
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.LOOTING), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        player.getAttribute(Attributes.LUCK).setBaseValue(1024);
        helper.assertValueEqual(player.getLuck(), 1024.0F, "the player's luck");
        for (int kill = 0; kill < KILLS; kill++) {
            List<ItemStack> coins = coinsOf(helper, EntityType.ZOMBIE, 1, player, true);
            helper.assertValueEqual(coins.stream().mapToLong(Coins::value).sum(), 1L,
                    "1 copper with Looting III and luck, kill " + kill);
        }
        assertRange(helper, player, 50, 9_988);
        logout(player);
        helper.succeed();
    }

    /** The rank multiplies the drop: a witch (rank 1.5) at level 10 drops 1.5 times a zombie's range. */
    @QaGameTest
    public static void theRankMultipliesTheDrop(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-coins-rank");
        double base = curve(helper).valueAt(10);
        Witch probe = helper.spawn(EntityType.WITCH, new BlockPos(2, 1, 2));
        double rank = MobRanks.of(probe);
        probe.discard();
        helper.assertValueEqual(rank, 1.5, "a witch's rank");
        long least = CoinDropMath.amount(base, rank, 0);
        long most = CoinDropMath.amount(base, rank, 1);
        helper.assertTrue(least > CoinDropMath.amount(base, 1, 0), "a witch's least is above a zombie's");
        for (int kill = 0; kill < KILLS; kill++) {
            long amount = coinsOf(helper, EntityType.WITCH, 10, player, true).stream().mapToLong(Coins::value).sum();
            helper.assertTrue(amount >= least && amount <= most,
                    "a level 10 witch drops " + least + " to " + most + " copper, not " + amount);
        }
        logout(player);
        helper.succeed();
    }

    /** No coins without a player's kill, from an animal, from the smallest slime, or with mob drops off. */
    @QaGameTest
    public static void onlyAPlayersKillOfAnEnemyDropsCoins(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-coins-who");
        helper.assertTrue(coinsOf(helper, EntityType.ZOMBIE, 30, player, false).isEmpty(),
                "a zombie that dies without a player's hit leaves no coins");
        helper.assertTrue(coinsOf(helper, EntityType.PIG, 30, player, true).isEmpty(), "a pig is no enemy");

        Slime small = helper.spawn(EntityType.SLIME, new BlockPos(2, 1, 2));
        small.setSize(1, true);
        MobLevels.setLevel(small, 30);
        helper.assertValueEqual(MobRanks.of(small), 0.0, "the smallest slime's rank");
        helper.assertTrue(killAndCollect(helper, small, level.damageSources().playerAttack(player)).isEmpty(),
                "the smallest slime, rank 0, leaves no coins");

        helper.assertTrue(level.getGameRules().get(GameRules.MOB_DROPS), "mob drops are on");
        level.getGameRules().set(GameRules.MOB_DROPS, false, server);
        List<ItemStack> withoutDrops;
        try {
            withoutDrops = coinsOf(helper, EntityType.ZOMBIE, 30, player, true);
        } finally {
            level.getGameRules().set(GameRules.MOB_DROPS, true, server);
        }
        helper.assertTrue(withoutDrops.isEmpty(), "no coins while the gamerule mob_drops is off");
        helper.assertFalse(coinsOf(helper, EntityType.ZOMBIE, 30, player, true).isEmpty(),
                "and coins again once it is on");
        logout(player);
        helper.succeed();
    }

    /**
     * The playtest handoff's lines, run through {@code /qa run} as the player: the six gives put the coins in the
     * inventory, and the summon, level and damage line kills a level 50 zombie with the player's kill credit, which
     * leaves about 1 gold.
     */
    @QaGameTest
    public static void thePlaytestCommandsGiveCoinsAndALevelFiftyKill(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-coins-cmd");
        player.getInventory().clearContent();
        run(server, player, "qa run give @s realmforge:copper_coin 99; give @s realmforge:copper_coin 99; "
                + "give @s realmforge:copper_coin 52; give @s realmforge:silver_coin 3; "
                + "give @s realmforge:gold_coin 2; give @s realmforge:platinum_coin 1");
        long carried = 0;
        for (ItemStack stack : player.getInventory()) {
            carried += Coins.value(stack);
        }
        helper.assertValueEqual(carried, 250L + 300 + 20_000 + 1_000_000, "the coins the gives put in the inventory");
        // Vanilla's /give shows each gift as a fake item of one for a tick: they are no drops of the zombie's.
        level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4)).forEach(ItemEntity::discard);

        run(server, player, "qa run summon minecraft:zombie ~ ~ ~1; qa level 50 @n[type=minecraft:zombie]; "
                + "damage @n[type=minecraft:zombie] 100000 minecraft:player_attack by @s");
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4));
        long dropped = drops.stream().mapToLong(drop -> Coins.value(drop.getItem())).sum();
        drops.forEach(ItemEntity::discard);
        long least = CoinDropMath.amount(curve(helper).valueAt(50), 1, 0);
        long most = CoinDropMath.amount(curve(helper).valueAt(50), 1, 1);
        helper.assertTrue(dropped >= least && dropped <= most,
                "the level 50 zombie left " + least + " to " + most + " copper, not " + dropped);
        level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(4)).forEach(Mob::discard);
        logout(player);
        helper.succeed();
    }

    /**
     * {@value #KILLS} zombies at {@code mobLevel}: every drop is between half and one and a half of {@code base}
     * (the curve's value there, which the test checks first), paid in the largest coins, and both halves of the
     * range come up.
     */
    private static void assertRange(GameTestHelper helper, ServerPlayer player, int mobLevel, double base) {
        helper.assertValueEqual(curve(helper).valueAt(mobLevel), base, "the curve at level " + mobLevel);
        long least = CoinDropMath.amount(base, 1, 0);
        long most = CoinDropMath.amount(base, 1, 1);
        boolean low = false;
        boolean high = false;
        for (int kill = 0; kill < KILLS; kill++) {
            List<ItemStack> coins = coinsOf(helper, EntityType.ZOMBIE, mobLevel, player, true);
            long amount = coins.stream().mapToLong(Coins::value).sum();
            helper.assertTrue(amount >= least && amount <= most,
                    "a level " + mobLevel + " zombie drops " + least + " to " + most + " copper, not " + amount);
            long gold = coins.stream().filter(stack -> stack.is(GeneratedItems.GOLD_COIN.get()))
                    .mapToLong(ItemStack::getCount).sum();
            helper.assertValueEqual(gold, amount / 10_000, "the gold in a drop of " + amount);
            long silver = coins.stream().filter(stack -> stack.is(GeneratedItems.SILVER_COIN.get()))
                    .mapToLong(ItemStack::getCount).sum();
            helper.assertValueEqual(silver, amount % 10_000 / 100, "the silver in a drop of " + amount);
            low |= amount < base;
            high |= amount > base;
        }
        helper.assertTrue(low && high, KILLS + " drops fall on both sides of " + base);
    }

    /**
     * Spawns a mob of {@code type} at {@code mobLevel} on the platform, kills it, by {@code player} or by nobody, and
     * returns the coins it dropped, which are removed with the rest of its loot.
     */
    private static <T extends Mob> List<ItemStack> coinsOf(GameTestHelper helper, EntityType<T> type, int mobLevel,
            ServerPlayer player, boolean byPlayer) {
        T mob = helper.spawn(type, new BlockPos(2, 1, 2));
        MobLevels.setLevel(mob, mobLevel);
        ServerLevel level = helper.getLevel();
        return killAndCollect(helper, mob, byPlayer ? level.damageSources().playerAttack(player)
                : level.damageSources().generic());
    }

    private static List<ItemStack> killAndCollect(GameTestHelper helper, Mob mob, DamageSource source) {
        ServerLevel level = helper.getLevel();
        mob.hurtServer(level, source, 1_000_000);
        helper.assertTrue(mob.isDeadOrDying(), mob.getType() + " died");
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(3));
        List<ItemStack> coins = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(Coins.TAG))
                .map(ItemStack::copy).toList();
        drops.forEach(ItemEntity::discard);
        mob.discard();
        return coins;
    }

    private static Curve curve(GameTestHelper helper) {
        return helper.getLevel().registryAccess().lookupOrThrow(CoreRegistries.CURVE)
                .getOrThrow(GeneratedCurves.COIN_DROP).value();
    }

    private static void run(MinecraftServer server, ServerPlayer player, String command) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
