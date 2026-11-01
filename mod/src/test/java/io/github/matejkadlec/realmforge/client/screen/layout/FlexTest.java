package io.github.matejkadlec.realmforge.client.screen.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.matejkadlec.realmforge.client.screen.layout.Flex.Align;
import io.github.matejkadlec.realmforge.client.screen.layout.Flex.Insets;
import io.github.matejkadlec.realmforge.client.screen.layout.Flex.Justify;
import io.github.matejkadlec.realmforge.client.screen.layout.Flex.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** RLM-560: the layout helper's arithmetic, sizes, gaps, padding, alignment and growing children. */
class FlexTest {
    @Test
    void aRowIsItsChildrenWithGapsAndPadding() {
        Flex<String> a = Flex.leaf(10, 5, "a");
        Flex<String> b = Flex.leaf(20, 8, "b");
        Flex<String> row = Flex.<String>row().gap(4).padding(new Insets(1, 2, 3, 6)).add(a, b);

        assertEquals(6 + 10 + 4 + 20 + 2, row.preferredWidth(), "padding, children and one gap");
        assertEquals(1 + 8 + 3, row.preferredHeight(), "padding and the tallest child");

        row.layout(100, 50, row.preferredWidth(), row.preferredHeight());
        assertEquals(new Rect(106, 51, 10, 5), a.rect(), "inside the left and top padding, at its own height");
        assertEquals(new Rect(120, 51, 20, 8), b.rect(), "after the gap");
    }

    @Test
    void aColumnStacksItsChildren() {
        Flex<String> a = Flex.leaf(10, 5, "a");
        Flex<String> b = Flex.leaf(20, 8, "b");
        Flex<String> column = Flex.<String>column().gap(2).add(a, b);

        assertEquals(20, column.preferredWidth());
        assertEquals(15, column.preferredHeight());
        column.layout(0, 0, 20, 15);
        assertEquals(new Rect(0, 0, 10, 5), a.rect());
        assertEquals(new Rect(0, 7, 20, 8), b.rect());
    }

    @Test
    void fixedSizesWinOverContent() {
        Flex<String> leaf = Flex.<String>leaf(10, 5, null).size(30, 12);
        assertEquals(30, leaf.preferredWidth());
        assertEquals(12, leaf.preferredHeight());
        Flex<String> row = Flex.<String>row().width(100).add(Flex.leaf(10, 5, null));
        assertEquals(100, row.preferredWidth(), "a container's fixed width too");
        assertEquals(5, row.preferredHeight());
    }

    @Test
    void growingChildrenShareTheFreeSpaceByWeight() {
        Flex<String> fixed = Flex.leaf(10, 5, "fixed");
        Flex<String> one = Flex.<String>leaf(0, 5, "one").grow(1);
        Flex<String> two = Flex.<String>leaf(0, 5, "two").grow(2);
        Flex<String> row = Flex.<String>row().add(fixed, one, two);

        row.layout(0, 0, 100, 5);
        assertEquals(10, fixed.rect().width(), "a child that does not grow keeps its size");
        assertEquals(30, one.rect().width(), "a third of the 90 left");
        assertEquals(60, two.rect().width(), "two thirds");
        assertEquals(40, two.rect().x());
    }

    @Test
    void aRemainderGoesToTheFirstGrowingChildren() {
        List<Flex<String>> columns = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            columns.add(Flex.<String>leaf(0, 5, "c" + i).grow(1));
        }
        Flex<String> row = Flex.<String>row().addAll(columns);

