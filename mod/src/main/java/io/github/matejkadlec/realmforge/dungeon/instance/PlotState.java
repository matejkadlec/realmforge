package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Lifecycle of a plot (architecture 15.5). */
public enum PlotState implements StringRepresentable {
    /** Rooms are being placed over several ticks; chunks are held by tickets. */
    BUILDING,
    /** Built, waiting for its party. */
    READY,
    /** At least one party member has entered. */
    ACTIVE,
    /** The run's goal is done (the final boss is dead, W4d); players stay until they leave or the plot closes. */
    COMPLETED,
    /** Released; its blocks are being cleared over several ticks before the plot is reused. */
    CLOSING,
    /** Unused and empty, ready for the next run. */
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
        return this == READY || this == ACTIVE || this == COMPLETED;
    }
}
