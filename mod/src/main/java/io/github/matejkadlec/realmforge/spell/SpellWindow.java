package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

/**
 * A spell usable only for a while after something happens (RLM-540; architecture 11.2): Overpower after a block,
 * Victory Rush after a kill. {@link Windows} opens it; a cast closes it, and the spell bar shows it open with a glow
 * and the time left.
 *
 * @param durationTicks how long it stays open
 * @param openedBy what opens it besides a passive's proc
 */
public record SpellWindow(int durationTicks, List<Opener> openedBy) {
    public static final Codec<SpellWindow> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ExtraCodecs.POSITIVE_INT.fieldOf("duration_ticks").forGetter(SpellWindow::durationTicks),
                    Opener.CODEC.listOf().optionalFieldOf("opened_by", List.of()).forGetter(SpellWindow::openedBy))
            .apply(instance, SpellWindow::new));

    public SpellWindow {
        openedBy = List.copyOf(openedBy);
    }

    /** What opens a window. */
    public enum Opener implements StringRepresentable {
        /** The player blocks a hit with a shield or a raised two-handed weapon (Overpower). */
        BLOCK("block"),
        /** The player kills an enemy that is not gray: its level at least the player's - 3 (Victory Rush). */
        KILL("kill");

        public static final Codec<Opener> CODEC = StringRepresentable.fromEnum(Opener::values);

        private final String serializedName;

        Opener(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
