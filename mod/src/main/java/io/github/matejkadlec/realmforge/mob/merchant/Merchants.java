package io.github.matejkadlec.realmforge.mob.merchant;

import com.google.common.collect.ImmutableSet;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedMerchants;
import io.github.matejkadlec.realmforge.generated.GeneratedMerchants.Merchant;
import io.github.matejkadlec.realmforge.generated.GeneratedMerchants.Trade;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeSet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Our merchants (architecture 14.4; RLM-285, ADR 0040): villager professions from {@code content/merchants/}, each
 * with a workstation block of ours and a fixed list of trades, paid in coins. The trades are vanilla trade data the
 * pipeline writes; a profession and its point of interest are built-in registry entries, so they are registered here,
 * from {@link GeneratedMerchants}: the point of interest is every state of the workstation block (one villager per
 * block, as vanilla's job sites), and the profession names its one trade set at level 1.
 *
 * <p><b>Fixed prices, no levels.</b> Vanilla's {@code Villager#startTrading} always applies its special prices:
 * reputation (switched off by the trades' {@code reputation_discount: 0}) and Hero of the Village, which takes 30 %
 * and more off every first cost and at least 1. For a profession in {@link #FIXED_PRICE} the mixin
 * {@code VillagerSpecialPricesMixin} skips them, so a price is the written one for every player, always, and calls
 * {@link #beforeTrading}, which puts the offers into the written order, since vanilla draws them in a random one. The
 * merchants have no levels (ADR 0034): the trades give the villager no XP, and {@code MerchantProgressBarMixin} turns
 * off the flag that makes the trade screen draw a level name and an XP bar, in the packet that opens the screen and
 * in the one a restock sends while it is open. Vanilla lets a level 1 villager without XP lose its profession with
 * its workstation ({@code ResetProfession}), so the first trade locks it by one point of XP ({@link #onTrade};
 * Matěj, 2026-10-19), as a vanilla villager's first trade does. Everything else about a merchant is vanilla's: how it
 * is clicked, named, restocked and zombified.
 */
public final class Merchants {
    /** {@code #realmforge:fixed_price}: the professions traded with at fixed prices, every merchant of ours. */
    public static final TagKey<VillagerProfession> FIXED_PRICE =
            TagKey.create(Registries.VILLAGER_PROFESSION, Realmforge.id("fixed_price"));
    /** The villager XP that locks a merchant's profession after its first trade. */
    public static final int LOCKING_XP = 1;
    /** One villager works at one workstation, and must stand next to it, as with vanilla's job sites. */
    private static final int MAX_TICKETS = 1;
    private static final int VALID_RANGE = 1;

    private static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Realmforge.MOD_ID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, Realmforge.MOD_ID);

    static {
        for (Merchant merchant : GeneratedMerchants.MERCHANTS) {
            ResourceKey<PoiType> jobSite = poiKey(merchant.id());
            POI_TYPES.register(merchant.id(), () -> new PoiType(
                    ImmutableSet.copyOf(merchant.workstation().get().getStateDefinition().getPossibleStates()),
                    MAX_TICKETS, VALID_RANGE));
            PROFESSIONS.register(merchant.id(), () -> new VillagerProfession(
                    Component.translatable("entity." + Realmforge.MOD_ID + ".villager." + merchant.id()),
                    poi -> poi.is(jobSite), poi -> poi.is(jobSite), ImmutableSet.of(), ImmutableSet.of(),
                    merchant.workSound().isEmpty() ? null
                            : BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(merchant.workSound())),
                    Int2ObjectMap.ofEntries(Int2ObjectMap.entry(GeneratedMerchants.LEVEL, tradeSet(merchant.id())))));
        }
    }

    private Merchants() {
    }

    /** Registers the merchants' points of interest and professions and the listener that locks a profession. */
    public static void register(IEventBus modBus) {
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
        NeoForge.EVENT_BUS.addListener(TradeWithVillagerEvent.class, Merchants::onTrade);
    }

    /** The key of a merchant's profession. */
    public static ResourceKey<VillagerProfession> professionKey(String merchant) {
        return ResourceKey.create(Registries.VILLAGER_PROFESSION, Realmforge.id(merchant));
    }

    /** The key of a merchant's point of interest, its workstation. */
    public static ResourceKey<PoiType> poiKey(String merchant) {
        return ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Realmforge.id(merchant));
    }

    /** The key of a merchant's one trade set. */
    public static ResourceKey<TradeSet> tradeSet(String merchant) {
        return ResourceKey.create(Registries.TRADE_SET, Realmforge.id(merchant + "/level_" + GeneratedMerchants.LEVEL));
    }

    /** Whether {@code villager}'s profession trades at fixed prices. */
    public static boolean isFixedPrice(Villager villager) {
        return villager.getVillagerData().profession().is(FIXED_PRICE);
    }

    /** The merchant {@code villager} is, if its profession is one of ours. */
    public static Optional<Merchant> of(Villager villager) {
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        return GeneratedMerchants.MERCHANTS.stream()
                .filter(merchant -> profession.is(professionKey(merchant.id())))
                .findFirst();
    }

    /**
     * Called by {@code VillagerSpecialPricesMixin} where vanilla would set a fixed-price merchant's special prices,
     * right before its trade screen opens: puts the offers into the written order.
     */
    public static void beforeTrading(Villager villager) {
        of(villager).ifPresent(merchant -> villager.getOffers().sort(inWrittenOrder(merchant)));
    }

    /** A fixed-price merchant's first trade locks its profession: one point of villager XP, never more. */
    static void onTrade(TradeWithVillagerEvent event) {
        if (event.getAbstractVillager() instanceof Villager villager && isFixedPrice(villager)
                && villager.getVillagerXp() < LOCKING_XP) {
            villager.setVillagerXp(LOCKING_XP);
        }
    }

    /** Orders offers as the merchant's trades are written; an offer that is none of them goes last. */
    static Comparator<MerchantOffer> inWrittenOrder(Merchant merchant) {
        List<Trade> trades = merchant.trades();
        return Comparator.comparingInt(offer -> {
            ItemStack result = offer.getResult();
            String item = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
            for (int index = 0; index < trades.size(); index++) {
                if (trades.get(index).gives().equals(item) && trades.get(index).count() == result.getCount()) {
                    return index;
                }
            }
            return trades.size();
        });
    }
}
