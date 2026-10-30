package io.github.matejkadlec.realmforge.client.spell;

import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Right click with a wand casts the spell bar's first slot (RLM-500; Matěj, 2026-10-29): with a wand
 * ({@code #realmforge:wands}) in the main hand and a spell in the first slot, a right click that interacts with no
 * block or entity is a press of that slot's key and holding it holds the key, so {@link SpellBarInput} casts it
 * exactly as the key would (the spell queue, aim assistance, the server's refusals). Blocks and entities come first, as
 * with a bow; sneaking skips a block's interaction, as vanilla does for any held item. The off hand's item is not used
 * while it does. An empty first slot leaves right click as it is. The rules: {@link WandClicks}. Main thread only.
 */
public final class WandCasting {
    /** The spell bar slot right click stands for: the first, R by default. */
    public static final int SLOT = 0;

    private static final WandClicks CLICKS = new WandClicks();

    private WandCasting() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, WandCasting::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> CLICKS.clear());
    }

    /** Whether a right click is left to cast as a press of {@link #SLOT}, using it up. */
    static boolean consumeClick() {
        return CLICKS.consumeClick();
    }

    /** Whether {@code player} holds right click to keep casting {@link #SLOT}; once a client tick. */
    static boolean isDown(LocalPlayer player) {
        return CLICKS.held(Minecraft.getInstance().options.keyUse.isDown() && wand(player));
    }

    private static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!event.isUseItem() || player == null) {
            return;
        }
        boolean spell = player.getData(SpellAttachments.SPELLBOOK).slot(SLOT).isPresent();
        if (CLICKS.use(event.getHand(), wand(player), spell)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    private static boolean wand(LocalPlayer player) {
        return player.getMainHandItem().is(SpellCaster.WANDS);
    }
}
