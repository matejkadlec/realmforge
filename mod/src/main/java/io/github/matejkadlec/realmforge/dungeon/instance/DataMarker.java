package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;

/**
 * A data marker of a built room (architecture 15.3, step 3): a structure block in data mode whose metadata names what
 * stands there, the way vanilla's structures mark spots. The builder never places it ({@link InstanceBuilder}); the
 * plot keeps its kind and where it was.
 *
 * @param kind what stands there
 * @param room its room, an index into the layout's rooms
 * @param pos where it is, relative to the plot's origin
 */
public record DataMarker(Kind kind, int room, BlockPos pos) {
    public static final Codec<DataMarker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Kind.CODEC.fieldOf("kind").forGetter(DataMarker::kind),
            Codec.INT.fieldOf("room").forGetter(DataMarker::room),
            BlockPos.CODEC.fieldOf("pos").forGetter(DataMarker::pos)
    ).apply(instance, DataMarker::new));

    /** The names a marker may have; content validation knows the same ones ({@code rooms/check.py}). */
    public enum Kind implements StringRepresentable {
        /** Where players arrive, in the entrance room; without one the builder finds a spot there. */
        ENTRANCE,
        /** Where the way out stands (W4d). */
        EXIT,
        /** A spot where the dungeon's encounters put a mob (W6). */
        MOB,
        /** A spot where the dungeon's loot puts a chest (W6). */
        CHEST;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The kind a marker's metadata names, if it names one. */
        public static Optional<Kind> byName(String name) {
            for (Kind kind : values()) {
                if (kind.getSerializedName().equals(name)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }
}
