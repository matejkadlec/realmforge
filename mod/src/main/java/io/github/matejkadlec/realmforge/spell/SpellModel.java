package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * A spell projectile's shape, an entry of the datapack registry {@code realmforge:spell_model} from
 * {@code content/spell_models/} (architecture 11.3, ADR 0073), drawn with the material of its spell's look
 * ({@link SpellLook#material}). Synced, as its clients draw it.
 *
 * @param shape what is drawn
 * @param scale its width as a share of the projectile's hitbox: 1 fills it; a model of cubes is as wide as the
 *     larger of its extents across its length
 * @param orientation how it turns as it flies
 * @param spinDegreesPerSecond how fast a {@link Orientation#FORWARD} model spins about its flight
 */
public record SpellModel(Shape shape, float scale, Orientation orientation, float spinDegreesPerSecond) {
    public static final float MAX_SCALE = 4;
    public static final float MAX_SPIN = 1440;

    public static final Codec<SpellModel> CODEC = RecordCodecBuilder.<SpellModel>create(instance -> instance.group(
                    Shape.CODEC.fieldOf("shape").forGetter(SpellModel::shape),
                    Codec.floatRange(0, MAX_SCALE).optionalFieldOf("scale", 1.0F).forGetter(SpellModel::scale),
                    Orientation.CODEC.optionalFieldOf("orientation", Orientation.TUMBLE)
                            .forGetter(SpellModel::orientation),
                    Codec.floatRange(0, MAX_SPIN).optionalFieldOf("spin_degrees_per_second", 0.0F)
                            .forGetter(SpellModel::spinDegreesPerSecond))
            .apply(instance, SpellModel::new)).validate(SpellModel::validate);

    private static DataResult<SpellModel> validate(SpellModel model) {
        if (!(model.scale > 0)) {
            return DataResult.error(() -> "A spell model's scale is above 0: " + model);
        }
        if (model.spinDegreesPerSecond != 0 && model.orientation != Orientation.FORWARD) {
            return DataResult.error(() -> "Only a forward spell model spins about its flight: " + model);
        }
        return DataResult.success(model);
    }

    /** What a model draws. */
    public enum Shape implements StringRepresentable {
        /** Three crossed slabs half a block across: the placeholder shape of a model without art. */
        ORB("orb"),
        /**
         * A GeckoLib model of at most 64 cubes, {@code assets/<ns>/geckolib/models/spell/<path>.geo.json} for the
         * entry's id ({@link #geometry}), which {@code rf gen} writes from {@code art/models/spell/} (RLM-640). It
         * forms cube by cube.
         */
        CUBES("cubes");

        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);

        private final String serializedName;

        Shape(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /**
     * The GeckoLib model of the {@link Shape#CUBES} entry {@code id}, as GeckoLib's model cache names it:
     * {@code <ns>:spell/<path>}.
     */
    public static Identifier geometry(Identifier id) {
        return id.withPrefix("spell/");
    }

    /** How a model turns as it flies. */
    public enum Orientation implements StringRepresentable {
        /** It turns over on every axis, as a thrown stone does. */
        TUMBLE("tumble"),
        /** It points along its flight, and may spin about that line. */
        FORWARD("forward");

        public static final Codec<Orientation> CODEC = StringRepresentable.fromEnum(Orientation::values);

        private final String serializedName;

        Orientation(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
