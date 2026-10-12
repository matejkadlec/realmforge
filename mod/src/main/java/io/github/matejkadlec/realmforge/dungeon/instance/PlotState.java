package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Lifecycle of a plot (architecture 15.5). W4 adds {@code completed} for a finished dungeon run. */
public enum PlotState implements StringRepresentable {
    /** Rooms are being placed over several ticks; chunks are held by tickets. */
    BUILDING,
    /** Built, waiting for its party. */
    READY,
    /** At least one party member has entered. */
    ACTIVE,
    /** Released; its blocks are being cleared over several ticks before the plot is reused. */
    CLOSING,
    /** Unused; may still hold blocks from its last run when the cleanup strategy overwrites on reuse. */
    FREE;

    public static final Codec<PlotState> CODEC = StringRepresentable.fromEnum(PlotState::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether the plot is being worked on by the builder this tick. */
    public boolean hasWork() {
        return this == BUILDING || this == CLOSING;
    }

    /** Whether players may be inside. */
    public boolean isPlayable() {
        return this == READY || this == ACTIVE;
    }
}
