package io.github.matejkadlec.realmforge.client.screen;

/**
 * A list's vertical scrollbar (RLM-542, Matěj's playtest of 2026-11-01: the spellbook's Known Spells scrolled with no
 * sign of it): a track beside the list, as tall as its visible rows, and a thumb whose height is the visible share of
 * the rows and whose place is the scroll. It shows only while the list holds more rows than fit. Pure arithmetic, in
 * screen pixels and rows: JUnit covers it.
 *
 * @param top the track's top
 * @param height the track's height
 * @param total how many rows the list holds
 * @param visible how many rows fit
 */
public record ScrollBar(int top, int height, int total, int visible) {
    /** The thumb's least height, so a long list's thumb can still be grabbed. */
    public static final int MIN_THUMB = 8;

    /** Whether it shows: the list holds more rows than fit. */
    public boolean shown() {
        return total > visible && height > 0;
    }

    /** The highest scroll: the first row shown when the last row is at the bottom. */
    public int maxScroll() {
        return Math.max(0, total - visible);
    }

    /** The thumb's height: the visible share of the track, at least {@link #MIN_THUMB}, at most the track. */
    public int thumbHeight() {
        if (total <= 0) {
            return height;
        }
        return Math.min(height, Math.max(MIN_THUMB, height * visible / total));
    }

    /** The thumb's top at {@code scroll}: at the track's top for the first row, at its bottom for the last. */
    public int thumbTop(int scroll) {
        int max = maxScroll();
        if (max == 0) {
            return top;
        }
        return top + (height - thumbHeight()) * Math.clamp(scroll, 0, max) / max;
    }

    /** Whether {@code y} lies on the thumb at {@code scroll}. */
    public boolean onThumb(double y, int scroll) {
        int thumbTop = thumbTop(scroll);
        return y >= thumbTop && y < thumbTop + thumbHeight();
    }

    /**
     * The scroll that puts the thumb's top at {@code thumbTop} (a drag, or a click on the track that centres the thumb
     * on it), to the nearest row and within the list.
     */
    public int scrollFor(double thumbTop) {
        int travel = height - thumbHeight();
        if (travel <= 0) {
            return 0;
        }
        return Math.clamp(Math.round((float) ((thumbTop - top) * maxScroll() / travel)), 0, maxScroll());
    }
}
