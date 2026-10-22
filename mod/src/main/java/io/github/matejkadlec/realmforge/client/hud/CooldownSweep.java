package io.github.matejkadlec.realmforge.client.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * The cooldown sweep over a spell bar slot (architecture 11.6; C3b, RLM-25), as in WoW: a dark wedge over the part of
 * the cooldown still to run, which shrinks clockwise from 12 o'clock as the cooldown ends. Pure geometry: each column
 * of the square becomes the spans its dark pixels form, so a slot draws with a few fills and JUnit covers it.
 */
public final class CooldownSweep {
    private CooldownSweep() {
    }

    /**
     * A dark span in a column of the square: the column {@code x} from row {@code top} (inclusive) to {@code bottom}
     * (exclusive), in pixels from the square's top left corner.
     */
    public record Span(int x, int top, int bottom) {
    }

    /**
     * The dark spans of a square of {@code size} pixels with {@code left} of the cooldown still to run, from 1 (all
     * dark) to 0 (none). A pixel is dark when its middle lies at or past the swept angle, measured clockwise from 12
     * o'clock about the square's middle.
     */
    public static List<Span> spans(int size, float left) {
        List<Span> spans = new ArrayList<>();
        if (left <= 0) {
            return spans;
        }
        double swept = 1 - Math.min(1, left);
        double middle = size / 2.0;
        for (int x = 0; x < size; x++) {
            int start = -1;
            for (int y = 0; y <= size; y++) {
                boolean dark = y < size && angle(x + 0.5 - middle, y + 0.5 - middle) >= swept;
                if (dark && start < 0) {
                    start = y;
                } else if (!dark && start >= 0) {
                    spans.add(new Span(x, start, y));
                    start = -1;
                }
            }
        }
        return spans;
    }

    /** The angle of the offset (dx right, dy down) clockwise from 12 o'clock, as a share of a turn from 0 to 1. */
    static double angle(double dx, double dy) {
        double turn = Math.atan2(dx, -dy) / (2 * Math.PI);
        return turn < 0 ? turn + 1 : turn;
    }
}
