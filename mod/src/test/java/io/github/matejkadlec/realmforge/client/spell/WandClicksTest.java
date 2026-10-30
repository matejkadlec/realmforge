package io.github.matejkadlec.realmforge.client.spell;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

/** Right click with a wand as the spell bar's first slot (RLM-500; Matěj, 2026-10-29). */
class WandClicksTest {
    private static final InteractionHand MAIN = InteractionHand.MAIN_HAND;
    private static final InteractionHand OFF = InteractionHand.OFF_HAND;

    @Test
    void aClickThatReachesTheOffHandCastsOnceAndUsesNothingThere() {
        WandClicks clicks = new WandClicks();
        assertFalse(clicks.use(MAIN, true, true), "the main hand still interacts with blocks and entities");
        assertTrue(clicks.use(OFF, true, true), "the off hand's item is not used");
        assertTrue(clicks.consumeClick());
        assertFalse(clicks.consumeClick());
    }

    @Test
    void holdingKeepsCastingAndInteractsWithNothingUntilLetGo() {
        WandClicks clicks = new WandClicks();
        clicks.use(MAIN, true, true);
        clicks.use(OFF, true, true);
        clicks.consumeClick();
        assertTrue(clicks.held(true));
        assertTrue(clicks.use(MAIN, true, true), "vanilla's repeat while held opens no door");
        assertFalse(clicks.consumeClick(), "a repeat is no new click");
        assertTrue(clicks.held(true));
        assertFalse(clicks.held(false));
        assertFalse(clicks.held(true), "a hold that stopped waits for the next click");
        assertFalse(clicks.use(MAIN, true, true), "after letting go, blocks and entities come first again");
    }

    @Test
    void aClickTheMainHandUsedUpNeverCasts() {
        WandClicks clicks = new WandClicks();
        // An interaction with a block or an entity returns before the off hand's turn.
        assertFalse(clicks.use(MAIN, true, true));
        assertFalse(clicks.consumeClick());
        assertFalse(clicks.held(true));
    }

    @Test
    void anEmptyFirstSlotOrNoWandLeavesRightClickAlone() {
        WandClicks clicks = new WandClicks();
        assertFalse(clicks.use(OFF, true, false));
        assertFalse(clicks.use(OFF, false, true));
        assertFalse(clicks.consumeClick());
        assertFalse(clicks.held(true));
    }

    @Test
    void clearForgetsClicksAndTheHold() {
        WandClicks clicks = new WandClicks();
        clicks.use(OFF, true, true);
        clicks.clear();
        assertFalse(clicks.consumeClick());
        assertFalse(clicks.held(true));
    }
}
