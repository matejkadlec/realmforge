package io.github.matejkadlec.realmforge.qa.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.loot.BudgetEntry;
import io.github.matejkadlec.realmforge.loot.LootContainers;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedContainerLootExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedContainerLootExpectations.ContainerLootExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedContainerLootExpectations.Tier;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-289 (ADR 0041, architecture 13): container loot by loot points in the game. The generated tables' budgets match
 * the pipeline's; a roll's points follow the opener's world tier and luck; a roll never overspends and a limited
 * option comes once; a tier's options are its own; money comes in the largest coins; a gem and an affix item are
 * rolled by Apotheosis for the opener; the two real tables fill a container at every world tier, in the instance
 * dimension too; and real containers, placed as the builder places them and opened through Lootr, roll for their
 * opener's world tier and luck. The other tables are rolled as Lootr rolls them: the opener as {@code THIS_ENTITY},
 * with their luck. The world tier is set with Apotheosis's own command, so no class of Apotheosis is named here.
 */
public final class ContainerLootGameTests {
    private static final List<String> TIERS = BudgetEntry.TIERS;
    private static final int ROLLS = 40;
    /** The slots of a chest or a barrel. */
    private static final int SLOTS = 27;

    private ContainerLootGameTests() {
    }

    /** The budget entry the game decoded from each generated table has the pipeline's points and options per tier. */
    @QaGameTest
    public static void theTablesBudgetsMatchThePipeline(GameTestHelper helper) {
        helper.assertFalse(GeneratedContainerLootExpectations.TABLES.isEmpty(), "no container loot was generated");
        for (ContainerLootExpectation expected : GeneratedContainerLootExpectations.TABLES) {
            BudgetEntry entry = budgetEntry(helper, Identifier.parse(expected.lootTable()));
            helper.assertValueEqual(expected.tiers().stream().map(Tier::name).toList(), TIERS, "the world tiers");
            for (int tier = 0; tier < TIERS.size(); tier++) {
                Tier wanted = expected.tiers().get(tier);
                String where = expected.id() + " at " + wanted.name();
                helper.assertValueEqual(entry.points(0, tier, 0), wanted.minPoints(), "the least points of " + where);
                helper.assertValueEqual(entry.points(1, tier, 0), wanted.maxPoints(), "the most points of " + where);
                List<BudgetEntry.Option> options = entry.optionsAt(tier);
                helper.assertValueEqual(options.size(), wanted.options(), "the options of " + where);
                helper.assertValueEqual(options.stream().mapToInt(BudgetEntry.Option::cost).min().orElse(0),
                        wanted.cheapest(), "the cheapest option of " + where);
            }
            // Luck II: two points at the table's percent each.
            helper.assertValueEqual(entry.points(0, 0, 2), (int) Math.round(expected.tiers().getFirst().minPoints()
                    * (1 + 2 * expected.luckPercent() / 100)), "the least points of " + expected.id() + " with luck 2");
        }
        helper.succeed();
    }

    /**
     * A roll's points at two world tiers and with luck, rolled as Lootr rolls a table. The test table gives 1,000
     * points of diamonds at 100 each, half more at Frontier and a tenth more per point of luck; equal stacks join.
     * {@link #aPlacedContainersRollFollowsItsOpenersWorldTierAndLuck} opens real containers for the same numbers.
     */
    @QaGameTest
    public static void aRollsPointsFollowTheOpenersWorldTierAndLuck(GameTestHelper helper) {
        withPlayer(helper, "qa-loot-tier", player -> {
            ResourceKey<LootTable> diamonds = table("test/budget_diamonds");
            setTier(player, "haven");
            List<ItemStack> haven = roll(helper, player, diamonds, 1);
            helper.assertValueEqual(haven.size(), 1, "ten diamonds are one stack");
            helper.assertValueEqual(count(haven, Items.DIAMOND), 10, "diamonds at Haven");

            setTier(player, "frontier");
            helper.assertValueEqual(count(roll(helper, player, diamonds, 2), Items.DIAMOND), 15,
                    "diamonds at Frontier");

            // Apotheosis's own augment of the tier raises a player's luck by a fifth at Frontier: 5 points are 6.
            player.getAttribute(Attributes.LUCK).setBaseValue(5);
            helper.assertValueEqual(player.getLuck(), 6.0F, "the luck of 5 at Frontier");
            helper.assertValueEqual(count(roll(helper, player, diamonds, 3), Items.DIAMOND), 24,
                    "diamonds at Frontier with luck 6: 2,400 points");
            setTier(player, "haven");
            helper.assertValueEqual(count(roll(helper, player, diamonds, 4), Items.DIAMOND), 15,
                    "diamonds at Haven with luck 5");
            player.getAttribute(Attributes.LUCK).setBaseValue(-20);
            helper.assertValueEqual(roll(helper, player, diamonds, 5).size(), 0,
                    "bad luck never gives negative points");
        });
    }

