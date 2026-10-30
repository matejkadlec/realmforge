package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.spell.SpellText;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a racial passive shows (C4, RLM-219; architecture 10.1): for a player of its race, the tooltip of every item of
 * the passive's {@code weapons} (hover, JEI) gets two lines below the item's stats, where Apotheosis puts "Heavenly
 * Implements": the passive's name in dark gray and its description in light gray, "Sword Specialization" and "+10%
 * damage". Common code reading synced data, so the client's tooltip listener only draws it.
 */
public final class RacialPassives {
    private static final double PERCENT = 100;

    private RacialPassives() {
    }

    /** The player's racial passive, if their race has one. */
    public static Optional<RacialPassive> of(Player player) {
        return Characters.data(player).race()
                .flatMap(key -> player.registryAccess().lookupOrThrow(CharacterRegistries.RACE).get(key))
                .map(Holder::value).flatMap(Race::racialPassive);
    }

    /**
     * The tooltip lines {@code stack} gets for {@code player}: none unless it is a weapon of their racial passive; the
     * passive's name in gold and its description in yellow (Matěj, 2026-10-30).
     */
    public static List<Component> tooltip(Player player, ItemStack stack) {
        return of(player).filter(passive -> passive.appliesWith(stack))
                .map(passive -> List.<Component>of(
                        Component.translatable(passive.name()).withStyle(ChatFormatting.GOLD),
                        Component.literal(description(passive)).withStyle(ChatFormatting.YELLOW)))
                .orElse(List.of());
    }

    /**
     * The passive's description in the current language, its {@code {bonus}} filled with its first modifier's amount:
     * in percent for a multiplied operation, as it is otherwise.
     */
    public static String description(RacialPassive passive) {
        CharacterModifier first = passive.modifiers().getFirst();
        double bonus = first.operation() == AttributeModifier.Operation.ADD_VALUE ? first.amount()
                : first.amount() * PERCENT;
        return Language.getInstance().getOrDefault(passive.description()).replace("{bonus}", SpellText.decimal(bonus));
    }
}
