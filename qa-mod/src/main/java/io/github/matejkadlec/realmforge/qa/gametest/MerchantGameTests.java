package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.mob.merchant.Merchants;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMerchantExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMerchantExpectations.MerchantExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMerchantExpectations.Stack;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMerchantExpectations.TradeExpectation;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.ResetProfession;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

/**
 * Merchants (RLM-285, RLM-241): every merchant of {@code content/merchants/} is a villager profession with its
 * workstation as its job site and its trades at the written prices and in the written order
 * ({@link GeneratedMerchantExpectations}); the prices stay fixed with reputation and Hero of the Village, where a
 * vanilla villager's fall; the trade screen is told no level; a player buys every trade of every merchant for its
 * coins; the first trade locks the profession without levelling the villager; and a jobless villager takes the job at
 * a workstation.
 */
public final class MerchantGameTests {
    private static final BlockPos VILLAGER = new BlockPos(1, 1, 1);

    private MerchantGameTests() {
    }

    @QaGameTest
    public static void merchantsAreProfessionsWithTheirWorkstations(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertFalse(GeneratedMerchantExpectations.MERCHANTS.isEmpty(), "no merchant expectations were generated");
        for (MerchantExpectation expected : GeneratedMerchantExpectations.MERCHANTS) {
            Identifier id = Identifier.parse(expected.id());
            String name = id.getPath();
            Holder<VillagerProfession> profession = BuiltInRegistries.VILLAGER_PROFESSION
                    .get(Merchants.professionKey(name)).orElse(null);
            helper.assertTrue(profession != null, expected.id() + " is a villager profession");
            helper.assertTrue(profession.is(Merchants.FIXED_PRICE), expected.id() + " trades at fixed prices");
            helper.assertValueEqual(profession.value().getTrades(1), Merchants.tradeSet(name),
                    expected.id() + "'s trade set at level 1");
            helper.assertTrue(profession.value().getTrades(2) == null, expected.id() + " has no trades at level 2");

            Block workstation = BuiltInRegistries.BLOCK.getValue(Identifier.parse(expected.workstation()));
            Optional<Holder<PoiType>> poi = PoiTypes.forState(workstation.defaultBlockState());
            helper.assertTrue(poi.isPresent() && poi.get().is(Merchants.poiKey(name)),
                    expected.workstation() + " is the job site of " + expected.id());
            helper.assertTrue(poi.get().is(PoiTypeTags.ACQUIRABLE_JOB_SITE), "a jobless villager may take it");
            helper.assertTrue(profession.value().acquirableJobSite().test(poi.get()), "this profession takes it");
            helper.assertTrue(profession.value().heldJobSite().test(poi.get()), "and keeps it");

            TradeSet trades = level.registryAccess().lookupOrThrow(Registries.TRADE_SET)
                    .getValue(ResourceKey.create(Registries.TRADE_SET, Identifier.parse(expected.tradeSet())));
            helper.assertTrue(trades != null, expected.tradeSet() + " is loaded");
            helper.assertValueEqual(trades.getTrades().size(), expected.trades().size(),
                    "the trades of " + expected.tradeSet());
            helper.assertFalse(trades.allowDuplicates(), "no trade is offered twice");
        }
        helper.succeed();
    }

