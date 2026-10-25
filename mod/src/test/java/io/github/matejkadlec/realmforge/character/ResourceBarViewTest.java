package io.github.matejkadlec.realmforge.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.spell.ResourceType;
import java.util.Optional;
import net.minecraft.core.Holder;
import org.junit.jupiter.api.Test;

class ResourceBarViewTest {
    private static final Holder<ResourceType> TYPE = Holder.direct(
            new ResourceType("resource.test", holder -> 100, holder -> 0, true));

    @Test
    void theTextShowsWholeNumbersWithTheAmountWithinZeroAndTheMaximum() {
        ResourceBarView half = view(50, 100);
        assertEquals("50 / 100", half.text());
        assertEquals(0.5, half.fraction(), 1e-9);
        assertEquals("49 / 99", view(49.7, 99.6).text(), "both rounded down");
        assertEquals("100 / 100", view(130, 100).text(), "an amount above the maximum shows the maximum");
        assertEquals(1, view(130, 100).fraction(), 1e-9);
        assertEquals("0 / 100", view(-4, 100).text());
        assertEquals(0, view(-4, 100).fraction(), 1e-9);
    }

    @Test
    void theLeadingEdgeShowsOnlyBetweenEmptyAndFull() {
        assertTrue(view(1, 100).leadingEdge());
        assertTrue(view(99, 100).leadingEdge());
        assertFalse(view(0, 100).leadingEdge(), "empty");
        assertFalse(view(100, 100).leadingEdge(), "full");
        assertFalse(view(0.6, 100).leadingEdge(), "less than one shows as 0");
    }

    @Test
    void aPoolBelowOneShowsNoBar() {
        assertEquals(Optional.empty(), ResourceBarView.of(TYPE, 0, 0));
        assertEquals(Optional.empty(), ResourceBarView.of(TYPE, 0.4, 0.5));
        assertEquals(Optional.empty(), ResourceBarView.of(TYPE, 5, -3));
    }

    private static ResourceBarView view(double amount, double maximum) {
        return ResourceBarView.of(TYPE, amount, maximum).orElseThrow();
    }
}