    /** A roll never spends more than its budget, spends all it can, and a limited option comes at most once. */
    @QaGameTest
    public static void aRollNeverOverspendsAndALimitedOptionComesOnce(GameTestHelper helper) {
        withPlayer(helper, "qa-loot-budget", player -> {
            setTier(player, "haven");
            int emeralds = 0;
            for (int seed = 1; seed <= ROLLS; seed++) {
                List<ItemStack> loot = roll(helper, player, table("test/budget_unique"), seed);
                int unique = count(loot, Items.EMERALD);
                helper.assertTrue(unique <= 1, "roll " + seed + " holds the unique emerald " + unique + " times");
                helper.assertValueEqual(unique * 400 + count(loot, Items.STICK) * 100, 1000,
                        "the points roll " + seed + " spent of its 1,000");
                emeralds += unique;
            }
            helper.assertTrue(emeralds > ROLLS / 2, "the emerald, of weight 1,000 to 1, came " + emeralds + " times");
        });
    }

    /** A world tier's options are its own: iron at Haven, gold at Frontier. */
    @QaGameTest
    public static void aTiersOptionsAreItsOwn(GameTestHelper helper) {
        withPlayer(helper, "qa-loot-options", player -> {
            setTier(player, "haven");
            List<ItemStack> haven = roll(helper, player, table("test/budget_tiers"), 1);
            helper.assertValueEqual(count(haven, Items.IRON_INGOT), 5, "iron at Haven");
            helper.assertValueEqual(count(haven, Items.GOLD_INGOT), 0, "gold at Haven");
            setTier(player, "frontier");
            List<ItemStack> frontier = roll(helper, player, table("test/budget_tiers"), 1);
            helper.assertValueEqual(count(frontier, Items.GOLD_INGOT), 5, "gold at Frontier");
            helper.assertValueEqual(count(frontier, Items.IRON_INGOT), 0, "iron at Frontier");
            // A tier without an option of its own gets nothing from this table.
            setTier(player, "ascent");
            helper.assertValueEqual(roll(helper, player, table("test/budget_tiers"), 1).size(), 0, "stacks at Ascent");
        });
    }

    /** {@code realmforge:money}: 250 of the smallest coin are 2 Silver Coins and 50 Copper Coins. */
    @QaGameTest
    public static void moneyComesInTheLargestCoins(GameTestHelper helper) {
        withPlayer(helper, "qa-loot-money", player -> {
            List<ItemStack> loot = roll(helper, player, table("test/money"), 1);
            helper.assertValueEqual(count(loot, GeneratedItems.SILVER_COIN.get()), 2, "Silver Coins");
            helper.assertValueEqual(count(loot, GeneratedItems.COPPER_COIN.get()), 50, "Copper Coins");
            helper.assertValueEqual(loot.size(), 2, "stacks");
        });
    }

    /** {@code realmforge:tier_gem} and {@code realmforge:tier_affix_item}: Apotheosis rolls both for the opener. */
    @QaGameTest
    public static void aGemAndAnAffixItemAreRolledForTheOpener(GameTestHelper helper) {
        withPlayer(helper, "qa-loot-apoth", player -> {
            for (String tier : List.of("haven", "pinnacle")) {
                setTier(player, tier);
                for (int seed = 1; seed <= 10; seed++) {
                    List<ItemStack> loot = roll(helper, player, table("test/tier_loot"), seed);
                    helper.assertValueEqual(loot.size(), 2, "a gem and an affix item at " + tier + ", roll " + seed);
                    List<String> ids = loot.stream().map(ContainerLootGameTests::id).toList();
                    helper.assertValueEqual(ids.stream().filter("apotheosis:gem"::equals).count(), 1L,
                            "gems at " + tier + ", roll " + seed + ": " + ids);
                    // An affix item is a piece of gear, one of a kind.
                    helper.assertTrue(loot.stream().anyMatch(stack -> !id(stack).equals("apotheosis:gem")
                            && stack.getMaxStackSize() == 1), "an affix item at " + tier + ", roll " + seed);
                }
            }
        });
    }

