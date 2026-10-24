package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.spell.ResourceType;
import io.github.matejkadlec.realmforge.spell.Resources;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;

/**
 * What the resource bar under the spell bar shows (RLM-400, architecture 11.5, 18): the player's class resource, read
 * from data synced to its owner, so the client draws the numbers the server has and a GameTest reads the same ones.
 * The amount is the pool in the {@code realmforge:combat} attachment (0 before a rage pool first fills), the maximum is
 * the resource type's: the Max Mana stat (synced, as every stat of ours) or rage's 100. Nothing without a character,
 * or while the maximum is below 1.
 *
 * @param type the resource the class uses
 * @param current the amount shown: rounded down, within 0 and the maximum
 * @param maximum the pool's size shown: rounded down, at least 1
 * @param fraction how full the pool is, from 0 to 1, from the exact amounts
 */
public record ResourceBarView(Holder<ResourceType> type, int current, int maximum, double fraction) {
    /** The bar of {@code player}'s class resource, empty without a character or a pool of at least 1. */
    public static Optional<ResourceBarView> of(Player player) {
        return Characters.mainClass(player).map(playerClass -> playerClass.value().resource()).flatMap(type ->
                of(type, Resources.amount(player, type), type.value().maximum(player)));
    }

    /** The bar of a pool of {@code type} holding {@code amount} of {@code maximum}. */
    public static Optional<ResourceBarView> of(Holder<ResourceType> type, double amount, double maximum) {
        int shownMaximum = (int) Math.floor(maximum);
        if (shownMaximum < 1) {
            return Optional.empty();
        }
        int shownCurrent = (int) Math.clamp(Math.floor(amount), 0, shownMaximum);
        return Optional.of(new ResourceBarView(type, shownCurrent, shownMaximum, Math.clamp(amount / maximum, 0, 1)));
    }

    /** "50 / 100": the amount and the maximum, as integers. */
    public String text() {
        return current + " / " + maximum;
    }

    /** Whether the fill's leading edge shows its darker line: the pool is neither empty nor full. */
    public boolean leadingEdge() {
        return current > 0 && current < maximum;
    }
}
