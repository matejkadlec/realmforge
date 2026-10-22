package io.github.matejkadlec.realmforge.qa.combat;

import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The QA mod's items, for tests and playtests only: {@code /give @s realmforge_qa:<id>} in the dev client. */
public final class QaItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealmforgeQa.MOD_ID);

    /**
     * A two-handed weapon to block with (RLM-297): an iron sword's stats under a name Better Combat's fallback reads
     * as a claymore, which is two-handed, so it is raised to block like any two-handed weapon of the pack. It looks
     * like an iron sword.
     */
    public static final DeferredItem<Item> TEST_GREATSWORD = ITEMS.registerItem("test_greatsword", Item::new,
            properties -> properties.sword(ToolMaterial.IRON, 5.0F, -3.0F));

    private QaItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
