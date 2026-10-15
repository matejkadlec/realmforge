package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

/**
 * How a spell is cast (architecture 11.1, 11.2): {@code instant} so far, its effects running when it is cast. Cast
 * times and channels come with C3a-2 (RLM-210).
 *
 * @param type the cast type
 */
public record SpellCast(Type type) {
    public static final SpellCast INSTANT = new SpellCast(Type.INSTANT);

    public static final Codec<SpellCast> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Type.CODEC.fieldOf("type").forGetter(SpellCast::type))
            .apply(instance, SpellCast::new));

    /** The cast types. */
    public enum Type implements StringRepresentable {
        /** The effects run when the spell is cast. */
        INSTANT("instant");

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        private final String serializedName;

        Type(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
