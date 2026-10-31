package io.github.matejkadlec.realmforge.client.compat.inventorysorter;

import io.github.matejkadlec.realmforge.compat.inventorysorter.BackpackSortRule;
import java.util.ArrayList;
import java.util.List;
import net.kyrptonaught.inventorysorter.InventorySorterMod;
import net.kyrptonaught.inventorysorter.config.NewConfigOptions;
import net.kyrptonaught.inventorysorter.network.SortPriorityRuleSetting;
import net.kyrptonaught.inventorysorter.sort.SortPriorityPosition;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Keeps every Sophisticated Backpacks backpack in its slot when Inventory Sorting sorts ({@link BackpackSortRule},
 * RLM-519), so sorting the inventory with a backpack open leaves the backpack open and unchanged. Inventory Sorting
 * sorts on the server, by the sort priority rules each client sends it from its config ({@code config/
 * inventorysorter.json}) when it joins a world. This adds our rule to the loaded config on every join, just before
 * that, and the rule comes back on the next join if the player removes it. Inventory Sorting writes its whole loaded
 * config, our rule included, into the player's file the next time it saves it (its config screen, the sort button's
 * toggle, settings synced back from {@code /invsort}, the missing-server warning); their own rules stay, and
 * {@link BackpackSortRule#isMissing} never adds ours twice (RLM-558). The pack cannot ship the file, which
 * packwiz-installer would copy over every player's own settings. Runs only while
 * both mods are loaded (architecture 19.4); the core mod compiles against the pack's Inventory Sorting, so an update
 * that changes its config fails the build, not the game.
 */
public final class BackpacksStayInPlace {
    private BackpacksStayInPlace() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        if (ModList.get().isLoaded("inventorysorter") && ModList.get().isLoaded("sophisticatedbackpacks")) {
            // Before Inventory Sorting's own listener (normal priority) sends the rules to the server.
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClientPlayerNetworkEvent.LoggingIn.class,
                    event -> addRule(InventorySorterMod.getConfig()));
        }
    }

    private static void addRule(NewConfigOptions config) {
        List<SortPriorityRuleSetting> rules = config.sortPriorityRules;
        if (BackpackSortRule.isMissing(rules.stream().map(SortPriorityRuleSetting::match).toList())) {
            List<SortPriorityRuleSetting> withRule = new ArrayList<>(rules);
            withRule.add(new SortPriorityRuleSetting(BackpackSortRule.MATCH,
                    SortPriorityPosition.fromConfigValue(BackpackSortRule.POSITION)));
            config.sortPriorityRules = withRule;
        }
    }
}
