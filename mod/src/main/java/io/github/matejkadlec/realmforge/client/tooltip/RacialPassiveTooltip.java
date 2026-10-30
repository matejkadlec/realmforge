package io.github.matejkadlec.realmforge.client.tooltip;

import io.github.matejkadlec.realmforge.character.RacialPassives;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddAttributeTooltipsEvent;

/**
 * The racial passive's lines in a weapon's tooltip (C4, RLM-219; architecture 10.1): for a Human, every sword-like
 * weapon (hover, JEI) shows "Sword Specialization" in gold and "+10% damage with swords" in yellow right below its
 * stats, where Apotheosis puts "Heavenly Implements". NeoForge's {@code AddAttributeTooltipsEvent} adds lines after the
 * attribute lines; the lines themselves come from {@link RacialPassives}.
 */
public final class RacialPassiveTooltip {
    private RacialPassiveTooltip() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AddAttributeTooltipsEvent.class, RacialPassiveTooltip::onAttributeTooltips);
    }

    private static void onAttributeTooltips(AddAttributeTooltipsEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        RacialPassives.tooltip(player, event.getStack()).forEach(line -> event.addTooltipLines(line));
    }
}
