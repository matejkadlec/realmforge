package io.github.matejkadlec.realmforge.loot;

import java.util.function.Consumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * What container loot asks the mod that owns the world tiers, Apotheosis (ADR 0038; the Apotheosis design, "World
 * tiers"): the tier of the player who opens a container, and a gem or an affix item rolled for them. {@code loot} sits
 * below {@code compat} (architecture 4.4), so it defines this provider and {@code compat/apotheosis} implements it
 * when Apotheosis is loaded; without it every player is at the first tier and the two rolls give nothing.
 */
public final class WorldTiers {
    /** The tiers in order: Haven, Frontier, Ascent, Summit, Pinnacle. */
    public static final int COUNT = 5;

    private static Provider provider = new Provider() {
    };

    private WorldTiers() {
    }

    /** What the owner of the world tiers provides. */
    public interface Provider {
        /** The player's world tier, from 0 (Haven) to {@link #COUNT} - 1 (Pinnacle). */
        default int tier(Player player) {
            return 0;
        }

        /**
         * Rolls a gem for the loot context's player, of a purity their tier gives: the purest of {@code rolls} rolls,
         * so more rolls give a better gem for the same tier.
         */
        default void gem(LootContext context, int rolls, Consumer<ItemStack> output) {
        }

        /** Rolls an item with affixes for the loot context's player, of a rarity their tier gives. */
        default void affixItem(LootContext context, Consumer<ItemStack> output) {
        }
    }

    /** Names the provider. Called once, at mod construction, by {@code compat/apotheosis}. */
    public static void provide(Provider tiers) {
        provider = tiers;
    }

    /** The world tier of the player a loot roll is for, its {@code THIS_ENTITY}: 0 without a player. */
    public static int of(LootContext context) {
        return context.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof Player player
                ? Math.clamp(provider.tier(player), 0, COUNT - 1) : 0;
    }

    static void gem(LootContext context, int rolls, Consumer<ItemStack> output) {
        provider.gem(context, rolls, output);
    }

    static void affixItem(LootContext context, Consumer<ItemStack> output) {
        provider.affixItem(context, output);
    }
}