    /** A merchant offers every trade, in the written order once a player opens it, at the written costs. */
    @QaGameTest
    public static void aMerchantOffersItsTradesAtTheWrittenPrices(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "qa-merchant-list");
        for (MerchantExpectation expected : GeneratedMerchantExpectations.MERCHANTS) {
            Villager villager = merchant(helper, expected);
            helper.assertValueEqual(villager.getOffers().size(), expected.trades().size(),
                    "the offers of " + expected.id());
            open(helper, player, villager);
            MerchantOffers offers = villager.getOffers();
            for (int index = 0; index < expected.trades().size(); index++) {
                TradeExpectation trade = expected.trades().get(index);
                MerchantOffer offer = offers.get(index);
                String what = expected.id() + " trade " + index;
                assertStack(helper, offer.getResult(), trade.gives(), what + " gives");
                assertStack(helper, offer.getCostA(), trade.costs().getFirst(), what + " wants");
                if (trade.costs().size() > 1) {
                    assertStack(helper, offer.getCostB(), trade.costs().get(1), what + " also wants");
                } else {
                    helper.assertTrue(offer.getCostB().isEmpty(), what + " has one cost");
                }
                helper.assertValueEqual(offer.getMaxUses(), trade.maxUses(), what + " max uses");
                helper.assertValueEqual(offer.getXp(), 0, what + " gives the villager no XP");
                helper.assertValueEqual(offer.getPriceMultiplier(), 0.0F, what + " has no reputation discount");
            }
            player.closeContainer();
            villager.discard();
        }
        logout(player);
        helper.succeed();
    }

    /**
     * Hero of the Village V and the best reputation: a vanilla armorer's prices fall, a merchant's stay as written,
     * and the trade screen is told level 0, which draws neither a level name nor an XP bar.
     */
    @QaGameTest
    public static void pricesStayFixedWithReputationAndHeroOfTheVillage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = customer(helper, "qa-merchant-hero");
        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 6000, 4));

        Villager armorer = helper.spawn(EntityType.VILLAGER, VILLAGER);
        armorer.setVillagerData(armorer.getVillagerData().withProfession(level.registryAccess(),
                VillagerProfession.ARMORER));
        armorer.getGossips().add(player.getUUID(), GossipType.MAJOR_POSITIVE, 100);
        helper.assertFalse(armorer.getOffers().isEmpty(), "the armorer has offers");
        QaPlayers.sentPackets(player, ClientboundMerchantOffersPacket.class);
        open(helper, player, armorer);
        helper.assertTrue(armorer.getOffers().stream().allMatch(offer -> offer.getSpecialPriceDiff() < 0),
                "Hero of the Village lowers every vanilla price");
        List<ClientboundMerchantOffersPacket> vanilla = QaPlayers.sentPackets(player,
                ClientboundMerchantOffersPacket.class);
        helper.assertValueEqual(vanilla.size(), 1, "the armorer's offers were sent");
        helper.assertValueEqual(vanilla.getFirst().getVillagerLevel(), 1, "a vanilla villager shows its level");
        player.closeContainer();
        armorer.discard();

        for (MerchantExpectation expected : GeneratedMerchantExpectations.MERCHANTS) {
            Villager villager = merchant(helper, expected);
            villager.getGossips().add(player.getUUID(), GossipType.MAJOR_POSITIVE, 100);
            helper.assertTrue(villager.getPlayerReputation(player) > 0, "the player's reputation is high");
            open(helper, player, villager);
            helper.assertTrue(villager.getTradingPlayer() == player, "the merchant trades with the player");
            MerchantOffers offers = villager.getOffers();
            for (int index = 0; index < expected.trades().size(); index++) {
                MerchantOffer offer = offers.get(index);
                String what = expected.id() + " trade " + index;
                helper.assertValueEqual(offer.getSpecialPriceDiff(), 0, what + " has no special price");
                assertStack(helper, offer.getCostA(), expected.trades().get(index).costs().getFirst(),
                        what + " still wants");
            }
            List<ClientboundMerchantOffersPacket> sent = QaPlayers.sentPackets(player,
                    ClientboundMerchantOffersPacket.class);
            helper.assertValueEqual(sent.size(), 1, "the merchant's offers were sent once");
            helper.assertValueEqual(sent.getFirst().getVillagerLevel(), Merchants.SHOWN_LEVEL,
                    "the trade screen is told no level");
            helper.assertValueEqual(sent.getFirst().getOffers().size(), expected.trades().size(), "with every offer");
            player.closeContainer();
            helper.assertTrue(villager.getTradingPlayer() == null, "closing the screen ends the trading");
            villager.discard();
        }
        logout(player);
        helper.succeed();
    }

    /**
     * A player buys every trade of every merchant once, through the trade screen's own slots: the costs go, the goods
     * come, the offer counts one use, and the villager stays at level 1 with the one point of XP that locks it.
     */
    @QaGameTest
    public static void aPlayerBuysEveryTrade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = customer(helper, "qa-merchant-buy");
        for (MerchantExpectation expected : GeneratedMerchantExpectations.MERCHANTS) {
            Villager villager = merchant(helper, expected);
            helper.assertValueEqual(villager.getVillagerXp(), 0, "a merchant that never traded has no XP");
            Villager untraded = merchant(helper, expected, true);
            helper.assertTrue(ResetProfession.create().tryStart(level, untraded, level.getGameTime()),
                    "an untraded merchant without a job site loses its profession, as vanilla's");
            helper.assertTrue(untraded.getVillagerData().profession().is(VillagerProfession.NONE), "it is jobless");
            untraded.discard();
            for (int index = 0; index < expected.trades().size(); index++) {
                TradeExpectation trade = expected.trades().get(index);
                String what = expected.id() + " trade " + index;
                player.getInventory().clearContent();
                open(helper, player, villager);
                MerchantMenu menu = (MerchantMenu) player.containerMenu;
                menu.setSelectionHint(index);
                menu.getSlot(0).set(stack(trade.costs().getFirst()));
                if (trade.costs().size() > 1) {
                    menu.getSlot(1).set(stack(trade.costs().get(1)));
                }
                assertStack(helper, menu.getSlot(2).getItem(), trade.gives(), what + " offers");
                menu.quickMoveStack(player, 2);
                Item bought = item(trade.gives().item());
                helper.assertValueEqual(count(player, bought), trade.gives().count(), what + " bought");
                helper.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(),
                        what + " took its whole price");
                helper.assertValueEqual(villager.getOffers().get(index).getUses(), 1, what + " counts one use");
                player.closeContainer();
            }
            helper.assertValueEqual(villager.getVillagerXp(), Merchants.LOCKING_XP,
                    "the trades left the one point of XP that locks the profession");
            helper.assertValueEqual(villager.getVillagerData().level(), 1, "and no level");
            helper.assertFalse(ResetProfession.create().tryStart(level, villager, level.getGameTime()),
                    "a merchant that traded keeps its profession without its job site");
            helper.assertTrue(villager.getVillagerData().profession().is(Merchants.FIXED_PRICE), "it is still a merchant");
            villager.discard();
        }
        logout(player);
        helper.succeed();
    }

    /** A jobless villager next to an Ancient Casting becomes an Ancient Builder, as with vanilla's workstations. */
    @QaGameTest(timeoutTicks = 1200)
    public static void aJoblessVillagerTakesTheJobAtAWorkstation(GameTestHelper helper) {
        MerchantExpectation expected = GeneratedMerchantExpectations.MERCHANTS.getFirst();
        Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
        helper.assertTrue(villager.getVillagerData().profession().is(VillagerProfession.NONE), "the villager is jobless");
        helper.setBlock(new BlockPos(3, 1, 3), BuiltInRegistries.BLOCK.getValue(Identifier.parse(expected.workstation())));
        String name = Identifier.parse(expected.id()).getPath();
        helper.succeedWhen(() -> helper.assertTrue(
                villager.getVillagerData().profession().is(Merchants.professionKey(name)),
                "the villager is still " + villager.getVillagerData().profession().getRegisteredName()));
    }

    /** The playtest handoff's setup line, run through {@code /qa run} as the player: every give lands. */
    @QaGameTest
    public static void thePlaytestSetupLineGivesEverything(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "qa-merchant-cmd");
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source,
                "qa run give @s realmforge:ancient_casting; give @s realmforge:ancient_workbench; "
                        + "give @s minecraft:villager_spawn_egg 2; give @s realmforge:gold_coin 3; "
                        + "give @s realmforge:silver_coin 30; give @s realmforge:copper_coin 99; "
                        + "give @s minecraft:obsidian 8; give @s minecraft:glass 4; give @s minecraft:lava_bucket; "
                        + "give @s minecraft:crafting_table");
        for (Stack expected : List.of(new Stack("realmforge:ancient_casting", 1),
                new Stack("realmforge:ancient_workbench", 1), new Stack("minecraft:villager_spawn_egg", 2),
                new Stack("realmforge:gold_coin", 3), new Stack("realmforge:silver_coin", 30),
                new Stack("realmforge:copper_coin", 99), new Stack("minecraft:obsidian", 8),
                new Stack("minecraft:glass", 4), new Stack("minecraft:lava_bucket", 1),
                new Stack("minecraft:crafting_table", 1))) {
            helper.assertValueEqual(count(player, item(expected.item())), expected.count(), expected.item());
        }
        logout(player);
        helper.succeed();
    }

    /** A new survival player on the test's platform. */
    private static ServerPlayer customer(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    private static Villager merchant(GameTestHelper helper, MerchantExpectation expected) {
        return merchant(helper, expected, false);
    }

    /** A villager of {@code expected}'s profession on the platform; {@code spare} puts it on another block. */
    private static Villager merchant(GameTestHelper helper, MerchantExpectation expected, boolean spare) {
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, spare ? new BlockPos(3, 1, 3) : VILLAGER);
        villager.setVillagerData(villager.getVillagerData().withProfession(helper.getLevel().registryAccess(),
                Merchants.professionKey(Identifier.parse(expected.id()).getPath())));
        return villager;
    }

    /** The player's right click on the villager, through the path a real interaction takes on the server. */
    private static void open(GameTestHelper helper, ServerPlayer player, Villager villager) {
        InteractionResult result = player.interactOn(villager, InteractionHand.MAIN_HAND, villager.position());
        helper.assertTrue(result instanceof InteractionResult.Success, "the villager trades: " + result);
        helper.assertTrue(player.containerMenu instanceof MerchantMenu, "the trade screen is open");
    }

    private static void assertStack(GameTestHelper helper, ItemStack stack, Stack expected, String what) {
        helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), expected.item(), what);
        helper.assertValueEqual(stack.getCount(), expected.count(), what + " (count)");
    }

    private static ItemStack stack(Stack stack) {
        return new ItemStack(item(stack.item()), stack.count());
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory()) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
