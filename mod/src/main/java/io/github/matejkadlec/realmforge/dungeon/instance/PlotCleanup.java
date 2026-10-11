package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * What happens to a plot's blocks after its run. Spike 3 compared all three (ADR 0019); {@link #CLEAR} is the
 * default, the others stay selectable for measurements with {@code /qa instance cleanup}.
 */
public enum PlotCleanup implements StringRepresentable {
    /** Clear the blocks over the next ticks, then reuse the plot. No player waits for it. */
    CLEAR,
    /** Free the plot at once with its blocks; the next run in it clears what its own rooms do not overwrite. */
    OVERWRITE,
    /** Never reuse a plot: drop it with its blocks and take a new one for every run. Disk use grows. */
    NEVER_REUSE;

    public static final Codec<PlotCleanup> CODEC = StringRepresentable.fromEnum(PlotCleanup::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