    /** The normal and the treasure table hold something at every world tier, and never more than a chest takes. */
    @QaGameTest(timeoutTicks = 400)
    public static void theRealTablesFillAContainerAtEveryTier(GameTestHelper helper) {
        withPlayer(helper, "qa-loot-real", player -> {
            for (ContainerLootExpectation expected : GeneratedContainerLootExpectations.TABLES) {
                ResourceKey<LootTable> table =
                        ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(expected.lootTable()));
                boolean coins = false;
                for (String tier : TIERS) {
                    setTier(player, tier);
                    for (int seed = 1; seed <= ROLLS; seed++) {
                        List<ItemStack> loot = roll(helper, player, table, seed);
                        String where = expected.id() + " at " + tier + ", roll " + seed;
                        helper.assertFalse(loot.isEmpty(), "an empty container: " + where);
                        helper.assertTrue(loot.size() <= SLOTS, loot.size() + " stacks do not fit a chest: " + where);
                        helper.assertTrue(loot.stream().noneMatch(ItemStack::isEmpty), "an empty stack: " + where);
                        coins |= count(loot, GeneratedItems.COPPER_COIN.get())
                                + count(loot, GeneratedItems.SILVER_COIN.get())
                                + count(loot, GeneratedItems.GOLD_COIN.get()) > 0;
                    }
                }
                helper.assertTrue(coins, expected.id() + " never held a coin");
            }
        });
    }

    /**
     * The ticket's "Done when", the whole way: containers the builder places ({@link LootContainers#place}, Lootr's
     * chests) opened at two world tiers and with luck. Lootr hands our budget entry the opener and their luck
     * ({@code THIS_ENTITY} and the luck of its loot parameters), or every container would roll as for a Haven player
     * without luck: 10 diamonds whoever opens it. Each check opens a chest of its own, since a player's contents of
     * one chest are rolled once.
     */
    @QaGameTest
    public static void aPlacedContainersRollFollowsItsOpenersWorldTierAndLuck(GameTestHelper helper) {
        withPlayer(helper, "qa-loot-chest", new Vec3(2.5, 1, 1.5), player -> {
            ResourceKey<LootTable> diamonds = table("test/budget_diamonds");
            setTier(player, "haven");
            helper.assertValueEqual(open(helper, player, new BlockPos(1, 1, 3), diamonds).countItem(Items.DIAMOND),
                    10, "diamonds in a chest opened at Haven");
            setTier(player, "frontier");
            helper.assertValueEqual(open(helper, player, new BlockPos(2, 1, 3), diamonds).countItem(Items.DIAMOND),
                    15, "diamonds in a chest opened at Frontier");
            // Apotheosis's Frontier augment makes 5 points of luck 6: 1,000 x 1.5 x 1.6 points.
            player.getAttribute(Attributes.LUCK).setBaseValue(5);
            helper.assertValueEqual(open(helper, player, new BlockPos(3, 1, 3), diamonds).countItem(Items.DIAMOND),
                    24, "diamonds in a chest opened at Frontier with luck");

            ContainerLootExpectation normal = GeneratedContainerLootExpectations.TABLES.stream()
                    .filter(expected -> expected.id().equals("realmforge:normal")).findFirst().orElseThrow();
            helper.assertFalse(open(helper, player, new BlockPos(2, 1, 4),
                    ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(normal.lootTable()))).isEmpty(),
                    "a chest with the normal table is empty");
        });
    }

    /**
     * The containers will stand in dungeons: in the instance dimension both real tables still fill a container, and
     * Apotheosis still answers there with gems and affix items (its gems of the Overworld, the Nether and the End are
     * bound to their dimensions; its core gems and its affix loot entries are not).
     */
    @QaGameTest(timeoutTicks = 400)
    public static void theTablesHoldLootInTheInstanceDimensionToo(GameTestHelper helper) {
        withPlayer(helper, "qa-loot-instance", player -> {
            ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
            player.teleportTo(instances, 0.5, 100, 0.5, Set.of(), 0.0F, 0.0F, true);
            helper.assertTrue(player.level() == instances, "the player is in the instance dimension");
            for (String tier : List.of("haven", "frontier")) {
                setTier(player, tier);
                for (int seed = 1; seed <= 10; seed++) {
                    List<String> ids = roll(instances, helper, player, table("test/tier_loot"), seed).stream()
                            .map(ContainerLootGameTests::id).toList();
                    helper.assertValueEqual(ids.size(), 2, "a gem and an affix item at " + tier + ": " + ids);
                    helper.assertValueEqual(ids.stream().filter("apotheosis:gem"::equals).count(), 1L,
                            "gems at " + tier + ": " + ids);
                }
                for (ContainerLootExpectation expected : GeneratedContainerLootExpectations.TABLES) {
                    ResourceKey<LootTable> table =
                            ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(expected.lootTable()));
                    for (int seed = 1; seed <= ROLLS; seed++) {
                        helper.assertFalse(roll(instances, helper, player, table, seed).isEmpty(),
                                "an empty container: " + expected.id() + " at " + tier + ", roll " + seed);
                    }
                }
            }
        });
    }

    private static ResourceKey<LootTable> table(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, RealmforgeQa.id(path));
    }

    /**
     * Places a chest with {@code table} at {@code relative} as the builder does, right-clicks it as {@code player} and
     * returns the contents its menu shows them: Lootr's roll for that player.
     */
    private static Container open(GameTestHelper helper, ServerPlayer player, BlockPos relative,
            ResourceKey<LootTable> table) {
        BlockPos pos = helper.absolutePos(relative);
        helper.assertTrue(LootContainers.place(helper.getLevel(), pos, Blocks.CHEST.defaultBlockState(), table, 0L),
                "the chest takes the table");
        helper.assertTrue(LootContainers.isPerPlayer(helper.getLevel().getBlockState(pos)), "the chest is Lootr's");
        player.closeContainer();
        player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        helper.assertTrue(player.containerMenu instanceof ChestMenu, "the chest does not open");
        return ((ChestMenu) player.containerMenu).getContainer();
    }

    /** Rolls a loot table for {@code player} as Lootr does: the opener as the roll's entity, with their luck. */
    private static List<ItemStack> roll(GameTestHelper helper, ServerPlayer player, ResourceKey<LootTable> table,
            long seed) {
        return roll(helper.getLevel(), helper, player, table, seed);
    }

    private static List<ItemStack> roll(ServerLevel level, GameTestHelper helper, ServerPlayer player,
            ResourceKey<LootTable> table, long seed) {
        LootTable loot = level.getServer().reloadableRegistries().getLootTable(table);
        helper.assertTrue(loot != LootTable.EMPTY, "the loot table " + table.identifier() + " is not loaded");
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);
        return loot.getRandomItems(params, seed);
    }

    /** The budget entry of a generated table, decoded from its file as the game decodes it. */
    private static BudgetEntry budgetEntry(GameTestHelper helper, Identifier table) {
        MinecraftServer server = helper.getLevel().getServer();
        Identifier file =
                Identifier.fromNamespaceAndPath(table.getNamespace(), "loot_table/" + table.getPath() + ".json");
        JsonElement entry;
        try (Reader reader = server.getResourceManager().openAsReader(file)) {
            entry = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("pools").get(0).getAsJsonObject()
                    .getAsJsonArray("entries").get(0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
        LootPoolEntryContainer decoded = LootPoolEntries.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, server.reloadableRegistries().lookup()), entry)
                .getOrThrow(message -> new IllegalStateException(file + ": " + message));
        helper.assertTrue(decoded instanceof BudgetEntry, table + " holds no realmforge:budget entry");
        return (BudgetEntry) decoded;
    }

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Sets the player's world tier with Apotheosis's own command. */
    private static void setTier(ServerPlayer player, String tier) {
        MinecraftServer server = player.level().getServer();
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS)
                .withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, "apoth set_world_tier @s " + tier);
    }

    /**
     * Logs a player in on the test's platform, runs {@code test} with it and, whatever happens, puts it back at Haven
     * without luck and logs it out: Apotheosis augments every mob that spawns by the world tier of the level's nearest
     * player, however far away ({@code ApothMobEvents#finalizeMobSpawns}), so a player left at a later tier would
     * change the mobs of every other test.
     */
    private static void withPlayer(GameTestHelper helper, String name, Vec3 at, Consumer<ServerPlayer> test) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, at);
        try {
            test.accept(player);
        } finally {
            setTier(player, "haven");
            player.getAttribute(Attributes.LUCK).setBaseValue(0);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static void withPlayer(GameTestHelper helper, String name, Consumer<ServerPlayer> test) {
        withPlayer(helper, name, QaPlayers.PLATFORM_MIDDLE, test);
    }
}
