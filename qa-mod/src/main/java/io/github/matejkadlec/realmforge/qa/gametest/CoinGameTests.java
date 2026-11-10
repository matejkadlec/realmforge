package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.core.CoreRegistries;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.generated.GeneratedCoins.Coin;
import io.github.matejkadlec.realmforge.generated.GeneratedCurves;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.item.CoinMath;
import io.github.matejkadlec.realmforge.item.Coins;
import io.github.matejkadlec.realmforge.loot.CoinDropMath;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedCoinExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedCoinExpectations.DropSample;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedCoinExpectations.SplitSample;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The coins and their converter (RLM-284): the game's chain of coins, its split of an amount and a mob's drop agree
 * with the pipeline ({@link GeneratedCoinExpectations}); using a coin merges every full hundred into the next coin and
 * sneaking while using it breaks one, both ways along the whole chain, through vanilla's right-click path; with too
 * few nothing happens; what does not fit a full inventory lies at the player's feet, in survival and in creative; and
 * a conversion pauses the coin for half a second.
 */
public final class CoinGameTests {
    private CoinGameTests() {
    }

    @QaGameTest
    public static void theCoinsAndTheirMathAgreeWithThePipeline(GameTestHelper helper) {
        List<Coin> chain = Coins.chain();
        helper.assertValueEqual(chain.size(), GeneratedCoinExpectations.COINS.size(), "the number of coins");
        helper.assertValueEqual(chain.size(), 4, "copper, silver, gold and platinum");
        for (int index = 0; index < chain.size(); index++) {
            Coin coin = chain.get(index);
            Item item = coin.item().get();
            helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(item).toString(),
                    GeneratedCoinExpectations.COINS.get(index).id(), "coin " + index);
            helper.assertValueEqual(coin.value(), GeneratedCoinExpectations.COINS.get(index).value(),
                    coin.item().getId() + "'s value");
            helper.assertTrue(new ItemStack(item).is(Coins.TAG), coin.item().getId() + " is in " + Coins.TAG);
            helper.assertValueEqual(item.getDefaultMaxStackSize(), 99, coin.item().getId() + " stacks to 99");
            helper.assertValueEqual(Coins.indexOf(item), index, coin.item().getId() + "'s place in the chain");
            helper.assertValueEqual(Coins.value(new ItemStack(item, 3)), 3 * coin.value(), "the worth of three");
            if (index > 0) {
                helper.assertValueEqual(CoinMath.rate(chain.get(index - 1).value(), coin.value()), 100L,
                        coin.item().getId() + " is 100 of the coin below");
            }
        }
        helper.assertValueEqual(Coins.indexOf(Items.GOLD_INGOT), -1, "a gold ingot is no coin");
        helper.assertValueEqual(Coins.value(new ItemStack(Items.GOLD_INGOT, 5)), 0L, "and worth nothing");

        long[] values = chain.stream().mapToLong(Coin::value).toArray();
        for (SplitSample sample : GeneratedCoinExpectations.SPLITS) {
            helper.assertValueEqual(Arrays.stream(CoinMath.split(sample.amount(), values)).boxed().toList(),
                    sample.counts(), "the coins that pay " + sample.amount());
            long worth = Coins.stacks(sample.amount()).stream().mapToLong(Coins::value).sum();
            helper.assertValueEqual(worth, sample.amount(), "the stacks that pay " + sample.amount());
        }
        List<ItemStack> platinum = Coins.stacks(150_000_000);
        helper.assertValueEqual(platinum.stream().map(ItemStack::getCount).toList(), List.of(99, 51),
                "150 platinum are two stacks");

