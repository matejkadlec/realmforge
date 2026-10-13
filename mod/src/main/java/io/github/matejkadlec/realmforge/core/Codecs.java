package io.github.matejkadlec.realmforge.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Codec building blocks shared by the features (architecture 4.2). */
public final class Codecs {
    /** A non-negative {@code long}: XP totals and game times. */
    public static final Codec<Long> NON_NEGATIVE_LONG = Codec.LONG.validate(value -> value >= 0
            ? DataResult.success(value)
            : DataResult.error(() -> "Value must be non-negative: " + value));

    /**
     * A finite, non-negative {@code float}: resource amounts. Accepts what {@code ExtraCodecs.NON_NEGATIVE_FLOAT} does,
     * with an error that names both conditions.
     */
    public static final Codec<Float> NON_NEGATIVE_FLOAT = Codec.FLOAT.validate(value -> value >= 0.0F
            && Float.isFinite(value)
            ? DataResult.success(value)
            : DataResult.error(() -> "Value must be finite and non-negative: " + value));

    /**
     * A non-negative {@code int} written as a string, for map keys: NBT compounds and JSON objects only take string
     * keys (spell bar slots).
     */
    public static final Codec<Integer> INDEX_KEY = Codec.STRING.comapFlatMap(Codecs::parseIndex, String::valueOf);

    private Codecs() {
    }

    /** A list codec that refuses repeated entries, which are always a mistake in saved state (a class twice). */
    public static <T> Codec<List<T>> distinctList(Codec<T> element) {
        return element.listOf().validate(list -> {
            Set<T> seen = new HashSet<>();
            for (T entry : list) {
                if (!seen.add(entry)) {
                    return DataResult.error(() -> "Duplicate entry: " + entry);
                }
            }
            return DataResult.success(list);
        });
    }

    private static DataResult<Integer> parseIndex(String key) {
        try {
            int index = Integer.parseInt(key);
            return index >= 0 && String.valueOf(index).equals(key)
                    ? DataResult.success(index)
                    : DataResult.error(() -> "Not a non-negative index: " + key);
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Not a number: " + key);
        }
    }
}
