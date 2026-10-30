package io.github.matejkadlec.realmforge.client.spell;

import net.minecraft.world.InteractionHand;

/**
 * Right click with a wand as the spell bar's first slot (RLM-500; Matěj, 2026-10-29): the rules of
 * {@link WandCasting}, apart from the game. Vanilla's use key goes through the main hand (an entity's interaction, a
 * block's, then the held item's use) and only then through the off hand. A wand's own use does nothing, so the off
 * hand's turn means the main hand found nothing to interact with: that turn becomes a click of the slot, and the off
 * hand's item is not used. From then until the use key is let go of, the hold keeps casting and vanilla's repeats of
 * the use key (every 4 ticks while it is held) interact with nothing, so a hold that sweeps across a door never opens
 * it.
 */
final class WandClicks {
    private int clicks;
    private boolean holding;

    /**
     * Vanilla's use key reached {@code hand}, with a wand in the main hand ({@code wand}) and a spell in the first slot
     * ({@code spell}) or not; returns whether the use key stops here.
     */
    boolean use(InteractionHand hand, boolean wand, boolean spell) {
        if (!wand || !spell) {
            return false;
        }
        if (hand == InteractionHand.MAIN_HAND) {
            return holding;
        }
        clicks++;
        holding = true;
        return true;
    }

    /** Whether a click is left to cast, using it up, as {@code KeyMapping#consumeClick} does. */
    boolean consumeClick() {
        if (clicks == 0) {
            return false;
        }
        clicks--;
        return true;
    }

    /**
     * Once a client tick: whether the hold goes on, given whether the use key is down with a wand in the main hand
     * ({@code down}). A hold that stops stays stopped until the next click.
     */
    boolean held(boolean down) {
        if (!down) {
            holding = false;
        }
        return holding;
    }

    void clear() {
        clicks = 0;
        holding = false;
    }
}
