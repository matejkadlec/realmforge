package io.github.matejkadlec.realmforge.client.screen.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * A small flexbox-style layout for our screens (RLM-560, architecture 18), so a screen says what goes next to what
 * instead of placing coordinates by hand: the character sheet's tabs now, RLM-225's frame and font and the guide (M4)
 * later. A node is a row or a column of children, or a leaf with a size of its own; any node may carry a payload, which
 * the screen draws in the rectangle the layout gives it (text, a panel, a bar, a hover area).
 *
 * <p>As in CSS flexbox: a container lays its children out along its axis with a gap between them, inside its padding.
 * Each child starts at its basis (its preferred size along the axis unless set); free space goes to the children that
 * grow, by their weights, and otherwise places them by {@link Justify}. Across the axis a child takes its preferred
 * size placed by {@link Align}, or the whole line for {@link Align#STRETCH}. Nothing shrinks: content larger than its
 * box overflows it, so a screen sizes its panel from {@link #preferredWidth()} and {@link #preferredHeight()} first.
 * Sizes are whole GUI pixels; a remainder goes one pixel at a time to the first growing children.
 *
 * <p>Pure arithmetic, without Minecraft classes, so JUnit covers it ({@code FlexTest}).
 *
 * @param <P> what the screen draws in a node
 */
public final class Flex<P> {
    /** A size or basis the layout works out itself. */
    public static final int AUTO = -1;

    /** The direction a container lays its children out in. */
    public enum Axis {
        ROW, COLUMN
    }

    /** Where a child sits across its container's axis. */
    public enum Align {
        START, CENTER, END, STRETCH
    }

    /** Where children sit along the axis when none grows into the free space. */
    public enum Justify {
        START, CENTER, END, SPACE_BETWEEN
    }

    /** Space inside a node's edges, in GUI pixels. */
    public record Insets(int top, int right, int bottom, int left) {
        public static final Insets NONE = new Insets(0, 0, 0, 0);

        public static Insets all(int value) {
            return new Insets(value, value, value, value);
        }

        public static Insets of(int vertical, int horizontal) {
            return new Insets(vertical, horizontal, vertical, horizontal);
        }

        int along(Axis axis) {
            return axis == Axis.ROW ? left + right : top + bottom;
        }

        int start(Axis axis) {
            return axis == Axis.ROW ? left : top;
        }
    }

    /** A node's place after {@link #layout}, in the screen's coordinates. */
    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    @Nullable
    private final Axis axis;
    private final List<Flex<P>> children = new ArrayList<>();
    @Nullable
    private P payload;
    private int contentWidth;
    private int contentHeight;
    private int width = AUTO;
    private int height = AUTO;
    private int gap;
    private Insets padding = Insets.NONE;
    private Align align = Align.START;
    @Nullable
    private Align alignSelf;
    private Justify justify = Justify.START;
    private int grow;
    private int basis = AUTO;
    private Rect rect = new Rect(0, 0, 0, 0);

    private Flex(@Nullable Axis axis) {
        this.axis = axis;
    }

    /** A container that lays its children out left to right. */
    public static <P> Flex<P> row() {
        return new Flex<>(Axis.ROW);
    }

    /** A container that lays its children out top to bottom. */
    public static <P> Flex<P> column() {
        return new Flex<>(Axis.COLUMN);
    }

    /** A leaf of the given size, such as a line of text, drawing {@code payload}. */
    public static <P> Flex<P> leaf(int width, int height, @Nullable P payload) {
        Flex<P> leaf = new Flex<>(null);
        leaf.contentWidth = Math.max(0, width);
        leaf.contentHeight = Math.max(0, height);
        leaf.payload = payload;
        return leaf;
    }

    /** An empty leaf that only takes space: with {@link #grow} it pushes its siblings apart. */
    public static <P> Flex<P> spacer() {
        return leaf(0, 0, null);
    }

    /** Adds children at the end; a leaf takes none. */
    @SafeVarargs
    public final Flex<P> add(Flex<P>... nodes) {
        return addAll(List.of(nodes));
    }

    public Flex<P> addAll(List<Flex<P>> nodes) {
        if (axis == null) {
            throw new IllegalStateException("a leaf has no children");
        }
        children.addAll(nodes);
        return this;
    }

    /** What the screen draws in this node's rectangle, drawn before its children's. */
    public Flex<P> payload(@Nullable P value) {
        this.payload = value;
        return this;
    }

    /** A fixed width, or {@link #AUTO}. */
    public Flex<P> width(int value) {
        this.width = value;
        return this;
    }

    /** A fixed height, or {@link #AUTO}. */
    public Flex<P> height(int value) {
        this.height = value;
        return this;
    }

    public Flex<P> size(int widthValue, int heightValue) {
        return width(widthValue).height(heightValue);
    }

    /** Space between neighbouring children along the axis. */
    public Flex<P> gap(int value) {
        this.gap = Math.max(0, value);
        return this;
    }

    public Flex<P> padding(Insets value) {
        this.padding = value;
        return this;
    }

    public Flex<P> padding(int value) {
        return padding(Insets.all(value));
    }

    /** Where the children sit across the axis, unless one sets its own ({@link #alignSelf}). */
    public Flex<P> align(Align value) {
        this.align = value;
        return this;
    }

    /** Where this node sits across its container's axis, overriding the container's {@link #align}. */
    public Flex<P> alignSelf(Align value) {
        this.alignSelf = value;
        return this;
    }

    public Flex<P> justify(Justify value) {
        this.justify = value;
        return this;
    }

    /** This node's share of its container's free space along the axis; 0, the default, keeps its basis. */
    public Flex<P> grow(int weight) {
        this.grow = Math.max(0, weight);
        return this;
    }

    /**
     * The size this node starts from along its container's axis before free space is shared, instead of its
     * preferred size: {@code basis(0).grow(1)} on every child splits a row into equal columns. Its container's preferred
     * size still counts the child's preferred size.
     */
    public Flex<P> basis(int value) {
        this.basis = value;
        return this;
    }

    @Nullable
    public P payload() {
        return payload;
    }

    public List<Flex<P>> children() {
        return Collections.unmodifiableList(children);
    }

    /** Where the last {@link #layout} put this node. */
    public Rect rect() {
        return rect;
    }

    /** The width this node wants: its fixed width, a leaf's own, or its children's with gaps and padding. */
    public int preferredWidth() {
        return width != AUTO ? width : axis == null ? contentWidth : fromChildren(Axis.ROW);
    }

    /** The height this node wants: its fixed height, a leaf's own, or its children's with gaps and padding. */
    public int preferredHeight() {
        return height != AUTO ? height : axis == null ? contentHeight : fromChildren(Axis.COLUMN);
    }

    private int fromChildren(Axis along) {
        int size = padding.along(along);
        if (along == axis) {
            for (Flex<P> child : children) {
                size += child.preferred(along);
            }
            size += gap * Math.max(0, children.size() - 1);
        } else {
            int largest = 0;
            for (Flex<P> child : children) {
                largest = Math.max(largest, child.preferred(along));
            }
            size += largest;
        }
        return size;
    }

    /** The preferred size along {@code along}; a basis changes only how free space is shared, not this. */
    private int preferred(Axis along) {
        return along == Axis.ROW ? preferredWidth() : preferredHeight();
    }

    /** Lays this node and everything inside it out in the rectangle at ({@code x}, {@code y}). */
    public void layout(int x, int y, int layoutWidth, int layoutHeight) {
        rect = new Rect(x, y, layoutWidth, layoutHeight);
        if (axis == null || children.isEmpty()) {
            return;
        }
        Axis cross = axis == Axis.ROW ? Axis.COLUMN : Axis.ROW;
        int innerMain = (axis == Axis.ROW ? layoutWidth : layoutHeight) - padding.along(axis);
        int innerCross = (axis == Axis.ROW ? layoutHeight : layoutWidth) - padding.along(cross);
        int count = children.size();
        int gaps = gap * (count - 1);

        int[] sizes = new int[count];
        int used = 0;
        int weights = 0;
        for (int i = 0; i < count; i++) {
            Flex<P> child = children.get(i);
            sizes[i] = child.basis != AUTO ? child.basis : child.preferred(axis);
            used += sizes[i];
            weights += child.grow;
        }
        int free = innerMain - gaps - used;
        if (free > 0 && weights > 0) {
            int shared = 0;
            for (int i = 0; i < count; i++) {
                int share = (int) ((long) free * children.get(i).grow / weights);
                sizes[i] += share;
                shared += share;
            }
            for (int i = 0; i < count && shared < free; i++) {
                if (children.get(i).grow > 0) {
                    sizes[i]++;
                    shared++;
                }
            }
            free = 0;
        }

        int offset = 0;
        int spacing = gap;
        int extra = 0;
        if (free > 0) {
            switch (justify) {
                case CENTER -> offset = free / 2;
                case END -> offset = free;
                case SPACE_BETWEEN -> {
                    if (count > 1) {
                        spacing += free / (count - 1);
                        extra = free % (count - 1);
                    }
                }
                case START -> {
                }
            }
        }

        int main = padding.start(axis) + offset;
        for (int i = 0; i < count; i++) {
            Flex<P> child = children.get(i);
            Align childAlign = child.alignSelf != null ? child.alignSelf : align;
            int fixedCross = cross == Axis.ROW ? child.width : child.height;
            int crossSize = childAlign == Align.STRETCH && fixedCross == AUTO ? innerCross
                    : child.preferred(cross);
            int crossOffset = switch (childAlign) {
                case CENTER -> (innerCross - crossSize) / 2;
                case END -> innerCross - crossSize;
                case START, STRETCH -> 0;
            };
            int crossStart = padding.start(cross) + crossOffset;
            if (axis == Axis.ROW) {
                child.layout(x + main, y + crossStart, sizes[i], crossSize);
            } else {
                child.layout(x + crossStart, y + main, crossSize, sizes[i]);
            }
            main += sizes[i] + spacing + (i < extra ? 1 : 0);
        }
    }

    /** Every node with a payload, a container before its children (the order to draw in), with its rectangle. */
    public void forEach(BiConsumer<Rect, P> action) {
        if (payload != null) {
            action.accept(rect, payload);
        }
        for (Flex<P> child : children) {
            child.forEach(action);
        }
    }

    /** The payload of the innermost node with a payload under the point, after {@link #layout}. */
    public Optional<P> payloadAt(double px, double py) {
        return payloadAt(px, py, payload -> true);
    }

    /**
     * The innermost payload under the point that {@code filter} accepts, after {@link #layout}: a screen asks for what
     * reacts to the mouse (a button, a hover area), not the text drawn over it.
     */
    public Optional<P> payloadAt(double px, double py, Predicate<? super P> filter) {
        if (!rect.contains(px, py)) {
            return Optional.empty();
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            Optional<P> inner = children.get(i).payloadAt(px, py, filter);
            if (inner.isPresent()) {
                return inner;
            }
        }
        return payload != null && filter.test(payload) ? Optional.of(payload) : Optional.empty();
    }
}