        Curve curve = helper.getLevel().registryAccess().lookupOrThrow(CoreRegistries.CURVE)
                .getOrThrow(GeneratedCurves.COIN_DROP).value();
        helper.assertFalse(GeneratedCoinExpectations.DROPS.isEmpty(), "no coin drop samples were generated");
        for (DropSample sample : GeneratedCoinExpectations.DROPS) {
            helper.assertValueEqual(CoinDropMath.amount(curve.valueAt(sample.level()), sample.rank(), sample.roll()),
                    sample.amount(), "the drop at level " + sample.level() + ", rank " + sample.rank() + ", roll "
                            + sample.roll());
        }
        helper.succeed();
    }

    /** 250 copper in three stacks: a use makes 2 silver and leaves 50; a second use, after the pause, is refused. */
    @QaGameTest
    public static void usingACoinMergesEveryFullHundred(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, "qa-coin-merge");
        Inventory inventory = player.getInventory();
        Item copper = GeneratedItems.COPPER_COIN.get();
        Item silver = GeneratedItems.SILVER_COIN.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(copper, 52));
        inventory.setItem(9, new ItemStack(copper, 99));
        inventory.setItem(20, new ItemStack(copper, 99));
        InteractionResult used = use(player);
        helper.assertTrue(used instanceof InteractionResult.Success, "the merge succeeds: " + used);
        helper.assertValueEqual(count(player, copper), 50L, "50 copper are left");
        helper.assertValueEqual(count(player, silver), 2L, "and 2 silver made");
        helper.assertTrue(player.getCooldowns().isOnCooldown(new ItemStack(copper)), "copper pauses");
        helper.assertTrue(drops(helper, player).isEmpty(), "nothing lies on the ground");

        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(copper), "the hand still holds copper");
        InteractionResult paused = use(player);
        helper.assertTrue(paused instanceof InteractionResult.Pass, "during the pause a use passes: " + paused);
        ready(player);
        QaPlayers.sentOverlayLines(player);
        InteractionResult tooFew = use(player);
        helper.assertTrue(tooFew instanceof InteractionResult.Fail, "50 copper do not merge: " + tooFew);
        helper.assertValueEqual(count(player, copper), 50L, "the 50 copper stay");
        helper.assertValueEqual(count(player, silver), 2L, "and no silver is made");
        List<Component> lines = QaPlayers.sentOverlayLines(player);
        helper.assertValueEqual(lines.size(), 1, "one action bar line says why");
        helper.assertTrue(lines.getFirst().getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(GeneratedTexts.MESSAGE_COIN_MERGE_TOO_FEW)
                && contents.getArgs()[0].equals(100L), "it names the 100 it takes: " + lines.getFirst());
        logout(player);
        helper.succeed();
    }

    /** Three silver in hand: sneaking and using breaks one into 100 copper, a stack of 99 and one more. */
    @QaGameTest
    public static void sneakingAndUsingACoinBreaksOne(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, "qa-coin-break");
        Item copper = GeneratedItems.COPPER_COIN.get();
        Item silver = GeneratedItems.SILVER_COIN.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(silver, 3));
        player.setShiftKeyDown(true);
        InteractionResult used = use(player);
        helper.assertTrue(used instanceof InteractionResult.Success, "the break succeeds: " + used);
        helper.assertValueEqual(count(player, silver), 2L, "one silver is gone");
        helper.assertValueEqual(count(player, copper), 100L, "for 100 copper");
        helper.assertTrue(player.getCooldowns().isOnCooldown(new ItemStack(silver)), "silver pauses");
        helper.assertTrue(drops(helper, player).isEmpty(), "nothing lies on the ground");
        logout(player);
        helper.succeed();
    }

    /** Along the whole chain: one of each larger coin breaks into 100 of the smaller, which merge back into the one. */
    @QaGameTest
    public static void everyCoinBreaksAndMergesBack(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, "qa-coin-chain");
        List<Coin> chain = Coins.chain();
        for (int index = 1; index < chain.size(); index++) {
            Item larger = chain.get(index).item().get();
            Item smaller = chain.get(index - 1).item().get();
            player.getInventory().clearContent();
            ready(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(larger));
            player.setShiftKeyDown(true);
            helper.assertTrue(use(player) instanceof InteractionResult.Success, larger + " breaks");
            helper.assertValueEqual(count(player, larger), 0L, "the one " + larger + " is gone");
            helper.assertValueEqual(count(player, smaller), 100L, "for 100 " + smaller);

            player.setShiftKeyDown(false);
            ready(player);
            ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
            helper.assertTrue(held.is(smaller), "the freed hand holds " + smaller + ": " + held);
            helper.assertTrue(use(player) instanceof InteractionResult.Success, "100 " + smaller + " merge");
            helper.assertValueEqual(count(player, smaller), 0L, "the 100 " + smaller + " are gone");
            helper.assertValueEqual(count(player, larger), 1L, "for one " + larger);
        }
        // The ends of the chain: the smallest coin does not break and the largest does not merge.
        player.getInventory().clearContent();
        ready(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(chain.getFirst().item().get(), 5));
        player.setShiftKeyDown(true);
        helper.assertTrue(use(player) instanceof InteractionResult.Pass, "copper does not break");
        helper.assertValueEqual(count(player, chain.getFirst().item().get()), 5L, "and stays");
        player.setShiftKeyDown(false);
        Item largest = chain.getLast().item().get();
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(largest, 99));
        player.getInventory().setItem(9, new ItemStack(largest, 99));
        helper.assertTrue(use(player) instanceof InteractionResult.Pass, "platinum does not merge");
        helper.assertValueEqual(count(player, largest), 198L, "and stays");
        logout(player);
        helper.succeed();
    }

    /**
     * A full inventory: two silver in hand and stone everywhere else, so a break's 100 copper fit nowhere and lie at
     * the player's feet, still, not thrown. In survival and in creative, where vanilla's own give would void them.
     * Merging in a full inventory needs no room: the 100 it takes free more than the one it makes needs.
     */
    @QaGameTest
    public static void changeThatDoesNotFitDropsAtThePlayersFeet(GameTestHelper helper) {
        Item copper = GeneratedItems.COPPER_COIN.get();
        Item silver = GeneratedItems.SILVER_COIN.get();
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
            ServerPlayer player = survivor(helper, "qa-coin-full-" + mode.getName());
            player.setGameMode(mode);
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                inventory.setItem(slot, new ItemStack(Items.STONE, 64));
            }
            inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.STONE, 64));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(silver, 2));
            player.setShiftKeyDown(true);
            helper.assertTrue(use(player) instanceof InteractionResult.Success, "the break succeeds in " + mode);
            helper.assertValueEqual(count(player, silver), 1L, "one silver is left in " + mode);
            helper.assertValueEqual(count(player, copper), 0L, "no copper fits in " + mode);
            List<ItemEntity> dropped = drops(helper, player);
            helper.assertValueEqual(dropped.stream().mapToLong(drop -> Coins.value(drop.getItem())).sum(), 100L,
                    "100 copper lie on the ground in " + mode);
            for (ItemEntity drop : dropped) {
                helper.assertTrue(drop.getItem().is(copper), "only copper dropped: " + drop.getItem());
                helper.assertValueEqual(drop.getDeltaMovement(), Vec3.ZERO, "a plain drop, not a throw");
                helper.assertTrue(drop.distanceToSqr(player) < 4, "at the player's feet");
            }
            dropped.forEach(ItemEntity::discard);

            ready(player);
            // 99 copper in hand and 1 more in the full inventory merge into one silver, which joins the one there:
            // the 100 it takes free more room than the one it makes needs.
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(copper, 99));
            inventory.setItem(9, new ItemStack(copper, 1));
            inventory.setItem(10, new ItemStack(silver, 1));
            helper.assertTrue(use(player) instanceof InteractionResult.Success, "the merge succeeds in " + mode);
            helper.assertValueEqual(count(player, copper), 0L, "the 100 copper are gone in " + mode);
            helper.assertValueEqual(count(player, silver), 2L, "for a second silver in " + mode);
            helper.assertTrue(drops(helper, player).isEmpty(), "and nothing drops in " + mode);
            logout(player);
        }
        helper.succeed();
    }

    /** A new player in survival on the test's platform, with an empty inventory. */
    private static ServerPlayer survivor(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    /** Uses the main hand's item through vanilla's right-click path, as a real client's use does. */
    private static InteractionResult use(ServerPlayer player) {
        return player.gameMode.useItem(player, player.level(), player.getItemInHand(InteractionHand.MAIN_HAND),
                InteractionHand.MAIN_HAND);
    }

    private static long count(ServerPlayer player, Item item) {
        long count = 0;
        for (ItemStack stack : player.getInventory()) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** The item entities within four blocks of the player. */
    private static List<ItemEntity> drops(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4));
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    /** Ends the pause a conversion put on any coin, so a test's next step need not wait half a second. */
    private static void ready(ServerPlayer player) {
        Coins.chain().forEach(coin -> player.getCooldowns()
                .removeCooldown(BuiltInRegistries.ITEM.getKey(coin.item().get())));
    }
}
