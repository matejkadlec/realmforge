package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.spell.SpellText;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a racial passive shows (C4, RLM-219; architecture 10.1): for a player of its race, the tooltip of every item of
 * the passive's {@code weapons} (hover, JEI) gets two lines below the item's stats, where Apotheosis puts "Heavenly
 * Implements": the passive's name in gold and its tooltip line in yellow, "Sword Specialization" and " +10% Damage",
 * indented like vanilla's attribute lines. The character sheet shows the longer description instead. Common code
 * reading synced data, so the client's tooltip listener only draws it.
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
     * passive's name in gold and its tooltip line in yellow (Matěj, 2026-10-30), the line indented by one space as
     * vanilla indents the attribute lines under "When in Main Hand:" (Matěj, 2026-11-04, RLM-563).
     */
    public static List<Component> tooltip(Player player, ItemStack stack) {
        return of(player).filter(passive -> passive.appliesWith(stack))
                .map(passive -> List.<Component>of(
                        Component.translatable(passive.name()).withStyle(ChatFormatting.GOLD),
                        CommonComponents.space().append(tooltipDescription(passive))
                                .withStyle(ChatFormatting.YELLOW)))
                .orElse(List.of());
    }

    /**
     * The passive's description in the current language, the line under its name on the character sheet, its
     * {@code {bonus}} filled with its first modifier's amount: in percent for a multiplied operation, as it is
     * otherwise.
     */
    public static String description(RacialPassive passive) {
        return filled(passive, passive.description());
    }

    /**
     * The passive's shorter line for the weapon's tooltip in the current language, filled as {@link #description}:
     * its {@code tooltip_description}, or the description when it has none.
     */
    public static String tooltipDescription(RacialPassive passive) {
        return filled(passive, passive.tooltipDescription().orElse(passive.description()));
    }

    private static String filled(RacialPassive passive, String key) {
        CharacterModifier first = passive.modifiers().getFirst();
        double bonus = first.operation() == AttributeModifier.Operation.ADD_VALUE ? first.amount()
                : first.amount() * PERCENT;
        return Language.getInstance().getOrDefault(key).replace("{bonus}", SpellText.decimal(bonus));
    }
}
