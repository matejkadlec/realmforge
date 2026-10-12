package io.github.matejkadlec.realmforge.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.ExtraCodecs;

/**
 * A named table of points (level to value) from {@code content/curves/}, entry of the datapack registry
 * {@code realmforge:curve} (architecture 5.2): XP per level, mob scaling, item budgets. Between two points the value
 * is linearly interpolated; before the first and after the last point it stays at that point's value.
 *
 * @param name translation key of an optional display name, for tools and debugging
 * @param points the points, in strictly increasing level order; at least one
 */
public record Curve(Optional<String> name, List<Point> points) {
    public static final Codec<Curve> CODEC = RecordCodecBuilder.<Curve>create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("name").forGetter(Curve::name),
                    ExtraCodecs.nonEmptyList(Point.CODEC.listOf()).fieldOf("points").forGetter(Curve::points))
            .apply(instance, Curve::new))
            .validate(Curve::validate);

    public Curve {
        points = List.copyOf(points);
    }

    /** One point of a curve: the value at a level. */
    public record Point(int level, double value) {
        public static final Codec<Point> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ExtraCodecs.NON_NEGATIVE_INT.fieldOf("level").forGetter(Point::level),
                        Codec.DOUBLE.fieldOf("value").forGetter(Point::value))
                .apply(instance, Point::new));
    }

    /** The value at {@code level}: interpolated between the neighbouring points, constant beyond the ends. */
    public double valueAt(double level) {
        Point first = points.getFirst();
        if (level <= first.level()) {
            return first.value();
        }
        for (int i = 1; i < points.size(); i++) {
            Point high = points.get(i);
            if (level < high.level()) {
                Point low = points.get(i - 1);
                double t = (level - low.level()) / (high.level() - low.level());
                return low.value() + t * (high.value() - low.value());
            }
        }
        return points.getLast().value();
    }

    private static DataResult<Curve> validate(Curve curve) {
        List<Point> points = curve.points();
        for (int i = 1; i < points.size(); i++) {
            int before = points.get(i - 1).level();
            int after = points.get(i).level();
            if (after <= before) {
                return DataResult.error(() -> "Curve point levels must strictly increase, but " + after
                        + " follows " + before);
            }
        }
        for (Point point : points) {
            if (!Double.isFinite(point.value())) {
                return DataResult.error(() -> "Curve value at level " + point.level() + " is not finite");
            }
        }
        return DataResult.success(curve);
    }
}