        row.layout(0, 0, 100, 5);
        assertEquals(List.of(34, 33, 33), columns.stream().map(column -> column.rect().width()).toList(),
                "100 over three: one pixel left over, to the first");
        assertEquals(List.of(0, 34, 67), columns.stream().map(column -> column.rect().x()).toList());
    }

    @Test
    void aZeroBasisMakesEqualColumnsWhateverTheirContent() {
        Flex<String> narrow = Flex.<String>leaf(10, 5, "narrow").basis(0).grow(1);
        Flex<String> wide = Flex.<String>leaf(50, 5, "wide").basis(0).grow(1);
        Flex<String> row = Flex.<String>row().gap(10).add(narrow, wide);

        assertEquals(70, row.preferredWidth(), "the preferred size still counts the content");
        row.layout(0, 0, 130, 5);
        assertEquals(60, narrow.rect().width());
        assertEquals(60, wide.rect().width());
        assertEquals(70, wide.rect().x());
    }

    @Test
    void justifyPlacesChildrenThatDoNotGrow() {
        assertEquals(List.of(0, 15), xs(Justify.START, 2));
        assertEquals(List.of(35, 50), xs(Justify.CENTER, 2), "70 free, half of it before");
        assertEquals(List.of(70, 85), xs(Justify.END, 2));
        assertEquals(List.of(0, 85), xs(Justify.SPACE_BETWEEN, 2), "the free space between them");
        assertEquals(List.of(0, 43, 85), xs(Justify.SPACE_BETWEEN, 3), "55 between three: 28 and 27");
        assertEquals(List.of(0), xs(Justify.SPACE_BETWEEN, 1), "a single child stays at the start");
    }

    private static List<Integer> xs(Justify justify, int count) {
        List<Flex<String>> leaves = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            leaves.add(Flex.leaf(15, 5, "l" + i));
        }
        Flex<String> row = Flex.<String>row().justify(justify).addAll(leaves);
        row.layout(0, 0, 100, 5);
        return leaves.stream().map(leaf -> leaf.rect().x()).toList();
    }

    @Test
    void alignPlacesChildrenAcrossTheAxis() {
        Flex<String> start = Flex.leaf(5, 4, "start");
        Flex<String> center = Flex.<String>leaf(5, 4, "center").alignSelf(Align.CENTER);
        Flex<String> end = Flex.<String>leaf(5, 4, "end").alignSelf(Align.END);
        Flex<String> stretch = Flex.<String>leaf(5, 4, "stretch").alignSelf(Align.STRETCH);
        Flex<String> fixed = Flex.<String>leaf(5, 4, "fixed").height(6).alignSelf(Align.STRETCH);
        Flex<String> row = Flex.<String>row().padding(Insets.of(1, 0)).add(start, center, end, stretch, fixed);

        row.layout(0, 0, 25, 12);
        assertEquals(new Rect(0, 1, 5, 4), start.rect());
        assertEquals(new Rect(5, 4, 5, 4), center.rect(), "(10 - 4) / 2 below the padding");
        assertEquals(new Rect(10, 7, 5, 4), end.rect());
        assertEquals(new Rect(15, 1, 5, 10), stretch.rect(), "the whole line inside the padding");
        assertEquals(new Rect(20, 1, 5, 6), fixed.rect(), "a fixed height never stretches");

        Flex<String> centered = Flex.leaf(4, 2, "centered");
        Flex<String> column = Flex.<String>column().align(Align.CENTER).add(centered);
        column.layout(0, 0, 10, 2);
        assertEquals(3, centered.rect().x(), "a container's align applies to every child");
    }

    @Test
    void nothingShrinks() {
        Flex<String> wide = Flex.leaf(80, 5, "wide");
        Flex<String> row = Flex.<String>row().add(wide, Flex.leaf(40, 5, "next"));
        row.layout(0, 0, 100, 5);
        assertEquals(80, wide.rect().width(), "content larger than its box overflows it");
        assertEquals(80, row.children().get(1).rect().x());
    }

    @Test
    void nestedNodesAreDrawnParentFirstAndFoundInnermostFirst() {
        Flex<String> label = Flex.leaf(20, 9, "label");
        Flex<String> value = Flex.leaf(10, 9, "value");
        Flex<String> line = Flex.<String>row().payload("line").add(label, Flex.<String>spacer().grow(1), value);
        Flex<String> panel = Flex.<String>column().payload("panel").padding(5).align(Align.STRETCH).add(line);

        panel.layout(0, 0, 60, 19);
        assertEquals(new Rect(5, 5, 50, 9), line.rect());
        assertEquals(new Rect(45, 5, 10, 9), value.rect(), "the spacer pushes the value to the right edge");

        List<String> drawn = new ArrayList<>();
        panel.forEach((rect, payload) -> drawn.add(payload));
        assertEquals(List.of("panel", "line", "label", "value"), drawn, "parents first, spacers have nothing");

        assertEquals(Optional.of("label"), panel.payloadAt(6, 6));
        assertEquals(Optional.of("line"), panel.payloadAt(30, 6), "over the spacer: the line itself");
        assertEquals(Optional.of("panel"), panel.payloadAt(1, 1), "in the padding");
        assertEquals(Optional.empty(), panel.payloadAt(70, 6), "outside");
        assertEquals(Optional.of("line"), panel.payloadAt(6, 6, payload -> payload.startsWith("l") && !payload
                .equals("label")), "a filter skips the label drawn over the line");
        assertEquals(Optional.empty(), panel.payloadAt(6, 6, payload -> payload.equals("value")),
                "and finds nothing when nothing there matches");
    }

    @Test
    void aLeafHasNoChildren() {
        Flex<String> leaf = Flex.leaf(1, 1, "leaf");
        assertThrows(IllegalStateException.class, () -> leaf.add(Flex.leaf(1, 1, "child")));
    }
}
