package io.github.matejkadlec.realmforge.world.realm;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The look of a realm portal's frame (RLM-301; realm portal spec, "Look and sound"): the textures its Gatewright
 * Bricks and its sealstone show. A dark frame is {@link #WASHED}; a lit one takes the look of the realm its portal
 * opens onto ({@link Realm.Portal#frame}), set on the frame's blocks as their {@link #PROPERTY} by
 * {@link RealmPortals}. A block's states and textures are fixed when the game starts, so the looks are a fixed set:
 * a new one is a value here, a name in the pipeline's {@code FRAME_LOOKS} and its colours in
 * {@code art/frame_looks.py}.
 */
public enum FrameLook implements StringRepresentable {
    /** Washed-out bricks, the socket empty: a dark frame, and a frame open onto an entry without a look of its own. */
    WASHED("washed"),
    /** Undervault's: heavy grey stone, a slate blue stone in the socket. */
    STONE("stone");

    public static final Codec<FrameLook> CODEC = StringRepresentable.fromEnum(FrameLook::values);
    /** {@code look}, on Gatewright Bricks and the sealstone. */
    public static final EnumProperty<FrameLook> PROPERTY = EnumProperty.create("look", FrameLook.class);

    private final String serializedName;

    FrameLook(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
