package io.github.matejkadlec.realmforge.client.tooltip;

import io.github.matejkadlec.realmforge.item.NetheriteUpgrades;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddAttributeTooltipsEvent;

/**
 * The netherite upgrade's line in an upgraded piece's tooltip (RLM-566; architecture 13): "Ruby Upgrade" in the gem's
 * colour right below the piece's stats, whose slot heading already lists the upgrade's bonus. The line itself comes
 * from {@link NetheriteUpgrades}.
 */
public final class NetheriteUpgradeTooltip {
    private NetheriteUpgradeTooltip() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AddAttributeTooltipsEvent.class, NetheriteUpgradeTooltip::onAttributeTooltips);
    }

    private static void onAttributeTooltips(AddAttributeTooltipsEvent event) {
        NetheriteUpgrades.tooltip(event.getStack()).forEach(line -> event.addTooltipLines(line));
    }
}
